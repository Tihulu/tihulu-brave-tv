#!/usr/bin/env python3
"""Apply an ephemeral Chromium UA focus-ring patch for TV D-pad navigation.

The patch is intentionally build-time only. build-debug.sh restores Chromium's tracked
source after the APK has been produced so the large checkout stays clean between runs.
"""

from __future__ import annotations

import argparse
from pathlib import Path

BEGIN = "/* TIHULU_TV_FOCUS_RING_COMPAT_BEGIN */"
END = "/* TIHULU_TV_FOCUS_RING_COMPAT_END */"
REL = Path("src/third_party/blink/renderer/core/html/resources/html.css")

ORIGINAL_FOCUS = ":focus-visible {\n    outline: auto 1px -webkit-focus-ring-color\n}"
PATCHED_FOCUS = (
    f"{BEGIN}\n"
    ":focus-visible {\n"
    "    outline: solid 4px rgb(255, 45, 85) !important;\n"
    "    outline-offset: 3px !important;\n"
    "}\n"
    f"{END}"
)

ORIGINAL_ROOT_EXCLUSION = (
    "html:focus-visible, body:focus-visible {\n"
    "    outline: none\n"
    "}"
)
PATCHED_ROOT_EXCLUSION = (
    "html:focus-visible, body:focus-visible {\n"
    "    outline: none !important;\n"
    "}"
)

ORIGINAL_EMBED_EXCLUSION = (
    "embed:focus-visible, iframe:focus-visible, object:focus-visible {\n"
    "    outline: none\n"
    "}"
)
PATCHED_EMBED_EXCLUSION = (
    "embed:focus-visible, iframe:focus-visible, object:focus-visible {\n"
    "    outline: none !important;\n"
    "}"
)


class PatchError(RuntimeError):
    pass


def transform(text: str) -> str:
    begin_count = text.count(BEGIN)
    end_count = text.count(END)
    if begin_count != end_count:
        raise PatchError(
            f"partial focus-ring compatibility marker: begin={begin_count}, end={end_count}"
        )

    if begin_count == 1:
        if text.count(PATCHED_FOCUS) != 1:
            raise PatchError("focus-ring marker exists but its owned CSS changed")
        if text.count(PATCHED_ROOT_EXCLUSION) != 1:
            raise PatchError("root focus exclusion is not the expected patched form")
        if text.count(PATCHED_EMBED_EXCLUSION) != 1:
            raise PatchError("embedded-content focus exclusion is not the expected patched form")
        return text
    if begin_count != 0:
        raise PatchError(f"expected at most one focus-ring marker pair, found {begin_count}")

    anchors = [
        (ORIGINAL_FOCUS, "Chromium :focus-visible UA rule"),
        (ORIGINAL_ROOT_EXCLUSION, "Chromium html/body focus exclusion"),
        (ORIGINAL_EMBED_EXCLUSION, "Chromium embed/iframe/object focus exclusion"),
    ]
    for anchor, description in anchors:
        count = text.count(anchor)
        if count != 1:
            raise PatchError(
                f"{description}: expected exactly one upstream anchor, found {count}; "
                "Chromium probably changed, review before updating the patch"
            )

    text = text.replace(ORIGINAL_FOCUS, PATCHED_FOCUS, 1)
    text = text.replace(ORIGINAL_ROOT_EXCLUSION, PATCHED_ROOT_EXCLUSION, 1)
    text = text.replace(ORIGINAL_EMBED_EXCLUSION, PATCHED_EMBED_EXCLUSION, 1)
    return text


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("workspace", type=Path)
    args = parser.parse_args()
    workspace = args.workspace.resolve()
    path = workspace / REL
    if not path.is_file():
        raise PatchError(f"missing Chromium UA stylesheet: {path}")

    original = path.read_text(encoding="utf-8")
    patched = transform(original)
    if patched != original:
        path.write_text(patched, encoding="utf-8")

    print(
        "Chromium TV focus compatibility: D-pad focus uses a 4px high-contrast UA outline; "
        "no per-key JavaScript injection."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PatchError as exc:
        print(f"ERROR: {exc}", file=__import__("sys").stderr)
        raise SystemExit(2)
