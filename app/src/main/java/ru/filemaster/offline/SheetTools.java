package ru.filemaster.offline;

import android.content.Context;
import android.net.Uri;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

final class SheetTools {
    private SheetTools() {}

    static Uri xlsxToCsv(Context ctx, Uri uri) throws Exception {
        String csv = xlsxToCsvString(ctx, uri);
        byte[] data = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        return FileStore.publishBytes(ctx, data, "Таблица_" + System.currentTimeMillis() + ".csv", "text/csv", null);
    }

    static Uri xlsxToPdf(Context ctx, Uri uri) throws Exception {
        String csv = xlsxToCsvString(ctx, uri).replace(';', '\t');
        return TextTools.publishTextPdf(ctx, csv, "Таблица_в_PDF_" + System.currentTimeMillis() + ".pdf");
    }

    static Uri csvToXlsx(Context ctx, Uri uri) throws Exception {
        String raw = readUtf8(ctx, uri).replace("\uFEFF", "");
        char sep = detectSeparator(raw);
        List<List<String>> rows = parseCsv(raw, sep);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos)) {
            put(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                    "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                    "</Types>");
            put(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                    "</Relationships>");
            put(zip, "xl/workbook.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                    "<sheets><sheet name=\"Лист1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                    "</Relationships>");
            StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            for (int r = 0; r < rows.size(); r++) {
                sheet.append("<row r=\"").append(r + 1).append("\">");
                List<String> row = rows.get(r);
                for (int c = 0; c < row.size(); c++) {
                    String ref = columnName(c + 1) + (r + 1);
                    sheet.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(xml(row.get(c))).append("</t></is></c>");
                }
                sheet.append("</row>");
            }
            sheet.append("</sheetData></worksheet>");
            put(zip, "xl/worksheets/sheet1.xml", sheet.toString());
        }
        return FileStore.publishBytes(ctx, bos.toByteArray(), "Таблица_" + System.currentTimeMillis() + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", null);
    }

    private static String xlsxToCsvString(Context ctx, Uri uri) throws Exception {
        File file = FileStore.copyUriToTemp(ctx, uri, ".xlsx");
        try (ZipFile zip = new ZipFile(file)) {
            List<String> shared = readSharedStrings(zip);
            ZipEntry sheetEntry = zip.getEntry("xl/worksheets/sheet1.xml");
            if (sheetEntry == null) throw new IllegalArgumentException("В XLSX не найден первый лист");
            XmlPullParserFactory f = XmlPullParserFactory.newInstance();
            f.setNamespaceAware(true);
            XmlPullParser p = f.newPullParser();
            try (InputStream in = zip.getInputStream(sheetEntry)) {
                p.setInput(in, "UTF-8");
                StringBuilder csv = new StringBuilder();
                List<String> row = null;
                String cellType = null;
                String cellRef = null;
                String value = "";
                int event = p.getEventType();
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        String n = p.getName();
                        if ("row".equals(n)) row = new ArrayList<>();
                        else if ("c".equals(n)) {
                            cellType = p.getAttributeValue(null, "t");
                            cellRef = p.getAttributeValue(null, "r");
                            value = "";
                        } else if (("v".equals(n) || "t".equals(n)) && row != null) {
                            value = p.nextText();
                        }
                    } else if (event == XmlPullParser.END_TAG) {
                        String n = p.getName();
                        if ("c".equals(n) && row != null) {
                            int col = refColumn(cellRef);
                            while (row.size() < col - 1) row.add("");
                            String out = value;
                            if ("s".equals(cellType)) {
                                try {
                                    int idx = Integer.parseInt(value);
                                    out = idx >= 0 && idx < shared.size() ? shared.get(idx) : value;
                                } catch (Exception ignored) {}
                            }
                            row.add(out == null ? "" : out);
                        } else if ("row".equals(n) && row != null) {
                            for (int i = 0; i < row.size(); i++) {
                                if (i > 0) csv.append(';');
                                csv.append(csvQuote(row.get(i), ';'));
                            }
                            csv.append('\n');
                            row = null;
                        }
                    }
                    event = p.next();
                }
                return csv.toString();
            }
        } finally {
            file.delete();
        }
    }

    private static List<String> readSharedStrings(ZipFile zip) throws Exception {
        List<String> list = new ArrayList<>();
        ZipEntry e = zip.getEntry("xl/sharedStrings.xml");
        if (e == null) return list;
        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setNamespaceAware(true);
        XmlPullParser p = f.newPullParser();
        try (InputStream in = zip.getInputStream(e)) {
            p.setInput(in, "UTF-8");
            StringBuilder current = null;
            int event = p.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "si".equals(p.getName())) current = new StringBuilder();
                else if (event == XmlPullParser.START_TAG && "t".equals(p.getName()) && current != null) current.append(p.nextText());
                else if (event == XmlPullParser.END_TAG && "si".equals(p.getName()) && current != null) {
                    list.add(current.toString());
                    current = null;
                }
                event = p.next();
            }
        }
        return list;
    }

    private static String readUtf8(Context ctx, Uri uri) throws Exception {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalArgumentException("Не удалось открыть таблицу");
            FileStore.copy(in, out);
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private static char detectSeparator(String raw) {
        String first = raw.split("\\r?\\n", 2)[0];
        int comma = count(first, ','), semi = count(first, ';'), tab = count(first, '\t');
        if (tab >= comma && tab >= semi && tab > 0) return '\t';
        if (semi >= comma && semi > 0) return ';';
        return ',';
    }

    private static int count(String s, char c) { int n = 0; for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++; return n; }

    private static List<List<String>> parseCsv(String raw, char sep) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quotes = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                if (quotes && i + 1 < raw.length() && raw.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else quotes = !quotes;
            } else if (c == sep && !quotes) {
                row.add(cell.toString()); cell.setLength(0);
            } else if ((c == '\n' || c == '\r') && !quotes) {
                if (c == '\r' && i + 1 < raw.length() && raw.charAt(i + 1) == '\n') i++;
                row.add(cell.toString()); cell.setLength(0);
                rows.add(row); row = new ArrayList<>();
            } else cell.append(c);
        }
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString()); rows.add(row); }
        return rows;
    }

    private static void put(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String csvQuote(String s, char sep) {
        if (s.indexOf(sep) >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private static String columnName(int n) {
        StringBuilder b = new StringBuilder();
        while (n > 0) { n--; b.insert(0, (char) ('A' + n % 26)); n /= 26; }
        return b.toString();
    }

    private static int refColumn(String ref) {
        if (ref == null || ref.isEmpty()) return 1;
        int n = 0;
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (c >= 'A' && c <= 'Z') n = n * 26 + (c - 'A' + 1);
            else if (c >= 'a' && c <= 'z') n = n * 26 + (c - 'a' + 1);
            else break;
        }
        return Math.max(1, n);
    }
}
