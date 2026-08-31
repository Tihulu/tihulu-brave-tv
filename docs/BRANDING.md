# Tihulu TV Browser branding

The application-facing name is **Tihulu TV Browser**.

The short technical attribution is:

> Based on Brave & Chromium

Tihulu TV Browser is an unofficial community derivative. It is not affiliated with or endorsed by Brave Software. Brave and Chromium trademarks, source code and upstream artwork remain the property of their respective owners.

## Production assets

### App icon

`assets/branding/tihulu_tv_icon.png`

- Square PNG.
- Used as the TV launcher's activity icon.
- Uses the Tihulu red / black / white visual identity with a browser-orbit / play motif.
- Do not replace it with the Brave lion or other official Brave branding for distributed Tihulu builds.

### Android TV / Google TV banner

`assets/branding/tihulu_tv_banner.png`

- 16:9 PNG.
- Used by the `LEANBACK_LAUNCHER` activity as its TV banner.
- Also suitable as the repository header image and a GitHub Release illustration.
- Includes the product name and the `Based on Brave & Chromium` attribution.

The overlay copies both PNGs into Chromium's `drawable-nodpi` resource directory and explicitly adds them to `chrome_java_resources.gni`.

## In-app branding

TV Controls includes **About Tihulu TV Browser**. The About dialog shows:

- the Tihulu app icon,
- `Tihulu TV Browser`,
- `Based on Brave & Chromium`,
- the unofficial-derivative notice,
- the existing **Check for updates** action.

This gives users a clear identity and upstream attribution without presenting the application as an official Brave release.

## Release usage

For GitHub Releases:

1. Use `assets/branding/tihulu_tv_banner.png` as the release image when an illustration is useful.
2. Name APK assets with the Tihulu product name and architecture, for example `Tihulu-TV-Browser-arm64.apk`.
3. Keep the release description explicit that this is an unofficial Brave/Chromium derivative.
4. Sign every update APK with the same release key before relying on the in-app updater.

See [`UPDATES.md`](UPDATES.md) for updater and signing requirements.
