package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
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

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ManualCropActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Uri inputUri;
    private CropView cropView;
    private boolean documentMode = true;
    private Button modeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        String raw = getIntent().getStringExtra("image_uri");
        if (raw == null || raw.isBlank()) { finish(); return; }
        inputUri = Uri.parse(raw);
        buildUi();
        loadPreview();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(238, 240, 245));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(dp(12), safe.top + dp(8), dp(12), safe.bottom + dp(12));
            return insets;
        });

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView cancel = text("×", 30, Color.rgb(25, 28, 36), false);
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(v -> finish());
        top.addView(cancel, new LinearLayout.LayoutParams(dp(50), dp(50)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Выровнять страницу", 23, Color.rgb(25, 28, 36), true));
        titles.addView(text("Перетащите 4 точки к углам листа", 13, Color.rgb(93, 99, 112), false));
        top.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(top);

        cropView = new CropView(this);
        root.addView(cropView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(8), 0, 0);
        Button reset = button("Сбросить углы");
        reset.setOnClickListener(v -> cropView.resetPoints());
        controls.addView(reset, weighted());
        modeButton = button("Режим: документ Ч/Б");
        modeButton.setOnClickListener(v -> {
            documentMode = !documentMode;
            modeButton.setText(documentMode ? "Режим: документ Ч/Б" : "Режим: цвет");
        });
        controls.addView(modeButton, weighted());
        root.addView(controls);

        Button save = button("Исправить перспективу");
        save.setTextSize(16);
        save.setOnClickListener(v -> saveCrop());
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        saveLp.setMargins(0, dp(8), 0, 0);
        root.addView(save, saveLp);
        setContentView(root);
    }

    private void loadPreview() {
        worker.submit(() -> {
            try {
                Bitmap bmp = ImageTools.loadScaled(this, inputUri, 1800);
                runOnUiThread(() -> cropView.setBitmap(bmp));
            } catch (Exception e) { runOnUiThread(() -> showErrorAndFinish(e)); }
        });
    }

    private void saveCrop() {
        float[] normalized = cropView.normalizedPoints();
        if (normalized == null) { showError(new IllegalStateException("Изображение ещё загружается")); return; }
        boolean docMode = documentMode;
        ProgressDialog dialog = ProgressDialog.show(this, "Скан", "Исправляю перспективу…", true, false);
        worker.submit(() -> {
            Bitmap source = null;
            Bitmap output = null;
            File temp = null;
            try {
                source = ImageTools.loadScaled(this, inputUri, 5000);
                float[] src = new float[8];
                for (int i = 0; i < 4; i++) {
                    src[i * 2] = normalized[i * 2] * source.getWidth();
                    src[i * 2 + 1] = normalized[i * 2 + 1] * source.getHeight();
                }
                int outW = Math.max(320, Math.round(Math.max(distance(src, 0, 1), distance(src, 3, 2))));
                int outH = Math.max(320, Math.round(Math.max(distance(src, 0, 3), distance(src, 1, 2))));
                float max = 5000f;
                float scale = Math.min(1f, max / Math.max(outW, outH));
                outW = Math.max(320, Math.round(outW * scale));
                outH = Math.max(320, Math.round(outH * scale));
                float[] dst = {0, 0, outW, 0, outW, outH, 0, outH};
                Matrix matrix = new Matrix();
                if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) throw new IllegalArgumentException("Не удалось рассчитать перспективу — проверьте положение углов");
                output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(output);
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(source, matrix, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
                if (docMode) applyDocumentFilter(output);
                temp = File.createTempFile("perspective_", ".jpg", getCacheDir());
                try (FileOutputStream fos = new FileOutputStream(temp)) {
                    if (!output.compress(Bitmap.CompressFormat.JPEG, 94, fos)) throw new IllegalStateException("Не удалось сохранить исправленный скан");
                }
                Uri result = FileStore.publishFile(this, temp, "Скан_углы_" + System.currentTimeMillis() + ".jpg", "image/jpeg", "Сканы");
                Intent data = new Intent();
                data.putExtra("cropped_uri", result.toString());
                runOnUiThread(() -> {
                    dialog.dismiss();
                    setResult(RESULT_OK, data);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { dialog.dismiss(); showError(e); });
            } finally {
                if (source != null && !source.isRecycled()) source.recycle();
                if (output != null && !output.isRecycled()) output.recycle();
                if (temp != null) temp.delete();
            }
        });
    }

    private static float distance(float[] p, int a, int b) {
        float dx = p[a * 2] - p[b * 2], dy = p[a * 2 + 1] - p[b * 2 + 1];
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static void applyDocumentFilter(Bitmap bitmap) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int[] px = new int[w * h];
        bitmap.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000;
            lum = Math.max(0, Math.min(255, Math.round((lum - 128f) * 1.30f + 128f)));
            if (lum > 238) lum = 255; else if (lum < 38) lum = 0;
            px[i] = Color.rgb(lum, lum, lum);
        }
        bitmap.setPixels(px, 0, w, 0, 0, w, h);
    }

    private Button button(String label) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); return b;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0); return lp;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color);
        if (bold) v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD); return v;
    }

    private void showErrorAndFinish(Exception e) {
        new AlertDialog.Builder(this).setTitle("Не удалось открыть фото").setMessage(message(e))
                .setPositiveButton("Закрыть", (d, w) -> finish()).show();
    }
    private void showError(Exception e) { new AlertDialog.Builder(this).setTitle("Не получилось").setMessage(message(e)).setPositiveButton("OK", null).show(); }
    private String message(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (cropView != null) cropView.release();
        if (isFinishing()) worker.shutdownNow();
    }

    static final class CropView extends View {
        private final RectF imageRect = new RectF();
        private final PointF[] points = {new PointF(), new PointF(), new PointF(), new PointF()};
        private Bitmap bitmap;
        private int active = -1;

        CropView(android.content.Context ctx) { super(ctx); setBackgroundColor(Color.rgb(30, 32, 38)); }

        void setBitmap(Bitmap b) { release(); bitmap = b; resetPoints(); }
        void release() { if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle(); bitmap = null; }
        void resetPoints() {
            points[0].set(.04f, .04f); points[1].set(.96f, .04f); points[2].set(.96f, .96f); points[3].set(.04f, .96f); invalidate();
        }
        float[] normalizedPoints() {
            if (bitmap == null) return null;
            float[] out = new float[8];
            for (int i = 0; i < 4; i++) { out[i * 2] = points[i].x; out[i * 2 + 1] = points[i].y; }
            return out;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null || bitmap.isRecycled()) return;
            float s = Math.min(getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
            float w = bitmap.getWidth() * s, h = bitmap.getHeight() * s;
            imageRect.set((getWidth() - w) / 2f, (getHeight() - h) / 2f, (getWidth() + w) / 2f, (getHeight() + h) / 2f);
            canvas.drawBitmap(bitmap, null, imageRect, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
            Path path = new Path();
            PointF p0 = screenPoint(0); path.moveTo(p0.x, p0.y);
            for (int i = 1; i < 4; i++) { PointF p = screenPoint(i); path.lineTo(p.x, p.y); }
            path.close();
            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG); line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(Math.max(3f, getResources().getDisplayMetrics().density * 2f)); line.setColor(Color.rgb(70, 225, 150));
            canvas.drawPath(path, line);
            Paint handle = new Paint(Paint.ANTI_ALIAS_FLAG); handle.setColor(Color.WHITE); handle.setStyle(Paint.Style.FILL);
            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG); ring.setColor(Color.rgb(49, 87, 213)); ring.setStyle(Paint.Style.STROKE); ring.setStrokeWidth(5f);
            float r = 13f * getResources().getDisplayMetrics().density;
            for (int i = 0; i < 4; i++) { PointF p = screenPoint(i); canvas.drawCircle(p.x, p.y, r, handle); canvas.drawCircle(p.x, p.y, r, ring); }
        }

        private PointF screenPoint(int i) { return new PointF(imageRect.left + points[i].x * imageRect.width(), imageRect.top + points[i].y * imageRect.height()); }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (bitmap == null) return false;
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                float best = Float.MAX_VALUE; int found = -1;
                for (int i = 0; i < 4; i++) { PointF p = screenPoint(i); float dx = p.x - e.getX(), dy = p.y - e.getY(); float d = dx * dx + dy * dy; if (d < best) { best = d; found = i; } }
                active = found; updatePoint(e.getX(), e.getY()); return true;
            }
            if (e.getAction() == MotionEvent.ACTION_MOVE) { updatePoint(e.getX(), e.getY()); return true; }
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) { updatePoint(e.getX(), e.getY()); active = -1; return true; }
            return true;
        }

        private void updatePoint(float x, float y) {
            if (active < 0 || imageRect.width() <= 0 || imageRect.height() <= 0) return;
            float nx = Math.max(0f, Math.min(1f, (x - imageRect.left) / imageRect.width()));
            float ny = Math.max(0f, Math.min(1f, (y - imageRect.top) / imageRect.height()));
            points[active].set(nx, ny); invalidate();
        }
    }
}
