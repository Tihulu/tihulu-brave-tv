#!/usr/bin/env python3
"""Add bounded runtime diagnostics to Brave Shields/adblock for TV debug builds.

This patch is diagnostic only: it does not change Shields preferences, filtering decisions,
component updater behavior, or request routing. It logs a small bounded set of lifecycle and
pre-work states so an ARM32 device can reveal exactly which layer is missing.
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

MARKER = "TIHULU_SHIELDS_RUNTIME_PROBE"


class ProbeError(RuntimeError):
    pass


def _replace_once(text: str, old: str, new: str, description: str) -> str:
    count = text.count(old)
    if count != 1:
        raise ProbeError(
            f"{description}: expected exactly one pinned Brave anchor, found {count}. "
            "Review the pinned Shields source before updating the probe."
        )
    return text.replace(old, new, 1)


def _atomic_write_text(path: Path, text: str) -> None:
    current = path.read_text(encoding="utf-8")
    if current == text:
        return
    tmp = path.with_name(path.name + ".tihulu-tmp")
    tmp.write_text(text, encoding="utf-8")
    os.replace(tmp, path)


def _validate_marker_pairs(text: str, names: list[str], description: str) -> bool:
    found_any = False
    for name in names:
        begin = f"{MARKER}_{name}_BEGIN"
        end = f"{MARKER}_{name}_END"
        begin_count = text.count(begin)
        end_count = text.count(end)
        if begin_count == 0 and end_count == 0:
            continue
        found_any = True
        if begin_count != 1 or end_count != 1 or text.index(end) < text.index(begin):
            raise ProbeError(
                f"Malformed {description} marker {name}: begin={begin_count}, end={end_count}"
            )
    return found_any


def transform_ad_block_service(text: str) -> tuple[str, bool]:
    marker_names = ["SERVICE", "DAT", "ENGINE"]
    if _validate_marker_pairs(text, marker_names, "AdBlockService"):
        required = [
            '"TIHULU_SHIELDS service-created"',
            '"TIHULU_SHIELDS default-dat success="',
            '"TIHULU_SHIELDS default-filter result="',
        ]
        missing = [token for token in required if token not in text]
        if missing:
            raise ProbeError("AdBlockService probe markers exist but body drifted: " + ", ".join(missing))
        return text, True

    if "TIHULU_SHIELDS " in text:
        raise ProbeError("AdBlockService contains unowned Tihulu Shields diagnostics")

    service_anchor = '  TRACE_EVENT("brave.adblock", "AdBlockService");\n'
    service_probe = (
        service_anchor
        + f"  // {MARKER}_SERVICE_BEGIN\n"
        + '  LOG(WARNING) << "TIHULU_SHIELDS service-created";\n'
        + f"  // {MARKER}_SERVICE_END\n"
    )
    text = _replace_once(text, service_anchor, service_probe, "AdBlockService constructor")

    dat_anchor = (
        "void AdBlockService::OnDATLoaded(bool is_default_engine, bool success) {\n"
        "  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);\n"
    )
    dat_probe = (
        dat_anchor
        + f"  // {MARKER}_DAT_BEGIN\n"
        + "  if (is_default_engine) {\n"
        + '    LOG(WARNING) << "TIHULU_SHIELDS default-dat success=" << success;\n'
        + "  }\n"
        + f"  // {MARKER}_DAT_END\n"
    )
    text = _replace_once(text, dat_anchor, dat_probe, "AdBlockService DAT callback")

    engine_anchor = (
        "  auto [load_result, serialized_dat] = std::move(result);\n\n"
    )
    engine_probe = (
        "  auto [load_result, serialized_dat] = std::move(result);\n"
        + f"  // {MARKER}_ENGINE_BEGIN\n"
        + "  if (is_default_engine && load_result != FilterListLoadResult::kResourcesOnly) {\n"
        + '    LOG(WARNING) << "TIHULU_SHIELDS default-filter result="\n'
        + "                 << static_cast<int>(load_result);\n"
        + "  }\n"
        + f"  // {MARKER}_ENGINE_END\n\n"
    )
    text = _replace_once(text, engine_anchor, engine_probe, "AdBlockService engine callback")

    if not _validate_marker_pairs(text, marker_names, "AdBlockService"):
        raise ProbeError("Internal error: AdBlockService probe was not applied")
    return text, True


def transform_network_helper(text: str) -> tuple[str, bool]:
    marker_names = ["PREWORK", "BLOCKED"]
    if _validate_marker_pairs(text, marker_names, "network helper"):
        required = [
            '"TIHULU_SHIELDS prework"',
            '"TIHULU_SHIELDS first-blocked-request"',
            "tihulu_probe_count < 8",
        ]
        missing = [token for token in required if token not in text]
        if missing:
            raise ProbeError("Network probe markers exist but body drifted: " + ", ".join(missing))
        return text, True

    if "TIHULU_SHIELDS " in text:
        raise ProbeError("Network helper contains unowned Tihulu Shields diagnostics")

    if '#include "base/logging.h"\n' not in text:
        text = _replace_once(
            text,
            '#include "base/functional/bind.h"\n',
            '#include "base/functional/bind.h"\n#include "base/logging.h"\n',
            "network helper logging include",
        )

    prework_anchor = (
        "int OnBeforeURLRequest_AdBlockTPPreWork(const ResponseCallback& next_callback,\n"
        "                                        T<BraveRequestInfo> ctx) {\n"
    )
    prework_probe = (
        prework_anchor
        + f"  // {MARKER}_PREWORK_BEGIN\n"
        + "  static int tihulu_probe_count = 0;\n"
        + "  if (tihulu_probe_count < 8) {\n"
        + "    ++tihulu_probe_count;\n"
        + '    LOG(WARNING) << "TIHULU_SHIELDS prework"\n'
        + '                 << " request_empty=" << ctx->request_url().is_empty()\n'
        + '                 << " initiator_empty=" << ctx->initiator_url().is_empty()\n'
        + '                 << " initiator_has_host=" << ctx->initiator_url().has_host()\n'
        + '                 << " allow_shields=" << ctx->allow_brave_shields()\n'
        + '                 << " allow_ads=" << ctx->allow_ads()\n'
        + '                 << " resource_type=" << static_cast<int>(ctx->resource_type());\n'
        + "  }\n"
        + f"  // {MARKER}_PREWORK_END\n"
    )
    text = _replace_once(text, prework_anchor, prework_probe, "adblock network pre-work")

    blocked_anchor = (
        "  if (ctx->blocked_by() == kAdBlocked) {\n"
        "    brave_shields::BraveShieldsWebContentsObserver::DispatchBlockedEvent(\n"
    )
    blocked_probe = (
        "  if (ctx->blocked_by() == kAdBlocked) {\n"
        + f"    // {MARKER}_BLOCKED_BEGIN\n"
        + "    static bool tihulu_logged_first_block = false;\n"
        + "    if (!tihulu_logged_first_block) {\n"
        + "      tihulu_logged_first_block = true;\n"
        + '      LOG(WARNING) << "TIHULU_SHIELDS first-blocked-request";\n'
        + "    }\n"
        + f"    // {MARKER}_BLOCKED_END\n"
        + "    brave_shields::BraveShieldsWebContentsObserver::DispatchBlockedEvent(\n"
    )
    text = _replace_once(text, blocked_anchor, blocked_probe, "adblock blocked-event probe")

    if not _validate_marker_pairs(text, marker_names, "network helper"):
        raise ProbeError("Internal error: network Shields probe was not applied")
    return text, True


def apply(project: Path) -> None:
    project = project.resolve()
    brave = project / "src/brave"
    service = brave / "components/brave_shields/content/browser/ad_block_service.cc"
    helper = brave / "browser/net/brave_ad_block_tp_network_delegate_helper.cc"
    for path in [service, helper]:
        if not path.is_file():
            raise ProbeError(f"Missing pinned Brave Shields source: {path}")

    service_text, _ = transform_ad_block_service(service.read_text(encoding="utf-8"))
    helper_text, _ = transform_network_helper(helper.read_text(encoding="utf-8"))
    _atomic_write_text(service, service_text)
    _atomic_write_text(helper, helper_text)
    print(
        "Brave Shields runtime probe: bounded pre-work, service, filter-engine and first-block diagnostics enabled for this build."
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("project", type=Path, help="Brave workspace root containing src/brave")
    args = parser.parse_args()
    try:
        apply(args.project)
    except ProbeError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
