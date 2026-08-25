package ru.filemaster.offline;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
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
    private static final int CROP_PAGE = 6102;
    private static final int PICK_GALLERY = 6103;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Uri> pages = new ArrayList<>();
    private Uri cameraUri;
    private Uri pendingRawUri;
    private ImageView preview;
    private TextView counter;
    private Button finishButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
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

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("←", 30, Color.rgb(25, 28, 36), false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("Многостраничный скан", 26, Color.rgb(25, 28, 36), true));
        titles.addView(text("Камера → 4 угла → один PDF", 13, Color.rgb(93, 99, 112), false));
        header.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(header);

        TextView hint = text("После каждого снимка можно вручную выставить четыре угла листа и исправить перспективу. Также можно добавить уже снятые фотографии.", 14, Color.rgb(93, 99, 112), false);
        hint.setPadding(0, dp(5), 0, dp(12));
        root.addView(hint);

        counter = text("", 15, Color.rgb(0, 135, 100), true);
        counter.setPadding(0, 0, 0, dp(10)); root.addView(counter);

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        previewLp.setMargins(0, 0, 0, dp(12)); root.addView(preview, previewLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER);
        Button add = button("+ Камера"); add.setOnClickListener(v -> capturePage()); row.addView(add, weightedButton());
        Button gallery = button("+ Галерея"); gallery.setOnClickListener(v -> pickGallery()); row.addView(gallery, weightedButton());
        root.addView(row);

        LinearLayout edit = new LinearLayout(this);
        edit.setOrientation(LinearLayout.HORIZONTAL); edit.setGravity(Gravity.CENTER);
        Button recrop = button("4 угла последней"); recrop.setOnClickListener(v -> recropLast()); edit.addView(recrop, weightedButton());
        Button remove = button("Удалить последнюю"); remove.setOnClickListener(v -> removeLast()); edit.addView(remove, weightedButton());
        root.addView(edit);

        finishButton = button("Готово — создать PDF");
        finishButton.setTextSize(16); finishButton.setOnClickListener(v -> createPdf());
        LinearLayout.LayoutParams finishLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        finishLp.setMargins(0, dp(10), 0, 0); root.addView(finishButton, finishLp);

        updateUi(); setContentView(root);
    }

    private Button button(String label) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); return b; }
    private LinearLayout.LayoutParams weightedButton() { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f); lp.setMargins(dp(3), dp(3), dp(3), dp(3)); return lp; }

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
        } catch (Exception e) { showError(e); }
    }

    private void pickGallery() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*"); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, PICK_GALLERY);
    }

    private void launchCrop(Uri uri) {
        pendingRawUri = uri;
        Intent i = new Intent(this, ManualCropActivity.class);
        i.putExtra("image_uri", uri.toString());
        startActivityForResult(i, CROP_PAGE);
    }

    private void recropLast() {
        if (pages.isEmpty()) { Toast.makeText(this, "Страниц пока нет", Toast.LENGTH_SHORT).show(); return; }
        launchCrop(pages.get(pages.size() - 1));
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_PAGE) {
            Uri raw = cameraUri; cameraUri = null;
            if (resultCode == RESULT_OK && raw != null) launchCrop(raw);
            else if (raw != null) try { getContentResolver().delete(raw, null, null); } catch (Exception ignored) {}
            return;
        }
        if (requestCode == CROP_PAGE) {
            Uri original = pendingRawUri; pendingRawUri = null;
            if (resultCode == RESULT_OK && data != null) {
                String value = data.getStringExtra("cropped_uri");
                if (value != null) {
                    Uri result = Uri.parse(value);
                    // Если переразмечали уже существующую последнюю страницу — заменяем её.
                    if (original != null && !pages.isEmpty() && pages.get(pages.size() - 1).equals(original)) pages.set(pages.size() - 1, result);
                    else pages.add(result);
                    if (original != null && !pages.contains(original)) try { getContentResolver().delete(original, null, null); } catch (Exception ignored) {}
                    updateUi();
                }
            } else if (original != null && !pages.contains(original)) {
                try { getContentResolver().delete(original, null, null); } catch (Exception ignored) {}
            }
            return;
        }
        if (requestCode == PICK_GALLERY && resultCode == RESULT_OK && data != null) {
            List<Uri> selected = new ArrayList<>();
            ClipData clips = data.getClipData();
            if (clips != null) for (int i = 0; i < clips.getItemCount(); i++) selected.add(clips.getItemAt(i).getUri());
            else if (data.getData() != null) selected.add(data.getData());
            if (selected.isEmpty()) return;
            ProgressDialog dialog = ProgressDialog.show(this, "Скан", "Подготавливаю фотографии…", true, false);
            worker.submit(() -> {
                List<Uri> enhanced = new ArrayList<>();
                try {
                    for (Uri uri : selected) enhanced.add(ImageTools.enhanceDocument(this, uri));
                    runOnUiThread(() -> { dialog.dismiss(); pages.addAll(enhanced); updateUi(); });
                } catch (Exception e) { runOnUiThread(() -> { dialog.dismiss(); showError(e); }); }
            });
        }
    }

    private void removeLast() {
        if (pages.isEmpty()) { Toast.makeText(this, "Страниц пока нет", Toast.LENGTH_SHORT).show(); return; }
        Uri removed = pages.remove(pages.size() - 1);
        new AlertDialog.Builder(this).setTitle("Страница удалена из скана")
                .setMessage("Удалить и сохранённый JPG этой страницы из «ФайлМастер»?")
                .setPositiveButton("Удалить JPG", (d, w) -> { try { getContentResolver().delete(removed, null, null); } catch (Exception ignored) {} updateUi(); })
                .setNegativeButton("Оставить JPG", (d, w) -> updateUi()).show();
    }

    private void updateUi() {
        counter.setText(pages.isEmpty() ? "Страниц пока нет" : "Страниц: " + pages.size());
        finishButton.setEnabled(!pages.isEmpty());
        if (pages.isEmpty()) { preview.setImageDrawable(null); preview.setContentDescription("Нет отсканированных страниц"); }
        else { preview.setImageURI(pages.get(pages.size() - 1)); preview.setContentDescription("Последняя отсканированная страница"); }
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
                    new AlertDialog.Builder(this).setTitle("Скан готов").setMessage("Создан PDF из " + snapshot.size() + " стр.")
                            .setPositiveButton("Открыть", (d, w) -> openPdf(pdf))
                            .setNeutralButton("Поделиться", (d, w) -> sharePdf(pdf))
                            .setNegativeButton("Закрыть", (d, w) -> finish()).show();
                });
            } catch (Exception e) { runOnUiThread(() -> { dialog.dismiss(); showError(e); }); }
        });
    }

    private void openPdf(Uri uri) {
        try { Intent i = new Intent(Intent.ACTION_VIEW); i.setDataAndType(uri, "application/pdf"); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(i, "Открыть PDF")); }
        catch (Exception e) { showError(new IllegalStateException("PDF сохранён, но открыть его не удалось")); }
    }
    private void sharePdf(Uri uri) {
        try { Intent i = new Intent(Intent.ACTION_SEND); i.setType("application/pdf"); i.putExtra(Intent.EXTRA_STREAM, uri); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(i, "Поделиться PDF")); }
        catch (Exception e) { showError(new IllegalStateException("Не удалось открыть меню отправки")); }
    }
    private void showError(Exception e) { new AlertDialog.Builder(this).setTitle("Не получилось").setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()).setPositiveButton("OK", null).show(); }
    private TextView text(String value, int sp, int color, boolean bold) { TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color); if (bold) v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD); return v; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() { super.onDestroy(); if (isFinishing()) worker.shutdownNow(); }
}
