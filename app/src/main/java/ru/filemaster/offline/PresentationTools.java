package ru.filemaster.offline;

import android.content.Context;
import android.net.Uri;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class PresentationTools {
    private PresentationTools() {}

    static String extractPptxText(Context ctx, Uri uri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".pptx");
        try (ZipFile zip = new ZipFile(input)) {
            List<String> names = new ArrayList<>();
            zip.stream().forEach(e -> {
                String n = e.getName();
                if (n.matches("ppt/slides/slide\\d+\\.xml")) names.add(n);
            });
            Collections.sort(names, (a, b) -> Integer.compare(slideNumber(a), slideNumber(b)));
            if (names.isEmpty()) throw new IllegalArgumentException("В PPTX не найдены слайды");
            StringBuilder out = new StringBuilder();
            int slide = 1;
            for (String name : names) {
                out.append("--- Слайд ").append(slide++).append(" ---\n");
                ZipEntry e = zip.getEntry(name);
                try (InputStream in = zip.getInputStream(e)) {
                    XmlPullParserFactory f = XmlPullParserFactory.newInstance();
                    f.setNamespaceAware(true);
                    XmlPullParser p = f.newPullParser();
                    p.setInput(in, "UTF-8");
                    int event = p.getEventType();
                    while (event != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.START_TAG && "t".equals(p.getName())) out.append(p.nextText());
                        else if (event == XmlPullParser.END_TAG && "p".equals(p.getName())) out.append('\n');
                        event = p.next();
                    }
                }
                out.append('\n');
            }
            return out.toString().replaceAll("\n{3,}", "\n\n").trim();
        } finally { input.delete(); }
    }

    static Uri toTxt(Context ctx, Uri uri) throws Exception {
        return FileStore.publishBytes(ctx, extractPptxText(ctx, uri).getBytes(StandardCharsets.UTF_8),
                "PPTX_текст_" + System.currentTimeMillis() + ".txt", "text/plain", null);
    }

    static Uri toPdf(Context ctx, Uri uri) throws Exception {
        return TextTools.publishTextPdf(ctx, extractPptxText(ctx, uri), "PPTX_в_PDF_" + System.currentTimeMillis() + ".pdf");
    }

    private static int slideNumber(String name) {
        try {
            String n = name.substring(name.lastIndexOf("slide") + 5, name.lastIndexOf('.'));
            return Integer.parseInt(n);
        } catch (Exception e) { return Integer.MAX_VALUE; }
    }
}
