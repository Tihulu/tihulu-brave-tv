package org.chromium.chrome.browser.tv;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Process;
import android.widget.Button;

public final class TvRuntimePolicyTest {
    public static void main(String[] args) {
        final ActivityManager manager = new ActivityManager();
        Context context = new Context() {
            @Override public Object getSystemService(String name) { return manager; }
        };
        Process.bit64 = true;
        manager.memory = 2L * 1024 * 1024 * 1024;
        assert TvMemoryProfile.shouldUseLowMemoryProfile(context) : "2 GB without vendor low_ram flag";
        manager.memory -= 256L * 1024 * 1024;
        assert TvMemoryProfile.shouldUseLowMemoryProfile(context) : "GPU reserved RAM";
        manager.memory = 4L * 1024 * 1024 * 1024;
        assert !TvMemoryProfile.shouldUseLowMemoryProfile(context) : "4 GB standard";
        manager.lowRam = true;
        assert TvMemoryProfile.shouldUseLowMemoryProfile(context) : "vendor override";
        manager.lowRam = false;
        manager.memory = 0;
        assert !TvMemoryProfile.shouldUseLowMemoryProfile(context) : "unknown RAM";
        Process.bit64 = false;
        assert TvMemoryProfile.shouldUseLowMemoryProfile(null) : "32 bit always conservative";
        Process.bit64 = true;
        assert !TvMemoryProfile.shouldUseLowMemoryProfile(null);
        Button mode = TvUi.button(context, "Mode: D-pad", () -> {});
        mode.focusListener.onFocusChange(mode, true);
        mode.setText("Mode: Cursor");
        mode.focusListener.onFocusChange(mode, false);
        mode.focusListener.onFocusChange(mode, true);
        assert mode.getText().toString().equals("Mode: Cursor") : "focus must preserve changed label";
        System.out.println("TV memory and dynamic focus regression tests passed.");
    }
}
