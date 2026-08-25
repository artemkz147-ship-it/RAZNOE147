package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.graphics.Typeface;
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

public class TableEditorActivity extends AppCompatActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private Uri inputUri;private EditText editor;private TextView status;
    @Override protected void onCreate(Bundle b){super.onCreate(b);WindowCompat.setDecorFitsSystemWindows(getWindow(),false);String raw=getIntent().getStringExtra("table_uri");if(raw==null||raw.isBlank()){finish();return;}inputUri=Uri.parse(raw);build();load();}
    private void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(247,248,252));ViewCompat.setOnApplyWindowInsetsListener(root,(v,i)->{Insets s=i.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(dp(12),s.top+dp(8),dp(12),s.bottom+dp(10));return i;});LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);TextView back=text("←",30,Color.rgb(25,28,36),false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());h.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.addView(text("Редактор таблицы",24,Color.rgb(25,28,36),true));status=text("Открываю таблицу…",13,Color.rgb(93,99,112),false);titles.addView(status);h.addView(titles,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));root.addView(h);TextView note=text("Строка = строка таблицы, столбцы разделены табуляцией. В XLSX редактируется первый лист; формулы можно вводить как =SUM(A1:A3). Стили и диаграммы при сохранении новой упрощённой копии не переносятся.",12,Color.rgb(93,99,112),false);note.setPadding(dp(8),0,dp(8),dp(6));root.addView(note);editor=new EditText(this);editor.setGravity(Gravity.TOP|Gravity.START);editor.setTypeface(Typeface.MONOSPACE);editor.setTextSize(14);editor.setTextColor(Color.rgb(25,28,36));editor.setBackgroundColor(Color.WHITE);editor.setPadding(dp(12),dp(12),dp(12),dp(12));editor.setHorizontallyScrolling(true);editor.setSingleLine(false);editor.setEnabled(false);root.addView(editor,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f));HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);LinearLayout actions=new LinearLayout(this);actions.setPadding(0,dp(6),0,0);actions.addView(saveButton("XLSX",0));actions.addView(saveButton("CSV",1));actions.addView(saveButton("TSV",2));scroll.addView(actions);root.addView(scroll,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(60)));setContentView(root);}
    private void load(){worker.submit(()->{try{String s=EditableTableTools.readAsTsv(this,inputUri);runOnUiThread(()->{editor.setText(s);editor.setEnabled(true);int rows=s.isEmpty()?0:s.split("\n",-1).length;status.setText(FileStore.displayName(this,inputUri)+" • строк: "+rows);});}catch(Exception e){runOnUiThread(()->fatal(e));}});}
    private Button saveButton(String label,int mode){Button b=new Button(this);b.setText("Сохранить "+label);b.setAllCaps(false);b.setOnClickListener(v->save(mode));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,dp(52));lp.setMargins(dp(2),0,dp(2),0);b.setLayoutParams(lp);return b;}
    private void save(int mode){String value=editor.getText().toString();ProgressDialog d=ProgressDialog.show(this,"Доки","Сохраняю таблицу…",true,false);worker.submit(()->{try{Uri out=switch(mode){case 0->EditableTableTools.saveXlsx(this,value);case 1->EditableTableTools.saveCsv(this,value);default->EditableTableTools.saveTsv(this,value);};runOnUiThread(()->{d.dismiss();new AlertDialog.Builder(this).setTitle("Готово").setMessage("Новая таблица сохранена. Исходный файл не изменён.").setPositiveButton("Открыть в Доки",(x,w)->ViewerIntents.open(this,out)).setNeutralButton("Поделиться",(x,w)->ViewerIntents.share(this,out)).setNegativeButton("Продолжить",null).show();});}catch(Exception e){runOnUiThread(()->{d.dismiss();error(e);});}});}
    private void fatal(Exception e){new AlertDialog.Builder(this).setTitle("Не удалось открыть таблицу").setMessage(msg(e)).setPositiveButton("Закрыть",(d,w)->finish()).show();}private void error(Exception e){new AlertDialog.Builder(this).setTitle("Не получилось").setMessage(msg(e)).setPositiveButton("OK",null).show();}private String msg(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}private TextView text(String s,int sp,int c,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}@Override protected void onDestroy(){super.onDestroy();if(isFinishing())worker.shutdownNow();}
}
