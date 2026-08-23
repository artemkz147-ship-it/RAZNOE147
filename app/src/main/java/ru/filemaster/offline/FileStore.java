package ru.filemaster.offline;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class FileStore {
    private FileStore() {}

    static File copyUriToTemp(Context context, Uri uri, String fallbackExt) throws IOException {
        String name = displayName(context, uri);
        String suffix = fallbackExt;
        int dot = name.lastIndexOf('.');
        if (dot >= 0) suffix = name.substring(dot);
        File out = File.createTempFile("fm_in_", suffix, context.getCacheDir());
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new IOException("Не удалось открыть файл");
            copy(in, os);
        }
        return out;
    }

    static Uri publishFile(Context context, File file, String displayName, String mime, String subfolder) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        String relative = Environment.DIRECTORY_DOWNLOADS + "/ФайлМастер";
        if (subfolder != null && !subfolder.isBlank()) relative += "/" + subfolder;
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, relative);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Не удалось создать файл в Загрузках");
        try (InputStream in = new FileInputStream(file); OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) throw new IOException("Не удалось открыть выходной файл");
            copy(in, out);
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(uri, done, null, null);
        return uri;
    }

    static Uri publishBytes(Context context, byte[] data, String displayName, String mime, String subfolder) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        String relative = Environment.DIRECTORY_DOWNLOADS + "/ФайлМастер";
        if (subfolder != null && !subfolder.isBlank()) relative += "/" + subfolder;
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, relative);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Не удалось создать файл");
        try (OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) throw new IOException("Не удалось открыть выходной файл");
            out.write(data);
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(uri, done, null, null);
        return uri;
    }

    static String displayName(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {}
        String last = uri.getLastPathSegment();
        return last == null ? "file" : last;
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
    }
}
