package ru.filemaster.offline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;

import com.caverock.androidsvg.SVG;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import dev.jaeyoung.tiffbitmapfactory.TiffBitmapFactory;
import dev.jaeyoung.tiffbitmapfactory.TiffBitmapFactoryOptions;

final class ExtendedImageTools {
    private ExtendedImageTools() {}

    static Uri svgToPng(Context ctx, Uri uri) throws Exception { return svg(ctx, uri, false); }
    static Uri svgToJpg(Context ctx, Uri uri) throws Exception { return svg(ctx, uri, true); }

    private static Uri svg(Context ctx, Uri uri, boolean jpeg) throws Exception {
        SVG svg;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalArgumentException("Не удалось открыть SVG");
            svg = SVG.getFromInputStream(in);
        }
        if (svg == null) throw new IllegalArgumentException("Некорректный SVG");
        float w = svg.getDocumentWidth(), h = svg.getDocumentHeight();
        if (!(w > 0) || !(h > 0) || Float.isNaN(w) || Float.isNaN(h)) {
            com.caverock.androidsvg.SVG.Box box = svg.getDocumentViewBox();
            if (box != null && box.width > 0 && box.height > 0) { w = box.width; h = box.height; }
            else { w = 1024; h = 1024; }
        }
        float scale = Math.min(1f, 4096f / Math.max(w, h));
        int width = Math.max(1, Math.round(w * scale)), height = Math.max(1, Math.round(h * scale));
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bmp); canvas.drawColor(jpeg ? Color.WHITE : Color.TRANSPARENT);
            canvas.scale(width / w, height / h); svg.renderToCanvas(canvas);
            return publish(ctx, bmp, jpeg ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG, jpeg ? 94 : 100,
                    "SVG_" + System.currentTimeMillis() + (jpeg ? ".jpg" : ".png"), jpeg ? "image/jpeg" : "image/png");
        } finally { bmp.recycle(); }
    }

    static Uri tiffFirstToPng(Context ctx, Uri uri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".tif");
        try {
            TiffBitmapFactoryOptions o = new TiffBitmapFactoryOptions(); o.setInPageIndex(0); o.setInMaxPixels(30_000_000L);
            Bitmap bmp = TiffBitmapFactory.INSTANCE.decodeFile(input.getAbsolutePath(), o);
            if (bmp == null) throw new IllegalArgumentException("Этот вариант TIFF не поддерживается лёгким декодером (например LZW/JPEG-in-TIFF/CCITT/tiled)");
            try { return publish(ctx, bmp, Bitmap.CompressFormat.PNG, 100, "TIFF_" + System.currentTimeMillis() + ".png", "image/png"); }
            finally { bmp.recycle(); }
        } finally { input.delete(); }
    }

    static int tiffAllPagesToPng(Context ctx, Uri uri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".tif");
        try {
            TiffBitmapFactoryOptions probe = new TiffBitmapFactoryOptions(); probe.setInJustDecodeBounds(true); probe.setInPageIndex(0);
            TiffBitmapFactory.INSTANCE.decodeFile(input.getAbsolutePath(), probe);
            int pages = Math.max(1, probe.getOutPageCount());
            String folder = "TIFF_страницы_" + System.currentTimeMillis(); int done = 0;
            for (int i = 0; i < pages; i++) {
                TiffBitmapFactoryOptions o = new TiffBitmapFactoryOptions(); o.setInPageIndex(i); o.setInMaxPixels(30_000_000L);
                Bitmap bmp = TiffBitmapFactory.INSTANCE.decodeFile(input.getAbsolutePath(), o);
                if (bmp == null) continue;
                File temp = File.createTempFile("tiff_page_", ".png", ctx.getCacheDir());
                try {
                    try (FileOutputStream fos = new FileOutputStream(temp)) { if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)) throw new IllegalStateException("Не удалось сохранить TIFF-страницу"); }
                    FileStore.publishFile(ctx, temp, String.format(java.util.Locale.ROOT, "TIFF_%03d.png", i + 1), "image/png", folder); done++;
                } finally { temp.delete(); bmp.recycle(); }
            }
            if (done == 0) throw new IllegalArgumentException("Не удалось декодировать страницы TIFF");
            return done;
        } finally { input.delete(); }
    }

    static Uri systemDecodeTo(Context ctx, Uri uri, String format) throws Exception {
        ImageDecoder.Source source = ImageDecoder.createSource(ctx.getContentResolver(), uri);
        Bitmap bmp = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            int w = info.getSize().getWidth(), h = info.getSize().getHeight();
            int max = Math.max(w, h); if (max > 4096) { float s = 4096f / max; decoder.setTargetSize(Math.max(1, Math.round(w*s)), Math.max(1, Math.round(h*s))); }
        });
        try {
            String f = format == null ? "png" : format.toLowerCase();
            if (f.equals("jpg") || f.equals("jpeg")) return publish(ctx,bmp,Bitmap.CompressFormat.JPEG,94,"Изображение_"+System.currentTimeMillis()+".jpg","image/jpeg");
            if (f.equals("webp")) return publish(ctx,bmp,Bitmap.CompressFormat.WEBP,94,"Изображение_"+System.currentTimeMillis()+".webp","image/webp");
            return publish(ctx,bmp,Bitmap.CompressFormat.PNG,100,"Изображение_"+System.currentTimeMillis()+".png","image/png");
        } finally { bmp.recycle(); }
    }

    private static Uri publish(Context ctx, Bitmap bmp, Bitmap.CompressFormat format, int quality, String name, String mime) throws Exception {
        File f = File.createTempFile("extended_image_", name.endsWith(".png") ? ".png" : name.endsWith(".webp") ? ".webp" : ".jpg", ctx.getCacheDir());
        try {
            try (FileOutputStream out = new FileOutputStream(f)) { if (!bmp.compress(format, quality, out)) throw new IllegalStateException("Не удалось закодировать изображение"); }
            return FileStore.publishFile(ctx, f, name, mime, null);
        } finally { f.delete(); }
    }
}
