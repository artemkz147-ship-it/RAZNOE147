package ru.filemaster.offline;

import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ResultsActivity extends AppCompatActivity {
    private static final int BG = Color.rgb(247, 248, 252);
    private static final int TEXT = Color.rgb(25, 28, 36);
    private static final int MUTED = Color.rgb(93, 99, 112);
    private static final int BLUE = Color.rgb(49, 87, 213);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getWindow().getDecorView().isAttachedToWindow()) render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(0, safe.top, 0, safe.bottom);
            return insets;
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(28));
        scroll.addView(root);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("←", 30, TEXT, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Мои файлы", 27, TEXT, true));
        titles.addView(text("Результаты, созданные ФайлМастер", 14, MUTED, false));
        top.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top);

        List<Entry> files = loadFiles();
        TextView count = text(files.isEmpty() ? "Файлов пока нет" : "Найдено: " + files.size(), 13, Color.rgb(0, 135, 100), true);
        count.setPadding(0, dp(8), 0, dp(12));
        root.addView(count);

        if (files.isEmpty()) {
            TextView empty = text("После конвертации, сканирования, подписи или архивации результаты появятся здесь. Сами файлы хранятся в «Загрузки / ФайлМастер».", 15, MUTED, false);
            empty.setPadding(dp(6), dp(18), dp(6), dp(18));
            root.addView(empty);
        } else {
            int shown = Math.min(files.size(), 200);
            for (int i = 0; i < shown; i++) root.addView(row(files.get(i)));
            if (files.size() > shown) {
                TextView more = text("Показаны последние " + shown + " файлов", 13, MUTED, false);
                more.setGravity(Gravity.CENTER);
                more.setPadding(0, dp(12), 0, 0);
                root.addView(more);
            }
        }
        setContentView(scroll);
    }

    private List<Entry> loadFiles() {
        List<Entry> result = new ArrayList<>();
        String[] projection = {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.RELATIVE_PATH
        };
        String selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
        String prefix = Environment.DIRECTORY_DOWNLOADS + "/ФайлМастер%";
        try (Cursor c = getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection,
                selection, new String[]{prefix}, MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
            if (c == null) return result;
            int idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);
            int sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE);
            while (c.moveToNext()) {
                long id = c.getLong(idCol);
                String name = c.getString(nameCol);
                String mime = c.getString(mimeCol);
                long size = c.getLong(sizeCol);
                Uri uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
                result.add(new Entry(uri, name == null ? "Файл" : name, mime == null ? "*/*" : mime, size));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private LinearLayout row(Entry e) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(14), dp(12), dp(10), dp(12));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(15));
        bg.setStroke(dp(1), Color.rgb(231, 233, 240));
        box.setBackground(bg);

        TextView badge = text(typeBadge(e.mime, e.name), 11, BLUE, true);
        badge.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable badgeBg = new android.graphics.drawable.GradientDrawable();
        badgeBg.setColor(Color.rgb(235, 239, 255));
        badgeBg.setCornerRadius(dp(12));
        badge.setBackground(badgeBg);
        box.addView(badge, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(8), 0);
        TextView name = text(e.name, 15, TEXT, true);
        name.setMaxLines(2);
        copy.addView(name);
        copy.addView(text(formatSize(e.size) + "  •  " + friendlyMime(e.mime), 12, MUTED, false));
        box.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView menu = text("⋮", 26, BLUE, true);
        menu.setGravity(Gravity.CENTER);
        box.addView(menu, new LinearLayout.LayoutParams(dp(40), dp(48)));
        box.setOnClickListener(v -> actions(e));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        box.setLayoutParams(lp);
        return box;
    }

    private void actions(Entry e) {
        new AlertDialog.Builder(this).setTitle(e.name)
                .setItems(new String[]{"Открыть", "Поделиться", "Удалить"}, (d, which) -> {
                    if (which == 0) open(e);
                    else if (which == 1) share(e);
                    else confirmDelete(e);
                }).setNegativeButton("Закрыть", null).show();
    }

    private void open(Entry e) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(e.uri, e.mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Открыть файл"));
        } catch (Exception ex) { Toast.makeText(this, "Нет приложения для открытия этого файла", Toast.LENGTH_LONG).show(); }
    }

    private void share(Entry e) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(e.mime);
            i.putExtra(Intent.EXTRA_STREAM, e.uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Поделиться файлом"));
        } catch (Exception ex) { Toast.makeText(this, "Не удалось открыть меню отправки", Toast.LENGTH_LONG).show(); }
    }

    private void confirmDelete(Entry e) {
        new AlertDialog.Builder(this).setTitle("Удалить файл?")
                .setMessage(e.name + " будет удалён из памяти устройства.")
                .setPositiveButton("Удалить", (d, w) -> {
                    try {
                        int deleted = getContentResolver().delete(e.uri, null, null);
                        if (deleted > 0) {
                            RecentStore.removeUri(this, e.uri);
                            render();
                        } else Toast.makeText(this, "Не удалось удалить файл", Toast.LENGTH_LONG).show();
                    } catch (Exception ex) { Toast.makeText(this, "Android не разрешил удалить этот файл", Toast.LENGTH_LONG).show(); }
                }).setNegativeButton("Отмена", null).show();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " Б";
        double kb = bytes / 1024d;
        if (kb < 1024) return String.format(Locale.getDefault(), "%.0f КБ", kb);
        return String.format(Locale.getDefault(), "%.1f МБ", kb / 1024d);
    }

    private String friendlyMime(String mime) {
        if (mime == null) return "файл";
        if (mime.equals("application/pdf")) return "PDF";
        if (mime.startsWith("image/")) return "изображение";
        if (mime.contains("zip") || mime.contains("7z") || mime.contains("gzip")) return "архив";
        if (mime.startsWith("text/")) return "текст";
        return "документ";
    }

    private String typeBadge(String mime, String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if ("application/pdf".equals(mime) || lower.endsWith(".pdf")) return "PDF";
        if (mime != null && mime.startsWith("image/")) return "IMG";
        if (lower.endsWith(".zip") || lower.endsWith(".7z") || lower.endsWith(".rar") || lower.endsWith(".gz")) return "ZIP";
        if (lower.endsWith(".xlsx") || lower.endsWith(".csv")) return "XLS";
        if (lower.endsWith(".docx") || lower.endsWith(".odt")) return "DOC";
        return "FILE";
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class Entry {
        final Uri uri;
        final String name;
        final String mime;
        final long size;
        Entry(Uri uri, String name, String mime, long size) {
            this.uri = uri;
            this.name = name;
            this.mime = mime;
            this.size = size;
        }
    }
}
