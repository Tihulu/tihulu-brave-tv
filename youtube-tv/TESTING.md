# Validation record

## Completed before the first build

- Inspected the pinned SmartTube native player, controls, metadata and preview paths.
- Verified the patch applies cleanly to the exact pinned commit and submodule revisions.
- Parsed Python build tooling and XML resources.

## Build checks

The GitHub Actions result is authoritative for compilation and signing. Results will be recorded after the build completes.

## Target-TV acceptance checks

1. Install the ARM32 APK on a 2 GB Mi Box/Google TV device. Open Settings → About and confirm **Lite**.
2. Navigate Home, Subscriptions, Search, Settings and Back entirely with D-pad. Confirm the focus ring and sidebar selection remain visible.
3. Type into Search using the system TV keyboard; verify arrows remain within the keyboard until dismissed.
4. Play several public videos while signed out, then repeat with device-code sign-in. Include pre-roll candidates, a video longer than ten minutes, a live stream and seek over a possible mid-roll point. Record any advertisement or playback error.
5. Open and dismiss captions/quality, pause/resume, seek both ways, leave the player and return. Confirm audio focus and screen sleep behavior.
6. Play 1080p video for 30 minutes and repeatedly switch videos. Capture `adb shell dumpsys meminfo com.tihulu.tube`, codec logs and `adb logcat`. No process kill, ANR, steadily increasing PSS or background audio after exit.
7. Repeat install/launch/playback on a native ARM64 Android TV device. An emulator does not validate ARM codecs or vendor memory behavior.

UI smoke screenshots are useful for layout/navigation checks but are not proof of successful playback or ad blocking.
