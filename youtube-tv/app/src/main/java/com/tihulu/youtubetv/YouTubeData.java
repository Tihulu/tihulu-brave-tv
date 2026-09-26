package com.tihulu.youtubetv;
import static org.schabi.newpipe.extractor.ServiceList.YouTube;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import java.util.*;

public final class YouTubeData {
 private YouTubeData(){}
 public static List<VideoItem> search(String q)throws Exception{
  SearchExtractor e=YouTube.getSearchExtractor(q,Collections.emptyList(),"");e.fetchPage();return map(e.getInitialPage().getItems());
 }
 @SuppressWarnings("unchecked")
 public static List<VideoItem> kiosk(String id)throws Exception{
  KioskExtractor<? extends InfoItem> e=(KioskExtractor<? extends InfoItem>)YouTube.getKioskList().getExtractorById(id,null);e.fetchPage();return map(e.getInitialPage().getItems());
 }
 private static List<VideoItem> map(List<? extends InfoItem> items){
  List<VideoItem> out=new ArrayList<>();
  for(InfoItem i:items)if(i instanceof StreamInfoItem){
   StreamInfoItem s=(StreamInfoItem)i;String thumb="";
   if(s.getThumbnails()!=null&&!s.getThumbnails().isEmpty())thumb=s.getThumbnails().get(0).getUrl();
   out.add(new VideoItem(s.getName(),s.getUploaderName()==null?"":s.getUploaderName(),s.getUrl(),thumb));
  }return out;
 }
}
