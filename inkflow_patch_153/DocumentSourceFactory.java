package app.inkflow.reader;

import android.content.Context;
import android.net.Uri;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Readers for office/text document formats shown through the normal book reader UI. */
public final class DocumentSourceFactory {
    private DocumentSourceFactory() {}

    public static boolean isDocumentFormat(String name) {
        String l = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return l.endsWith(".docx") || l.endsWith(".docm") || l.endsWith(".doc") ||
                l.endsWith(".rtf") || l.endsWith(".odt");
    }

    public static BookContent open(Context c, Uri uri, String name) throws Exception {
        String l = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (l.endsWith(".docx") || l.endsWith(".docm")) return parseDocx(c, uri, name);
        if (l.endsWith(".doc")) return parseDoc(c, uri, name);
        if (l.endsWith(".rtf")) return parseRtf(c, uri, name);
        if (l.endsWith(".odt")) return parseOdt(c, uri, name);
        throw new IOException("Неподдерживаемый документ: " + name);
    }

    private static BookContent parseDocx(Context c, Uri uri, String fallback) throws Exception {
        File file = copy(c, uri, ".docx");
        try (ZipFile z = new ZipFile(file)) {
            ZipEntry entry = z.getEntry("word/document.xml");
            if (entry == null) throw new IOException("DOCX повреждён: word/document.xml не найден");
            Document doc;
            try (InputStream in = z.getInputStream(entry)) {
                DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
                f.setNamespaceAware(false);
                doc = f.newDocumentBuilder().parse(in);
            }
            String title = stripExt(fallback);
            StringBuilder html = new StringBuilder("<h1>").append(esc(title)).append("</h1>");
            NodeList ps = doc.getElementsByTagName("w:p");
            for (int i = 0; i < ps.getLength(); i++) {
                Element p = (Element) ps.item(i);
                String text = paragraphText(p).trim();
                if (text.isEmpty()) continue;
                String style = paragraphStyle(p).toLowerCase(Locale.ROOT);
                if (style.contains("title") || style.contains("heading1") || style.contains("заголовок1"))
                    html.append("<h2>").append(esc(text)).append("</h2>");
                else if (style.contains("heading") || style.contains("заголовок"))
                    html.append("<h3>").append(esc(text)).append("</h3>");
                else html.append("<p>").append(esc(text)).append("</p>");
            }
            return new BookContent(title, html.toString());
        }
    }

    private static String paragraphText(Node node) {
        StringBuilder out = new StringBuilder();
        appendDocxText(node, out);
        return out.toString();
    }

    private static void appendDocxText(Node node, StringBuilder out) {
        String n = node.getNodeName();
        if ("w:t".equals(n)) out.append(node.getTextContent());
        else if ("w:tab".equals(n)) out.append('\t');
        else if ("w:br".equals(n) || "w:cr".equals(n)) out.append('\n');
        NodeList ch = node.getChildNodes();
        for (int i = 0; i < ch.getLength(); i++) appendDocxText(ch.item(i), out);
    }

    private static String paragraphStyle(Element p) {
        NodeList styles = p.getElementsByTagName("w:pStyle");
        if (styles.getLength() == 0) return "";
        Element e = (Element) styles.item(0);
        String v = e.getAttribute("w:val");
        if (v == null || v.isEmpty()) v = e.getAttribute("val");
        return v == null ? "" : v;
    }

