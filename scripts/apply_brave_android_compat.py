#!/usr/bin/env python3
"""Apply narrow, fail-closed Brave Android compatibility fixes needed by the pinned build.

This script intentionally patches only verified upstream patterns. It is idempotent and
transactional: all affected files are validated before anything is written.
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

MARKER = "TIHULU_ANDROID_ADS_TOOLTIP_COMPAT"


class CompatError(RuntimeError):
    pass


def _atomic_write_text(path: Path, text: str) -> None:
    current = path.read_text(encoding="utf-8") if path.exists() else None
    if current == text:
        return
    tmp = path.with_name(path.name + ".tihulu-tmp")
    tmp.write_text(text, encoding="utf-8")
    os.replace(tmp, path)


def _replace_once(text: str, old: str, new: str, description: str) -> str:
    count = text.count(old)
    if count != 1:
        raise CompatError(
            f"{description}: expected exactly one upstream anchor, found {count}. "
            "Brave upstream changed; review before extending this compatibility patch."
        )
    return text.replace(old, new, 1)


def _marker_state(text: str, suffix: str) -> bool:
    begin = f"{MARKER}_{suffix}_BEGIN"
    end = f"{MARKER}_{suffix}_END"
    begin_count = text.count(begin)
    end_count = text.count(end)
    if begin_count == 0 and end_count == 0:
        return False
    if begin_count != 1 or end_count != 1 or text.index(end) < text.index(begin):
        raise CompatError(
            f"Malformed compatibility markers for {suffix}: "
            f"begin={begin_count}, end={end_count}"
        )
    return True


def _already_fixed(header: str, source: str) -> bool:
    return (
        "std::unique_ptr<AdsTooltipsDelegate> CreateAdsTooltipsDelegate() const;" in header
        and "std::unique_ptr<AdsTooltipsDelegate>\nAdsServiceFactory::CreateAdsTooltipsDelegate() const {" in source
        and 'brave/components/brave_ads/browser/tooltips/ads_tooltips_delegate.h' in source
    )


def transform(header: str, source: str, tooltips_build: str) -> tuple[str, str, bool]:
    header_marked = _marker_state(header, "HEADER")
    source_marked = _marker_state(source, "SOURCE")
    if header_marked != source_marked:
        raise CompatError("Brave Ads tooltip compatibility patch is only partially applied")

    if header_marked:
        if not _already_fixed(header, source):
            raise CompatError("Brave Ads tooltip compatibility markers exist but the patch body drifted")
        return header, source, True

    # Future Brave revisions may fix this upstream. Do not patch an already-correct tree.
    if _already_fixed(header, source):
        return header, source, False

    # This is the exact Android link hazard seen in Brave 1.94.x: the tooltip implementation
    # source_set is empty on Android, while AdsServiceFactory returns a concrete unique_ptr.
    # Destroying the returned temporary instantiates the concrete inline destructor and makes
    # the linker look for AdsTooltipsController::~AdsTooltipsController(), whose .cc is excluded.
    if "if (!is_android)" not in tooltips_build or '"ads_tooltips_controller.cc"' not in tooltips_build:
        raise CompatError(
            "Ads tooltip implementation is no longer excluded on Android; refusing to apply the old link workaround"
        )
    if "#if BUILDFLAG(IS_ANDROID)\n  return nullptr;\n#else" not in source:
        raise CompatError("AdsServiceFactory Android-null tooltip branch changed upstream")

    old_header_decl = "class AdsService;\nclass AdsTooltipsDelegateImpl;"
    new_header_decl = (
        f"class AdsService;\n"
        f"// {MARKER}_HEADER_BEGIN\n"
        f"class AdsTooltipsDelegate;\n"
        f"class AdsTooltipsDelegateImpl;\n"
        f"// {MARKER}_HEADER_END"
    )
    header = _replace_once(
        header,
        old_header_decl,
        new_header_decl,
        "AdsServiceFactory tooltip forward declarations",
    )
    header = _replace_once(
        header,
        "  std::unique_ptr<AdsTooltipsDelegateImpl> CreateAdsTooltipsDelegate() const;",
        "  std::unique_ptr<AdsTooltipsDelegate> CreateAdsTooltipsDelegate() const;",
        "AdsServiceFactory tooltip return type",
    )

    concrete_include = '#include "brave/browser/brave_ads/tooltips/ads_tooltips_delegate_impl.h"\n'
    interface_include = '#include "brave/components/brave_ads/browser/tooltips/ads_tooltips_delegate.h"\n'
    source = _replace_once(
        source,
        concrete_include,
        concrete_include
        + f"// {MARKER}_SOURCE_BEGIN\n"
        + interface_include
        + f"// {MARKER}_SOURCE_END\n",
        "AdsServiceFactory tooltip interface include",
    )
    source = _replace_once(
        source,
        "std::unique_ptr<AdsTooltipsDelegateImpl>\nAdsServiceFactory::CreateAdsTooltipsDelegate() const {",
        "std::unique_ptr<AdsTooltipsDelegate>\nAdsServiceFactory::CreateAdsTooltipsDelegate() const {",
        "AdsServiceFactory tooltip return definition",
    )

    if not _already_fixed(header, source):
        raise CompatError("Internal error: transformed Ads tooltip factory did not reach the safe interface-owned form")
    return header, source, True


def apply(project: Path) -> None:
    project = project.resolve()
    brave = project / "src/brave"
    header_path = brave / "browser/brave_ads/ads_service_factory.h"
    source_path = brave / "browser/brave_ads/ads_service_factory.cc"
    tooltips_build_path = brave / "browser/brave_ads/tooltips/BUILD.gn"

    required = [header_path, source_path, tooltips_build_path]
    missing = [str(path) for path in required if not path.is_file()]
    if missing:
        raise CompatError("Missing Brave Android compatibility input(s): " + ", ".join(missing))

    header = header_path.read_text(encoding="utf-8")
    source = source_path.read_text(encoding="utf-8")
    tooltips_build = tooltips_build_path.read_text(encoding="utf-8")

    new_header, new_source, needs_compat = transform(header, source, tooltips_build)

    # Write only after every source and invariant has been validated.
    _atomic_write_text(header_path, new_header)
    _atomic_write_text(source_path, new_source)

    if needs_compat:
        print("Brave Android compatibility: Ads tooltip factory uses interface ownership; excluded desktop tooltip implementation will not leak linker symbols.")
    else:
        print("Brave Android compatibility: upstream Ads tooltip factory is already safe; no patch needed.")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("project", type=Path, help="Brave workspace root containing src/brave")
    args = parser.parse_args()
    try:
        apply(args.project)
    except CompatError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
