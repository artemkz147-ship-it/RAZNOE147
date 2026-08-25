package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.ImageType;
import com.tom_roush.pdfbox.rendering.PDFRenderer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SignaturePlacementActivity extends AppCompatActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<SignaturePlacement> placements=new ArrayList<>();
    private Uri pdfUri;private File signatureFile,pdfTemp;private PDDocument doc;private PDFRenderer renderer;private Bitmap signatureBitmap;private PlacementView view;private TextView pageLabel;private int page;
    @Override protected void onCreate(Bundle b){super.onCreate(b);PDFBoxResourceLoader.init(getApplicationContext());WindowCompat.setDecorFitsSystemWindows(getWindow(),false);String p=getIntent().getStringExtra("pdf_uri"),s=getIntent().getStringExtra("signature_path");if(p==null||s==null){finish();return;}pdfUri=Uri.parse(p);signatureFile=new File(s);signatureBitmap=BitmapFactory.decodeFile(s);if(signatureBitmap==null){finish();return;}build();load();}
    private void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(247,248,252));ViewCompat.setOnApplyWindowInsetsListener(root,(v,i)->{Insets s=i.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(dp(8)+s.left,dp(6)+s.top,dp(8)+s.right,dp(8)+s.bottom);return i;});LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView back=t("←",30,true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(t("Размещение подписи",23,true));pageLabel=t("Готовлю PDF…",12,false);names.addView(pageLabel);top.addView(names,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));root.addView(top);view=new PlacementView(this);root.addView(view,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);Button prev=b("‹"),next=b("›");prev.setOnClickListener(v->{if(page>0){page--;renderPage();}});next.setOnClickListener(v->{if(doc!=null&&page+1<doc.getNumberOfPages()){page++;renderPage();}});nav.addView(prev,new LinearLayout.LayoutParams(dp(58),dp(46)));TextView hint=t("Перетащите подпись пальцем",13,false);hint.setGravity(Gravity.CENTER);nav.addView(hint,new LinearLayout.LayoutParams(0,dp(46),1f));nav.addView(next,new LinearLayout.LayoutParams(dp(58),dp(46)));root.addView(nav);LinearLayout tools=new LinearLayout(this);Button add=b("Добавить"),copy=b("Копия"),minus=b("−"),plus=b("+"),del=b("Удалить");add.setOnClickListener(v->addPlacement());copy.setOnClickListener(v->copyPlacement());minus.setOnClickListener(v->resize(.88f));plus.setOnClickListener(v->resize(1.14f));del.setOnClickListener(v->deletePlacement());for(Button x:new Button[]{add,copy,minus,plus,del})tools.addView(x,new LinearLayout.LayoutParams(0,dp(46),1f));root.addView(tools);Button save=b("Сохранить все подписи");save.setOnClickListener(v->save());root.addView(save,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));setContentView(root);}
    private void load(){worker.submit(()->{try{pdfTemp=FileStore.copyUriToTemp(this,pdfUri,".pdf");doc=PDDocument.load(pdfTemp);renderer=new PDFRenderer(doc);if(doc.getNumberOfPages()==0)throw new IllegalArgumentException("PDF без страниц");runOnUiThread(()->{addPlacement();renderPage();});}catch(Exception e){runOnUiThread(()->error(e));}});}
    private void renderPage(){if(renderer==null)return;int wanted=page;pageLabel.setText("Страница "+(page+1)+" / "+doc.getNumberOfPages()+" • подписей: "+placements.size());worker.submit(()->{try{Bitmap b=renderer.renderImageWithDPI(wanted,110, ImageType.RGB);runOnUiThread(()->{if(wanted!=page||isFinishing()){b.recycle();return;}view.setPage(b,page,placements,signatureBitmap);});}catch(Exception e){runOnUiThread(()->error(e));}});}
    private void addPlacement(){if(doc==null)return;float pw=doc.getPage(page).getMediaBox().getWidth(),ph=doc.getPage(page).getMediaBox().getHeight();float w=.28f,h=w*(signatureBitmap.getHeight()/(float)signatureBitmap.getWidth())*(pw/ph);h=Math.max(.035f,Math.min(.24f,h));SignaturePlacement p=new SignaturePlacement(page,.5f-w/2f,.72f,w,h);placements.add(p);view.setActive(p);pageLabel.setText("Страница "+(page+1)+" / "+doc.getNumberOfPages()+" • подписей: "+placements.size());view.invalidate();}
    private void copyPlacement(){SignaturePlacement a=view.getActive();if(a==null){addPlacement();return;}SignaturePlacement p=a.copy();p.page=page;p.x=Math.min(1f-p.width,Math.max(0,p.x+.035f));p.y=Math.min(1f-p.height,Math.max(0,p.y+.035f));placements.add(p);view.setActive(p);view.invalidate();pageLabel.setText("Страница "+(page+1)+" / "+doc.getNumberOfPages()+" • подписей: "+placements.size());}
    private void resize(float factor){SignaturePlacement a=view.getActive();if(a==null)return;float cx=a.x+a.width/2f,cy=a.y+a.height/2f,nw=Math.max(.08f,Math.min(.65f,a.width*factor)),nh=Math.max(.02f,Math.min(.4f,a.height*factor));a.width=nw;a.height=nh;a.x=Math.max(0,Math.min(1-nw,cx-nw/2));a.y=Math.max(0,Math.min(1-nh,cy-nh/2));view.invalidate();}
    private void deletePlacement(){SignaturePlacement a=view.getActive();if(a==null)return;placements.remove(a);view.setActive(null);view.invalidate();pageLabel.setText("Страница "+(page+1)+" / "+doc.getNumberOfPages()+" • подписей: "+placements.size());}
    private void save(){if(placements.isEmpty()){error(new IllegalArgumentException("Добавьте хотя бы одну подпись"));return;}ProgressDialog d=ProgressDialog.show(this,"Доки","Сохраняю подписи…",true,false);List<SignaturePlacement> copy=new ArrayList<>();for(SignaturePlacement p:placements)copy.add(p.copy());worker.submit(()->{try{Uri out=SignaturePdfTools.apply(this,pdfUri,signatureFile,copy);runOnUiThread(()->{d.dismiss();new AlertDialog.Builder(this).setTitle("Готово").setMessage("Все размещённые подписи сохранены в новой копии PDF.").setPositiveButton("Открыть",(x,w)->ViewerIntents.open(this,out)).setNeutralButton("Поделиться",(x,w)->ViewerIntents.share(this,out)).setNegativeButton("Закрыть",null).show();});}catch(Exception e){runOnUiThread(()->{d.dismiss();error(e);});}});}
    private Button b(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}private TextView t(String s,int sp,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(bold?Color.rgb(25,28,36):Color.rgb(93,99,112));if(bold)v.setTypeface(v.getTypeface(),android.graphics.Typeface.BOLD);return v;}private void error(Exception e){new AlertDialog.Builder(this).setTitle("Не получилось").setMessage(e.getMessage()==null?"Ошибка":e.getMessage()).setPositiveButton("OK",null).show();}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}@Override protected void onDestroy(){super.onDestroy();worker.shutdownNow();if(view!=null)view.recyclePage();if(signatureBitmap!=null&&!signatureBitmap.isRecycled())signatureBitmap.recycle();try{if(doc!=null)doc.close();}catch(Exception ignored){}if(pdfTemp!=null)pdfTemp.delete();}

    static final class PlacementView extends View {private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private Bitmap page,sig;private int currentPage;private List<SignaturePlacement> list;private SignaturePlacement active;private RectF dst=new RectF();private float lastX,lastY;PlacementView(android.content.Context c){super(c);setBackgroundColor(Color.rgb(40,42,48));}void setPage(Bitmap b,int p,List<SignaturePlacement> l,Bitmap s){recyclePage();page=b;currentPage=p;list=l;sig=s;if(active==null||active.page!=p)active=firstOnPage();invalidate();}void recyclePage(){if(page!=null&&!page.isRecycled())page.recycle();page=null;}void setActive(SignaturePlacement p){active=p;}SignaturePlacement getActive(){return active;}private SignaturePlacement firstOnPage(){if(list!=null)for(SignaturePlacement p:list)if(p.page==currentPage)return p;return null;}
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(page==null)return;float s=Math.min(getWidth()/(float)page.getWidth(),getHeight()/(float)page.getHeight());float w=page.getWidth()*s,h=page.getHeight()*s;dst.set((getWidth()-w)/2f,(getHeight()-h)/2f,(getWidth()+w)/2f,(getHeight()+h)/2f);c.drawBitmap(page,null,dst,paint);if(list==null||sig==null)return;for(SignaturePlacement p:list){if(p.page!=currentPage)continue;RectF r=rect(p);c.drawBitmap(sig,null,r,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(p==active?2.5f:1f));paint.setColor(p==active?Color.rgb(49,87,213):0xAAFFFFFF);c.drawRect(r,paint);paint.setStyle(Paint.Style.FILL);}}
        private RectF rect(SignaturePlacement p){return new RectF(dst.left+p.x*dst.width(),dst.top+p.y*dst.height(),dst.left+(p.x+p.width)*dst.width(),dst.top+(p.y+p.height)*dst.height());}
        @Override public boolean onTouchEvent(MotionEvent e){if(page==null||list==null)return false;float nx=(e.getX()-dst.left)/dst.width(),ny=(e.getY()-dst.top)/dst.height();if(e.getAction()==MotionEvent.ACTION_DOWN){active=null;for(int i=list.size()-1;i>=0;i--){SignaturePlacement p=list.get(i);if(p.page==currentPage&&rect(p).contains(e.getX(),e.getY())){active=p;break;}}lastX=nx;lastY=ny;invalidate();return active!=null;}if(e.getAction()==MotionEvent.ACTION_MOVE&&active!=null){float dx=nx-lastX,dy=ny-lastY;active.x=Math.max(0,Math.min(1-active.width,active.x+dx));active.y=Math.max(0,Math.min(1-active.height,active.y+dy));lastX=nx;lastY=ny;invalidate();return true;}return true;}private float dp(float v){return v*getResources().getDisplayMetrics().density;}}
}
