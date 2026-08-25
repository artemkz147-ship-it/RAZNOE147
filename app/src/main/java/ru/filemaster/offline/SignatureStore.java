package ru.filemaster.offline;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

final class SignatureStore {
    private SignatureStore() {}

    static File directory(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "saved_signatures");
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) throw new IllegalStateException("Не удалось создать хранилище подписей");
        return dir;
    }

    static File save(Context ctx, Bitmap bitmap) throws Exception {
        File out = new File(directory(ctx), "signature_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)) throw new IllegalStateException("Не удалось сохранить подпись");
        }
        return out;
    }

    static List<File> list(Context ctx) {
        File[] files = directory(ctx).listFiles((dir, name) -> name.startsWith("signature_") && name.endsWith(".png"));
        if (files == null) return new ArrayList<>();
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return new ArrayList<>(Arrays.asList(files));
    }

    static boolean delete(File file) {
        return file != null && file.exists() && file.delete();
    }
}
