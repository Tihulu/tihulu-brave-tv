# In-app GitHub updates

Tihulu TV Browser includes a **Check for updates** action in TV Controls.

## User flow

1. The app queries the public GitHub API endpoint for the latest non-draft release of `Tihulu/tihulu-brave-tv`.
2. It selects an APK asset that matches the TV CPU architecture. If the release contains only one APK, that asset is used as the fallback.
3. Android Download Manager downloads the APK over HTTPS.
4. When the download completes, the app opens Android's package installer.
5. Android asks the user to confirm the update. The project does not attempt silent installation.

If Android requires it, the user must allow Tihulu TV Browser to install unknown apps from this source.

## Release asset naming

Architecture-specific APK names should include a recognizable architecture token, for example:

- `Tihulu-TV-Browser-arm64.apk`
- `Tihulu-TV-Browser-arm.apk`
- `Tihulu-TV-Browser-x64.apk`
- `Tihulu-TV-Browser-x86.apk`

A release containing exactly one APK may use a generic name such as `Tihulu-TV-Browser.apk`.

The updater only accepts APK download URLs returned by GitHub Releases and rejects arbitrary non-GitHub URLs.

## Signing is mandatory for real updates

Android only permits an APK to replace an installed application when the package identity and signing certificate are compatible with the installed build.

That means public update releases need a stable release signing key. A debug APK built on one developer machine normally uses that machine's debug key, so an independently signed GitHub release may not be able to update it in place.

Before calling the updater production-ready:

1. Establish the final application ID / package identity.
2. Establish a protected long-lived release signing key.
3. Sign every published update with the same key.
4. Install one release-signed build on a real Google TV.
5. Publish a newer release-signed APK and verify the in-app update path end-to-end.

Do not commit private signing keys, keystores or signing passwords to this repository.

## Current development status

The updater code and CI surface tests are implemented. A real packaged GitHub Release does not yet exist, so the current button will report that no packaged release is available until a release with an APK asset is published.

A real Google TV update test remains required before release status should be considered stable.

## Play Store note

The updater uses Android's `REQUEST_INSTALL_PACKAGES` permission because it installs APKs obtained outside an app store. If the project is later distributed through Google Play, review the then-current Play policy before keeping this permission or the GitHub self-update path in that distribution channel.
