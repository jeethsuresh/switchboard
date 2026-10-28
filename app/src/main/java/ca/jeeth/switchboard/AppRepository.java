package ca.jeeth.switchboard;

import android.content.*;
import android.media.tv.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

/** One icon catalog per process. Home/overlay never scan packages on the UI thread. */
final class AppRepository {
    private static AppRepository instance;
    static synchronized AppRepository get(Context context){if(instance==null)instance=new AppRepository(context.getApplicationContext());return instance;}
    private final Context context;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<Runnable> listeners=new HashSet<>();
    private boolean loading,dirtyApps,dirtyInputs;
    volatile boolean ready;
    volatile List<Catalog.Tile> apps=Collections.emptyList(),inputs=Collections.emptyList();
    private AppRepository(Context c){
        context=c;
        IntentFilter filter=new IntentFilter();for(String a:new String[]{Intent.ACTION_PACKAGE_ADDED,Intent.ACTION_PACKAGE_REMOVED,Intent.ACTION_PACKAGE_CHANGED,Intent.ACTION_PACKAGE_REPLACED})filter.addAction(a);filter.addDataScheme("package");
        BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){refresh(true,false);}};
        if(Build.VERSION.SDK_INT>=33)c.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);else c.registerReceiver(receiver,filter);
        TvInputManager manager=(TvInputManager)c.getSystemService(Context.TV_INPUT_SERVICE);
        if(manager!=null)manager.registerCallback(new TvInputManager.TvInputCallback(){
            public void onInputStateChanged(String id,int state){refresh(false,true);}
            public void onInputAdded(String id){refresh(false,true);}
            public void onInputRemoved(String id){refresh(false,true);}
            public void onInputUpdated(String id){refresh(false,true);}
        },main);
        refresh(true,true);
    }
    void listen(Runnable r){listeners.add(r);}
    void unlisten(Runnable r){listeners.remove(r);}
    void refresh(boolean app,boolean input){
        dirtyApps|=app;dirtyInputs|=input;if(loading)return;
        final boolean a=dirtyApps,i=dirtyInputs;dirtyApps=false;dirtyInputs=false;loading=true;
        worker.execute(()->{
            List<Catalog.Tile> loadedApps=null,loadedInputs=null;
            try{if(a)loadedApps=Catalog.apps(context);if(i)loadedInputs=Catalog.inputs(context);}catch(RuntimeException ignored){}
            final List<Catalog.Tile> nextApps=loadedApps,nextInputs=loadedInputs;
            main.post(()->{if(nextApps!=null)apps=Collections.unmodifiableList(nextApps);if(nextInputs!=null)inputs=Collections.unmodifiableList(nextInputs);ready=true;loading=false;
                for(Runnable r:new ArrayList<>(listeners))r.run();if(dirtyApps||dirtyInputs)refresh(false,false);
            });
        });
    }
    List<Catalog.Tile> hdmi(){List<Catalog.Tile> ports=new ArrayList<>();for(Catalog.Tile t:inputs)if(t.input.getType()==TvInputInfo.TYPE_HDMI&&t.input.getParentId()==null)ports.add(t);ports.sort(Comparator.comparing(t->t.input.getId()));return ports;}
    boolean knownApp(String pkg){for(Catalog.Tile t:apps)if(t.intent!=null&&t.id.equals("app:"+pkg))return true;return false;}
}
