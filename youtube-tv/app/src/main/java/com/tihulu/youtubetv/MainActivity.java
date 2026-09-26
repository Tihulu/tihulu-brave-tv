package com.tihulu.youtubetv;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends AppCompatActivity{
 private final ExecutorService io=Executors.newSingleThreadExecutor();private VideoAdapter adapter;private ProgressBar progress;private TextView title;
 @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_main);progress=findViewById(R.id.progress);title=findViewById(R.id.sectionTitle);
  RecyclerView row=findViewById(R.id.videoRow);row.setLayoutManager(new LinearLayoutManager(this,RecyclerView.HORIZONTAL,false));adapter=new VideoAdapter(this::play);row.setAdapter(adapter);
  findViewById(R.id.searchButton).setOnClickListener(v->startActivity(new Intent(this,SearchActivity.class)));
  bind(R.id.liveButton,"live","Live now");bind(R.id.musicButton,"trending_music","Music");bind(R.id.gamingButton,"trending_gaming","Gaming");bind(R.id.podcastsButton,"trending_podcasts_episodes","Podcasts");
  findViewById(R.id.searchButton).requestFocus();load("trending_music","Music");
 }
 private void bind(int id,String kiosk,String label){findViewById(id).setOnClickListener(v->load(kiosk,label));}
 private void load(String kiosk,String label){title.setText(label);progress.setVisibility(View.VISIBLE);io.execute(()->{try{List<VideoItem> r=YouTubeData.kiosk(kiosk);runOnUiThread(()->{progress.setVisibility(View.GONE);adapter.setItems(r);});}catch(Exception e){runOnUiThread(()->{progress.setVisibility(View.GONE);title.setText(label+" · unavailable, try Search");});}});}
 private void play(VideoItem i){Intent x=new Intent(this,PlayerActivity.class);x.putExtra("url",i.url);x.putExtra("title",i.title);startActivity(x);}
 @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
