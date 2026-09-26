package com.tihulu.youtubetv;
import androidx.annotation.NonNull;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

public final class NewPipeDownloader extends Downloader {
 private static final NewPipeDownloader INSTANCE=new NewPipeDownloader();
 private static final String UA="Mozilla/5.0 (Linux; Android 14; Google TV) AppleWebKit/537.36 Chrome/136 Safari/537.36";
 private final OkHttpClient client=new OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).followRedirects(true).build();
 private NewPipeDownloader(){}
 public static NewPipeDownloader getInstance(){return INSTANCE;}
 @Override public Response execute(@NonNull Request request) throws IOException, ReCaptchaException {
   RequestBody body=request.dataToSend()==null?null:RequestBody.create(request.dataToSend(),null);
   okhttp3.Request.Builder b=new okhttp3.Request.Builder().url(request.url()).method(request.httpMethod(),body).header("User-Agent",UA);
   for(Map.Entry<String,List<String>> e:request.headers().entrySet()){b.removeHeader(e.getKey());for(String v:e.getValue())b.addHeader(e.getKey(),v);}
   try(okhttp3.Response r=client.newCall(b.build()).execute()){
     if(r.code()==429) throw new ReCaptchaException("YouTube challenge requested",request.url());
     ResponseBody rb=r.body(); String text=rb==null?null:rb.string();
     return new Response(r.code(),r.message(),r.headers().toMultimap(),text,r.request().url().toString());
   }
 }
}
