package ca.jeeth.switchboard;

import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import android.media.tv.*;
import java.util.*;

final class Catalog {
    static final class Tile {
        final String id, title, subtitle;
        final Intent intent;
        final Drawable icon;
        final TvInputInfo input;
        Tile(String id, String title, String subtitle, Intent intent, Drawable icon, TvInputInfo input) {
            this.id=id; this.title=title; this.subtitle=subtitle; this.intent=intent; this.icon=icon; this.input=input;
        }
    }
    static List<Tile> apps(Context c) {
        PackageManager pm=c.getPackageManager();
        Map<String,Tile> found=new LinkedHashMap<>();
        for (String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query=new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo r:pm.queryIntentActivities(query, 0)) {
                ActivityInfo a=r.activityInfo;
                if (a.packageName.equals(c.getPackageName()) || found.containsKey(a.packageName)) continue;
                Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(category)
                    .setComponent(new ComponentName(a.packageName,a.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                boolean archived=a.name.startsWith("com.google.android.archive.");
                found.put(a.packageName,new Tile("app:"+a.packageName,r.loadLabel(pm).toString(),
                    archived?"Archived · unavailable":category.equals(Intent.CATEGORY_LEANBACK_LAUNCHER)?"Installed":"Mobile app",
                    archived?null:launch,smallIcon(c,r.loadIcon(pm)),null));
            }
        }
        List<Tile> list=new ArrayList<>(found.values());
        list.sort(Comparator.comparing(t->t.title.toLowerCase(Locale.ROOT)));
        return list;
    }
    private static Drawable smallIcon(Context c,Drawable source) {
        int size=96;
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(size,size,android.graphics.Bitmap.Config.ARGB_8888);
        source.setBounds(0,0,size,size);source.draw(new android.graphics.Canvas(bitmap));
        return new android.graphics.drawable.BitmapDrawable(c.getResources(),bitmap);
    }
    static List<Tile> inputs(Context c) {
        List<Tile> list=new ArrayList<>();
        TvInputManager manager=(TvInputManager)c.getSystemService(Context.TV_INPUT_SERVICE);
        if(manager==null) return list;
        try {
            for(TvInputInfo i:manager.getTvInputList()) {
                if(!i.isPassthroughInput()) continue;
                CharSequence custom=i.loadCustomLabel(c);
                String title=(custom==null?i.loadLabel(c):custom).toString();
                int state=manager.getInputState(i.getId());
                String status=state==TvInputManager.INPUT_STATE_CONNECTED?"Connected":
                    state==TvInputManager.INPUT_STATE_CONNECTED_STANDBY?"Standby":"No signal";
                list.add(new Tile("input:"+i.getId(),title,status,
                    new Intent(Intent.ACTION_VIEW,TvContract.buildChannelUriForPassthroughInput(i.getId()))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),null,i));
            }
        } catch(RuntimeException ignored) { /* Capability report distinguishes an empty result. */ }
        return list;
    }
    static Intent appIntent(Context c,String pkg) {
        if(pkg.isEmpty()) return null;
        PackageManager pm=c.getPackageManager();
        Intent intent=pm.getLeanbackLaunchIntentForPackage(pkg);
        if(intent==null) intent=pm.getLaunchIntentForPackage(pkg);
        if(intent==null) return null;
        ResolveInfo resolved=pm.resolveActivity(intent,0);
        if(resolved==null || resolved.activityInfo.name.startsWith("com.google.android.archive.")) return null;
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
