#!/usr/bin/env python3
"""POC-002 durable operation engine.

POC-ONLY — NOT PRODUCTION AUTHORITY.
Standard-library host harness used to validate durable-state/reconciliation semantics.
"""
from __future__ import annotations

import argparse
import errno
import hashlib
import json
import os
import resource
import sys
import time
import uuid
from pathlib import Path
from typing import Any

BUFFER_BYTES = 1024 * 1024


def now_ns() -> int:
    return time.time_ns()


def peak_rss_bytes() -> int:
    raw = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    return int(raw if sys.platform == "darwin" else raw * 1024)


def fsync_dir(path: Path) -> None:
    fd = os.open(path, os.O_RDONLY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def atomic_write_json(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    encoded = (json.dumps(data, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode()
    with open(tmp, "wb") as f:
        f.write(encoded)
        f.flush()
        os.fsync(f.fileno())
    os.replace(tmp, path)
    fsync_dir(path.parent)


def load_state(path: Path) -> dict[str, Any]:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def fingerprint(path: Path) -> dict[str, Any]:
    st = path.stat()
    return {"size": st.st_size, "mtime_ns": st.st_mtime_ns, "dev": st.st_dev, "ino": st.st_ino}


def sha256(path: Path) -> tuple[str, float]:
    start = time.perf_counter()
    h = hashlib.sha256()
    with open(path, "rb", buffering=0) as f:
        while True:
            block = f.read(4 * 1024 * 1024)
            if not block:
                break
            h.update(block)
    return h.hexdigest(), time.perf_counter() - start


def save_state(path: Path, state: dict[str, Any]) -> None:
    state["updated_at_ns"] = now_ns()
    atomic_write_json(path, state)


def fail_state(state_path: Path, state: dict[str, Any], code: str, message: str, status: str = "FAILED") -> int:
    state["status"] = status
    state["error"] = {"code": code, "message": message, "at_ns": now_ns()}
    save_state(state_path, state)
    return 2


def create_operation(source: Path, destination: Path, state_path: Path, move: bool, checkpoint_bytes: int, buffer_bytes: int) -> dict[str, Any]:
    source = source.resolve()
    destination = destination.resolve()
    if not source.is_file():
        raise FileNotFoundError(source)
    op_id = str(uuid.uuid4())
    partial = destination.with_name(destination.name + f".{op_id}.partial")
    source_fp = fingerprint(source)
    state = {
        "schema": 1,
        "poc_only": True,
        "operation_id": op_id,
        "operation": "MOVE" if move else "COPY",
        "source_ref": str(source),
        "destination_ref": str(destination),
        "partial_ref": str(partial),
        "phase": "PLAN",
        "status": "READY",
        "total_bytes": source_fp["size"],
        "completed_bytes": 0,
        "checkpoint_bytes": checkpoint_bytes,
        "buffer_bytes": buffer_bytes,
        "retry_count": 0,
        "checkpoint_count": 0,
        "source_fingerprint": source_fp,
        "verification": None,
        "error": None,
        "created_at_ns": now_ns(),
        "updated_at_ns": now_ns(),
        "completed_at_ns": None,
        "source_deleted_at_ns": None,
        "metrics": {"transfer_seconds": 0.0, "checkpoint_persist_seconds": 0.0, "verification_seconds": 0.0, "peak_rss_bytes": peak_rss_bytes()},
    }
    save_state(state_path, state)
    return state


def maybe_kill(fault: str | None, point: str, completed: int, total: int) -> None:
    hit = fault == point
    if fault == "early" and point == "checkpoint" and completed >= min(total, 4 * 1024 * 1024):
        hit = True
    elif fault == "mid" and point == "checkpoint" and completed >= total // 2:
        hit = True
    elif fault == "near_end" and point == "checkpoint" and completed >= (total * 9) // 10:
        hit = True
    if hit:
        os._exit(91)


def run_operation(state_path: Path, fault: str | None = None, cancel_at: int | None = None, enospc_at: int | None = None) -> int:
    state = load_state(state_path)
    source = Path(state["source_ref"])
    destination = Path(state["destination_ref"])
    partial = Path(state["partial_ref"])

    if state["phase"] == "COMPLETE":
        if state["operation"] == "MOVE" and source.exists() and destination.exists():
            source.unlink()
            state["source_deleted_at_ns"] = now_ns()
            save_state(state_path, state)
        return 0

    if not source.exists():
        return fail_state(state_path, state, "SOURCE_MISSING", "source no longer exists", "BLOCKED")

    current_fp = fingerprint(source)
    expected_fp = state["source_fingerprint"]
    if any(current_fp[k] != expected_fp[k] for k in ("size", "mtime_ns", "dev", "ino")):
        return fail_state(state_path, state, "SOURCE_MUTATED", f"source fingerprint changed: {current_fp} != {expected_fp}", "BLOCKED")

    if destination.exists():
        return fail_state(state_path, state, "DESTINATION_CONFLICT", "final destination already exists", "BLOCKED")

    state["retry_count"] += 1
    state["status"] = "RUNNING"
    state["error"] = None

    partial.parent.mkdir(parents=True, exist_ok=True)
    if partial.exists():
        actual_partial = partial.stat().st_size
        if actual_partial > state["total_bytes"]:
            return fail_state(state_path, state, "PARTIAL_OVERSIZE", f"partial={actual_partial} total={state['total_bytes']}")
        state["completed_bytes"] = actual_partial
        state["reconciled_partial_bytes"] = actual_partial
    else:
        state["completed_bytes"] = 0
        state["phase"] = "CREATE_PARTIAL"
        with open(partial, "xb"):
            pass
        save_state(state_path, state)

    state["phase"] = "TRANSFER"
    save_state(state_path, state)
    transfer_start = time.perf_counter()
    checkpoint_mark = state["completed_bytes"]

    try:
        with open(source, "rb", buffering=0) as src, open(partial, "r+b", buffering=0) as dst:
            src.seek(state["completed_bytes"])
            dst.seek(state["completed_bytes"])
            while state["completed_bytes"] < state["total_bytes"]:
                remaining = state["total_bytes"] - state["completed_bytes"]
                chunk = src.read(min(state["buffer_bytes"], remaining))
                if not chunk:
                    return fail_state(state_path, state, "UNEXPECTED_EOF", "source ended before expected total")
                if enospc_at is not None and state["completed_bytes"] + len(chunk) > enospc_at:
                    allowed = max(0, enospc_at - state["completed_bytes"])
                    if allowed:
                        dst.write(chunk[:allowed])
                        dst.flush()
                        os.fsync(dst.fileno())
                        state["completed_bytes"] += allowed
                    raise OSError(errno.ENOSPC, "POC synthetic ENOSPC injection")
                dst.write(chunk)
                state["completed_bytes"] += len(chunk)

                if cancel_at is not None and state["completed_bytes"] >= cancel_at:
                    dst.flush()
                    os.fsync(dst.fileno())
                    state["phase"] = "CHECKPOINT"
                    state["status"] = "CANCELLED"
                    save_state(state_path, state)
                    return 3

                if state["completed_bytes"] - checkpoint_mark >= state["checkpoint_bytes"] or state["completed_bytes"] == state["total_bytes"]:
                    dst.flush()
                    os.fsync(dst.fileno())
                    state["phase"] = "CHECKPOINT"
                    cp_start = time.perf_counter()
                    state["checkpoint_count"] += 1
                    save_state(state_path, state)
                    state["metrics"]["checkpoint_persist_seconds"] += time.perf_counter() - cp_start
                    maybe_kill(fault, "checkpoint", state["completed_bytes"], state["total_bytes"])
                    checkpoint_mark = state["completed_bytes"]
                    state["phase"] = "TRANSFER"
            dst.flush()
            os.fsync(dst.fileno())
    except OSError as exc:
        if exc.errno == errno.ENOSPC:
            state["phase"] = "CHECKPOINT"
            state["status"] = "FAILED"
            state["error"] = {"code": "ENOSPC", "message": str(exc), "synthetic": True, "at_ns": now_ns()}
            state["metrics"]["transfer_seconds"] += time.perf_counter() - transfer_start
            state["metrics"]["peak_rss_bytes"] = max(state["metrics"]["peak_rss_bytes"], peak_rss_bytes())
            save_state(state_path, state)
            return 4
        raise

    state["metrics"]["transfer_seconds"] += time.perf_counter() - transfer_start
    state["phase"] = "VERIFY"
    state["status"] = "RUNNING"
    save_state(state_path, state)
    maybe_kill(fault, "after_transfer", state["completed_bytes"], state["total_bytes"])

    if partial.stat().st_size != state["total_bytes"]:
        return fail_state(state_path, state, "SIZE_MISMATCH", f"partial size={partial.stat().st_size} expected={state['total_bytes']}")

    src_hash, src_hash_seconds = sha256(source)
    partial_hash, partial_hash_seconds = sha256(partial)
    state["metrics"]["verification_seconds"] += src_hash_seconds + partial_hash_seconds
    state["verification"] = {
        "method": "size+sha256",
        "source_sha256": src_hash,
        "partial_sha256": partial_hash,
        "source_hash_seconds": src_hash_seconds,
        "partial_hash_seconds": partial_hash_seconds,
        "verified_at_ns": now_ns(),
    }
    if src_hash != partial_hash:
        return fail_state(state_path, state, "HASH_MISMATCH", "source and partial hashes differ")
    save_state(state_path, state)
    maybe_kill(fault, "after_verify", state["completed_bytes"], state["total_bytes"])

    state["phase"] = "FINALIZE"
    pre = partial.stat()
    save_state(state_path, state)
    os.replace(partial, destination)
    fsync_dir(destination.parent)
    post = destination.stat()
    state["finalization"] = {
        "mechanism": "os.replace same-filesystem",
        "partial_dev": pre.st_dev,
        "partial_ino": pre.st_ino,
        "final_dev": post.st_dev,
        "final_ino": post.st_ino,
        "same_device": pre.st_dev == post.st_dev,
        "inode_preserved": pre.st_ino == post.st_ino,
        "partial_exists_after": partial.exists(),
        "destination_exists_after": destination.exists(),
        "observed_only_not_universal_atomicity_claim": True,
    }
    state["phase"] = "COMPLETE"
    state["status"] = "COMPLETE"
    state["completed_at_ns"] = now_ns()
    state["metrics"]["peak_rss_bytes"] = max(state["metrics"]["peak_rss_bytes"], peak_rss_bytes())
    save_state(state_path, state)

    if state["operation"] == "MOVE":
        source.unlink()
        state["source_deleted_at_ns"] = now_ns()
        save_state(state_path, state)
    return 0


def cli() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="cmd", required=True)
    create = sub.add_parser("create")
    create.add_argument("--source", required=True)
    create.add_argument("--destination", required=True)
    create.add_argument("--state", required=True)
    create.add_argument("--move", action="store_true")
    create.add_argument("--checkpoint-bytes", type=int, default=8 * 1024 * 1024)
    create.add_argument("--buffer-bytes", type=int, default=BUFFER_BYTES)
    run = sub.add_parser("run")
    run.add_argument("--state", required=True)
    run.add_argument("--fault", choices=["early", "mid", "near_end", "after_transfer", "after_verify"])
    run.add_argument("--cancel-at", type=int)
    run.add_argument("--enospc-at", type=int)
    args = parser.parse_args()
    if args.cmd == "create":
        state = create_operation(Path(args.source), Path(args.destination), Path(args.state), args.move, args.checkpoint_bytes, args.buffer_bytes)
        print(json.dumps(state, sort_keys=True))
        return 0
    return run_operation(Path(args.state), args.fault, args.cancel_at, args.enospc_at)


if __name__ == "__main__":
    raise SystemExit(cli())
