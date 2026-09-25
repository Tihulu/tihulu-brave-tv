# Tihulu YouTube TV

A TV-first, unofficial YouTube client for Android TV / Google TV.

## Design goals

- Native D-pad navigation rather than a browser/WebView shell.
- A 10-foot interface inspired by the current YouTube TV information architecture: left navigation, horizontal content shelves, search, and a full-screen watch experience.
- Direct media playback with Media3/ExoPlayer.
- Two installable APK variants:
  - **arm32** — `armeabi-v7a`, low-memory profile for 2 GB devices.
  - **arm64** — `arm64-v8a`, higher quality ceiling and larger caches.
- Anonymous YouTube discovery and search without requiring a Google API key.

## Ad blocking approach

This app does **not** render the YouTube website. It resolves the actual media streams with NewPipe Extractor and sends those streams directly to Media3. Because the web player/ad pipeline is skipped, ordinary pre-roll/mid-roll ad requests from the YouTube web/TV client are not part of the playback path.

The extractor downloader also rejects well-known ad/tracker hosts such as DoubleClick and Google ad-service endpoints. It deliberately does **not** block `youtube.com` or `googlevideo.com`, because those are required for search, metadata, and video playback.

This is conceptually different from Brave's browser-level filtering. Brave's native adblock engine is a useful reference for network filtering, but a TV video client is more reliable when the playback path itself never embeds the YouTube web player.

> YouTube can change extraction or ad-delivery behavior at any time. NewPipe Extractor updates may therefore be required to keep playback working.

## 2 GB / ARM32 profile

The ARM32 flavor uses:
- smaller Coil memory cache;
- fewer items retained per home shelf;
- a 20 MB ExoPlayer target buffer;
- muxed audio/video streams when available to avoid an extra adaptive media pipeline;
- a 1080p ceiling;
- no expensive blur/backdrop effects.

The ARM64 flavor may use separate video + audio streams for higher quality and allows up to 2160p when the extractor exposes a compatible stream.

## Build

GitHub Actions builds both installable debug APKs automatically. Locally:

```bash
cd youtube-tv
gradle :app:assembleArm32Debug :app:assembleArm64Debug
```

Outputs:

```text
app/build/outputs/apk/arm32/debug/app-arm32-debug.apk
app/build/outputs/apk/arm64/debug/app-arm64-debug.apk
```

## Current scope

Implemented in the first native slice:
- Home shelves for Live, Gaming, and Music;
- YouTube video search;
- D-pad focus states;
- full-screen Media3 player controls;
- direct-stream ad avoidance;
- automatic 32-bit/64-bit build artifacts.

Planned next:
- subscriptions imported locally;
- local history/bookmarks;
- quality/audio/subtitle selector;
- SponsorBlock support as a separate optional feature;
- device-code/account integration only if it can be implemented without relying on private Google credentials.

## Licensing

This subproject is GPL-compatible and uses NewPipe Extractor, which is GPL-3.0-or-later. It is not affiliated with or endorsed by YouTube or Google.
