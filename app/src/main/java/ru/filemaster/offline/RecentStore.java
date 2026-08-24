package ru.filemaster.offline;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RecentStore {
    private static final String PREFS = "filemaster_recent";
    private static final String KEY = "items";
    private static final int LIMIT = 20;

    private RecentStore() {}

    static final class Entry {
        final Uri uri;
        final String name;
        final String mime;
        final long time;

        Entry(Uri uri, String name, String mime, long time) {
            this.uri = uri;
            this.name = name;
            this.mime = mime;
            this.time = time;
        }
    }

    static synchronized void add(Context ctx, Uri uri, String name, String mime) {
        if (uri == null) return;
        List<Entry> items = list(ctx);
        String target = uri.toString();
        items.removeIf(e -> e.uri.toString().equals(target));
        items.add(0, new Entry(uri, safe(name), safe(mime), System.currentTimeMillis()));
        if (items.size() > LIMIT) items = new ArrayList<>(items.subList(0, LIMIT));
        save(ctx, items);
    }

    static synchronized List<Entry> list(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = p.getString(KEY, "");
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        List<Entry> out = new ArrayList<>();
        for (String line : raw.split("\\n")) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\|", -1);
            if (parts.length != 4) continue;
            try {
                Uri uri = Uri.parse(Uri.decode(parts[0]));
                String name = Uri.decode(parts[1]);
                String mime = Uri.decode(parts[2]);
                long time = Long.parseLong(parts[3]);
                out.add(new Entry(uri, name, mime, time));
            } catch (Exception ignored) {}
        }
        return out;
    }

    static synchronized void clear(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }

    private static void save(Context ctx, List<Entry> items) {
        StringBuilder b = new StringBuilder();
        for (Entry e : items) {
            if (b.length() > 0) b.append('\n');
            b.append(Uri.encode(e.uri.toString())).append('|')
                    .append(Uri.encode(safe(e.name))).append('|')
                    .append(Uri.encode(safe(e.mime))).append('|')
                    .append(e.time);
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, b.toString()).apply();
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
