package ru.filemaster.offline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility;
import com.tom_roush.pdfbox.multipdf.Splitter;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.rendering.ImageType;
import com.tom_roush.pdfbox.rendering.PDFRenderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

final class PdfTools {
    private PdfTools() {}

    static Uri merge(Context ctx, List<Uri> uris) throws Exception {
        if (uris.size() < 2) throw new IllegalArgumentException("Нужно выбрать минимум два PDF");
        List<File> inputs = new ArrayList<>();
        File out = File.createTempFile("merged_", ".pdf", ctx.getCacheDir());
        try {
            PDFMergerUtility merger = new PDFMergerUtility();
            for (Uri uri : uris) {
                File f = FileStore.copyUriToTemp(ctx, uri, ".pdf");
                inputs.add(f);
                merger.addSource(f);
            }
            merger.setDestinationFileName(out.getAbsolutePath());
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly());
            return FileStore.publishFile(ctx, out, "Объединено_" + System.currentTimeMillis() + ".pdf", "application/pdf", null);
        } finally {
            for (File f : inputs) f.delete();
            out.delete();
        }
    }

    static int split(Context ctx, Uri uri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".pdf");
        String folder = "Разделено_" + System.currentTimeMillis();
        int count = 0;
        try (PDDocument doc = PDDocument.load(input)) {
            List<PDDocument> pages = new Splitter().split(doc);
            int i = 1;
            for (PDDocument page : pages) {
                File out = File.createTempFile("page_", ".pdf", ctx.getCacheDir());
                try (page) {
                    page.save(out);
                }
                FileStore.publishFile(ctx, out, String.format("Страница_%03d.pdf", i++), "application/pdf", folder);
                out.delete();
                count++;
            }
        } finally {
            input.delete();
        }
        return count;
    }

    static Uri imagesToPdf(Context ctx, List<Uri> uris) throws Exception {
        if (uris.isEmpty()) throw new IllegalArgumentException("Не выбраны изображения");
        File out = File.createTempFile("images_", ".pdf", ctx.getCacheDir());
        try (PDDocument doc = new PDDocument()) {
            for (Uri uri : uris) {
                Bitmap bitmap;
                try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                    bitmap = BitmapFactory.decodeStream(in);
                }
                if (bitmap == null) continue;
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                PDImageXObject image = LosslessFactory.createFromImage(doc, bitmap);
                float pw = page.getMediaBox().getWidth();
                float ph = page.getMediaBox().getHeight();
                float scale = Math.min(pw / bitmap.getWidth(), ph / bitmap.getHeight());
                float w = bitmap.getWidth() * scale;
                float h = bitmap.getHeight() * scale;
                float x = (pw - w) / 2f;
                float y = (ph - h) / 2f;
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(image, x, y, w, h);
                }
                bitmap.recycle();
            }
            if (doc.getNumberOfPages() == 0) throw new IllegalArgumentException("Не удалось прочитать изображения");
            doc.save(out);
        }
        try {
            return FileStore.publishFile(ctx, out, "Фото_в_PDF_" + System.currentTimeMillis() + ".pdf", "application/pdf", null);
        } finally {
            out.delete();
        }
    }

    static int pdfToJpeg(Context ctx, Uri uri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".pdf");
        String folder = "PDF_в_JPG_" + System.currentTimeMillis();
        int count = 0;
        try (PDDocument doc = PDDocument.load(input)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                Bitmap bmp = renderer.renderImageWithDPI(i, 150, ImageType.RGB);
                File out = File.createTempFile("pdfpage_", ".jpg", ctx.getCacheDir());
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                }
                bmp.recycle();
                FileStore.publishFile(ctx, out, String.format("Страница_%03d.jpg", i + 1), "image/jpeg", folder);
                out.delete();
                count++;
            }
        } finally {
            input.delete();
        }
        return count;
    }

    static Uri addVisibleSignature(Context ctx, Uri pdfUri, File signaturePng) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, pdfUri, ".pdf");
        File out = File.createTempFile("signed_", ".pdf", ctx.getCacheDir());
        Bitmap signature = BitmapFactory.decodeFile(signaturePng.getAbsolutePath());
        if (signature == null) throw new IllegalArgumentException("Не удалось прочитать подпись");
        try (PDDocument doc = PDDocument.load(input)) {
            if (doc.getNumberOfPages() == 0) throw new IllegalArgumentException("PDF без страниц");
            PDPage page = doc.getPage(doc.getNumberOfPages() - 1);
            PDImageXObject image = LosslessFactory.createFromImage(doc, signature);
            float maxW = page.getMediaBox().getWidth() * 0.32f;
            float scale = maxW / signature.getWidth();
            float w = maxW;
            float h = signature.getHeight() * scale;
            float x = page.getMediaBox().getWidth() - w - 36f;
            float y = 36f;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.drawImage(image, x, y, w, h);
            }
            doc.save(out);
        } finally {
            signature.recycle();
            input.delete();
        }
        try {
            return FileStore.publishFile(ctx, out, "Подписано_" + System.currentTimeMillis() + ".pdf", "application/pdf", null);
        } finally {
            out.delete();
        }
    }
}
