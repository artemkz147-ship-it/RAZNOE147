package com.izgryazivknyazi.game;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;
import org.json.JSONObject;
import com.yandex.mobile.ads.common.AdRequest;
import com.yandex.mobile.ads.common.AdRequestError;
import com.yandex.mobile.ads.common.AdError;
import com.yandex.mobile.ads.common.ImpressionData;
import com.yandex.mobile.ads.common.YandexAds;
import com.yandex.mobile.ads.rewarded.*;
import com.yandex.mobile.ads.interstitial.*;

/** Native ad callbacks are the only source of completed-view rewards. */
final class GameAdManager {
    private static final String REWARDED = "R-M-20146145-2";
    private static final String INTERSTITIAL = "R-M-20146145-1";
    private final Activity activity;
    private final WebView web;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private RewardedAdLoader rewardLoader;
    private InterstitialAdLoader interstitialLoader;
    private RewardedAd rewardAd;
    private InterstitialAd interstitialAd;
    private String requestId, requestType;
    private boolean ready, showing, destroyed, interstitialLoading;
    private long lastInterstitialLoad;

    GameAdManager(Activity activity, WebView web) {
        this.activity=activity;this.web=web;
        YandexAds.initialize(activity, () -> activity.runOnUiThread(() -> {
            if(destroyed)return;
            ready=true;rewardLoader=new RewardedAdLoader(activity);
            interstitialLoader=new InterstitialAdLoader(activity);
            preloadInterstitial();
            if("rewarded".equals(requestType))loadRewarded(requestId);
        }));
    }
    private boolean valid(String id){return id!=null&&id.matches("[a-z]+-[0-9]+-[0-9]+")&&id.length()<100;}
    private boolean current(String id){return !destroyed&&id!=null&&id.equals(requestId);}
    private void send(String type,String id,String status){
        if(destroyed||activity.isFinishing())return;
        try{
            JSONObject data=new JSONObject();data.put("type",type);data.put("requestId",id);data.put("status",status);
            web.evaluateJavascript("window.dispatchEvent(new CustomEvent('game-ad',{detail:"+data+"}))",null);
        }catch(Exception error){Log.w("GameAds","Callback failed",error);}
    }
    void showRewarded(String id){
        if(!valid(id)||destroyed)return;
        if(requestId!=null){send("rewarded",id,"failed");return;}
        requestId=id;requestType="rewarded";showing=false;
        handler.postDelayed(()->{if(current(id)&&!showing)finish(id,"failed");},15000);
        if(ready)loadRewarded(id);
    }
    private void loadRewarded(String id){
        if(!current(id))return;
        try{rewardLoader.loadAd(new AdRequest.Builder(REWARDED).build(),new RewardedAdLoadListener(){
            @Override public void onAdLoaded(RewardedAd ad){
                if(!current(id)||activity.isFinishing()){ad.setAdEventListener(null);return;}
                rewardAd=ad;showing=true;
                ad.setAdEventListener(new RewardedAdEventListener(){
                    @Override public void onAdShown(){send("rewarded",id,"shown");}
                    @Override public void onRewarded(Reward reward){if(current(id))send("rewarded",id,"rewarded");}
                    @Override public void onAdDismissed(){finish(id,"closed");}
                    @Override public void onAdFailedToShow(AdError error){Log.w("GameAds","Reward show: "+error);finish(id,"failed");}
                    @Override public void onAdClicked(){}
                    @Override public void onAdImpression(ImpressionData data){}
                });
                try{ad.show(activity);}catch(Exception error){Log.w("GameAds","Reward show",error);finish(id,"failed");}
            }
            @Override public void onAdFailedToLoad(AdRequestError error){Log.w("GameAds","Reward load: "+error);finish(id,"failed");}
        });}catch(Exception error){Log.w("GameAds","Reward load",error);finish(id,"failed");}
    }
    private void preloadInterstitial(){
        if(!ready||destroyed||interstitialLoading||interstitialAd!=null)return;
        long now=android.os.SystemClock.elapsedRealtime();
        if(lastInterstitialLoad>0&&now-lastInterstitialLoad<60000)return;
        lastInterstitialLoad=now;interstitialLoading=true;
        try{interstitialLoader.loadAd(new AdRequest.Builder(INTERSTITIAL).build(),new InterstitialAdLoadListener(){
            @Override public void onAdLoaded(InterstitialAd ad){interstitialLoading=false;if(destroyed){ad.setAdEventListener(null);return;}interstitialAd=ad;}
            @Override public void onAdFailedToLoad(AdRequestError error){interstitialLoading=false;Log.w("GameAds","Interstitial load: "+error);}
        });}catch(Exception error){interstitialLoading=false;Log.w("GameAds","Interstitial load",error);}
    }
    void showInterstitial(String id){
        if(!valid(id)||destroyed)return;
        if(requestId!=null||interstitialAd==null){send("interstitial",id,"skipped");preloadInterstitial();return;}
        requestId=id;requestType="interstitial";showing=true;
        interstitialAd.setAdEventListener(new InterstitialAdEventListener(){
            @Override public void onAdShown(){send("interstitial",id,"shown");}
            @Override public void onAdDismissed(){finish(id,"closed");}
            @Override public void onAdFailedToShow(AdError error){Log.w("GameAds","Interstitial show: "+error);finish(id,"failed");}
            @Override public void onAdClicked(){}
            @Override public void onAdImpression(ImpressionData data){}
        });
        try{interstitialAd.show(activity);}catch(Exception error){Log.w("GameAds","Interstitial show",error);finish(id,"failed");}
    }
    private void finish(String id,String status){
        if(!current(id))return;
        String type=requestType;
        if("rewarded".equals(type)){if(rewardAd!=null)rewardAd.setAdEventListener(null);rewardAd=null;}
        else{if(interstitialAd!=null)interstitialAd.setAdEventListener(null);interstitialAd=null;}
        requestId=null;requestType=null;showing=false;handler.removeCallbacksAndMessages(null);
        send(type,id,status);
        if("interstitial".equals(type))preloadInterstitial();
    }
    void cancel(String id){if(current(id)&&!showing)finish(id,"failed");}
    boolean isShowing(){return showing;}
    void destroy(){destroyed=true;handler.removeCallbacksAndMessages(null);if(rewardAd!=null)rewardAd.setAdEventListener(null);if(interstitialAd!=null)interstitialAd.setAdEventListener(null);rewardAd=null;interstitialAd=null;rewardLoader=null;interstitialLoader=null;requestId=null;}
}
