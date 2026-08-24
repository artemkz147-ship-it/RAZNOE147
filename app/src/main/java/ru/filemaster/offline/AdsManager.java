package ru.filemaster.offline;

import android.app.Activity;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.yandex.mobile.ads.banner.BannerAdEventListener;
import com.yandex.mobile.ads.banner.BannerAdSize;
import com.yandex.mobile.ads.banner.BannerAdView;
import com.yandex.mobile.ads.common.AdError;
import com.yandex.mobile.ads.common.AdRequest;
import com.yandex.mobile.ads.common.AdRequestError;
import com.yandex.mobile.ads.common.ImpressionData;
import com.yandex.mobile.ads.interstitial.InterstitialAd;
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener;
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener;
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader;

import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

final class AdsManager {
    private static volatile AdsManager instance;

    static AdsManager get(Application application) {
        AdsManager local = instance;
        if (local == null) {
            synchronized (AdsManager.class) {
                local = instance;
                if (local == null) {
                    local = new AdsManager(application);
                    instance = local;
                }
            }
        }
        return local;
    }

    private final Application app;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<Activity, BannerHolder> banners = new IdentityHashMap<>();
    private final AdPolicy policy = new AdPolicy(SystemClock.elapsedRealtime());
    private WeakReference<Activity> currentActivity = new WeakReference<>(null);

    private boolean sdkReady;
    private InterstitialAdLoader interstitialLoader;
    private InterstitialAd interstitialAd;
    private boolean interstitialLoading;
    private boolean interstitialShowing;
    private long lastInterstitialLoadFailureAt;

    private final Runnable outputInterstitialAttempt = this::attemptInterstitialAfterOutputs;

    private AdsManager(Application application) {
        app = application;
    }

