package ru.filemaster.offline;

import android.content.Context;
import android.net.Uri;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class SheetExtraTools {
    private SheetExtraTools() {}

    static int allSheetsToCsv(Context ctx, Uri uri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".xlsx");
        String folder = "XLSX_все_листы_" + System.currentTimeMillis();
        int count = 0;
        try (ZipFile zip = new ZipFile(input)) {
            List<String> shared = readSharedStrings(zip);
            List<ZipEntry> sheets = sheetEntries(zip);
            if (sheets.isEmpty()) throw new IllegalArgumentException("В XLSX не найдены листы");
            int n = 1;
            for (ZipEntry entry : sheets) {
                String csv = parseSheet(zip, entry, shared, true);
                FileStore.publishBytes(ctx, ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8),
                        String.format(Locale.ROOT, "Лист_%02d.csv", n++), "text/csv", folder);
                count++;
            }
        } finally { input.delete(); }
        return count;
    }

    static Uri allSheetsToPdf(Context ctx, Uri uri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".xlsx");
        try (ZipFile zip = new ZipFile(input)) {
            List<String> shared = readSharedStrings(zip);
            List<ZipEntry> sheets = sheetEntries(zip);
            if (sheets.isEmpty()) throw new IllegalArgumentException("В XLSX не найдены листы");
            StringBuilder text = new StringBuilder();
            int n = 1;
            for (ZipEntry entry : sheets) {
                if (text.length() > 0) text.append("\n\n");
                text.append("===== ЛИСТ ").append(n++).append(" =====\n");
                text.append(parseSheet(zip, entry, shared, true).replace(';', '\t'));
            }
            return TextTools.publishTextPdf(ctx, text.toString(), "XLSX_все_листы_" + System.currentTimeMillis() + ".pdf");
        } finally { input.delete(); }
    }

    static Uri workbookReport(Context ctx, Uri uri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".xlsx");
        try (ZipFile zip = new ZipFile(input)) {
            List<String> shared = readSharedStrings(zip);
            List<ZipEntry> sheets = sheetEntries(zip);
            StringBuilder report = new StringBuilder("Отчёт XLSX\nФайл: ")
                    .append(FileStore.displayName(ctx, uri)).append("\nЛистов: ").append(sheets.size()).append("\n\n");
            int i = 1;
            for (ZipEntry entry : sheets) {
                SheetStats s = stats(zip, entry, shared);
                report.append("Лист ").append(i++).append(": строк ").append(s.rows)
                        .append(", ячеек ").append(s.cells).append(", формул ").append(s.formulas).append('\n');
            }
            return FileStore.publishBytes(ctx, report.toString().getBytes(StandardCharsets.UTF_8),
                    "Отчёт_XLSX_" + System.currentTimeMillis() + ".txt", "text/plain", null);
        } finally { input.delete(); }
    }

    private static List<ZipEntry> sheetEntries(ZipFile zip) {
        List<ZipEntry> out = new ArrayList<>();
        Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            String n = e.getName();
            if (n.matches("xl/worksheets/sheet\\d+\\.xml")) out.add(e);
        }
        out.sort(Comparator.comparingInt(e -> sheetNumber(e.getName())));
        return out;
    }

    private static int sheetNumber(String name) {
        try {
            int a = name.lastIndexOf("sheet") + 5, b = name.lastIndexOf(".xml");
            return Integer.parseInt(name.substring(a, b));
        } catch (Exception e) { return Integer.MAX_VALUE; }
    }

    private static String parseSheet(ZipFile zip, ZipEntry entry, List<String> shared, boolean formulas) throws Exception {
        XmlPullParserFactory f = XmlPullParserFactory.newInstance(); f.setNamespaceAware(true);
        XmlPullParser p = f.newPullParser();
        try (InputStream in = zip.getInputStream(entry)) {
            p.setInput(in, "UTF-8");
            StringBuilder csv = new StringBuilder();
            List<String> row = null;
            String cellType = null, cellRef = null, value = "", formula = "";
            int event = p.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String n = p.getName();
                    if ("row".equals(n)) row = new ArrayList<>();
                    else if ("c".equals(n)) { cellType = p.getAttributeValue(null, "t"); cellRef = p.getAttributeValue(null, "r"); value = ""; formula = ""; }
                    else if ("f".equals(n) && row != null) formula = p.nextText();
                    else if (("v".equals(n) || "t".equals(n)) && row != null) value = p.nextText();
                } else if (event == XmlPullParser.END_TAG) {
                    String n = p.getName();
                    if ("c".equals(n) && row != null) {
                        int col = refColumn(cellRef); while (row.size() < col - 1) row.add("");
                        String out = value;
                        if ("s".equals(cellType)) {
                            try { int idx = Integer.parseInt(value); out = idx >= 0 && idx < shared.size() ? shared.get(idx) : value; } catch (Exception ignored) {}
                        }
                        if (formulas && formula != null && !formula.isBlank()) out = "=" + formula + (out == null || out.isBlank() ? "" : "  [" + out + "]");
                        row.add(out == null ? "" : out);
                    } else if ("row".equals(n) && row != null) {
                        for (int i = 0; i < row.size(); i++) { if (i > 0) csv.append(';'); csv.append(csvQuote(row.get(i))); }
                        csv.append('\n'); row = null;
                    }
                }
                event = p.next();
            }
            return csv.toString();
        }
    }

    private static SheetStats stats(ZipFile zip, ZipEntry entry, List<String> shared) throws Exception {
        String raw = parseSheet(zip, entry, shared, true);
        int rows = 0, cells = 0, formulas = 0;
        for (String line : raw.split("\n")) {
            if (line.isEmpty()) continue; rows++;
            String[] parts = line.split(";", -1); cells += parts.length;
            for (String p : parts) if (p.startsWith("=")) formulas++;
        }
        return new SheetStats(rows, cells, formulas);
    }

    private static List<String> readSharedStrings(ZipFile zip) throws Exception {
        List<String> list = new ArrayList<>(); ZipEntry e = zip.getEntry("xl/sharedStrings.xml"); if (e == null) return list;
        XmlPullParserFactory f = XmlPullParserFactory.newInstance(); f.setNamespaceAware(true); XmlPullParser p = f.newPullParser();
        try (InputStream in = zip.getInputStream(e)) {
            p.setInput(in, "UTF-8"); StringBuilder current = null; int event = p.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "si".equals(p.getName())) current = new StringBuilder();
                else if (event == XmlPullParser.START_TAG && "t".equals(p.getName()) && current != null) current.append(p.nextText());
                else if (event == XmlPullParser.END_TAG && "si".equals(p.getName()) && current != null) { list.add(current.toString()); current = null; }
                event = p.next();
            }
        }
        return list;
    }

    private static int refColumn(String ref) {
        if (ref == null || ref.isEmpty()) return 1; int n = 0;
        for (int i = 0; i < ref.length(); i++) { char c = ref.charAt(i); if (c >= 'A' && c <= 'Z') n = n * 26 + (c - 'A' + 1); else if (c >= 'a' && c <= 'z') n = n * 26 + (c - 'a' + 1); else break; }
        return Math.max(1, n);
    }

    private static String csvQuote(String s) {
        if (s == null) return "";
        if (s.indexOf(';') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private record SheetStats(int rows, int cells, int formulas) {}
}
