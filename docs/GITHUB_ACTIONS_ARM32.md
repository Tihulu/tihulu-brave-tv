# GitHub Actions ARM32 APK build

Tihulu TV Browser can build its 32-bit ARM Android TV APK from the GitHub Actions UI.

The workflow is:

```text
Actions -> Build ARM32 APK -> Run workflow
                         |
                         v
              self-hosted Linux x64 runner
                         |
                         v
              Brave/Chromium ARM32 build
                         |
                         v
        Tihulu-TV-Browser-arm32-debug.apk
                         |
                         v
              GitHub Actions artifact
```

## Why this uses a self-hosted runner

A full Brave/Chromium Android checkout and build is much larger than a standard GitHub-hosted runner disk. The ARM32 workflow therefore targets a self-hosted Linux x64 runner with the custom label:

```text
tihulu-brave-build
```

The build machine can still be a normal 64-bit Linux PC. The generated APK targets 32-bit ARM / `armeabi-v7a`.

## 1. Prepare the build machine

Recommended baseline:

- Ubuntu 24.04, Pop!_OS 24.04, or a compatible Debian-family Linux
- x86_64 host
- at least 16 GB RAM
- roughly 200 GB free disk for a fresh Brave/Chromium checkout and build
- outbound internet access to GitHub and Brave/Chromium dependency servers
- `sudo` access for the account running the GitHub runner

The repository build script installs/updates the host dependencies it needs.

## 2. Add the GitHub self-hosted runner

Open the repository on GitHub:

```text
Settings -> Actions -> Runners -> New self-hosted runner
```

Choose **Linux** and **x64**, then run the exact registration commands GitHub shows on the build PC.

Add the custom runner label:

```text
tihulu-brave-build
```

The finished runner should therefore match all of these labels:

```text
self-hosted
linux
x64
tihulu-brave-build
```

The ARM32 workflow is manual-only (`workflow_dispatch`), so normal pull requests continue to use the lightweight GitHub-hosted validation workflow instead of the large build machine.

## 3. Optional: put Brave/Chromium on a large disk

By default the persistent checkout is stored at:

```text
~/.cache/tihulu-brave-tv/brave-browser
```

If the large disk is mounted somewhere else, create a GitHub Actions repository variable:

```text
Settings -> Secrets and variables -> Actions -> Variables
```

Variable name:

```text
BRAVE_TV_WORKSPACE
```

Example value:

```text
/mnt/builddisk/tihulu-brave/brave-browser
```

Do not point this variable inside the checked-out Tihulu repository. The workflow intentionally keeps Brave/Chromium outside `GITHUB_WORKSPACE` so later `actions/checkout` runs cannot delete the very large reusable checkout.

## 4. Run the ARM32 build

Open:

```text
Actions -> Build ARM32 APK -> Run workflow
```

The workflow:

1. checks out Tihulu TV Browser;
2. selects the persistent Brave workspace;
3. checks available disk space;
4. runs `./scripts/build-apk-one-line.sh arm`;
5. finds the newest APK containing `armeabi-v7a`;
6. rejects the artifact if another native ABI is unexpectedly packaged;
7. generates a SHA-256 checksum;
8. uploads both files to the workflow run.

The expected APK filename is:

```text
Tihulu-TV-Browser-arm32-debug.apk
```

The GitHub artifact is named:

```text
Tihulu-TV-Browser-arm32-<run number>
```

Artifacts are retained for 7 days by this workflow.

## 5. Rebuilds reuse the large checkout

The Brave/Chromium source tree lives outside the normal GitHub Actions repository checkout. As long as the self-hosted build machine and that directory remain intact, future runs reuse the existing checkout and incremental build outputs.

Do not routinely delete the persistent Brave workspace after a failed build. Diagnose the first error and rerun the workflow; the existing source and completed build work can usually be reused.

## Output verification

Before upload, the workflow inspects the APK ZIP and requires:

```text
armeabi-v7a
```

It rejects an APK that lacks ARM32 native libraries or unexpectedly includes another native ABI. A SHA-256 checksum is uploaded next to the APK.

This produces a debug APK for testing on 32-bit Android TV / Google TV hardware. Release signing and store packaging remain separate release tasks.
