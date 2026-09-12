package ru.filemaster.offline;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.ImageType;
import com.tom_roush.pdfbox.rendering.PDFRenderer;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PdfPagePreviewActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ZoomImageView image;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        PDFBoxResourceLoader.init(getApplicationContext());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        String raw = getIntent().getStringExtra("pdf_uri");
        int index = getIntent().getIntExtra("page_index", -1);
        if (raw == null || raw.isBlank() || index < 0) { finish(); return; }
        build(index + 1);
        load(Uri.parse(raw), index);
    }

    private void build(int pageNumber) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(28, 30, 36));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, i) -> {
            Insets s = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return i;
        });
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), dp(4), dp(10), dp(4));
        TextView back = text("←", 30, true); back.setGravity(Gravity.CENTER); back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text("Страница " + pageNumber, 20, true));
        labels.addView(text("Разведите пальцы для увеличения • двигайте увеличенную страницу", 12, false));
        top.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top);
        image = new ZoomImageView(this);
        root.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void load(Uri uri, int page) {
        ProgressDialog d = ProgressDialog.show(this, "Доки", "Открываю страницу…", true, false);
        worker.submit(() -> {
            File temp = null;
            try {
                temp = FileStore.copyUriToTemp(this, uri, ".pdf");
                try (PDDocument doc = PDDocument.load(temp)) {
                    if (page >= doc.getNumberOfPages()) throw new IllegalArgumentException("Страница не найдена");
                    Bitmap bitmap = new PDFRenderer(doc).renderImageWithDPI(page, 180, ImageType.RGB);
                    runOnUiThread(() -> { d.dismiss(); image.setBitmap(bitmap); });
                }
            } catch (Exception e) {
                runOnUiThread(() -> { d.dismiss(); android.widget.Toast.makeText(this, e.getMessage() == null ? "Не удалось открыть страницу" : e.getMessage(), android.widget.Toast.LENGTH_LONG).show(); finish(); });
            } finally { if (temp != null) temp.delete(); }
        });
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(bold ? Color.WHITE : Color.rgb(190, 194, 205));
        if (bold) v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD); return v;
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() { super.onDestroy(); worker.shutdownNow(); if (image != null) image.release(); }

    static final class ZoomImageView extends AppCompatImageView {
        private final Matrix matrix = new Matrix();
        private final ScaleGestureDetector scaleDetector;
        private Bitmap bitmap;
        private float baseScale = 1f, scale = 1f, lastX, lastY;

        ZoomImageView(android.content.Context c) {
            super(c); setScaleType(ScaleType.MATRIX); setBackgroundColor(Color.rgb(28,30,36));
            scaleDetector = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector detector) {
                    if (bitmap == null) return false;
                    float next = Math.max(1f, Math.min(6f, scale * detector.getScaleFactor()));
                    float factor = next / scale; scale = next;
                    matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                    constrain(); setImageMatrix(matrix); return true;
                }
            });
        }

        void setBitmap(Bitmap b) { release(); bitmap = b; setImageBitmap(b); post(this::fit); }
        void release() { if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle(); bitmap = null; setImageDrawable(null); }
        private void fit() {
            if (bitmap == null || getWidth() == 0 || getHeight() == 0) return;
            matrix.reset();
            float sx = getWidth() / (float) bitmap.getWidth(), sy = getHeight() / (float) bitmap.getHeight();
            baseScale = Math.min(sx, sy); scale = 1f;
            float w = bitmap.getWidth() * baseScale, h = bitmap.getHeight() * baseScale;
            matrix.postScale(baseScale, baseScale);
            matrix.postTranslate((getWidth() - w) / 2f, (getHeight() - h) / 2f);
            setImageMatrix(matrix);
        }
        private void constrain() {
            if (bitmap == null) return;
            RectF r = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()); matrix.mapRect(r);
            float dx = 0, dy = 0;
            if (r.width() <= getWidth()) dx = getWidth()/2f - r.centerX(); else if (r.left > 0) dx = -r.left; else if (r.right < getWidth()) dx = getWidth() - r.right;
            if (r.height() <= getHeight()) dy = getHeight()/2f - r.centerY(); else if (r.top > 0) dy = -r.top; else if (r.bottom < getHeight()) dy = getHeight() - r.bottom;
            matrix.postTranslate(dx, dy);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            scaleDetector.onTouchEvent(e);
            if (bitmap == null) return false;
            if (!scaleDetector.isInProgress()) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { lastX=e.getX(); lastY=e.getY(); return true; }
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE && scale > 1f) {
                    float dx=e.getX()-lastX, dy=e.getY()-lastY; lastX=e.getX(); lastY=e.getY(); matrix.postTranslate(dx,dy); constrain(); setImageMatrix(matrix); return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_UP && scale <= 1.01f) fit();
            }
            return true;
        }
    }
}
