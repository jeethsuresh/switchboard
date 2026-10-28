package ca.jeeth.switchboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;

/** Shows the same controls in an activity or above the current screen. */
final class ControlPanel extends ContextWrapper {
    private final RemoteGuardService service;
    private AlertDialog dialog;

    ControlPanel(Context context){
        super(context);
        service=context instanceof RemoteGuardService?(RemoteGuardService)context:null;

    }
    void panel(AlertDialog next){
        if(service!=null){service.panel(next);return;}
        Activity activity=(Activity)getBaseContext();
        if(activity.isFinishing()||activity.isDestroyed())return;
        dismissOverlay();next.show();dialog=next;
    }
    void dismissOverlay(){
        if(service!=null){service.dismissOverlay();return;}
        if(dialog!=null){dialog.dismiss();dialog=null;}
    }
    void openHome(){
        dismissOverlay();
        startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }
}
