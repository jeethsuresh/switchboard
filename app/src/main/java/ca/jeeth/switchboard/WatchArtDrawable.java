package ca.jeeth.switchboard;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Cropped artwork, a readable text scrim and a focus border in one lightweight background. */
final class WatchArtDrawable extends Drawable {
    private final Paint imagePaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),scrimPaint=new Paint(Paint.ANTI_ALIAS_FLAG),borderPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frame=new RectF();
    private final float radius,stroke;
    private Bitmap bitmap;
    WatchArtDrawable(float density){radius=10*density;stroke=2*density;}
    void bitmap(Bitmap value){bitmap=value;update();invalidateSelf();}
    Bitmap bitmap(){return bitmap;}
    @Override protected void onBoundsChange(Rect bounds){update();}
    private void update(){Rect b=getBounds();frame.set(b.left+stroke/2,b.top+stroke/2,b.right-stroke/2,b.bottom-stroke/2);
        if(bitmap!=null&&b.width()>0&&b.height()>0){BitmapShader shader=new BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);float scale=Math.max((float)b.width()/bitmap.getWidth(),(float)b.height()/bitmap.getHeight());Matrix matrix=new Matrix();matrix.setScale(scale,scale);matrix.postTranslate(b.left+(b.width()-bitmap.getWidth()*scale)/2,b.top+(b.height()-bitmap.getHeight()*scale)/2);shader.setLocalMatrix(matrix);imagePaint.setShader(shader);
            scrimPaint.setShader(new LinearGradient(0,b.top+b.height()*.18f,0,b.bottom,new int[]{0x080c1215,0x900c1215,0xf80c1215},new float[]{0,.5f,1},Shader.TileMode.CLAMP));
        }else imagePaint.setShader(null);
    }
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] state){invalidateSelf();return true;}
    @Override public void draw(Canvas canvas){boolean focused=false;for(int state:getState())if(state==android.R.attr.state_focused||state==android.R.attr.state_pressed)focused=true;
        if(bitmap==null){imagePaint.setColor(focused?Color.rgb(36,65,61):DashboardView.SURFACE);canvas.drawRoundRect(frame,radius,radius,imagePaint);}
        else{canvas.drawRoundRect(frame,radius,radius,imagePaint);canvas.drawRoundRect(frame,radius,radius,scrimPaint);}
        if(focused){borderPaint.setColor(DashboardView.ACCENT);borderPaint.setStyle(Paint.Style.STROKE);borderPaint.setStrokeWidth(stroke);canvas.drawRoundRect(frame,radius,radius,borderPaint);}
    }
    @Override public void setAlpha(int alpha){}
    @Override public void setColorFilter(ColorFilter filter){}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
