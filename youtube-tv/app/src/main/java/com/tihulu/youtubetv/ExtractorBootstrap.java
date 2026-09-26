package com.tihulu.youtubetv;
import org.schabi.newpipe.extractor.NewPipe;
import java.util.concurrent.atomic.AtomicBoolean;
public final class ExtractorBootstrap {
 private static final AtomicBoolean I=new AtomicBoolean(false);
 private ExtractorBootstrap(){}
 public static void init(){ if(I.compareAndSet(false,true)) NewPipe.init(NewPipeDownloader.getInstance()); }
}
