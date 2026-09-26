package com.tihulu.youtubetv;
import android.app.Application;
public class TihuluTvApp extends Application {
 @Override public void onCreate(){ super.onCreate(); ExtractorBootstrap.init(); }
}