    void onSdkReady() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::onSdkReady);
            return;
        }
        if (sdkReady) return;
        sdkReady = true;
        interstitialLoader = new InterstitialAdLoader(app);

        Activity activity = currentActivity.get();
        if (activity != null) {
            BannerHolder holder = banners.get(activity);
            if (holder != null) holder.onSdkReady();
        }

        // App Open gets priority during cold start. Full-screen interstitial is preloaded later.
        mainHandler.postDelayed(this::loadInterstitialIfAllowed, 4_000L);
    }

    void onActivityResumed(Activity activity) {
        if (activity == null) return;
        currentActivity = new WeakReference<>(activity);
        if (shouldShowBanner(activity)) {
            BannerHolder holder = banners.get(activity);
            if (holder == null) {
                holder = new BannerHolder(activity);
                banners.put(activity, holder);
            }
            holder.onResume();
        }
        mainHandler.removeCallbacks(outputInterstitialAttempt);
        mainHandler.postDelayed(outputInterstitialAttempt, 700L);
    }

    void onActivityPaused(Activity activity) {
        BannerHolder holder = banners.get(activity);
        if (holder != null) holder.onPause();
        Activity current = currentActivity.get();
        if (current == activity) currentActivity = new WeakReference<>(null);
    }

    void onActivityDestroyed(Activity activity) {
        BannerHolder holder = banners.remove(activity);
        if (holder != null) holder.destroy();
        Activity current = currentActivity.get();
        if (current == activity) currentActivity = new WeakReference<>(null);
    }

    /** Called after a file was successfully published into Downloads/ФайлМастер. */
    void recordOutputCreated() {
        policy.recordOutput();
        mainHandler.removeCallbacks(outputInterstitialAttempt);
        // Debounce multi-file/batch operations and wait for progress dialogs to disappear.
        mainHandler.postDelayed(outputInterstitialAttempt, 2_200L);
    }

    private boolean shouldShowBanner(Activity activity) {
        return !(activity instanceof LaunchActivity)
                && !(activity instanceof EditableOpenActivity)
                && !(activity instanceof ZipOpenActivity);
    }

    private boolean canShowFullscreenOn(Activity activity) {
        return activity != null
                && !activity.isFinishing()
                && !activity.isDestroyed()
                && shouldShowBanner(activity);
    }

    private void attemptInterstitialAfterOutputs() {
        if (!sdkReady || interstitialShowing || !policy.canShowInterstitial(SystemClock.elapsedRealtime())) return;

        Activity activity = currentActivity.get();
        if (!canShowFullscreenOn(activity)) return;

        // Do not jump over a progress/result dialog. Try again after the activity regains focus.
        if (!activity.hasWindowFocus()) {
            mainHandler.removeCallbacks(outputInterstitialAttempt);
            mainHandler.postDelayed(outputInterstitialAttempt, 1_200L);
            return;
        }

        if (interstitialAd == null) {
            loadInterstitialIfAllowed();
            return;
        }
        showInterstitial(activity);
    }

    private void loadInterstitialIfAllowed() {
        if (!sdkReady || interstitialLoader == null || interstitialLoading || interstitialAd != null || interstitialShowing) return;
        long now = SystemClock.elapsedRealtime();
        if (lastInterstitialLoadFailureAt > 0L
                && now - lastInterstitialLoadFailureAt < AdConfig.INTERSTITIAL_RETRY_AFTER_FAILURE_MS) return;

        interstitialLoading = true;
        AdRequest request = new AdRequest.Builder(AdConfig.INTERSTITIAL_ID).build();
        interstitialLoader.loadAd(request, new InterstitialAdLoadListener() {
            @Override
            public void onAdLoaded(InterstitialAd ad) {
                interstitialLoading = false;
                interstitialAd = ad;
                if (policy.canShowInterstitial(SystemClock.elapsedRealtime())) {
                    mainHandler.removeCallbacks(outputInterstitialAttempt);
                    mainHandler.postDelayed(outputInterstitialAttempt, 800L);
                }
            }

            @Override
            public void onAdFailedToLoad(AdRequestError error) {
                interstitialLoading = false;
                lastInterstitialLoadFailureAt = SystemClock.elapsedRealtime();
                // No immediate retry: the next natural operation/resume may retry after the cooldown.
            }
        });
    }

    private void showInterstitial(Activity activity) {
        final InterstitialAd ad = interstitialAd;
        if (ad == null || interstitialShowing) return;
        interstitialShowing = true;

        ad.setAdEventListener(new InterstitialAdEventListener() {
            private boolean impressionStarted;

            @Override
            public void onAdShown() {
                impressionStarted = true;
                policy.markInterstitialShown(SystemClock.elapsedRealtime());
            }

            @Override
            public void onAdFailedToShow(AdError adError) {
                if (!impressionStarted) lastInterstitialLoadFailureAt = SystemClock.elapsedRealtime();
                finish(false);
            }

            @Override
            public void onAdDismissed() {
                finish(true);
            }

            @Override public void onAdClicked() {}
            @Override public void onAdImpression(ImpressionData impressionData) {}

            private void finish(boolean preloadSoon) {
                ad.setAdEventListener(null);
                if (interstitialAd == ad) interstitialAd = null;
                interstitialShowing = false;
                if (preloadSoon) mainHandler.postDelayed(AdsManager.this::loadInterstitialIfAllowed, 5_000L);
            }
        });

        try {
            ad.show(activity);
        } catch (RuntimeException error) {
            ad.setAdEventListener(null);
            if (interstitialAd == ad) interstitialAd = null;
            interstitialShowing = false;
            lastInterstitialLoadFailureAt = SystemClock.elapsedRealtime();
        }
    }

    private final class BannerHolder implements ViewGroup.OnHierarchyChangeListener {
        private final Activity activity;
        private final FrameLayout content;
        private final FrameLayout slot;
        private final FallbackBannerView fallback;
        private final WeakHashMap<View, Integer> originalBottomMargins = new WeakHashMap<>();
        private final Runnable refreshRunnable = this::loadBanner;
        private BannerAdView banner;
        private boolean resumed;
        private boolean destroyed;
        private boolean loading;
        private boolean loadedOnce;
        private long lastLoadAt;
        private int slotHeightPx;
        private int safeBottomPx;
        private int safeLeftPx;
        private int safeRightPx;

        BannerHolder(Activity activity) {
            this.activity = activity;
            this.content = activity.findViewById(android.R.id.content);
            this.slotHeightPx = dp(activity, 58);
            this.slot = new FrameLayout(activity);
            this.slot.setTag("doki_ad_slot");
            this.slot.setBackgroundColor(0xFFF7FAFF);
            this.fallback = new FallbackBannerView(activity);
            slot.addView(fallback, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            if (content != null) {
                content.setOnHierarchyChangeListener(this);
                ViewCompat.setOnApplyWindowInsetsListener(slot, (v, insets) -> {
                    Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    safeBottomPx = safe.bottom;
                    safeLeftPx = safe.left;
                    safeRightPx = safe.right;
                    updateLayoutReservation();
                    return insets;
                });
                ensureSlot();
                reserveAllChildren();
                ViewCompat.requestApplyInsets(slot);
            }
        }

        void onSdkReady() {
            if (resumed) scheduleNextLoad();
        }

        void onResume() {
            resumed = true;
            ensureSlot();
            reserveAllChildren();
            scheduleNextLoad();
        }

        void onPause() {
            resumed = false;
            mainHandler.removeCallbacks(refreshRunnable);
        }

        private void scheduleNextLoad() {
            mainHandler.removeCallbacks(refreshRunnable);
            if (!sdkReady || destroyed || !resumed) return;
            long delay = AdPolicy.bannerDelay(lastLoadAt, SystemClock.elapsedRealtime());
            mainHandler.postDelayed(refreshRunnable, delay);
        }

        private void loadBanner() {
            if (!sdkReady || destroyed || !resumed || loading || content == null) return;
            ensureSlot();
            int widthPixels = Math.max(1, content.getWidth() - safeLeftPx - safeRightPx);
            if (widthPixels <= 1) widthPixels = activity.getResources().getDisplayMetrics().widthPixels;
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            int widthDp = Math.max(1, Math.round(widthPixels / dm.density));

            BannerAdSize adSize = BannerAdSize.sticky(activity, widthDp);
            int calculatedHeight = dp(activity, Math.max(50, adSize.getHeight()));
            if (calculatedHeight != slotHeightPx) {
                slotHeightPx = calculatedHeight;
                updateLayoutReservation();
            }

            if (banner == null) {
                banner = new BannerAdView(activity);
                slot.addView(banner, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                banner.setBannerAdEventListener(new BannerAdEventListener() {
                    @Override
                    public void onAdLoaded() {
                        loading = false;
                        loadedOnce = true;
                        lastLoadAt = SystemClock.elapsedRealtime();
                        fallback.setVisibility(View.GONE);
                        if (destroyed || activity.isDestroyed()) {
                            destroyBannerOnly();
                            return;
                        }
                        scheduleNextLoad();
                    }

                    @Override
                    public void onAdFailedToLoad(AdRequestError error) {
                        loading = false;
                        lastLoadAt = SystemClock.elapsedRealtime();
                        if (!loadedOnce) fallback.setVisibility(View.VISIBLE);
                        // Retry only after the 30-second placement cooldown.
                        scheduleNextLoad();
                    }

                    @Override public void onAdClicked() {}
                    @Override public void onLeftApplication() {}
                    @Override public void onReturnedToApplication() {}
                    @Override public void onImpression(ImpressionData impressionData) {}
                });
            }

            banner.setAdSize(adSize);
            loading = true;
            lastLoadAt = SystemClock.elapsedRealtime();
            banner.loadAd(new AdRequest.Builder(AdConfig.BANNER_ID).build());
        }

        private void ensureSlot() {
            if (content == null || destroyed) return;
            if (slot.getParent() != content) {
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, slotHeightPx, Gravity.BOTTOM);
                lp.leftMargin = safeLeftPx;
                lp.rightMargin = safeRightPx;
                lp.bottomMargin = safeBottomPx;
                content.addView(slot, lp);
                slot.bringToFront();
            }
            updateLayoutReservation();
        }

        private void reserveAllChildren() {
            if (content == null || destroyed) return;
            for (int i = 0; i < content.getChildCount(); i++) {
                View child = content.getChildAt(i);
                if (child != slot) reserve(child);
            }
            slot.bringToFront();
        }

        private void reserve(View child) {
            ViewGroup.LayoutParams raw = child.getLayoutParams();
            if (!(raw instanceof FrameLayout.LayoutParams)) return;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;
            Integer original = originalBottomMargins.get(child);
            if (original == null) {
                original = lp.bottomMargin;
                originalBottomMargins.put(child, original);
            }
            int wanted = original + slotHeightPx + safeBottomPx;
            if (lp.bottomMargin != wanted) {
                lp.bottomMargin = wanted;
                child.setLayoutParams(lp);
            }
        }

        private void updateLayoutReservation() {
            if (content == null || destroyed) return;
            ViewGroup.LayoutParams raw = slot.getLayoutParams();
            if (raw instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;
                lp.height = slotHeightPx;
                lp.gravity = Gravity.BOTTOM;
                lp.leftMargin = safeLeftPx;
                lp.rightMargin = safeRightPx;
                lp.bottomMargin = safeBottomPx;
                slot.setLayoutParams(lp);
            }
            reserveAllChildren();
        }

        @Override
        public void onChildViewAdded(View parent, View child) {
            if (destroyed || child == slot) return;
            reserve(child);
            mainHandler.post(this::ensureSlot);
        }

        @Override
        public void onChildViewRemoved(View parent, View child) {
            if (destroyed) return;
            originalBottomMargins.remove(child);
            if (child == slot) mainHandler.post(this::ensureSlot);
        }

        private void destroyBannerOnly() {
            if (banner != null) {
                banner.setBannerAdEventListener(null);
                banner.destroy();
                banner = null;
            }
        }

        void destroy() {
            destroyed = true;
            resumed = false;
            mainHandler.removeCallbacks(refreshRunnable);
            destroyBannerOnly();
            if (content != null) {
                content.setOnHierarchyChangeListener(null);
                for (int i = 0; i < content.getChildCount(); i++) {
                    View child = content.getChildAt(i);
                    if (child == slot) continue;
                    Integer original = originalBottomMargins.get(child);
                    ViewGroup.LayoutParams raw = child.getLayoutParams();
                    if (original != null && raw instanceof FrameLayout.LayoutParams) {
                        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;
                        lp.bottomMargin = original;
                        child.setLayoutParams(lp);
                    }
                }
                if (slot.getParent() == content) content.removeView(slot);
            }
            originalBottomMargins.clear();
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.max(1, Math.round(value * activity.getResources().getDisplayMetrics().density));
    }
}
