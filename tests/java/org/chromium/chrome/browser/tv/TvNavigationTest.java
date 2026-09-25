package org.chromium.chrome.browser.tv;

import android.content.Context;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModel;

public final class TvNavigationTest {
    private static final class Model implements TabModel {
        Tab[] tabs;
        int selected = -1;
        Model(Tab... tabs) { this.tabs = tabs; }
        public int index() { return selected; }
        public int getCount() { return tabs.length; }
        public Tab getTabAt(int index) { return tabs[index]; }
        public void setIndex(int index, int type) { selected = index; }
    }
    private static Tab tab(int id, boolean destroyed) {
        return new Tab() {
            @Override public int getId() { return id; }
            @Override public boolean isDestroyed() { return destroyed; }
        };
    }
    private static final class Target extends ViewGroup {
        MotionEvent last;
        int count;
        View focus;
        Target() { super(new Context()); }
        @Override public View findFocus() { return focus; }
        @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
            last = event;
            count++;
            return true;
        }
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = TvBraveActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    private static KeyEvent key(int action, int code, int repeats) {
        return new KeyEvent(0, 0, action, code, repeats, 0);
    }
    public static void main(String[] args) throws Exception {
        assert TvNavigationMode.DPAD.toggle() == TvNavigationMode.CURSOR;
        assert TvNavigationMode.CURSOR.toggle() == TvNavigationMode.SCROLL;
        assert TvNavigationMode.SCROLL.toggle() == TvNavigationMode.DPAD;
        assert TvNavigationMode.fromPreference(null) == TvNavigationMode.DPAD;
        assert TvNavigationMode.fromPreference("future-mode") == TvNavigationMode.DPAD;
        TvBraveActivity first = new TvBraveActivity();
        assert first.tabModel() == null : "early remote input must not access uninitialized tab model";
        first.setNavigationMode(TvNavigationMode.SCROLL);
        TvBraveActivity recreated = new TvBraveActivity();
        assert recreated.navigationMode() == TvNavigationMode.SCROLL : "restore after recreation";
        first.setNavigationMode(TvNavigationMode.CURSOR);
        assert new TvBraveActivity().navigationMode() == TvNavigationMode.CURSOR;
        first.getSharedPreferences("tihulu_tv", 0).edit().putString("navigation_mode", "future-mode").apply();
        assert new TvBraveActivity().navigationMode() == TvNavigationMode.DPAD;

        Target target = new Target();
        TvMouseDispatcher.scroll(target, 30, 40, 0, -2);
        assert target.last.action == MotionEvent.ACTION_SCROLL;
        assert target.last.source == InputDevice.SOURCE_MOUSE;
        assert target.last.x == 30 && target.last.y == 40;
        assert target.last.v == -2 && target.last.h == 0;
        assert target.last.recycled;

        TvBraveActivity activity = new TvBraveActivity();
        set(activity, "mRoot", target);
        set(activity, "mTvRuntimeEnabled", true);
        activity.setNavigationMode(TvNavigationMode.SCROLL);
        activity.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0));
        int count = target.count;
        assert target.last.v == -2;
        activity.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 1));
        assert target.count == count : "throttle repeated wheel events";
        activity.dispatchKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN, 0));
        assert target.count == count : "no wheel event on release";
        activity.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 0));
        assert target.last.v == 2 : "up must scroll instead of opening toolbar";
        activity.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0));
        assert target.last.h == 2 && target.last.v == 0;
        count = target.count;
        set(activity, "mHtmlFullscreen", true);
        activity.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0));
        assert target.count == count : "player owns fullscreen keys";
        set(activity, "mHtmlFullscreen", false);
        target.focus = new android.widget.EditText(new Context());
        activity.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0));
        assert target.count == count : "native text editing owns its keys";

        Model model = new Model(tab(20, false), tab(10, false));
        TvTabPanel.selectTab(model, model, 10);
        assert model.selected == 1 : "stable tab ID after reorder";
        model.selected = -1;
        TvTabPanel.selectTab(model, model, 30);
        assert model.selected == -1 : "closed tab must not select its old index";
        Model privateModel = new Model(tab(10, false));
        TvTabPanel.selectTab(privateModel, model, 10);
        assert privateModel.selected == -1 : "do not cross models";
        model.tabs = new Tab[] {tab(10, true)};
        TvTabPanel.selectTab(model, model, 10);
        assert model.selected == -1 : "ignore destroyed tabs";
        System.out.println("TV scroll, persistence and stable tab-selection tests passed.");
    }
}
