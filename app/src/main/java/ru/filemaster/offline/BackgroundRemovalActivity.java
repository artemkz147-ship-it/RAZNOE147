package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackgroundRemovalActivity extends AppCompatActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Uri uri;private MaskView view;private TextView status;private int threshold=48;
    @Override protected void onCreate(Bundle b){super.onCreate(b);WindowCompat.setDecorFitsSystemWindows(getWindow(),false);String raw=getIntent().getStringExtra("image_uri");if(raw==null||raw.isBlank()){finish();return;}uri=Uri.parse(raw);build();load();}
    private void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(247,248,252));ViewCompat.setOnApplyWindowInsetsListener(root,(v,i)->{Insets s=i.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(dp(8)+s.left,dp(6)+s.top,dp(8)+s.right,dp(8)+s.bottom);return i;});LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView back=t("←",30,true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(t("Умное удаление фона",23,true));status=t("Открываю изображение…",12,false);names.addView(status);top.addView(names,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));root.addView(top);view=new MaskView(this);root.addView(view,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));LinearLayout modes=new LinearLayout(this);Button auto=b("Авто"),erase=b("Стереть"),restore=b("Вернуть"),reset=b("Сброс");auto.setOnClickListener(v->auto());erase.setOnClickListener(v->{view.setErase(true);status.setText("Ручной режим: стираем фон пальцем");});restore.setOnClickListener(v->{view.setErase(false);status.setText("Ручной режим: возвращаем детали пальцем");});reset.setOnClickListener(v->{view.reset();status.setText("Исходник восстановлен");});modes.addView(auto);modes.addView(erase);modes.addView(restore);modes.addView(reset);root.addView(modes,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));TextView th=t("Чувствительность авто: 48",12,false);root.addView(th);SeekBar sens=new SeekBar(this);sens.setMax(100);sens.setProgress(38);sens.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean from){threshold=10+p;th.setText("Чувствительность авто: "+threshold);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});root.addView(sens);TextView brush=t("Кисть: 44 px",12,false);root.addView(brush);SeekBar bs=new SeekBar(this);bs.setMax(140);bs.setProgress(34);bs.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean from){int z=10+p;view.setBrush(z);brush.setText("Кисть: "+z+" px");}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});root.addView(bs);Button save=b("Сохранить PNG с прозрачным фоном");save.setOnClickListener(v->save());root.addView(save,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));setContentView(root);}
    private void load(){worker.submit(()->{try{BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}int sample=1;while(Math.max(bounds.outWidth,bounds.outHeight)/sample>3000)sample*=2;BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=sample;o.inPreferredConfig=Bitmap.Config.ARGB_8888;Bitmap src;try(InputStream in=getContentResolver().openInputStream(uri)){src=BitmapFactory.decodeStream(in,null,o);}if(src==null)throw new IllegalArgumentException("Не удалось прочитать изображение");Bitmap work=src.copy(Bitmap.Config.ARGB_8888,true);src.recycle();runOnUiThread(()->{view.setBitmap(work);status.setText("Авто + ручная доработка • "+work.getWidth()+" × "+work.getHeight()+" px");auto();});}catch(Exception e){runOnUiThread(()->error(e));}});}
    private void auto(){if(!view.ready())return;ProgressDialog d=ProgressDialog.show(this,"Доки","Определяю фон по краям…",true,false);int value=threshold;worker.submit(()->{try{view.autoRemove(value);runOnUiThread(()->{d.dismiss();view.invalidate();status.setText("Авто готово • можно стереть лишнее или вернуть детали");});}catch(Exception e){runOnUiThread(()->{d.dismiss();error(e);});}});}
    private void save(){if(!view.ready())return;ProgressDialog d=ProgressDialog.show(this,"Доки","Сохраняю прозрачный PNG…",true,false);Bitmap copy=view.snapshot();worker.submit(()->{try{Uri out=ImageAdvancedTools.publishTransparentPng(this,copy,"Без_фона");copy.recycle();runOnUiThread(()->{d.dismiss();ResultDialogs.show(this,"Фон удалён. Результат сохранён как PNG с прозрачностью.",out);});}catch(Exception e){copy.recycle();runOnUiThread(()->{d.dismiss();error(e);});}});}
    private Button b(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1f);p.setMargins(dp(1),0,dp(1),0);b.setLayoutParams(p);return b;}private TextView t(String s,int sp,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(bold?Color.rgb(25,28,36):Color.rgb(93,99,112));if(bold)v.setTypeface(v.getTypeface(),android.graphics.Typeface.BOLD);return v;}private void error(Exception e){new AlertDialog.Builder(this).setTitle("Не получилось").setMessage(e.getMessage()==null?"Ошибка":e.getMessage()).setPositiveButton("OK",null).show();}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}@Override protected void onDestroy(){super.onDestroy();worker.shutdownNow();if(view!=null)view.recycle();}

    static final class MaskView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private Bitmap original,work;private int brush=44;private boolean erase=true;private float scale,ox,oy;
        MaskView(android.content.Context c){super(c);setBackgroundColor(Color.WHITE);}
        synchronized void setBitmap(Bitmap b){recycle();original=b.copy(Bitmap.Config.ARGB_8888,false);work=b;invalidate();}synchronized boolean ready(){return work!=null;}void setErase(boolean v){erase=v;}void setBrush(int v){brush=Math.max(5,v);}synchronized void reset(){if(original==null)return;if(work!=null&&!work.isRecycled())work.recycle();work=original.copy(Bitmap.Config.ARGB_8888,true);invalidate();}synchronized Bitmap snapshot(){return work.copy(Bitmap.Config.ARGB_8888,false);}synchronized void recycle(){if(work!=null&&!work.isRecycled())work.recycle();if(original!=null&&!original.isRecycled())original.recycle();work=null;original=null;}
        synchronized void autoRemove(int threshold){if(work==null||original==null)return;int w=original.getWidth(),h=original.getHeight();int[] px=new int[w*h];original.getPixels(px,0,w,0,0,w,h);long rr=0,gg=0,bb=0,n=0;int step=Math.max(1,Math.min(w,h)/500);for(int x=0;x<w;x+=step){int a=px[x],b=px[(h-1)*w+x];rr+=Color.red(a)+Color.red(b);gg+=Color.green(a)+Color.green(b);bb+=Color.blue(a)+Color.blue(b);n+=2;}for(int y=0;y<h;y+=step){int a=px[y*w],b=px[y*w+w-1];rr+=Color.red(a)+Color.red(b);gg+=Color.green(a)+Color.green(b);bb+=Color.blue(a)+Color.blue(b);n+=2;}int ar=(int)(rr/Math.max(1,n)),ag=(int)(gg/Math.max(1,n)),ab=(int)(bb/Math.max(1,n));int t2=threshold*threshold*3;for(int i=0;i<px.length;i++){int c=px[i];int dr=Color.red(c)-ar,dg=Color.green(c)-ag,db=Color.blue(c)-ab;if(dr*dr+dg*dg+db*db<=t2)px[i]=c&0x00FFFFFF;else px[i]=(c&0x00FFFFFF)|0xFF000000;}work.setPixels(px,0,w,0,0,w,h);}
        @Override protected synchronized void onDraw(Canvas c){super.onDraw(c);if(work==null)return;int tile=dp(12);p.setStyle(Paint.Style.FILL);for(int y=0;y<getHeight();y+=tile)for(int x=0;x<getWidth();x+=tile){p.setColor(((x/tile+y/tile)&1)==0?0xFFE8E8E8:0xFFCFCFCF);c.drawRect(x,y,x+tile,y+tile,p);}scale=Math.min(getWidth()/(float)work.getWidth(),getHeight()/(float)work.getHeight());float dw=work.getWidth()*scale,dh=work.getHeight()*scale;ox=(getWidth()-dw)/2f;oy=(getHeight()-dh)/2f;c.drawBitmap(work,null,new android.graphics.RectF(ox,oy,ox+dw,oy+dh),p);}
        @Override public boolean onTouchEvent(MotionEvent e){if(work==null)return false;if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE){paintAt((e.getX()-ox)/scale,(e.getY()-oy)/scale);invalidate();return true;}return true;}
        private synchronized void paintAt(float fx,float fy){if(work==null||original==null)return;int cx=Math.round(fx),cy=Math.round(fy),r=Math.max(1,Math.round(brush/Math.max(.01f,scale)/2f));int l=Math.max(0,cx-r),t=Math.max(0,cy-r),rr=Math.min(work.getWidth()-1,cx+r),bb=Math.min(work.getHeight()-1,cy+r);for(int y=t;y<=bb;y++){int dy=y-cy;for(int x=l;x<=rr;x++){int dx=x-cx;if(dx*dx+dy*dy>r*r)continue;if(erase){int c=work.getPixel(x,y);work.setPixel(x,y,c&0x00FFFFFF);}else work.setPixel(x,y,original.getPixel(x,y));}}}
        private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    }
}
