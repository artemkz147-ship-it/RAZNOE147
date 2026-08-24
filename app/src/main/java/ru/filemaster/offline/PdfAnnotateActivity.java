package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.rendering.ImageType;
import com.tom_roush.pdfbox.rendering.PDFRenderer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfAnnotateActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Uri pdfUri;
    private int pageNumber;
    private DrawView drawView;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PDFBoxResourceLoader.init(getApplicationContext());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        String raw = getIntent().getStringExtra("pdf_uri");
        pageNumber = getIntent().getIntExtra("page", 1);
        if (raw == null || raw.isBlank()) {
            finish();
            return;
        }
        pdfUri = Uri.parse(raw);
        buildUi();
        loadPreview();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(10), dp(12), dp(12));
        root.setBackgroundColor(Color.rgb(239, 241, 246));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(dp(12), safe.top + dp(8), dp(12), safe.bottom + dp(12));
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(30);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("Рисование по PDF");
        title.setTextSize(23);
        title.setTextColor(Color.rgb(25, 28, 36));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        titles.addView(title);
        status = new TextView(this);
        status.setText("Страница " + pageNumber + " • загружаю…");
        status.setTextSize(13);
        status.setTextColor(Color.rgb(93, 99, 112));
        titles.addView(status);
        header.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header);

        TextView notice = new TextView(this);
        notice.setText("Ручка и маркер работают пальцем. Изменённая страница при сохранении сводится в изображение, остальные страницы остаются как были.");
        notice.setTextSize(12);
        notice.setTextColor(Color.rgb(93, 99, 112));
        notice.setPadding(dp(8), dp(4), dp(8), dp(8));
        root.addView(notice);

        drawView = new DrawView(this);
        LinearLayout.LayoutParams drawLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(drawView, drawLp);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER);
        tools.setPadding(0, dp(8), 0, 0);

        Button pen = button("Ручка");
        pen.setOnClickListener(v -> drawView.setMode(DrawView.MODE_PEN));
        tools.addView(pen, weighted());
        Button highlighter = button("Маркер");
        highlighter.setOnClickListener(v -> drawView.setMode(DrawView.MODE_HIGHLIGHT));
        tools.addView(highlighter, weighted());
        Button undo = button("Отмена");
        undo.setOnClickListener(v -> drawView.undo());
        tools.addView(undo, weighted());
        Button clear = button("Очистить");
        clear.setOnClickListener(v -> drawView.clear());
        tools.addView(clear, weighted());
        root.addView(tools);

        Button save = button("Сохранить новый PDF");
        save.setTextSize(16);
        save.setOnClickListener(v -> savePdf());
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        saveLp.setMargins(0, dp(8), 0, 0);
        root.addView(save, saveLp);

        setContentView(root);
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private void loadPreview() {
        worker.submit(() -> {
            File input = null;
            try {
                input = FileStore.copyUriToTemp(this, pdfUri, ".pdf");
                try (PDDocument doc = PDDocument.load(input)) {
                    if (pageNumber < 1 || pageNumber > doc.getNumberOfPages()) {
                        throw new IllegalArgumentException("В PDF всего страниц: " + doc.getNumberOfPages());
                    }
                    PDFRenderer renderer = new PDFRenderer(doc);
                    Bitmap preview = renderer.renderImageWithDPI(pageNumber - 1, 110, ImageType.RGB);
                    int total = doc.getNumberOfPages();
                    runOnUiThread(() -> {
                        drawView.setPageBitmap(preview);
                        status.setText("Страница " + pageNumber + " из " + total + " • режим: ручка");
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> showErrorAndFinish(e));
            } finally {
                if (input != null) input.delete();
            }
        });
    }

    private void savePdf() {
        List<Stroke> strokes = drawView.snapshot();
        if (strokes.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Нет изменений")
                    .setMessage("Сначала нарисуйте ручкой или маркером.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        ProgressDialog dialog = ProgressDialog.show(this, "ФайлМастер", "Сохраняю изменённую страницу…", true, false);
        worker.submit(() -> {
            File input = null;
            File out = null;
            try {
                input = FileStore.copyUriToTemp(this, pdfUri, ".pdf");
                out = File.createTempFile("annotated_", ".pdf", getCacheDir());
                try (PDDocument src = PDDocument.load(input); PDDocument dst = new PDDocument()) {
                    int index = pageNumber - 1;
                    if (index < 0 || index >= src.getNumberOfPages()) throw new IllegalArgumentException("Страница не найдена");
                    PDFRenderer renderer = new PDFRenderer(src);
                    Bitmap rendered = renderer.renderImageWithDPI(index, 150, ImageType.RGB);
                    Bitmap annotated = rendered.copy(Bitmap.Config.ARGB_8888, true);
                    rendered.recycle();
                    drawStrokes(annotated, strokes);
                    try {
                        for (int i = 0; i < src.getNumberOfPages(); i++) {
                            if (i != index) {
                                dst.importPage(src.getPage(i));
                                continue;
                            }
                            PDPage original = src.getPage(i);
                            PDRectangle crop = original.getCropBox();
                            float w = crop.getWidth();
                            float h = crop.getHeight();
                            int rotation = ((original.getRotation() % 360) + 360) % 360;
                            if (rotation == 90 || rotation == 270) {
                                float t = w; w = h; h = t;
                            }
                            PDPage page = new PDPage(new PDRectangle(w, h));
                            dst.addPage(page);
                            PDImageXObject image = JPEGFactory.createFromImage(dst, annotated, 0.94f);
                            try (PDPageContentStream cs = new PDPageContentStream(dst, page)) {
                                cs.drawImage(image, 0, 0, w, h);
                            }
                        }
                        dst.save(out);
                    } finally { annotated.recycle(); }
                }
                Uri result = FileStore.publishFile(this, out, "Разметка_PDF_" + System.currentTimeMillis() + ".pdf", "application/pdf", null);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    new AlertDialog.Builder(this).setTitle("Готово")
                            .setMessage("Новый PDF с разметкой сохранён.")
                            .setPositiveButton("Открыть", (d, w) -> openResult(result))
                            .setNeutralButton("Поделиться", (d, w) -> shareResult(result))
                            .setNegativeButton("Закрыть", (d, w) -> finish())
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { dialog.dismiss(); showError(e); });
            } finally {
                if (input != null) input.delete();
                if (out != null) out.delete();
            }
        });
    }

    private static void drawStrokes(Bitmap bitmap, List<Stroke> strokes) {
        Canvas canvas = new Canvas(bitmap);
        float min = Math.min(bitmap.getWidth(), bitmap.getHeight());
        for (Stroke stroke : strokes) {
            if (stroke.points.isEmpty()) continue;
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            if (stroke.mode == DrawView.MODE_HIGHLIGHT) {
                paint.setColor(Color.argb(100, 255, 215, 0));
                paint.setStrokeWidth(min * 0.028f);
            } else {
                paint.setColor(Color.rgb(220, 30, 45));
                paint.setStrokeWidth(min * 0.0055f);
            }
            Path path = new Path();
            PointF first = stroke.points.get(0);
            path.moveTo(first.x * bitmap.getWidth(), first.y * bitmap.getHeight());
            for (int i = 1; i < stroke.points.size(); i++) {
                PointF p = stroke.points.get(i);
                path.lineTo(p.x * bitmap.getWidth(), p.y * bitmap.getHeight());
            }
            canvas.drawPath(path, paint);
        }
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

    private void showErrorAndFinish(Exception e) {
        new AlertDialog.Builder(this).setTitle("Не удалось открыть PDF")
                .setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                .setPositiveButton("Закрыть", (d, w) -> finish()).show();
    }

    private void showError(Exception e) {
        new AlertDialog.Builder(this).setTitle("Не получилось")
                .setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                .setPositiveButton("OK", null).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        drawView.releaseBitmap();
        if (isFinishing()) worker.shutdownNow();
    }

    static final class Stroke {
        final int mode;
        final List<PointF> points = new ArrayList<>();
        Stroke(int mode) { this.mode = mode; }
        Stroke copy() {
            Stroke c = new Stroke(mode);
            for (PointF p : points) c.points.add(new PointF(p.x, p.y));
            return c;
        }
    }

    static final class DrawView extends View {
        static final int MODE_PEN = 1;
        static final int MODE_HIGHLIGHT = 2;

        private final List<Stroke> strokes = new ArrayList<>();
        private final RectF imageRect = new RectF();
        private Bitmap pageBitmap;
        private Stroke active;
        private int mode = MODE_PEN;

        DrawView(android.content.Context context) {
            super(context);
            setBackgroundColor(Color.rgb(225, 228, 235));
        }

        void setPageBitmap(Bitmap bitmap) {
            releaseBitmap();
            pageBitmap = bitmap;
            invalidate();
        }

        void setMode(int value) {
            mode = value;
            invalidate();
        }

        void undo() {
            if (!strokes.isEmpty()) strokes.remove(strokes.size() - 1);
            invalidate();
        }

        void clear() {
            strokes.clear();
            invalidate();
        }

        List<Stroke> snapshot() {
            List<Stroke> copy = new ArrayList<>();
            for (Stroke s : strokes) copy.add(s.copy());
            return copy;
        }

        void releaseBitmap() {
            if (pageBitmap != null && !pageBitmap.isRecycled()) pageBitmap.recycle();
            pageBitmap = null;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (pageBitmap == null || pageBitmap.isRecycled()) return;
            calculateImageRect();
            Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(pageBitmap, null, imageRect, bitmapPaint);
            for (Stroke stroke : strokes) drawStroke(canvas, stroke);
            if (active != null && !strokes.contains(active)) drawStroke(canvas, active);
        }

        private void drawStroke(Canvas canvas, Stroke stroke) {
            if (stroke.points.isEmpty()) return;
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            if (stroke.mode == MODE_HIGHLIGHT) {
                paint.setColor(Color.argb(100, 255, 215, 0));
                paint.setStrokeWidth(Math.min(imageRect.width(), imageRect.height()) * 0.028f);
            } else {
                paint.setColor(Color.rgb(220, 30, 45));
                paint.setStrokeWidth(Math.min(imageRect.width(), imageRect.height()) * 0.0055f);
            }
            Path path = new Path();
            PointF first = stroke.points.get(0);
            path.moveTo(imageRect.left + first.x * imageRect.width(), imageRect.top + first.y * imageRect.height());
            for (int i = 1; i < stroke.points.size(); i++) {
                PointF p = stroke.points.get(i);
                path.lineTo(imageRect.left + p.x * imageRect.width(), imageRect.top + p.y * imageRect.height());
            }
            canvas.drawPath(path, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (pageBitmap == null || pageBitmap.isRecycled()) return false;
            calculateImageRect();
            float x = event.getX();
            float y = event.getY();
            boolean inside = imageRect.contains(x, y);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    if (!inside) return false;
                    active = new Stroke(mode);
                    active.points.add(normalize(x, y));
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_MOVE -> {
                    if (active == null) return false;
                    float cx = Math.max(imageRect.left, Math.min(imageRect.right, x));
                    float cy = Math.max(imageRect.top, Math.min(imageRect.bottom, y));
                    active.points.add(normalize(cx, cy));
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (active == null) return false;
                    if (active.points.size() == 1) active.points.add(new PointF(active.points.get(0).x + 0.001f, active.points.get(0).y));
                    strokes.add(active);
                    active = null;
                    invalidate();
                    return true;
                }
            }
            return super.onTouchEvent(event);
        }

        private PointF normalize(float x, float y) {
            return new PointF((x - imageRect.left) / imageRect.width(), (y - imageRect.top) / imageRect.height());
        }

        private void calculateImageRect() {
            if (pageBitmap == null || getWidth() <= 0 || getHeight() <= 0) return;
            float viewW = getWidth();
            float viewH = getHeight();
            float scale = Math.min(viewW / pageBitmap.getWidth(), viewH / pageBitmap.getHeight());
            float w = pageBitmap.getWidth() * scale;
            float h = pageBitmap.getHeight() * scale;
            float left = (viewW - w) / 2f;
            float top = (viewH - h) / 2f;
            imageRect.set(left, top, left + w, top + h);
        }
    }
}
