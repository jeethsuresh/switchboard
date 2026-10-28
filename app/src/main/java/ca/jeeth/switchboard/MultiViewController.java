package ca.jeeth.switchboard;

import android.content.*;
import android.graphics.Rect;
import android.os.*;
import org.json.*;
import java.util.concurrent.*;

final class MultiViewController {
    static Catalog.Tile main,pip;
    static Catalog.Tile nativeMain,nativePip;
    static boolean active;
    private static boolean appWindowsUsed;
    private static boolean nativeActive;
    private static Catalog.Tile exitSource;
    private static String runningMain="",runningPip="";
    static int corner,size=30,nativeSize=30;
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final Handler ui=new Handler(Looper.getMainLooper());
    static String label(Catalog.Tile tile){return tile==null?"Choose a source":tile.title;}
    static void startNative(ControlPanel s,Catalog.Tile mainSource,Catalog.Tile insetSource){
        if(mainSource==null||mainSource.input==null){OverlayMenus.message(s,"Choose an HDMI input","Select the main HDMI source first.");return;}
        nativeMain=mainSource;nativePip=insetSource;exitSource=mainSource;active=true;nativeActive=true;runningMain="";runningPip="";
        s.dismissOverlay();s.startActivity(new Intent(s,MultiViewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("mainInput",mainSource.input.getId()).putExtra("pipInput",insetSource==null?"":insetSource.input.getId()));
    }
    static String component(Catalog.Tile tile){return tile.input!=null?"ca.jeeth.switchboard/.MultiViewActivity":tile.intent.getComponent().flattenToShortString();}
    private static JSONObject launch(Context c,Catalog.Tile tile,int mode)throws Exception{
        JSONObject r=BridgeClient.command("launch").put("component",component(tile)).put("mode",mode);
        if(tile.input!=null)r.put("mainInput",tile.input.getId()).put("pipInput","");
        if(mode==5){
            int w=c.getResources().getDisplayMetrics().widthPixels,h=c.getResources().getDisplayMetrics().heightPixels;
            int width=w*size/100,height=width*9/16,margin=24;
            int left=corner%2==0?w-width-margin:margin,top=corner<2?margin:h-height-margin;
            Rect bounds=new Rect(left,top,left+width,top+height);
            r.put("left",bounds.left).put("top",bounds.top).put("right",bounds.right).put("bottom",bounds.bottom);
            r.put("screenWidth",w).put("screenHeight",h).put("corner",corner);
        }
        return r;
    }
    static void apply(ControlPanel s){
        if(main==null||pip==null){OverlayMenus.message(s,"Choose both sources","Select a main source and a different inset source first.");return;}
        if(main.id.equals(pip.id)){OverlayMenus.message(s,"Choose different sources","The same app or input cannot occupy both positions in this experiment.");return;}
        if(main.intent==null||pip.intent==null){OverlayMenus.message(s,"Source unavailable","An unavailable or archived app cannot be used as a source.");return;}
        if(main.input!=null&&pip.input!=null){
            startNative(s,main,pip);
            return;
        }
        Catalog.Tile mainSource=main,pipSource=pip;
        OverlayMenus.message(s,"Starting app-window experiment","Preparing the main source and inset. Hold Home for controls.");
        worker.execute(()->{
            try{
                JSONObject status=BridgeClient.request(BridgeClient.command("status"));
                if(!status.optString("freeformSetting").equals("1"))throw new Exception("Enable experimental app windows in the multiview controls, then restart the TV and restart the ADB helper.");
                Store store=new Store(s);int boot=android.provider.Settings.Global.getInt(s.getContentResolver(),"boot_count",-1);
                if(store.prefs.contains("freeformChangedBoot")&&store.prefs.getInt("freeformChangedBoot",-2)==boot)throw new Exception("The app-window settings were changed during this boot. Restart the TV and restart the ADB helper before retrying.");
                appWindowsUsed=true;
                nativeActive=false;exitSource=mainSource;
                BridgeClient.request(launch(s,mainSource,1).put("reuse",active&&runningMain.equals(mainSource.id)));
                BridgeClient.request(launch(s,pipSource,5).put("reuse",active&&runningPip.equals(pipSource.id)));
                runningMain=mainSource.id;runningPip=pipSource.id;
                active=true;ui.post(s::dismissOverlay);
            }catch(Exception e){ui.post(()->OverlayMenus.message(s,"App-window request failed",e.getMessage()+"\n\nAn ADB helper is required. This experiment is not native Android PiP."));}
        });
    }
    static void focus(ControlPanel s,boolean inset){
        if(nativeActive){focusNative(s,inset);return;}
        Catalog.Tile target=inset?pip:main;
        if(target==null){OverlayMenus.message(s,"Choose a source","No source is selected for that position.");return;}
        try{BridgeClient.call(BridgeClient.command("focus").put("component",component(target)),r->s.dismissOverlay(),e->OverlayMenus.message(s,"Focus unavailable",e));}
        catch(Exception e){OverlayMenus.message(s,"Focus unavailable",e.toString());}
    }
    static void focusNative(ControlPanel s,boolean inset){
        MultiViewActivity.focusPane(inset);s.dismissOverlay();
    }
    static void exit(ControlPanel s){
        active=false;runningMain="";runningPip="";
        MultiViewActivity.finishExperiment();
        if(!appWindowsUsed){restoreCecAndOpenMain(s);return;}
        BridgeClient.call(BridgeClient.command("restoreWindows"),r->{appWindowsUsed=false;restoreCecAndOpenMain(s);},e->restoreCecAndOpenMain(s));
    }
    private static void restoreCecAndOpenMain(ControlPanel s){
        Store store=new Store(s);
        Runnable open=()->{
            s.dismissOverlay();
            Catalog.Tile source=exitSource==null?main:exitSource;
            if(source!=null&&source.intent!=null)try{s.startActivity(source.intent);}catch(Exception ignored){}
        };
        if(!store.prefs.getBoolean("cecLocked",false)){open.run();return;}
        try{BridgeClient.call(BridgeClient.command("cecLock").put("lock",false).put("restore",store.prefs.getInt("cecBeforeLock",1)),r->{store.prefs.edit().putBoolean("cecLocked",false).apply();open.run();},e->OverlayMenus.message(s,"Restore CEC manually",e+"\nOpen TV Settings → Channels & Inputs → External Devices Control and turn HDMI Control back on."));}
        catch(Exception e){OverlayMenus.message(s,"CEC restoration failed",e.toString());}
    }
}
