# Tihulu Tube

A dedicated native YouTube player for Google TV and Android TV, built from pinned SmartTube 32.56 source with a Tihulu interface and explicit memory budgets. This is independent of the browser project in the rest of this repository. Package: `com.tihulu.tube`. Android 6.0 or newer.

## Builds

The **Build Tihulu Tube** GitHub Actions workflow produces two signed preview APKs:

| APK | Target |
| --- | --- |
| `TihuluTube-0.1.0-arm32-preview.apk` | `armeabi-v7a`; Lite profile, including 2 GB TV boxes |
| `TihuluTube-0.1.0-arm64-preview.apk` | `arm64-v8a`; automatically uses Lite on low-memory TVs |

Use ARM32 when your TV runs a 32-bit Android OS, even if its processor supports 64-bit instructions. The APKs have the same application ID; install the appropriate one, not both. Preview builds are debug signed; their signing key is retained in the workflow cache. A cache eviction can require reinstalling a subsequent preview. Production release signing is not configured.

## Interface and controls

- Dark slate surfaces, rounded thumbnail cards, high-contrast focus outline, pill-shaped sidebar destinations, and a floating playback panel.
- Native D-pad browsing and playback, YouTube account pairing, search, captions, quality selection, and viewing history inherited from SmartTube.
- Android TV's system keyboard handles text entry. No browser cursor or separate text-input workaround.
- Original Tihulu vector branding and a separate package allow coexistence with SmartTube and the browser.

## Ad handling

Brave combines network filtering with cosmetic rules, resource replacements and scriptlet injection. Its request blocker alone is not the complete YouTube blocking system.

This app instead uses SmartTube's native content-stream playback pipeline. It does not embed the YouTube website or schedule the website's advertising player. SponsorBlock is a separate optional feature for creator-embedded sponsorships, configured in Settings. This is not a port of Brave Shields. YouTube can change its playback endpoints and invalidate extraction; maintain the pinned upstream revision and rebuild when fixes are required. Live playback, account access and ad absence must be verified on the target TV/network. Building successfully does not prove them.

## Memory profile

Every 32-bit process uses Lite. The ARM64 process also uses Lite when Android reports low RAM, total RAM is unknown, or reported RAM is at most 2304 MiB.

| Allocation/default | Lite | Standard |
| --- | --- | --- |
| Video allocation target | 24 MiB | 64 MiB |
| Thumbnail memory cache | 12 MiB | 32 MiB |
| Bitmap pool | 4 MiB | 8 MiB |
| Array pool | 2 MiB | 4 MiB |
| Thumbnail disk cache | 48 MiB | 96 MiB |
| Initial video preference | 1080p AVC, 30 fps | Device-aware upstream preference |
| Animated thumbnail previews | Disabled | User setting |
| Back buffer | Disabled | Disabled |

Saved quality choices take precedence. No `largeHeap` request. The video target is an allocator threshold, not a hard process limit; codecs, graphics, JavaScript extraction and native libraries consume additional memory. A 2 GB hardware playback/soak test remains necessary before claiming a measured memory guarantee.

## Rebuild

Requirements: JDK 17, Android SDK platform 34 and build tools, Python 3, Git. Gradle is included upstream. Gradle may install the declared NDK for library stripping.

```bash
git clone --recurse-submodules https://github.com/yuliskov/SmartTube.git .work/tihulu-tube
git -C .work/tihulu-tube checkout 9336539b3db340c7bcf55f95afad44d1d1abb7e4
git -C .work/tihulu-tube submodule update --init --recursive
python3 youtube-tv/prepare.py .work/tihulu-tube
cd .work/tihulu-tube
./gradlew --no-daemon --max-workers=2 :smarttubetv:assembleStfdroidDebug
```

`prepare.py` verifies the source/submodule pins and refuses to overwrite tracked modifications. The upstream `stfdroid` flavor is used to omit Firebase; the output is Tihulu Tube, not an F-Droid publication. Automatic upstream APK installation is disabled because it would be the wrong package/signature. About links to this project's source and builds.

## Validation

The workflow runs boundary tests for memory classification, compiles all native app modules, verifies the two APK signatures and their separate ABIs, and checks identical native-library coverage. See `TESTING.md` for on-device checks and results.

## Credits and research

Source and copyright notices are preserved in the pinned upstream and its dependencies. Tihulu modifications follow this repository's AGPL-3.0 license. SmartTube's root license is MIT; dependencies retain their respective licenses. Vector lettering uses DejaVu Sans, whose license permits reproducing text in artwork.

- [SmartTube source and license](https://github.com/yuliskov/SmartTube)
- [Brave adblock-rust](https://github.com/brave/adblock-rust)
- [YouTube's TV interface announcement](https://blog.youtube/news-and-events/happy-birthday-youtube-20/)
- [YouTube TV discovery and shows](https://blog.youtube/news-and-events/new-features-to-help-creators/)
- [Android TV memory guidance](https://developer.android.com/training/tv/playback/memory)
