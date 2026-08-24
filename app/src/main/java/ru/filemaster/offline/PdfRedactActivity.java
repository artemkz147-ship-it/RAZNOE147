package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
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

public class PdfRedactActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Uri pdfUri;
    private int pageNumber;
    private RedactView redactView;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PDFBoxResourceLoader.init(getApplicationContext());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        String raw = getIntent().getStringExtra("pdf_uri");
        pageNumber = getIntent().getIntExtra("page", 1);
        if (raw == null || raw.isBlank()) { finish(); return; }
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
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(30);
        back.setGravity(android.view.Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(50), dp(50)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("Скрыть данные в PDF");
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
        notice.setText("Проведите пальцем по области, которую нужно уничтожить. При сохранении выбранная страница полностью пересобирается как изображение с чёрными областями — исходный текст и объекты под ними в новом PDF не сохраняются.");
        notice.setTextSize(12);
        notice.setTextColor(Color.rgb(120, 73, 0));
        notice.setBackgroundColor(Color.rgb(255, 246, 225));
        notice.setPadding(dp(10), dp(9), dp(10), dp(9));
        root.addView(notice);

        redactView = new RedactView(this);
        LinearLayout.LayoutParams viewLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        viewLp.setMargins(0, dp(8), 0, 0);
        root.addView(redactView, viewLp);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        Button undo = button("Отменить область");
        undo.setOnClickListener(v -> redactView.undo());
        tools.addView(undo, weighted());
        Button clear = button("Очистить всё");
        clear.setOnClickListener(v -> redactView.clear());
        tools.addView(clear, weighted());
        root.addView(tools);

        Button save = button("Применить скрытие и сохранить");
        save.setOnClickListener(v -> saveRedacted());
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
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
        lp.setMargins(dp(2), dp(8), dp(2), 0);
        return lp;
    }

    private void loadPreview() {
        worker.submit(() -> {
            File input = null;
            try {
                input = FileStore.copyUriToTemp(this, pdfUri, ".pdf");
                try (PDDocument doc = PDDocument.load(input)) {
                    if (pageNumber < 1 || pageNumber > doc.getNumberOfPages()) throw new IllegalArgumentException("В PDF всего страниц: " + doc.getNumberOfPages());
                    Bitmap preview = new PDFRenderer(doc).renderImageWithDPI(pageNumber - 1, 110, ImageType.RGB);
                    int total = doc.getNumberOfPages();
                    runOnUiThread(() -> {
                        redactView.setPageBitmap(preview);
                        status.setText("Страница " + pageNumber + " из " + total + " • областей: 0");
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> showErrorAndFinish(e));
            } finally { if (input != null) input.delete(); }
        });
    }

    private void saveRedacted() {
        List<RectF> areas = redactView.snapshot();
        if (areas.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Нет областей")
                    .setMessage("Выделите хотя бы одну область, которую нужно скрыть.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Применить безвозвратно?")
                .setMessage("В новом файле выбранная страница будет сведена в изображение. Содержимое под чёрными областями будет уничтожено.")
                .setPositiveButton("Применить", (d, w) -> doSave(areas))
                .setNegativeButton("Отмена", null).show();
    }

    private void doSave(List<RectF> areas) {
        ProgressDialog dialog = ProgressDialog.show(this, "ФайлМастер", "Удаляю выбранные области…", true, false);
        worker.submit(() -> {
            File input = null;
            File out = null;
            try {
                input = FileStore.copyUriToTemp(this, pdfUri, ".pdf");
                out = File.createTempFile("redacted_", ".pdf", getCacheDir());
                try (PDDocument src = PDDocument.load(input); PDDocument dst = new PDDocument()) {
                    int index = pageNumber - 1;
                    if (index < 0 || index >= src.getNumberOfPages()) throw new IllegalArgumentException("Страница не найдена");
                    Bitmap rendered = new PDFRenderer(src).renderImageWithDPI(index, 170, ImageType.RGB);
                    Bitmap redacted = rendered.copy(Bitmap.Config.ARGB_8888, true);
                    rendered.recycle();
                    Canvas canvas = new Canvas(redacted);
                    Paint black = new Paint(Paint.ANTI_ALIAS_FLAG);
                    black.setColor(Color.BLACK);
                    black.setStyle(Paint.Style.FILL);
                    for (RectF n : areas) {
                        canvas.drawRect(n.left * redacted.getWidth(), n.top * redacted.getHeight(),
                                n.right * redacted.getWidth(), n.bottom * redacted.getHeight(), black);
                    }
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
                            if (rotation == 90 || rotation == 270) { float t = w; w = h; h = t; }
                            PDPage page = new PDPage(new PDRectangle(w, h));
                            dst.addPage(page);
                            PDImageXObject image = JPEGFactory.createFromImage(dst, redacted, 0.95f);
                            try (PDPageContentStream cs = new PDPageContentStream(dst, page)) {
                                cs.drawImage(image, 0, 0, w, h);
                            }
                        }
                        dst.save(out);
                    } finally { redacted.recycle(); }
                }
                Uri result = FileStore.publishFile(this, out, "Скрытые_данные_" + System.currentTimeMillis() + ".pdf", "application/pdf", null);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    new AlertDialog.Builder(this).setTitle("Готово")
                            .setMessage("Создан новый PDF. Данные под выбранными областями не сохранены в изменённой странице.")
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

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        redactView.releaseBitmap();
        if (isFinishing()) worker.shutdownNow();
    }

    final class RedactView extends View {
        private final List<RectF> areas = new ArrayList<>();
        private final RectF imageRect = new RectF();
        private Bitmap pageBitmap;
        private float downX;
        private float downY;
        private RectF active;

        RedactView(android.content.Context context) {
            super(context);
            setBackgroundColor(Color.rgb(225, 228, 235));
        }

        void setPageBitmap(Bitmap bitmap) {
            releaseBitmap();
            pageBitmap = bitmap;
            invalidate();
        }

        void undo() {
            if (!areas.isEmpty()) areas.remove(areas.size() - 1);
            updateStatus();
            invalidate();
        }

        void clear() {
            areas.clear();
            active = null;
            updateStatus();
            invalidate();
        }

        List<RectF> snapshot() {
            List<RectF> copy = new ArrayList<>();
            for (RectF r : areas) copy.add(new RectF(r));
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
            calcRect();
            Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(pageBitmap, null, imageRect, bmpPaint);
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(Color.argb(215, 0, 0, 0));
            for (RectF r : areas) canvas.drawRect(toView(r), fill);
            if (active != null) {
                Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                activePaint.setColor(Color.argb(150, 0, 0, 0));
                canvas.drawRect(active, activePaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (pageBitmap == null || pageBitmap.isRecycled()) return false;
            calcRect();
            float x = Math.max(imageRect.left, Math.min(imageRect.right, event.getX()));
            float y = Math.max(imageRect.top, Math.min(imageRect.bottom, event.getY()));
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    if (!imageRect.contains(event.getX(), event.getY())) return false;
                    downX = x;
                    downY = y;
                    active = new RectF(x, y, x, y);
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_MOVE -> {
                    if (active == null) return false;
                    active.set(Math.min(downX, x), Math.min(downY, y), Math.max(downX, x), Math.max(downY, y));
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP -> {
                    if (active == null) return false;
                    if (active.width() >= dp(8) && active.height() >= dp(8)) areas.add(toNormalized(active));
                    active = null;
                    updateStatus();
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL -> {
                    active = null;
                    invalidate();
                    return true;
                }
            }
            return super.onTouchEvent(event);
        }

        private void updateStatus() {
            status.setText("Страница " + pageNumber + " • областей: " + areas.size());
        }

        private RectF toNormalized(RectF r) {
            return new RectF(
                    (r.left - imageRect.left) / imageRect.width(),
                    (r.top - imageRect.top) / imageRect.height(),
                    (r.right - imageRect.left) / imageRect.width(),
                    (r.bottom - imageRect.top) / imageRect.height());
        }

        private RectF toView(RectF n) {
            return new RectF(
                    imageRect.left + n.left * imageRect.width(),
                    imageRect.top + n.top * imageRect.height(),
                    imageRect.left + n.right * imageRect.width(),
                    imageRect.top + n.bottom * imageRect.height());
        }

        private void calcRect() {
            if (pageBitmap == null || getWidth() <= 0 || getHeight() <= 0) return;
            float scale = Math.min(getWidth() / (float) pageBitmap.getWidth(), getHeight() / (float) pageBitmap.getHeight());
            float w = pageBitmap.getWidth() * scale;
            float h = pageBitmap.getHeight() * scale;
            float left = (getWidth() - w) / 2f;
            float top = (getHeight() - h) / 2f;
            imageRect.set(left, top, left + w, top + h);
        }
    }
}
