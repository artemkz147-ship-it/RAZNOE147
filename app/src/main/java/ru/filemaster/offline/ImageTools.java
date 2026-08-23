package ru.filemaster.offline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

final class ImageTools {
    private ImageTools() {}

    static Uri compress(Context ctx, Uri uri, int quality) throws Exception {
        Bitmap bmp = loadScaled(ctx, uri, 3200);
        try {
            return publishJpeg(ctx, bmp, quality, "Сжатое_" + System.currentTimeMillis() + ".jpg");
        } finally {
            bmp.recycle();
        }
    }

    static Uri resize(Context ctx, Uri uri, int maxSide) throws Exception {
        Bitmap bmp = loadScaled(ctx, uri, Math.max(320, maxSide));
        Bitmap scaled = scaleToMax(bmp, maxSide);
        try {
            return publishJpeg(ctx, scaled, 90, "Размер_" + maxSide + "_" + System.currentTimeMillis() + ".jpg");
        } finally {
            if (scaled != bmp) scaled.recycle();
            bmp.recycle();
        }
    }

    static Uri rotate90(Context ctx, Uri uri) throws Exception {
        Bitmap bmp = loadScaled(ctx, uri, 3200);
        Matrix matrix = new Matrix();
        matrix.postRotate(90f);
        Bitmap out = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        try {
            return publishJpeg(ctx, out, 92, "Поворот_" + System.currentTimeMillis() + ".jpg");
        } finally {
            out.recycle();
            bmp.recycle();
        }
    }

    static Uri grayscale(Context ctx, Uri uri) throws Exception {
        Bitmap bmp = loadScaled(ctx, uri, 3200);
        Bitmap out = toGrayContrast(bmp, false);
        try {
            return publishJpeg(ctx, out, 92, "ЧБ_" + System.currentTimeMillis() + ".jpg");
        } finally {
            out.recycle();
            bmp.recycle();
        }
    }

    static Uri enhanceDocument(Context ctx, Uri uri) throws Exception {
        Bitmap bmp = loadScaled(ctx, uri, 2400);
        Bitmap cropped = autoCropDocument(bmp);
        Bitmap enhanced = toGrayContrast(cropped, true);
        try {
            return publishJpeg(ctx, enhanced, 90, "Скан_" + System.currentTimeMillis() + ".jpg");
        } finally {
            enhanced.recycle();
            if (cropped != bmp) cropped.recycle();
            bmp.recycle();
        }
    }

    private static Bitmap loadScaled(Context ctx, Uri uri, int maxSide) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IllegalArgumentException("Не удалось прочитать изображение");
        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / (sample * 2) >= maxSide) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bmp;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bmp == null) throw new IllegalArgumentException("Не удалось декодировать изображение");
        return scaleToMax(bmp, maxSide);
    }

    private static Bitmap scaleToMax(Bitmap bmp, int maxSide) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int largest = Math.max(w, h);
        if (largest <= maxSide) return bmp;
        float s = maxSide / (float) largest;
        return Bitmap.createScaledBitmap(bmp, Math.max(1, Math.round(w * s)), Math.max(1, Math.round(h * s)), true);
    }

    private static Bitmap autoCropDocument(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int step = Math.max(1, Math.min(w, h) / 700);
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int c = src.getPixel(x, y);
                int lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000;
                if (lum < 225) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX <= minX || maxY <= minY) return src;
        int marginX = Math.max(12, (maxX - minX) / 40);
        int marginY = Math.max(12, (maxY - minY) / 40);
        minX = Math.max(0, minX - marginX);
        minY = Math.max(0, minY - marginY);
        maxX = Math.min(w - 1, maxX + marginX);
        maxY = Math.min(h - 1, maxY + marginY);
        int cw = maxX - minX + 1;
        int ch = maxY - minY + 1;
        if (cw < w * 0.35 || ch < h * 0.35) return src;
        return Bitmap.createBitmap(src, minX, minY, cw, ch);
    }

    private static Bitmap toGrayContrast(Bitmap src, boolean documentMode) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000;
            if (documentMode) {
                float normalized = (lum - 128f) * 1.35f + 128f;
                lum = Math.max(0, Math.min(255, Math.round(normalized)));
                if (lum > 235) lum = 255;
                else if (lum < 45) lum = 0;
            }
            px[i] = Color.rgb(lum, lum, lum);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        return out;
    }

    private static Uri publishJpeg(Context ctx, Bitmap bmp, int quality, String name) throws Exception {
        File out = File.createTempFile("fm_img_", ".jpg", ctx.getCacheDir());
        try (FileOutputStream fos = new FileOutputStream(out)) {
            if (!bmp.compress(Bitmap.CompressFormat.JPEG, Math.max(20, Math.min(100, quality)), fos)) {
                throw new IllegalStateException("Не удалось сохранить изображение");
            }
        }
        try {
            return FileStore.publishFile(ctx, out, name, "image/jpeg", null);
        } finally {
            out.delete();
        }
    }
}
