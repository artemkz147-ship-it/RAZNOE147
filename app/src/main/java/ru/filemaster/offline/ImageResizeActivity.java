package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageResizeActivity extends AppCompatActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Uri uri;private EditText width,height;private CheckBox lock;private TextView source;private int srcW,srcH;private boolean syncing;
    @Override protected void onCreate(Bundle b){super.onCreate(b);WindowCompat.setDecorFitsSystemWindows(getWindow(),false);String raw=getIntent().getStringExtra("image_uri");if(raw==null||raw.isBlank()){finish();return;}uri=Uri.parse(raw);build();loadSize();}
    private void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(247,248,252));ViewCompat.setOnApplyWindowInsetsListener(root,(v,i)->{Insets s=i.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(dp(14)+s.left,dp(10)+s.top,dp(14)+s.right,dp(12)+s.bottom);return i;});LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView back=t("←",30,true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.addView(t("Размер и пропорции",24,true));source=t("Читаю исходный размер…",13,false);names.addView(source);top.addView(names,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));root.addView(top);TextView hint=t("Укажите ширину и высоту в пикселях. С замком пропорций изменение одной стороны автоматически пересчитает вторую.",13,false);hint.setPadding(dp(4),dp(8),dp(4),dp(16));root.addView(hint);
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.HORIZONTAL);width=field("Ширина, px");height=field("Высота, px");LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(58),1f);a.setMargins(0,0,dp(6),0);LinearLayout.LayoutParams c=new LinearLayout.LayoutParams(0,dp(58),1f);c.setMargins(dp(6),0,0,0);fields.addView(width,a);fields.addView(height,c);root.addView(fields);lock=new CheckBox(this);lock.setText("Сохранять исходные пропорции");lock.setChecked(true);root.addView(lock);TextView exact=t("Если выключить замок, получится точный размер W × H даже с изменением пропорций.",12,false);exact.setPadding(dp(4),0,dp(4),dp(12));root.addView(exact);TextView spacer=new TextView(this);root.addView(spacer,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));Button save=new Button(this);save.setText("Создать изображение нужного размера");save.setAllCaps(false);save.setOnClickListener(v->save());root.addView(save,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));setContentView(root);
        width.addTextChangedListener(watcher(true));height.addTextChangedListener(watcher(false));}
    private void loadSize(){worker.submit(()->{try{ImageAdvancedTools.Size s=ImageAdvancedTools.size(this,uri);srcW=s.width;srcH=s.height;runOnUiThread(()->{source.setText("Исходник: "+srcW+" × "+srcH+" px");syncing=true;width.setText(String.valueOf(srcW));height.setText(String.valueOf(srcH));syncing=false;});}catch(Exception e){runOnUiThread(()->error(e));}});}
    private TextWatcher watcher(boolean fromWidth){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){if(syncing||!lock.isChecked()||srcW<=0||srcH<=0)return;try{int value=Integer.parseInt(s.toString());if(value<1)return;syncing=true;if(fromWidth)height.setText(String.valueOf(Math.max(1,Math.round(value*srcH/(float)srcW))));else width.setText(String.valueOf(Math.max(1,Math.round(value*srcW/(float)srcH))));}catch(Exception ignored){}finally{syncing=false;}}public void afterTextChanged(Editable e){}};}
    private void save(){try{int w=Integer.parseInt(width.getText().toString().trim()),h=Integer.parseInt(height.getText().toString().trim());if(w<1||h<1)throw new NumberFormatException();ProgressDialog d=ProgressDialog.show(this,"Доки","Меняю размер…",true,false);worker.submit(()->{try{Uri out=ImageAdvancedTools.resizeExact(this,uri,w,h);runOnUiThread(()->{d.dismiss();new AlertDialog.Builder(this).setTitle("Готово").setMessage("Новый размер: "+w+" × "+h+" px").setPositiveButton("Открыть",(x,z)->ViewerIntents.open(this,out)).setNeutralButton("Поделиться",(x,z)->ViewerIntents.share(this,out)).setNegativeButton("Закрыть",null).show();});}catch(Exception e){runOnUiThread(()->{d.dismiss();error(e);});}});}catch(Exception e){error(new IllegalArgumentException("Введите ширину и высоту в пикселях"));}}
    private EditText field(String hint){EditText e=new EditText(this);e.setHint(hint);e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setSingleLine(true);e.setPadding(dp(12),0,dp(12),0);return e;}private TextView t(String s,int sp,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(bold?Color.rgb(25,28,36):Color.rgb(93,99,112));if(bold)v.setTypeface(v.getTypeface(),android.graphics.Typeface.BOLD);return v;}private void error(Exception e){new AlertDialog.Builder(this).setTitle("Не получилось").setMessage(e.getMessage()==null?"Ошибка":e.getMessage()).setPositiveButton("OK",null).show();}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}@Override protected void onDestroy(){super.onDestroy();worker.shutdownNow();}
}
