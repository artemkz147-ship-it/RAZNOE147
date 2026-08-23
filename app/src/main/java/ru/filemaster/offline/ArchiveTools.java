package ru.filemaster.offline;

import android.content.Context;
import android.net.Uri;

import com.github.junrar.Junrar;

import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class ArchiveTools {
    private ArchiveTools() {}

    static Uri createZip(Context ctx, List<Uri> uris) throws Exception {
        if (uris.isEmpty()) throw new IllegalArgumentException("Не выбраны файлы");
        File out = File.createTempFile("archive_", ".zip", ctx.getCacheDir());
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
            for (Uri uri : uris) {
                String name = safeName(FileStore.displayName(ctx, uri));
                zip.putNextEntry(new ZipEntry(name));
                try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                    if (in != null) FileStore.copy(in, zip);
                }
                zip.closeEntry();
            }
        }
        try { return FileStore.publishFile(ctx, out, "Архив_" + System.currentTimeMillis() + ".zip", "application/zip", null); }
        finally { out.delete(); }
    }

    static Uri createEncryptedZip(Context ctx, List<Uri> uris, String password) throws Exception {
        if (uris.isEmpty()) throw new IllegalArgumentException("Не выбраны файлы");
        if (password == null || password.length() < 4) throw new IllegalArgumentException("Пароль должен быть минимум 4 символа");
        File out = File.createTempFile("encrypted_", ".zip", ctx.getCacheDir());
        if (out.exists()) out.delete();
        List<File> temps = new ArrayList<>();
        try {
            net.lingala.zip4j.ZipFile zip = new net.lingala.zip4j.ZipFile(out, password.toCharArray());
            for (Uri uri : uris) {
                File temp = FileStore.copyUriToTemp(ctx, uri, ".bin");
                temps.add(temp);
                ZipParameters p = new ZipParameters();
                p.setEncryptFiles(true);
                p.setEncryptionMethod(EncryptionMethod.AES);
                p.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
                p.setFileNameInZip(safeName(FileStore.displayName(ctx, uri)));
                zip.addFile(temp, p);
            }
            return FileStore.publishFile(ctx, out, "Защищённый_архив_" + System.currentTimeMillis() + ".zip", "application/zip", null);
        } finally {
            for (File f : temps) f.delete();
            out.delete();
        }
    }

    static Uri create7z(Context ctx, List<Uri> uris) throws Exception {
        if (uris.isEmpty()) throw new IllegalArgumentException("Не выбраны файлы");
        File out = File.createTempFile("archive_", ".7z", ctx.getCacheDir());
        List<File> temps = new ArrayList<>();
        try (SevenZOutputFile seven = new SevenZOutputFile(out)) {
            for (Uri uri : uris) {
                File temp = FileStore.copyUriToTemp(ctx, uri, ".bin");
                temps.add(temp);
                String name = safeName(FileStore.displayName(ctx, uri));
                SevenZArchiveEntry entry = seven.createArchiveEntry(temp, name);
                seven.putArchiveEntry(entry);
                try (FileInputStream in = new FileInputStream(temp)) {
                    byte[] b = new byte[64 * 1024]; int n;
                    while ((n = in.read(b)) != -1) seven.write(b, 0, n);
                }
                seven.closeArchiveEntry();
            }
        } finally { for (File f : temps) f.delete(); }
        try { return FileStore.publishFile(ctx, out, "Архив_" + System.currentTimeMillis() + ".7z", "application/x-7z-compressed", null); }
        finally { out.delete(); }
    }

    static Uri createTarGz(Context ctx, List<Uri> uris) throws Exception {
        if (uris.isEmpty()) throw new IllegalArgumentException("Не выбраны файлы");
        File out = File.createTempFile("archive_", ".tar.gz", ctx.getCacheDir());
        try (FileOutputStream fos = new FileOutputStream(out);
             GzipCompressorOutputStream gz = new GzipCompressorOutputStream(fos);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gz)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (Uri uri : uris) {
                File temp = FileStore.copyUriToTemp(ctx, uri, ".bin");
                try {
                    TarArchiveEntry e = new TarArchiveEntry(safeName(FileStore.displayName(ctx, uri)));
                    e.setSize(temp.length());
                    tar.putArchiveEntry(e);
                    try (FileInputStream in = new FileInputStream(temp)) { FileStore.copy(in, tar); }
                    tar.closeArchiveEntry();
                } finally { temp.delete(); }
            }
            tar.finish();
        }
        try { return FileStore.publishFile(ctx, out, "Архив_" + System.currentTimeMillis() + ".tar.gz", "application/gzip", null); }
        finally { out.delete(); }
    }

    static int extract(Context ctx, Uri archiveUri) throws Exception {
        String name = FileStore.displayName(ctx, archiveUri).toLowerCase();
        File archive = FileStore.copyUriToTemp(ctx, archiveUri, extension(name));
        String folder = "Распаковано_" + System.currentTimeMillis();
        File extractRoot = new File(ctx.getCacheDir(), "extract_" + System.nanoTime());
        if (!extractRoot.mkdirs()) throw new IOException("Не удалось создать временную папку");
        try {
            if (name.endsWith(".zip")) {
                net.lingala.zip4j.ZipFile z = new net.lingala.zip4j.ZipFile(archive);
                if (z.isEncrypted()) throw new IllegalArgumentException("Архив защищён паролем — выберите «Распаковать ZIP с паролем»");
                z.extractAll(extractRoot.getAbsolutePath());
            } else if (name.endsWith(".7z")) extract7z(archive, extractRoot);
            else if (name.endsWith(".rar")) Junrar.extract(archive, extractRoot);
            else throw new IllegalArgumentException("Поддерживается распаковка ZIP, 7Z и RAR");
            return publishTree(ctx, extractRoot, extractRoot, folder);
        } finally { archive.delete(); deleteTree(extractRoot); }
    }

    static int extractEncryptedZip(Context ctx, Uri archiveUri, String password) throws Exception {
        if (password == null || password.isEmpty()) throw new IllegalArgumentException("Введите пароль");
        File archive = FileStore.copyUriToTemp(ctx, archiveUri, ".zip");
        String folder = "Распаковано_" + System.currentTimeMillis();
        File root = new File(ctx.getCacheDir(), "extract_" + System.nanoTime());
        if (!root.mkdirs()) throw new IOException("Не удалось создать временную папку");
        try {
            net.lingala.zip4j.ZipFile zip = new net.lingala.zip4j.ZipFile(archive, password.toCharArray());
            zip.extractAll(root.getAbsolutePath());
            return publishTree(ctx, root, root, folder);
        } finally { archive.delete(); deleteTree(root); }
    }

    private static void extract7z(File archive, File dest) throws IOException {
        String root = dest.getCanonicalPath() + File.separator;
        try (SevenZFile seven = new SevenZFile(archive)) {
            SevenZArchiveEntry e; byte[] buffer = new byte[64 * 1024];
            while ((e = seven.getNextEntry()) != null) {
                String entryName = e.getName();
                if (entryName == null || entryName.isBlank()) continue;
                File out = new File(dest, entryName);
                String canonical = out.getCanonicalPath();
                if (!canonical.startsWith(root)) throw new IOException("Опасный путь внутри архива");
                if (e.isDirectory()) {
                    if (!out.mkdirs() && !out.isDirectory()) throw new IOException("Не удалось создать папку");
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.mkdirs() && !parent.isDirectory()) throw new IOException("Не удалось создать папку");
                    try (OutputStream os = new FileOutputStream(out)) {
                        int n; while ((n = seven.read(buffer)) > 0) os.write(buffer, 0, n);
                    }
                }
            }
        }
    }

    private static int publishTree(Context ctx, File root, File current, String folder) throws IOException {
        int count = 0;
        File[] files = current.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) count += publishTree(ctx, root, f, folder);
            else {
                String relativeParent = root.toPath().relativize(f.getParentFile().toPath()).toString().replace('\\', '/');
                String sub = folder + (relativeParent.isBlank() ? "" : "/" + relativeParent);
                FileStore.publishFile(ctx, f, f.getName(), "application/octet-stream", sub);
                count++;
            }
        }
        return count;
    }

    private static String safeName(String name) { return name.replace('\\', '_').replace('/', '_'); }
    private static String extension(String name) { int dot = name.lastIndexOf('.'); return dot >= 0 ? name.substring(dot) : ".bin"; }
    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) { File[] children = f.listFiles(); if (children != null) for (File c : children) deleteTree(c); }
        f.delete();
    }
}
