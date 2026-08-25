package ru.filemaster.offline;

import android.content.Context;
import android.net.Uri;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class OpenDocumentTools {
    private OpenDocumentTools() {}

    static String extractOdtText(Context ctx, Uri uri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".odt");
        try (ZipFile zip = new ZipFile(input)) {
            ZipEntry entry = zip.getEntry("content.xml");
            if (entry == null) throw new IllegalArgumentException("В ODT не найден content.xml");
            try (InputStream in = zip.getInputStream(entry)) {
                XmlPullParserFactory f = XmlPullParserFactory.newInstance();
                f.setNamespaceAware(true);
                XmlPullParser p = f.newPullParser();
                p.setInput(in, "UTF-8");
                StringBuilder out = new StringBuilder();
                int event = p.getEventType();
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.TEXT) {
                        String t = p.getText();
                        if (t != null) out.append(t);
                    } else if (event == XmlPullParser.START_TAG) {
                        String n = p.getName();
                        if ("tab".equals(n)) out.append('\t');
                        else if ("line-break".equals(n)) out.append('\n');
                    } else if (event == XmlPullParser.END_TAG) {
                        String n = p.getName();
                        if ("p".equals(n) || "h".equals(n)) out.append('\n');
                        else if ("table-cell".equals(n)) out.append('\t');
                        else if ("table-row".equals(n)) out.append('\n');
                    }
                    event = p.next();
                }
                return out.toString().replaceAll("[ \t]+\n", "\n").replaceAll("\n{3,}", "\n\n").trim();
            }
        } finally { input.delete(); }
    }

    static Uri odtToTxt(Context ctx, Uri uri) throws Exception {
        return FileStore.publishBytes(ctx, extractOdtText(ctx, uri).getBytes(StandardCharsets.UTF_8),
                "ODT_текст_" + System.currentTimeMillis() + ".txt", "text/plain", null);
    }

    static Uri odtToPdf(Context ctx, Uri uri) throws Exception {
        return TextTools.publishTextPdf(ctx, extractOdtText(ctx, uri), "ODT_в_PDF_" + System.currentTimeMillis() + ".pdf");
    }
}
