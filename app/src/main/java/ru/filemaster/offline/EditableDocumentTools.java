package ru.filemaster.offline;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class EditableDocumentTools {
    private EditableDocumentTools() {}

    static String read(Context ctx, Uri uri) throws Exception {
        String name = FileStore.displayName(ctx, uri).toLowerCase();
        if (name.endsWith(".docx")) return DocxTools.extractText(ctx, uri);
        if (name.endsWith(".odt")) return OpenDocumentTools.extractOdtText(ctx, uri);
        return TextTools.readTextLike(ctx, uri);
    }

    static Uri saveDocx(Context ctx, String text) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos)) {
            put(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
            put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
            StringBuilder body = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
            for (String line : normalize(text).split("\n", -1)) body.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(xml(line)).append("</w:t></w:r></w:p>");
            body.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr></w:body></w:document>");
            put(zip, "word/document.xml", body.toString());
        }
        return FileStore.publishBytes(ctx, bos.toByteArray(), "Отредактированный_документ_" + System.currentTimeMillis() + ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", null);
    }

    static Uri saveOdt(Context ctx, String text) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bos)) {
            byte[] mime = "application/vnd.oasis.opendocument.text".getBytes(StandardCharsets.US_ASCII);
            CRC32 crc = new CRC32(); crc.update(mime);
            ZipEntry m = new ZipEntry("mimetype"); m.setMethod(ZipEntry.STORED); m.setSize(mime.length); m.setCompressedSize(mime.length); m.setCrc(crc.getValue()); zip.putNextEntry(m); zip.write(mime); zip.closeEntry();
            StringBuilder content = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><office:document-content xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" office:version=\"1.2\"><office:body><office:text>");
            for (String line : normalize(text).split("\n", -1)) content.append("<text:p>").append(xml(line)).append("</text:p>");
            content.append("</office:text></office:body></office:document-content>");
            put(zip, "content.xml", content.toString());
            put(zip, "META-INF/manifest.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" manifest:version=\"1.2\"><manifest:file-entry manifest:full-path=\"/\" manifest:media-type=\"application/vnd.oasis.opendocument.text\"/><manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\"/></manifest:manifest>");
        }
        return FileStore.publishBytes(ctx, bos.toByteArray(), "Отредактированный_документ_" + System.currentTimeMillis() + ".odt", "application/vnd.oasis.opendocument.text", null);
    }

    static Uri saveTxt(Context ctx, String text) throws Exception {
        return FileStore.publishBytes(ctx, normalize(text).getBytes(StandardCharsets.UTF_8), "Отредактированный_текст_" + System.currentTimeMillis() + ".txt", "text/plain", null);
    }

    static Uri savePdf(Context ctx, String text) throws Exception {
        return TextTools.publishTextPdf(ctx, normalize(text), "Отредактированный_текст_" + System.currentTimeMillis() + ".pdf");
    }

    private static String normalize(String s) { return (s == null ? "" : s).replace("\r\n", "\n").replace('\r', '\n'); }
    private static void put(ZipOutputStream zip, String path, String value) throws Exception { zip.putNextEntry(new ZipEntry(path)); zip.write(value.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
    private static String xml(String s) { return (s == null ? "" : s).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }
}
