package ca.jeeth.switchboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Fixed native view hierarchy, reused for Home and the accessibility overlay. */
final class DashboardView extends FrameLayout {
    static final int BG=Color.rgb(12,18,21),SURFACE=Color.rgb(25,34,40),WHITE=Color.rgb(239,244,245),MUTED=Color.rgb(149,169,178),ACCENT=Color.rgb(155,232,205);
    private final ControlPanel controls;
    private final Store store;
    private final AppRepository repository;
    private final WatchNextRepository watching;
    private final Map<Integer,Card> cards=new HashMap<>();
    private final Card[] appCards=new Card[16],inputCards=new Card[3],watchCards=new Card[4];
    private final TextView brand,appsHeading,inputHeading,hint,pageLabel,watchHeading,watchPage;
    private final Card recent,hub,settings;
    private List<Catalog.Tile> apps=Collections.emptyList();
    private List<WatchEntry> watchEntries=Collections.emptyList();
    private int page,watchStart,watchReturnSlot;
    private final boolean overlay;
    private boolean bound;
    private List<Catalog.Tile> catalogSnapshot;
    private final Runnable watchChanged=()->bindWatching(false);
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener preferences=(p,key)->{if(key!=null&&(key.startsWith("label:")||key.startsWith("hidden:")||key.startsWith("order:")||key.equals("customOrder")))post(()->bind(true));};

