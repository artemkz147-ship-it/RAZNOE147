package ru.filemaster.offline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

final class ImageTools {
    private ImageTools() {}

    static Uri compress(Context ctx, Uri uri, int quality) throws Exception {
        Bitmap bmp = loadScaled(ctx, uri, 3200);
        try { return publishBitmap(ctx, bmp, Bitmap.CompressFormat.JPEG, quality, "Сжатое_" + System.currentTimeMillis() + ".jpg", "image/jpeg"); }
        finally { bmp.recycle(); }
    }

    static int compressBatch(Context ctx, List<Uri> uris, int quality) throws Exception {
        if (uris == null || uris.isEmpty()) throw new IllegalArgumentException("Не выбраны изображения");
        int done = 0;
        for (Uri uri : uris) { compress(ctx, uri, quality); done++; }
        return done;
    }

    static Uri resize(Context ctx, Uri uri, int maxSide) throws Exception {
        Bitmap bmp = loadScaled(ctx, uri, Math.max(320, maxSide));
        Bitmap scaled = scaleToMax(bmp, maxSide);
        try { return publishBitmap(ctx, scaled, Bitmap.CompressFormat.JPEG, 90, "Размер_" + maxSide + "_" + System.currentTimeMillis() + ".jpg", "image/jpeg"); }
        finally {
            if (scaled != bmp && !scaled.isRecycled()) scaled.recycle();
            if (!bmp.isRecycled()) bmp.recycle();
        }
    }

    static Uri rotate90(Context ctx, Uri uri) throws Exception {
        Bitmap bmp = loadScaled(ctx, uri, 3200);
        Matrix matrix = new Matrix();
        matrix.postRotate(90f);
        Bitmap out = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        try { return publishBitmap(ctx, out, Bitmap.CompressFormat.JPEG, 92, "Поворот_" + System.currentTimeMillis() + ".jpg", "image/jpeg"); }
        finally {
            if (out != bmp && !out.isRecycled()) out.recycle();
            if (!bmp.isRecycled()) bmp.recycle();
        }
    }

    static Uri flipHorizontal(Context ctx, Uri uri) throws Exception {
        Bitmap bmp = loadScaled(ctx, uri, 3200);
        Matrix matrix = new Matrix();
        matrix.setScale(-1f, 1f);
        matrix.postTranslate(bmp.getWidth(), 0f);
        Bitmap out = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        try { return publishBitmap(ctx, out, Bitmap.CompressFormat.JPEG, 92, "Отражение_" + System.currentTimeMillis() + ".jpg", "image/jpeg"); }
        finally {
            if (out != bmp && !out.isRecycled()) out.recycle();
            if (!bmp.isRecycled()) bmp.recycle();
        }
    }

    static Uri cropMargins(Context ctx, Uri uri, int percent) throws Exception {
        if (percent < 1 || percent > 35) throw new IllegalArgumentException("Укажите от 1 до 35 процентов");
        Bitmap bmp = loadScaled(ctx, uri, 4000);
        int dx = Math.round(bmp.getWidth() * percent / 100f);
        int dy = Math.round(bmp.getHeight() * percent / 100f);
        int width = bmp.getWidth() - dx * 2;
        int height = bmp.getHeight() - dy * 2;
        if (width < 64 || height < 64) {
            bmp.recycle();
            throw new IllegalArgumentException("После обрезки изображение получится слишком маленьким");
        }
        Bitmap out = Bitmap.createBitmap(bmp, dx, dy, width, height);
        try { return publishBitmap(ctx, out, Bitmap.CompressFormat.JPEG, 94, "Обрезанное_" + System.currentTimeMillis() + ".jpg", "image/jpeg"); }
        finally {
            if (out != bmp && !out.isRecycled()) out.recycle();
            if (!bmp.isRecycled()) bmp.recycle();
        }
    }

