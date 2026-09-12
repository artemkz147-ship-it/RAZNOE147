package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
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

public class DocumentEditorActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Uri inputUri; private EditText editor; private TextView status;
    @Override protected void onCreate(Bundle savedInstanceState){super.onCreate(savedInstanceState);WindowCompat.setDecorFitsSystemWindows(getWindow(),false);String raw=getIntent().getStringExtra("document_uri");if(raw==null||raw.isBlank()){finish();return;}inputUri=Uri.parse(raw);buildUi();load();}
    private void buildUi(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(247,248,252));ViewCompat.setOnApplyWindowInsetsListener(root,(v,i)->{Insets s=i.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(dp(12),s.top+dp(8),dp(12),s.bottom+dp(10));return i;});LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);TextView back=text("←",30,Color.rgb(25,28,36),false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());h.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.addView(text("Редактор документа",24,Color.rgb(25,28,36),true));status=text("Открываю текст…",13,Color.rgb(93,99,112),false);titles.addView(status);h.addView(titles,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));root.addView(h);TextView note=text("Редактор работает с текстовым содержимым DOCX/ODT/TXT/HTML/Markdown/RTF. Сложная исходная верстка Word/ODT при сохранении новой редактируемой копии упрощается.",12,Color.rgb(93,99,112),false);note.setPadding(dp(8),0,dp(8),dp(6));root.addView(note);editor=new EditText(this);editor.setGravity(Gravity.TOP|Gravity.START);editor.setTextSize(16);editor.setTextColor(Color.rgb(25,28,36));editor.setBackgroundColor(Color.WHITE);editor.setPadding(dp(14),dp(14),dp(14),dp(14));editor.setSingleLine(false);editor.setEnabled(false);root.addView(editor,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f));HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);LinearLayout buttons=new LinearLayout(this);buttons.setPadding(0,dp(6),0,0);buttons.addView(saveButton("DOCX",0));buttons.addView(saveButton("ODT",1));buttons.addView(saveButton("TXT",2));buttons.addView(saveButton("PDF",3));scroll.addView(buttons);root.addView(scroll,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(60)));setContentView(root);}
    private Button saveButton(String label,int mode){Button b=new Button(this);b.setText("Сохранить "+label);b.setAllCaps(false);b.setOnClickListener(v->save(mode));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,dp(52));lp.setMargins(dp(2),0,dp(2),0);b.setLayoutParams(lp);return b;}
    private void load(){worker.submit(()->{try{String text=EditableDocumentTools.read(this,inputUri);runOnUiThread(()->{editor.setText(text);editor.setEnabled(true);status.setText(FileStore.displayName(this,inputUri)+" • символов: "+text.length());});}catch(Exception e){runOnUiThread(()->showFatal(e));}});}
    private void save(int mode){String text=editor.getText().toString();ProgressDialog d=ProgressDialog.show(this,"Доки","Сохраняю документ…",true,false);worker.submit(()->{try{Uri out=switch(mode){case 0->EditableDocumentTools.saveDocx(this,text);case 1->EditableDocumentTools.saveOdt(this,text);case 2->EditableDocumentTools.saveTxt(this,text);default->EditableDocumentTools.savePdf(this,text);};runOnUiThread(()->{d.dismiss();ResultDialogs.show(this,"Новая отредактированная копия сохранена. Исходный файл не изменён.",out);});}catch(Exception e){runOnUiThread(()->{d.dismiss();showError(e);});}});}
    private void showFatal(Exception e){new AlertDialog.Builder(this).setTitle("Не удалось открыть документ").setMessage(msg(e)).setPositiveButton("Закрыть",(d,w)->finish()).show();}private void showError(Exception e){new AlertDialog.Builder(this).setTitle("Не получилось").setMessage(msg(e)).setPositiveButton("OK",null).show();}private String msg(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}private TextView text(String s,int sp,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);if(b)v.setTypeface(v.getTypeface(),android.graphics.Typeface.BOLD);return v;}private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}@Override protected void onDestroy(){super.onDestroy();if(isFinishing())worker.shutdownNow();}
}
