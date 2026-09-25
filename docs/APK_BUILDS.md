# Native APK builds

The lightweight **Validate** workflow does not build an Android app. The separate
**Build native TV APK** workflow compiles the pinned Brave/Chromium engine with the
TV overlay, checks the APK signature and uploads installable development APKs.
No published APK is implied until that workflow has actually succeeded.

## Build machine

Register a GitHub Actions self-hosted runner for this repository from
**Settings → Actions → Runners → New self-hosted runner**. Use Linux x64 (Ubuntu
22.04/24.04 is the intended host) and add the custom label **tihulu-android**.
Use a dedicated build account/machine with at least 16 GB RAM, preferably 32 GB,
and an SSD with at least 200 GiB free before the initial checkout; 350–400 GiB
available is preferable for both architectures. The runner account needs sudo
for the existing dependency installers. A warm checkout still needs 60 GiB free.

The workflow only runs on manual dispatch, never on pull requests. Keep the runner
restricted to trusted maintainers. Do not use an ordinary small GitHub-hosted
runner for the Chromium build. No paid runner or cloud machine is provisioned by
this repository.

The source/dependency cache lives at `~/.cache/tihulu-tv/brave-browser`, outside the
Actions checkout so it survives runs. Set the repository Actions variable
`TIHULU_BUILD_WORKSPACE` to an absolute path to use another SSD. Both architectures
build sequentially against the same source tree; workflow concurrency prevents
two builds from mutating it together. Avoid a manual build using that checkout
while the workflow is running.

## Create and download APKs

Once this workflow is on the default branch:

1. Open **Actions → Build native TV APK → Run workflow**.
2. Select the branch and `arm`, `arm64` or `both`.
3. Wait for the full native build to finish. First builds can take hours.
4. Download `tihulu-tv-native-…` from the completed run's **Artifacts** section.
5. Extract the ZIP. Use `tihulu-tv-armeabi-v7a.apk` for ARM32 Android, or
   `tihulu-tv-arm64-v8a.apk` when the TV reports ARM64 support.

```bash
adb shell getprop ro.product.cpu.abilist
adb install -r tihulu-tv-armeabi-v7a.apk
```

Each APK has its own `.apk.sha256` and `.json` containing the exact overlay commit,
pinned Brave version and ABI. Verify the checksum after extraction:

```bash
sha256sum -c tihulu-tv-armeabi-v7a.apk.sha256
```

Artifacts expire after 30 days. These are development builds using upstream's
development signing configuration, not a production release channel. APKs are
not automatically added to Releases or offered by the in-app stable updater.
Production releases need a maintainer-owned persistent signing key and device
acceptance testing. If an existing installation has a different signature,
`adb install -r` will refuse the update; preserve/export needed data before making
any decision to uninstall it.

## Build directly on a suitable Linux machine

```bash
git clone --branch codex/tv-ui-low-memory https://github.com/Tihulu/tihulu-brave-tv.git
cd tihulu-brave-tv
./scripts/build-ci-apk.sh arm
# Optional, using the same checkout:
./scripts/build-ci-apk.sh arm64
```

Outputs go to `out/apk/`. The script refuses to package an old APK after a failed
or incomplete build, checks the embedded native ABI and APK ZIP integrity, and
runs the Android SDK's `apksigner verify`. Disk preflight runs before dependency
installation or large downloads. Keep the `.work` checkout to resume interrupted
builds.
