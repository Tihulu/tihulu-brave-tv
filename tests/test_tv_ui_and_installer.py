import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class TvUiAndInstallerTests(unittest.TestCase):
    def test_tv_browser_bar_has_large_remote_actions_and_focus_feedback(self):
        text = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBrowserBar.java"
        ).read_text(encoding="utf-8")
        for label in ["← Back", "→ Forward", "↻ Reload", "Search / Address", "Tabs", "Menu", "✕ Close"]:
            self.assertIn(label, text)
        self.assertIn("dp(context, 64)", text)
        self.assertIn("setOnFocusChangeListener", text)
        self.assertIn("FOCUSED_BG", text)
        self.assertIn('button.setText(focused ? "▶ " + label + " ◀" : label);', text)
        self.assertNotIn("setScaleX", text)
        self.assertNotIn("setScaleY", text)
        self.assertIn("window.setGravity(Gravity.TOP);", text)
        self.assertIn("window.setDimAmount(0.0f);", text)

    def test_remote_can_open_close_bar_and_toggle_navigation_with_six_keys(self):
        activity = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBraveActivity.java"
        ).read_text(encoding="utf-8")
        bar = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBrowserBar.java"
        ).read_text(encoding="utf-8")
        self.assertIn("event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP", activity)
        self.assertIn("event.getRepeatCount() > 0", activity)
        self.assertIn("postShowBrowserBar();", activity)
        self.assertIn("toggleNavigationMode();", bar)
        self.assertIn("Mode button: Cursor/D-pad", bar)
        self.assertIn("keyCode == KeyEvent.KEYCODE_DPAD_DOWN", bar)
        self.assertIn("SELECT_LONG_PRESS_MS = 550L", activity)
        self.assertIn("mRoot.postDelayed(mSelectLongPressRunnable, SELECT_LONG_PRESS_MS);", activity)
        self.assertIn("mSelectLongPressConsumed", activity)
        self.assertIn("if (wasLongPress) return true;", activity)
        self.assertIn("toggleNavigationMode();", activity)

    def test_plain_dpad_path_is_throttled_and_cursor_hover_is_coalesced(self):
        activity = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBraveActivity.java"
        ).read_text(encoding="utf-8")
        self.assertIn("DPAD_REPEAT_DIVISOR = 3", activity)
        self.assertIn("event.getRepeatCount() % DPAD_REPEAT_DIVISOR != 0", activity)
        self.assertIn("CURSOR_REPEAT_ACCELERATION", activity)
        self.assertIn("CURSOR_MAX_ACCEL_REPEAT", activity)
        self.assertIn("moveCursorForKey(keyCode, event.getRepeatCount());", activity)
        self.assertIn("int boundedRepeat = Math.min(Math.max(repeatCount, 0)", activity)
        self.assertIn("float multiplier = 1.0f + boundedRepeat * CURSOR_REPEAT_ACCELERATION;", activity)
        self.assertIn("CURSOR_HOVER_MIN_INTERVAL_MS = 50L", activity)
        self.assertIn("mCursorHoverPosted", activity)
        self.assertIn("mRoot.postDelayed(mCursorHoverRunnable, CURSOR_HOVER_MIN_INTERVAL_MS - elapsed);", activity)
        self.assertIn("TvMouseDispatcher.hover(target, mPointerTargetX, mPointerTargetY);", activity)
        self.assertIn("TvMouseDispatcher.primaryClick(target, mPointerTargetX, mPointerTargetY);", activity)

    def test_cursor_targets_active_chromium_content_in_local_coordinates(self):
        activity = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBraveActivity.java"
        ).read_text(encoding="utf-8")
        mouse = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvMouseDispatcher.java"
        ).read_text(encoding="utf-8")
        self.assertIn("Tab tab = getActivityTab();", activity)
        self.assertIn("tab.getContentView()", activity)
        self.assertIn("contentView != null ? contentView : tab.getView()", activity)
        self.assertIn("getLocationInWindow", activity)
        self.assertIn("mapCursorToTarget(target)", activity)
        self.assertNotIn("TvMouseDispatcher.primaryClick(mRoot", activity)
        self.assertIn("MotionEvent.TOOL_TYPE_MOUSE", mouse)
        self.assertIn("InputDevice.SOURCE_MOUSE", mouse)
        self.assertIn("MotionEvent.ACTION_HOVER_MOVE", mouse)
        self.assertIn("MotionEvent.ACTION_BUTTON_PRESS", mouse)
        self.assertIn("MotionEvent.ACTION_BUTTON_RELEASE", mouse)
        self.assertIn("MotionEvent.BUTTON_PRIMARY", mouse)
        self.assertIn("event.setActionButton(actionButton);", mouse)

    def test_tv_mode_lookup_is_cached_out_of_remote_hot_path(self):
        activity = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBraveActivity.java"
        ).read_text(encoding="utf-8")
        dispatch = activity.split("public boolean dispatchKeyEvent(KeyEvent event)", 1)[1].split(
            "public TvNavigationMode navigationMode()", 1
        )[0]
        self.assertIn("if (!mTvRuntimeEnabled)", dispatch)
        self.assertNotIn("isTelevision()", dispatch)
        self.assertIn("mTvRuntimeEnabled = isTelevision();", activity)

    def test_cursor_overlay_and_runtime_ui_are_lazy_for_low_memory_tvs(self):
        activity = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBraveActivity.java"
        ).read_text(encoding="utf-8")
        startup = activity.split("public void performPostInflationStartup()", 1)[1].split(
            "public void onDestroyInternal()", 1
        )[0]
        for forbidden in [
            "addView(",
            "addOnLayoutChangeListener",
            "getFullscreenManager()",
            "ensureCursorInitialized();",
            "TvBrowserBar.show",
            "TvControlPanel.show",
            "postDelayed(",
        ]:
            self.assertNotIn(forbidden, startup)
        self.assertIn("ensureCursorInitialized();", activity)
        self.assertIn("private void ensureCursorInitialized()", activity)
        self.assertIn("private void installCursorLayoutListener()", activity)
        self.assertNotIn("initializeCursor();", activity)

    def test_low_memory_profile_uses_chromium_supported_mode(self):
        profile = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvMemoryProfile.java"
        ).read_text(encoding="utf-8")
        patcher = (ROOT / "scripts/apply_overlay.py").read_text(encoding="utf-8")
        about = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvAboutPanel.java"
        ).read_text(encoding="utf-8")
        self.assertIn("public final class TvMemoryProfile", profile)
        self.assertIn("public static void apply(Context context)", profile)
        self.assertIn('LOW_END_DEVICE_SWITCH = "enable-low-end-device-mode"', profile)
        self.assertIn("if (!Process.is64Bit()) return true;", profile)
        self.assertIn("manager.isLowRamDevice()", profile)
        self.assertIn("CommandLine.getInstance().appendSwitch(LOW_END_DEVICE_SWITCH);", profile)
        self.assertIn("TvMemoryProfile.apply(getApplication());", patcher)
        self.assertIn("TvMemoryProfile.runtimeLabel(context)", about)
        for unsafe in ["single-process", "process-per-site", "renderer-process-limit"]:
            self.assertNotIn(unsafe, profile)
            self.assertNotIn(unsafe, patcher)

    def test_branding_surfaces_are_wired(self):
        panel = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvControlPanel.java"
        ).read_text(encoding="utf-8")
        about = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvAboutPanel.java"
        ).read_text(encoding="utf-8")
        activity = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBraveActivity.java"
        ).read_text(encoding="utf-8")
        patcher = (ROOT / "scripts/apply_overlay.py").read_text(encoding="utf-8")
        self.assertIn('about.setText("About Tihulu TV Browser")', panel)
        self.assertIn('title.setText("Tihulu TV Browser")', about)
        self.assertIn('engine.setText("Based on Brave & Chromium")', about)
        self.assertIn("TvBuildInfo.BRAVE_VERSION", about)
        self.assertIn("TvBuildInfo.CHROMIUM_VERSION", about)
        self.assertIn("R.drawable.tihulu_tv_icon", about)
        self.assertIn(
            "TvAboutPanel.show(this, this::checkForUpdates, this::checkBraveUpstream);",
            activity,
        )
        self.assertIn('android:icon="@drawable/tihulu_tv_icon"', patcher)
        self.assertIn('android:banner="@drawable/tihulu_tv_banner"', patcher)
        self.assertIn('android:windowSoftInputMode="adjustResize"', patcher)
        self.assertTrue((ROOT / "assets/branding/tihulu_tv_icon.png").is_file())
        self.assertTrue((ROOT / "assets/branding/tihulu_tv_banner.png").is_file())

    def test_brave_upstream_check_is_read_only(self):
        about = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvAboutPanel.java"
        ).read_text(encoding="utf-8")
        activity = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBraveActivity.java"
        ).read_text(encoding="utf-8")
        checker = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBraveUpstream.java"
        ).read_text(encoding="utf-8")
        self.assertIn('brave.setText("Check Brave upstream")', about)
        self.assertIn("TvBraveUpstream.check(this, mRoot);", activity)
        self.assertIn("brave/brave-browser/releases/latest", checker)
        self.assertIn("Update safely through a newer Tihulu TV Browser APK", checker)
        self.assertNotIn("DownloadManager", checker)
        self.assertNotIn("ACTION_VIEW", checker)
        self.assertNotIn("REQUEST_INSTALL_PACKAGES", checker)

    def test_github_update_button_downloads_release_apk(self):
        panel = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvControlPanel.java"
        ).read_text(encoding="utf-8")
        about = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvAboutPanel.java"
        ).read_text(encoding="utf-8")
        activity = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvBraveActivity.java"
        ).read_text(encoding="utf-8")
        updater = (
            ROOT
            / "overlay/brave/android/java/org/chromium/chrome/browser/tv/TvGitHubUpdater.java"
        ).read_text(encoding="utf-8")
        patcher = (ROOT / "scripts/apply_overlay.py").read_text(encoding="utf-8")
        self.assertIn('update.setText("Check for Tihulu updates")', panel)
        self.assertIn('update.setText("Check for Tihulu updates")', about)
        self.assertIn("callback.checkForUpdates();", panel)
        self.assertIn("TvGitHubUpdater.checkAndInstall(this, mRoot);", activity)
        self.assertIn("/releases/latest", updater)
        self.assertIn("browser_download_url", updater)
        self.assertIn("DownloadManager.Request", updater)
        self.assertIn("FLAG_GRANT_READ_URI_PERMISSION", updater)
        self.assertIn("android.permission.REQUEST_INSTALL_PACKAGES", patcher)

    def test_one_line_builder_runs_full_dependency_chain(self):
        installer = (ROOT / "scripts/install-host-deps.sh").read_text(encoding="utf-8")
        builder = (ROOT / "scripts/build-apk-one-line.sh").read_text(encoding="utf-8")
        entrypoint = (ROOT / "install.sh").read_text(encoding="utf-8")
        self.assertIn('GIT_MIN="2.46.0"', installer)
        self.assertIn('GIT_FALLBACK_VERSION="2.54.0"', installer)
        self.assertIn(
            'GIT_FALLBACK_SHA256="f689162364c10de79ef89aa8dbf48731eb057e34edbbd20aca510ce0154681a3"',
            installer,
        )
        self.assertIn('NODE_MIN="24.16.0"', installer)
        self.assertIn('NODE_MAX="25.0.0"', installer)
        self.assertIn('PNPM_MIN="11.9.0"', installer)
        self.assertIn('npm install --prefix', installer)
        self.assertIn("latest-v24.x/SHASUMS256.txt", installer)
        self.assertGreaterEqual(installer.count("sha256sum --check --strict"), 2)
        self.assertNotIn("DEPOT_TOOLS_PYTHON_BYPASS", installer)
        self.assertIn("install-build-deps.sh", builder)
        self.assertIn("build-debug.sh", builder)
        self.assertIn("MEM_KB", builder)
        self.assertIn("git clone https://github.com/Tihulu/tihulu-brave-tv.git", entrypoint)

    def test_bootstrap_recovers_from_public_gclient_rate_limits(self):
        bootstrap = (ROOT / "scripts/bootstrap.sh").read_text(encoding="utf-8")
        self.assertIn('RECOVERY_JOBS="${BRAVE_GCLIENT_RECOVERY_JOBS:-8}"', bootstrap)
        self.assertIn('RECOVERY_ATTEMPTS="${BRAVE_GCLIENT_RECOVERY_ATTEMPTS:-5}"', bootstrap)
        self.assertIn("recover_gclient_sync()", bootstrap)
        self.assertIn('--jobs="$RECOVERY_JOBS"', bootstrap)
        self.assertIn('sleep "$delay"', bootstrap)
        self.assertIn("Preserving the existing Chromium checkout", bootstrap)
        self.assertNotIn("rm -rf \"$WORKSPACE\"", bootstrap)

    def test_bootstrap_does_not_repeat_deterministic_hook_failures(self):
        bootstrap = (ROOT / "scripts/bootstrap.sh").read_text(encoding="utf-8")
        self.assertIn("run_hooks_once_or_fail()", bootstrap)
        self.assertIn("will not be retried in a loop", bootstrap)
        self.assertIn('SYNC_MARKER="$WORKSPACE/.brave_latest_successful_sync.json"', bootstrap)
        self.assertIn("Detected an existing synced Brave/Chromium checkout; skipping full init.", bootstrap)
        self.assertIn("brave_sync_without_hooks()", bootstrap)

    def test_brave_hooks_use_isolated_python_with_brave_pythonpath(self):
        installer = (ROOT / "scripts/install-host-deps.sh").read_text(encoding="utf-8")
        bootstrap = (ROOT / "scripts/bootstrap.sh").read_text(encoding="utf-8")
        self.assertIn("python3-venv", installer)
        self.assertIn("ensure_python_env()", installer)
        self.assertIn('python_env="$TOOLS/python"', installer)
        self.assertIn('"$host_python" -m venv "$python_env"', installer)
        self.assertIn('HOOK_PYTHON="$ROOT/.tools/python/bin/python3"', bootstrap)
        self.assertIn("run_brave_hooks()", bootstrap)
        self.assertIn('brave_env="$BRAVE_CORE/build/env.sh"', bootstrap)
        self.assertIn('depot_tools="$BRAVE_CORE/vendor/depot_tools"', bootstrap)
        self.assertIn('gclient_py="$depot_tools/gclient.py"', bootstrap)
        self.assertIn('pnpm_run run sync --target_os=android --target_arch="$ARCH" --nohooks', bootstrap)
        self.assertIn('pnpm_run run init --target_os=android --target_arch="$ARCH" --nohooks', bootstrap)
        self.assertIn('source "$brave_env"', bootstrap)
        self.assertIn('export PATH="$ROOT/.tools/python/bin:$PATH"', bootstrap)
        self.assertIn("Brave hook PYTHONPATH is missing", bootstrap)
        self.assertIn("python3 -m pip --version", bootstrap)
        self.assertIn('"$HOOK_PYTHON" "$gclient_py" runhooks', bootstrap)
        self.assertNotIn("DEPOT_TOOLS_PYTHON_BYPASS", bootstrap)

    def test_generated_hook_environment_cannot_expand_comment_commands(self):
        installer = (ROOT / "scripts/install-host-deps.sh").read_text(encoding="utf-8")
        self.assertIn("cat <<'EOF_ENV'", installer)
        self.assertIn(
            "printf 'export PATH=\"%s/bin:%s/node24/bin:%s/python/bin:$PATH\"\\n'",
            installer,
        )
        self.assertNotIn('cat > "$ENV_FILE" <<EOF_ENV', installer)

    def test_optional_apt_packages_require_real_candidate(self):
        installer = (ROOT / "scripts/install-host-deps.sh").read_text(encoding="utf-8")
        self.assertIn("package_has_candidate()", installer)
        self.assertIn('LC_ALL=C apt-cache policy "$package"', installer)
        self.assertIn('"$candidate" != "(none)"', installer)
        self.assertIn("if package_has_candidate python-is-python3; then", installer)
        self.assertIn("if package_has_candidate python3-distutils; then", installer)
        self.assertNotIn("apt-cache show python3-distutils", installer)

    def test_host_installer_bootstraps_javac(self):
        installer = (ROOT / "scripts/install-host-deps.sh").read_text(encoding="utf-8")
        self.assertIn("if ! command -v javac", installer)
        self.assertIn("package_has_candidate openjdk-21-jdk-headless", installer)
        self.assertIn("package_has_candidate default-jdk-headless", installer)
        self.assertIn("JDK installation completed but javac is still unavailable", installer)
        self.assertIn('echo "Java: $(javac -version 2>&1)"', installer)


if __name__ == "__main__":
    unittest.main()
