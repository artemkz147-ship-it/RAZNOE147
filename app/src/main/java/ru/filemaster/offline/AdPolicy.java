package ru.filemaster.offline;

final class AdPolicy {
    private int outputsSinceInterstitial;
    private long lastInterstitialAt;

    AdPolicy(long nowMs) {
        lastInterstitialAt = nowMs;
    }

    synchronized void recordOutput() {
        if (outputsSinceInterstitial < 10_000) outputsSinceInterstitial++;
    }

    synchronized boolean canShowInterstitial(long nowMs) {
        return outputsSinceInterstitial >= AdConfig.INTERSTITIAL_OUTPUTS_PER_SHOW
                && nowMs - lastInterstitialAt >= AdConfig.INTERSTITIAL_MIN_GAP_MS;
    }

    synchronized void markInterstitialShown(long nowMs) {
        outputsSinceInterstitial = 0;
        lastInterstitialAt = nowMs;
    }

    synchronized int pendingOutputs() {
        return outputsSinceInterstitial;
    }

    static long bannerDelay(long lastLoadAtMs, long nowMs) {
        if (lastLoadAtMs <= 0L) return 0L;
        long elapsed = Math.max(0L, nowMs - lastLoadAtMs);
        return Math.max(0L, AdConfig.BANNER_REFRESH_MS - elapsed);
    }
}
