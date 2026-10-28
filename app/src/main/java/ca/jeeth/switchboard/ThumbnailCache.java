package ca.jeeth.switchboard;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.util.LruCache;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Small, private image cache. No image work or network I/O runs on the UI thread. */
final class ThumbnailCache {
    private static ThumbnailCache instance;
    static synchronized ThumbnailCache get(Context c){if(instance==null)instance=new ThumbnailCache(c.getApplicationContext());return instance;}
    private final Context context;
    private final File directory;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(12),new ThreadPoolExecutor.AbortPolicy());
    private final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(6*1024*1024){protected int sizeOf(String key,Bitmap image){return image.getAllocationByteCount();}};
    private final Map<String,List<Consumer<Bitmap>>> pending=new HashMap<>();
    private final Map<String,Long> failures=new HashMap<>();
    private ThumbnailCache(Context c){context=c;directory=new File(c.getCacheDir(),"watch-thumbnails");}
    void load(String uri,Consumer<Bitmap> callback){
        if(uri.isEmpty()){callback.accept(null);return;}
        Bitmap bitmap=memory.get(uri);if(bitmap!=null){callback.accept(bitmap);return;}
        if(failures.getOrDefault(uri,0L)>SystemClock.elapsedRealtime()){callback.accept(null);return;}
        List<Consumer<Bitmap>> waiting=pending.get(uri);if(waiting!=null){waiting.add(callback);return;}
        waiting=new ArrayList<>();waiting.add(callback);pending.put(uri,waiting);
        try{workers.execute(()->{Bitmap image=null;try{image=read(uri);}catch(Exception ignored){}final Bitmap ready=image;
            main.post(()->{if(ready!=null){memory.put(uri,ready);failures.remove(uri);}else{if(failures.size()>128)failures.clear();failures.put(uri,SystemClock.elapsedRealtime()+60000);}
                List<Consumer<Bitmap>> callbacks=pending.remove(uri);if(callbacks!=null)for(Consumer<Bitmap> c:callbacks)c.accept(ready);
            });
        });}catch(RejectedExecutionException busy){List<Consumer<Bitmap>> callbacks=pending.remove(uri);if(callbacks!=null)for(Consumer<Bitmap> c:callbacks)c.accept(null);}
    }
    private Bitmap read(String value)throws Exception{
        File cached=new File(directory,hash(value)+".jpg");
        if(cached.isFile()&&System.currentTimeMillis()-cached.lastModified()<86400000){Bitmap b=BitmapFactory.decodeFile(cached.getPath());if(b!=null)return b;}
        Uri uri=Uri.parse(value);byte[] bytes;
        if("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))bytes=download(value);
        else if("content".equals(uri.getScheme())||"android.resource".equals(uri.getScheme())){try(InputStream in=context.getContentResolver().openInputStream(uri)){if(in==null)return null;bytes=bounded(in);}}
        else return null;
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0)return null;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
        while(bounds.outWidth/options.inSampleSize>768||bounds.outHeight/options.inSampleSize>768)options.inSampleSize*=2;
        Bitmap result=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(result==null)return null;
        try{directory.mkdirs();File temp=File.createTempFile("thumb-",".tmp",directory);
            try{try(OutputStream out=new FileOutputStream(temp)){result.compress(Bitmap.CompressFormat.JPEG,85,out);}if(!temp.renameTo(cached))temp.delete();}finally{temp.delete();}
            trimDisk();
        }catch(IOException ignored){}
        return result;
    }
    private static byte[] download(String value)throws Exception{
        URL url=new URL(value);
        for(int hop=0;hop<5;hop++){
            if(!url.getProtocol().equals("https")&&!url.getProtocol().equals("http"))throw new IOException("Unsupported image scheme");
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(3000);connection.setReadTimeout(4000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("Accept","image/*");
            try{int status=connection.getResponseCode();
                if(status>=300&&status<400){String redirect=connection.getHeaderField("Location");if(redirect==null)throw new IOException("Missing image location");URL next=new URL(url,redirect);if(url.getProtocol().equals("https")&&!next.getProtocol().equals("https"))throw new IOException("Image redirect downgraded transport");url=next;continue;}
                if(status<200||status>=300||connection.getContentLengthLong()>4*1024*1024)throw new IOException("Image unavailable");
                try(InputStream in=connection.getInputStream()){return bounded(in);}
            }finally{connection.disconnect();}
        }throw new IOException("Too many image redirects");
    }
    private static byte[] bounded(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=in.read(buffer))!=-1){if(out.size()+count>4*1024*1024)throw new IOException("Image exceeds cache limit");out.write(buffer,0,count);}return out.toByteArray();}
    private synchronized void trimDisk(){File[] files=directory.listFiles((dir,name)->name.endsWith(".jpg"));if(files==null)return;long bytes=0;for(File f:files)bytes+=f.length();
        if(bytes<=24*1024*1024&&files.length<=200)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));int remaining=files.length;
        for(File f:files){if(bytes<=20*1024*1024&&remaining<=160)break;long size=f.length();if(f.delete()){bytes-=size;remaining--;}}
    }
    private static String hash(String value)throws Exception{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
}
