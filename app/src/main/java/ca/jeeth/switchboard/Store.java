package ca.jeeth.switchboard;

import android.content.*;
import android.view.KeyEvent;
import org.json.*;
import java.util.*;

final class Store {
    final SharedPreferences prefs;
    Store(Context c){prefs=c.getSharedPreferences("switchboard",Context.MODE_PRIVATE);}
    List<String> order(String row){
        List<String> ids=new ArrayList<>();
        try{JSONArray a=new JSONArray(prefs.getString("order:"+row,"[]"));for(int i=0;i<a.length();i++)ids.add(a.getString(i));}catch(JSONException ignored){}return ids;
    }
    void saveOrder(String row,List<Catalog.Tile> tiles){JSONArray a=new JSONArray();for(Catalog.Tile t:tiles)a.put(t.id);prefs.edit().putString("order:"+row,a.toString()).apply();}
    void sort(String row,List<Catalog.Tile> tiles){List<String> ids=order(row);tiles.sort(Comparator.comparingInt(t->{int i=ids.indexOf(t.id);return i<0?Integer.MAX_VALUE:i;}));}
    void used(String pkg){if(!pkg.isEmpty()&&!defaultHidden("app:"+pkg))prefs.edit().putLong("used:app:"+pkg,System.currentTimeMillis()).apply();}
    String label(Catalog.Tile tile){return prefs.getString("label:"+tile.id,tile.title);}
    List<Catalog.Tile> apps(List<Catalog.Tile> source,boolean all){
        List<Catalog.Tile> result=new ArrayList<>();
        for(Catalog.Tile t:source)if(all||t.intent!=null&&!prefs.getBoolean("hidden:"+t.id,defaultHidden(t.id)))result.add(t);
        if(!all&&prefs.getBoolean("customOrder",false))sort("grid",result);
        else result.sort((a,b)->{
            int used=Long.compare(prefs.getLong("used:"+b.id,0),prefs.getLong("used:"+a.id,0));
            if(used!=0)return used;
            int preferred=Integer.compare(preferred(a.id),preferred(b.id));return preferred!=0?preferred:a.title.compareToIgnoreCase(b.title);
        });
        return result;
    }
    Catalog.Tile recent(List<Catalog.Tile> source){
        Catalog.Tile recent=null;long latest=0;
        for(Catalog.Tile t:source){long time=prefs.getLong("used:"+t.id,0);if(t.intent!=null&&time>latest){recent=t;latest=time;}}
        return recent;
    }
    private static int preferred(String id){String[] ids={"app:com.netflix.ninja","app:com.google.android.youtube.tv","app:com.amazon.amazonvideo.livingroom","app:com.disney.disneyplus","app:ca.bellmedia.cravetv"};int n=Arrays.asList(ids).indexOf(id);return n<0?99:n;}
    private static boolean defaultHidden(String id){
        // Utility shortcuts remain in All apps. Newly installed apps appear automatically.
        return Arrays.asList("app:com.google.android.apps.tv.launcherx","app:com.android.tv.settings","app:com.tcl.tv.settings",
            "app:com.android.vending","app:com.google.android.play.games","app:com.tcl.dashboard","app:com.tcl.exhibit",
            "app:com.tcl.guard","app:com.tcl.smartalexa","app:com.tcl.ocean.instructions","app:com.tcl.suspension",
            "app:com.tcl.tv","app:com.tcl.messagebox","app:com.tcl.ui_mediaCenter","app:com.esaba.downloader").contains(id);
    }
    static String key(KeyEvent e){return e.getKeyCode()+":"+e.getScanCode();}
    static boolean protectedKey(int code){
        // OK is kept with navigation so apps and the mapping editor always remain operable.
        return code==KeyEvent.KEYCODE_HOME||code==KeyEvent.KEYCODE_BACK||code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN||code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT||code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_NUMPAD_ENTER||code==KeyEvent.KEYCODE_TV_INPUT||code>=KeyEvent.KEYCODE_TV_INPUT_HDMI_1&&code<=KeyEvent.KEYCODE_TV_INPUT_VGA_1;
    }
    void map(String key,String pkg,String name){prefs.edit().putString("map:"+key,pkg).putString("name:"+key,name).apply();}
    boolean mapped(String key){return prefs.contains("map:"+key);}
    String pkg(String key){return prefs.getString("map:"+key,"");}
    String name(String key){return prefs.getString("name:"+key,"Remote button");}
    String action(String key,boolean hold){return prefs.getString((hold?"hold:":"tap:")+key,hold?"app:"+pkg(key):"guard");}
    void action(String key,boolean hold,String action){prefs.edit().putString((hold?"hold:":"tap:")+key,action).apply();}
    long holdMs(){return prefs.getLong("holdMs",800);}
    Set<String> mappedKeys(){Set<String> keys=new TreeSet<>();for(String k:prefs.getAll().keySet())if(k.startsWith("map:"))keys.add(k.substring(4));return keys;}
    void unmap(String key){prefs.edit().remove("map:"+key).remove("name:"+key).remove("tap:"+key).remove("hold:"+key).apply();}
}
