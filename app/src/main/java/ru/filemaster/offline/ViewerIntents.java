package ru.filemaster.offline;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

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

    static void start(Activity activity, Class<?> cls, String key, Uri uri) {
        Intent i = new Intent(activity, cls);
        i.putExtra(key, uri.toString());
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(i);
    }
}
