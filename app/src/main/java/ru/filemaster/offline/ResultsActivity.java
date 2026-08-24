package ru.filemaster.offline;

import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ResultsActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(247, 248, 252), TEXT = Color.rgb(25, 28, 36), MUTED = Color.rgb(93, 99, 112), BLUE = Color.rgb(49, 87, 213);
    private LinearLayout listRoot;
    private TextView countView;
    private EditText search;
    private Spinner filterSpinner, sortSpinner;
    private List<Entry> all = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        buildUi(); load();
    }

    @Override protected void onResume() { super.onResume(); if (listRoot != null) load(); }

    private void buildUi() {
        LinearLayout screen = new LinearLayout(this); screen.setOrientation(LinearLayout.VERTICAL); screen.setBackgroundColor(BG);
        ViewCompat.setOnApplyWindowInsetsListener(screen, (v, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(dp(16), safe.top + dp(8), dp(16), safe.bottom + dp(12)); return insets;
        });
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("←", 30, TEXT, false); back.setGravity(Gravity.CENTER); back.setOnClickListener(v -> finish()); top.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL); titles.addView(text("Мои файлы", 27, TEXT, true)); titles.addView(text("Поиск, сортировка и управление результатами", 14, MUTED, false));
        top.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)); screen.addView(top);

        search = new EditText(this); search.setHint("Поиск по имени файла"); search.setSingleLine(true); search.setTextSize(15); search.setPadding(dp(14), dp(8), dp(14), dp(8));
        screen.addView(search, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){ renderList(); } public void afterTextChanged(Editable e){} });

        LinearLayout controls = new LinearLayout(this); controls.setPadding(0, dp(8), 0, dp(6));
        filterSpinner = new Spinner(this); String[] filters = {"Все типы", "PDF", "Изображения", "Документы", "Таблицы", "Архивы", "Текст"}; filterSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, filters));
        sortSpinner = new Spinner(this); String[] sorts = {"Сначала новые", "Сначала старые", "Имя А–Я", "Размер ↓", "Размер ↑"}; sortSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sorts));
        controls.addView(filterSpinner, new LinearLayout.LayoutParams(0, dp(52), 1f)); controls.addView(sortSpinner, new LinearLayout.LayoutParams(0, dp(52), 1f)); screen.addView(controls);
        filterSpinner.setOnItemSelectedListener(new SimpleItemListener(this::renderList)); sortSpinner.setOnItemSelectedListener(new SimpleItemListener(this::renderList));

        countView = text("", 13, Color.rgb(0, 135, 100), true); countView.setPadding(0, dp(4), 0, dp(8)); screen.addView(countView);
        ScrollView scroll = new ScrollView(this); listRoot = new LinearLayout(this); listRoot.setOrientation(LinearLayout.VERTICAL); listRoot.setPadding(0, 0, 0, dp(20)); scroll.addView(listRoot);
        screen.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)); setContentView(screen);
    }

    private void load() { all = loadFiles(); renderList(); }

    private void renderList() {
        if (listRoot == null) return;
        List<Entry> files = new ArrayList<>(); String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int filter = filterSpinner == null ? 0 : filterSpinner.getSelectedItemPosition();
        for (Entry e : all) {
            if (!q.isEmpty() && !e.name.toLowerCase(Locale.ROOT).contains(q) && !e.path.toLowerCase(Locale.ROOT).contains(q)) continue;
            if (!matchesFilter(e, filter)) continue; files.add(e);
        }
        int sort = sortSpinner == null ? 0 : sortSpinner.getSelectedItemPosition();
        switch (sort) {
            case 1 -> files.sort(Comparator.comparingLong(e -> e.date));
            case 2 -> files.sort(Comparator.comparing(e -> e.name.toLowerCase(Locale.ROOT)));
            case 3 -> files.sort((a,b) -> Long.compare(b.size, a.size));
            case 4 -> files.sort(Comparator.comparingLong(e -> e.size));
            default -> files.sort((a,b) -> Long.compare(b.date, a.date));
        }
        countView.setText(files.isEmpty() ? "Ничего не найдено" : "Показано: " + files.size() + " из " + all.size());
        listRoot.removeAllViews();
        if (files.isEmpty()) {
            TextView empty = text(all.isEmpty() ? "Файлов пока нет. Результаты появятся здесь после обработки." : "Измените поиск или фильтр.", 15, MUTED, false); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(8), dp(30), dp(8), dp(20)); listRoot.addView(empty); return;
        }
        int shown = Math.min(files.size(), 500);
        for (int i = 0; i < shown; i++) listRoot.addView(row(files.get(i)));
        if (files.size() > shown) { TextView more = text("Показаны первые " + shown + " результатов", 13, MUTED, false); more.setGravity(Gravity.CENTER); listRoot.addView(more); }
    }

    private boolean matchesFilter(Entry e, int filter) {
        if (filter == 0) return true; String badge = typeBadge(e.mime, e.name);
        return switch (filter) { case 1 -> badge.equals("PDF"); case 2 -> badge.equals("IMG"); case 3 -> badge.equals("DOC") || badge.equals("PPT"); case 4 -> badge.equals("XLS"); case 5 -> badge.equals("ZIP"); case 6 -> badge.equals("TXT"); default -> true; };
    }

    private List<Entry> loadFiles() {
        List<Entry> result = new ArrayList<>();
        String[] projection = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.RELATIVE_PATH};
        String selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?"; String prefix = Environment.DIRECTORY_DOWNLOADS + "/ФайлМастер%";
        try (Cursor c = getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, new String[]{prefix}, null)) {
            if (c == null) return result;
            int idCol=c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID), nameCol=c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME), mimeCol=c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE), sizeCol=c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE), dateCol=c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED), pathCol=c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH);
            while (c.moveToNext()) {
                long id=c.getLong(idCol), size=c.getLong(sizeCol), date=c.getLong(dateCol); String name=c.getString(nameCol), mime=c.getString(mimeCol), path=c.getString(pathCol);
                Uri uri=ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,id); result.add(new Entry(uri,name==null?"Файл":name,mime==null?"*/*":mime,size,date,path==null?"":path));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private LinearLayout row(Entry e) {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.HORIZONTAL); box.setGravity(Gravity.CENTER_VERTICAL); box.setPadding(dp(14),dp(12),dp(10),dp(12));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(15)); bg.setStroke(dp(1),Color.rgb(231,233,240)); box.setBackground(bg);
        TextView badge=text(typeBadge(e.mime,e.name),11,BLUE,true); badge.setGravity(Gravity.CENTER); android.graphics.drawable.GradientDrawable badgeBg=new android.graphics.drawable.GradientDrawable(); badgeBg.setColor(Color.rgb(235,239,255)); badgeBg.setCornerRadius(dp(12)); badge.setBackground(badgeBg); box.addView(badge,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout copy=new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL); copy.setPadding(dp(12),0,dp(8),0); TextView name=text(e.name,15,TEXT,true); name.setMaxLines(2); copy.addView(name);
        copy.addView(text(formatSize(e.size)+"  •  "+friendlyMime(e.mime),12,MUTED,false));
        String rel=e.path.replace(Environment.DIRECTORY_DOWNLOADS+"/ФайлМастер/","").replace(Environment.DIRECTORY_DOWNLOADS+"/ФайлМастер",""); if(!rel.isBlank()) copy.addView(text(rel,11,Color.rgb(120,125,137),false));
        box.addView(copy,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f)); TextView menu=text("⋮",26,BLUE,true); menu.setGravity(Gravity.CENTER); box.addView(menu,new LinearLayout.LayoutParams(dp(40),dp(48))); box.setOnClickListener(v->actions(e));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT); lp.setMargins(0,0,0,dp(8)); box.setLayoutParams(lp); return box;
    }

    private void actions(Entry e) {
        new AlertDialog.Builder(this).setTitle(e.name).setItems(new String[]{"Открыть","Поделиться","Переименовать","Удалить"},(d,which)->{
            if(which==0)open(e); else if(which==1)share(e); else if(which==2)rename(e); else confirmDelete(e);
        }).setNegativeButton("Закрыть",null).show();
    }

    private void rename(Entry e) {
        EditText edit=new EditText(this); edit.setText(e.name); edit.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Переименовать файл").setView(edit).setPositiveButton("Сохранить",(d,w)->{
            String name=edit.getText().toString().trim(); if(name.isEmpty()){Toast.makeText(this,"Имя не может быть пустым",Toast.LENGTH_LONG).show();return;}
            try{ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.DISPLAY_NAME,name);int changed=getContentResolver().update(e.uri,v,null,null);if(changed>0)load();else Toast.makeText(this,"Не удалось переименовать",Toast.LENGTH_LONG).show();}
            catch(Exception ex){Toast.makeText(this,"Android не разрешил переименовать файл",Toast.LENGTH_LONG).show();}
        }).setNegativeButton("Отмена",null).show();
    }

    private void open(Entry e){try{Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(e.uri,e.mime);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Открыть файл"));}catch(Exception ex){Toast.makeText(this,"Нет приложения для открытия этого файла",Toast.LENGTH_LONG).show();}}
    private void share(Entry e){try{Intent i=new Intent(Intent.ACTION_SEND);i.setType(e.mime);i.putExtra(Intent.EXTRA_STREAM,e.uri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Поделиться файлом"));}catch(Exception ex){Toast.makeText(this,"Не удалось открыть меню отправки",Toast.LENGTH_LONG).show();}}
    private void confirmDelete(Entry e){new AlertDialog.Builder(this).setTitle("Удалить файл?").setMessage(e.name+" будет удалён из памяти устройства.").setPositiveButton("Удалить",(d,w)->{try{int deleted=getContentResolver().delete(e.uri,null,null);if(deleted>0){RecentStore.removeUri(this,e.uri);load();}else Toast.makeText(this,"Не удалось удалить файл",Toast.LENGTH_LONG).show();}catch(Exception ex){Toast.makeText(this,"Android не разрешил удалить этот файл",Toast.LENGTH_LONG).show();}}).setNegativeButton("Отмена",null).show();}

    private String formatSize(long bytes){if(bytes<1024)return bytes+" Б";double kb=bytes/1024d;if(kb<1024)return String.format(Locale.getDefault(),"%.0f КБ",kb);return String.format(Locale.getDefault(),"%.1f МБ",kb/1024d);}
    private String friendlyMime(String mime){if(mime==null)return"файл";if(mime.equals("application/pdf"))return"PDF";if(mime.startsWith("image/"))return"изображение";if(mime.contains("zip")||mime.contains("7z")||mime.contains("gzip")||mime.contains("rar"))return"архив";if(mime.startsWith("text/"))return"текст";return"документ";}
    private String typeBadge(String mime,String name){String lower=name==null?"":name.toLowerCase(Locale.ROOT);if("application/pdf".equals(mime)||lower.endsWith(".pdf"))return"PDF";if(mime!=null&&mime.startsWith("image/"))return"IMG";if(lower.endsWith(".zip")||lower.endsWith(".7z")||lower.endsWith(".rar")||lower.endsWith(".gz")||lower.endsWith(".bz2")||lower.endsWith(".xz"))return"ZIP";if(lower.endsWith(".xlsx")||lower.endsWith(".csv")||lower.endsWith(".tsv"))return"XLS";if(lower.endsWith(".docx")||lower.endsWith(".odt"))return"DOC";if(lower.endsWith(".pptx"))return"PPT";if(lower.endsWith(".txt")||lower.endsWith(".md")||lower.endsWith(".rtf"))return"TXT";return"FILE";}
    private TextView text(String value,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private int dp(int value){return(int)(value*getResources().getDisplayMetrics().density+0.5f);}

    private static final class Entry{final Uri uri;final String name,mime,path;final long size,date;Entry(Uri uri,String name,String mime,long size,long date,String path){this.uri=uri;this.name=name;this.mime=mime;this.size=size;this.date=date;this.path=path;}}
    private static final class SimpleItemListener implements android.widget.AdapterView.OnItemSelectedListener { final Runnable run; SimpleItemListener(Runnable r){run=r;} public void onItemSelected(android.widget.AdapterView<?>p,View v,int pos,long id){run.run();} public void onNothingSelected(android.widget.AdapterView<?>p){} }
}
