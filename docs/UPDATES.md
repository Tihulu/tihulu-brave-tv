# In-app updates

Tihulu TV Browser has two deliberately separate update checks:

- **Check for Tihulu updates** downloads a packaged Tihulu TV Browser APK from this repository's GitHub Releases and opens Android's package installer.
- **Check Brave upstream** is read-only. It checks Brave's latest public stable release and compares it with the Brave version compiled into the current APK. It never replaces Brave/Chromium files in place.

The About panel also shows the Brave and Chromium versions captured from `src/brave/package.json` when the TV overlay is applied.

## Safe engine-update model

Brave and Chromium are compiled into the Android application. Updating individual browser-engine files on a running TV installation would be fragile and is intentionally not supported.

When Brave needs to be updated, the supported flow is:

1. Update/sync the Brave checkout on the build PC.
2. Reapply the Tihulu TV overlay. The patcher records the new Brave and Chromium versions in the APK and fails closed if important upstream anchors moved.
3. Run the validation suite and a full Brave Android APK build.
4. Smoke-test the packaged APK on real Google TV hardware.
5. Publish that tested APK as a new `Tihulu/tihulu-brave-tv` GitHub Release.
6. On the TV, use **Check for Tihulu updates**. Installing the new full APK updates the Tihulu TV layer, Brave and Chromium together.

This means a newer Tihulu release can carry both project changes and a newer Brave/Chromium engine without introducing a second unsafe in-place engine updater.

## Tihulu APK update flow

1. The app queries the public GitHub API endpoint for the latest non-draft release of `Tihulu/tihulu-brave-tv`.
2. It selects an APK asset that matches the TV CPU architecture. If the release contains only one APK, that asset is used as the fallback.
3. Android Download Manager downloads the APK over HTTPS.
4. When the download completes, the app opens Android's package installer.
5. Android asks the user to confirm the update. The project does not attempt silent installation.

If Android requires it, the user must allow Tihulu TV Browser to install unknown apps from this source.

## Brave upstream check

The read-only checker queries `brave/brave-browser`'s latest public GitHub Release. It reports one of three states:

- A newer public stable Brave version exists: build and publish a newer Tihulu TV Browser APK before updating the TV.
- The compiled Brave version matches public stable.
- The compiled Brave version is newer than public stable, which can happen when a development/newer Brave checkout was used.

The checker does not use `DownloadManager`, does not request package-install permission and does not launch an installer.

## Release asset naming

Architecture-specific APK names should include a recognizable architecture token, for example:

- `Tihulu-TV-Browser-arm64.apk`
- `Tihulu-TV-Browser-arm.apk`
- `Tihulu-TV-Browser-x64.apk`
- `Tihulu-TV-Browser-x86.apk`

A release containing exactly one APK may use a generic name such as `Tihulu-TV-Browser.apk`.

The updater only accepts APK download URLs returned by GitHub Releases and rejects arbitrary non-GitHub URLs.

For useful release notes, record at least:

```text
Tihulu TV Browser: vX.Y.Z
Brave: X.Y.Z
Chromium: X.Y.Z.W
```

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

The Tihulu updater, Brave upstream checker, build-time Brave/Chromium metadata and CI surface tests are implemented. A real packaged GitHub Release does not yet exist, so the Tihulu update button will report that no packaged release is available until a release with an APK asset is published.

A real Google TV update test remains required before release status should be considered stable.

## Play Store note

The Tihulu GitHub updater uses Android's `REQUEST_INSTALL_PACKAGES` permission because it installs APKs obtained outside an app store. If the project is later distributed through Google Play, review the then-current Play policy before keeping this permission or the GitHub self-update path in that distribution channel.
