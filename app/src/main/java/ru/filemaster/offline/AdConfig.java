package ru.filemaster.offline;

final class AdConfig {
    static final String BANNER_ID = "R-M-19805050-1";
    static final String INTERSTITIAL_ID = "R-M-19805050-2";
    static final String APP_OPEN_ID = "R-M-19805050-3";

    // Yandex rules prohibit refreshing a block on the same screen more often than once per 30 seconds.
    static final long BANNER_REFRESH_MS = 30_000L;

    // Full-screen ads are deliberately much less frequent than banners.
    static final int INTERSTITIAL_OUTPUTS_PER_SHOW = 3;
    static final long INTERSTITIAL_MIN_GAP_MS = 120_000L;
    static final long INTERSTITIAL_RETRY_AFTER_FAILURE_MS = 60_000L;

    // Do not delay cold start for an ad that did not arrive quickly enough.
    static final long APP_OPEN_TIMEOUT_MS = 1_800L;

    private AdConfig() {}
}
