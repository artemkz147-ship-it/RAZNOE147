package ru.filemaster.offline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.ImageType;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class PdfOfficeTools {
    private PdfOfficeTools() {}

    static Uri toDocx(Context ctx, Uri uri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".pdf");
        try (PDDocument doc = PDDocument.load(input)) {
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder body = new StringBuilder();
            for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(doc).replace("\r\n", "\n").replace('\r', '\n');
                if (page > 1) body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>");
                for (String line : text.split("\n", -1)) {
                    body.append("<w:p><w:r><w:t xml:space=\"preserve\">")
                            .append(xml(line)).append("</w:t></w:r></w:p>");
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bos)) {
                put(zip, "[Content_Types].xml",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                        "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
                        "</Types>");
                put(zip, "_rels/.rels",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
                        "</Relationships>");
                put(zip, "word/document.xml",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                        "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" +
                        body +
                        "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\"/></w:sectPr>" +
                        "</w:body></w:document>");
            }
            return FileStore.publishBytes(ctx, bos.toByteArray(), "PDF_в_DOCX_" + System.currentTimeMillis() + ".docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", null);
        } finally { input.delete(); }
    }

    static Uri toXlsx(Context ctx, Uri uri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".pdf");
        try (PDDocument doc = PDDocument.load(input)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<List<String>> rows = new ArrayList<>();
            for (int page = 1; page <= doc.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                rows.add(List.of("Страница " + page));
                String text = stripper.getText(doc).replace("\r\n", "\n").replace('\r', '\n');
                for (String line : text.split("\n")) {
                    if (line.isBlank()) continue;
                    String[] cells = line.trim().split("(?:\\t+|\\s{2,})");
                    List<String> row = new ArrayList<>();
                    for (String cell : cells) row.add(cell.trim());
                    rows.add(row);
                }
                rows.add(new ArrayList<>());
            }
            return publishXlsx(ctx, rows, "PDF_в_XLSX_" + System.currentTimeMillis() + ".xlsx");
        } finally { input.delete(); }
    }

    static Uri toPptxVisual(Context ctx, Uri uri) throws Exception {
        File input = FileStore.copyUriToTemp(ctx, uri, ".pdf");
        try (PDDocument doc = PDDocument.load(input)) {
            if (doc.getNumberOfPages() == 0) throw new IllegalArgumentException("PDF без страниц");
            PDFRenderer renderer = new PDFRenderer(doc);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bos)) {
                StringBuilder types = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"jpg\" ContentType=\"image/jpeg\"/><Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>");
                StringBuilder ids = new StringBuilder();
                StringBuilder rels = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
                for (int i = 1; i <= doc.getNumberOfPages(); i++) {
                    types.append("<Override PartName=\"/ppt/slides/slide").append(i).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>");
                    ids.append("<p:sldId id=\"").append(255 + i).append("\" r:id=\"rId").append(i).append("\"/>");
                    rels.append("<Relationship Id=\"rId").append(i).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide").append(i).append(".xml\"/>");
                }
                types.append("</Types>");
                rels.append("</Relationships>");
                put(zip, "[Content_Types].xml", types.toString());
                put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"ppt/presentation.xml\"/></Relationships>");
                put(zip, "ppt/presentation.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><p:presentation xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"><p:sldIdLst>" + ids + "</p:sldIdLst><p:sldSz cx=\"12192000\" cy=\"6858000\" type=\"screen16x9\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>");
                put(zip, "ppt/_rels/presentation.xml.rels", rels.toString());

                for (int i = 0; i < doc.getNumberOfPages(); i++) {
                    Bitmap page = renderer.renderImageWithDPI(i, 120, ImageType.RGB);
                    Bitmap slide = Bitmap.createBitmap(1600, 900, Bitmap.Config.RGB_565);
                    Canvas canvas = new Canvas(slide);
                    canvas.drawColor(Color.WHITE);
                    float scale = Math.min(1600f / page.getWidth(), 900f / page.getHeight());
                    int w = Math.max(1, Math.round(page.getWidth() * scale));
                    int h = Math.max(1, Math.round(page.getHeight() * scale));
                    android.graphics.Rect dst = new android.graphics.Rect((1600 - w) / 2, (900 - h) / 2, (1600 + w) / 2, (900 + h) / 2);
                    canvas.drawBitmap(page, null, dst, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
                    page.recycle();
                    ByteArrayOutputStream image = new ByteArrayOutputStream();
                    slide.compress(Bitmap.CompressFormat.JPEG, 90, image);
                    slide.recycle();
                    putBytes(zip, "ppt/media/image" + (i + 1) + ".jpg", image.toByteArray());
                    put(zip, "ppt/slides/slide" + (i + 1) + ".xml", slideXml(i + 1));
                    put(zip, "ppt/slides/_rels/slide" + (i + 1) + ".xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/image" + (i + 1) + ".jpg\"/></Relationships>");
                }
            }
            return FileStore.publishBytes(ctx, bos.toByteArray(), "PDF_в_PPTX_" + System.currentTimeMillis() + ".pptx",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation", null);
        } finally { input.delete(); }
    }

    private static Uri publishXlsx(Context ctx, List<List<String>> rows, String name) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos)) {
            put(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            put(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"PDF\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            put(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            for (int r = 0; r < rows.size(); r++) {
                sheet.append("<row r=\"").append(r + 1).append("\">");
                List<String> row = rows.get(r);
                for (int c = 0; c < row.size(); c++) {
                    String ref = columnName(c + 1) + (r + 1);
                    sheet.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(row.get(c))).append("</t></is></c>");
                }
                sheet.append("</row>");
            }
            sheet.append("</sheetData></worksheet>");
            put(zip, "xl/worksheets/sheet1.xml", sheet.toString());
        }
        return FileStore.publishBytes(ctx, bos.toByteArray(), name, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", null);
    }

    private static String slideXml(int n) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<p:sld xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"><p:cSld><p:spTree>" +
                "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>" +
                "<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>" +
                "<p:pic><p:nvPicPr><p:cNvPr id=\"2\" name=\"Страница " + n + "\"/><p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>" +
                "<p:blipFill><a:blip r:embed=\"rId1\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>" +
                "<p:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"12192000\" cy=\"6858000\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>" +
                "</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>";
    }

    private static void put(ZipOutputStream zip, String name, String text) throws Exception {
        putBytes(zip, name, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void putBytes(ZipOutputStream zip, String name, byte[] data) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static String xml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String columnName(int n) {
        StringBuilder b = new StringBuilder();
        while (n > 0) { n--; b.insert(0, (char) ('A' + n % 26)); n /= 26; }
        return b.toString();
    }
}
