package com.tihulu.youtubetv;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.*;

public final class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.Holder>{
 public interface Listener{void onClick(VideoItem item);}
 private final List<VideoItem> items=new ArrayList<>(); private final Listener listener;
 public VideoAdapter(Listener listener){this.listener=listener;setHasStableIds(true);}
 public void setItems(List<VideoItem> x){items.clear();items.addAll(x);notifyDataSetChanged();}
 @Override public long getItemId(int p){return items.get(p).url.hashCode();}
 @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup p,int t){return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_video,p,false));}
 @Override public void onBindViewHolder(@NonNull Holder h,int p){VideoItem i=items.get(p);h.title.setText(i.title);h.subtitle.setText(i.subtitle);Glide.with(h.thumbnail).load(i.thumbnail).centerCrop().dontAnimate().into(h.thumbnail);h.itemView.setOnClickListener(v->listener.onClick(i));}
 @Override public int getItemCount(){return items.size();}
 static final class Holder extends RecyclerView.ViewHolder{
  final ImageView thumbnail;final TextView title,subtitle;
  Holder(View v){super(v);thumbnail=v.findViewById(R.id.thumbnail);title=v.findViewById(R.id.videoTitle);subtitle=v.findViewById(R.id.videoSubtitle);}
 }
}
