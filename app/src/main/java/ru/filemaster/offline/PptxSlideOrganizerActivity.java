package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PptxSlideOrganizerActivity extends AppCompatActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<PptxSlideTools.Slide> slides=new ArrayList<>();
    private Uri uri;private LinearLayout list;private TextView status;
    @Override protected void onCreate(Bundle b){super.onCreate(b);WindowCompat.setDecorFitsSystemWindows(getWindow(),false);String raw=getIntent().getStringExtra("pptx_uri");if(raw==null||raw.isBlank()){finish();return;}uri=Uri.parse(raw);build();load();}
    private void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(247,248,252));ViewCompat.setOnApplyWindowInsetsListener(root,(v,i)->{Insets s=i.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(dp(10)+s.left,dp(8)+s.top,dp(10)+s.right,dp(8)+s.bottom);return i;});LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView back=text("←",30,Color.rgb(25,28,36),false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(text("Конструктор слайдов",24,Color.rgb(25,28,36),true));status=text("Читаю презентацию…",13,Color.rgb(93,99,112),false);names.addView(status);top.addView(names,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));root.addView(top);TextView note=text("Меняется только порядок слайдов. Содержимое, изображения и оформление PPTX не конвертируются.",12,Color.rgb(93,99,112),false);note.setPadding(dp(8),0,dp(8),dp(8));root.addView(note);ScrollView sv=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sv.addView(list);root.addView(sv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));Button save=new Button(this);save.setText("Собрать новую презентацию");save.setAllCaps(false);save.setOnClickListener(v->save());root.addView(save,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));setContentView(root);}
    private void load(){worker.submit(()->{try{List<PptxSlideTools.Slide> x=PptxSlideTools.readSlides(this,uri);slides.addAll(x);runOnUiThread(this::render);}catch(Exception e){runOnUiThread(()->fatal(e));}});}
    private void render(){list.removeAllViews();status.setText(slides.size()+" слайдов");for(int i=0;i<slides.size();i++){int index=i;PptxSlideTools.Slide s=slides.get(i);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(9),dp(8),dp(9));row.setBackgroundColor(Color.WHITE);LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);t.addView(text("Слайд "+(i+1),15,Color.rgb(25,28,36),true));t.addView(text(s.title,13,Color.rgb(93,99,112),false));row.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));Button up=mini("↑"),down=mini("↓");up.setEnabled(i>0);down.setEnabled(i+1<slides.size());up.setOnClickListener(v->{swap(index,index-1);});down.setOnClickListener(v->{swap(index,index+1);});row.addView(up);row.addView(down);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);rp.setMargins(0,0,0,dp(6));list.addView(row,rp);}}
    private void swap(int a,int b){PptxSlideTools.Slide s=slides.get(a);slides.set(a,slides.get(b));slides.set(b,s);render();}
    private void save(){ProgressDialog d=ProgressDialog.show(this,"Доки","Перестраиваю порядок слайдов…",true,false);worker.submit(()->{try{Uri out=PptxSlideTools.reorder(this,uri,slides);runOnUiThread(()->{d.dismiss();ResultDialogs.show(this,"Новая презентация сохранена. Исходный PPTX не изменён.",out);});}catch(Exception e){runOnUiThread(()->{d.dismiss();error(e);});}});}
    private Button mini(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(44)));return b;}private TextView text(String s,int sp,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);if(b)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}private String msg(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}private void fatal(Exception e){new AlertDialog.Builder(this).setTitle("Не удалось открыть PPTX").setMessage(msg(e)).setPositiveButton("Закрыть",(d,w)->finish()).show();}private void error(Exception e){new AlertDialog.Builder(this).setTitle("Не получилось").setMessage(msg(e)).setPositiveButton("OK",null).show();}@Override protected void onDestroy(){super.onDestroy();worker.shutdownNow();}
}
