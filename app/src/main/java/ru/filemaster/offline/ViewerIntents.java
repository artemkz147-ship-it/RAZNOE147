package ru.filemaster.offline;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.widget.Toast;

final class ViewerIntents {
    private ViewerIntents() {}

    static void open(Activity activity, Uri uri) {
        Intent i = new Intent(activity, DokiViewerActivity.class);
        i.setData(uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(i);
    }

    static void share(Activity activity, Uri uri) {
        try {
            String mime = activity.getContentResolver().getType(uri);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mime == null ? "*/*" : mime);
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(i, "Поделиться файлом"));
        } catch (Exception ignored) {}
    }

    static String outputFolderLabel(Activity activity, Uri resultUri) {
        String relative = relativePath(activity, resultUri);
        if (relative == null || relative.isBlank()) return "Загрузки / ФайлМастер";
        String clean = relative.replace('\\', '/');
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length()-1);
        if (clean.startsWith("Download/")) clean = "Загрузки / " + clean.substring("Download/".length()).replace("/", " / ");
        else clean = clean.replace("/", " / ");
        return clean;
    }

    static void openOutputFolder(Activity activity, Uri resultUri) {
        String relative = relativePath(activity, resultUri);
        if (relative == null || relative.isBlank()) relative = "Download/ФайлМастер";
        String clean = relative.replace('\\', '/');
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length()-1);
        String docId = "primary:" + clean;
        Uri folder = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId);
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(folder, DocumentsContract.Document.MIME_TYPE_DIR);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(i);
            return;
        } catch (Exception ignored) {}
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, folder);
            activity.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(activity, "Папка: " + outputFolderLabel(activity, resultUri), Toast.LENGTH_LONG).show();
        }
    }

    private static String relativePath(Activity activity, Uri uri) {
        if (uri == null || !"content".equalsIgnoreCase(uri.getScheme())) return null;
        String[] projection = { MediaStore.MediaColumns.RELATIVE_PATH };
        try (Cursor c = activity.getContentResolver().query(uri, projection, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int index = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH);
                if (index >= 0) return c.getString(index);
            }
        } catch (Exception ignored) {}
        return null;
    }

    static void start(Activity activity, Class<?> cls, String key, Uri uri) {
        Intent i = new Intent(activity, cls);
        i.putExtra(key, uri.toString());
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(i);
    }
}
