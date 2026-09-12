package ru.filemaster.offline;

import android.content.Context;
import android.net.Uri;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class DocxTools {
    private DocxTools() {}

    static String extractText(Context ctx, Uri uri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".docx");
        try (ZipFile zip = new ZipFile(input)) {
            ZipEntry entry = zip.getEntry("word/document.xml");
            if (entry == null) throw new IllegalArgumentException("В DOCX не найден документ");
            try (InputStream in = zip.getInputStream(entry)) {
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                factory.setNamespaceAware(true);
                XmlPullParser p = factory.newPullParser();
                p.setInput(in, "UTF-8");
                StringBuilder out = new StringBuilder();
                int event = p.getEventType();
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        String name = p.getName();
                        if ("t".equals(name)) out.append(p.nextText());
                        else if ("tab".equals(name)) out.append('\t');
                        else if ("br".equals(name) || "cr".equals(name)) out.append('\n');
                    } else if (event == XmlPullParser.END_TAG) {
                        String name = p.getName();
                        if ("p".equals(name) || "tr".equals(name)) out.append('\n');
                        else if ("tc".equals(name)) out.append('\t');
                    }
                    event = p.next();
                }
                return out.toString().replaceAll("[ \t]+\n", "\n").replaceAll("\n{3,}", "\n\n").trim();
            }
        } finally {
            input.delete();
        }
    }

    static Uri toTxt(Context ctx, Uri uri) throws Exception {
        String text = extractText(ctx, uri);
        return FileStore.publishBytes(ctx, text.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "DOCX_текст_" + System.currentTimeMillis() + ".txt", "text/plain", null);
    }

    static Uri toPdf(Context ctx, Uri uri) throws Exception {
        return TextTools.publishTextPdf(ctx, extractText(ctx, uri), "DOCX_в_PDF_" + System.currentTimeMillis() + ".pdf");
    }
}
