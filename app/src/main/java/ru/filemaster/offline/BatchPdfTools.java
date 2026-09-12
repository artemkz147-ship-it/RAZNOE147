package ru.filemaster.offline;

import android.content.Context;
import android.net.Uri;

import java.util.List;

final class BatchPdfTools {
    private BatchPdfTools() {}

    static int compress(Context ctx, List<Uri> uris, int dpi, float quality) throws Exception {
        require(uris); int done = 0;
        for (Uri uri : uris) { PdfExtraTools.compressRaster(ctx, uri, dpi, quality); done++; }
        return done;
    }

    static int cleanMetadata(Context ctx, List<Uri> uris) throws Exception {
        require(uris); int done = 0;
        for (Uri uri : uris) { PdfExtraTools.removeMetadata(ctx, uri); done++; }
        return done;
    }

    static int watermark(Context ctx, List<Uri> uris, String text) throws Exception {
        require(uris); int done = 0;
        for (Uri uri : uris) { PdfExtraTools.watermark(ctx, uri, text); done++; }
        return done;
    }

    static int toJpg(Context ctx, List<Uri> uris) throws Exception {
        require(uris); int pages = 0;
        for (Uri uri : uris) pages += PdfTools.pdfToJpeg(ctx, uri);
        return pages;
    }

    static int ocr(Context ctx, List<Uri> uris) throws Exception {
        require(uris); int done = 0;
        for (Uri uri : uris) { OcrTools.recognizePdfToTxt(ctx, uri); done++; }
        return done;
    }

    static int protect(Context ctx, List<Uri> uris, String password) throws Exception {
        require(uris); int done = 0;
        for (Uri uri : uris) { PdfTools.protect(ctx, uri, password); done++; }
        return done;
    }

    private static void require(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) throw new IllegalArgumentException("Не выбраны PDF");
    }
}
