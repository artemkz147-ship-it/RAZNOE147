package app.inkflow.reader;

import android.app.Activity;
import android.content.Context;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import com.yandex.mobile.ads.banner.*;
import com.yandex.mobile.ads.common.*;

public final class BannerAdSlot extends FrameLayout {
    public static final String AD_UNIT_ID="R-M-20002801-1";
    public static final long REFRESH_MS=35_000L;
    private static final int BASE_SLOT_DP=50;

    private final Activity activity;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ImageView placeholder;
    private BannerAdView banner;
    private boolean active=false, initRequested=false;
    private int navigationInsetPx=0;
    private int contentHeightDp=BASE_SLOT_DP;

    private final Runnable refresh=new Runnable(){@Override public void run(){if(!active||!isShown())return;safeLoadBanner();handler.postDelayed(this,REFRESH_MS);}};

    public BannerAdSlot(Activity a){
        super(a);activity=a;setBackgroundColor(Ui.BG2);setClipChildren(true);setClipToPadding(true);
        placeholder=new ImageView(a);
        placeholder.setImageResource(R.drawable.ad_loading);
        placeholder.setScaleType(ImageView.ScaleType.CENTER_CROP);
        placeholder.setAdjustViewBounds(false);
        placeholder.setContentDescription("Спасибо, что вы с нами");
        addView(placeholder,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        setOnApplyWindowInsetsListener((v,insets)->{
            try{
                int bottom=Build.VERSION.SDK_INT>=30?insets.getInsets(WindowInsets.Type.navigationBars()).bottom:insets.getSystemWindowInsetBottom();
                navigationInsetPx=Math.max(0,bottom);
                setPadding(0,0,0,navigationInsetPx);
                updateHeight();
            }catch(Throwable ignored){}
            return insets;
        });
    }

    public static View wrap(Activity a,View content){
        if(content instanceof LinearLayout&&"ad_wrapped".equals(content.getTag()))return content;
        ViewGroup parent=content.getParent() instanceof ViewGroup?(ViewGroup)content.getParent():null;
        if(parent!=null)parent.removeView(content);
        LinearLayout shell=new LinearLayout(a);shell.setTag("ad_wrapped");shell.setOrientation(LinearLayout.VERTICAL);shell.setBackgroundColor(Ui.BG);
        shell.addView(content,new LinearLayout.LayoutParams(-1,0,1f));
        BannerAdSlot slot=new BannerAdSlot(a);
        shell.addView(slot,new LinearLayout.LayoutParams(-1,dp(a,BASE_SLOT_DP)));
        return shell;
    }

    private static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}

    private void updateHeight(){
        ViewGroup.LayoutParams lp=getLayoutParams();
        if(lp!=null){
            int wanted=dp(activity,Math.max(BASE_SLOT_DP,contentHeightDp))+navigationInsetPx;
            if(lp.height!=wanted){lp.height=wanted;setLayoutParams(lp);}
        }
        if(banner!=null){
            try{
                FrameLayout.LayoutParams blp=(FrameLayout.LayoutParams)banner.getLayoutParams();
                blp.height=dp(activity,Math.max(1,contentHeightDp));
                blp.gravity=Gravity.BOTTOM;
                banner.setLayoutParams(blp);
            }catch(Throwable ignored){}
        }
        requestLayout();
    }

    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();try{requestApplyInsets();updateHeight();start();}catch(Throwable ignored){}}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE)start();else stopRefreshOnly();}

    private void start(){
        if(active)return;active=true;
        if(!initRequested){
            initRequested=true;
            handler.postDelayed(()->{
                try{YandexAds.initialize(activity.getApplicationContext(),()->activity.runOnUiThread(()->{
                    if(!active)return;
                    safeLoadBanner();handler.removeCallbacks(refresh);handler.postDelayed(refresh,REFRESH_MS);
                }));}catch(Throwable ignored){}
            },700);
        }else{
            safeLoadBanner();handler.removeCallbacks(refresh);handler.postDelayed(refresh,REFRESH_MS);
        }
    }

    private void stopRefreshOnly(){active=false;handler.removeCallbacks(refresh);}
    private void stop(){active=false;handler.removeCallbacksAndMessages(null);destroyBanner();}
    private void destroyBanner(){try{if(banner!=null){banner.setBannerAdEventListener(null);removeView(banner);banner.destroy();banner=null;}}catch(Throwable ignored){banner=null;}}
    private void safeLoadBanner(){try{loadBanner();}catch(Throwable ignored){destroyBanner();}}

    private void loadBanner(){
        if(!active||activity.isFinishing()||activity.isDestroyed())return;
        destroyBanner();
        contentHeightDp=BASE_SLOT_DP;updateHeight();
        DisplayMetrics dm=getResources().getDisplayMetrics();
        int widthPx=getWidth()>0?getWidth():dm.widthPixels;
        int widthDp=Math.max(1,Math.round(widthPx/dm.density));
        BannerAdSize size=BannerAdSize.inline(activity,widthDp,BASE_SLOT_DP);
        BannerAdView view=new BannerAdView(activity);banner=view;view.setVisibility(INVISIBLE);view.setAdSize(size);
        view.setBannerAdEventListener(new BannerAdEventListener(){
            @Override public void onAdLoaded(){
                if(banner!=view||activity.isDestroyed()){try{view.destroy();}catch(Throwable ignored){}return;}
                int actual=BASE_SLOT_DP;
                try{int h=view.getAdSize().getHeight();if(h>0)actual=h;}catch(Throwable ignored){}
                contentHeightDp=Math.max(BASE_SLOT_DP,actual);
                updateHeight();
                view.setVisibility(VISIBLE);
            }
            @Override public void onAdFailedToLoad(AdRequestError e){if(banner==view)view.setVisibility(INVISIBLE);contentHeightDp=BASE_SLOT_DP;updateHeight();}
            @Override public void onAdClicked(){}
            @Override public void onImpression(ImpressionData data){}
        });
        addView(view,new FrameLayout.LayoutParams(-1,dp(activity,BASE_SLOT_DP),Gravity.BOTTOM));
        view.loadAd(new AdRequest.Builder(AD_UNIT_ID).build());
    }
}
