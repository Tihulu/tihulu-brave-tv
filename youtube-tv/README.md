# Tihulu YouTube TV

TV-first unofficial YouTube client for Google TV / Android TV.

## Architecture
The app does not embed YouTube's ad-enabled web player. It resolves media streams locally with NewPipeExtractor and plays them with AndroidX Media3. This is more reliable for video-ad avoidance than trying to filter YouTube requests inside WebView, and it avoids Chromium/WebView RAM overhead on 2 GB boxes.

Brave's adblock-rust remains relevant for a future embedded-web fallback; Brave documents network blocking, cosmetic filtering and uBlock-style syntax support.

## Current alpha
- Home feeds: Live, Music, Gaming, Podcasts
- Search
- D-pad focus states
- Full-screen Media3 playback
- Separate armeabi-v7a and arm64-v8a builds
- Release shrinking enabled

## Build
```
gradle :app:assembleArm32Release
gradle :app:assembleArm64Release
```

## Limitations
This is unofficial and YouTube extraction can break when YouTube changes its player/API behavior. NewPipeExtractor should be kept current. Anonymous access can also hit YouTube anti-bot challenges. Account sign-in, subscriptions, recommendations, captions and SponsorBlock are follow-up work.
