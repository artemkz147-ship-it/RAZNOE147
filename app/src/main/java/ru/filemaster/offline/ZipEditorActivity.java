package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ZipEditorActivity extends AppCompatActivity {
    private static final int PICK_ADD = 7201;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Row> rows = new ArrayList<>();
    private Uri sourceUri;
    private File workingZip;
    private char[] password;
    private LinearLayout listRoot;
    private TextView status;
    private Button deleteButton, saveButton;
    private boolean encrypted;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        String raw = getIntent().getStringExtra("zip_uri");
        if (raw == null || raw.isBlank()) { finish(); return; }
        sourceUri = Uri.parse(raw);
        buildUi();
        prepare();
    }

    private void buildUi() {
        LinearLayout screen = new LinearLayout(this); screen.setOrientation(LinearLayout.VERTICAL); screen.setBackgroundColor(Color.rgb(247,248,252));
        ViewCompat.setOnApplyWindowInsetsListener(screen,(v,insets)->{Insets safe=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(dp(12),safe.top+dp(8),dp(12),safe.bottom+dp(10));return insets;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView back=text("←",30,Color.rgb(25,28,36),false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.addView(text("Редактор ZIP",24,Color.rgb(25,28,36),true));status=text("Готовлю копию архива…",13,Color.rgb(93,99,112),false);titles.addView(status);header.addView(titles,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));screen.addView(header);
        TextView note=text("Исходный ZIP не изменяется. Добавление, удаление и переименование выполняются во временной копии; затем сохраняется новый ZIP.",12,Color.rgb(93,99,112),false);note.setPadding(dp(8),dp(4),dp(8),dp(8));screen.addView(note);
        ScrollView scroll=new ScrollView(this);listRoot=new LinearLayout(this);listRoot.setOrientation(LinearLayout.VERTICAL);listRoot.setPadding(0,dp(4),0,dp(6));scroll.addView(listRoot);screen.addView(scroll,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f));
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);Button add=button("+ Добавить");add.setOnClickListener(v->pickFiles());actions.addView(add,weighted());deleteButton=button("Удалить отмеченные");deleteButton.setOnClickListener(v->deleteSelected());actions.addView(deleteButton,weighted());screen.addView(actions);
        saveButton=button("Сохранить новый ZIP");saveButton.setTextSize(16);saveButton.setEnabled(false);saveButton.setOnClickListener(v->publish());LinearLayout.LayoutParams saveLp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(54));saveLp.setMargins(0,dp(6),0,0);screen.addView(saveButton,saveLp);setContentView(screen);
    }

    private void prepare() {
        worker.submit(()->{
            try {
                workingZip=FileStore.copyUriToTemp(this,sourceUri,".zip");
                ZipFile zip=new ZipFile(workingZip); encrypted=zip.isEncrypted();
                if(zip.isSplitArchive())throw new IllegalArgumentException("Разделённый ZIP можно распаковать, но ZIP-формат не разрешает изменять split/spanned архивы");
                runOnUiThread(()->{status.setText((encrypted?"Защищённый ZIP":"ZIP")+" • выберите элементы или добавьте файлы");saveButton.setEnabled(true);refresh();});
            }catch(Exception e){runOnUiThread(()->showFatal(e));}
        });
    }

    private void refresh() {
        if(workingZip==null)return;
        worker.submit(()->{
            try{
                ZipFile zip=currentZip();List<FileHeader> headers=zip.getFileHeaders();
                runOnUiThread(()->render(headers));
            }catch(Exception e){runOnUiThread(()->showError(e));}
        });
    }

    private ZipFile currentZip(){return password==null?new ZipFile(workingZip):new ZipFile(workingZip,password);}

    private void render(List<FileHeader> headers){rows.clear();listRoot.removeAllViews();int files=0;for(FileHeader h:headers)if(!h.isDirectory())files++;status.setText((encrypted?"Защищённый ZIP":"ZIP")+" • файлов: "+files);
        if(headers.isEmpty()){TextView empty=text("Архив пуст",15,Color.rgb(93,99,112),false);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(28),0,dp(20));listRoot.addView(empty);return;}
        for(FileHeader h:headers){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.HORIZONTAL);box.setGravity(Gravity.CENTER_VERTICAL);box.setPadding(dp(8),dp(6),dp(6),dp(6));CheckBox check=new CheckBox(this);check.setEnabled(!h.isDirectory());box.addView(check,new LinearLayout.LayoutParams(dp(44),dp(48)));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(text(h.getFileName(),14,Color.rgb(25,28,36),!h.isDirectory()));copy.addView(text(h.isDirectory()?"папка":size(h.getUncompressedSize()),11,Color.rgb(93,99,112),false));box.addView(copy,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));TextView rename=text("✎",22,Color.rgb(49,87,213),true);rename.setGravity(Gravity.CENTER);rename.setVisibility(h.isDirectory()?View.INVISIBLE:View.VISIBLE);rename.setOnClickListener(v->rename(h));box.addView(rename,new LinearLayout.LayoutParams(dp(44),dp(48)));listRoot.addView(box);rows.add(new Row(h,check));}
        updateDeleteState();for(Row r:rows)r.check.setOnCheckedChangeListener((b,c)->updateDeleteState());
    }

    private void updateDeleteState(){boolean any=false;for(Row r:rows)if(r.check.isChecked()){any=true;break;}deleteButton.setEnabled(any);}

    private void pickFiles(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,PICK_ADD);}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=PICK_ADD||resultCode!=RESULT_OK||data==null)return;List<Uri> uris=new ArrayList<>();ClipData clips=data.getClipData();if(clips!=null)for(int i=0;i<clips.getItemCount();i++)uris.add(clips.getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());if(uris.isEmpty())return;if(encrypted&&password==null){askPassword(()->addFiles(uris));}else addFiles(uris);}

    private void addFiles(List<Uri> uris){ProgressDialog d=ProgressDialog.show(this,"ZIP","Добавляю файлы…",true,false);worker.submit(()->{List<File> temps=new ArrayList<>();try{ZipFile zip=currentZip();for(Uri uri:uris){File temp=FileStore.copyUriToTemp(this,uri,".bin");temps.add(temp);ZipParameters p=new ZipParameters();p.setFileNameInZip(safeName(FileStore.displayName(this,uri)));if(encrypted){p.setEncryptFiles(true);p.setEncryptionMethod(EncryptionMethod.AES);p.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);}zip.addFile(temp,p);}runOnUiThread(()->{d.dismiss();refresh();});}catch(Exception e){runOnUiThread(()->{d.dismiss();showError(e);});}finally{for(File f:temps)f.delete();}});}

    private void deleteSelected(){List<String> names=new ArrayList<>();for(Row r:rows)if(r.check.isChecked())names.add(r.header.getFileName());if(names.isEmpty())return;new AlertDialog.Builder(this).setTitle("Удалить из копии ZIP?").setMessage("Выбрано: "+names.size()).setPositiveButton("Удалить",(d,w)->{ProgressDialog p=ProgressDialog.show(this,"ZIP","Удаляю записи…",true,false);worker.submit(()->{try{currentZip().removeFiles(names);runOnUiThread(()->{p.dismiss();refresh();});}catch(Exception e){runOnUiThread(()->{p.dismiss();showError(e);});}});}).setNegativeButton("Отмена",null).show();}

    private void rename(FileHeader header){EditText edit=new EditText(this);edit.setText(header.getFileName());edit.setSelectAllOnFocus(true);new AlertDialog.Builder(this).setTitle("Новое имя внутри ZIP").setView(edit).setPositiveButton("Переименовать",(d,w)->{String value=edit.getText().toString().trim();if(value.isEmpty())return;ProgressDialog p=ProgressDialog.show(this,"ZIP","Переименовываю…",true,false);worker.submit(()->{try{currentZip().renameFile(header.getFileName(),safePath(value));runOnUiThread(()->{p.dismiss();refresh();});}catch(Exception e){runOnUiThread(()->{p.dismiss();showError(e);});}});}).setNegativeButton("Отмена",null).show();}

    private void publish(){if(workingZip==null)return;ProgressDialog d=ProgressDialog.show(this,"ZIP","Сохраняю новый архив…",true,false);worker.submit(()->{try{Uri out=FileStore.publishFile(this,workingZip,"Изменённый_архив_"+System.currentTimeMillis()+".zip","application/zip",null);runOnUiThread(()->{d.dismiss();new AlertDialog.Builder(this).setTitle("ZIP готов").setMessage("Исходный архив остался без изменений.").setPositiveButton("Открыть",(x,w)->open(out)).setNeutralButton("Поделиться",(x,w)->share(out)).setNegativeButton("Закрыть",(x,w)->finish()).show();});}catch(Exception e){runOnUiThread(()->{d.dismiss();showError(e);});}});}

    private void askPassword(Runnable after){EditText e=new EditText(this);e.setHint("Пароль ZIP");e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);new AlertDialog.Builder(this).setTitle("Архив зашифрован").setMessage("Для добавления новых зашифрованных файлов нужен пароль архива.").setView(e).setPositiveButton("Продолжить",(d,w)->{String s=e.getText().toString();if(s.isEmpty()){Toast.makeText(this,"Введите пароль",Toast.LENGTH_LONG).show();return;}password=s.toCharArray();after.run();}).setNegativeButton("Отмена",null).show();}
    private void open(Uri uri){try{Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(uri,"application/zip");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Открыть ZIP"));}catch(Exception e){showError(new IllegalStateException("ZIP сохранён, но открыть его не удалось"));}}
    private void share(Uri uri){try{Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/zip");i.putExtra(Intent.EXTRA_STREAM,uri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Поделиться ZIP"));}catch(Exception e){showError(new IllegalStateException("Не удалось открыть меню отправки"));}}

    private String safeName(String s){return s.replace('\\','_').replace('/','_');}private String safePath(String s){String v=s.replace('\\','/');while(v.startsWith("/"))v=v.substring(1);if(v.contains("../")||v.equals(".."))throw new IllegalArgumentException("Недопустимый путь");return v;}
    private String size(long b){if(b<1024)return b+" Б";double k=b/1024d;if(k<1024)return String.format(java.util.Locale.getDefault(),"%.0f КБ",k);return String.format(java.util.Locale.getDefault(),"%.1f МБ",k/1024d);}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}private LinearLayout.LayoutParams weighted(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1f);p.setMargins(dp(2),0,dp(2),0);return p;}private TextView text(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(v.getTypeface(),android.graphics.Typeface.BOLD);return v;}
    private void showFatal(Exception e){new AlertDialog.Builder(this).setTitle("ZIP нельзя редактировать").setMessage(msg(e)).setPositiveButton("Закрыть",(d,w)->finish()).show();}private void showError(Exception e){new AlertDialog.Builder(this).setTitle("Не получилось").setMessage(msg(e)).setPositiveButton("OK",null).show();}private String msg(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onDestroy(){super.onDestroy();if(password!=null)java.util.Arrays.fill(password,'\0');if(workingZip!=null)workingZip.delete();if(isFinishing())worker.shutdownNow();}
    private static final class Row{final FileHeader header;final CheckBox check;Row(FileHeader h,CheckBox c){header=h;check=c;}}
}
