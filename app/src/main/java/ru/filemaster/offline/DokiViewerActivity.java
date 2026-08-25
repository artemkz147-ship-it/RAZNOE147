package ru.filemaster.offline;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.ImageType;
import com.tom_roush.pdfbox.rendering.PDFRenderer;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Internal read-only viewer used both from Android ACTION_VIEW and app results. */
public class DokiViewerActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Uri uri;
    private String name;
    private String mime;
    private LinearLayout content;
    private TextView status;
    private File pdfTemp;
    private PDDocument pdfDoc;
    private PDFRenderer pdfRenderer;
    private int pdfPage;
    private ImageView pdfImage;
    private TextView pdfPageLabel;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        PDFBoxResourceLoader.init(getApplicationContext());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        uri = getIntent().getData();
        if (uri == null) {
            String raw = getIntent().getStringExtra("file_uri");
            if (raw != null && !raw.isBlank()) uri = Uri.parse(raw);
        }
        if (uri == null) { finish(); return; }
        name = FileStore.displayName(this, uri);
        mime = getContentResolver().getType(uri);
        if (mime == null) mime = mimeFromName(name);
        buildUi();
        load();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247,248,252));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(dp(10) + s.left, dp(8) + s.top, dp(10) + s.right, dp(8) + s.bottom);
            return insets;
        });

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("←", 30, Color.rgb(25,28,36), false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout names = new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(name, 20, Color.rgb(25,28,36), true); title.setMaxLines(1);
        status = text("Открываю…", 12, Color.rgb(93,99,112), false);
        names.addView(title); names.addView(status);
        top.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView share = text("↗", 27, Color.rgb(49,87,213), true); share.setGravity(Gravity.CENTER); share.setOnClickListener(v -> ViewerIntents.share(this, uri));
        top.addView(share, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(top);

        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setGravity(Gravity.CENTER_HORIZONTAL);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void load() {
        String lower = name.toLowerCase(Locale.ROOT);
        if ("application/pdf".equals(mime) || lower.endsWith(".pdf")) loadPdf();
        else if ((mime != null && mime.startsWith("image/")) || isImageName(lower)) loadImage();
        else if (lower.endsWith(".xlsx") || lower.endsWith(".csv") || lower.endsWith(".tsv")) loadTable();
        else if (lower.endsWith(".pptx")) loadPresentation();
        else if (lower.endsWith(".zip") || lower.endsWith(".7z") || lower.endsWith(".rar")) loadArchive();
        else loadDocumentText();
    }

    private void showLoading(String value) {
        content.removeAllViews();
        ProgressBar p = new ProgressBar(this); content.addView(p, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView t = text(value, 14, Color.rgb(93,99,112), false); t.setPadding(0, dp(10), 0, 0); content.addView(t);
    }

    private void loadPdf() {
        showLoading("Готовлю PDF…");
        worker.submit(() -> {
            try {
                pdfTemp = FileStore.copyUriToTemp(this, uri, ".pdf");
                pdfDoc = PDDocument.load(pdfTemp);
                if (pdfDoc.getNumberOfPages() == 0) throw new IllegalArgumentException("PDF без страниц");
                pdfRenderer = new PDFRenderer(pdfDoc);
                runOnUiThread(this::buildPdfUi);
            } catch (Exception e) { runOnUiThread(() -> error(e)); }
        });
    }

    private void buildPdfUi() {
        content.removeAllViews();
        status.setText("PDF • " + pdfDoc.getNumberOfPages() + " стр.");
        pdfImage = new ImageView(this); pdfImage.setAdjustViewBounds(true); pdfImage.setScaleType(ImageView.ScaleType.FIT_CENTER); pdfImage.setBackgroundColor(Color.WHITE);
        content.addView(pdfImage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setPadding(0, dp(8), 0, dp(4));
        Button prev = button("‹"); prev.setOnClickListener(v -> { if (pdfPage > 0) { pdfPage--; renderPdfPage(); }});
        pdfPageLabel = text("", 14, Color.rgb(25,28,36), true); pdfPageLabel.setGravity(Gravity.CENTER);
        Button next = button("›"); next.setOnClickListener(v -> { if (pdfDoc != null && pdfPage + 1 < pdfDoc.getNumberOfPages()) { pdfPage++; renderPdfPage(); }});
        nav.addView(prev, new LinearLayout.LayoutParams(dp(58), dp(48))); nav.addView(pdfPageLabel, new LinearLayout.LayoutParams(0, dp(48), 1f)); nav.addView(next, new LinearLayout.LayoutParams(dp(58), dp(48)));
        content.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        HorizontalScrollView hsv = new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout actions = new LinearLayout(this);
        actions.addView(action("Редактировать", () -> ViewerIntents.start(this, PdfVisualEditorActivity.class, "pdf_uri", uri)));
        actions.addView(action("Конструктор страниц", () -> ViewerIntents.start(this, PdfPageOrganizerActivity.class, "pdf_uri", uri)));
        actions.addView(action("Подписать", () -> ViewerIntents.start(this, SignatureFlowActivity.class, "pdf_uri", uri)));
        hsv.addView(actions); content.addView(hsv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        renderPdfPage();
    }

    private void renderPdfPage() {
        if (pdfRenderer == null || pdfDoc == null) return;
        int page = pdfPage;
        pdfPageLabel.setText("Страница " + (page + 1) + " / " + pdfDoc.getNumberOfPages());
        worker.submit(() -> {
            try {
                Bitmap b = pdfRenderer.renderImageWithDPI(page, 120, ImageType.RGB);
                runOnUiThread(() -> {
                    if (page != pdfPage || isFinishing()) { b.recycle(); return; }
                    Bitmap old = pdfImage.getDrawable() instanceof android.graphics.drawable.BitmapDrawable bd ? bd.getBitmap() : null;
                    pdfImage.setImageBitmap(b);
                    if (old != null && old != b && !old.isRecycled()) old.recycle();
                });
            } catch (Exception e) { runOnUiThread(() -> error(e)); }
        });
    }

    private void loadImage() {
        showLoading("Открываю изображение…");
        worker.submit(() -> {
            try {
                Bitmap b = ImageTools.loadScaled(this, uri, 3600);
                runOnUiThread(() -> {
                    content.removeAllViews(); status.setText("Изображение • " + b.getWidth() + "×" + b.getHeight());
                    ImageView iv = new ImageView(this); iv.setAdjustViewBounds(true); iv.setScaleType(ImageView.ScaleType.FIT_CENTER); iv.setImageBitmap(b); iv.setBackgroundColor(Color.rgb(233,235,240));
                    content.addView(iv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    HorizontalScrollView hsv = new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false); LinearLayout a = new LinearLayout(this);
                    a.addView(action("Оптимизация", () -> ViewerIntents.start(this, ImageCompressionActivity.class, "image_uri", uri)));
                    a.addView(action("Размер и пропорции", () -> ViewerIntents.start(this, ImageResizeActivity.class, "image_uri", uri)));
                    a.addView(action("Кадрирование", () -> ViewerIntents.start(this, ImageCropActivity.class, "image_uri", uri)));
                    a.addView(action("Удалить фон", () -> ViewerIntents.start(this, BackgroundRemovalActivity.class, "image_uri", uri)));
                    hsv.addView(a); content.addView(hsv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
                });
            } catch (Exception e) { runOnUiThread(() -> error(e)); }
        });
    }

    private void loadDocumentText() {
        showLoading("Читаю документ…");
        worker.submit(() -> {
            try {
                String value = EditableDocumentTools.read(this, uri);
                runOnUiThread(() -> showTextPreview(value, "Документ", () -> ViewerIntents.start(this, DocumentEditorActivity.class, "document_uri", uri)));
            } catch (Exception e) { runOnUiThread(() -> error(e)); }
        });
    }

    private void loadTable() {
        showLoading("Читаю таблицу…");
        worker.submit(() -> {
            try {
                String value = EditableTableTools.readAsTsv(this, uri);
                runOnUiThread(() -> showTextPreview(value, "Таблица", () -> ViewerIntents.start(this, TableEditorActivity.class, "table_uri", uri)));
            } catch (Exception e) { runOnUiThread(() -> error(e)); }
        });
    }

    private void loadPresentation() {
        showLoading("Читаю презентацию…");
        worker.submit(() -> {
            try {
                String value = PresentationTools.extractPptxText(this, uri);
                runOnUiThread(() -> showTextPreview(value, "Презентация", () -> ViewerIntents.start(this, PptxSlideOrganizerActivity.class, "pptx_uri", uri)));
            } catch (Exception e) { runOnUiThread(() -> error(e)); }
        });
    }

    private void loadArchive() {
        showLoading("Читаю архив…");
        worker.submit(() -> {
            try {
                String value = ArchiveTools.listContents(this, uri);
                Runnable edit = name.toLowerCase(Locale.ROOT).endsWith(".zip") ? () -> ViewerIntents.start(this, ZipEditorActivity.class, "zip_uri", uri) : null;
                runOnUiThread(() -> showTextPreview(value, "Архив", edit));
            } catch (Exception e) { runOnUiThread(() -> error(e)); }
        });
    }

    private void showTextPreview(String value, String kind, Runnable edit) {
        content.removeAllViews(); status.setText(kind + " • просмотр в Доки");
        TextView tv = text(value == null || value.isBlank() ? "Нет отображаемого содержимого" : value, 15, Color.rgb(25,28,36), false);
        tv.setTextIsSelectable(true); tv.setPadding(dp(12), dp(12), dp(12), dp(16)); tv.setBackgroundColor(Color.WHITE);
        content.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (edit != null) { Button b = action(kind.equals("Презентация") ? "Конструктор слайдов" : "Редактировать", edit); content.addView(b); }
    }

    private Button action(String label, Runnable run) { Button b = button(label); b.setAllCaps(false); b.setOnClickListener(v -> run.run()); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(50)); lp.setMargins(dp(3), dp(4), dp(3), dp(4)); b.setLayoutParams(lp); return b; }
    private Button button(String label) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); return b; }
    private TextView text(String s, int sp, int color, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v; }
    private void error(Exception e) { new AlertDialog.Builder(this).setTitle("Не удалось открыть файл").setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()).setPositiveButton("Закрыть", (d,w) -> finish()).show(); }
    private boolean isImageName(String n) { return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".gif") || n.endsWith(".heic") || n.endsWith(".avif") || n.endsWith(".bmp"); }
    private String mimeFromName(String n) { String x=n.toLowerCase(Locale.ROOT); if(x.endsWith(".pdf"))return"application/pdf"; if(isImageName(x))return"image/*"; if(x.endsWith(".docx"))return"application/vnd.openxmlformats-officedocument.wordprocessingml.document"; if(x.endsWith(".xlsx"))return"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; if(x.endsWith(".pptx"))return"application/vnd.openxmlformats-officedocument.presentationml.presentation"; return"*/*"; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
        try { if (pdfDoc != null) pdfDoc.close(); } catch (Exception ignored) {}
        if (pdfTemp != null) pdfTemp.delete();
        if (pdfImage != null && pdfImage.getDrawable() instanceof android.graphics.drawable.BitmapDrawable bd) { Bitmap b = bd.getBitmap(); if (b != null && !b.isRecycled()) b.recycle(); }
    }
}
