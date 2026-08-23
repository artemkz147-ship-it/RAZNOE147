package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_FILES = 1001;
    private static final int PICK_ONE = 1002;
    private static final int DRAW_SIGNATURE = 1003;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private String action = "";
    private Uri pendingPdf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PDFBoxResourceLoader.init(getApplicationContext());
        renderHome();

        Intent incoming = getIntent();
        if (Intent.ACTION_VIEW.equals(incoming.getAction()) && incoming.getData() != null) {
            pendingPdf = incoming.getData();
            showPdfQuickActions(pendingPdf);
        }
    }

    private void renderHome() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(28));
        scroll.addView(root);

        TextView title = text("ФайлМастер", 30, Color.rgb(25, 28, 36), true);
        root.addView(title);
        TextView subtitle = text("PDF • OCR • Подпись • Архиватор", 16, Color.DKGRAY, false);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle);

        TextView offline = text("✓ Полностью офлайн после установки  •  Файлы никуда не отправляются", 14, Color.rgb(0, 128, 96), true);
        offline.setPadding(dp(12), dp(10), dp(12), dp(10));
        offline.setBackground(rounded(Color.rgb(235, 250, 245), dp(12)));
        root.addView(offline);

        root.addView(section("PDF"));
        root.addView(tool("Объединить PDF", "Склеить несколько PDF в один", v -> pick(true, "application/pdf", "merge_pdf")));
        root.addView(tool("Разделить PDF", "Каждая страница — отдельный PDF", v -> pick(false, "application/pdf", "split_pdf")));
        root.addView(tool("Фото → PDF", "Несколько JPG/PNG в один документ", v -> pick(true, "image/*", "images_pdf")));
        root.addView(tool("PDF → JPG", "Экспорт всех страниц в изображения", v -> pick(false, "application/pdf", "pdf_jpg")));
        root.addView(tool("Подписать PDF", "Нарисовать подпись пальцем и встроить в документ", v -> pick(false, "application/pdf", "sign_pdf")));

        root.addView(section("OCR"));
        root.addView(tool("Распознать текст RU + EN", "Фото → текст. Языковые модели уже внутри APK", v -> pick(false, "image/*", "ocr")));

        root.addView(section("Архиватор"));
        root.addView(tool("Создать ZIP", "Упаковать несколько файлов", v -> pick(true, "*/*", "zip")));
        root.addView(tool("Создать 7Z", "Локальное LZMA2-сжатие", v -> pick(true, "*/*", "7z")));
        root.addView(tool("Распаковать ZIP / 7Z / RAR", "Распаковка в папку «Загрузки/ФайлМастер»", v -> pick(false, "*/*", "extract")));

        root.addView(section("Следующие модули"));
        TextView roadmap = text("Word / Excel / CSV / Markdown • редактор PDF • пароль и шифрование • криптографическая подпись сертификатом • сканер камеры", 15, Color.DKGRAY, false);
        roadmap.setPadding(dp(4), 0, dp(4), dp(20));
        root.addView(roadmap);

        setContentView(scroll);
    }

    private void pick(boolean multi, String mime, String nextAction) {
        action = nextAction;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multi);
        startActivityForResult(i, multi ? PICK_FILES : PICK_ONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == DRAW_SIGNATURE) {
            String path = data.getStringExtra("signature_path");
            if (path != null && pendingPdf != null) {
                File sig = new File(path);
                runTask("Добавляю подпись…", () -> PdfTools.addVisibleSignature(this, pendingPdf, sig), "Подписанный PDF сохранён в Загрузки/ФайлМастер");
            }
            return;
        }

        List<Uri> uris = collectUris(data);
        if (uris.isEmpty()) return;
        try {
            switch (action) {
                case "merge_pdf" -> runTask("Объединяю PDF…", () -> PdfTools.merge(this, uris), "PDF объединён");
                case "split_pdf" -> runTask("Разделяю PDF…", () -> PdfTools.split(this, uris.get(0)), "Страницы сохранены");
                case "images_pdf" -> runTask("Создаю PDF…", () -> PdfTools.imagesToPdf(this, uris), "PDF создан");
                case "pdf_jpg" -> runTask("Экспортирую страницы…", () -> PdfTools.pdfToJpeg(this, uris.get(0)), "Страницы сохранены как JPG");
                case "zip" -> runTask("Создаю ZIP…", () -> ArchiveTools.createZip(this, uris), "ZIP создан");
                case "7z" -> runTask("Создаю 7Z…", () -> ArchiveTools.create7z(this, uris), "7Z создан");
                case "extract" -> runTask("Распаковываю архив…", () -> ArchiveTools.extract(this, uris.get(0)), "Архив распакован");
                case "ocr" -> runOcr(uris.get(0));
                case "sign_pdf" -> {
                    pendingPdf = uris.get(0);
                    startActivityForResult(new Intent(this, SignatureActivity.class), DRAW_SIGNATURE);
                }
            }
        } catch (Exception e) {
            showError(e);
        }
    }

    private void runOcr(Uri uri) {
        ProgressDialog dialog = ProgressDialog.show(this, "OCR", "Распознаю текст на устройстве…", true, false);
        worker.submit(() -> {
            try {
                String result = OcrTools.recognize(this, uri);
                FileStore.publishBytes(this, result.getBytes(StandardCharsets.UTF_8), "OCR_" + System.currentTimeMillis() + ".txt", "text/plain", null);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    new AlertDialog.Builder(this)
                            .setTitle("Распознанный текст")
                            .setMessage(result.isBlank() ? "Текст не найден" : result)
                            .setPositiveButton("Готово", null)
                            .setNeutralButton("Файл сохранён", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { dialog.dismiss(); showError(e); });
            }
        });
    }

    private interface Work { Object run() throws Exception; }

    private void runTask(String label, Work work, String success) {
        ProgressDialog dialog = ProgressDialog.show(this, "ФайлМастер", label, true, false);
        worker.submit(() -> {
            try {
                Object result = work.run();
                runOnUiThread(() -> {
                    dialog.dismiss();
                    String suffix = result instanceof Integer ? " (" + result + ")" : "";
                    Toast.makeText(this, success + suffix, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { dialog.dismiss(); showError(e); });
            }
        });
    }

    private List<Uri> collectUris(Intent data) {
        List<Uri> list = new ArrayList<>();
        ClipData clips = data.getClipData();
        if (clips != null) {
            for (int i = 0; i < clips.getItemCount(); i++) list.add(clips.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            list.add(data.getData());
        }
        return list;
    }

    private void showPdfQuickActions(Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle("PDF открыт в ФайлМастер")
                .setItems(new String[]{"Подписать", "Разделить", "Экспортировать страницы в JPG"}, (d, which) -> {
                    pendingPdf = uri;
                    if (which == 0) startActivityForResult(new Intent(this, SignatureActivity.class), DRAW_SIGNATURE);
                    else if (which == 1) runTask("Разделяю PDF…", () -> PdfTools.split(this, uri), "Страницы сохранены");
                    else runTask("Экспортирую страницы…", () -> PdfTools.pdfToJpeg(this, uri), "Страницы сохранены как JPG");
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void showError(Exception e) {
        new AlertDialog.Builder(this)
                .setTitle("Не получилось")
                .setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                .setPositiveButton("OK", null)
                .show();
    }

    private TextView section(String text) {
        TextView v = text(text, 21, Color.rgb(25, 28, 36), true);
        v.setPadding(0, dp(26), 0, dp(10));
        return v;
    }

    private View tool(String title, String subtitle, View.OnClickListener listener) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackground(rounded(Color.WHITE, dp(15)));
        box.setElevation(dp(2));
        box.setOnClickListener(listener);

        TextView t = text(title, 17, Color.rgb(25, 28, 36), true);
        box.addView(t);
        TextView s = text(subtitle, 14, Color.DKGRAY, false);
        s.setPadding(0, dp(3), 0, 0);
        box.addView(s);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        box.setLayoutParams(lp);
        return box;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        return v;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) worker.shutdownNow();
    }
}
