package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField;
import com.tom_roush.pdfbox.rendering.ImageType;
import com.tom_roush.pdfbox.rendering.PDFRenderer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfFormDesignerActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Draft> drafts = new ArrayList<>();
    private Uri pdfUri;
    private int pageNumber = 1;
    private FieldView fieldView;
    private TextView status;
    private int mode = Draft.TEXT;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PDFBoxResourceLoader.init(getApplicationContext());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        String raw=getIntent().getStringExtra("pdf_uri"); pageNumber=getIntent().getIntExtra("page",1);
        if(raw==null||raw.isBlank()){finish();return;} pdfUri=Uri.parse(raw); buildUi(); loadPreview();
    }

    private void buildUi(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(239,241,246));ViewCompat.setOnApplyWindowInsetsListener(root,(v,i)->{Insets s=i.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(dp(10),s.top+dp(6),dp(10),s.bottom+dp(10));return i;});
        LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);TextView back=text("←",30,Color.rgb(25,28,36),false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());h.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.addView(text("Создать поля PDF",23,Color.rgb(25,28,36),true));status=text("Страница "+pageNumber+" • загружаю…",13,Color.rgb(93,99,112),false);titles.addView(status);h.addView(titles,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));root.addView(h);
        TextView hint=text("Выберите тип и протяните прямоугольник пальцем. После этого задайте имя поля. Исходный PDF не перезаписывается.",12,Color.rgb(93,99,112),false);hint.setPadding(dp(8),0,dp(8),dp(6));root.addView(hint);
        fieldView=new FieldView(this, this::onRect);root.addView(fieldView,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f));
        LinearLayout tools=new LinearLayout(this);tools.setGravity(Gravity.CENTER);Button textB=button("Текстовое поле");textB.setOnClickListener(v->{mode=Draft.TEXT;status.setText("Протяните прямоугольник текстового поля");});tools.addView(textB,weighted());Button checkB=button("Чекбокс");checkB.setOnClickListener(v->{mode=Draft.CHECK;status.setText("Протяните квадрат/прямоугольник чекбокса");});tools.addView(checkB,weighted());Button undo=button("Отмена");undo.setOnClickListener(v->{if(!drafts.isEmpty())drafts.remove(drafts.size()-1);fieldView.setDrafts(drafts);});tools.addView(undo,weighted());root.addView(tools);
        Button save=button("Сохранить PDF с полями");save.setTextSize(16);save.setOnClickListener(v->save());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(54));lp.setMargins(0,dp(6),0,0);root.addView(save,lp);setContentView(root);}

    private void loadPreview(){worker.submit(()->{File input=null;try{input=FileStore.copyUriToTemp(this,pdfUri,".pdf");try(PDDocument doc=PDDocument.load(input)){if(pageNumber<1||pageNumber>doc.getNumberOfPages())throw new IllegalArgumentException("В PDF страниц: "+doc.getNumberOfPages());Bitmap b=new PDFRenderer(doc).renderImageWithDPI(pageNumber-1,105,ImageType.RGB);int total=doc.getNumberOfPages();runOnUiThread(()->{fieldView.setBitmap(b);status.setText("Страница "+pageNumber+" из "+total+" • режим: текстовое поле");});}}catch(Exception e){runOnUiThread(()->showError(e));}finally{if(input!=null)input.delete();}});}

    private void onRect(RectF rect){if(rect.width()<.02f||rect.height()<.015f)return;EditText name=new EditText(this);name.setHint("Например: ФИО");name.setSingleLine(true);LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);wrap.setPadding(dp(20),0,dp(20),0);wrap.addView(name);EditText def=null;if(mode==Draft.TEXT){def=new EditText(this);def.setHint("Начальное значение (необязательно)");def.setSingleLine(true);wrap.addView(def);}EditText finalDef=def;int selectedMode=mode;new AlertDialog.Builder(this).setTitle(selectedMode==Draft.TEXT?"Текстовое поле":"Чекбокс").setView(wrap).setPositiveButton("Добавить",(d,w)->{String n=name.getText().toString().trim();if(n.isEmpty())n="Поле_"+(drafts.size()+1);for(Draft x:drafts)if(x.name.equals(n)){n=n+"_"+(drafts.size()+1);break;}Draft draft=new Draft(selectedMode,new RectF(rect),n,finalDef==null?"":finalDef.getText().toString());drafts.add(draft);fieldView.setDrafts(drafts);status.setText("Добавлено полей: "+drafts.size());}).setNegativeButton("Отмена",null).show();}

    private void save(){if(drafts.isEmpty()){new AlertDialog.Builder(this).setTitle("Нет новых полей").setMessage("Сначала добавьте хотя бы одно поле.").setPositiveButton("OK",null).show();return;}ProgressDialog dialog=ProgressDialog.show(this,"ФайлМастер","Создаю интерактивные поля…",true,false);List<Draft> snap=new ArrayList<>();for(Draft d:drafts)snap.add(d.copy());worker.submit(()->{File input=null,out=null;try{input=FileStore.copyUriToTemp(this,pdfUri,".pdf");out=File.createTempFile("form_design_",".pdf",getCacheDir());try(PDDocument doc=PDDocument.load(input)){PDPage page=doc.getPage(pageNumber-1);PDAcroForm form=doc.getDocumentCatalog().getAcroForm();if(form==null){form=new PDAcroForm(doc);doc.getDocumentCatalog().setAcroForm(form);}PDResources resources=form.getDefaultResources();if(resources==null){resources=new PDResources();form.setDefaultResources(resources);}com.tom_roush.pdfbox.cos.COSName fontName=resources.add(PDType1Font.HELVETICA);String da="/"+fontName.getName()+" 10 Tf 0 g";form.setDefaultAppearance(da);form.setNeedAppearances(true);
                    PDRectangle box=page.getCropBox();for(Draft draft:snap){PDRectangle rect=toPdfRect(draft.rect,box);if(form.getField(draft.name)!=null)throw new IllegalArgumentException("Поле с именем «"+draft.name+"» уже существует");if(draft.type==Draft.TEXT){PDTextField field=new PDTextField(form);field.setPartialName(draft.name);field.setDefaultAppearance(da);PDAnnotationWidget widget=field.getWidgets().get(0);widget.setRectangle(rect);widget.setPage(page);widget.setPrinted(true);PDBorderStyleDictionary border=new PDBorderStyleDictionary();border.setWidth(1);widget.setBorderStyle(border);form.getFields().add(field);page.getAnnotations().add(widget);if(!draft.defaultValue.isEmpty())field.setValue(draft.defaultValue);}else{PDCheckBox field=new PDCheckBox(form);field.setPartialName(draft.name);PDAnnotationWidget widget=field.getWidgets().get(0);widget.setRectangle(rect);widget.setPage(page);widget.setPrinted(true);PDBorderStyleDictionary border=new PDBorderStyleDictionary();border.setWidth(1);widget.setBorderStyle(border);form.getFields().add(field);page.getAnnotations().add(widget);field.unCheck();}}doc.save(out);}Uri result=FileStore.publishFile(this,out,"PDF_с_полями_"+System.currentTimeMillis()+".pdf","application/pdf",null);runOnUiThread(()->{dialog.dismiss();new AlertDialog.Builder(this).setTitle("Форма готова").setMessage("Добавлено полей: "+snap.size()).setPositiveButton("Открыть",(d,w)->open(result)).setNeutralButton("Поделиться",(d,w)->share(result)).setNegativeButton("Закрыть",(d,w)->finish()).show();});}catch(Exception e){runOnUiThread(()->{dialog.dismiss();showError(e);});}finally{if(input!=null)input.delete();if(out!=null)out.delete();}});}

    private PDRectangle toPdfRect(RectF n,PDRectangle box){float x=box.getLowerLeftX()+n.left*box.getWidth();float w=n.width()*box.getWidth();float h=n.height()*box.getHeight();float y=box.getLowerLeftY()+(1f-n.bottom)*box.getHeight();return new PDRectangle(x,y,w,h);}
    private void open(Uri u){try{Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(u,"application/pdf");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Открыть PDF"));}catch(Exception e){showError(new IllegalStateException("PDF сохранён, но открыть его не удалось"));}}
    private void share(Uri u){try{Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/pdf");i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Поделиться PDF"));}catch(Exception e){showError(new IllegalStateException("Не удалось открыть меню отправки"));}}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}private LinearLayout.LayoutParams weighted(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1f);p.setMargins(dp(2),0,dp(2),0);return p;}private TextView text(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(v.getTypeface(),android.graphics.Typeface.BOLD);return v;}private void showError(Exception e){new AlertDialog.Builder(this).setTitle("Не получилось").setMessage(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()).setPositiveButton("OK",null).show();}private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}@Override protected void onDestroy(){super.onDestroy();if(fieldView!=null)fieldView.release();if(isFinishing())worker.shutdownNow();}

    private interface RectListener{void done(RectF rect);}static final class Draft{static final int TEXT=1,CHECK=2;final int type;final RectF rect;final String name,defaultValue;Draft(int t,RectF r,String n,String d){type=t;rect=r;name=n;defaultValue=d;}Draft copy(){return new Draft(type,new RectF(rect),name,defaultValue);}}
    static final class FieldView extends View{private Bitmap bitmap;private final RectF imageRect=new RectF();private final List<Draft> drafts=new ArrayList<>();private RectF active;private final RectListener listener;FieldView(android.content.Context c,RectListener l){super(c);listener=l;setBackgroundColor(Color.rgb(45,47,54));}void setBitmap(Bitmap b){release();bitmap=b;invalidate();}void setDrafts(List<Draft>d){drafts.clear();for(Draft x:d)drafts.add(x.copy());invalidate();}void release(){if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();bitmap=null;}
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(bitmap==null)return;float s=Math.min(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight());float w=bitmap.getWidth()*s,h=bitmap.getHeight()*s;imageRect.set((getWidth()-w)/2f,(getHeight()-h)/2f,(getWidth()+w)/2f,(getHeight()+h)/2f);c.drawBitmap(bitmap,null,imageRect,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));for(Draft d:drafts)drawRect(c,d.rect,d.type==Draft.CHECK?Color.rgb(230,125,20):Color.rgb(49,87,213),d.name);if(active!=null)drawRect(c,active,Color.rgb(0,180,120),"");}
        private void drawRect(Canvas c,RectF n,int color,String label){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dpF(2));p.setColor(color);RectF r=screen(n);c.drawRect(r,p);if(label!=null&&!label.isEmpty()){p.setStyle(Paint.Style.FILL);p.setTextSize(dpF(12));c.drawText(label,r.left+dpF(3),Math.max(dpF(14),r.top-dpF(3)),p);}}
        private RectF screen(RectF n){return new RectF(imageRect.left+n.left*imageRect.width(),imageRect.top+n.top*imageRect.height(),imageRect.left+n.right*imageRect.width(),imageRect.top+n.bottom*imageRect.height());}private PointF norm(float x,float y){return new PointF(Math.max(0,Math.min(1,(x-imageRect.left)/imageRect.width())),Math.max(0,Math.min(1,(y-imageRect.top)/imageRect.height())));}private float dpF(float v){return v*getResources().getDisplayMetrics().density;}
        @Override public boolean onTouchEvent(MotionEvent e){if(bitmap==null||!imageRect.contains(e.getX(),e.getY()))return true;PointF n=norm(e.getX(),e.getY());if(e.getAction()==MotionEvent.ACTION_DOWN){active=new RectF(n.x,n.y,n.x,n.y);invalidate();return true;}if(e.getAction()==MotionEvent.ACTION_MOVE&&active!=null){active.right=n.x;active.bottom=n.y;invalidate();return true;}if(e.getAction()==MotionEvent.ACTION_UP&&active!=null){float l=Math.min(active.left,n.x),r=Math.max(active.left,n.x),t=Math.min(active.top,n.y),b=Math.max(active.top,n.y);RectF done=new RectF(l,t,r,b);active=null;invalidate();listener.done(done);return true;}return true;}}
}
