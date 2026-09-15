package com.dinomosaic.kids;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yandex.mobile.ads.banner.BannerAdEventListener;
import com.yandex.mobile.ads.banner.BannerAdSize;
import com.yandex.mobile.ads.banner.BannerAdView;
import com.yandex.mobile.ads.common.AdError;
import com.yandex.mobile.ads.common.AdRequest;
import com.yandex.mobile.ads.common.AdRequestError;
import com.yandex.mobile.ads.common.ImpressionData;
import com.yandex.mobile.ads.common.YandexAds;
import com.yandex.mobile.ads.interstitial.InterstitialAd;
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener;
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener;
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader;

import java.io.InputStream;

import ru.rustore.sdk.review.RuStoreReviewManager;
import ru.rustore.sdk.review.RuStoreReviewManagerFactory;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 5173;
    private static final String BANNER_AD_UNIT = "R-M-20049918-1";
    private static final String INTERSTITIAL_AD_UNIT = "R-M-20049918-2";
    private static final String DEVELOPER_URL = "https://www.rustore.ru/catalog/developer/c7qawh";

    private WebView webView;
    private ValueCallback<Uri[]> pendingFileChooser;

    private FrameLayout adSlot;
    private FrameLayout placeholderView;
    private BannerAdView bannerAd;
    private String currentScreen = "menu";
    private String currentPuzzleId = "";
    private boolean yandexReady = false;

    private InterstitialAdLoader interstitialAdLoader;
    private InterstitialAd interstitialAd;
    private boolean interstitialLoading = false;

    private RuStoreReviewManager reviewManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemUi();
        createRootUi();
        reviewManager = RuStoreReviewManagerFactory.INSTANCE.create(this);

        YandexAds.initialize(this, () -> {
            yandexReady = true;
            setupInterstitialLoader();
            preloadInterstitial();
            if ("puzzle".equals(currentScreen)) {
                loadBannerForCurrentPuzzle();
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl("file:///android_asset/index.html");
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void createRootUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(223, 245, 255));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        createWebView();
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        adSlot = new FrameLayout(this);
        adSlot.setMinimumHeight(dp(82));
        adSlot.setBackgroundColor(Color.rgb(235, 247, 255));
        root.addView(adSlot, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        placeholderView = createPromoPlaceholder();
        adSlot.addView(placeholderView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(82)
        ));

        setContentView(root);
        showPlaceholder();
    }

    private FrameLayout createPromoPlaceholder() {
        FrameLayout frame = new FrameLayout(this);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(204, 242, 255), Color.rgb(255, 239, 179)}
        );
        background.setCornerRadius(dp(14));
        frame.setBackground(background);
        frame.setPadding(dp(4), dp(4), dp(4), dp(4));
        frame.setOnClickListener(v -> openDeveloperPage());

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setAdjustViewBounds(false);
        boolean loadedImage = false;
        try (InputStream input = getAssets().open("promo-banner.png")) {
            image.setImageBitmap(BitmapFactory.decodeStream(input));
            loadedImage = true;
        } catch (Exception ignored) {
        }
        frame.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        if (!loadedImage) {
            TextView text = new TextView(this);
            text.setText("Играть в другие игры");
            text.setTextColor(Color.rgb(111, 69, 0));
            text.setTextSize(20);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setGravity(Gravity.CENTER);
            frame.addView(text, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
        }
        return frame;
    }

    private void createWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(223, 245, 255));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);

        webView.addJavascriptInterface(new AdsBridge(), "AndroidAds");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {
                if (pendingFileChooser != null) {
                    pendingFileChooser.onReceiveValue(null);
                }
                pendingFileChooser = filePathCallback;
                try {
                    Intent intent = fileChooserParams.createIntent();
                    intent.setType("image/*");
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception ignored) {
                    pendingFileChooser = null;
                    return false;
                }
            }
        });
    }

    private final class AdsBridge {
        @JavascriptInterface
        public void setScreen(String screen) {
            runOnUiThread(() -> {
                currentScreen = screen == null ? "" : screen;
                if (!"puzzle".equals(currentScreen)) {
                    destroyBanner();
                    showPlaceholder();
                }
            });
        }

        @JavascriptInterface
        public void newPuzzle(String puzzleId) {
            runOnUiThread(() -> {
                currentScreen = "puzzle";
                currentPuzzleId = puzzleId == null ? "" : puzzleId;
                showPlaceholder();
                destroyBanner();
                loadBannerForCurrentPuzzle();
                if (interstitialAd == null && !interstitialLoading) {
                    preloadInterstitial();
                }
            });
        }

        @JavascriptInterface
        public void afterWin(int winCount, boolean alreadyRated) {
            runOnUiThread(() -> handleAfterWin(winCount, alreadyRated));
        }
    }

    private void loadBannerForCurrentPuzzle() {
        if (!yandexReady || !"puzzle".equals(currentScreen) || adSlot == null) {
            return;
        }
        adSlot.post(() -> {
            if (!"puzzle".equals(currentScreen) || isFinishing() || isDestroyed()) return;
            destroyBanner();
            showPlaceholder();

            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int widthPx = adSlot.getWidth() > 0 ? adSlot.getWidth() : metrics.widthPixels;
            int widthDp = Math.max(1, Math.round(widthPx / metrics.density));

            final BannerAdView newBanner = new BannerAdView(this);
            newBanner.setAdUnitId(BANNER_AD_UNIT);
            newBanner.setAdSize(BannerAdSize.sticky(this, widthDp));
            newBanner.setVisibility(View.INVISIBLE);
            newBanner.setBannerAdEventListener(new BannerAdEventListener() {
                @Override
                public void onAdLoaded() {
                    if (isDestroyed() || bannerAd != newBanner || !"puzzle".equals(currentScreen)) {
                        newBanner.destroy();
                        return;
                    }
                    placeholderView.setVisibility(View.GONE);
                    newBanner.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAdFailedToLoad(AdRequestError adRequestError) {
                    if (bannerAd == newBanner) {
                        showPlaceholder();
                    }
                }

                @Override public void onAdClicked() {}
                @Override public void onLeftApplication() {}
                @Override public void onReturnedToApplication() {}
                @Override public void onImpression(ImpressionData impressionData) {}
            });

            bannerAd = newBanner;
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
            );
            adSlot.addView(newBanner, params);
            newBanner.loadAd(new AdRequest.Builder().build());
        });
    }

    private void showPlaceholder() {
        if (placeholderView != null) {
            placeholderView.setVisibility(View.VISIBLE);
            placeholderView.bringToFront();
        }
    }

    private void destroyBanner() {
        if (bannerAd != null) {
            bannerAd.setBannerAdEventListener(null);
            if (bannerAd.getParent() instanceof ViewGroup) {
                ((ViewGroup) bannerAd.getParent()).removeView(bannerAd);
            }
            bannerAd.destroy();
            bannerAd = null;
        }
    }

    private void setupInterstitialLoader() {
        if (interstitialAdLoader != null) return;
        interstitialAdLoader = new InterstitialAdLoader(this);
        interstitialAdLoader.setAdLoadListener(new InterstitialAdLoadListener() {
            @Override
            public void onAdLoaded(InterstitialAd ad) {
                interstitialLoading = false;
                interstitialAd = ad;
            }

            @Override
            public void onAdFailedToLoad(AdRequestError adRequestError) {
                interstitialLoading = false;
                interstitialAd = null;
            }
        });
    }

    private void preloadInterstitial() {
        if (!yandexReady || interstitialLoading || interstitialAd != null) return;
        setupInterstitialLoader();
        if (interstitialAdLoader == null) return;
        interstitialLoading = true;
        interstitialAdLoader.loadAd(new AdRequest.Builder(INTERSTITIAL_AD_UNIT).build());
    }

    private void handleAfterWin(int winCount, boolean alreadyRated) {
        final boolean needInterstitial = winCount > 0 && winCount % 3 == 0;
        final boolean needReview = !alreadyRated && winCount > 0 && winCount % 5 == 0;

        Runnable continueAfterInterstitial = () -> {
            if (needReview) {
                launchRuStoreReview(this::continueAfterWin);
            } else {
                continueAfterWin();
            }
        };

        if (needInterstitial) {
            showInterstitialThen(continueAfterInterstitial);
        } else {
            continueAfterInterstitial.run();
        }
    }

    private void showInterstitialThen(Runnable onDone) {
        final InterstitialAd ad = interstitialAd;
        interstitialAd = null;
        if (ad == null) {
            preloadInterstitial();
            onDone.run();
            return;
        }

        final boolean[] finished = {false};
        Runnable finishOnce = () -> {
            if (finished[0]) return;
            finished[0] = true;
            ad.setAdEventListener(null);
            preloadInterstitial();
            onDone.run();
        };

        ad.setAdEventListener(new InterstitialAdEventListener() {
            @Override public void onAdShown() {}
            @Override public void onAdClicked() {}
            @Override public void onAdImpression(ImpressionData impressionData) {}

            @Override
            public void onAdFailedToShow(AdError adError) {
                finishOnce.run();
            }

            @Override
            public void onAdDismissed() {
                finishOnce.run();
            }
        });
        ad.show(this);
    }

    private void launchRuStoreReview(Runnable onDone) {
        if (reviewManager == null) {
            onDone.run();
            return;
        }

        reviewManager.requestReviewFlow()
                .addOnSuccessListener(reviewInfo ->
                        reviewManager.launchReviewFlow(reviewInfo)
                                .addOnSuccessListener(unit -> {
                                    markReviewCompletedInWeb();
                                    onDone.run();
                                })
                                .addOnFailureListener(throwable -> {
                                    if (isReviewAlreadyExists(throwable)) {
                                        markReviewCompletedInWeb();
                                    }
                                    onDone.run();
                                })
                )
                .addOnFailureListener(throwable -> {
                    if (isReviewAlreadyExists(throwable)) {
                        markReviewCompletedInWeb();
                    }
                    onDone.run();
                });
    }

    private boolean isReviewAlreadyExists(Throwable throwable) {
        if (throwable == null) return false;
        String name = throwable.getClass().getSimpleName();
        return "RuStoreReviewExists".equals(name) || "RuStoreReviewExistsException".equals(name);
    }

    private void markReviewCompletedInWeb() {
        if (webView != null) {
            webView.evaluateJavascript(
                    "window.nativeMarkRated && window.nativeMarkRated();",
                    null
            );
        }
    }

    private void continueAfterWin() {
        if (webView != null) {
            webView.evaluateJavascript(
                    "window.nativeAfterWinDone && window.nativeAfterWinDone();",
                    null
            );
        }
    }

    private void openDeveloperPage() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(DEVELOPER_URL)));
        } catch (Exception ignored) {
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void configureSystemUi() {
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (pendingFileChooser != null) {
                Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                pendingFileChooser.onReceiveValue(result);
                pendingFileChooser = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        configureSystemUi();
        if ("puzzle".equals(currentScreen)) {
            destroyBanner();
            showPlaceholder();
            loadBannerForCurrentPuzzle();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) configureSystemUi();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        destroyBanner();

        if (interstitialAd != null) {
            interstitialAd.setAdEventListener(null);
            interstitialAd = null;
        }
        if (interstitialAdLoader != null) {
            interstitialAdLoader.setAdLoadListener(null);
            interstitialAdLoader = null;
        }

        if (pendingFileChooser != null) {
            pendingFileChooser.onReceiveValue(null);
            pendingFileChooser = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.removeJavascriptInterface("AndroidAds");
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