    static Uri grayscale(Context ctx, Uri uri) throws Exception {
        Bitmap bmp = loadScaled(ctx, uri, 3200);
        Bitmap out = toGrayContrast(bmp, false);
        try { return publishBitmap(ctx, out, Bitmap.CompressFormat.JPEG, 92, "ЧБ_" + System.currentTimeMillis() + ".jpg", "image/jpeg"); }
        finally {
            if (!out.isRecycled()) out.recycle();
            if (!bmp.isRecycled()) bmp.recycle();
        }
    }

    static Uri watermark(Context ctx, Uri uri, String text) throws Exception {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("Введите текст водяного знака");
        Bitmap src = loadScaled(ctx, uri, 4200);
        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        try {
            Canvas canvas = new Canvas(out);
            float min = Math.min(out.getWidth(), out.getHeight());
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(Math.max(24f, min * 0.055f));
            paint.setColor(Color.argb(185, 255, 255, 255));
            paint.setShadowLayer(Math.max(2f, min * 0.004f), 1f, 1f, Color.argb(210, 0, 0, 0));
            float margin = min * 0.035f;
            float width = paint.measureText(text);
            float x = Math.max(margin, out.getWidth() - width - margin);
            float y = out.getHeight() - margin;
            canvas.drawText(text, x, y, paint);
            return publishBitmap(ctx, out, Bitmap.CompressFormat.JPEG, 94, "Водяной_знак_" + System.currentTimeMillis() + ".jpg", "image/jpeg");
        } finally {
            if (!out.isRecycled()) out.recycle();
            if (!src.isRecycled()) src.recycle();
        }
    }

    static int watermarkBatch(Context ctx, List<Uri> uris, String text) throws Exception {
        if (uris == null || uris.isEmpty()) throw new IllegalArgumentException("Не выбраны изображения");
        int done = 0;
        for (Uri uri : uris) { watermark(ctx, uri, text); done++; }
        return done;
    }

    static Uri stripMetadata(Context ctx, Uri uri) throws Exception {
        Bitmap bmp = loadScaled(ctx, uri, 5000);
        try {
            // Полное декодирование + новая кодировка намеренно не переносит EXIF/XMP/IPTC.
            return publishBitmap(ctx, bmp, Bitmap.CompressFormat.JPEG, 96,
                    "Без_метаданных_" + System.currentTimeMillis() + ".jpg", "image/jpeg");
        } finally { if (!bmp.isRecycled()) bmp.recycle(); }
    }

    static int stripMetadataBatch(Context ctx, List<Uri> uris) throws Exception {
        if (uris == null || uris.isEmpty()) throw new IllegalArgumentException("Не выбраны изображения");
        int done = 0;
        for (Uri uri : uris) { stripMetadata(ctx, uri); done++; }
        return done;
    }

    static Uri convert(Context ctx, Uri uri, String format) throws Exception {
        if (format == null) throw new IllegalArgumentException("Не выбран формат");
        Bitmap bmp = loadScaled(ctx, uri, 4096);
        String f = format.trim().toLowerCase();
        try {
            if (f.equals("png")) return publishBitmap(ctx, bmp, Bitmap.CompressFormat.PNG, 100, "Конвертировано_" + System.currentTimeMillis() + ".png", "image/png");
            if (f.equals("webp")) return publishBitmap(ctx, bmp, Bitmap.CompressFormat.WEBP, 92, "Конвертировано_" + System.currentTimeMillis() + ".webp", "image/webp");
            if (f.equals("jpg") || f.equals("jpeg")) return publishBitmap(ctx, bmp, Bitmap.CompressFormat.JPEG, 92, "Конвертировано_" + System.currentTimeMillis() + ".jpg", "image/jpeg");
            throw new IllegalArgumentException("Поддерживаются JPG, PNG и WebP");
        } finally { if (!bmp.isRecycled()) bmp.recycle(); }
    }

    static int convertBatch(Context ctx, List<Uri> uris, String format) throws Exception {
        if (uris == null || uris.isEmpty()) throw new IllegalArgumentException("Не выбраны изображения");
        int done = 0;
        for (Uri uri : uris) { convert(ctx, uri, format); done++; }
        return done;
    }

