package org.chromium.chrome.browser.tabmodel;
import org.chromium.chrome.browser.tab.Tab;
public interface TabModel { int index(); int getCount(); Tab getTabAt(int i); void setIndex(int i,int type); }