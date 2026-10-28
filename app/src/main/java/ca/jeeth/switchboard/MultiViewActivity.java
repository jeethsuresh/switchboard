package ca.jeeth.switchboard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.tv.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;

public final class MultiViewActivity extends Activity {
    private static WeakReference<MultiViewActivity> current=new WeakReference<>(null);
    static boolean focusInset;
    private TvView main,pip;
    private TextView status;
    private String mainId="",pipId="",mainState="Waiting",pipState="Not selected";
    private boolean started,mainVideoAvailable,insetRequested;
    private ControlPanel controls;
    private Button controlsButton;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable hideStatus=()->{if(mainVideoAvailable&&(pipId.isEmpty()||pipState.equals("Video available")))status.setVisibility(View.GONE);};
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    static void finishExperiment(){MultiViewActivity a=current.get();if(a!=null)a.finish();}
    static void focusPane(boolean inset){
        focusInset=inset;MultiViewActivity a=current.get();
        if(a!=null)(inset&&!a.pipId.isEmpty()?a.pip:a.main).requestFocus();
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);current=new WeakReference<>(this);controls=new ControlPanel(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        main=new TvView(this);main.setFocusable(true);root.addView(main,new FrameLayout.LayoutParams(-1,-1));
        pip=new TvView(this);pip.setFocusable(true);pip.setZOrderMediaOverlay(true);root.addView(pip);
        main.setCallback(callback(false));pip.setCallback(callback(true));
        status=new TextView(this);status.setTextSize(13);status.setTextColor(Color.WHITE);status.setPadding(dp(12),dp(8),dp(12),dp(8));status.setBackgroundColor(0xc8101714);
        root.addView(status,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        controlsButton=new Button(this);controlsButton.setText("PiP controls");controlsButton.setAllCaps(false);
        controlsButton.setOnClickListener(v->OverlayMenus.nativeExperiment(controls));
        FrameLayout.LayoutParams buttonParams=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);
        buttonParams.setMargins(dp(12),dp(12),dp(12),dp(12));root.addView(controlsButton,buttonParams);
        read(getIntent());position();
    }
    private void read(Intent intent){
        String m=intent.getStringExtra("mainInput"),p=intent.getStringExtra("pipInput");m=m==null?"":m;p=p==null?"":p;
        if(m.equals(mainId)&&p.equals(pipId))return;
        if(started&&m.equals(mainId)){
            // Updating the inset must not tear down a working main HDMI session.
            handler.removeCallbacksAndMessages(null);pip.reset();insetRequested=false;pipId=p;
            pipState=p.isEmpty()?"Not selected":"Waiting for main video";
            if(mainVideoAvailable)startInset();
            showStatus();return;
        }
        release();mainId=m;pipId=p;mainState="Waiting";pipState=p.isEmpty()?"Not selected":"Waiting for main video";showStatus();
    }
    @Override public void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);read(intent);if(hasWindowFocus())start();position();}
    private void position(){
        int width=getResources().getDisplayMetrics().widthPixels*MultiViewController.nativeSize/100;
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width,width*9/16);
        p.gravity=(MultiViewController.corner%2==0?Gravity.END:Gravity.START)|(MultiViewController.corner<2?Gravity.TOP:Gravity.BOTTOM);
        p.setMargins(dp(12),dp(12),dp(12),dp(45));pip.setLayoutParams(p);pip.setVisibility(pipId.isEmpty()?View.GONE:View.VISIBLE);
    }
    private TvView.TvInputCallback callback(boolean inset){return new TvView.TvInputCallback(){
        @Override public void onVideoAvailable(String id){if(!inset){mainVideoAvailable=true;startInset();}update(inset,"Video available");}
        @Override public void onVideoUnavailable(String id,int reason){if(!inset)mainVideoAvailable=false;update(inset,reason(reason));}
        @Override public void onConnectionFailed(String id){if(!inset)mainVideoAvailable=false;update(inset,"Session refused");}
        @Override public void onDisconnected(String id){if(!inset)mainVideoAvailable=false;update(inset,"Disconnected");}
    };}
    private static String reason(int value){return switch(value){
        case 0->"Unspecified video error (0)";case 1->"Tuning (1)";case 2->"Weak signal / no usable video (2)";
        case 3->"Buffering (3)";case 4->"Audio only (4)";case 5->"Not connected (5)";case 6->"Insufficient video resources (6)";default->"No video (reason "+value+")";
    };}
    private void update(boolean inset,String value){if(inset)pipState=value;else mainState=value;android.util.Log.i("SwitchboardVideo",(inset?"Inset ["+pipId:"Main ["+mainId)+"] "+value);showStatus();}
    private void showStatus(){if(status!=null){handler.removeCallbacks(hideStatus);status.setText("EXPERIMENT  ·  Main: "+mainState+(pipId.isEmpty()?"":"  ·  Inset: "+pipState)+"\nPiP controls button / Menu: controls. Back exits. Hold Home also works with the remote guard.");status.setVisibility(isInMultiWindowMode()?View.GONE:View.VISIBLE);if(controlsButton!=null)controlsButton.setVisibility(isInMultiWindowMode()?View.GONE:View.VISIBLE);if(mainVideoAvailable)handler.postDelayed(hideStatus,5000);}}
    private void start(){
        if(started||mainId.isEmpty())return;started=true;position();
        try{
            main.setStreamVolume(1f);pip.setStreamVolume(0f);main.tune(mainId,TvContract.buildChannelUriForPassthroughInput(mainId));
        }catch(Exception e){update(false,"Session denied");}
    }
    private void startInset(){
        if(!started||!mainVideoAvailable||insetRequested||pipId.isEmpty())return;
        insetRequested=true;String target=pipId;
        handler.postDelayed(()->{
            if(!started||!mainVideoAvailable||!pipId.equals(target)){insetRequested=false;return;}
            try{pip.tune(target,TvContract.buildChannelUriForPassthroughInput(target));}catch(Exception e){update(true,"Session denied");}
        },500);
    }
    private void release(){handler.removeCallbacksAndMessages(null);started=false;mainVideoAvailable=false;insetRequested=false;if(main!=null)main.reset();if(pip!=null)pip.reset();}
    @Override public void onStart(){super.onStart();start();}
    // Keep a visible HDMI pane alive while a freeform streaming app receives focus.
    @Override public void onStop(){release();super.onStop();}
    @Override public void onDestroy(){controls.dismissOverlay();release();if(current.get()==this)current.clear();super.onDestroy();}
    @Override public void onMultiWindowModeChanged(boolean inMultiWindow){super.onMultiWindowModeChanged(inMultiWindow);showStatus();position();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getKeyCode()==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_UP)OverlayMenus.nativeExperiment(controls);return true;}
        if(event.getKeyCode()==KeyEvent.KEYCODE_BACK)return super.dispatchKeyEvent(event);
        if(controlsButton.hasFocus())return super.dispatchKeyEvent(event);
        TvView target=focusInset&&!pipId.isEmpty()?pip:main;
        return target.dispatchKeyEvent(event)||super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed(){MultiViewController.exit(controls);}
}
