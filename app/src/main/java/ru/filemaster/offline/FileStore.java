package ru.filemaster.offline;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
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
        String suffix = InputSafety.safeTempSuffix(name, fallbackExt);
        long sourceSize = size(context, uri);
        long available = new StatFs(context.getCacheDir().getAbsolutePath()).getAvailableBytes();
        if (!InputSafety.enoughCache(available, sourceSize)) {
            throw new IOException("Недостаточно свободного места для обработки файла. Освободите место и повторите попытку.");
        }
        File out = File.createTempFile("fm_in_", suffix, context.getCacheDir());
        boolean ok = false;
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) throw new IOException("Не удалось открыть файл");
            copy(in, os);
            ok = true;
            return out;
        } finally {
            if (!ok) out.delete();
        }
    }

    static Uri publishFile(Context context, File file, String displayName, String mime, String subfolder) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = baseValues(displayName, mime, subfolder);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Не удалось создать файл в Загрузках");
        boolean ok = false;
        try (InputStream in = new FileInputStream(file); OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) throw new IOException("Не удалось открыть выходной файл");
            copy(in, out);
            finishPending(resolver, uri);
            RecentStore.add(context, uri, displayName, mime);
            recordCompletedOutput(context);
            ok = true;
            return uri;
        } finally {
            if (!ok) {
                try { resolver.delete(uri, null, null); } catch (Exception ignored) {}
            }
        }
    }

    static Uri publishBytes(Context context, byte[] data, String displayName, String mime, String subfolder) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = baseValues(displayName, mime, subfolder);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Не удалось создать файл");
        boolean ok = false;
        try (OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) throw new IOException("Не удалось открыть выходной файл");
            out.write(data);
            finishPending(resolver, uri);
            RecentStore.add(context, uri, displayName, mime);
            recordCompletedOutput(context);
            ok = true;
            return uri;
        } finally {
            if (!ok) {
                try { resolver.delete(uri, null, null); } catch (Exception ignored) {}
            }
        }
    }

    private static void recordCompletedOutput(Context context) {
        Context applicationContext = context.getApplicationContext();
        if (applicationContext instanceof Application) {
            AdsManager.get((Application) applicationContext).recordOutputCreated();
        }
    }

    private static ContentValues baseValues(String displayName, String mime, String subfolder) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        String relative = Environment.DIRECTORY_DOWNLOADS + "/ФайлМастер";
        String safe = InputSafety.safeSubfolder(subfolder);
        if (safe != null) relative += "/" + safe;
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, relative);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        return values;
    }

    private static void finishPending(ContentResolver resolver, Uri uri) {
        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(uri, done, null, null);
    }

    static String displayName(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String value = c.getString(idx);
                    if (value != null && !value.isBlank()) return value;
                }
            }
        } catch (Exception ignored) {}
        String last = uri.getLastPathSegment();
        return last == null ? "file" : last;
    }

    static long size(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !c.isNull(idx)) return c.getLong(idx);
            }
        } catch (Exception ignored) {}
        return -1L;
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
    }
}
