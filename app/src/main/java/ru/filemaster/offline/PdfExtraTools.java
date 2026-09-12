package ru.filemaster.offline;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.font.PDFont;
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.pdmodel.graphics.PDXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.rendering.ImageType;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PdfExtraTools {
    private PdfExtraTools() {}

    static Uri compressRaster(Context ctx, Uri uri, int dpi, float quality) throws Exception {
        dpi = Math.max(72, Math.min(180, dpi));
        quality = Math.max(0.35f, Math.min(0.92f, quality));
        File input = FileStore.copyUriToTemp(ctx, uri, ".pdf");
        File out = File.createTempFile("compressed_", ".pdf", ctx.getCacheDir());
        try (PDDocument src = PDDocument.load(input); PDDocument dst = new PDDocument()) {
            PDFRenderer renderer = new PDFRenderer(src);
            for (int i = 0; i < src.getNumberOfPages(); i++) {
                PDPage original = src.getPage(i);
                PDRectangle box = original.getCropBox();
                float width = box.getWidth();
                float height = box.getHeight();
                int rotation = ((original.getRotation() % 360) + 360) % 360;
                if (rotation == 90 || rotation == 270) {
                    float t = width; width = height; height = t;
                }
                Bitmap bmp = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);
                try {
                    PDPage page = new PDPage(new PDRectangle(width, height));
                    dst.addPage(page);
                    PDImageXObject image = JPEGFactory.createFromImage(dst, bmp, quality);
                    try (PDPageContentStream cs = new PDPageContentStream(dst, page)) {
                        cs.drawImage(image, 0, 0, width, height);
                    }
                } finally { bmp.recycle(); }
            }
            dst.save(out);
        } finally { input.delete(); }
        try {
            return FileStore.publishFile(ctx, out, "Сжатый_PDF_" + System.currentTimeMillis() + ".pdf", "application/pdf", null);
        } finally { out.delete(); }
    }

    static Uri repair(Context ctx, Uri uri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".pdf");
        File out = File.createTempFile("repaired_", ".pdf", ctx.getCacheDir());
        try (PDDocument doc = PDDocument.load(input)) {
            doc.save(out);
        } finally { input.delete(); }
        try {
            return FileStore.publishFile(ctx, out, "Восстановлен_" + System.currentTimeMillis() + ".pdf", "application/pdf", null);
        } finally { out.delete(); }
    }

    static Uri cropMargins(Context ctx, Uri uri, float percent) throws Exception {
        if (percent <= 0f || percent >= 35f) throw new IllegalArgumentException("Укажите от 1 до 34 процентов");
        File input = FileStore.copyUriToTemp(ctx, uri, ".pdf");
        File out = File.createTempFile("cropped_", ".pdf", ctx.getCacheDir());
        try (PDDocument doc = PDDocument.load(input)) {
            for (PDPage page : doc.getPages()) {
                PDRectangle box = page.getCropBox();
                float dx = box.getWidth() * percent / 100f;
                float dy = box.getHeight() * percent / 100f;
                float width = box.getWidth() - dx * 2f;
                float height = box.getHeight() - dy * 2f;
                if (width < 72f || height < 72f) throw new IllegalArgumentException("Слишком большое обрезание для этой страницы");
                PDRectangle cropped = new PDRectangle(box.getLowerLeftX() + dx, box.getLowerLeftY() + dy, width, height);
                page.setCropBox(cropped);
            }
            doc.save(out);
        } finally { input.delete(); }
        try {
            return FileStore.publishFile(ctx, out, "Обрезанный_PDF_" + System.currentTimeMillis() + ".pdf", "application/pdf", null);
        } finally { out.delete(); }
    }

    static int extractEmbeddedImages(Context ctx, Uri uri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".pdf");
        int[] number = {0};
        Set<Integer> visited = new HashSet<>();
        try (PDDocument doc = PDDocument.load(input)) {
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                PDResources resources = doc.getPage(i).getResources();
                if (resources != null) extractImagesFromResources(ctx, resources, i + 1, number, visited);
            }
        } finally { input.delete(); }
        if (number[0] == 0) throw new IllegalArgumentException("Встроенные изображения в PDF не найдены");
        return number[0];
    }

    private static void extractImagesFromResources(Context ctx, PDResources resources, int page, int[] number, Set<Integer> visited) throws Exception {
        for (COSName name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);
            if (xObject == null) continue;
            int identity = System.identityHashCode(xObject.getCOSObject());
            if (!visited.add(identity)) continue;
            if (xObject instanceof PDImageXObject image) {
                Bitmap bitmap = image.getImage();
                if (bitmap == null) continue;
                File temp = File.createTempFile("pdf_image_", ".png", ctx.getCacheDir());
                try {
                    try (FileOutputStream out = new FileOutputStream(temp)) {
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IllegalStateException("Не удалось сохранить изображение из PDF");
                    }
                    number[0]++;
                    FileStore.publishFile(ctx, temp, "PDF_страница_" + page + "_изображение_" + number[0] + ".png", "image/png", "Изображения_PDF");
                } finally {
                    temp.delete();
                    bitmap.recycle();
                }
            } else if (xObject instanceof PDFormXObject form && form.getResources() != null) {
                extractImagesFromResources(ctx, form.getResources(), page, number, visited);
            }
        }
    }

    static Uri removeMetadata(Context ctx, Uri uri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".pdf");
        File out = File.createTempFile("cleanmeta_", ".pdf", ctx.getCacheDir());
        try (PDDocument doc = PDDocument.load(input)) {
            doc.setDocumentInformation(new PDDocumentInformation());
            doc.getDocumentCatalog().setMetadata(null);
            doc.save(out);
        } finally { input.delete(); }
        try {
            return FileStore.publishFile(ctx, out, "Без_метаданных_" + System.currentTimeMillis() + ".pdf", "application/pdf", null);
        } finally { out.delete(); }
    }

    static Uri watermark(Context ctx, Uri uri, String text) throws Exception {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("Введите текст водяного знака");
        File input = FileStore.copyUriToTemp(ctx, uri, ".pdf");
        File out = File.createTempFile("watermark_", ".pdf", ctx.getCacheDir());
        try (PDDocument doc = PDDocument.load(input)) {
            PDFont font = loadUnicodeFont(ctx, doc);
            float size = 34f;
            for (PDPage page : doc.getPages()) {
                float width = page.getCropBox().getWidth();
                float height = page.getCropBox().getHeight();
                float textWidth;
                try { textWidth = font.getStringWidth(text) / 1000f * size; }
                catch (Exception e) { textWidth = Math.min(width - 40f, text.length() * size * 0.55f); }
                float x = Math.max(20f, (width - textWidth) / 2f);
                float y = height / 2f;
                try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.beginText();
                    cs.setNonStrokingColor(185, 185, 185);
                    cs.setFont(font, size);
                    cs.newLineAtOffset(x, y);
                    cs.showText(text);
                    cs.endText();
                }
            }
            doc.save(out);
        } finally { input.delete(); }
        try {
            return FileStore.publishFile(ctx, out, "Водяной_знак_" + System.currentTimeMillis() + ".pdf", "application/pdf", null);
        } finally { out.delete(); }
    }

    static Uri compareText(Context ctx, List<Uri> uris) throws Exception {
        if (uris.size() != 2) throw new IllegalArgumentException("Выберите ровно два PDF");
        File a = FileStore.copyUriToTemp(ctx, uris.get(0), ".pdf");
        File b = FileStore.copyUriToTemp(ctx, uris.get(1), ".pdf");
        try (PDDocument da = PDDocument.load(a); PDDocument db = PDDocument.load(b)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String ta = stripper.getText(da).replace("\r\n", "\n");
            String tb = stripper.getText(db).replace("\r\n", "\n");
            String[] la = ta.split("\n", -1);
            String[] lb = tb.split("\n", -1);
            int max = Math.max(la.length, lb.length);
            int diff = 0;
            StringBuilder report = new StringBuilder();
            report.append("Сравнение PDF по извлечённому тексту\n")
                    .append("A: ").append(FileStore.displayName(ctx, uris.get(0))).append("\n")
                    .append("B: ").append(FileStore.displayName(ctx, uris.get(1))).append("\n\n");
            for (int i = 0; i < max; i++) {
                String aa = i < la.length ? la[i] : "";
                String bb = i < lb.length ? lb[i] : "";
                if (!aa.equals(bb)) {
                    diff++;
                    if (diff <= 500) {
                        report.append("Строка ").append(i + 1).append(":\n")
                                .append("- A: ").append(aa).append("\n")
                                .append("+ B: ").append(bb).append("\n\n");
                    }
                }
            }
            report.insert(report.indexOf("\n\n") + 2, "Различающихся строк: " + diff + "\n\n");
            if (diff > 500) report.append("Показаны первые 500 различий.\n");
            return FileStore.publishBytes(ctx, report.toString().getBytes(StandardCharsets.UTF_8),
                    "Сравнение_PDF_" + System.currentTimeMillis() + ".txt", "text/plain", null);
        } finally { a.delete(); b.delete(); }
    }

    private static PDFont loadUnicodeFont(Context ctx, PDDocument doc) throws Exception {
        File systemRoboto = new File("/system/fonts/Roboto-Regular.ttf");
        if (systemRoboto.exists()) {
            try (InputStream in = new FileInputStream(systemRoboto)) {
                return PDType0Font.load(doc, in);
            } catch (Exception ignored) {}
        }
        try (InputStream in = ctx.getAssets().open("com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf")) {
            return PDType0Font.load(doc, in);
        } catch (Exception ignored) {
            return PDType1Font.HELVETICA;
        }
    }
}
