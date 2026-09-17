#!/usr/bin/env python3
# POC-ONLY — NOT PRODUCTION AUTHORITY
from __future__ import annotations

import bz2
import gzip
import io
import lzma
import os
import shutil
import stat
import subprocess
import tarfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "fixtures" / "generated"
TMP = ROOT / "fixtures" / "tmp"
SEVEN = ROOT / "vendor" / "7zip" / "7zz"
JUNRAR = ROOT / "vendor" / "junrar-src" / "src" / "test" / "resources" / "com" / "github" / "junrar"
MIB = 1024 * 1024


def clean() -> None:
    shutil.rmtree(OUT, ignore_errors=True)
    shutil.rmtree(TMP, ignore_errors=True)
    OUT.mkdir(parents=True, exist_ok=True)
    TMP.mkdir(parents=True, exist_ok=True)


def zip_fixtures() -> None:
    with zipfile.ZipFile(OUT / "normal.zip", "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("hello.txt", "hello archive\n")
        z.writestr("nested/ユニコード😀.txt", "unicode payload\n")
        z.writestr("spaces and quotes ' okay.txt", "name test\n")

    with zipfile.ZipFile(OUT / "zip64-forced.zip", "w", allowZip64=True) as z:
        with z.open("forced.bin", "w", force_zip64=True) as f:
            f.write(b"ZIP64-FORCED\n")

    inner = io.BytesIO()
    with zipfile.ZipFile(inner, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("inside.txt", "nested archive\n")
    with zipfile.ZipFile(OUT / "nested.zip", "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("inner.zip", inner.getvalue())
        z.writestr("outer.txt", "outer\n")

    with zipfile.ZipFile(OUT / "traversal.zip", "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("safe.txt", "safe\n")
        z.writestr("../escape.txt", "must never escape\n")
        z.writestr("nested/../../escape2.txt", "must never escape\n")

    with zipfile.ZipFile(OUT / "symlink-link-write.zip", "w") as z:
        link = zipfile.ZipInfo("link")
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        z.writestr(link, "../../outside-target")
        z.writestr("link/pwn.txt", "must never follow link\n")
        z.writestr("safe.txt", "safe\n")

    zeros = b"\0" * MIB
    with zipfile.ZipFile(OUT / "bomb-safe.zip", "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        with z.open("zeros-64m.bin", "w", force_zip64=True) as f:
            for _ in range(64):
                f.write(zeros)

    with zipfile.ZipFile(OUT / "many-10k.zip", "w", zipfile.ZIP_STORED) as z:
        for i in range(10_000):
            z.writestr(f"e/{i:05d}.txt", b"")

    with zipfile.ZipFile(OUT / "many-100k.zip", "w", zipfile.ZIP_STORED) as z:
        for i in range(100_000):
            z.writestr(f"e/{i:06d}.txt", b"")

    raw = (OUT / "normal.zip").read_bytes()
    (OUT / "malformed-truncated.zip").write_bytes(raw[: max(32, len(raw) // 2)])


def tar_payload() -> bytes:
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w") as t:
        for name, data in [("hello.txt", b"hello tar\n"), ("nested/ユニコード😀.txt", b"unicode tar\n")]:
            info = tarfile.TarInfo(name)
            info.size = len(data)
            info.mtime = 1_700_000_000
            t.addfile(info, io.BytesIO(data))
    return buf.getvalue()


def tar_fixtures() -> None:
    payload = tar_payload()
    (OUT / "normal.tar").write_bytes(payload)
    (OUT / "normal.tar.gz").write_bytes(gzip.compress(payload, compresslevel=6))
    (OUT / "normal.tar.bz2").write_bytes(bz2.compress(payload, compresslevel=9))
    (OUT / "normal.tar.xz").write_bytes(lzma.compress(payload, preset=6))

    with tarfile.open(OUT / "traversal.tar", "w") as t:
        data = b"escape\n"
        info = tarfile.TarInfo("../escape.txt")
        info.size = len(data)
        t.addfile(info, io.BytesIO(data))
        safe = tarfile.TarInfo("safe.txt")
        safe.size = 5
        t.addfile(safe, io.BytesIO(b"safe\n"))

    with tarfile.open(OUT / "symlink-link-write.tar", "w") as t:
        link = tarfile.TarInfo("link")
        link.type = tarfile.SYMTYPE
        link.linkname = "../../outside-target"
        t.addfile(link)
        data = b"must never follow link\n"
        child = tarfile.TarInfo("link/pwn.txt")
        child.size = len(data)
        t.addfile(child, io.BytesIO(data))

    (OUT / "malformed-truncated.tar").write_bytes(payload[:700])


def run7(args: list[str], cwd: Path | None = None) -> None:
    cp = subprocess.run([str(SEVEN), *args], cwd=str(cwd) if cwd else None, text=True, capture_output=True)
    if cp.returncode != 0:
        raise RuntimeError(f"7zz failed {args}: {cp.returncode}\n{cp.stdout}\n{cp.stderr}")


def sevenz_fixtures() -> None:
    safe = TMP / "seven-safe"
    safe.mkdir(parents=True)
    (safe / "hello.txt").write_text("hello 7z\n", encoding="utf-8")
    (safe / "ユニコード😀.txt").write_text("unicode 7z\n", encoding="utf-8")
    (safe / "nested").mkdir()
    (safe / "nested" / "target.txt").write_text("target\n", encoding="utf-8")

    run7(["a", "-bd", "-y", "-t7z", "-ms=off", str(OUT / "normal.7z"), str(safe)])
    run7(["a", "-bd", "-y", "-t7z", "-ms=on", str(OUT / "solid.7z"), str(safe)])
    run7(["a", "-bd", "-y", "-t7z", "-ms=on", "-pPOCpass", "-mhe=on", str(OUT / "encrypted.7z"), str(safe)])

    large = TMP / "large-64m.bin"
    with open(large, "wb") as f:
        f.truncate(64 * MIB)
    run7(["a", "-bd", "-y", "-t7z", "-ms=on", str(OUT / "large-64m.7z"), str(large)])

    many = TMP / "many10k"
    many.mkdir()
    for i in range(10_000):
        (many / f"{i:05d}.txt").touch()
    run7(["a", "-bd", "-y", "-t7z", "-ms=on", str(OUT / "many-10k.7z"), str(many)])

    # Interoperability ZIP encryption fixtures from the current official 7-Zip tool.
    input_file = safe / "hello.txt"
    run7(["a", "-bd", "-y", "-tzip", "-pPOCpass", "-mem=AES256", str(OUT / "zip-aes256.zip"), str(input_file)])
    run7(["a", "-bd", "-y", "-tzip", "-pPOCpass", "-mem=ZipCrypto", str(OUT / "zip-traditional.zip"), str(input_file)])


def rar_fixtures() -> None:
    targets = {
        "rar4.rar": JUNRAR / "rar4.rar",
        "rar5.rar": JUNRAR / "rar5.rar",
        "rar4-password-junrar.rar": JUNRAR / "password" / "rar4-password-junrar.rar",
        "rar5-password-junrar.rar": JUNRAR / "password" / "rar5-password-junrar.rar",
        "rar5-encrypted-junrar.rar": JUNRAR / "password" / "rar5-encrypted-junrar.rar",
        "rar4-solid.rar": JUNRAR / "solid" / "rar4-solid.rar",
        "rar5-solid.rar": JUNRAR / "solid" / "rar5-solid.rar",
        "rar-malformed-corrupt-header.rar": JUNRAR / "abnormal" / "corrupt-header.rar",
        "rar-mkdir-escape.rar": JUNRAR / "mkdir-escape.rar",
        "rar-sibling-prefix-traversal.rar": JUNRAR / "sibling-prefix-traversal.rar",
    }
    for name, src in targets.items():
        shutil.copy2(src, OUT / name)

    for src in (JUNRAR / "volumes" / "rar5-part").glob("stored.part*.rar"):
        shutil.copy2(src, OUT / src.name)
    for src in (JUNRAR / "volumes" / "new-part").glob("test-documents.part*.rar"):
        shutil.copy2(src, OUT / src.name)


def main() -> None:
    clean()
    zip_fixtures()
    tar_fixtures()
    sevenz_fixtures()
    rar_fixtures()
    print(f"generated {len(list(OUT.iterdir()))} top-level fixtures in {OUT}")


if __name__ == "__main__":
    main()
