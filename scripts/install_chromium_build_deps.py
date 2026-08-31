#!/usr/bin/env python3
"""Run Chromium's build-dependency installer with a narrow Pop!_OS compatibility guard.

Chromium forces lib32 dependencies for Android builds on 64-bit hosts. Pop!_OS may ship an
amd64 linux-libc-dev from its kernel stack while Ubuntu's i386 archive offers a different
linux-libc-dev:i386 version. APT correctly refuses to co-install the mismatched pair. For an
Android cross-build we keep Chromium's dependency list intact except for that single conflicting
package, rather than downgrading the host's Pop!_OS kernel UAPI headers.
"""

from __future__ import annotations

import argparse
import importlib.util
import os
import subprocess
import sys
from pathlib import Path
from types import ModuleType
from typing import Callable

CONFLICTING_PACKAGE = "linux-libc-dev:i386"


def _command_output(argv: list[str]) -> str:
    try:
        return subprocess.check_output(argv, stderr=subprocess.DEVNULL, text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        return ""


def installed_linux_libc_version() -> str:
    return _command_output(["dpkg-query", "-W", "-f=${Version}", "linux-libc-dev"])


def i386_linux_libc_candidate() -> str:
    output = _command_output(["apt-cache", "policy", CONFLICTING_PACKAGE])
    for line in output.splitlines():
        stripped = line.strip()
        if stripped.startswith("Candidate:"):
            candidate = stripped.split(":", 1)[1].strip()
            return "" if candidate == "(none)" else candidate
    return ""


def is_pop_os() -> bool:
    try:
        text = Path("/etc/os-release").read_text(encoding="utf-8")
    except OSError:
        return False
    values: dict[str, str] = {}
    for line in text.splitlines():
        if "=" not in line or line.lstrip().startswith("#"):
            continue
        key, value = line.split("=", 1)
        values[key] = value.strip().strip('"')
    return values.get("ID", "").lower() == "pop"


def should_skip_pop_i386_linux_libc() -> tuple[bool, str, str]:
    installed = installed_linux_libc_version()
    candidate = i386_linux_libc_candidate()
    mismatch = bool(is_pop_os() and installed and candidate and installed != candidate)
    return mismatch, installed, candidate


def load_upstream_installer(path: Path) -> ModuleType:
    spec = importlib.util.spec_from_file_location("chromium_install_build_deps", path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load Chromium dependency installer: {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def install_pop_compat_filter(module: ModuleType) -> None:
    original: Callable = module.lib32_list

    def filtered(options):
        packages = list(original(options))
        return [package for package in packages if package != CONFLICTING_PACKAGE]

    module.lib32_list = filtered


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("installer", type=Path)
    parser.add_argument("installer_args", nargs=argparse.REMAINDER)
    args = parser.parse_args(argv)

    installer = args.installer.resolve()
    if not installer.is_file():
        print(f"Chromium dependency installer not found: {installer}", file=sys.stderr)
        return 2

    module = load_upstream_installer(installer)
    skip, installed, candidate = should_skip_pop_i386_linux_libc()
    if skip:
        print(
            "Pop!_OS compatibility: keeping installed linux-libc-dev "
            f"{installed} and omitting incompatible {CONFLICTING_PACKAGE} candidate {candidate}.",
            file=sys.stderr,
        )
        print(
            "This avoids downgrading Pop!_OS kernel UAPI headers. All other Chromium Android "
            "dependencies, including the remaining i386 multilib packages, are still installed.",
            file=sys.stderr,
        )
        install_pop_compat_filter(module)

    # The upstream module parses its own sys.argv. Preserve the path as argv[0] and pass through
    # only arguments supplied after our installer-path argument.
    old_argv = sys.argv
    try:
        sys.argv = [str(installer), *args.installer_args]
        result = module.main()
        return int(result or 0)
    finally:
        sys.argv = old_argv


if __name__ == "__main__":
    raise SystemExit(main())
