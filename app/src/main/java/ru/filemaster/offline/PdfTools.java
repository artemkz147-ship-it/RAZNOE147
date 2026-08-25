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
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission;
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.rendering.ImageType;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
                try (page) { page.save(out); }
                FileStore.publishFile(ctx, out, String.format("Страница_%03d.pdf", i++), "application/pdf", folder);
                out.delete();
                count++;
            }
        } finally { input.delete(); }
        return count;
    }

    static Uri imagesToPdf(Context ctx, List<Uri> uris) throws Exception {
        if (uris.isEmpty()) throw new IllegalArgumentException("Не выбраны изображения");
        File out = File.createTempFile("images_", ".pdf", ctx.getCacheDir());
        try (PDDocument doc = new PDDocument()) {
            for (Uri uri : uris) {
                Bitmap bitmap;
                try (InputStream in = ctx.getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(in); }
                if (bitmap == null) continue;
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                PDImageXObject image = LosslessFactory.createFromImage(doc, bitmap);
                float pw = page.getMediaBox().getWidth(), ph = page.getMediaBox().getHeight();
                float scale = Math.min(pw / bitmap.getWidth(), ph / bitmap.getHeight());
                float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(image, (pw - w) / 2f, (ph - h) / 2f, w, h);
                }
                bitmap.recycle();
            }
            if (doc.getNumberOfPages() == 0) throw new IllegalArgumentException("Не удалось прочитать изображения");
            doc.save(out);
        }
        try { return FileStore.publishFile(ctx, out, "Фото_в_PDF_" + System.currentTimeMillis() + ".pdf", "application/pdf", null); }
        finally { out.delete(); }
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
                try (FileOutputStream fos = new FileOutputStream(out)) { bmp.compress(Bitmap.CompressFormat.JPEG, 90, fos); }
                bmp.recycle();
                FileStore.publishFile(ctx, out, String.format("Страница_%03d.jpg", i + 1), "image/jpeg", folder);
                out.delete();
                count++;
            }
        } finally { input.delete(); }
        return count;
    }

    static Uri rotateAll(Context ctx, Uri uri, int degrees) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".pdf");
        File out = File.createTempFile("rotate_", ".pdf", ctx.getCacheDir());
        try (PDDocument doc = PDDocument.load(input)) {
            for (PDPage page : doc.getPages()) page.setRotation((page.getRotation() + degrees) % 360);
            doc.save(out);
        } finally { input.delete(); }
        try { return FileStore.publishFile(ctx, out, "Поворот_PDF_" + System.currentTimeMillis() + ".pdf", "application/pdf", null); }
        finally { out.delete(); }
    }

    static Uri removePages(Context ctx, Uri uri, String pageSpec) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".pdf");
        File out = File.createTempFile("removed_", ".pdf", ctx.getCacheDir());
        try (PDDocument doc = PDDocument.load(input)) {
            Set<Integer> pages = parsePageSpec(pageSpec, doc.getNumberOfPages());
            if (pages.isEmpty()) throw new IllegalArgumentException("Не указаны страницы");
            if (pages.size() >= doc.getNumberOfPages()) throw new IllegalArgumentException("Нельзя удалить все страницы");
            List<Integer> sorted = new ArrayList<>(pages);
            sorted.sort(Collections.reverseOrder());
            for (int oneBased : sorted) doc.removePage(oneBased - 1);
            doc.save(out);
        } finally { input.delete(); }
        try { return FileStore.publishFile(ctx, out, "Удалены_страницы_" + System.currentTimeMillis() + ".pdf", "application/pdf", null); }
        finally { out.delete(); }
    }

    static Uri extractPages(Context ctx, Uri uri, String pageSpec) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".pdf");
        File out = File.createTempFile("extract_pages_", ".pdf", ctx.getCacheDir());
        try (PDDocument src = PDDocument.load(input); PDDocument dst = new PDDocument()) {
            Set<Integer> pages = parsePageSpec(pageSpec, src.getNumberOfPages());
            if (pages.isEmpty()) throw new IllegalArgumentException("Не указаны страницы");
            List<Integer> sorted = new ArrayList<>(pages);
            Collections.sort(sorted);
            for (int oneBased : sorted) dst.importPage(src.getPage(oneBased - 1));
            dst.save(out);
        } finally { input.delete(); }
        try { return FileStore.publishFile(ctx, out, "Извлечено_" + System.currentTimeMillis() + ".pdf", "application/pdf", null); }
        finally { out.delete(); }
    }

    static Uri reversePages(Context ctx, Uri uri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".pdf");
        File out = File.createTempFile("reverse_", ".pdf", ctx.getCacheDir());
        try (PDDocument src = PDDocument.load(input); PDDocument dst = new PDDocument()) {
            for (int i = src.getNumberOfPages() - 1; i >= 0; i--) dst.importPage(src.getPage(i));
            dst.save(out);
        } finally { input.delete(); }
        try { return FileStore.publishFile(ctx, out, "Обратный_порядок_" + System.currentTimeMillis() + ".pdf", "application/pdf", null); }
        finally { out.delete(); }
    }

    static Uri addPageNumbers(Context ctx, Uri uri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".pdf");
        File out = File.createTempFile("numbers_", ".pdf", ctx.getCacheDir());
        try (PDDocument doc = PDDocument.load(input)) {
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                PDPage page = doc.getPage(i);
                String number = String.valueOf(i + 1);
                float y = 20f;
                float x = page.getMediaBox().getWidth() / 2f - number.length() * 2.5f;
                try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, 9f);
                    cs.newLineAtOffset(x, y);
                    cs.showText(number);
                    cs.endText();
                }
            }
            doc.save(out);
        } finally { input.delete(); }
        try { return FileStore.publishFile(ctx, out, "Номера_страниц_" + System.currentTimeMillis() + ".pdf", "application/pdf", null); }
        finally { out.delete(); }
    }

    static Uri extractText(Context ctx, Uri uri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".pdf");
        try (PDDocument doc = PDDocument.load(input)) {
            String text = new PDFTextStripper().getText(doc);
            return FileStore.publishBytes(ctx, text.getBytes(StandardCharsets.UTF_8), "PDF_текст_" + System.currentTimeMillis() + ".txt", "text/plain", null);
        } finally { input.delete(); }
    }

    static Uri protect(Context ctx, Uri uri, String password) throws Exception {
        if (password == null || password.length() < 4) throw new IllegalArgumentException("Пароль должен быть минимум 4 символа");
        File input = FileStore.copyUriToTemp(ctx, uri, ".pdf");
        File out = File.createTempFile("protected_", ".pdf", ctx.getCacheDir());
        try (PDDocument doc = PDDocument.load(input)) {
            AccessPermission permission = new AccessPermission();
            StandardProtectionPolicy policy = new StandardProtectionPolicy(password, password, permission);
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            doc.save(out);
        } finally { input.delete(); }
        try { return FileStore.publishFile(ctx, out, "Защищено_" + System.currentTimeMillis() + ".pdf", "application/pdf", null); }
        finally { out.delete(); }
    }

    static Uri unlock(Context ctx, Uri uri, String password) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".pdf");
        File out = File.createTempFile("unlocked_", ".pdf", ctx.getCacheDir());
        try (PDDocument doc = PDDocument.load(input, password == null ? "" : password)) {
            doc.setAllSecurityToBeRemoved(true);
            doc.save(out);
        } finally { input.delete(); }
        try { return FileStore.publishFile(ctx, out, "Без_пароля_" + System.currentTimeMillis() + ".pdf", "application/pdf", null); }
        finally { out.delete(); }
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
            float w = maxW, h = signature.getHeight() * scale;
            float x = page.getMediaBox().getWidth() - w - 36f, y = 36f;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.drawImage(image, x, y, w, h);
            }
            doc.save(out);
        } finally { signature.recycle(); input.delete(); }
        try { return FileStore.publishFile(ctx, out, "Подписано_" + System.currentTimeMillis() + ".pdf", "application/pdf", null); }
        finally { out.delete(); }
    }

    private static Set<Integer> parsePageSpec(String spec, int max) {
        Set<Integer> out = new HashSet<>();
        if (spec == null) return out;
        for (String part : spec.replace('–', '-').split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            try {
                if (part.contains("-")) {
                    String[] a = part.split("-", 2);
                    int start = Integer.parseInt(a[0].trim()), end = Integer.parseInt(a[1].trim());
                    if (start > end) { int t = start; start = end; end = t; }
                    for (int i = start; i <= end; i++) if (i >= 1 && i <= max) out.add(i);
                } else {
                    int n = Integer.parseInt(part);
                    if (n >= 1 && n <= max) out.add(n);
                }
            } catch (NumberFormatException ignored) {}
        }
        return out;
    }
}
