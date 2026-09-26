package com.tihulu.youtubetv;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import java.util.*;
import java.util.concurrent.*;

public final class SearchActivity extends AppCompatActivity{
 private final ExecutorService io=Executors.newSingleThreadExecutor();private VideoAdapter adapter;private ProgressBar progress;private TextView status;private EditText query;
 @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_search);query=findViewById(R.id.query);progress=findViewById(R.id.progress);status=findViewById(R.id.status);
  RecyclerView grid=findViewById(R.id.results);grid.setLayoutManager(new GridLayoutManager(this,4));adapter=new VideoAdapter(this::play);grid.setAdapter(adapter);
  query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);query.setSingleLine(true);
  query.setOnEditorActionListener((v,a,e)->{boolean enter=e!=null&&e.getKeyCode()==KeyEvent.KEYCODE_ENTER&&e.getAction()==KeyEvent.ACTION_DOWN;if(a==EditorInfo.IME_ACTION_SEARCH||enter){search();return true;}return false;});
  findViewById(R.id.goButton).setOnClickListener(v->search());query.requestFocus();
 }
 private void search(){String q=query.getText().toString().trim();if(q.isEmpty())return;progress.setVisibility(View.VISIBLE);status.setText("Searching…");io.execute(()->{try{List<VideoItem> r=YouTubeData.search(q);runOnUiThread(()->{progress.setVisibility(View.GONE);status.setText(r.size()+" results");adapter.setItems(r);});}catch(Exception e){runOnUiThread(()->{progress.setVisibility(View.GONE);status.setText("Search failed. YouTube may have changed its extraction flow.");});}});}
 private void play(VideoItem i){Intent x=new Intent(this,PlayerActivity.class);x.putExtra("url",i.url);x.putExtra("title",i.title);startActivity(x);}
 @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
