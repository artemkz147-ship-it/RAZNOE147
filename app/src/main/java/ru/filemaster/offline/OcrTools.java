package ru.filemaster.offline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.ImageType;
import com.tom_roush.pdfbox.rendering.PDFRenderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class OcrTools {
    private OcrTools() {}

    static String recognize(Context ctx, Uri imageUri) throws Exception {
        Bitmap bitmap;
        try (InputStream in = ctx.getContentResolver().openInputStream(imageUri)) {
            bitmap = BitmapFactory.decodeStream(in);
        }
        if (bitmap == null) throw new IllegalArgumentException("Не удалось прочитать изображение");
        try {
            TessBaseAPI api = openApi(ctx);
            try {
                api.setImage(bitmap);
                String text = api.getUTF8Text();
                return text == null ? "" : text.trim();
            } finally { api.recycle(); }
        } finally { bitmap.recycle(); }
    }

    static Uri recognizePdfToTxt(Context ctx, Uri pdfUri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, pdfUri, ".pdf");
        StringBuilder out = new StringBuilder();
        TessBaseAPI api = openApi(ctx);
        try (PDDocument doc = PDDocument.load(input)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                Bitmap bitmap = renderer.renderImageWithDPI(i, 160, ImageType.RGB);
                try {
                    api.setImage(bitmap);
                    String text = api.getUTF8Text();
                    out.append("--- Страница ").append(i + 1).append(" ---\n");
                    if (text != null) out.append(text.trim());
                    out.append("\n\n");
                    api.clear();
                } finally { bitmap.recycle(); }
            }
        } finally {
            api.recycle();
            input.delete();
        }
        return FileStore.publishBytes(ctx, out.toString().getBytes(StandardCharsets.UTF_8),
                "OCR_PDF_" + System.currentTimeMillis() + ".txt", "text/plain", null);
    }

    private static TessBaseAPI openApi(Context ctx) throws Exception {
        File dataRoot = new File(ctx.getFilesDir(), "tesseract");
        File tessdata = new File(dataRoot, "tessdata");
        if (!tessdata.exists() && !tessdata.mkdirs()) throw new IllegalStateException("Не удалось подготовить OCR");
        ensureAsset(ctx, "tessdata/rus.traineddata", new File(tessdata, "rus.traineddata"));
        ensureAsset(ctx, "tessdata/eng.traineddata", new File(tessdata, "eng.traineddata"));
        TessBaseAPI api = new TessBaseAPI();
        if (!api.init(dataRoot.getAbsolutePath(), "rus+eng")) {
            api.recycle();
            throw new IllegalStateException("OCR не инициализирован");
        }
        api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
        return api;
    }

    private static void ensureAsset(Context ctx, String assetName, File out) throws Exception {
        if (out.exists() && out.length() > 1024) return;
        try (InputStream in = ctx.getAssets().open(assetName); FileOutputStream fos = new FileOutputStream(out)) {
            FileStore.copy(in, fos);
        }
    }
}
