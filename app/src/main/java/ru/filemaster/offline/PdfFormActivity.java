package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfFormActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, EditText> editors = new LinkedHashMap<>();
    private Uri pdfUri;
    private LinearLayout fieldsRoot;
    private Button saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PDFBoxResourceLoader.init(getApplicationContext());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        String raw = getIntent().getStringExtra("pdf_uri");
        if (raw == null || raw.isBlank()) { finish(); return; }
        pdfUri = Uri.parse(raw);
        buildUi();
        loadFields();
    }

    private void buildUi() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Color.rgb(247, 248, 252));
        ViewCompat.setOnApplyWindowInsetsListener(screen, (v, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(dp(14), safe.top + dp(8), dp(14), safe.bottom + dp(12));
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("←", 30, Color.rgb(25, 28, 36), false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(50), dp(50)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Заполнить PDF-форму", 24, Color.rgb(25, 28, 36), true));
        titles.addView(text("Редактируются существующие текстовые поля формы", 13, Color.rgb(93, 99, 112), false));
        header.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        screen.addView(header);

        TextView note = text("Приложение не добавляет новые поля — оно заполняет текстовые поля, которые уже созданы в PDF. Кнопки, флажки и списки в этой версии остаются без изменений.", 12, Color.rgb(93, 99, 112), false);
        note.setPadding(dp(8), dp(4), dp(8), dp(8));
        screen.addView(note);

        ScrollView scroll = new ScrollView(this);
        fieldsRoot = new LinearLayout(this);
        fieldsRoot.setOrientation(LinearLayout.VERTICAL);
        fieldsRoot.setPadding(dp(2), dp(8), dp(2), dp(10));
        scroll.addView(fieldsRoot);
        screen.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        saveButton = new Button(this);
        saveButton.setText("Сохранить заполненный PDF");
        saveButton.setAllCaps(false);
        saveButton.setEnabled(false);
        saveButton.setOnClickListener(v -> save());
        screen.addView(saveButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));
        setContentView(screen);
    }

    private void loadFields() {
        fieldsRoot.removeAllViews();
        TextView loading = text("Читаю поля формы…", 15, Color.rgb(93, 99, 112), false);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, dp(30), 0, dp(30));
        fieldsRoot.addView(loading);

        worker.submit(() -> {
            File input = null;
            try {
                input = FileStore.copyUriToTemp(this, pdfUri, ".pdf");
                List<FieldInfo> fields = new ArrayList<>();
                int unsupported = 0;
                try (PDDocument doc = PDDocument.load(input)) {
                    PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
                    if (form == null) throw new IllegalArgumentException("В этом PDF нет интерактивной формы");
                    for (PDField field : form.getFieldTree()) {
                        if (field instanceof PDTextField) {
                            String name = field.getFullyQualifiedName();
                            if (name == null || name.isBlank()) name = "Поле " + (fields.size() + 1);
                            String value = field.getValueAsString();
                            fields.add(new FieldInfo(name, value == null ? "" : value));
                        } else {
                            unsupported++;
                        }
                    }
                }
                if (fields.isEmpty()) {
                    throw new IllegalArgumentException(unsupported > 0
                            ? "Текстовых полей нет. Найдены только другие типы полей: " + unsupported
                            : "Текстовые поля формы не найдены");
                }
                int other = unsupported;
                runOnUiThread(() -> showFields(fields, other));
            } catch (Exception e) {
                runOnUiThread(() -> showLoadError(e));
            } finally { if (input != null) input.delete(); }
        });
    }

    private void showFields(List<FieldInfo> fields, int unsupported) {
        editors.clear();
        fieldsRoot.removeAllViews();
        TextView summary = text("Текстовых полей: " + fields.size() + (unsupported > 0 ? "  •  других полей: " + unsupported : ""), 13, Color.rgb(0, 135, 100), true);
        summary.setPadding(dp(4), 0, dp(4), dp(10));
        fieldsRoot.addView(summary);
        for (FieldInfo info : fields) {
            LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(dp(12), dp(10), dp(12), dp(10));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(14));
            bg.setStroke(dp(1), Color.rgb(231, 233, 240));
            block.setBackground(bg);

            TextView label = text(info.name, 14, Color.rgb(25, 28, 36), true);
            block.addView(label);
            EditText edit = new EditText(this);
            edit.setText(info.value);
            edit.setTextSize(15);
            edit.setSingleLine(false);
            edit.setMinLines(1);
            edit.setMaxLines(5);
            edit.setHint("Введите значение");
            block.addView(edit, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            editors.put(info.name, edit);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(8));
            fieldsRoot.addView(block, lp);
        }
        saveButton.setEnabled(true);
    }

    private void save() {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, EditText> e : editors.entrySet()) values.put(e.getKey(), e.getValue().getText().toString());
        if (values.isEmpty()) return;
        ProgressDialog dialog = ProgressDialog.show(this, "ФайлМастер", "Заполняю форму…", true, false);
        worker.submit(() -> {
            File input = null;
            File out = null;
            try {
                input = FileStore.copyUriToTemp(this, pdfUri, ".pdf");
                out = File.createTempFile("form_filled_", ".pdf", getCacheDir());
                try (PDDocument doc = PDDocument.load(input)) {
                    PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
                    if (form == null) throw new IllegalArgumentException("Форма исчезла из документа");
                    for (Map.Entry<String, String> item : values.entrySet()) {
                        PDField field = form.getField(item.getKey());
                        if (field instanceof PDTextField) field.setValue(item.getValue());
                    }
                    doc.save(out);
                }
                Uri result = FileStore.publishFile(this, out, "Заполненная_форма_" + System.currentTimeMillis() + ".pdf", "application/pdf", null);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    new AlertDialog.Builder(this).setTitle("Готово")
                            .setMessage("Заполненный PDF сохранён как новый файл.")
                            .setPositiveButton("Открыть", (d, w) -> openResult(result))
                            .setNeutralButton("Поделиться", (d, w) -> shareResult(result))
                            .setNegativeButton("Закрыть", (d, w) -> finish()).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { dialog.dismiss(); showError(e); });
            } finally {
                if (input != null) input.delete();
                if (out != null) out.delete();
            }
        });
    }

    private void showLoadError(Exception e) {
        fieldsRoot.removeAllViews();
        TextView error = text(e.getMessage() == null ? "Не удалось прочитать форму" : e.getMessage(), 15, Color.rgb(160, 45, 45), false);
        error.setGravity(Gravity.CENTER);
        error.setPadding(dp(12), dp(30), dp(12), dp(20));
        fieldsRoot.addView(error);
        saveButton.setEnabled(false);
    }

    private void openResult(Uri uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/pdf");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Открыть PDF"));
        } catch (Exception e) { showError(new IllegalStateException("PDF сохранён, но открыть его не удалось")); }
    }

    private void shareResult(Uri uri) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("application/pdf");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Поделиться PDF"));
        } catch (Exception e) { showError(new IllegalStateException("Не удалось открыть меню отправки")); }
    }

    private void showError(Exception e) {
        new AlertDialog.Builder(this).setTitle("Не получилось")
                .setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                .setPositiveButton("OK", null).show();
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) worker.shutdownNow();
    }

    private static final class FieldInfo {
        final String name;
        final String value;
        FieldInfo(String name, String value) { this.name = name; this.value = value; }
    }
}