    DashboardView(ControlPanel s,boolean overlay){
        super(s);controls=s;this.overlay=overlay;store=new Store(s);repository=AppRepository.get(s);watching=WatchNextRepository.get(s);
        setBackgroundColor(BG);setPadding(0,0,0,0);
        brand=label(overlay?"SWITCHBOARD  /  QUICK CONTROLS":"SWITCHBOARD",15,ACCENT);brand.setLetterSpacing(.14f);brand.setTypeface(null,Typeface.BOLD);addView(brand);
        watchHeading=heading("CONTINUE WATCHING");watchPage=label("",11,MUTED);watchPage.setGravity(Gravity.RIGHT);addView(watchPage);
        inputHeading=heading("INPUTS");appsHeading=heading("APPS  /  RECENT");
        pageLabel=label("",11,MUTED);pageLabel.setGravity(Gravity.RIGHT);addView(pageLabel);
        hint=label("",11,MUTED);addView(hint);
        hub=card(HomeNavigation.HUB);hub.set("✦","Switchboard",overlay?"OK closes controls":"Choose a direction",null);
        hub.setOnClickListener(v->{if(overlay)controls.dismissOverlay();else LauncherSettings.allApps(controls);});
        recent=card(HomeNavigation.RECENT);
        settings=card(HomeNavigation.SETTINGS);settings.set("⚙","Settings","",null);settings.setOnClickListener(v->LauncherSettings.show(controls));
        for(int i=0;i<3;i++)inputCards[i]=card(HomeNavigation.INPUT+i);
        for(int i=0;i<16;i++)appCards[i]=card(HomeNavigation.APP+i);
        for(int i=0;i<4;i++){watchCards[i]=card(HomeNavigation.WATCH+i);final int slot=i;
            watchCards[i].setOnFocusChangeListener((v,focused)->{if(focused){watchReturnSlot=slot;updateHint();}else post(()->{if(!watchFocused()){bindWatching(false);updateHint();}});});
        }
        bind(true);
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String text,int size,int color){TextView v=new TextView(getContext());v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setSingleLine(true);v.setEllipsize(TextUtils.TruncateAt.END);return v;}
    private TextView heading(String text){TextView v=label(text,11,MUTED);v.setLetterSpacing(.12f);addView(v);return v;}
    private Card card(int node){Card c=new Card(getContext(),node>=HomeNavigation.WATCH);c.setId(View.generateViewId());cards.put(node,c);addView(c);
        c.setOnKeyListener((v,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            int direction=switch(key){case KeyEvent.KEYCODE_DPAD_LEFT->0;case KeyEvent.KEYCODE_DPAD_RIGHT->1;case KeyEvent.KEYCODE_DPAD_UP->2;case KeyEvent.KEYCODE_DPAD_DOWN->3;default->-1;};
            if(direction<0){if(key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN){if(!watchFocused())changePage(key==KeyEvent.KEYCODE_CHANNEL_DOWN?1:-1);return true;}return false;}
            if(node>=HomeNavigation.WATCH){navigateWatch(node-HomeNavigation.WATCH,direction);return true;}
            if(node>=HomeNavigation.APP&&node<HomeNavigation.APP+16){int col=(node-HomeNavigation.APP)%4;
                if(direction==HomeNavigation.RIGHT&&col==3&&(page+1)*16<apps.size()){changePage(1);cards.get(node-3).requestFocus();return true;}
                if(direction==HomeNavigation.LEFT&&col==0&&page>0){changePage(-1);cards.get(node+3).requestFocus();return true;}
            }
            int target=HomeNavigation.next(node,direction);
            if(target==HomeNavigation.WATCH){bindWatching(true);target+=Math.min(watchReturnSlot,watchSlots()-1);}
            cards.get(target).requestFocus();return true;
        });return c;
    }
    private int watchSlots(){return Math.max(1,Math.min(4,watchEntries.size()-watchStart));}
    private boolean watchFocused(){for(Card c:watchCards)if(c!=null&&c.hasFocus())return true;return false;}
    private void navigateWatch(int slot,int direction){
        if(direction==HomeNavigation.DOWN){recent.requestFocus();bindWatching(false);return;}
        if(direction==HomeNavigation.UP)return;
        if(direction==HomeNavigation.RIGHT){
            if(slot+1<watchSlots())watchCards[slot+1].requestFocus();
            else if(watchStart+watchSlots()<watchEntries.size()){watchStart++;renderWatchCards();updateHint();}
        }else if(slot>0)watchCards[slot-1].requestFocus();
        else if(watchStart>0){watchStart--;renderWatchCards();updateHint();}
    }
    void center(){page=0;watchStart=0;watchReturnSlot=0;watching.refresh(false);bind(true);hub.requestFocusFromTouch();}
    void bind(boolean reorder){
        if(reorder||!bound||catalogSnapshot!=repository.apps){catalogSnapshot=repository.apps;apps=store.apps(repository.apps,false);page=Math.min(page,Math.max(0,(apps.size()-1)/16));bound=true;}
        List<Catalog.Tile> ports=repository.hdmi();
        for(int i=0;i<3;i++){
            final Catalog.Tile tile=i<ports.size()?ports.get(i):null;Card c=inputCards[i];
            String title=tile==null?"":store.label(tile);
            if(title.matches("(?i)HDMI\\s*"+(i+1)))title="";
            if(tile!=null&&title.isEmpty())for(Catalog.Tile child:repository.inputs)
                if(tile.input.getId().equals(child.input.getParentId())&&!child.title.matches("(?i)HDMI.*")){title=store.label(child);break;}
            c.set("HDMI "+(i+1),title,"",null);c.setContentDescription("HDMI "+(i+1)+(tile==null?"":", "+store.label(tile)));
            c.setOnClickListener(v->{if(tile!=null)Actions.tile(controls,tile);else OverlayMenus.inputs(controls,false,false);});
            c.setOnLongClickListener(v->{if(tile!=null)LauncherSettings.tile(controls,tile,()->bind(false));return true;});
        }
        Catalog.Tile last=store.recent(repository.apps);
        recent.set(last==null?"↑":"","Recent app",last==null?"Open an app to begin":store.label(last),last==null?null:last.icon);
        recent.setContentDescription(last==null?"Recently used app, no history yet":"Recent app · "+store.label(last));
        recent.setOnClickListener(v->{if(last!=null)Actions.tile(controls,last);else LauncherSettings.allApps(controls);});
        for(int i=0;i<16;i++){
            int index=page*16+i;Card c=appCards[i];Catalog.Tile tile=index<apps.size()?apps.get(index):null;
            c.set(tile==null?"+":"",tile==null?"Add shortcut":store.label(tile),"",tile==null?null:tile.icon);
            c.setContentDescription(tile==null?"Empty app slot, choose an app":store.label(tile));
            c.setOnClickListener(v->{if(tile==null)LauncherSettings.addShortcut(controls,()->bind(true));else Actions.tile(controls,tile);});
            c.setOnLongClickListener(v->{if(tile!=null)LauncherSettings.tile(controls,tile,()->bind(true));else LauncherSettings.addShortcut(controls,()->bind(true));return true;});c.setAlpha(tile==null?.42f:1f);
        }
        appsHeading.setText(store.prefs.getBoolean("customOrder",false)?"APPS  /  CUSTOM ORDER":"APPS  /  RECENTLY USED");
        int pages=Math.max(1,(apps.size()+15)/16);pageLabel.setText(pages>1?(page+1)+" / "+pages+(page+1<pages?"  →":"") : "4 × 4");
        bindWatching(false);updateHint();
    }
    private void bindWatching(boolean force){
        // Keep a focused show stable during background updates, but remove dismissed/unavailable items.
        boolean focused=watchFocused(),removed=false;
        if(focused&&!watchEntries.isEmpty()){
            String key=watchEntries.get(Math.min(watchStart+watchReturnSlot,watchEntries.size()-1)).key;
            removed=watching.entries.stream().noneMatch(e->e.key.equals(key));
        }
        if(force||!focused||watchEntries.isEmpty()||removed||!watching.allowed()){
            watchEntries=watching.entries;watchStart=Math.max(0,Math.min(watchStart,Math.max(0,watchEntries.size()-4)));
            watchReturnSlot=Math.min(watchReturnSlot,watchSlots()-1);renderWatchCards();
            if(focused&&!watchCards[watchReturnSlot].hasFocus())watchCards[watchReturnSlot].requestFocus();
        }
    }
    private void renderWatchCards(){
        int count=watchSlots();
        for(int i=0;i<4;i++){
            Card c=watchCards[i];c.setVisibility(i<count?VISIBLE:INVISIBLE);if(i>=count)continue;
            final WatchEntry e=watchEntries.isEmpty()?null:watchEntries.get(watchStart+i);
            if(e==null){
                String title=switch(watching.state){case PERMISSION->"Enable Continue Watching";case LOADING->"Loading shared shows…";case ERROR->"Shared shows unavailable";default->"No shared shows yet";};
                String detail=switch(watching.state){case PERMISSION->"OK to allow TV listings access";case LOADING->"Reading local app entries";default->"OK for details / refresh";};
                c.set("▶",title,detail,null);c.progress.setVisibility(GONE);c.artwork("");c.setContentDescription("Continue Watching · "+title);
                c.setOnClickListener(v->WatchNextMenus.empty(controls));c.setOnLongClickListener(v->{WatchNextMenus.show(controls);return true;});
            }else{
                Catalog.Tile app=null;for(Catalog.Tile t:repository.apps)if(t.id.equals("app:"+e.pkg)){app=t;break;}
                String appName=app==null?e.pkg:store.label(app);String episode=e.episodeLabel();
                String detail=(episode.isEmpty()?appName:episode+" · "+appName)+(e.type==1?" · Next episode":e.type==2?" · New":"");
                c.set(app==null?"▶":"",e.title,detail,app==null?null:app.icon);
                c.progress.setVisibility(e.duration>0&&e.type==0?VISIBLE:GONE);c.progress.setProgress(e.progress());
                c.artwork(e.artUri);
                c.setContentDescription("Continue Watching · "+e.title+" · "+detail);
                c.setOnClickListener(v->WatchNextRepository.open(controls,e));c.setOnLongClickListener(v->{WatchNextMenus.item(controls,e);return true;});
            }
        }
        watchPage.setText(watchEntries.isEmpty()?"":(watchStart+1)+"–"+(watchStart+count)+" / "+watchEntries.size());
    }
    private void updateHint(){
        if(watchFocused()&&!watchEntries.isEmpty()){
            int at=watchStart+watchReturnSlot;if(at<watchEntries.size()){WatchEntry e=watchEntries.get(at);hint.setText(e.title+"  ·  "+e.episodeLabel()+"  ·  Hold OK for options");return;}
        }
        hint.setText(!repository.ready?"Loading local apps…":overlay?"Back closes  ·  ↑ ↑ Continue Watching  ·  Hold OK to customize":"Hold Home for quick controls  ·  ↑ ↑ Continue Watching  ·  Hold OK to customize");
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();store.prefs.registerOnSharedPreferenceChangeListener(preferences);watching.listen(watchChanged);watching.refresh(false);}
    @Override protected void onDetachedFromWindow(){watching.unlisten(watchChanged);store.prefs.unregisterOnSharedPreferenceChangeListener(preferences);super.onDetachedFromWindow();}
    private void changePage(int delta){int next=page+delta;if(next>=0&&next*16<apps.size()){page=next;bind(false);}}
    private void place(View v,float x,float y,float w,float h,int width,int height){v.layout(Math.round(x*width),Math.round(y*height),Math.round((x+w)*width),Math.round((y+h)*height));}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);setMeasuredDimension(w,h);
        for(int i=0;i<getChildCount();i++){View v=getChildAt(i);boolean watch=Arrays.asList(watchCards).contains(v),input=Arrays.asList(inputCards).contains(v);
            float cw=v==brand?.75f:v==hint?.92f:v==watchHeading?.72f:v==watchPage?.155f:v==appsHeading?.36f:v==pageLabel?.14f:v==inputHeading?.19f:watch?.2225f:v==hub||v==recent||v==settings?.19f:input?.173f:.123f;
            float ch=v instanceof Card?(watch?.17f:v==recent?.18f:v==hub?.15f:v==settings||input?.13f:.12f):v==brand?.045f:v==watchHeading||v==watchPage?.035f:.04f;
            v.measure(MeasureSpec.makeMeasureSpec(Math.round(w*cw),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(h*ch),MeasureSpec.EXACTLY));
        }
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){int w=r-l,h=b-t;
        place(brand,.04f,.02f,.75f,.045f,w,h);place(watchHeading,.04f,.077f,.72f,.035f,w,h);place(watchPage,.8f,.077f,.155f,.035f,w,h);
        for(int i=0;i<4;i++)place(watchCards[i],.04f+i*.2325f,.125f,.2225f,.17f,w,h);
        place(inputHeading,.04f,.335f,.19f,.04f,w,h);place(appsHeading,.445f,.335f,.36f,.04f,w,h);place(pageLabel,.817f,.335f,.14f,.04f,w,h);
        place(recent,.235f,.34f,.19f,.18f,w,h);place(hub,.235f,.56f,.19f,.15f,w,h);place(settings,.235f,.79f,.19f,.13f,w,h);
        for(int i=0;i<3;i++)place(inputCards[i],.04f,.405f+i*.165f,.173f,.13f,w,h);
        for(int i=0;i<16;i++)place(appCards[i],.445f+(i%4)*.129f,.385f+(i/4)*.1275f,.123f,.12f,w,h);
        place(hint,.04f,.94f,.92f,.04f,w,h);
    }
    private final class Card extends LinearLayout {
        final ImageView icon;final TextView glyph,title,subtitle;final ProgressBar progress;
        final WatchArtDrawable art;private String artKey="";
        Card(Context context,boolean watch){super(context);setOrientation(VERTICAL);setGravity(Gravity.CENTER);setPadding(dp(8),dp(5),dp(8),dp(5));setFocusable(true);setFocusableInTouchMode(true);setClickable(true);
            StateListDrawable background=new StateListDrawable();background.addState(new int[]{android.R.attr.state_focused},shape(Color.rgb(36,65,61),ACCENT));background.addState(new int[]{android.R.attr.state_pressed},shape(Color.rgb(36,65,61),ACCENT));background.addState(new int[]{},shape(SURFACE,Color.TRANSPARENT));art=watch?new WatchArtDrawable(getResources().getDisplayMetrics().density):null;setBackground(art==null?background:art);
            icon=new ImageView(context);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);addView(icon,new LinearLayout.LayoutParams(dp(34),dp(26)));
            glyph=label("",20,ACCENT);glyph.setGravity(Gravity.CENTER);addView(glyph,new LinearLayout.LayoutParams(-1,dp(27)));
            title=label("",13,WHITE);title.setGravity(Gravity.CENTER);title.setTypeface(null,Typeface.BOLD);addView(title,new LinearLayout.LayoutParams(-1,dp(21)));
            subtitle=label("",10,MUTED);subtitle.setGravity(Gravity.CENTER);addView(subtitle,new LinearLayout.LayoutParams(-1,dp(16)));
            progress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));progress.setVisibility(GONE);addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        }
        void set(String symbol,String name,String detail,Drawable drawable){icon.setImageDrawable(drawable);icon.setVisibility(drawable==null?GONE:VISIBLE);glyph.setText(symbol);glyph.setVisibility(symbol.isEmpty()?GONE:VISIBLE);title.setText(name);title.setVisibility(name.isEmpty()?GONE:VISIBLE);subtitle.setText(detail);subtitle.setVisibility(detail.isEmpty()?GONE:VISIBLE);}
        void artwork(String uri){
            if(art==null)return;
            if(!artKey.equals(uri)){artKey=uri;art.bitmap(null);setGravity(Gravity.CENTER);}
            if(uri.isEmpty())return;
            if(art.bitmap()!=null){icon.setVisibility(GONE);glyph.setVisibility(GONE);setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);return;}
            java.lang.ref.WeakReference<Card> target=new java.lang.ref.WeakReference<>(this);
            ThumbnailCache.get(getContext()).load(uri,bitmap->{Card card=target.get();if(card==null||!uri.equals(card.artKey))return;card.art.bitmap(bitmap);
                if(bitmap!=null){card.icon.setVisibility(GONE);card.glyph.setVisibility(GONE);card.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);}
            });
        }
        GradientDrawable shape(int fill,int stroke){GradientDrawable shape=new GradientDrawable();shape.setColor(fill);shape.setCornerRadius(dp(10));shape.setStroke(dp(2),stroke);return shape;}
    }
}
