package ca.jeeth.switchboard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;

public final class MainActivity extends Activity {
    private AppRepository repository;
    private DashboardView dashboard;
    private ControlPanel controls;
    private boolean resumed;
    private final Runnable changed=()->{if(dashboard!=null)dashboard.bind(!resumed||dashboard.findFocus()==null);};
    @Override public void onCreate(Bundle state){super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        repository=AppRepository.get(this);controls=new ControlPanel(this);dashboard=new DashboardView(controls,false);setContentView(dashboard);repository.listen(changed);
    }
    @Override public void onResume(){super.onResume();resumed=true;dashboard.center();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);controls.dismissOverlay();dashboard.center();}
    @Override public void onBackPressed(){controls.dismissOverlay();dashboard.center();}
    @Override public boolean onKeyDown(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_MENU){LauncherSettings.show(controls);return true;}return super.onKeyDown(key,event);}
    @Override public void onPause(){resumed=false;super.onPause();}
    @Override public void onDestroy(){repository.unlisten(changed);controls.dismissOverlay();super.onDestroy();}
}
