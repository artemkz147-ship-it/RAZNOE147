package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageCropActivity extends AppCompatActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Uri uri;private CropView crop;private TextView status;
    @Override protected void onCreate(Bundle b){super.onCreate(b);WindowCompat.setDecorFitsSystemWindows(getWindow(),false);String raw=getIntent().getStringExtra("image_uri");if(raw==null||raw.isBlank()){finish();return;}uri=Uri.parse(raw);build();load();}
    private void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(247,248,252));ViewCompat.setOnApplyWindowInsetsListener(root,(v,i)->{Insets s=i.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(dp(8)+s.left,dp(6)+s.top,dp(8)+s.right,dp(8)+s.bottom);return i;});LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView back=t("←",30,true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(t("Кадрирование",24,true));status=t("Открываю изображение…",12,false);names.addView(status);top.addView(names,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));root.addView(top);crop=new CropView(this);crop.setBackgroundColor(Color.rgb(33,35,42));root.addView(crop,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);LinearLayout modes=new LinearLayout(this);modes.addView(mode("Свободно",()->crop.setFree()));modes.addView(mode("1:1",()->crop.setRatio(1f)));modes.addView(mode("4:3",()->crop.setRatio(4f/3f)));modes.addView(mode("3:4",()->crop.setRatio(3f/4f)));modes.addView(mode("16:9",()->crop.setRatio(16f/9f)));modes.addView(mode("9:16",()->crop.setRatio(9f/16f)));modes.addView(mode("Точный размер",this::askExact));hsv.addView(modes);root.addView(hsv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));Button save=new Button(this);save.setText("Вырезать выбранную область");save.setAllCaps(false);save.setOnClickListener(v->save());root.addView(save,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));setContentView(root);}
    private void load(){worker.submit(()->{try{ImageAdvancedTools.Size s=ImageAdvancedTools.size(this,uri);Bitmap p=ImageTools.loadScaled(this,uri,2200);runOnUiThread(()->{crop.setImage(p,s.width,s.height);status.setText("Исходник: "+s.width+" × "+s.height+" px • рамку можно двигать");});}catch(Exception e){runOnUiThread(()->error(e));}});}
    private Button mode(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(v->r.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(48));p.setMargins(dp(2),0,dp(2),0);b.setLayoutParams(p);return b;}
    private void askExact(){LinearLayout box=new LinearLayout(this);box.setPadding(dp(16),0,dp(16),0);box.setOrientation(LinearLayout.HORIZONTAL);EditText w=number("Ширина px"),h=number("Высота px");box.addView(w,new LinearLayout.LayoutParams(0,dp(56),1f));box.addView(h,new LinearLayout.LayoutParams(0,dp(56),1f));new AlertDialog.Builder(this).setTitle("Точный размер кадра").setMessage("Рамка станет фиксированной: её можно двигать, но нельзя растягивать.").setView(box).setPositiveButton("Применить",(d,x)->{try{int wi=Integer.parseInt(w.getText().toString()),he=Integer.parseInt(h.getText().toString());crop.setFixed(wi,he);status.setText("Фиксированный кадр: "+wi+" × "+he+" px • только перемещение");}catch(Exception e){error(new IllegalArgumentException("Введите ширину и высоту в пикселях"));}}).setNegativeButton("Отмена",null).show();}
    private void save(){RectF r=crop.getCrop();if(r==null)return;int x=Math.round(r.left),y=Math.round(r.top),w=Math.round(r.width()),h=Math.round(r.height());ProgressDialog d=ProgressDialog.show(this,"Доки","Вырезаю область…",true,false);worker.submit(()->{try{Uri out=ImageAdvancedTools.cropRegion(this,uri,x,y,w,h);runOnUiThread(()->{d.dismiss();new AlertDialog.Builder(this).setTitle("Готово").setMessage("Кадр: "+w+" × "+h+" px").setPositiveButton("Открыть",(a,b)->ViewerIntents.open(this,out)).setNeutralButton("Поделиться",(a,b)->ViewerIntents.share(this,out)).setNegativeButton("Закрыть",null).show();});}catch(Exception e){runOnUiThread(()->{d.dismiss();error(e);});}});}
    private EditText number(String hint){EditText e=new EditText(this);e.setHint(hint);e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setSingleLine(true);return e;}private TextView t(String s,int sp,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(bold?Color.rgb(25,28,36):Color.rgb(93,99,112));if(bold)v.setTypeface(v.getTypeface(),android.graphics.Typeface.BOLD);return v;}private void error(Exception e){new AlertDialog.Builder(this).setTitle("Не получилось").setMessage(e.getMessage()==null?"Ошибка":e.getMessage()).setPositiveButton("OK",null).show();}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}@Override protected void onDestroy(){super.onDestroy();worker.shutdownNow();if(crop!=null)crop.recycle();}

    static final class CropView extends View {
        private static final int FREE=0,RATIO=1,FIXED=2;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private Bitmap bitmap;private int srcW,srcH,mode=FREE;private float ratio;private RectF crop=new RectF();private RectF imageDst=new RectF();private int drag;private float lastX,lastY;
        CropView(android.content.Context c){super(c);p.setStrokeWidth(2f*c.getResources().getDisplayMetrics().density);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setImage(Bitmap b,int w,int h){if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();bitmap=b;srcW=w;srcH=h;crop.set(0,0,w,h);invalidate();}
        void recycle(){if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();bitmap=null;}
        RectF getCrop(){return new RectF(crop);}
        void setFree(){mode=FREE;ratio=0;invalidate();}
        void setRatio(float r){if(srcW<=0)return;mode=RATIO;ratio=r;float w=srcW,h=w/r;if(h>srcH){h=srcH;w=h*r;}float l=(srcW-w)/2f,t=(srcH-h)/2f;crop.set(l,t,l+w,t+h);invalidate();}
        void setFixed(int w,int h){if(w<1||h<1||w>srcW||h>srcH)throw new IllegalArgumentException("Фиксированный кадр должен помещаться в изображение: максимум "+srcW+" × "+srcH);mode=FIXED;float l=(srcW-w)/2f,t=(srcH-h)/2f;crop.set(l,t,l+w,t+h);invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(bitmap==null)return;float vw=getWidth(),vh=getHeight();float s=Math.min(vw/bitmap.getWidth(),vh/bitmap.getHeight());float dw=bitmap.getWidth()*s,dh=bitmap.getHeight()*s;imageDst.set((vw-dw)/2f,(vh-dh)/2f,(vw+dw)/2f,(vh+dh)/2f);c.drawBitmap(bitmap,null,imageDst,p);RectF cr=toView(crop);p.setStyle(Paint.Style.FILL);p.setColor(0x99000000);c.drawRect(imageDst.left,imageDst.top,imageDst.right,cr.top,p);c.drawRect(imageDst.left,cr.bottom,imageDst.right,imageDst.bottom,p);c.drawRect(imageDst.left,cr.top,cr.left,cr.bottom,p);c.drawRect(cr.right,cr.top,imageDst.right,cr.bottom,p);p.setStyle(Paint.Style.STROKE);p.setColor(Color.WHITE);p.setStrokeWidth(dp(2));c.drawRect(cr,p);p.setStyle(Paint.Style.FILL);float rr=dp(7);c.drawCircle(cr.left,cr.top,rr,p);c.drawCircle(cr.right,cr.top,rr,p);c.drawCircle(cr.right,cr.bottom,rr,p);c.drawCircle(cr.left,cr.bottom,rr,p);}
        private RectF toView(RectF s){float sx=imageDst.width()/srcW,sy=imageDst.height()/srcH;return new RectF(imageDst.left+s.left*sx,imageDst.top+s.top*sy,imageDst.left+s.right*sx,imageDst.top+s.bottom*sy);}
        private float srcX(float x){return clamp((x-imageDst.left)*srcW/imageDst.width(),0,srcW);}private float srcY(float y){return clamp((y-imageDst.top)*srcH/imageDst.height(),0,srcH);}private float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}
        @Override public boolean onTouchEvent(MotionEvent e){if(bitmap==null)return false;float sx=srcX(e.getX()),sy=srcY(e.getY());if(e.getAction()==MotionEvent.ACTION_DOWN){RectF v=toView(crop);float x=e.getX(),y=e.getY(),d=dp(26);if(mode!=FIXED&&dist(x,y,v.left,v.top)<d)drag=2;else if(mode!=FIXED&&dist(x,y,v.right,v.top)<d)drag=3;else if(mode!=FIXED&&dist(x,y,v.right,v.bottom)<d)drag=4;else if(mode!=FIXED&&dist(x,y,v.left,v.bottom)<d)drag=5;else if(crop.contains(sx,sy))drag=1;else drag=0;lastX=sx;lastY=sy;return drag!=0;}if(e.getAction()==MotionEvent.ACTION_MOVE&&drag!=0){float dx=sx-lastX,dy=sy-lastY;if(drag==1)move(dx,dy);else resize(sx,sy);lastX=sx;lastY=sy;invalidate();return true;}if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){drag=0;return true;}return true;}
        private void move(float dx,float dy){float w=crop.width(),h=crop.height(),l=clamp(crop.left+dx,0,srcW-w),t=clamp(crop.top+dy,0,srcH-h);crop.set(l,t,l+w,t+h);}
        private void resize(float x,float y){float min=Math.max(8,Math.min(srcW,srcH)*0.03f);float ax,ay;boolean left=drag==2||drag==5,top=drag==2||drag==3;ax=left?crop.right:crop.left;ay=top?crop.bottom:crop.top;float w=Math.max(min,Math.abs(x-ax)),h=Math.max(min,Math.abs(y-ay));if(mode==RATIO){if(w/h>ratio)h=w/ratio;else w=h*ratio;}w=Math.min(w,left?ax:srcW-ax);h=Math.min(h,top?ay:srcH-ay);if(mode==RATIO){float m=Math.min(w,h*ratio);w=m;h=m/ratio;}float l=left?ax-w:ax,r=left?ax:ax+w,t=top?ay-h:ay,b=top?ay:ay+h;crop.set(clamp(l,0,srcW),clamp(t,0,srcH),clamp(r,0,srcW),clamp(b,0,srcH));}
        private float dist(float x,float y,float a,float b){return(float)Math.hypot(x-a,y-b);}private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    }
}
