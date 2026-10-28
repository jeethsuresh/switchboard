package ca.jeeth.switchboard;

import android.app.AlertDialog;
import java.util.*;
import org.json.JSONObject;

final class OverlayMenus {
    static void message(ControlPanel s,String title,String message){
        s.panel(new AlertDialog.Builder(s).setTitle(title).setMessage(message).setPositiveButton("Close",null).create());
    }
    private static void menu(ControlPanel s,String title,String message,String[] labels,java.util.function.IntConsumer select){
        AlertDialog.Builder builder=new AlertDialog.Builder(s).setTitle(title);
        if(message!=null)builder.setMessage(message);
        // AlertDialog's message and list share a panel; use a title-only list for reliable TV focus.
        builder.setItems(labels,(d,index)->select.accept(index)).setNegativeButton("Close",null);
        s.panel(builder.create());
    }
    static void dashboard(ControlPanel s){
        DashboardView dashboard=new DashboardView(s,true);
        AlertDialog dialog=new AlertDialog.Builder(s).setView(dashboard).create();
        AppRepository repository=AppRepository.get(s);
        Runnable changed=()->dashboard.bind(false);
        repository.listen(changed);
        dialog.setOnDismissListener(d->repository.unlisten(changed));
        s.panel(dialog);
        android.view.Window window=dialog.getWindow();
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        android.util.DisplayMetrics metrics=s.getResources().getDisplayMetrics();
        window.setLayout((int)(metrics.widthPixels*.94f),(int)(metrics.heightPixels*.92f));
        dashboard.post(dashboard::center);
    }
    static void nativeExperiment(ControlPanel s){
        menu(s,"HDMI PiP · no helper or remote guard",null,new String[]{"Choose two HDMI sources and start","Single HDMI baseline · no inset","Add / change inset · keep main session","Remove inset · keep main session","Swap main and inset","Move inset","Control inset with remote","Control main with remote","CEC settings / fullscreen switching","Exit to main input"},index->{
            switch(index){
                case 0->nativeSource(s,null,false);case 1->nativeSource(s,null,true);
                case 2->{if(MultiViewController.nativeMain==null)message(s,"Choose a main input","Start the single HDMI baseline first, then add an inset here.");else nativeSource(s,MultiViewController.nativeMain,false);}
                case 3->{MultiViewController.nativePip=null;applyNative(s);}
                case 4->{Catalog.Tile swap=MultiViewController.nativeMain;MultiViewController.nativeMain=MultiViewController.nativePip;MultiViewController.nativePip=swap;applyNative(s);}
                case 5->{MultiViewController.corner=(MultiViewController.corner+1)%4;applyNative(s);}
                case 6->MultiViewController.focusNative(s,true);case 7->MultiViewController.focusNative(s,false);
                case 8->s.panel(new AlertDialog.Builder(s).setTitle("Isolate CEC switching")
                    .setMessage("TCL can open its fullscreen HDMI activity when a CEC source becomes active. For a comparison test, power both devices on, open the TV's CEC settings and temporarily turn HDMI Control off, then return and run this experiment again. Restore your preferred CEC setting afterward. This manual test needs no helper.")
                    .setPositiveButton("Open CEC settings",(d,w)->s.startActivity(new android.content.Intent("com.android.tv.settings.CEC").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))).setNegativeButton("Close",null).create());
                case 9->MultiViewController.exit(s);
            }
        });
    }
    private static void applyNative(ControlPanel s){MultiViewController.startNative(s,MultiViewController.nativeMain,MultiViewController.nativePip);}
    private static void nativeSource(ControlPanel s,Catalog.Tile main,boolean single){
        List<Catalog.Tile> list=new ArrayList<>();
        for(Catalog.Tile t:Catalog.inputs(s))if(t.input.getType()==android.media.tv.TvInputInfo.TYPE_HDMI&&t.input.getParentId()==null&&(main==null||!main.id.equals(t.id)))list.add(t);
        if(list.isEmpty()){message(s,"HDMI ports unavailable","At least two distinct HDMI ports must be exposed by TCL.");return;}
        menu(s,main==null?"Main HDMI input":"Inset HDMI input",null,list.stream().map(t->t.title+" · "+t.subtitle).toArray(String[]::new),index->{
            if(single){MultiViewController.nativeMain=list.get(index);MultiViewController.nativePip=null;confirmNative(s);}
            else if(main==null)nativeSource(s,list.get(index),false);
            else{MultiViewController.nativeMain=main;MultiViewController.nativePip=list.get(index);confirmNative(s);}
        });
    }
    private static void confirmNative(ControlPanel s){
        s.panel(new AlertDialog.Builder(s).setTitle("Run HDMI experiment?")
            .setMessage("This requests TV input video directly. No helper or remote guard is required. TCL may interrupt video or switch to fullscreen HDMI through CEC. Use the CEC settings option to compare behavior with HDMI Control disabled.")
            .setPositiveButton("Start",(d,w)->applyNative(s)).setNegativeButton("Cancel",null).create());
    }
    static void apps(ControlPanel s,boolean picking,boolean inset){
        List<Catalog.Tile> list=new Store(s).apps(AppRepository.get(s).apps,true);
        String[] labels=list.stream().map(t->t.title+(t.intent==null?" · unavailable":"")).toArray(String[]::new);
        menu(s,picking?(inset?"Inset app":"Main app"):"Open an app",null,labels,index->{
            Catalog.Tile selected=list.get(index);
            if(selected.intent==null){message(s,"App unavailable","This app cannot be launched. No installation page will be opened.");return;}
            if(picking){if(inset)MultiViewController.pip=selected;else MultiViewController.main=selected;multiview(s);}
            else Actions.tile(s,selected);
        });
    }
    static void inputs(ControlPanel s,boolean picking,boolean inset){
        List<Catalog.Tile> list=new ArrayList<>();
        for(Catalog.Tile t:AppRepository.get(s).inputs)if(!picking||t.input.getParentId()==null)list.add(t);
        if(list.isEmpty()){message(s,"No inputs","The TV did not expose any inputs.");return;}
        String[] labels=list.stream().map(t->t.title+" · "+t.subtitle).toArray(String[]::new);
        menu(s,picking?(inset?"Inset input":"Main input"):"Switch input",null,labels,index->{
            Catalog.Tile selected=list.get(index);
            if(picking){if(inset)MultiViewController.pip=selected;else MultiViewController.main=selected;multiview(s);}
            else Actions.tile(s,selected);
        });
    }
    private static void source(ControlPanel s,boolean inset){
        menu(s,inset?"Choose inset source":"Choose main source",null,new String[]{"HDMI / TV inputs","Installed TV apps"},index->{if(index==0)inputs(s,true,inset);else apps(s,true,inset);});
    }
    static void multiview(ControlPanel s){
        BridgeClient.init(s);
        Store store=new Store(s);boolean locked=store.prefs.getBoolean("cecLocked",false);
        String[] labels={"Main: "+MultiViewController.label(MultiViewController.main),"Inset: "+MultiViewController.label(MultiViewController.pip),
            "Start / apply experiment","Swap main and inset","Move inset to next corner","Inset size: "+(MultiViewController.size==30?"Small (TV minimum)":MultiViewController.size==45?"Medium":"Large"),
            "Control inset with remote","Control main with remote",locked?"Unlock CEC / restore previous setting":"Lock CEC during experiment",
            "App-window setup / helper status","Exit experiment to main source"};
        menu(s,"Multiview · experimental",null,labels,index->{
            switch(index){
                case 0->source(s,false);case 1->source(s,true);case 2->confirmStart(s);
                case 3->{Catalog.Tile swap=MultiViewController.main;MultiViewController.main=MultiViewController.pip;MultiViewController.pip=swap;if(MultiViewController.active)MultiViewController.apply(s);else multiview(s);}
                case 4->{MultiViewController.corner=(MultiViewController.corner+1)%4;if(MultiViewController.active)MultiViewController.apply(s);else multiview(s);}
                case 5->{MultiViewController.size=MultiViewController.size==30?45:MultiViewController.size==45?60:30;if(MultiViewController.active)MultiViewController.apply(s);else multiview(s);}
                case 6->MultiViewController.focus(s,true);case 7->MultiViewController.focus(s,false);case 8->cec(s,!locked);case 9->setup(s);case 10->MultiViewController.exit(s);
            }
        });
    }
    private static void confirmStart(ControlPanel s){
        s.panel(new AlertDialog.Builder(s).setTitle("Run multiview experiment?")
            .setMessage("This can interrupt HDMI video or put a connected device into standby. The optional CEC lock temporarily disables CEC. App windows need the ADB helper and freeform support; playback and remote focus vary by app. Hold Home for controls.")
            .setPositiveButton("Start",(d,w)->MultiViewController.apply(s)).setNegativeButton("Cancel",null).create());
    }
    private static void cec(ControlPanel s,boolean lock){
        Store store=new Store(s);
        if(lock){s.panel(new AlertDialog.Builder(s).setTitle("Temporarily disable CEC?")
            .setMessage("This locks out CEC source switching and power commands during the experiment. CEC remote and volume control may also stop. Exit the experiment or use Unlock CEC to restore the previous setting.")
            .setPositiveButton("Lock CEC",(d,w)->setCec(s,true)).setNegativeButton("Cancel",null).create());}
        else setCec(s,false);
    }
    private static void setCec(ControlPanel s,boolean lock){
        Store store=new Store(s);
        try{JSONObject request=BridgeClient.command("cecLock").put("lock",lock).put("restore",store.prefs.getInt("cecBeforeLock",1));
            BridgeClient.call(request,r->{if(lock)store.prefs.edit().putInt("cecBeforeLock",r.optInt("original",1)).putBoolean("cecLocked",true).apply();
                else store.prefs.edit().putBoolean("cecLocked",false).apply();multiview(s);},e->message(s,"CEC control unavailable",e+"\nStart the ADB helper before using this control."));
        }catch(Exception e){message(s,"CEC control failed",e.toString());}
    }
    private static void setup(ControlPanel s){
        menu(s,"App-window experiment setup",null,new String[]{"Check helper status","Enable freeform app windows (TV restart required)","Restore previous app-window settings"},index->{
            Store store=new Store(s);
            if(index==0){BridgeClient.call(BridgeClient.command("status"),r->message(s,"ADB helper connected","Freeform setting: "+r.optString("freeformSetting")+"\nForce resize: "+r.optString("forceResizeSetting")+"\nCEC enabled: "+r.optInt("cec")+"\n\nA setting of 1 still requires a TV restart before testing. Video and focus must be checked separately."),e->message(s,"ADB helper unavailable",e+"\nRun bridge/start-helper.sh from the source project on your computer. The helper must be restarted after each TV reboot."));}
            else if(index==1){s.panel(new AlertDialog.Builder(s).setTitle("Enable experimental app windows?")
                .setMessage("This changes Android's freeform and forced-resize developer settings. Restart the TV afterward, then restart the ADB helper. Some TV apps may resize poorly or refuse playback. The previous settings are saved for restoration.")
                .setPositiveButton("Enable",(d,w)->BridgeClient.call(BridgeClient.command("enableFreeform"),r->{
                    if(!store.prefs.contains("previousFreeform"))store.prefs.edit().putString("previousFreeform",r.optString("previousFreeform","null")).putString("previousResize",r.optString("previousResize","null")).apply();
                    store.prefs.edit().putInt("freeformChangedBoot",android.provider.Settings.Global.getInt(s.getContentResolver(),"boot_count",-1)).apply();
                    message(s,"Restart required","Restart the TV from Settings → System → Restart. Then run the ADB helper again before choosing an app as an inset.");
                },e->message(s,"Helper unavailable",e))).setNegativeButton("Cancel",null).create());}
            else{try{BridgeClient.call(BridgeClient.command("restoreFreeform").put("freeform",store.prefs.getString("previousFreeform","null")).put("resize",store.prefs.getString("previousResize","null")),r->message(s,"Settings restored","Restart the TV to apply the previous window settings."),e->message(s,"Could not restore settings",e));}catch(Exception e){message(s,"Restore failed",e.toString());}}
        });
    }
}
