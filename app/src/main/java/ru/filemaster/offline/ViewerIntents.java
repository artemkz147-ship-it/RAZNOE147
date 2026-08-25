package ru.filemaster.offline;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
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

    static void openOutputFolder(Activity activity) {
        Uri folder = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download/ФайлМастер");
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
            Toast.makeText(activity, "Папка: Загрузки / ФайлМастер", Toast.LENGTH_LONG).show();
        }
    }

    static void start(Activity activity, Class<?> cls, String key, Uri uri) {
        Intent i = new Intent(activity, cls);
        i.putExtra(key, uri.toString());
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(i);
    }
}
