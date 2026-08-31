#!/usr/bin/env python3
"""Apply/verify the Tihulu overlay only when its inputs actually changed.

Repeatedly rewriting identical generated source files changes mtimes and can invalidate a
large Chromium incremental build. A content fingerprint plus the existing verifier lets
reruns reuse Ninja/Siso work without weakening drift checks.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STAMP_NAME = ".tihulu_tv_overlay_fingerprint"


def overlay_inputs() -> list[Path]:
    paths = [
        ROOT / "scripts/apply_overlay.py",
        ROOT / "scripts/verify_overlay.py",
    ]
    for base in [ROOT / "overlay", ROOT / "assets/branding"]:
        if base.is_dir():
            paths.extend(path for path in base.rglob("*") if path.is_file())
    return sorted(set(paths), key=lambda path: path.relative_to(ROOT).as_posix())


def fingerprint() -> str:
    digest = hashlib.sha256()
    for path in overlay_inputs():
        rel = path.relative_to(ROOT).as_posix().encode("utf-8")
        digest.update(len(rel).to_bytes(4, "big"))
        digest.update(rel)
        data = path.read_bytes()
        digest.update(len(data).to_bytes(8, "big"))
        digest.update(data)
    return digest.hexdigest()


def verify(project: Path, quiet: bool) -> bool:
    kwargs = {}
    if quiet:
        kwargs = {"stdout": subprocess.DEVNULL, "stderr": subprocess.DEVNULL}
    result = subprocess.run(
        [sys.executable, str(ROOT / "scripts/verify_overlay.py"), str(project)],
        check=False,
        **kwargs,
    )
    return result.returncode == 0


def _write_stamp(path: Path, value: str) -> None:
    current = path.read_text(encoding="utf-8").strip() if path.is_file() else None
    if current == value:
        return
    tmp = path.with_name(path.name + ".tihulu-tmp")
    tmp.write_text(value + "\n", encoding="utf-8")
    os.replace(tmp, path)


def ensure(project: Path) -> None:
    project = project.resolve()
    stamp = project / STAMP_NAME
    wanted = fingerprint()
    current = stamp.read_text(encoding="utf-8").strip() if stamp.is_file() else ""

    if current == wanted and verify(project, quiet=True):
        print("Tihulu TV Browser overlay unchanged; reusing the existing verified checkout.")
        return

    subprocess.run(
        [sys.executable, str(ROOT / "scripts/apply_overlay.py"), str(project)],
        check=True,
    )
    if not verify(project, quiet=False):
        raise RuntimeError("Overlay verification failed after applying the current overlay")
    _write_stamp(stamp, wanted)
    print("Tihulu TV Browser overlay fingerprint updated.")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("project", type=Path)
    args = parser.parse_args()
    try:
        ensure(args.project)
    except (OSError, RuntimeError, subprocess.CalledProcessError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
