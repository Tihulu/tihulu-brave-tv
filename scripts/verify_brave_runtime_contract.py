#!/usr/bin/env python3
"""Fail closed when the pinned Brave/Chromium TV runtime contract drifts.

This verifier intentionally does not initialize services or change preferences. It checks the
upstream source anchors that guarantee Tihulu's ChromeTabbedActivity subclass still inherits Brave's
Android activity layer and that Brave Shields/adblock remains wired through the browser process and
component updater. It also guards the local build arguments against known unsafe ARM32 shortcuts.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


class ContractError(RuntimeError):
    pass


def _read(path: Path) -> str:
    if not path.is_file():
        raise ContractError(f"Missing pinned runtime source: {path}")
    return path.read_text(encoding="utf-8")


def _require(path: Path, *anchors: str) -> str:
    text = _read(path)
    missing = [anchor for anchor in anchors if anchor not in text]
    if missing:
        raise ContractError(
            f"Pinned runtime contract drifted in {path}: missing " + ", ".join(repr(x) for x in missing)
        )
    return text


def _build_args(text: str) -> str:
    match = re.search(r"BUILD_ARGS=\(\n(?P<body>.*?)\n\)", text, flags=re.DOTALL)
    if not match:
        raise ContractError("build-debug.sh BUILD_ARGS block changed; review runtime safety checks")
    return match.group("body")


def verify(workspace: Path, repo_root: Path) -> None:
    workspace = workspace.resolve()
    repo_root = repo_root.resolve()
    chromium = workspace / "src"
    brave = chromium / "brave"

    _require(
        brave / "build/android/bytecode/java/org/brave/bytecode/BraveTabbedActivityClassAdapter.java",
        'sChromeTabbedActivityClassName =\n            "org/chromium/chrome/browser/ChromeTabbedActivity"',
        'sBraveActivityClassName = "org/chromium/chrome/browser/app/BraveActivity"',
        "changeSuperName(sChromeTabbedActivityClassName, sBraveActivityClassName);",
    )
    _require(
        brave / "browser/brave_browser_main_extra_parts.cc",
        "void BraveBrowserMainExtraParts::PostBrowserStart()",
        "g_brave_browser_process->StartBraveServices();",
    )
    _require(
        brave / "browser/brave_browser_process_impl.cc",
        "std::make_unique<brave_shields::AdBlockService>",
        "ad_block_service_",
    )
    _require(
        brave / "components/brave_shields/core/browser/ad_block_filter_list_catalog_provider.cc",
        "RegisterAdBlockFilterListCatalogComponent(",
        "OnFilterListCatalogLoaded",
    )
    _require(
        brave / "browser/android/brave_shields_content_settings.cc",
        "g_brave_browser_process->ad_block_service()",
        "JNI_BraveShieldsContentSettings_GetBraveShieldsEnabled",
    )

    cta = _require(
        chromium / "chrome/android/java/src/org/chromium/chrome/browser/ChromeTabbedActivity.java",
        "public class ChromeTabbedActivity extends ChromeActivity",
    )
    exact_class_anchor = "getClass().equals(ChromeTabbedActivity.class)"
    exact_class_count = cta.count(exact_class_anchor)
    if exact_class_count != 2:
        raise ContractError(
            "ChromeTabbedActivity exact-class launch-dispatch contract changed: expected 2 "
            f"known checks, found {exact_class_count}. Review TvBraveActivity subclass behavior."
        )

    build_script = _read(repo_root / "scripts/build-debug.sh")
    args = _build_args(build_script)
    forbidden = [
        "enable_brave_ads=false",
        "enable_brave_rewards=false",
        "single-process",
        "process-per-site",
        "renderer-process-limit",
        "disable-site-isolation",
        "disable-site-isolation-trials",
        "no-sandbox",
        "disable-component-update",
    ]
    present = [value for value in forbidden if value in args]
    if present:
        raise ContractError("Unsafe Android build argument(s): " + ", ".join(present))

    print("Brave Android runtime contract: BraveActivity inheritance verified.")
    print("Brave Shields runtime contract: browser-process adblock + component updater verified.")
    print("Chromium subclass audit: 2 known exact-class launch dispatch checks; no Shields init dependency.")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("workspace", type=Path, help="Brave workspace containing src/brave")
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="Tihulu repository root",
    )
    args = parser.parse_args()
    try:
        verify(args.workspace, args.repo_root)
    except ContractError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
