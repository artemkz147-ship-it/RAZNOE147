package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfFormActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, EditorInfo> editors = new LinkedHashMap<>();
    private Uri pdfUri;
    private LinearLayout fieldsRoot;
    private Button saveButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PDFBoxResourceLoader.init(getApplicationContext());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        String raw = getIntent().getStringExtra("pdf_uri");
        if (raw == null || raw.isBlank()) { finish(); return; }
        pdfUri = Uri.parse(raw);
        buildUi(); loadFields();
    }

    @Override protected void onResume() { super.onResume(); if (fieldsRoot != null) loadFields(); }

    private void buildUi() {
        LinearLayout screen = new LinearLayout(this); screen.setOrientation(LinearLayout.VERTICAL); screen.setBackgroundColor(Color.rgb(247,248,252));
        ViewCompat.setOnApplyWindowInsetsListener(screen,(v,insets)->{Insets safe=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(dp(14),safe.top+dp(8),dp(14),safe.bottom+dp(12));return insets;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView back=text("←",30,Color.rgb(25,28,36),false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(dp(50),dp(50)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.addView(text("PDF-форма",24,Color.rgb(25,28,36),true));titles.addView(text("Заполнение и создание интерактивных полей",13,Color.rgb(93,99,112),false));header.addView(titles,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));screen.addView(header);

        Button create=new Button(this);create.setText("+ Создать новые поля на странице");create.setAllCaps(false);create.setOnClickListener(v->askPageForDesigner());screen.addView(create,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(52)));
        TextView note=text("Существующие поля: текст, флажки, радиокнопки и списки. Конструктор создаёт новые текстовые поля и чекбоксы. Push-button действия не изменяются.",12,Color.rgb(93,99,112),false);note.setPadding(dp(8),dp(4),dp(8),dp(8));screen.addView(note);
        ScrollView scroll=new ScrollView(this);fieldsRoot=new LinearLayout(this);fieldsRoot.setOrientation(LinearLayout.VERTICAL);fieldsRoot.setPadding(dp(2),dp(8),dp(2),dp(10));scroll.addView(fieldsRoot);screen.addView(scroll,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f));
        saveButton=new Button(this);saveButton.setText("Сохранить заполненный PDF");saveButton.setAllCaps(false);saveButton.setEnabled(false);saveButton.setOnClickListener(v->save());screen.addView(saveButton,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(56)));setContentView(screen);
    }

    private void askPageForDesigner(){EditText e=new EditText(this);e.setHint("Номер страницы, например 1");e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);new AlertDialog.Builder(this).setTitle("На какой странице создать поля?").setView(e).setPositiveButton("Открыть конструктор",(d,w)->{try{int p=Integer.parseInt(e.getText().toString().trim());if(p<1)throw new NumberFormatException();Intent i=new Intent(this,PdfFormDesignerActivity.class);i.putExtra("pdf_uri",pdfUri.toString());i.putExtra("page",p);startActivity(i);}catch(Exception ex){showError(new IllegalArgumentException("Введите номер страницы от 1"));}}).setNegativeButton("Отмена",null).show();}

    private void loadFields() {
        fieldsRoot.removeAllViews();TextView loading=text("Читаю поля формы…",15,Color.rgb(93,99,112),false);loading.setGravity(Gravity.CENTER);loading.setPadding(0,dp(24),0,dp(24));fieldsRoot.addView(loading);saveButton.setEnabled(false);
        worker.submit(()->{File input=null;try{input=FileStore.copyUriToTemp(this,pdfUri,".pdf");List<FieldInfo> fields=new ArrayList<>();int unsupported=0;try(PDDocument doc=PDDocument.load(input)){PDAcroForm form=doc.getDocumentCatalog().getAcroForm();if(form==null){int other=0;runOnUiThread(()->showFields(fields,other));return;}for(PDField field:form.getFieldTree()){String name=field.getFullyQualifiedName();if(name==null||name.isBlank())name="Поле "+(fields.size()+1);if(field instanceof PDTextField)fields.add(FieldInfo.text(name,safe(field.getValueAsString())));else if(field instanceof PDCheckBox check)fields.add(FieldInfo.check(name,check.isChecked()));else if(field instanceof PDRadioButton radio){List<String> opts=new ArrayList<>();List<String> exports=radio.getExportValues();if(exports!=null&&!exports.isEmpty())opts.addAll(exports);if(opts.isEmpty()){Set<String> on=radio.getOnValues();if(on!=null)opts.addAll(on);}fields.add(FieldInfo.radio(name,safe(field.getValueAsString()),opts));}else if(field instanceof PDChoice choice){List<String> display=choice.getOptionsDisplayValues(),export=choice.getOptionsExportValues();fields.add(FieldInfo.choice(name,safe(field.getValueAsString()),display==null?new ArrayList<>():new ArrayList<>(display),export==null?new ArrayList<>():new ArrayList<>(export),choice.isMultiSelect()));}else unsupported++;}}int other=unsupported;runOnUiThread(()->showFields(fields,other));}catch(Exception e){runOnUiThread(()->showLoadError(e));}finally{if(input!=null)input.delete();}});
    }

    private void showFields(List<FieldInfo> fields,int unsupported){editors.clear();fieldsRoot.removeAllViews();if(fields.isEmpty()){TextView empty=text("Заполняемых полей пока нет. Нажмите «Создать новые поля на странице», чтобы добавить текстовое поле или чекбокс.",15,Color.rgb(93,99,112),false);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(12),dp(28),dp(12),dp(20));fieldsRoot.addView(empty);saveButton.setEnabled(false);return;}
        TextView summary=text("Поддерживаемых полей: "+fields.size()+(unsupported>0?"  •  прочих: "+unsupported:""),13,Color.rgb(0,135,100),true);summary.setPadding(dp(4),0,dp(4),dp(10));fieldsRoot.addView(summary);
        for(FieldInfo info:fields){LinearLayout block=new LinearLayout(this);block.setOrientation(LinearLayout.VERTICAL);block.setPadding(dp(12),dp(10),dp(12),dp(10));android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(14));bg.setStroke(dp(1),Color.rgb(231,233,240));block.setBackground(bg);block.addView(text(info.name,14,Color.rgb(25,28,36),true));View editor;
            if(info.type==FieldInfo.CHECK){CheckBox c=new CheckBox(this);c.setText(info.checked?"Отмечено":"Отметить");c.setChecked(info.checked);c.setOnCheckedChangeListener((b,checked)->b.setText(checked?"Отмечено":"Отметить"));editor=c;}
            else if(info.type==FieldInfo.RADIO||(info.type==FieldInfo.CHOICE&&!info.multi)){Spinner s=new Spinner(this);List<String> options=info.display.isEmpty()?info.export:info.display;if(options.isEmpty()&&!info.value.isBlank())options=new ArrayList<>(List.of(info.value));s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));int selected=indexForCurrent(info,options);if(selected>=0)s.setSelection(selected);editor=s;}
            else{EditText e=new EditText(this);e.setText(info.value);e.setTextSize(15);e.setSingleLine(false);e.setMinLines(1);e.setMaxLines(info.multi?4:6);e.setHint(info.multi?"Несколько значений через запятую":"Введите значение");editor=e;}
            block.addView(editor,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));editors.put(info.name,new EditorInfo(info,editor));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lp.setMargins(0,0,0,dp(8));fieldsRoot.addView(block,lp);}
        saveButton.setEnabled(true);
    }

    private int indexForCurrent(FieldInfo info,List<String> display){int idx=display.indexOf(info.value);if(idx>=0)return idx;return info.export.indexOf(info.value);}

    private void save(){if(editors.isEmpty())return;ProgressDialog dialog=ProgressDialog.show(this,"ФайлМастер","Заполняю форму…",true,false);worker.submit(()->{File input=null,out=null;try{input=FileStore.copyUriToTemp(this,pdfUri,".pdf");out=File.createTempFile("form_filled_",".pdf",getCacheDir());try(PDDocument doc=PDDocument.load(input)){PDAcroForm form=doc.getDocumentCatalog().getAcroForm();if(form==null)throw new IllegalArgumentException("Форма исчезла из документа");for(Map.Entry<String,EditorInfo> item:editors.entrySet()){PDField field=form.getField(item.getKey());if(field==null)continue;EditorInfo ed=item.getValue();if(field instanceof PDTextField&&ed.view instanceof EditText e)field.setValue(e.getText().toString());else if(field instanceof PDCheckBox c&&ed.view instanceof CheckBox cb){if(cb.isChecked())c.check();else c.unCheck();}else if(field instanceof PDRadioButton r&&ed.view instanceof Spinner s){String value=valueForPosition(ed.info,s.getSelectedItemPosition());if(value!=null)r.setValue(value);}else if(field instanceof PDChoice choice){if(ed.info.multi&&ed.view instanceof EditText e){String raw=e.getText().toString().trim();List<String> vals=new ArrayList<>();if(!raw.isBlank())for(String p:raw.split(","))if(!p.trim().isEmpty())vals.add(p.trim());choice.setValue(vals);}else if(ed.view instanceof Spinner s){String value=valueForPosition(ed.info,s.getSelectedItemPosition());if(value!=null)choice.setValue(value);}}}doc.save(out);}Uri result=FileStore.publishFile(this,out,"Заполненная_форма_"+System.currentTimeMillis()+".pdf","application/pdf",null);runOnUiThread(()->{dialog.dismiss();new AlertDialog.Builder(this).setTitle("Готово").setMessage("Заполненный PDF сохранён как новый файл.").setPositiveButton("Открыть",(d,w)->openResult(result)).setNeutralButton("Поделиться",(d,w)->shareResult(result)).setNegativeButton("Закрыть",null).show();});}catch(Exception e){runOnUiThread(()->{dialog.dismiss();showError(e);});}finally{if(input!=null)input.delete();if(out!=null)out.delete();}});}
    private String valueForPosition(FieldInfo info,int pos){if(pos<0)return null;if(pos<info.export.size())return info.export.get(pos);if(pos<info.display.size())return info.display.get(pos);return null;}
    private void showLoadError(Exception e){fieldsRoot.removeAllViews();TextView error=text(message(e),15,Color.rgb(160,45,45),false);error.setGravity(Gravity.CENTER);error.setPadding(dp(12),dp(30),dp(12),dp(20));fieldsRoot.addView(error);saveButton.setEnabled(false);}
    private void openResult(Uri uri){try{Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(uri,"application/pdf");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Открыть PDF"));}catch(Exception e){showError(new IllegalStateException("PDF сохранён, но открыть его не удалось"));}}
    private void shareResult(Uri uri){try{Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/pdf");i.putExtra(Intent.EXTRA_STREAM,uri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Поделиться PDF"));}catch(Exception e){showError(new IllegalStateException("Не удалось открыть меню отправки"));}}
    private void showError(Exception e){new AlertDialog.Builder(this).setTitle("Не получилось").setMessage(message(e)).setPositiveButton("OK",null).show();}private String message(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}private TextView text(String value,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}private int dp(int value){return(int)(value*getResources().getDisplayMetrics().density+.5f);}private static String safe(String s){return s==null?"":s;}@Override protected void onDestroy(){super.onDestroy();if(isFinishing())worker.shutdownNow();}

    private static final class EditorInfo{final FieldInfo info;final View view;EditorInfo(FieldInfo i,View v){info=i;view=v;}}
    private static final class FieldInfo{static final int TEXT=1,CHECK=2,RADIO=3,CHOICE=4;final String name,value;final int type;final boolean checked,multi;final List<String> display,export;private FieldInfo(String n,String v,int t,boolean c,List<String>d,List<String>e,boolean m){name=n;value=v;type=t;checked=c;display=d;export=e;multi=m;}static FieldInfo text(String n,String v){return new FieldInfo(n,v,TEXT,false,new ArrayList<>(),new ArrayList<>(),false);}static FieldInfo check(String n,boolean c){return new FieldInfo(n,c?"On":"Off",CHECK,c,new ArrayList<>(),new ArrayList<>(),false);}static FieldInfo radio(String n,String v,List<String>o){return new FieldInfo(n,v,RADIO,false,new ArrayList<>(o),new ArrayList<>(o),false);}static FieldInfo choice(String n,String v,List<String>d,List<String>e,boolean m){return new FieldInfo(n,v,CHOICE,false,d,e,m);}}
}
