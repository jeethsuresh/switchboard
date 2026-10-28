package ca.jeeth.switchboard;

import android.app.AlertDialog;
import android.content.Intent;

final class WatchNextMenus {
    static void enable(ControlPanel s){s.dismissOverlay();s.startActivity(new Intent(s,ListingsPermissionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    static void show(ControlPanel s){WatchNextRepository repo=WatchNextRepository.get(s);
        LauncherSettings.menu(s,"Continue Watching · "+repo.entries.size()+" shared items",new String[]{repo.allowed()?"TV listings access · enabled":"Enable TV listings access","Refresh shared shows","Restore hidden shows","What can appear here?"},i->{switch(i){
            case 0->{if(!repo.allowed())enable(s);else OverlayMenus.message(s,"TV listings access","Switchboard can read searchable shows published by your streaming apps. Manage or revoke this permission in TV Settings → Apps → Switchboard → Permissions.");}
            case 1->{repo.refresh(true);OverlayMenus.message(s,"Refreshing Continue Watching","Switchboard reads the TV's shared Watch Next entries. Apps control which shows and progress they publish.");}
            case 2->repo.restoreHidden();
            case 3->OverlayMenus.message(s,"Continue Watching",explanation(repo));
        }});
    }
    static void empty(ControlPanel s){WatchNextRepository repo=WatchNextRepository.get(s);if(!repo.allowed()){enable(s);return;}
        s.panel(new AlertDialog.Builder(s).setTitle(repo.state==WatchNextRepository.State.ERROR?"Could not read shared shows":"No shared shows available")
            .setMessage(explanation(repo)).setPositiveButton("Refresh",(d,w)->repo.refresh(true)).setNeutralButton("Options",(d,w)->show(s)).setNegativeButton("Close",null).create());
    }
    static void item(ControlPanel s,WatchEntry e){
        String detail=e.episodeLabel();
        String progress=e.duration>0?(e.position/60000)+" / "+(e.duration/60000)+" min":"";
        s.panel(new AlertDialog.Builder(s).setTitle(e.title).setMessage(detail+(!detail.isEmpty()&&!progress.isEmpty()?"\n":"")+progress)
            .setPositiveButton("Resume",(d,w)->WatchNextRepository.open(s,e)).setNeutralButton("Hide this item",(d,w)->WatchNextRepository.get(s).hide(e)).setNegativeButton("Close",null).create());
    }
    private static String explanation(WatchNextRepository repo){
        StringBuilder text=new StringBuilder("Apps publish show titles, playback progress and resume links to Android Watch Next. Switchboard can show only items they share and that still open in an installed app. Google TV's cloud-only entries may be unavailable.\n\n");
        if(!repo.allowed())text.append("Enable TV listings access to read shared shows.");
        else if(repo.publishers.isEmpty())text.append("No publishers were visible in the last successful read. Some services do not publish searchable entries. Try playing and pausing a show, then return Home.");
        else{text.append("Published rows visible on this TV:\n");for(java.util.Map.Entry<String,Integer> entry:repo.publishers.entrySet())text.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');}
        return text.toString();
    }
}
