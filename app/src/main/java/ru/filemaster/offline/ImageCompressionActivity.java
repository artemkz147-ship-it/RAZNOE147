package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageCompressionActivity extends AppCompatActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Uri uri;private int quality=100;private TextView qLabel;
    @Override protected void onCreate(Bundle b){super.onCreate(b);WindowCompat.setDecorFitsSystemWindows(getWindow(),false);String raw=getIntent().getStringExtra("image_uri");if(raw==null||raw.isBlank()){finish();return;}uri=Uri.parse(raw);build();}
    private void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(247,248,252));ViewCompat.setOnApplyWindowInsetsListener(root,(v,i)->{Insets s=i.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(dp(12)+s.left,dp(8)+s.top,dp(12)+s.right,dp(10)+s.bottom);return i;});LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView back=t("←",30,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout tt=new LinearLayout(this);tt.setOrientation(LinearLayout.VERTICAL);tt.addView(t("Оптимизация изображения",24,true));tt.addView(t("Разрешение и пропорции не меняются",13,false));top.addView(tt,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));root.addView(top);
        ImageView preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackgroundColor(Color.WHITE);root.addView(preview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));worker.submit(()->{try{Bitmap bmp=ImageTools.loadScaled(this,uri,1800);runOnUiThread(()->preview.setImageBitmap(bmp));}catch(Exception ignored){}});
        Button lossless=new Button(this);lossless.setText("Без потерь качества");lossless.setAllCaps(false);lossless.setOnClickListener(v->saveLossless());LinearLayout.LayoutParams lossLp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52));lossLp.setMargins(0,dp(8),0,dp(4));root.addView(lossless,lossLp);TextView lossNote=t("Сохраняет пиксели без потерь: WebP Lossless на Android 11+ или PNG на Android 10. Формат файла может измениться.",12,false);lossNote.setPadding(dp(4),0,dp(4),dp(8));root.addView(lossNote);
        qLabel=t("Максимум качества • JPEG 100%",16,true);qLabel.setPadding(dp(4),dp(8),dp(4),dp(4));root.addView(qLabel);TextView note=t("Или выберите качество JPEG в процентах. Чем ниже процент, тем обычно меньше файл. Размер изображения в пикселях остаётся прежним.",12,false);note.setPadding(dp(4),0,dp(4),dp(4));root.addView(note);SeekBar bar=new SeekBar(this);bar.setMax(80);bar.setProgress(80);bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){quality=20+p;qLabel.setText(quality==100?"Максимум качества • JPEG 100%":"Качество JPEG • "+quality+"%");}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});root.addView(bar);Button save=new Button(this);save.setText("Сохранить JPEG с выбранным качеством");save.setAllCaps(false);save.setOnClickListener(v->saveJpeg());root.addView(save,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));setContentView(root);}
    private void saveLossless(){ProgressDialog d=ProgressDialog.show(this,"Доки","Оптимизирую без потерь…",true,false);worker.submit(()->{try{Uri out=ImageAdvancedTools.optimizeLossless(this,uri);runOnUiThread(()->{d.dismiss();ResultDialogs.show(this,"Разрешение и декодированные пиксели сохранены без потерь.",out);});}catch(Exception e){runOnUiThread(()->{d.dismiss();error(e);});}});}
    private void saveJpeg(){ProgressDialog d=ProgressDialog.show(this,"Доки","Оптимизирую изображение…",true,false);int q=quality;worker.submit(()->{try{Uri out=ImageAdvancedTools.optimize(this,uri,q);runOnUiThread(()->{d.dismiss();ResultDialogs.show(this,"Разрешение сохранено. Качество JPEG: "+q+"%.",out);});}catch(Exception e){runOnUiThread(()->{d.dismiss();error(e);});}});}
    private void error(Exception e){new AlertDialog.Builder(this).setTitle("Не получилось").setMessage(e.getMessage()==null?"Ошибка":e.getMessage()).setPositiveButton("OK",null).show();}private TextView t(String s,int sp,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(bold?Color.rgb(25,28,36):Color.rgb(93,99,112));if(bold)v.setTypeface(v.getTypeface(),android.graphics.Typeface.BOLD);return v;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}@Override protected void onDestroy(){super.onDestroy();worker.shutdownNow();}
}
