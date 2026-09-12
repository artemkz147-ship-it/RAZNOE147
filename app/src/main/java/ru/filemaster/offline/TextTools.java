package ru.filemaster.offline;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.text.Html;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class TextTools {
    private TextTools() {}

    static String readTextLike(Context ctx, Uri uri) throws Exception {
        String name = FileStore.displayName(ctx, uri).toLowerCase();
        if (name.endsWith(".docx")) return DocxTools.extractText(ctx, uri);
        byte[] bytes;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalArgumentException("Не удалось открыть файл");
            FileStore.copy(in, out);
            bytes = out.toByteArray();
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (name.endsWith(".html") || name.endsWith(".htm")) {
            return Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().trim();
        }
        if (name.endsWith(".rtf")) {
            return text.replaceAll("\\\\'[0-9a-fA-F]{2}", "")
                    .replaceAll("\\\\[a-zA-Z]+-?\\d* ?", "")
                    .replace("{", "").replace("}", "").trim();
        }
        return text;
    }

    static Uri toTxt(Context ctx, Uri uri) throws Exception {
        String text = readTextLike(ctx, uri);
        return FileStore.publishBytes(ctx, text.getBytes(StandardCharsets.UTF_8),
                "Текст_" + System.currentTimeMillis() + ".txt", "text/plain", null);
    }

    static Uri toPdf(Context ctx, Uri uri) throws Exception {
        return publishTextPdf(ctx, readTextLike(ctx, uri), "Текст_в_PDF_" + System.currentTimeMillis() + ".pdf");
    }

    static Uri publishTextPdf(Context ctx, String text, String fileName) throws Exception {
        if (text == null) text = "";
        PdfDocument pdf = new PdfDocument();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(14f);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        final int pageW = 595;
        final int pageH = 842;
        final float left = 40f;
        final float right = 40f;
        final float top = 48f;
        final float bottom = 48f;
        final float lineH = 19f;
        int pageNo = 1;
        PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create());
        Canvas canvas = page.getCanvas();
        float y = top;
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        for (String paragraph : normalized.split("\n", -1)) {
            String remaining = paragraph;
            if (remaining.isEmpty()) {
                y += lineH;
                if (y > pageH - bottom) {
                    pdf.finishPage(page);
                    pageNo++;
                    page = pdf.startPage(new PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create());
                    canvas = page.getCanvas();
                    y = top;
                }
                continue;
            }
            while (!remaining.isEmpty()) {
                int count = paint.breakText(remaining, true, pageW - left - right, null);
                if (count <= 0) count = 1;
                int cut = count;
                if (count < remaining.length()) {
                    int space = remaining.lastIndexOf(' ', count - 1);
                    if (space > 10) cut = space + 1;
                }
                String line = remaining.substring(0, cut).trim();
                if (!line.isEmpty()) canvas.drawText(line, left, y, paint);
                y += lineH;
                remaining = remaining.substring(cut).trim();
                if (y > pageH - bottom) {
                    pdf.finishPage(page);
                    pageNo++;
                    page = pdf.startPage(new PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create());
                    canvas = page.getCanvas();
                    y = top;
                }
            }
            y += 4f;
        }
        pdf.finishPage(page);
        File out = File.createTempFile("fm_text_", ".pdf", ctx.getCacheDir());
        try (FileOutputStream fos = new FileOutputStream(out)) {
            pdf.writeTo(fos);
        } finally {
            pdf.close();
        }
        try {
            return FileStore.publishFile(ctx, out, fileName, "application/pdf", null);
        } finally {
            out.delete();
        }
    }
}
