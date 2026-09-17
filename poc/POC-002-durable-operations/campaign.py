#!/usr/bin/env python3
"""POC-002 fault/recovery campaign.

POC-ONLY — NOT PRODUCTION AUTHORITY.
Runs the durable engine in child processes so process death is real child-process termination.
"""
from __future__ import annotations

import hashlib
import json
import os
import platform
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent
ENGINE = ROOT / "engine.py"
WORK = ROOT / "work"
RESULTS = ROOT / "results"
MIB = 1024 * 1024
GIB = 1024 * MIB


def run_cmd(args: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run([sys.executable, str(ENGINE), *args], text=True, capture_output=True)


def read_state(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb", buffering=0) as f:
        while True:
            b = f.read(4 * MIB)
            if not b:
                break
            h.update(b)
    return h.hexdigest()


def create_pattern(path: Path, size: int, sparse: bool = False) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if sparse:
        with open(path, "wb") as f:
            f.truncate(size)
        return
    block = bytes(((i * 17 + 3) & 0xFF for i in range(MIB)))
    with open(path, "wb", buffering=0) as f:
        remain = size
        while remain:
            n = min(len(block), remain)
            f.write(block[:n])
            remain -= n
        f.flush()
        os.fsync(f.fileno())


def create_case(name: str, size: int, *, move: bool = False, checkpoint: int = 8 * MIB, sparse: bool = False) -> tuple[Path, Path, Path, Path]:
    case = WORK / name
    case.mkdir(parents=True, exist_ok=True)
    source = case / "source.bin"
    destination = case / "destination.bin"
    state = case / "state.json"
    create_pattern(source, size, sparse=sparse)
    args = ["create", "--source", str(source), "--destination", str(destination), "--state", str(state), "--checkpoint-bytes", str(checkpoint), "--buffer-bytes", str(MIB)]
    if move:
        args.append("--move")
    cp = run_cmd(args)
    if cp.returncode != 0:
        raise RuntimeError(f"create failed {name}: {cp.returncode} {cp.stderr}")
    return case, source, destination, state


def compact_state(state: dict[str, Any]) -> dict[str, Any]:
    keep = [
        "operation_id", "operation", "phase", "status", "total_bytes", "completed_bytes",
        "checkpoint_bytes", "buffer_bytes", "retry_count", "checkpoint_count",
        "reconciled_partial_bytes", "verification", "error", "finalization",
        "completed_at_ns", "source_deleted_at_ns", "metrics",
    ]
    return {k: state.get(k) for k in keep if k in state}


def run_fault_case(fault: str) -> dict[str, Any]:
    case, source, dest, state_path = create_case(f"fault-{fault}", 64 * MIB)
    first = run_cmd(["run", "--state", str(state_path), "--fault", fault])
    crashed = read_state(state_path)
    partial = Path(crashed["partial_ref"])
    before = {
        "child_returncode": first.returncode,
        "source_exists": source.exists(),
        "destination_exists": dest.exists(),
        "partial_exists": partial.exists(),
        "partial_bytes": partial.stat().st_size if partial.exists() else None,
        "state": compact_state(crashed),
    }
    second = run_cmd(["run", "--state", str(state_path)])
    final = read_state(state_path)
    result = {
        "before_resume": before,
        "resume_returncode": second.returncode,
        "after_resume": {
            "source_exists": source.exists(),
            "destination_exists": dest.exists(),
            "partial_exists": Path(final["partial_ref"]).exists(),
            "state": compact_state(final),
        },
        "pass": first.returncode == 91 and second.returncode == 0 and final["phase"] == "COMPLETE" and dest.exists() and source.exists(),
    }
    shutil.rmtree(case)
    return result


def baseline_copy() -> dict[str, Any]:
    case, source, dest, state_path = create_case("baseline-copy", 4 * MIB)
    cp = run_cmd(["run", "--state", str(state_path)])
    state = read_state(state_path)
    result = {
        "returncode": cp.returncode,
        "source_hash": sha256(source),
        "destination_hash": sha256(dest) if dest.exists() else None,
        "state": compact_state(state),
        "pass": cp.returncode == 0 and state["phase"] == "COMPLETE" and source.exists() and dest.exists(),
    }
    shutil.rmtree(case)
    return result


def cancellation_case() -> dict[str, Any]:
    case, source, dest, state_path = create_case("cancellation", 64 * MIB)
    first = run_cmd(["run", "--state", str(state_path), "--cancel-at", str(20 * MIB)])
    cancelled = read_state(state_path)
    partial = Path(cancelled["partial_ref"])
    second = run_cmd(["run", "--state", str(state_path)])
    final = read_state(state_path)
    result = {
        "cancel_returncode": first.returncode,
        "cancel_state": compact_state(cancelled),
        "partial_bytes_after_cancel": partial.stat().st_size if partial.exists() else cancelled.get("completed_bytes"),
        "source_exists_after_cancel": source.exists(),
        "destination_exists_after_cancel": dest.exists(),
        "resume_returncode": second.returncode,
        "final_state": compact_state(final),
        "pass": first.returncode == 3 and cancelled["status"] == "CANCELLED" and second.returncode == 0 and final["phase"] == "COMPLETE",
    }
    shutil.rmtree(case)
    return result


def enospc_case() -> dict[str, Any]:
    case, source, dest, state_path = create_case("enospc", 64 * MIB)
    first = run_cmd(["run", "--state", str(state_path), "--enospc-at", str(23 * MIB)])
    failed = read_state(state_path)
    partial = Path(failed["partial_ref"])
    partial_bytes = partial.stat().st_size if partial.exists() else None
    second = run_cmd(["run", "--state", str(state_path)])
    final = read_state(state_path)
    result = {
        "injected_returncode": first.returncode,
        "failed_state": compact_state(failed),
        "partial_bytes_after_enospc": partial_bytes,
        "resume_returncode": second.returncode,
        "final_state": compact_state(final),
        "pass": first.returncode == 4 and failed.get("error", {}).get("code") == "ENOSPC" and second.returncode == 0 and final["phase"] == "COMPLETE",
    }
    shutil.rmtree(case)
    return result


def conflict_case() -> dict[str, Any]:
    case, source, dest, state_path = create_case("destination-conflict", 8 * MIB)
    dest.write_bytes(b"preexisting-destination")
    before = sha256(dest)
    cp = run_cmd(["run", "--state", str(state_path)])
    state = read_state(state_path)
    after = sha256(dest)
    result = {
        "returncode": cp.returncode,
        "state": compact_state(state),
        "destination_unchanged": before == after,
        "source_exists": source.exists(),
        "pass": cp.returncode == 2 and state["status"] == "BLOCKED" and state.get("error", {}).get("code") == "DESTINATION_CONFLICT" and before == after,
    }
    shutil.rmtree(case)
    return result


def source_mutation_case() -> dict[str, Any]:
    case, source, dest, state_path = create_case("source-mutation", 64 * MIB)
    first = run_cmd(["run", "--state", str(state_path), "--fault", "mid"])
    crashed = read_state(state_path)
    with open(source, "r+b", buffering=0) as f:
        f.seek(12345)
        f.write(b"MUTATED!")
        f.flush()
        os.fsync(f.fileno())
    os.utime(source, None)
    second = run_cmd(["run", "--state", str(state_path)])
    final = read_state(state_path)
    result = {
        "kill_returncode": first.returncode,
        "pre_mutation_state": compact_state(crashed),
        "resume_returncode": second.returncode,
        "final_state": compact_state(final),
        "source_exists": source.exists(),
        "destination_exists": dest.exists(),
        "partial_exists": Path(final["partial_ref"]).exists(),
        "pass": first.returncode == 91 and second.returncode == 2 and final["status"] == "BLOCKED" and final.get("error", {}).get("code") == "SOURCE_MUTATED" and source.exists() and not dest.exists(),
    }
    shutil.rmtree(case)
    return result


def move_ordering_case() -> dict[str, Any]:
    case, source, dest, state_path = create_case("move-ordering", 64 * MIB, move=True)
    first = run_cmd(["run", "--state", str(state_path), "--fault", "after_verify"])
    crashed = read_state(state_path)
    source_exists_before_resume = source.exists()
    dest_exists_before_resume = dest.exists()
    second = run_cmd(["run", "--state", str(state_path)])
    final = read_state(state_path)
    result = {
        "kill_returncode": first.returncode,
        "state_after_verify_kill": compact_state(crashed),
        "source_exists_before_resume": source_exists_before_resume,
        "destination_exists_before_resume": dest_exists_before_resume,
        "resume_returncode": second.returncode,
        "source_exists_final": source.exists(),
        "destination_exists_final": dest.exists(),
        "final_state": compact_state(final),
        "delete_after_complete": bool(final.get("source_deleted_at_ns") and final.get("completed_at_ns") and final["source_deleted_at_ns"] >= final["completed_at_ns"]),
        "pass": first.returncode == 91 and source_exists_before_resume and not dest_exists_before_resume and second.returncode == 0 and not source.exists() and dest.exists() and final.get("source_deleted_at_ns", 0) >= final.get("completed_at_ns", 0),
    }
    shutil.rmtree(case)
    return result


def checkpoint_benchmarks() -> dict[str, Any]:
    case = WORK / "checkpoint-bench"
    case.mkdir(parents=True, exist_ok=True)
    source = case / "source-256m.bin"
    create_pattern(source, 256 * MIB)
    results: dict[str, Any] = {}
    for cp_bytes in (1 * MIB, 8 * MIB, 64 * MIB):
        dest = case / f"dest-{cp_bytes}.bin"
        state_path = case / f"state-{cp_bytes}.json"
        created = run_cmd(["create", "--source", str(source), "--destination", str(dest), "--state", str(state_path), "--checkpoint-bytes", str(cp_bytes), "--buffer-bytes", str(MIB)])
        if created.returncode != 0:
            raise RuntimeError(created.stderr)
        wall = time.perf_counter()
        cp = run_cmd(["run", "--state", str(state_path)])
        wall = time.perf_counter() - wall
        state = read_state(state_path)
        results[str(cp_bytes)] = {"returncode": cp.returncode, "wall_seconds": wall, "state": compact_state(state), "pass": cp.returncode == 0 and state["phase"] == "COMPLETE"}
        if dest.exists():
            dest.unlink()
        state_path.unlink(missing_ok=True)
    shutil.rmtree(case)
    return results


def multi_gib_case() -> dict[str, Any]:
    case, source, dest, state_path = create_case("multi-gib-2g", 2 * GIB, checkpoint=64 * MIB, sparse=True)
    src_stat = source.stat()
    wall = time.perf_counter()
    cp = run_cmd(["run", "--state", str(state_path)])
    wall = time.perf_counter() - wall
    state = read_state(state_path)
    result = {
        "logical_bytes": 2 * GIB,
        "source_blocks_512": getattr(src_stat, "st_blocks", None),
        "returncode": cp.returncode,
        "wall_seconds": wall,
        "destination_size": dest.stat().st_size if dest.exists() else None,
        "state": compact_state(state),
        "pass": cp.returncode == 0 and state["phase"] == "COMPLETE" and dest.exists() and dest.stat().st_size == 2 * GIB,
    }
    shutil.rmtree(case)
    return result


def main() -> int:
    if WORK.exists():
        shutil.rmtree(WORK)
    WORK.mkdir(parents=True)
    RESULTS.mkdir(parents=True, exist_ok=True)

    started = time.time_ns()
    campaign: dict[str, Any] = {
        "poc": "POC-002",
        "poc_only": True,
        "production_authority": False,
        "architecture_start_sha": "79fc0f18c7f5e1d8e0ae714808f89977ff178b62",
        "host": {
            "platform": platform.platform(),
            "machine": platform.machine(),
            "python": sys.version,
            "executable": sys.executable,
        },
        "started_at_ns": started,
        "scenarios": {},
    }
    scenarios = campaign["scenarios"]
    scenarios["baseline_copy"] = baseline_copy()
    for fault in ("early", "mid", "near_end", "after_transfer", "after_verify"):
        scenarios[f"process_kill_{fault}"] = run_fault_case(fault)
    scenarios["user_cancellation_resume"] = cancellation_case()
    scenarios["synthetic_enospc_resume"] = enospc_case()
    scenarios["destination_conflict"] = conflict_case()
    scenarios["source_mutation"] = source_mutation_case()
    scenarios["move_delete_ordering"] = move_ordering_case()
    scenarios["checkpoint_tradeoff_256m"] = checkpoint_benchmarks()
    scenarios["multi_gib_2g"] = multi_gib_case()

    simple_passes = []
    for key, value in scenarios.items():
        if key == "checkpoint_tradeoff_256m":
            simple_passes.extend(item["pass"] for item in value.values())
        else:
            simple_passes.append(bool(value.get("pass")))
    campaign["all_mechanical_assertions_passed"] = all(simple_passes)
    campaign["finished_at_ns"] = time.time_ns()
    campaign["elapsed_seconds"] = (campaign["finished_at_ns"] - started) / 1_000_000_000

    out = RESULTS / "campaign.json"
    out.write_text(json.dumps(campaign, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    summary = {
        "all_mechanical_assertions_passed": campaign["all_mechanical_assertions_passed"],
        "elapsed_seconds": campaign["elapsed_seconds"],
        "scenario_pass": {k: (all(x["pass"] for x in v.values()) if k == "checkpoint_tradeoff_256m" else v.get("pass")) for k, v in scenarios.items()},
    }
    (RESULTS / "summary.json").write_text(json.dumps(summary, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    shutil.rmtree(WORK)
    print(json.dumps(summary, sort_keys=True))
    return 0 if campaign["all_mechanical_assertions_passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