    private static BookContent parseDoc(Context c, Uri uri, String fallback) throws Exception {
        String title = stripExt(fallback);
        StringBuilder html = new StringBuilder("<h1>").append(esc(title)).append("</h1>");
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             HWPFDocument doc = new HWPFDocument(in);
             WordExtractor extractor = new WordExtractor(doc)) {
            String[] paragraphs = extractor.getParagraphText();
            for (String p : paragraphs) {
                String t = p == null ? "" : p.replace('\r', ' ').trim();
                if (!t.isEmpty()) html.append("<p>").append(esc(t)).append("</p>");
            }
        }
        return new BookContent(title, html.toString());
    }

    private static BookContent parseOdt(Context c, Uri uri, String fallback) throws Exception {
        File file = copy(c, uri, ".odt");
        try (ZipFile z = new ZipFile(file)) {
            ZipEntry entry = z.getEntry("content.xml");
            if (entry == null) throw new IOException("ODT повреждён: content.xml не найден");
            Document doc;
            try (InputStream in = z.getInputStream(entry)) {
                DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
                f.setNamespaceAware(false);
                doc = f.newDocumentBuilder().parse(in);
            }
            String title = stripExt(fallback);
            StringBuilder html = new StringBuilder("<h1>").append(esc(title)).append("</h1>");
            NodeList all = doc.getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                Element e = (Element) all.item(i);
                String n = e.getNodeName();
                if (!("text:p".equals(n) || "text:h".equals(n))) continue;
                String t = e.getTextContent() == null ? "" : e.getTextContent().trim();
                if (t.isEmpty()) continue;
                if ("text:h".equals(n)) html.append("<h2>").append(esc(t)).append("</h2>");
                else html.append("<p>").append(esc(t)).append("</p>");
            }
            return new BookContent(title, html.toString());
        }
    }

    private static BookContent parseRtf(Context c, Uri uri, String fallback) throws Exception {
        byte[] bytes;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) { bytes = readAll(in); }
        String raw = new String(bytes, StandardCharsets.ISO_8859_1);
        int codepage = 1251;
        Matcher cp = Pattern.compile("\\\\ansicpg(\\d+)").matcher(raw);
        if (cp.find()) try { codepage = Integer.parseInt(cp.group(1)); } catch (Exception ignored) {}
        Charset charset;
        try { charset = Charset.forName(codepage == 65001 ? "UTF-8" : "windows-" + codepage); }
        catch (Exception e) { charset = Charset.forName("windows-1251"); }
        String plain = rtfToText(raw, charset);
        String title = stripExt(fallback);
        StringBuilder html = new StringBuilder("<h1>").append(esc(title)).append("</h1>");
        for (String p : plain.replace("\r", "").split("\n+")) {
            String t = p.trim();
            if (!t.isEmpty()) html.append("<p>").append(esc(t)).append("</p>");
        }
        return new BookContent(title, html.toString());
    }

    private static String rtfToText(String s, Charset charset) {
        StringBuilder out = new StringBuilder();
        ArrayDeque<Boolean> skipStack = new ArrayDeque<>();
        boolean skip = false;
        int uc = 1;
        for (int i = 0; i < s.length();) {
            char ch = s.charAt(i);
            if (ch == '{') { skipStack.push(skip); i++; continue; }
            if (ch == '}') { if (!skipStack.isEmpty()) skip = skipStack.pop(); i++; continue; }
            if (ch != '\\') { if (!skip && ch != '\r' && ch != '\n') out.append(ch); i++; continue; }
            i++;
            if (i >= s.length()) break;
            char c = s.charAt(i);
            if (c == '\\' || c == '{' || c == '}') { if (!skip) out.append(c); i++; continue; }
            if (c == '\'') {
                if (i + 2 < s.length()) {
                    try {
                        int b = Integer.parseInt(s.substring(i + 1, i + 3), 16);
                        if (!skip) out.append(new String(new byte[]{(byte)b}, charset));
                    } catch (Exception ignored) {}
                    i += 3; continue;
                }
            }
            int wordStart = i;
            while (i < s.length() && Character.isLetter(s.charAt(i))) i++;
            String word = s.substring(wordStart, i);
            int sign = 1;
            if (i < s.length() && s.charAt(i) == '-') { sign = -1; i++; }
            int numStart = i;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            Integer num = null;
            if (i > numStart) try { num = Integer.parseInt(s.substring(numStart, i)) * sign; } catch (Exception ignored) {}
            if (i < s.length() && s.charAt(i) == ' ') i++;

            if (word.equals("fonttbl") || word.equals("colortbl") || word.equals("stylesheet") ||
                    word.equals("info") || word.equals("pict") || word.equals("object") ||
                    word.equals("header") || word.equals("footer")) { skip = true; continue; }
            if (skip) continue;
            if (word.equals("par") || word.equals("line")) out.append('\n');
            else if (word.equals("tab")) out.append('\t');
            else if (word.equals("emdash")) out.append('—');
            else if (word.equals("endash")) out.append('–');
            else if (word.equals("bullet")) out.append('•');
            else if (word.equals("lquote") || word.equals("rquote")) out.append('’');
            else if (word.equals("ldblquote") || word.equals("rdblquote")) out.append('”');
            else if (word.equals("uc") && num != null) uc = Math.max(0, num);
            else if (word.equals("u") && num != null) {
                int v = num < 0 ? num + 65536 : num;
                out.append((char) v);
                int skipped = 0;
                while (i < s.length() && skipped < uc && s.charAt(i) != '\\' && s.charAt(i) != '{' && s.charAt(i) != '}') { i++; skipped++; }
            }
        }
        return out.toString().replaceAll("[ \\t]+\\n", "\n").trim();
    }

    private static File copy(Context c, Uri uri, String ext) throws IOException {
        File f = new File(c.getCacheDir(), "doc_" + Math.abs(uri.toString().hashCode()) + ext);
        try (InputStream in = c.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(f)) {
            if (in == null) throw new IOException("Не удалось открыть документ");
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return f;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        if (in == null) throw new IOException("Не удалось открыть документ");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        return out.toByteArray();
    }

    private static String stripExt(String s) {
        if (s == null) return "Документ";
        int i = s.lastIndexOf('.');
        return i > 0 ? s.substring(0, i) : s;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
