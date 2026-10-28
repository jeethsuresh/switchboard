package ca.jeeth.switchboard;

import android.accessibilityservice.AccessibilityService;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import java.util.*;

public final class RemoteGuardService extends AccessibilityService {
    static volatile boolean connected;
    static RemoteGuardService instance;
    private ControlPanel controls;
    ControlPanel controls(){if(controls==null)controls=new ControlPanel(this);return controls;}
    static boolean enabled(android.content.Context context){
        String value=android.provider.Settings.Secure.getString(context.getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if(value==null)return false;
        android.content.ComponentName own=new android.content.ComponentName(context,RemoteGuardService.class);
        for(String entry:value.split(":"))if(own.equals(android.content.ComponentName.unflattenFromString(entry)))return true;
        return false;
    }
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Map<String,Press> presses=new HashMap<>();
    private final Map<String,String> learningPresses=new HashMap<>();
    private final Map<Integer,HomePress> homePresses=new HashMap<>();
    private long lastHomeRedirect;
    private String lastApp="";
    private Store store;
    private AlertDialog overlay;
    private final class HomePress {
        final HoldGate gate=new HoldGate();
        final Runnable hold=()->{if(gate.tick(SystemClock.uptimeMillis(),500))OverlayMenus.dashboard(controls());};
    }
    private final class Press {
        final HoldGate gate=new HoldGate();
        final String pkg,name,tapAction,holdAction;
        final long threshold;
        final Runnable fire;
        Press(String key){
            pkg=store.pkg(key);name=store.name(key);tapAction=store.action(key,false);holdAction=store.action(key,true);threshold=store.holdMs();
            fire=()->{if(gate.tick(SystemClock.uptimeMillis(),threshold))perform(this,true);};
        }
    }
    @Override protected void onServiceConnected(){store=new Store(this);AppRepository.get(this);connected=true;instance=this;}
    @Override protected boolean onKeyEvent(KeyEvent event){
        if(store==null)return false;
        if(event.getKeyCode()==KeyEvent.KEYCODE_HOME){
            int device=event.getDeviceId();
            if(event.getAction()==KeyEvent.ACTION_DOWN && store.prefs.getBoolean("homeOverride",true)){
                if(!homePresses.containsKey(device)){HomePress press=new HomePress();press.gate.down(SystemClock.uptimeMillis());homePresses.put(device,press);handler.postDelayed(press.hold,500);}return true;
            }
            if(event.getAction()==KeyEvent.ACTION_UP && homePresses.containsKey(device)){
                HomePress press=homePresses.remove(device);handler.removeCallbacks(press.hold);
                if(event.isCanceled()){press.gate.cancel();return true;}
                if(press.gate.tick(SystemClock.uptimeMillis(),500))OverlayMenus.dashboard(controls());
                if(press.gate.up())openHome();return true;
            }
            return false;
        }
        if(Store.protectedKey(event.getKeyCode()))return false;
        String key=Store.key(event), physical=event.getDeviceId()+"/"+key;
        boolean down=event.getAction()==KeyEvent.ACTION_DOWN, up=event.getAction()==KeyEvent.ACTION_UP;
        if(!down&&!up)return false;
        // An in-flight key remains consumed through UP even if learning times out.
        if(learningPresses.containsKey(physical)){
            if(up){learningPresses.remove(physical);store.prefs.edit().putString("learnedKey",key).remove("learningUntil").apply();}
            return true;
        }
        if(down && store.prefs.getLong("learningUntil",0)>SystemClock.elapsedRealtime()){
            learningPresses.put(physical,key);return true;
        }
        Press press=presses.get(physical);
        if(down){
            if(press!=null)return true;
            if(!store.mapped(key))return false;
            press=new Press(key);press.gate.down(SystemClock.uptimeMillis());presses.put(physical,press);
            handler.postDelayed(press.fire,press.threshold);return true;
        }
        if(press==null)return false;
        presses.remove(physical);handler.removeCallbacks(press.fire);
        if(event.isCanceled()){press.gate.cancel();return true;}
        // A delayed main thread must still recognize a genuine long press on UP.
        if(press.gate.tick(SystemClock.uptimeMillis(),press.threshold))perform(press,true);
        if(press.gate.up())perform(press,false);
        return true;
    }
    private void perform(Press press,boolean hold){
        String action=hold?press.holdAction:press.tapAction;
        if(action.equals("guard")){
            String target=press.holdAction.startsWith("app:")?press.holdAction.substring(4):press.pkg;
            if(Catalog.appIntent(this,target)==null)unavailable(press.name);
            else showOverlay("Hold to open "+press.name,"Hold this button for "+(press.threshold/1000.0)+" seconds. A tap keeps you here.");
        }else Actions.execute(controls(),action,press.name);
    }
    private void unavailable(String name){showOverlay(name+" is unavailable","This app is not installed, is archived, or cannot be launched. No installation page will be opened.");}
    private void showOverlay(String title,String message){
        if(overlay!=null)overlay.dismiss();
        try {
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Dismiss",(d,w)->d.dismiss()).create();
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
            dialog.show();overlay=dialog;
            handler.postDelayed(()->{if(dialog.isShowing())dialog.dismiss();},4500);
        } catch(RuntimeException ignored){ /* Never fall back to a store or foreground activity. */ }
    }
    void openHome(){
        dismissOverlay();
        lastHomeRedirect=SystemClock.uptimeMillis();
        try{startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));}
        catch(RuntimeException ignored){}
    }
    void dismissOverlay(){if(overlay!=null){overlay.dismiss();overlay=null;}}
    void panel(AlertDialog dialog){
        dismissOverlay();dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);dialog.show();overlay=dialog;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(store!=null && event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName()!=null){
            String pkg=event.getPackageName().toString();
            String cls=event.getClassName()==null?"":event.getClassName().toString();
            // Activity metadata only: exclude dialogs and child-view focus events.
            if(AppRepository.get(this).knownApp(pkg) && !pkg.equals("com.google.android.apps.tv.launcherx") && !cls.startsWith("android.widget.") && !cls.contains("Dialog")){
                if(!pkg.equals(lastApp)){store.used(pkg);lastApp=pkg;}
            }else if(pkg.equals(getPackageName())||pkg.equals("com.tcl.tv"))lastApp="";
        }
        // TCL may handle Home before delivering a key. Window metadata provides a fallback;
        // no screen contents are requested or inspected.
        if(store!=null && store.prefs.getBoolean("homeOverride",true) && event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            && "com.google.android.apps.tv.launcherx".contentEquals(event.getPackageName()==null?"":event.getPackageName())
            && "com.google.android.apps.tv.launcherx.home.HomeActivity".contentEquals(event.getClassName()==null?"":event.getClassName())
            && homePresses.isEmpty() && SystemClock.uptimeMillis()-lastHomeRedirect>800)openHome();
    }
    private void clear(){
        for(Press press:presses.values()){handler.removeCallbacks(press.fire);press.gate.cancel();}
        for(HomePress press:homePresses.values()){handler.removeCallbacks(press.hold);press.gate.cancel();}
        presses.clear();learningPresses.clear();homePresses.clear();
        if(overlay!=null){overlay.dismiss();overlay=null;}
    }
    @Override public void onInterrupt(){clear();}
    @Override public void onDestroy(){connected=false;if(instance==this)instance=null;clear();handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
