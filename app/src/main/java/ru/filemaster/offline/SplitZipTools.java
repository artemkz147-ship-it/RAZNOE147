package ru.filemaster.offline;

import android.content.Context;
import android.net.Uri;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

final class SplitZipTools {
    private SplitZipTools() {}

    static int create(Context ctx, List<Uri> uris, int partSizeMb) throws Exception {
        if (uris == null || uris.isEmpty()) throw new IllegalArgumentException("Не выбраны файлы");
        if (partSizeMb < 1 || partSizeMb > 2048) throw new IllegalArgumentException("Размер части: от 1 до 2048 МБ");
        File dir = new File(ctx.getCacheDir(), "split_create_" + System.nanoTime());
        if (!dir.mkdirs()) throw new IllegalStateException("Не удалось создать временную папку");
        List<File> inputs = new ArrayList<>();
        try {
            for (int i = 0; i < uris.size(); i++) {
                Uri uri = uris.get(i);
                String name = uniqueName(dir, safeName(FileStore.displayName(ctx, uri)), i);
                File f = new File(dir, name);
                try (InputStream in = ctx.getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(f)) {
                    if (in == null) throw new IllegalArgumentException("Не удалось открыть " + name);
                    FileStore.copy(in, out);
                }
                inputs.add(f);
            }
            File main = new File(dir, "Архив.zip");
            ZipFile zip = new ZipFile(main);
            zip.createSplitZipFile(inputs, new ZipParameters(), true, partSizeMb * 1024L * 1024L);
            File[] parts = dir.listFiles((d, name) -> name.matches("(?i)Архив\\.z\\d{2,}|Архив\\.zip"));
            if (parts == null || parts.length < 2) throw new IllegalStateException("Split ZIP не был создан");
            java.util.Arrays.sort(parts, java.util.Comparator.comparing(File::getName));
            String folder = "Split_ZIP_" + System.currentTimeMillis();
            int count = 0;
            for (File part : parts) {
                FileStore.publishFile(ctx, part, part.getName(), "application/zip", folder);
                count++;
            }
            return count;
        } finally { deleteTree(dir); }
    }

    static Uri merge(Context ctx, List<Uri> parts) throws Exception {
        if (parts == null || parts.size() < 2) throw new IllegalArgumentException("Выберите .zip и все части .z01/.z02/…");
        File dir = new File(ctx.getCacheDir(), "split_merge_" + System.nanoTime());
        if (!dir.mkdirs()) throw new IllegalStateException("Не удалось создать временную папку");
        File main = null;
        try {
            for (int i = 0; i < parts.size(); i++) {
                Uri uri = parts.get(i);
                String display = safeName(FileStore.displayName(ctx, uri));
                File f = new File(dir, display);
                try (InputStream in = ctx.getContentResolver().openInputStream(uri); FileOutputStream out = new FileOutputStream(f)) {
                    if (in == null) throw new IllegalArgumentException("Не удалось открыть часть " + display);
                    FileStore.copy(in, out);
                }
                if (display.toLowerCase().endsWith(".zip")) main = f;
            }
            if (main == null) throw new IllegalArgumentException("Среди выбранных частей нет основного файла .zip");
            ZipFile split = new ZipFile(main);
            if (!split.isSplitArchive()) throw new IllegalArgumentException("Выбранный ZIP не помечен как многотомный");
            File merged = new File(dir, "Объединённый.zip");
            split.mergeSplitFiles(merged);
            return FileStore.publishFile(ctx, merged, "Объединённый_ZIP_" + System.currentTimeMillis() + ".zip", "application/zip", null);
        } finally { deleteTree(dir); }
    }

    private static String safeName(String value) {
        String s = value == null || value.isBlank() ? "file" : value;
        return s.replace('\\', '_').replace('/', '_').replace(':', '_');
    }

    private static String uniqueName(File dir, String name, int index) {
        if (!new File(dir, name).exists()) return name;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        return base + "_" + (index + 1) + ext;
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] c = f.listFiles(); if (c != null) for (File x : c) deleteTree(x);
        }
        f.delete();
    }
}
