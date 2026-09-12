package ru.filemaster.offline;

final class InputSafety {
    static final long CACHE_HEADROOM = 64L * 1024L * 1024L;

    private InputSafety() {}

    static String safeTempSuffix(String displayName, String fallbackExt) {
        String fallback = normalizeFallback(fallbackExt);
        if (displayName == null) return fallback;
        if (displayName.contains("/") || displayName.contains("\\") || displayName.contains("..")) return fallback;
        int dot = displayName.lastIndexOf('.');
        if (dot < 0 || dot >= displayName.length() - 1) return fallback;
        String ext = displayName.substring(dot + 1);
        if (ext.length() > 12 || !ext.matches("[A-Za-z0-9]+")) return fallback;
        return "." + ext.toLowerCase(java.util.Locale.ROOT);
    }

    static boolean enoughCache(long availableBytes, long inputBytes) {
        if (inputBytes < 0) return true;
        long required;
        if (inputBytes > Long.MAX_VALUE - CACHE_HEADROOM) required = Long.MAX_VALUE;
        else required = inputBytes + CACHE_HEADROOM;
        return availableBytes >= required;
    }

    static String safeSubfolder(String value) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.replace('\\', '_').replace('/', '_').replace("..", "_").trim();
        cleaned = cleaned.replaceAll("[\\p{Cntrl}]", "_");
        if (cleaned.length() > 80) cleaned = cleaned.substring(0, 80);
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String normalizeFallback(String fallbackExt) {
        if (fallbackExt == null) return ".bin";
        String raw = fallbackExt.trim();
        if (raw.contains("/") || raw.contains("\\") || raw.contains("..")) return ".bin";
        if (raw.startsWith(".")) raw = raw.substring(1);
        if (raw.isEmpty() || raw.length() > 12 || !raw.matches("[A-Za-z0-9]+")) return ".bin";
        return "." + raw.toLowerCase(java.util.Locale.ROOT);
    }
}
