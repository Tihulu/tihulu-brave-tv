package com.tihulu.youtubetv;
import static org.schabi.newpipe.extractor.ServiceList.YouTube;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.*;
import androidx.media3.ui.PlayerView;
import org.schabi.newpipe.extractor.stream.*;
import java.util.*;
import java.util.concurrent.*;

public final class PlayerActivity extends AppCompatActivity{
 private final ExecutorService io=Executors.newSingleThreadExecutor();private ExoPlayer player;private PlayerView pv;private ProgressBar progress;private TextView error;
 @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_player);pv=findViewById(R.id.playerView);progress=findViewById(R.id.progress);error=findViewById(R.id.error);player=new ExoPlayer.Builder(this).build();pv.setPlayer(player);pv.setControllerShowTimeoutMs(4000);pv.setControllerAutoShow(true);String url=getIntent().getStringExtra("url");if(url==null){finish();return;}extract(url);}
 @OptIn(markerClass=UnstableApi.class) private void extract(String page){progress.setVisibility(View.VISIBLE);io.execute(()->{try{StreamExtractor e=YouTube.getStreamExtractor(page);e.fetchPage();MediaSource s=source(e);runOnUiThread(()->{progress.setVisibility(View.GONE);player.setMediaSource(s);player.prepare();player.play();pv.requestFocus();});}catch(Exception ex){runOnUiThread(()->{progress.setVisibility(View.GONE);error.setText("Playback extraction failed · "+ex.getClass().getSimpleName());error.setVisibility(View.VISIBLE);});}});}
 @OptIn(markerClass=UnstableApi.class) private MediaSource source(StreamExtractor e)throws Exception{
  DefaultHttpDataSource.Factory d=new DefaultHttpDataSource.Factory().setUserAgent("Mozilla/5.0 (Linux; Android TV)");
  List<VideoStream> vo=e.getVideoOnlyStreams();List<AudioStream> au=e.getAudioStreams();
  if(vo!=null&&!vo.isEmpty()&&au!=null&&!au.isEmpty()){VideoStream v=best(vo);AudioStream a=au.get(0);return new MergingMediaSource(new ProgressiveMediaSource.Factory(d).createMediaSource(MediaItem.fromUri(v.getContent())),new ProgressiveMediaSource.Factory(d).createMediaSource(MediaItem.fromUri(a.getContent())));}
  List<VideoStream> m=e.getVideoStreams();if(m==null||m.isEmpty())throw new IllegalStateException("No playable stream");VideoStream v=best(m);return new ProgressiveMediaSource.Factory(d).createMediaSource(MediaItem.fromUri(v.getContent()));
 }
 private VideoStream best(List<VideoStream> s){VideoStream b=s.get(0);int bp=res(b.getResolution());for(VideoStream v:s){if(v.getContent()==null||v.getContent().isEmpty())continue;int p=res(v.getResolution());if(p>bp&&p<=2160){b=v;bp=p;}}return b;}
 private int res(String x){if(x==null)return 0;try{return Integer.parseInt(x.replaceAll("[^0-9]",""));}catch(Exception e){return 0;}}
 @Override protected void onDestroy(){io.shutdownNow();if(player!=null)player.release();super.onDestroy();}
}
