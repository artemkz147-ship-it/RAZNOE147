package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.rendering.ImageType;
import com.tom_roush.pdfbox.rendering.PDFRenderer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfPageOrganizerActivity extends AppCompatActivity {
    private static final class Item {
        final int sourceIndex;
        int rotationDelta;
        Bitmap thumb;
        Item(int sourceIndex) { this.sourceIndex = sourceIndex; }
        Item copy() { Item i = new Item(sourceIndex); i.rotationDelta = rotationDelta; i.thumb = thumb; return i; }
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Item> items = new ArrayList<>();
    private Uri pdfUri;
    private File temp;
    private PDDocument source;
    private PDFRenderer renderer;
    private LinearLayout list;
    private TextView subtitle;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        PDFBoxResourceLoader.init(getApplicationContext());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        String raw = getIntent().getStringExtra("pdf_uri");
        if (raw == null || raw.isBlank()) { finish(); return; }
        pdfUri = Uri.parse(raw);
        build(); load();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(247,248,252));
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,i)->{Insets s=i.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(dp(10)+s.left,dp(8)+s.top,dp(10)+s.right,dp(8)+s.bottom);return i;});
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView back=text("←",30,Color.rgb(25,28,36),false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(text("Конструктор страниц",24,Color.rgb(25,28,36),true));subtitle=text("Готовлю страницы…",13,Color.rgb(93,99,112),false);names.addView(subtitle);top.addView(names,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));root.addView(top);
        TextView note=text("Меняйте порядок, поворачивайте, удаляйте и создавайте копии страниц. Нажмите на миниатюру страницы, чтобы рассмотреть её крупно. Исходный PDF не изменяется.",12,Color.rgb(93,99,112),false);note.setPadding(dp(8),0,dp(8),dp(8));root.addView(note);
        ScrollView scroll=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        Button save=new Button(this);save.setText("Собрать новый PDF");save.setAllCaps(false);save.setOnClickListener(v->save());root.addView(save,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));setContentView(root);
    }

    private void load() {
        worker.submit(() -> {
            try {
                temp=FileStore.copyUriToTemp(this,pdfUri,".pdf");source=PDDocument.load(temp);renderer=new PDFRenderer(source);
                if(source.getNumberOfPages()==0)throw new IllegalArgumentException("PDF без страниц");
                for(int i=0;i<source.getNumberOfPages();i++){Item item=new Item(i);item.thumb=renderer.renderImageWithDPI(i,54, ImageType.RGB);items.add(item);}
                runOnUiThread(this::renderList);
            } catch(Exception e){runOnUiThread(()->fatal(e));}
        });
    }

    private void renderList() {
        list.removeAllViews();subtitle.setText(items.size()+" стр. • новая копия после сохранения");
        for(int pos=0;pos<items.size();pos++){
            final int index=pos;Item item=items.get(pos);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(8),dp(6),dp(8));row.setBackgroundColor(Color.WHITE);
            ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.FIT_CENTER);iv.setImageBitmap(item.thumb);if(item.rotationDelta!=0)iv.setRotation(item.rotationDelta);iv.setContentDescription("Открыть страницу "+(item.sourceIndex+1)+" крупно");iv.setOnClickListener(v->openPreview(item.sourceIndex));row.addView(iv,new LinearLayout.LayoutParams(dp(78),dp(104)));
            LinearLayout center=new LinearLayout(this);center.setOrientation(LinearLayout.VERTICAL);center.setPadding(dp(10),0,dp(6),0);center.addView(text("Страница "+(item.sourceIndex+1),15,Color.rgb(25,28,36),true));center.addView(text("Позиция "+(pos+1)+(item.rotationDelta==0?"":" • поворот "+item.rotationDelta+"°"),12,Color.rgb(93,99,112),false));center.addView(text("Нажмите на миниатюру для увеличения",11,Color.rgb(49,87,213),false));
            LinearLayout buttons=new LinearLayout(this);Button up=mini("↑"),down=mini("↓"),rot=mini("↻"),copy=mini("⧉"),del=mini("×");up.setEnabled(pos>0);down.setEnabled(pos+1<items.size());up.setOnClickListener(v->{swap(index,index-1);});down.setOnClickListener(v->{swap(index,index+1);});rot.setOnClickListener(v->{item.rotationDelta=(item.rotationDelta+90)%360;renderList();});copy.setOnClickListener(v->{items.add(index+1,item.copy());renderList();});del.setOnClickListener(v->{if(items.size()<=1)return;items.remove(index);renderList();});buttons.addView(up);buttons.addView(down);buttons.addView(rot);buttons.addView(copy);buttons.addView(del);center.addView(buttons);row.addView(center,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);rp.setMargins(0,0,0,dp(6));list.addView(row,rp);
        }
    }

    private void openPreview(int sourceIndex){Intent i=new Intent(this,PdfPagePreviewActivity.class);i.putExtra("pdf_uri",pdfUri.toString());i.putExtra("page_index",sourceIndex);startActivity(i);}
    private void swap(int a,int b){Item x=items.get(a);items.set(a,items.get(b));items.set(b,x);renderList();}

    private void save(){if(items.isEmpty())return;ProgressDialog d=ProgressDialog.show(this,"Доки","Собираю PDF…",true,false);worker.submit(()->{File out=null;try{out=File.createTempFile("organizer_",".pdf",getCacheDir());try(PDDocument dst=new PDDocument()){for(Item item:items){PDPage page=dst.importPage(source.getPage(item.sourceIndex));page.setRotation((page.getRotation()+item.rotationDelta)%360);}dst.save(out);}Uri result=FileStore.publishFile(this,out,"Конструктор_страниц_"+System.currentTimeMillis()+".pdf","application/pdf",null);runOnUiThread(()->{d.dismiss();ResultDialogs.show(this,"Новый PDF собран. Исходный файл не изменён.",result);});}catch(Exception e){runOnUiThread(()->{d.dismiss();error(e);});}finally{if(out!=null)out.delete();}});}

    private Button mini(String s){Button b=new Button(this);b.setText(s);b.setTextSize(16);b.setAllCaps(false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(46),dp(44));p.setMargins(dp(1),0,dp(1),0);b.setLayoutParams(p);return b;}
    private TextView text(String s,int sp,int c,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private void fatal(Exception e){new AlertDialog.Builder(this).setTitle("Не удалось открыть PDF").setMessage(msg(e)).setPositiveButton("Закрыть",(d,w)->finish()).show();}private void error(Exception e){new AlertDialog.Builder(this).setTitle("Не получилось").setMessage(msg(e)).setPositiveButton("OK",null).show();}private String msg(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){super.onDestroy();worker.shutdownNow();for(Item i:items){if(i.thumb!=null&&!i.thumb.isRecycled())i.thumb.recycle();}try{if(source!=null)source.close();}catch(Exception ignored){}if(temp!=null)temp.delete();}
}
