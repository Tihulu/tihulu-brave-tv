# Tihulu TV Browser Lite experiment

This branch is a lightweight experiment that deliberately does **not** compile or bundle Brave/Chromium.

Instead it uses the Android System WebView already installed on the Android TV / Google TV device.

## Goal

Test whether Tihulu can deliver a practical TV-first browser with:

- a tiny normal Android build instead of a 100+ GB Chromium build workspace;
- compatibility with 32-bit ARM TV boxes because the APK itself contains no native browser engine;
- D-pad page navigation;
- a TV-sized address/search bar;
- back, forward and reload controls;
- a small built-in request blocker;
- low application overhead on 1-2 GB devices.

## Important trade-off

This is **not Brave** and does not include Brave Shields.

The experimental blocker currently performs host-based request blocking inside WebView. It is intentionally small and is only meant to prove the architecture. A production blocker would need a maintained filter-list engine, per-site controls, cosmetic filtering and much broader compatibility work.

The rendering/JavaScript engine is whatever Android System WebView version the TV vendor/device provides.

## Build locally

The experiment is a conventional Android Gradle project:

```bash
gradle :app:assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Build on GitHub

The branch contains a normal GitHub-hosted workflow:

```text
Actions -> Build WebView Lite APK
```

Unlike the full Brave branch, this build is expected to fit comfortably on the standard GitHub-hosted runner because no Brave/Chromium source checkout or native browser compilation is performed.

## Architecture

```text
Tihulu Java UI
      |
      +-- TV toolbar
      +-- D-pad spatial focus helper
      +-- lightweight host blocker
      |
Android WebView API
      |
System WebView / Chromium already installed on TV
```

The debug APK is architecture-independent at the application layer and is suitable for testing on 32-bit Android TV devices that provide a working System WebView implementation.


## 0.2 TV UI and blocker update

The Lite experiment now carries the low-end TV interaction fixes from the full Brave branch, adapted for WebView:

- no permanently attached browser toolbar; hold **Up** and release, or press **Menu / Info / Guide**, to open the top TV bar;
- high-contrast red focused actions with explicit `▶ ... ◀` markers and no scale animations;
- D-pad repeat throttling to avoid expensive focus scans on every Android repeat event;
- explicit **Mode: D-pad / Mode: Cursor** control instead of overloading long-OK;
- cursor movement acceleration without synthetic hover-event storms; OK sends the actual touch click;
- lightweight tab slots with previous/next/new/close controls;
- TV-sized address/search dialog and keyboard focus;
- fullscreen HTML5 video custom-view handling;
- lifecycle save/restore for tabs, current tab and navigation mode;
- a visible Shield counter and Ad blocker On/Off control.

### Ad blocking

Lite does not claim Brave Shields compatibility. It uses a WebView-specific blocker:

1. packaged host/filter rules are checked in `WebViewClient.shouldInterceptRequest`;
2. matching subresource requests are answered with an empty HTTP 204 response;
3. common ad containers that remain in the DOM are hidden with a small cosmetic stylesheet;
4. third-party cookies remain disabled by default.

Rules live in:

```text
app/src/main/assets/adblock_rules.txt
```

The list is intentionally compact for the Lite experiment and can be expanded or replaced with a maintained filter-list update system later.