    static Uri enhanceDocument(Context ctx, Uri uri) throws Exception {
        Bitmap bmp = loadScaled(ctx, uri, 2400);
        Bitmap cropped = autoCropDocument(bmp);
        Bitmap enhanced = toGrayContrast(cropped, true);
        try { return publishBitmap(ctx, enhanced, Bitmap.CompressFormat.JPEG, 90, "Скан_" + System.currentTimeMillis() + ".jpg", "image/jpeg"); }
        finally {
            if (!enhanced.isRecycled()) enhanced.recycle();
            if (cropped != bmp && !cropped.isRecycled()) cropped.recycle();
            if (!bmp.isRecycled()) bmp.recycle();
        }
    }

    static Uri enhanceWithoutCrop(Context ctx, Uri uri) throws Exception {
        Bitmap bmp = loadScaled(ctx, uri, 2800);
        Bitmap enhanced = toGrayContrast(bmp, true);
        try { return publishBitmap(ctx, enhanced, Bitmap.CompressFormat.JPEG, 92, "Скан_коррекция_" + System.currentTimeMillis() + ".jpg", "image/jpeg"); }
        finally {
            if (!enhanced.isRecycled()) enhanced.recycle();
            if (!bmp.isRecycled()) bmp.recycle();
        }
    }

    static Bitmap loadScaled(Context ctx, Uri uri, int maxSide) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, bounds); }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IllegalArgumentException("Не удалось прочитать изображение");
        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / (sample * 2) >= maxSide) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bmp;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) { bmp = BitmapFactory.decodeStream(in, null, opts); }
        if (bmp == null) throw new IllegalArgumentException("Не удалось декодировать изображение");
        return scaleToMax(bmp, maxSide);
    }

    private static Bitmap scaleToMax(Bitmap bmp, int maxSide) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int largest = Math.max(w, h);
        if (largest <= maxSide) return bmp;
        float s = maxSide / (float) largest;
        Bitmap out = Bitmap.createScaledBitmap(bmp, Math.max(1, Math.round(w * s)), Math.max(1, Math.round(h * s)), true);
        if (out != bmp && !bmp.isRecycled()) bmp.recycle();
        return out;
    }

    private static Bitmap autoCropDocument(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int step = Math.max(1, Math.min(w, h) / 700);
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int c = src.getPixel(x, y);
                int lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000;
                if (lum < 225) {
                    if (x < minX) minX = x; if (x > maxX) maxX = x;
                    if (y < minY) minY = y; if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX <= minX || maxY <= minY) return src;
        int marginX = Math.max(12, (maxX - minX) / 40), marginY = Math.max(12, (maxY - minY) / 40);
        minX = Math.max(0, minX - marginX); minY = Math.max(0, minY - marginY);
        maxX = Math.min(w - 1, maxX + marginX); maxY = Math.min(h - 1, maxY + marginY);
        int cw = maxX - minX + 1, ch = maxY - minY + 1;
        if (cw < w * 0.35 || ch < h * 0.35) return src;
        return Bitmap.createBitmap(src, minX, minY, cw, ch);
    }

    private static Bitmap toGrayContrast(Bitmap src, boolean documentMode) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000;
            if (documentMode) {
                float normalized = (lum - 128f) * 1.35f + 128f;
                lum = Math.max(0, Math.min(255, Math.round(normalized)));
                if (lum > 235) lum = 255; else if (lum < 45) lum = 0;
            }
            px[i] = Color.rgb(lum, lum, lum);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        return out;
    }

    private static Uri publishBitmap(Context ctx, Bitmap bmp, Bitmap.CompressFormat format, int quality, String name, String mime) throws Exception {
        String suffix = name.toLowerCase().endsWith(".png") ? ".png" : name.toLowerCase().endsWith(".webp") ? ".webp" : ".jpg";
        File out = File.createTempFile("fm_img_", suffix, ctx.getCacheDir());
        try (FileOutputStream fos = new FileOutputStream(out)) {
            if (!bmp.compress(format, Math.max(20, Math.min(100, quality)), fos)) throw new IllegalStateException("Не удалось сохранить изображение");
        }
        try { return FileStore.publishFile(ctx, out, name, mime, null); }
        finally { out.delete(); }
    }
}
