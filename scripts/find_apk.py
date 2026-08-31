#!/usr/bin/env python3
"""Find the newest valid Brave APK that actually contains the requested native ABI."""

from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path

ARCH_TO_ABI = {
    "arm64": "arm64-v8a",
    "arm64-v8a": "arm64-v8a",
    "arm": "armeabi-v7a",
    "armeabi-v7a": "armeabi-v7a",
    "x64": "x86_64",
    "x86_64": "x86_64",
    "x86": "x86",
}


class ApkSelectionError(RuntimeError):
    pass


def normalize_abi(arch: str) -> str:
    try:
        return ARCH_TO_ABI[arch]
    except KeyError as exc:
        raise ApkSelectionError(f"Unsupported Android architecture: {arch}") from exc


def apk_native_abis(apk: Path) -> set[str]:
    """Return ABI directory names from lib/<abi>/... entries in a valid APK ZIP."""
    try:
        with zipfile.ZipFile(apk) as archive:
            abis: set[str] = set()
            for name in archive.namelist():
                parts = name.split("/", 2)
                if len(parts) == 3 and parts[0] == "lib" and parts[1]:
                    abis.add(parts[1])
            return abis
    except (OSError, zipfile.BadZipFile) as exc:
        raise ApkSelectionError(f"Invalid APK archive: {apk}: {exc}") from exc


def find_apks(output_root: Path, arch: str) -> list[Path]:
    target_abi = normalize_abi(arch)
    if not output_root.is_dir():
        return []

    matches: list[Path] = []
    for apk in output_root.rglob("*.apk"):
        if "brave" not in apk.name.lower():
            continue
        try:
            abis = apk_native_abis(apk)
        except ApkSelectionError:
            # A partial/truncated output must never outrank a valid older APK.
            continue
        if target_abi in abis:
            matches.append(apk)

    matches.sort(key=lambda path: path.stat().st_mtime_ns, reverse=True)
    return matches


def find_apk(output_root: Path, arch: str) -> Path:
    target_abi = normalize_abi(arch)
    matches = find_apks(output_root, arch)
    if not matches:
        raise ApkSelectionError(
            f"No valid Brave APK containing native ABI {target_abi} was found under {output_root}"
        )
    return matches[0]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("output_root", type=Path)
    parser.add_argument("arch")
    args = parser.parse_args()
    try:
        print(find_apk(args.output_root.resolve(), args.arch))
    except ApkSelectionError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
