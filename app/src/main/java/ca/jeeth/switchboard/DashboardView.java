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
final class DashboardView extends android.widget.FrameLayout {
    static final int BG=Color.rgb(12,18,21),SURFACE=Color.rgb(25,34,40),WHITE=Color.rgb(239,244,245),MUTED=Color.rgb(149,169,178),ACCENT=Color.rgb(155,232,205);
    private final ControlPanel controls;
    private final Store store;
    private final AppRepository repository;
    private final Map<Integer,Card> cards=new HashMap<>();
    private final Card[] appCards=new Card[16],inputCards=new Card[3];
    private final TextView brand,appsHeading,inputHeading,hint,pageLabel;
    private final Card recent,hub,settings;
    private List<Catalog.Tile> apps=Collections.emptyList();
    private int page;
    private final boolean overlay;
    private boolean bound;
    private List<Catalog.Tile> catalogSnapshot;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener preferences=(p,key)->{if(key!=null&&(key.startsWith("label:")||key.startsWith("hidden:")||key.startsWith("order:")||key.equals("customOrder")))post(()->bind(true));};
    DashboardView(ControlPanel s,boolean overlay){
        super(s);controls=s;this.overlay=overlay;store=new Store(s);repository=AppRepository.get(s);
        setBackgroundColor(BG);setPadding(0,0,0,0);
        brand=label(overlay?"SWITCHBOARD  /  QUICK CONTROLS":"SWITCHBOARD",15,ACCENT);brand.setLetterSpacing(.14f);brand.setTypeface(null,Typeface.BOLD);addView(brand);
        inputHeading=label("INPUTS",11,MUTED);inputHeading.setLetterSpacing(.12f);addView(inputHeading);
        appsHeading=label("APPS  /  RECENT",11,MUTED);appsHeading.setLetterSpacing(.12f);addView(appsHeading);
        pageLabel=label("",11,MUTED);pageLabel.setGravity(Gravity.RIGHT);addView(pageLabel);
        hint=label(overlay?"Back closes  ·  Hold OK to customize":"Hold Home for quick controls  ·  Hold OK to customize",11,MUTED);addView(hint);
        hub=card(HomeNavigation.HUB);hub.set("✦","Switchboard",overlay?"OK closes controls":"Choose a direction",null);
        hub.setOnClickListener(v->{if(overlay)controls.dismissOverlay();else LauncherSettings.allApps(controls);});
        recent=card(HomeNavigation.RECENT);
        settings=card(HomeNavigation.SETTINGS);settings.set("⚙","Settings","",null);settings.setOnClickListener(v->LauncherSettings.show(controls));
        for(int i=0;i<3;i++)inputCards[i]=card(HomeNavigation.INPUT+i);
        for(int i=0;i<16;i++)appCards[i]=card(HomeNavigation.APP+i);
        bind(true);
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String text,int size,int color){TextView v=new TextView(getContext());v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setSingleLine(true);v.setEllipsize(TextUtils.TruncateAt.END);return v;}
    private Card card(int node){Card c=new Card(getContext());c.setId(View.generateViewId());cards.put(node,c);addView(c);
        c.setOnKeyListener((v,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            int direction=switch(key){case KeyEvent.KEYCODE_DPAD_LEFT->0;case KeyEvent.KEYCODE_DPAD_RIGHT->1;case KeyEvent.KEYCODE_DPAD_UP->2;case KeyEvent.KEYCODE_DPAD_DOWN->3;default->-1;};
            if(direction<0){if(key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN){changePage(key==KeyEvent.KEYCODE_CHANNEL_DOWN?1:-1);return true;}return false;}
            if(node>=HomeNavigation.APP){int col=(node-HomeNavigation.APP)%4;
                if(direction==HomeNavigation.RIGHT&&col==3&&(page+1)*16<apps.size()){changePage(1);cards.get(node-3).requestFocus();return true;}
                if(direction==HomeNavigation.LEFT&&col==0&&page>0){changePage(-1);cards.get(node+3).requestFocus();return true;}
            }
            cards.get(HomeNavigation.next(node,direction)).requestFocus();return true;
        });return c;
    }
    void center(){page=0;bind(true);hub.requestFocus();}
    void bind(boolean reorder){
        if(reorder||!bound||catalogSnapshot!=repository.apps){catalogSnapshot=repository.apps;apps=store.apps(repository.apps,false);page=Math.min(page,Math.max(0,(apps.size()-1)/16));bound=true;}
        List<Catalog.Tile> ports=repository.hdmi();
        for(int i=0;i<3;i++){
            final Catalog.Tile tile=i<ports.size()?ports.get(i):null;Card c=inputCards[i];
            String title=tile==null?"":store.label(tile);
            if(title.matches("(?i)HDMI\\s*"+(i+1)))title="";
            // Show the connected CEC device label beneath the fixed physical port number.
            if(tile!=null&&title.isEmpty())for(Catalog.Tile child:repository.inputs)
                if(tile.input.getId().equals(child.input.getParentId())&&!child.title.matches("(?i)HDMI.*")){title=store.label(child);break;}
            c.set("HDMI "+(i+1),title,"",null);
            c.setContentDescription("HDMI "+(i+1)+(tile==null?"":", "+store.label(tile)));
            c.setOnClickListener(v->{if(tile!=null)Actions.tile(controls,tile);else OverlayMenus.inputs(controls,false,false);});
            c.setOnLongClickListener(v->{if(tile!=null)LauncherSettings.tile(controls,tile,()->bind(false));return true;});
        }
        Catalog.Tile last=store.recent(repository.apps);
        recent.set(last==null?"↑":"","Resume",last==null?"Open an app to begin":store.label(last),last==null?null:last.icon);
        recent.setContentDescription(last==null?"Recently used app, no history yet":"Resume "+store.label(last));
        recent.setOnClickListener(v->{if(last!=null)Actions.tile(controls,last);else LauncherSettings.allApps(controls);});
        for(int i=0;i<16;i++){
            int index=page*16+i;Card c=appCards[i];Catalog.Tile tile=index<apps.size()?apps.get(index):null;
            c.set(tile==null?"+":"",tile==null?"Add shortcut":store.label(tile),"",tile==null?null:tile.icon);
            c.setContentDescription(tile==null?"Empty app slot, choose an app":store.label(tile));
            c.setOnClickListener(v->{if(tile==null)LauncherSettings.addShortcut(controls,()->bind(true));else Actions.tile(controls,tile);});
            c.setOnLongClickListener(v->{if(tile!=null)LauncherSettings.tile(controls,tile,()->bind(true));else LauncherSettings.addShortcut(controls,()->bind(true));return true;});
            c.setAlpha(tile==null?.42f:1f);
        }
        appsHeading.setText(store.prefs.getBoolean("customOrder",false)?"APPS  /  CUSTOM ORDER":"APPS  /  RECENTLY USED");
        int pages=Math.max(1,(apps.size()+15)/16);pageLabel.setText(pages>1?(page+1)+" / "+pages+(page+1<pages?"  →":"") : "4 × 4");
        hint.setText(!repository.ready?"Loading local apps…":overlay?"Back closes  ·  Hold OK to customize":"Hold Home for quick controls  ·  Hold OK to customize");
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();store.prefs.registerOnSharedPreferenceChangeListener(preferences);}
    @Override protected void onDetachedFromWindow(){store.prefs.unregisterOnSharedPreferenceChangeListener(preferences);super.onDetachedFromWindow();}
    private void changePage(int delta){int next=page+delta;if(next>=0&&next*16<apps.size()){page=next;bind(false);}}
    private void place(View v,float x,float y,float w,float h,int width,int height){v.layout(Math.round(x*width),Math.round(y*height),Math.round((x+w)*width),Math.round((y+h)*height));}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);setMeasuredDimension(w,h);
        for(int i=0;i<getChildCount();i++){View v=getChildAt(i);float cw=v==brand?.75f:v==hint?.9f:v==appsHeading?.36f:v==pageLabel?.14f:v==inputHeading?.19f:v==hub||v==recent||v==settings?.19f:Arrays.asList(inputCards).contains(v)?.173f:.123f;
            float ch=v instanceof Card?(v==recent?.22f:v==hub?.16f:v==settings?.13f:Arrays.asList(inputCards).contains(v)?.135f:.17f):.05f;
            v.measure(MeasureSpec.makeMeasureSpec(Math.round(w*cw),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.round(h*ch),MeasureSpec.EXACTLY));
        }
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){int w=r-l,h=b-t;
        place(brand,.04f,.035f,.75f,.05f,w,h);place(inputHeading,.04f,.19f,.19f,.05f,w,h);
        place(appsHeading,.445f,.105f,.36f,.05f,w,h);place(pageLabel,.817f,.105f,.14f,.05f,w,h);
        place(hint,.04f,.92f,.9f,.05f,w,h);
        place(hub,.235f,.435f,.19f,.16f,w,h);place(recent,.235f,.125f,.19f,.22f,w,h);place(settings,.235f,.76f,.19f,.13f,w,h);
        for(int i=0;i<3;i++)place(inputCards[i],.04f,.2775f+i*.17f,.173f,.135f,w,h);
        for(int i=0;i<16;i++)place(appCards[i],.445f+(i%4)*.129f,.18f+(i/4)*.182f,.123f,.17f,w,h);
    }
    private final class Card extends LinearLayout {
        final ImageView icon;
        final TextView glyph,title,subtitle;
        Card(Context context){super(context);setOrientation(VERTICAL);setGravity(Gravity.CENTER);setPadding(dp(8),dp(5),dp(8),dp(5));setFocusable(true);setClickable(true);
            StateListDrawable background=new StateListDrawable();background.addState(new int[]{android.R.attr.state_focused},shape(Color.rgb(36,65,61),ACCENT));background.addState(new int[]{android.R.attr.state_pressed},shape(Color.rgb(36,65,61),ACCENT));background.addState(new int[]{},shape(SURFACE,Color.TRANSPARENT));setBackground(background);
            icon=new ImageView(context);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);addView(icon,new LinearLayout.LayoutParams(dp(38),dp(34)));
            glyph=label("",20,ACCENT);glyph.setGravity(Gravity.CENTER);addView(glyph,new LinearLayout.LayoutParams(-1,dp(30)));
            title=label("",13,WHITE);title.setGravity(Gravity.CENTER);title.setTypeface(null,Typeface.BOLD);addView(title,new LinearLayout.LayoutParams(-1,dp(21)));
            subtitle=label("",10,MUTED);subtitle.setGravity(Gravity.CENTER);addView(subtitle,new LinearLayout.LayoutParams(-1,dp(16)));
        }
        void set(String symbol,String name,String detail,Drawable drawable){icon.setImageDrawable(drawable);icon.setVisibility(drawable==null?GONE:VISIBLE);glyph.setText(symbol);glyph.setVisibility(symbol.isEmpty()?GONE:VISIBLE);title.setText(name);title.setVisibility(name.isEmpty()?GONE:VISIBLE);subtitle.setText(detail);subtitle.setVisibility(detail.isEmpty()?GONE:VISIBLE);}
        GradientDrawable shape(int fill,int stroke){GradientDrawable shape=new GradientDrawable();shape.setColor(fill);shape.setCornerRadius(dp(10));shape.setStroke(dp(2),stroke);return shape;}
    }
}
