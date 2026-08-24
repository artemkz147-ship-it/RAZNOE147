package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanSessionActivity extends AppCompatActivity {
    private static final int CAMERA_PAGE = 6101;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Uri> pages = new ArrayList<>();
    private Uri cameraUri;
    private ImageView preview;
    private TextView counter;
    private Button finishButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(247, 248, 252));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(dp(18), safe.top + dp(12), dp(18), safe.bottom + dp(18));
            return insets;
        });

        TextView title = new TextView(this);
        title.setText("Многостраничный скан");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(25, 28, 36));
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Снимайте страницы по одной. Каждая страница автоматически улучшается, а в конце они собираются в один PDF.");
        hint.setTextSize(14);
        hint.setTextColor(Color.rgb(93, 99, 112));
        hint.setPadding(0, dp(5), 0, dp(12));
        root.addView(hint);

        counter = new TextView(this);
        counter.setTextSize(15);
        counter.setTextColor(Color.rgb(0, 135, 100));
        counter.setTypeface(counter.getTypeface(), android.graphics.Typeface.BOLD);
        counter.setPadding(0, 0, 0, dp(10));
        root.addView(counter);

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        previewLp.setMargins(0, 0, 0, dp(12));
        root.addView(preview, previewLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        Button add = new Button(this);
        add.setText("+ Страница");
        add.setOnClickListener(v -> capturePage());
        row.addView(add, weightedButton());

        Button remove = new Button(this);
        remove.setText("Удалить последнюю");
        remove.setOnClickListener(v -> removeLast());
        row.addView(remove, weightedButton());
        root.addView(row);

        finishButton = new Button(this);
        finishButton.setText("Готово — создать PDF");
        finishButton.setAllCaps(false);
        finishButton.setOnClickListener(v -> createPdf());
        LinearLayout.LayoutParams finishLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        finishLp.setMargins(0, dp(10), 0, 0);
        root.addView(finishButton, finishLp);

        updateUi();
        setContentView(root);
    }

    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private void capturePage() {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "Скан_страница_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ФайлМастер/Сканы");
            cameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (cameraUri == null) throw new IllegalStateException("Не удалось подготовить файл для камеры");
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (intent.resolveActivity(getPackageManager()) == null) throw new IllegalStateException("На устройстве не найдено приложение камеры");
            startActivityForResult(intent, CAMERA_PAGE);
        } catch (Exception e) {
            showError(e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CAMERA_PAGE) return;
        Uri raw = cameraUri;
        cameraUri = null;
        if (resultCode != RESULT_OK || raw == null) {
            if (raw != null) try { getContentResolver().delete(raw, null, null); } catch (Exception ignored) {}
            return;
        }
        ProgressDialog dialog = ProgressDialog.show(this, "Скан", "Улучшаю страницу…", true, false);
        worker.submit(() -> {
            try {
                Uri enhanced = ImageTools.enhanceDocument(this, raw);
                try { getContentResolver().delete(raw, null, null); } catch (Exception ignored) {}
                runOnUiThread(() -> {
                    dialog.dismiss();
                    pages.add(enhanced);
                    updateUi();
                });
            } catch (Exception e) {
                try { getContentResolver().delete(raw, null, null); } catch (Exception ignored) {}
                runOnUiThread(() -> { dialog.dismiss(); showError(e); });
            }
        });
    }

    private void removeLast() {
        if (pages.isEmpty()) {
            Toast.makeText(this, "Страниц пока нет", Toast.LENGTH_SHORT).show();
            return;
        }
        pages.remove(pages.size() - 1);
        updateUi();
    }

    private void updateUi() {
        counter.setText(pages.isEmpty() ? "Страниц пока нет" : "Страниц: " + pages.size());
        finishButton.setEnabled(!pages.isEmpty());
        if (pages.isEmpty()) {
            preview.setImageDrawable(null);
            preview.setContentDescription("Нет отсканированных страниц");
        } else {
            preview.setImageURI(pages.get(pages.size() - 1));
            preview.setContentDescription("Последняя отсканированная страница");
        }
    }

    private void createPdf() {
        if (pages.isEmpty()) return;
        List<Uri> snapshot = new ArrayList<>(pages);
        ProgressDialog dialog = ProgressDialog.show(this, "ФайлМастер", "Собираю страницы в PDF…", true, false);
        worker.submit(() -> {
            try {
                Uri pdf = PdfTools.imagesToPdf(this, snapshot);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    new AlertDialog.Builder(this)
                            .setTitle("Скан готов")
                            .setMessage("Создан PDF из " + snapshot.size() + " стр.")
                            .setPositiveButton("Открыть", (d, w) -> openPdf(pdf))
                            .setNeutralButton("Поделиться", (d, w) -> sharePdf(pdf))
                            .setNegativeButton("Закрыть", (d, w) -> finish())
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { dialog.dismiss(); showError(e); });
            }
        });
    }

    private void openPdf(Uri uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/pdf");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Открыть PDF"));
        } catch (Exception e) { showError(new IllegalStateException("PDF сохранён, но открыть его не удалось")); }
    }

    private void sharePdf(Uri uri) {
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) worker.shutdownNow();
    }
}
