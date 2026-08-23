package ru.filemaster.offline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

final class OcrTools {
    private OcrTools() {}

    static String recognize(Context ctx, Uri imageUri) throws Exception {
        File dataRoot = new File(ctx.getFilesDir(), "tesseract");
        File tessdata = new File(dataRoot, "tessdata");
        if (!tessdata.exists() && !tessdata.mkdirs()) throw new IllegalStateException("Не удалось подготовить OCR");
        ensureAsset(ctx, "tessdata/rus.traineddata", new File(tessdata, "rus.traineddata"));
        ensureAsset(ctx, "tessdata/eng.traineddata", new File(tessdata, "eng.traineddata"));

        Bitmap bitmap;
        try (InputStream in = ctx.getContentResolver().openInputStream(imageUri)) {
            bitmap = BitmapFactory.decodeStream(in);
        }
        if (bitmap == null) throw new IllegalArgumentException("Не удалось прочитать изображение");

        TessBaseAPI api = new TessBaseAPI();
        try {
            if (!api.init(dataRoot.getAbsolutePath(), "rus+eng")) throw new IllegalStateException("OCR не инициализирован");
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
            api.setImage(bitmap);
            String text = api.getUTF8Text();
            return text == null ? "" : text.trim();
        } finally {
            api.recycle();
            bitmap.recycle();
        }
    }

    private static void ensureAsset(Context ctx, String assetName, File out) throws Exception {
        if (out.exists() && out.length() > 1024) return;
        try (InputStream in = ctx.getAssets().open(assetName); FileOutputStream fos = new FileOutputStream(out)) {
            FileStore.copy(in, fos);
        }
    }
}
