#!/usr/bin/env python3
"""Copy a fresh native build to a predictable APK, checksum and provenance bundle."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import zipfile
from datetime import datetime, timezone
from pathlib import Path

from find_apk import ApkSelectionError, find_apks, normalize_abi


def package_apk(output_root: Path, arch: str, destination: Path, not_before: float,
                revision: str, brave_ref: str) -> Path:
    abi = normalize_abi(arch)
    candidates = [path for path in find_apks(output_root, arch)
                  if path.stat().st_mtime >= not_before]
    if not candidates:
        raise ApkSelectionError(f"No fresh {abi} APK from this build; refusing to publish a stale output")
    source = candidates[0]
    with zipfile.ZipFile(source) as archive:
        if "AndroidManifest.xml" not in archive.namelist() or "classes.dex" not in archive.namelist():
            raise ApkSelectionError("APK is missing its Android manifest or primary dex")
        damaged = archive.testzip()
        if damaged:
            raise ApkSelectionError(f"Corrupt APK member: {damaged}")
    destination.mkdir(parents=True, exist_ok=True)
    target = destination / f"tihulu-tv-{abi}.apk"
    shutil.copy2(source, target)
    with target.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    target.with_suffix(".apk.sha256").write_text(f"{digest}  {target.name}\n", encoding="utf-8")
    target.with_suffix(".json").write_text(json.dumps({
        "apk": target.name, "abi": abi, "sha256": digest,
        "overlay_commit": revision, "brave_ref": brave_ref,
        "built_at": datetime.now(timezone.utc).isoformat(),
        "channel": "development", "device_tested": False,
    }, indent=2) + "\n", encoding="utf-8")
    return target


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output_root", type=Path)
    parser.add_argument("arch")
    parser.add_argument("destination", type=Path)
    parser.add_argument("--not-before", required=True, type=float)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    revision = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
    brave_ref = (root / "config/brave-core-ref").read_text().strip()
    try:
        print(package_apk(args.output_root, args.arch, args.destination, args.not_before, revision, brave_ref))
    except (OSError, ApkSelectionError) as error:
        parser.exit(1, f"APK packaging failed: {error}\n")


if __name__ == "__main__":
    main()
