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
