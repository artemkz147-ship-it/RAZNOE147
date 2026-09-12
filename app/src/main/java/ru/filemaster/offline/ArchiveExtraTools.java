package ru.filemaster.offline;

import android.content.Context;
import android.net.Uri;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

final class ArchiveExtraTools {
    private ArchiveExtraTools() {}

    static int extract(Context ctx, Uri uri) throws Exception {
        String name = FileStore.displayName(ctx, uri).toLowerCase();
        File input = FileStore.copyUriToTemp(ctx, uri, extension(name));
        File root = new File(ctx.getCacheDir(), "extra_extract_" + System.nanoTime());
        if (!root.mkdirs()) throw new IllegalStateException("Не удалось создать временную папку");
        String folder = "Распаковано_" + System.currentTimeMillis();
        try {
            if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
                try (InputStream raw = new BufferedInputStream(new FileInputStream(input));
                     GzipCompressorInputStream gz = new GzipCompressorInputStream(raw);
                     TarArchiveInputStream tar = new TarArchiveInputStream(gz)) { extractTar(tar, root); }
            } else if (name.endsWith(".tar.bz2") || name.endsWith(".tbz2")) {
                try (InputStream raw = new BufferedInputStream(new FileInputStream(input));
                     BZip2CompressorInputStream bz = new BZip2CompressorInputStream(raw);
                     TarArchiveInputStream tar = new TarArchiveInputStream(bz)) { extractTar(tar, root); }
            } else if (name.endsWith(".tar.xz") || name.endsWith(".txz")) {
                try (InputStream raw = new BufferedInputStream(new FileInputStream(input));
                     XZCompressorInputStream xz = new XZCompressorInputStream(raw);
                     TarArchiveInputStream tar = new TarArchiveInputStream(xz)) { extractTar(tar, root); }
            } else if (name.endsWith(".tar")) {
                try (TarArchiveInputStream tar = new TarArchiveInputStream(new BufferedInputStream(new FileInputStream(input)))) { extractTar(tar, root); }
            } else if (name.endsWith(".gz")) {
                String outName = strip(name, ".gz");
                try (InputStream raw = new BufferedInputStream(new FileInputStream(input));
                     GzipCompressorInputStream gz = new GzipCompressorInputStream(raw)) { writeSingle(gz, new File(root, outName)); }
            } else if (name.endsWith(".bz2")) {
                String outName = strip(name, ".bz2");
                try (InputStream raw = new BufferedInputStream(new FileInputStream(input));
                     BZip2CompressorInputStream bz = new BZip2CompressorInputStream(raw)) { writeSingle(bz, new File(root, outName)); }
            } else if (name.endsWith(".xz")) {
                String outName = strip(name, ".xz");
                try (InputStream raw = new BufferedInputStream(new FileInputStream(input));
                     XZCompressorInputStream xz = new XZCompressorInputStream(raw)) { writeSingle(xz, new File(root, outName)); }
            } else throw new IllegalArgumentException("Нужен TAR, TAR.GZ, TAR.BZ2, TAR.XZ, GZ, BZ2 или XZ");
            return publishTree(ctx, root, root, folder);
        } finally {
            input.delete();
            deleteTree(root);
        }
    }

    private static void extractTar(TarArchiveInputStream tar, File root) throws Exception {
        String canonicalRoot = root.getCanonicalPath() + File.separator;
        TarArchiveEntry e;
        while ((e = tar.getNextTarEntry()) != null) {
            String name = e.getName();
            if (name == null || name.isBlank()) continue;
            File out = new File(root, name);
            String canonical = out.getCanonicalPath();
            if (!canonical.startsWith(canonicalRoot)) throw new IllegalArgumentException("Опасный путь внутри архива");
            if (e.isDirectory()) {
                if (!out.mkdirs() && !out.isDirectory()) throw new IllegalStateException("Не удалось создать папку");
            } else {
                File parent = out.getParentFile();
                if (parent != null && !parent.mkdirs() && !parent.isDirectory()) throw new IllegalStateException("Не удалось создать папку");
                try (OutputStream os = new FileOutputStream(out)) { FileStore.copy(tar, os); }
            }
        }
    }

    private static void writeSingle(InputStream in, File out) throws Exception {
        try (OutputStream os = new FileOutputStream(out)) { FileStore.copy(in, os); }
    }

    private static int publishTree(Context ctx, File root, File current, String folder) throws Exception {
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

    private static String strip(String name, String suffix) {
        String base = name.substring(0, name.length() - suffix.length());
        return base.isBlank() ? "file" : base;
    }

    private static String extension(String name) {
        if (name.endsWith(".tar.gz")) return ".tar.gz";
        if (name.endsWith(".tar.bz2")) return ".tar.bz2";
        if (name.endsWith(".tar.xz")) return ".tar.xz";
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : ".bin";
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteTree(c);
        }
        f.delete();
    }
}
