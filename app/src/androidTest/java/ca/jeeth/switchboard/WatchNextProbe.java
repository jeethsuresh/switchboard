package ca.jeeth.switchboard;

import android.app.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.os.*;
import java.util.*;

/** On-device integration probe. Does not open shows, alter provider rows or print viewing titles. */
public final class WatchNextProbe extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private static void checkTextLayouts(android.view.View view){
        if(view.getVisibility()!=android.view.View.VISIBLE)return;
        if(view instanceof android.widget.TextView){android.widget.TextView text=(android.widget.TextView)view;
            // Detached descendants each have their own observer until attached to a real window.
            // TextView uses pre-draw to align/scroll single-line layouts inside the visible bounds.
            text.getViewTreeObserver().dispatchOnPreDraw();if(text.length()>0&&text.getLayout()==null)throw new AssertionError("A visible text label has not been laid out");}
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)checkTextLayouts(group.getChildAt(i));}
    }
    private static void key(DashboardView view,int key){view.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,key));view.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,key));}
    private static void expectFocus(DashboardView view,String prefix){android.view.View focused=view.findFocus();if(focused==null||focused.getContentDescription()==null||!focused.getContentDescription().toString().startsWith(prefix))throw new AssertionError("Expected focus on "+prefix);}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Context context=getTargetContext();
        if(context.checkSelfPermission(WatchNextRepository.PERMISSION)!=PackageManager.PERMISSION_GRANTED)throw new AssertionError("TV listings permission must be enabled before probing");
        WatchNextRepository[] ref={null};runOnMainSync(()->{ref[0]=WatchNextRepository.get(context);ref[0].refresh(true);});
        long deadline=SystemClock.elapsedRealtime()+15000;
        while(ref[0].state==WatchNextRepository.State.LOADING&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);
        WatchNextRepository repo=ref[0];
        result.putString("state",repo.state.toString());result.putInt("usableItems",repo.entries.size());result.putString("publishers",repo.publishers.toString());
        if(repo.state==WatchNextRepository.State.ERROR){
            try(Cursor raw=context.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI,null,null,null,null)){
                result.putString("providerColumns",raw==null?"no cursor":Arrays.toString(raw.getColumnNames()));
            }
            throw new AssertionError("Shared-show read failed");
        }
        if(repo.state!=WatchNextRepository.State.EMPTY&&repo.state!=WatchNextRepository.State.READY)throw new AssertionError("Read did not complete");
        for(WatchEntry entry:repo.entries){
            android.content.Intent intent=WatchNextRepository.resumeIntent(context,entry);
            if(intent==null||!entry.pkg.equals(intent.getPackage())||intent.getSelector()!=null)throw new AssertionError("Resume link did not stay scoped to its owning app");
        }
        AppRepository[] apps={null};runOnMainSync(()->apps[0]=AppRepository.get(context));
        deadline=SystemClock.elapsedRealtime()+10000;while(!apps[0].ready&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);
        List<String> artwork=new ArrayList<>();for(int i=0;i<Math.min(4,repo.entries.size());i++){String uri=repo.entries.get(i).artUri;if(!uri.isEmpty())artwork.add(uri);}
        java.util.concurrent.CountDownLatch images=new java.util.concurrent.CountDownLatch(artwork.size());
        java.util.concurrent.atomic.AtomicInteger loaded=new java.util.concurrent.atomic.AtomicInteger(),oversized=new java.util.concurrent.atomic.AtomicInteger();
        runOnMainSync(()->{for(String uri:artwork)ThumbnailCache.get(context).load(uri,bitmap->{if(bitmap!=null){loaded.incrementAndGet();if(Math.max(bitmap.getWidth(),bitmap.getHeight())>768)oversized.incrementAndGet();}images.countDown();});});
        if(!images.await(25,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Artwork requests did not complete");
        if(oversized.get()>0)throw new AssertionError("Artwork exceeded decoded size limit");
        result.putInt("visibleArtworkSupplied",artwork.size());result.putInt("visibleArtworkLoaded",loaded.get());
        final Throwable[] uiFailure={null};runOnMainSync(()->{try{
            android.view.ContextThemeWrapper themed=new android.view.ContextThemeWrapper(context,context.getApplicationInfo().theme);
            DashboardView dashboard=new DashboardView(new ControlPanel(themed),false);
            dashboard.measure(android.view.View.MeasureSpec.makeMeasureSpec(1920,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(1080,android.view.View.MeasureSpec.EXACTLY));dashboard.layout(0,0,1920,1080);dashboard.center();
            key(dashboard,android.view.KeyEvent.KEYCODE_DPAD_UP);expectFocus(dashboard,"Recent app");
            key(dashboard,android.view.KeyEvent.KEYCODE_DPAD_UP);expectFocus(dashboard,"Continue Watching");
            android.view.View first=dashboard.findFocus();
            if(repo.entries.size()>1){key(dashboard,android.view.KeyEvent.KEYCODE_DPAD_RIGHT);if(dashboard.findFocus()==first)throw new AssertionError("Watch row did not move right");key(dashboard,android.view.KeyEvent.KEYCODE_DPAD_LEFT);if(dashboard.findFocus()!=first)throw new AssertionError("Watch row did not return left");}
            for(int i=0;i<repo.entries.size()+4;i++)key(dashboard,android.view.KeyEvent.KEYCODE_DPAD_RIGHT);
            java.lang.reflect.Field start=DashboardView.class.getDeclaredField("watchStart");start.setAccessible(true);
            if(start.getInt(dashboard)!=Math.max(0,repo.entries.size()-4))throw new AssertionError("Watch viewport did not reach its last item");
            key(dashboard,android.view.KeyEvent.KEYCODE_DPAD_DOWN);expectFocus(dashboard,"Recent app");
            dashboard.center();
            // Binding requests another layout, normally supplied by ViewRoot on the next frame.
            // This detached preview must perform that pass explicitly before drawing TextViews.
            dashboard.measure(android.view.View.MeasureSpec.makeMeasureSpec(1920,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(1080,android.view.View.MeasureSpec.EXACTLY));dashboard.layout(0,0,1920,1080);
            dashboard.getViewTreeObserver().dispatchOnPreDraw();
            checkTextLayouts(dashboard);
            android.view.ViewGroup center=(android.view.ViewGroup)dashboard.findFocus();
            android.widget.TextView title=(android.widget.TextView)center.getChildAt(2);
            result.putString("labelMetrics", "native labels laid out and aligned before drawing");
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(1920,1080,android.graphics.Bitmap.Config.ARGB_8888);
            dashboard.draw(new android.graphics.Canvas(bitmap));
            try(java.io.OutputStream file=context.openFileOutput("watch-home-preview.png",Context.MODE_PRIVATE)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,file);}bitmap.recycle();
        }catch(Throwable failure){uiFailure[0]=failure;}});
        if(uiFailure[0]!=null)throw new AssertionError("Native row checks failed",uiFailure[0]);
        result.putString("navigation","native views: two Up, horizontal movement, full-row scrolling and Down-to-recent passed off-screen");
        result.putString("preview","private app file watch-home-preview.png");
        result.putString("validation","read-only provider query and package-scoped resume links passed");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("failure",error.toString());finish(Activity.RESULT_CANCELED,result);}}
}
