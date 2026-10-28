package ca.jeeth.switchboard;

import android.app.AlertDialog;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.widget.EditText;
import java.util.*;
import java.util.function.Consumer;

final class LauncherSettings {
    private static final String[] SIX_NAMES={"Netflix","Prime Video","YouTube","TCL TV+","Apple TV","Pluto TV"};
    private static final String[] SIX_PACKAGES={"com.netflix.ninja","com.amazon.amazonvideo.livingroom","com.google.android.youtube.tv","com.tcl.tv.plus","com.apple.atve.androidtv.appletv","tv.pluto.android"};
    static void menu(ControlPanel s,String title,String[] labels,java.util.function.IntConsumer action){s.panel(new AlertDialog.Builder(s).setTitle(title).setItems(labels,(d,i)->action.accept(i)).setNegativeButton("Close",null).create());}
    static void show(ControlPanel s){Store store=new Store(s);
        menu(s,"Switchboard settings",new String[]{"Remote buttons · "+store.mappedKeys().size()+" mapped","All apps / hidden shortcuts","App grid order · "+(store.prefs.getBoolean("customOrder",false)?"custom":"recently used"),"Remote guard / Home behavior","All inputs / AirPlay / CEC","TV settings","Experiments · parked","About / privacy"},i->{switch(i){
            case 0->remote(s);case 1->allApps(s);case 2->order(s);case 3->guard(s);case 4->connections(s);case 5->Actions.settings(s,Settings.ACTION_SETTINGS);case 6->experiments(s);case 7->OverlayMenus.message(s,"Switchboard TV 0.2.0","Native icons, local app history, no media feed or analytics. App-use history stays on this TV. The remote guard observes foreground app/window names and button events; it cannot read screen contents. Up resumes the last used app. Streaming services manage their own playback progress.");
        }});
    }
    static void allApps(ControlPanel s){Store store=new Store(s);List<Catalog.Tile> apps=store.apps(AppRepository.get(s).apps,true);
        if(apps.isEmpty()){OverlayMenus.message(s,"Apps loading","Try again in a moment.");return;}
        menu(s,"All apps · hold OK on Home to customize",apps.stream().map(t->store.label(t)+(t.intent==null?" · unavailable":"")).toArray(String[]::new),i->menu(s,store.label(apps.get(i)),new String[]{"Open app","Customize shortcut"},j->{if(j==0)Actions.tile(s,apps.get(i));else tile(s,apps.get(i),()->{});}));
    }
    static void addShortcut(ControlPanel s,Runnable changed){Store store=new Store(s);List<Catalog.Tile> apps=new ArrayList<>();for(Catalog.Tile t:AppRepository.get(s).apps)if(t.intent!=null)apps.add(t);
        menu(s,"Show app on Home",apps.stream().map(t->store.label(t)).toArray(String[]::new),i->{store.prefs.edit().putBoolean("hidden:"+apps.get(i).id,false).apply();changed.run();OverlayMenus.message(s,"Shortcut shown",store.label(apps.get(i))+" is available in the app grid. Recent use determines its position unless custom order is selected.");});
    }
    static void tile(ControlPanel s,Catalog.Tile tile,Runnable changed){Store store=new Store(s);
        if(tile.input!=null){rename(s,"Input name",store.label(tile),value->{store.prefs.edit().putString("label:"+tile.id,value).apply();changed.run();});return;}
        boolean hidden=!store.apps(AppRepository.get(s).apps,false).stream().anyMatch(t->t.id.equals(tile.id));
        menu(s,store.label(tile),new String[]{"Rename shortcut","Move to a grid position",hidden?"Show on Home":"Hide from Home","Open app"},i->{switch(i){
            case 0->rename(s,"Shortcut name",store.label(tile),value->{store.prefs.edit().putString("label:"+tile.id,value).apply();changed.run();});
            case 1->{List<Catalog.Tile> apps=store.apps(AppRepository.get(s).apps,false);apps.removeIf(t->t.id.equals(tile.id));List<String> labels=new ArrayList<>();for(int n=0;n<=apps.size();n++)labels.add("Position "+(n+1)+(n<apps.size()?" · before "+store.label(apps.get(n)):" · end"));
                menu(s,"Choose position · switches to custom order",labels.toArray(new String[0]),position->{apps.add(position,tile);store.saveOrder("grid",apps);store.prefs.edit().putBoolean("customOrder",true).putBoolean("hidden:"+tile.id,false).apply();changed.run();});}
            case 2->{store.prefs.edit().putBoolean("hidden:"+tile.id,!hidden).apply();changed.run();}
            case 3->Actions.tile(s,tile);
        }});
    }
    private static void order(ControlPanel s){Store store=new Store(s);menu(s,"App grid order",new String[]{"Recently used · automatic","Custom order · hold OK on an app to move it","Clear local recent-app history"},i->{
        if(i<2)store.prefs.edit().putBoolean("customOrder",i==1).apply();else s.panel(new AlertDialog.Builder(s).setTitle("Clear recent-app history?").setMessage("This clears Switchboard's local app history.").setPositiveButton("Clear",(d,w)->{SharedPreferences.Editor edit=store.prefs.edit();for(String key:store.prefs.getAll().keySet())if(key.startsWith("used:"))edit.remove(key);edit.apply();}).setNegativeButton("Cancel",null).create());
    });}
    static void remote(ControlPanel s){Store store=new Store(s);List<String> keys=new ArrayList<>(store.mappedKeys());List<String> labels=new ArrayList<>();labels.add("Learn another button");labels.add("Set up six TCL remote buttons");labels.add("Hold duration · "+(store.holdMs()/1000.0)+" seconds");
        for(String key:keys)labels.add(store.name(key)+" · tap: "+Actions.label(s,store.action(key,false))+" / hold: "+Actions.label(s,store.action(key,true)));
        menu(s,"Remote buttons",labels.toArray(new String[0]),i->{if(i==0)learn(s,-1);else if(i==1)learn(s,0);else if(i==2)menu(s,"Hold duration",new String[]{"0.6 seconds","0.8 seconds","1.2 seconds"},j->store.prefs.edit().putLong("holdMs",new long[]{600,800,1200}[j]).apply());else mapping(s,keys.get(i-3));});
    }
    private static void mapping(ControlPanel s,String key){Store store=new Store(s);
        menu(s,store.name(key),new String[]{"Tap → "+Actions.label(s,store.action(key,false)),"Hold → "+Actions.label(s,store.action(key,true)),"Rename button","Restore original button behavior"},i->{
            if(i<2)pickAction(s,i==0,action->{store.action(key,i==1,action);mapping(s,key);});
            else if(i==2)rename(s,"Button name",store.name(key),value->{store.prefs.edit().putString("name:"+key,value).apply();mapping(s,key);});
            else s.panel(new AlertDialog.Builder(s).setTitle("Restore "+store.name(key)+"?").setMessage("Switchboard will stop intercepting this button. Its original action may open an app or installation page.").setPositiveButton("Restore",(d,w)->store.unmap(key)).setNegativeButton("Cancel",null).create());
        });
    }
    private static void pickAction(ControlPanel s,boolean tap,Consumer<String> chosen){
        String[] actions={"overlay","app","input","settings","allapps","inputs","none","guard"};
        String[] labels={"Quick controls overlay","Launch an app","Switch to an input","Switchboard settings","All apps","All inputs","Do nothing / block button","Hold hint / unavailable overlay"};
        menu(s,tap?"Tap action":"Hold action",tap?labels:Arrays.copyOf(labels,7),i->{
            if(i==1){List<Catalog.Tile> apps=AppRepository.get(s).apps;menu(s,"Choose app",apps.stream().map(t->t.title+(t.intent==null?" · unavailable":"")).toArray(String[]::new),j->chosen.accept(apps.get(j).id));}
            else if(i==2){List<Catalog.Tile> inputs=AppRepository.get(s).inputs;menu(s,"Choose input",inputs.stream().map(t->new Store(s).label(t)).toArray(String[]::new),j->chosen.accept(inputs.get(j).id));}
            else chosen.accept(actions[i]);
        });
    }
    private static void learn(ControlPanel s,int stage){
        if(!RemoteGuardService.connected){OverlayMenus.message(s,"Remote guard is not connected",RemoteGuardService.enabled(s)?"Android has enabled the service but has not bound it. Toggle Switchboard remote guard off and on in TV Accessibility settings.":"Enable Switchboard remote guard in TV Settings → Accessibility, then return here.");return;}
        new Learning(s,stage).start();
    }
    private static final class Learning {
        final ControlPanel controls;final Store store;final int stage;final Handler handler=new Handler(Looper.getMainLooper());
        AlertDialog dialog;boolean finished;
        final SharedPreferences.OnSharedPreferenceChangeListener listener=(p,key)->{if(key.equals("learnedKey"))handler.post(()->received(p.getString(key,"")));};
        Learning(ControlPanel s,int stage){controls=s;store=new Store(s);this.stage=stage;}
        void start(){store.prefs.edit().remove("learnedKey").putLong("learningUntil",SystemClock.elapsedRealtime()+30000).apply();store.prefs.registerOnSharedPreferenceChangeListener(listener);
            dialog=new AlertDialog.Builder(controls).setTitle(stage<0?"Press and release a remote button":"Button "+(stage+1)+" / 6 · "+SIX_NAMES[stage])
                .setMessage("Press "+(stage<0?"the button to customize":SIX_NAMES[stage])+" once. Arrows, OK, Home, Back and Input stay reserved. Other buttons can be mapped if Android delivers them. Some power, voice or vendor buttons are handled by firmware first.")
                .setNegativeButton("Cancel",null).create();dialog.setOnDismissListener(d->finish());controls.panel(dialog);
            handler.postDelayed(()->{if(!finished){finish();dialog.dismiss();OverlayMenus.message(controls,"No button received","Android did not deliver a remappable key within 30 seconds. That button may be handled by TV firmware.");}},30000);
        }
        void finish(){if(finished)return;finished=true;handler.removeCallbacksAndMessages(null);store.prefs.unregisterOnSharedPreferenceChangeListener(listener);store.prefs.edit().remove("learningUntil").apply();}
        void received(String key){if(finished||key.isEmpty())return;finish();dialog.dismiss();
            if(stage>=0){store.map(key,SIX_PACKAGES[stage],SIX_NAMES[stage]);store.action(key,false,"guard");store.action(key,true,"app:"+SIX_PACKAGES[stage]);if(stage<5)new Learning(controls,stage+1).start();else remote(controls);}
            else{if(!store.mapped(key)){store.map(key,"","Button "+key);store.action(key,false,"none");store.action(key,true,"overlay");}mapping(controls,key);}
        }
    }
    private static void guard(ControlPanel s){Store store=new Store(s);boolean home=store.prefs.getBoolean("homeOverride",true);
        menu(s,"Remote guard · "+(RemoteGuardService.connected?"connected":RemoteGuardService.enabled(s)?"enabled, disconnected":"off"),new String[]{"Open TV Accessibility settings","Home override · "+(home?"on":"off"),"System Home settings"},i->{if(i==0)Actions.settings(s,Settings.ACTION_ACCESSIBILITY_SETTINGS);else if(i==1)store.prefs.edit().putBoolean("homeOverride",!home).apply();else Actions.settings(s,Settings.ACTION_HOME_SETTINGS);});
    }
    private static void connections(ControlPanel s){menu(s,"Inputs and connections",new String[]{"All TV inputs","AirPlay & Cast","CEC / HDMI Control settings"},i->{if(i==0)OverlayMenus.inputs(s,false,false);else if(i==2)Actions.settings(s,"com.android.tv.settings.CEC");else s.panel(new AlertDialog.Builder(s).setTitle("AirPlay & Cast").setMessage("Use AirPlay or Screen Mirroring from an Apple device, or Cast from a compatible app, on the same network.")
        .setPositiveButton("Open TCL AirPlay",(d,w)->{try{s.sendBroadcast(new Intent("Show.Home.AirplayAPK").setComponent(new ComponentName("com.tcl.airplay2","com.mediatek.partner.airplay.BootupReceiver")));}catch(RuntimeException e){Actions.settings(s,Settings.ACTION_SETTINGS);}}).setNeutralButton("TV settings",(d,w)->Actions.settings(s,Settings.ACTION_SETTINGS)).setNegativeButton("Close",null).create());});}
    private static void experiments(ControlPanel s){menu(s,"Parked video experiments",new String[]{"HDMI-only PiP · no helper","App-window multiview · optional ADB helper","Experiment notes"},i->{if(i==0)OverlayMenus.nativeExperiment(s);else if(i==1)OverlayMenus.multiview(s);else OverlayMenus.message(s,"Experiments preserved","Single HDMI playback works. Two-HDMI playback is unreliable on this firmware. App + HDMI showed both pictures, but Netflix's inset had a distorted aspect ratio and black bar during playback. The freeform caption reserves space. These experiments are parked; the launcher needs neither PiP nor its helper. Full handoff notes are saved in EXPERIMENTS.md with the source project.");});}
    private static void rename(ControlPanel s,String title,String previous,Consumer<String> saved){EditText input=new EditText(s);input.setSingleLine(true);input.setText(previous);input.setSelectAllOnFocus(true);
        s.panel(new AlertDialog.Builder(s).setTitle(title).setView(input).setPositiveButton("Save",(d,w)->{String value=input.getText().toString().trim();if(!value.isEmpty())saved.accept(value);}).setNegativeButton("Cancel",null).create());
    }
}
