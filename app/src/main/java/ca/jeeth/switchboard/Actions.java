package ca.jeeth.switchboard;

import android.content.*;
import android.media.tv.TvContract;
import android.provider.Settings;

final class Actions {
    static void tile(ControlPanel s,Catalog.Tile tile){execute(s,tile.id,tile.title);}
    static void execute(ControlPanel s,String action,String name){
        if(action.equals("none"))return;
        if(action.equals("overlay")){OverlayMenus.dashboard(s);return;}
        if(action.equals("settings")){LauncherSettings.show(s);return;}
        if(action.equals("home")){s.openHome();return;}
        if(action.equals("allapps")){LauncherSettings.allApps(s);return;}
        if(action.equals("inputs")){OverlayMenus.inputs(s,false,false);return;}
        Intent intent=null;String pkg=null;
        if(action.startsWith("app:")){pkg=action.substring(4);intent=Catalog.appIntent(s,pkg);}
        else if(action.startsWith("input:"))intent=new Intent(Intent.ACTION_VIEW,TvContract.buildChannelUriForPassthroughInput(action.substring(6))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if(intent==null){OverlayMenus.message(s,name+" is unavailable","This app is not installed, is archived, or cannot be launched. No installation page will be opened.");return;}
        try{s.dismissOverlay();s.startActivity(intent);if(pkg!=null)new Store(s).used(pkg);}catch(RuntimeException e){OverlayMenus.message(s,"Could not open "+name,"The TV refused this shortcut. Try TV Settings or the Inputs button.");}
    }
    static void settings(ControlPanel s,String action){try{s.dismissOverlay();s.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(RuntimeException e){try{s.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(RuntimeException ignored){OverlayMenus.message(s,"Settings unavailable","Use the TV's Settings button.");}}}
    static String label(ControlPanel s,String action){
        if(action.startsWith("app:")){for(Catalog.Tile t:AppRepository.get(s).apps)if(t.id.equals(action))return new Store(s).label(t);return switch(action.substring(4)){case ""->"Unavailable app";case "com.apple.atve.androidtv.appletv"->"Apple TV";case "tv.pluto.android"->"Pluto TV";case "com.tcl.tv.plus"->"TCL TV+";default->action.substring(4);};}
        if(action.startsWith("input:")){for(Catalog.Tile t:AppRepository.get(s).inputs)if(t.id.equals(action))return new Store(s).label(t);return "TV input";}
        return switch(action){case "none"->"Do nothing";case "guard"->"Hold hint / unavailable overlay";case "overlay"->"Quick controls overlay";case "settings"->"Switchboard settings";case "home"->"Switchboard Home";case "allapps"->"All apps";case "inputs"->"All inputs";default->action;};
    }
}
