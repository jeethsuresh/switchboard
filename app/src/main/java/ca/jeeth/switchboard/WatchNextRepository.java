package ca.jeeth.switchboard;

import android.content.*;
import android.content.pm.*;
import android.database.*;
import android.media.tv.TvContract;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

/** Read-only, cached access to searchable programs published by other TV apps. */
final class WatchNextRepository {
    static final String PERMISSION="android.permission.READ_TV_LISTINGS";
    enum State { PERMISSION,LOADING,READY,EMPTY,ERROR }
    private static WatchNextRepository instance;
    static synchronized WatchNextRepository get(Context c){if(instance==null)instance=new WatchNextRepository(c.getApplicationContext());return instance;}
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Set<Runnable> listeners=new HashSet<>();
    private final Runnable changed=()->refresh(true);
    private boolean loading,dirty,observing;
    private long lastRead;
    volatile State state=State.PERMISSION;
    volatile List<WatchEntry> entries=Collections.emptyList();
    volatile Map<String,Integer> publishers=Collections.emptyMap();
    private WatchNextRepository(Context context){this.context=context;}
    boolean allowed(){return context.checkSelfPermission(PERMISSION)==PackageManager.PERMISSION_GRANTED;}
    void listen(Runnable r){listeners.add(r);}
    void unlisten(Runnable r){listeners.remove(r);}
    private void notifyChanged(){for(Runnable r:new ArrayList<>(listeners))r.run();}
    void refresh(boolean force){
        if(!allowed()){entries=Collections.emptyList();publishers=Collections.emptyMap();state=State.PERMISSION;notifyChanged();return;}
        if(!observing){try{context.getContentResolver().registerContentObserver(TvContract.WatchNextPrograms.CONTENT_URI,true,new ContentObserver(main){
            @Override public void onChange(boolean self){main.removeCallbacks(changed);main.postDelayed(changed,400);}
        });observing=true;}catch(RuntimeException ignored){}}
        if(loading){dirty|=force;return;}
        if(!force&&SystemClock.elapsedRealtime()-lastRead<3000)return;
        loading=true;if(entries.isEmpty())state=State.LOADING;
        worker.execute(()->{
            List<WatchEntry> found=new ArrayList<>();Map<String,Integer> counts=new TreeMap<>();Map<String,Boolean> available=new HashMap<>();State next;
            String[] projection={"_id","package_name","title","episode_title","season_display_number","episode_display_number","intent_uri","last_engagement_time_utc_millis","last_playback_position_millis","duration_millis","watch_next_type","browsable","content_id","internal_provider_id","thumbnail_uri","poster_art_uri"};
            try(Cursor cursor=context.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI,projection,null,null,null)){
                if(cursor==null)throw new IllegalStateException("TV provider unavailable");
                while(cursor.moveToNext()){
                    String pkg=string(cursor,1);counts.put(pkg,counts.getOrDefault(pkg,0)+1);
                    if(!available.computeIfAbsent(pkg,p->Catalog.appIntent(context,p)!=null))continue;
                    String intent=string(cursor,6);String id=string(cursor,12);if(id.isEmpty())id=string(cursor,13);if(id.isEmpty())id=intent.isEmpty()?string(cursor,0):intent;
                    WatchEntry e=new WatchEntry(id,pkg,string(cursor,2),string(cursor,3),string(cursor,4),string(cursor,5),intent,number(cursor,7),number(cursor,8),number(cursor,9),(int)number(cursor,10),number(cursor,11)!=0,string(cursor,14).isEmpty()?string(cursor,15):string(cursor,14));
                    if(e.useful()&&resumeIntent(context,e)!=null)found.add(e);
                }
                Map<String,Long> hidden=new HashMap<>();for(Map.Entry<String,?> p:new Store(context).prefs.getAll().entrySet())if(p.getKey().startsWith("watchHidden:")&&p.getValue() instanceof Long)hidden.put(p.getKey().substring(12),(Long)p.getValue());
                found=WatchEntry.ordered(found,hidden);next=found.isEmpty()?State.EMPTY:State.READY;
            }catch(SecurityException e){next=allowed()?State.ERROR:State.PERMISSION;found.clear();counts.clear();}
            catch(RuntimeException e){next=State.ERROR;found.clear();counts.clear();}
            final List<WatchEntry> result=Collections.unmodifiableList(found);final Map<String,Integer> providers=Collections.unmodifiableMap(counts);final State status=next;
            main.post(()->{loading=false;lastRead=SystemClock.elapsedRealtime();
                if(allowed()){entries=result;publishers=providers;state=status;}else{entries=Collections.emptyList();publishers=Collections.emptyMap();state=State.PERMISSION;}
                notifyChanged();if(dirty){dirty=false;refresh(true);}
            });
        });
    }
    static Intent resumeIntent(Context c,WatchEntry e){
        try{
            Intent intent=Intent.parseUri(e.intentUri,Intent.URI_INTENT_SCHEME|Intent.URI_ANDROID_APP_SCHEME);
            if(intent.getComponent()!=null&&!e.pkg.equals(intent.getComponent().getPackageName()))return null;
            intent.setSelector(null);intent.setPackage(e.pkg);intent.setClipData(null);intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if(intent.getAction()==null)intent.setAction(Intent.ACTION_VIEW);
            ResolveInfo resolved=c.getPackageManager().resolveActivity(intent,0);
            if(resolved==null||!e.pkg.equals(resolved.activityInfo.packageName)||resolved.activityInfo.name.startsWith("com.google.android.archive."))return null;
            return intent;
        }catch(Exception ignored){return null;}
    }
    static void open(ControlPanel s,WatchEntry e){
        Intent intent=Catalog.appIntent(s,e.pkg)==null?null:resumeIntent(s,e);
        if(intent==null){OverlayMenus.message(s,"Resume link unavailable","This service no longer provides a usable link to this item. Open the app from the app grid instead.");return;}
        try{s.dismissOverlay();s.startActivity(intent);new Store(s).used(e.pkg);}catch(RuntimeException failure){OverlayMenus.message(s,"Could not resume","The streaming app refused this resume link. Open it from the app grid instead.");}
    }
    void hide(WatchEntry e){new Store(context).prefs.edit().putLong("watchHidden:"+e.key,e.engagement).apply();refresh(true);}
    void restoreHidden(){android.content.SharedPreferences.Editor edit=new Store(context).prefs.edit();for(String key:new Store(context).prefs.getAll().keySet())if(key.startsWith("watchHidden:"))edit.remove(key);edit.apply();refresh(true);}
    private static String string(Cursor c,int index){String v=c.getString(index);return v==null?"":v;}
    private static long number(Cursor c,int index){return c.isNull(index)?0:c.getLong(index);}
}
