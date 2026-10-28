package ca.jeeth.switchboard;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;

/** A one-time Android permission prompt; ordinary Continue Watching needs no helper. */
public final class ListingsPermissionActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);
        if(checkSelfPermission(WatchNextRepository.PERMISSION)==PackageManager.PERMISSION_GRANTED){finish();return;}
        if(state!=null)return;
        Store store=new Store(this);
        if(store.prefs.getBoolean("askedTvListings",false)&&!shouldShowRequestPermissionRationale(WatchNextRepository.PERMISSION)){
            new AlertDialog.Builder(this).setTitle("Allow TV listings access")
                .setMessage("Continue Watching reads show details and resume links shared by your streaming apps. Enable TV listings access for Switchboard in App permissions.")
                .setPositiveButton("App permissions",(d,w)->{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));finish();})
                .setNegativeButton("Cancel",(d,w)->finish()).setOnCancelListener(d->finish()).show();
        }else{store.prefs.edit().putBoolean("askedTvListings",true).apply();requestPermissions(new String[]{WatchNextRepository.PERMISSION},71);}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);WatchNextRepository.get(this).refresh(true);finish();}
}
