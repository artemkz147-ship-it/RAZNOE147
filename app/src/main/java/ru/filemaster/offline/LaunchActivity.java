package ru.filemaster.offline;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.yandex.mobile.ads.appopenad.AppOpenAd;
import com.yandex.mobile.ads.appopenad.AppOpenAdEventListener;
import com.yandex.mobile.ads.appopenad.AppOpenAdLoadListener;
import com.yandex.mobile.ads.appopenad.AppOpenAdLoader;
import com.yandex.mobile.ads.common.AdError;
import com.yandex.mobile.ads.common.AdRequest;
import com.yandex.mobile.ads.common.AdRequestError;
import com.yandex.mobile.ads.common.ImpressionData;

/** Launcher screen used only for a short, bounded App Open ad opportunity. */
public final class LaunchActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppOpenAdLoader loader;
    private AppOpenAd appOpenAd;
    private boolean resumed;
    private boolean finished;
    private boolean loadStarted;

    private final Runnable timeout = this::continueToApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        buildSplash();

        handler.postDelayed(timeout, AdConfig.APP_OPEN_TIMEOUT_MS);
        DokiApplication application = (DokiApplication) getApplication();
        application.whenAdsReady(this::loadAppOpen);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        tryShowLoadedAd();
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    private void loadAppOpen() {
        if (finished || loadStarted) return;
        loadStarted = true;
        loader = new AppOpenAdLoader(getApplication());
        AdRequest request = new AdRequest.Builder(AdConfig.APP_OPEN_ID).build();
        loader.loadAd(request, new AppOpenAdLoadListener() {
            @Override
            public void onAdLoaded(AppOpenAd ad) {
                if (finished) {
                    ad.setAdEventListener(null);
                    return;
                }
                appOpenAd = ad;
                tryShowLoadedAd();
            }

            @Override
            public void onAdFailedToLoad(AdRequestError error) {
                continueToApp();
            }
        });
    }

    private void tryShowLoadedAd() {
        if (finished || !resumed || appOpenAd == null) return;
        handler.removeCallbacks(timeout);
        final AppOpenAd ad = appOpenAd;
        ad.setAdEventListener(new AppOpenAdEventListener() {
            @Override public void onAdShown() {}

            @Override
            public void onAdFailedToShow(AdError error) {
                clearAd(ad);
                continueToApp();
            }

            @Override
            public void onAdDismissed() {
                clearAd(ad);
                continueToApp();
            }

            @Override public void onAdClicked() {}
            @Override public void onAdImpression(ImpressionData impressionData) {}
        });
        try {
            ad.show(this);
        } catch (RuntimeException error) {
            clearAd(ad);
            continueToApp();
        }
    }

    private void clearAd(AppOpenAd ad) {
        ad.setAdEventListener(null);
        if (appOpenAd == ad) appOpenAd = null;
    }

    private void continueToApp() {
        if (finished) return;
        finished = true;
        handler.removeCallbacks(timeout);
        if (appOpenAd != null) {
            appOpenAd.setAdEventListener(null);
            appOpenAd = null;
        }
        startActivity(new Intent(this, MainActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void buildSplash() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(244, 249, 255), Color.rgb(222, 238, 255)});
        root.setBackground(bg);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(dp(28) + safe.left, dp(28) + safe.top, dp(28) + safe.right, dp(28) + safe.bottom);
            return insets;
        });

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher);
        icon.setContentDescription("Доки");
        root.addView(icon, new LinearLayout.LayoutParams(dp(112), dp(112)));

        TextView title = new TextView(this);
        title.setText("Доки");
        title.setTextColor(Color.rgb(20, 57, 126));
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(18);
        root.addView(title, titleLp);

        TextView subtitle = new TextView(this);
        subtitle.setText("Документы обрабатываются на устройстве");
        subtitle.setTextColor(Color.rgb(84, 111, 151));
        subtitle.setTextSize(15);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(7);
        root.addView(subtitle, subLp);

        ProgressBar progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        progressLp.topMargin = dp(24);
        root.addView(progress, progressLp);

        TextView hint = new TextView(this);
        hint.setText("Открываем приложение…");
        hint.setTextColor(Color.rgb(111, 131, 162));
        hint.setTextSize(13);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(10);
        root.addView(hint, hintLp);

        setContentView(root);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (appOpenAd != null) appOpenAd.setAdEventListener(null);
        appOpenAd = null;
        loader = null;
        super.onDestroy();
    }
}
