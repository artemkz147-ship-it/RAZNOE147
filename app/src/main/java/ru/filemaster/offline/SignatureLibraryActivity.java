package ru.filemaster.offline;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SignatureLibraryActivity extends AppCompatActivity {
    private static final int DRAW = 3101;
    private boolean selectMode;
    private LinearLayout list;
    private TextView subtitle;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        selectMode = getIntent().getBooleanExtra("select_mode", false);
        build(); render();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(247,248,252));
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,i)->{Insets s=i.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(dp(12)+s.left,dp(8)+s.top,dp(12)+s.right,dp(10)+s.bottom);return i;});
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("←",30,true); back.setGravity(Gravity.CENTER); back.setOnClickListener(v->finish()); top.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout names = new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL); names.addView(text(selectMode?"Выберите подпись":"Мои подписи",24,true)); subtitle=text("",13,false); names.addView(subtitle); top.addView(names,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)); root.addView(top);
        TextView note=text(selectMode?"Нажмите на сохранённую подпись, чтобы разместить её в PDF. Новую можно нарисовать здесь же.":"Подписи хранятся только на этом устройстве и остаются доступными после закрытия приложения.",13,false);note.setPadding(dp(6),0,dp(6),dp(10));root.addView(note);
        ScrollView scroll=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        Button draw=new Button(this);draw.setText("+ Нарисовать новую подпись");draw.setAllCaps(false);draw.setOnClickListener(v->startActivityForResult(new Intent(this,SignatureActivity.class),DRAW));root.addView(draw,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));setContentView(root);
    }

    private void render() {
        List<File> files=SignatureStore.list(this);list.removeAllViews();subtitle.setText(files.isEmpty()?"Сохранённых подписей пока нет":"Сохранено: "+files.size());
        if(files.isEmpty()){TextView empty=text("Нарисуйте подпись один раз — после этого её можно использовать повторно в любых PDF.",15,false);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(20),dp(40),dp(20),dp(20));list.addView(empty);return;}
        SimpleDateFormat fmt=new SimpleDateFormat("dd.MM.yyyy • HH:mm", Locale.getDefault());
        for(File file:files){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(8),dp(8),dp(8));row.setBackgroundColor(Color.WHITE);
            ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackgroundColor(Color.rgb(250,250,250));Bitmap b=BitmapFactory.decodeFile(file.getAbsolutePath());preview.setImageBitmap(b);row.addView(preview,new LinearLayout.LayoutParams(dp(130),dp(72)));
            LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(12),0,dp(6),0);info.addView(text("Сохранённая подпись",15,true));info.addView(text(fmt.format(new Date(file.lastModified())),12,false));info.addView(text(selectMode?"Нажмите, чтобы вставить":"Можно использовать в любом PDF",11,false));row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            Button del=new Button(this);del.setText("×");del.setTextSize(20);del.setOnClickListener(v->confirmDelete(file));row.addView(del,new LinearLayout.LayoutParams(dp(48),dp(48)));
            row.setOnClickListener(v->{if(selectMode)select(file);else preview(file);});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);rp.setMargins(0,0,0,dp(8));list.addView(row,rp);
        }
    }

    private void select(File file){Intent result=new Intent();result.putExtra("signature_path",file.getAbsolutePath());setResult(RESULT_OK,result);finish();}
    private void preview(File file){ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setPadding(dp(12),dp(12),dp(12),dp(12));image.setImageURI(Uri.fromFile(file));new AlertDialog.Builder(this).setTitle("Сохранённая подпись").setView(image).setPositiveButton("Закрыть",null).show();}
    private void confirmDelete(File file){new AlertDialog.Builder(this).setTitle("Удалить подпись?").setMessage("Она исчезнет из библиотеки, но уже подписанные PDF не изменятся.").setPositiveButton("Удалить",(d,w)->{SignatureStore.delete(file);render();}).setNegativeButton("Отмена",null).show();}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==DRAW&&resultCode==RESULT_OK&&data!=null){String path=data.getStringExtra("signature_path");if(selectMode&&path!=null&&!path.isBlank()){select(new File(path));return;}render();}}
    private TextView text(String s,int sp,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(bold?Color.rgb(25,28,36):Color.rgb(93,99,112));if(bold)v.setTypeface(v.getTypeface(),android.graphics.Typeface.BOLD);return v;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
