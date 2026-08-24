from pathlib import Path


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'v283 patch missing: {label}')
    return text.replace(old, new, 1)

# -----------------------------------------------------------------------------
# Gradle: current RuStore Pay SDK + Yandex Mobile Ads SDK.
# -----------------------------------------------------------------------------
p = Path('settings.gradle')
s = p.read_text(encoding='utf-8')
s = must_replace(
    s,
    '''    repositories {\n        google()\n        mavenCentral()\n    }\n}\nrootProject.name''',
    '''    repositories {\n        google()\n        mavenCentral()\n        maven {\n            url = uri("https://nexus-external.vkteam.ru/repository/maven-rustore-exposed/")\n        }\n    }\n}\nrootProject.name''',
    'RuStore Maven repository',
)
p.write_text(s, encoding='utf-8')

p = Path('app/build.gradle')
s = p.read_text(encoding='utf-8')
s = must_replace(
    s,
    "dependencies {\n    implementation 'androidx.webkit:webkit:1.16.0'\n}",
    "dependencies {\n    implementation 'androidx.webkit:webkit:1.16.0'\n    implementation platform('ru.rustore.sdk:bom:2026.07.01')\n    implementation 'ru.rustore.sdk:pay'\n    implementation 'com.yandex.android:mobileads:8.2.0'\n}",
    'monetization dependencies',
)
s = must_replace(s, 'versionCode 23', 'versionCode 24', 'version code')
s = must_replace(s, "versionName '2.8.2'", "versionName '2.8.3'", 'version name')
p.write_text(s, encoding='utf-8')

# -----------------------------------------------------------------------------
# Manifest: Pay SDK console app id / deeplink and network permissions.
# Console app id is taken from the user's RuStore Console URL.
# -----------------------------------------------------------------------------
p = Path('app/src/main/AndroidManifest.xml')
s = p.read_text(encoding='utf-8')
s = must_replace(
    s,
    '<manifest xmlns:android="http://schemas.android.com/apk/res/android">',
    '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n    <uses-permission android:name="android.permission.INTERNET" />\n    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />\n    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />',
    'network permissions',
)
s = must_replace(
    s,
    'android:exported="true"\n            android:screenOrientation="sensorLandscape">',
    'android:exported="true"\n            android:launchMode="singleTop"\n            android:screenOrientation="sensorLandscape">',
    'singleTop payment return',
)
s = must_replace(
    s,
    '''            <intent-filter>\n                <action android:name="android.intent.action.MAIN" />\n                <category android:name="android.intent.category.LAUNCHER" />\n            </intent-filter>\n        </activity>''',
    '''            <intent-filter>\n                <action android:name="android.intent.action.MAIN" />\n                <category android:name="android.intent.category.LAUNCHER" />\n            </intent-filter>\n            <intent-filter>\n                <action android:name="android.intent.action.VIEW" />\n                <category android:name="android.intent.category.DEFAULT" />\n                <category android:name="android.intent.category.BROWSABLE" />\n                <data android:scheme="ru.retro.threeinone.rustore.pay" />\n            </intent-filter>\n        </activity>\n        <meta-data\n            android:name="console_app_id_value"\n            android:value="2063748892" />\n        <meta-data\n            android:name="sdk_pay_scheme_value"\n            android:value="ru.retro.threeinone.rustore.pay" />''',
    'RuStore Pay manifest metadata',
)
p.write_text(s, encoding='utf-8')

# -----------------------------------------------------------------------------
# MainActivity: native monetization + raw gamepad scan-code bridge.
# -----------------------------------------------------------------------------
p = Path('app/src/main/java/ru/retro/threeinone/MainActivity.java')
s = p.read_text(encoding='utf-8')

s = must_replace(
    s,
    'import android.content.Intent;\n',
    'import android.content.Intent;\nimport android.content.SharedPreferences;\n',
    'SharedPreferences import',
)
s = must_replace(
    s,
    'import android.webkit.RenderProcessGoneDetail;\n',
    'import android.webkit.JavascriptInterface;\nimport android.webkit.RenderProcessGoneDetail;\n',
    'JavascriptInterface import',
)

imports = '''\nimport com.yandex.mobile.ads.common.AdError;\nimport com.yandex.mobile.ads.common.AdRequest;\nimport com.yandex.mobile.ads.common.AdRequestError;\nimport com.yandex.mobile.ads.common.ImpressionData;\nimport com.yandex.mobile.ads.interstitial.InterstitialAd;\nimport com.yandex.mobile.ads.interstitial.InterstitialAdEventListener;\nimport com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener;\nimport com.yandex.mobile.ads.interstitial.InterstitialAdLoader;\n\nimport ru.rustore.sdk.pay.RuStorePayClient;\nimport ru.rustore.sdk.pay.model.PreferredPurchaseType;\nimport ru.rustore.sdk.pay.model.ProductId;\nimport ru.rustore.sdk.pay.model.ProductPurchase;\nimport ru.rustore.sdk.pay.model.ProductPurchaseParams;\nimport ru.rustore.sdk.pay.model.SdkTheme;\n'''
s = must_replace(
    s,
    'import androidx.webkit.WebViewAssetLoader;\n',
    'import androidx.webkit.WebViewAssetLoader;\n' + imports,
    'monetization imports',
)

s = must_replace(
    s,
    '    private static final String START_URL = "https://appassets.androidplatform.net/assets/index.html";\n',
    '''    private static final String START_URL = "https://appassets.androidplatform.net/assets/index.html";\n    private static final String PREMIUM_PRODUCT_ID = "3v1prem147";\n    private static final String INTERSTITIAL_AD_UNIT_ID = "R-M-19800317-1";\n''',
    'monetization constants',
)
s = must_replace(
    s,
    '    private WebViewAssetLoader assetLoader;\n',
    '''    private WebViewAssetLoader assetLoader;\n    private SharedPreferences monetizationPrefs;\n    private volatile boolean premiumEnabled;\n    private InterstitialAdLoader interstitialAdLoader;\n    private InterstitialAd interstitialAd;\n    private boolean interstitialLoading;\n    private boolean launchWaitingForAd;\n''',
    'monetization fields',
)

s = must_replace(
    s,
    '        createWebView();\n        root.post(this::enterImmersiveSafely);',
    '''        createWebView();\n        initializeMonetization();\n        proceedRuStoreIntent(getIntent());\n        root.post(this::enterImmersiveSafely);''',
    'initialize monetization',
)

s = must_replace(
    s,
    '            webView.setWebChromeClient(new WebChromeClient() {',
    '            webView.addJavascriptInterface(new RetroNativeBridge(), "RetroNative");\n\n            webView.setWebChromeClient(new WebChromeClient() {',
    'native JS bridge',
)

# Capture every key produced by a real gamepad/joystick source. Cheap controllers often
# expose one of their small center buttons as an unusual Android key code.  Passing the
# hardware scan code as well lets the mapper distinguish buttons even when keyCode is 0
# or two controls share a generic Android key name.
s = must_replace(
    s,
    '''        if (!fromPad) return false;\n        final int code = event.getKeyCode();\n        return KeyEvent.isGamepadButton(code) ||\n                code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN ||\n                code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT ||\n                code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU ||\n                code == KeyEvent.KEYCODE_ENTER;''',
    '''        return fromPad;''',
    'accept raw gamepad keys',
)
s = must_replace(
    s,
    '''                    final int code = event.getKeyCode();\n                    final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;\n                    final String js = "window.__retroNativePad&&window.__retroNativePad(" + code + "," + (down ? "true" : "false") + ")";\n                    webView.evaluateJavascript(js, null);''',
    '''                    final int code = event.getKeyCode();\n                    final int scanCode = event.getScanCode();\n                    final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;\n                    final String js = "window.__retroNativePad&&window.__retroNativePad(" + code + "," + scanCode + "," + (down ? "true" : "false") + ")";\n                    webView.evaluateJavascript(js, null);''',
    'raw key + scan bridge',
)

native_monetization = r'''
    private final class RetroNativeBridge {
        @JavascriptInterface
        public boolean isPremium() {
            return premiumEnabled;
        }

        @JavascriptInterface
        public void refreshPremium() {
            runOnUiThread(MainActivity.this::refreshPremiumEntitlement);
        }

        @JavascriptInterface
        public void buyPremium() {
            runOnUiThread(MainActivity.this::purchasePremium);
        }

        @JavascriptInterface
        public void requestGameLaunch() {
            runOnUiThread(MainActivity.this::requestGameLaunchWithAd);
        }
    }

    private void initializeMonetization() {
        monetizationPrefs = getSharedPreferences("retro3_monetization", MODE_PRIVATE);
        premiumEnabled = monetizationPrefs.getBoolean("premium", false);
        preloadInterstitialAd();
        refreshPremiumEntitlement();
    }

    private String jsQuote(String value) {
        if (value == null) return "\"\"";
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n") + "\"";
    }

    private void evalJs(String js) {
        try {
            if (webView != null) webView.evaluateJavascript(js, null);
        } catch (Throwable ignored) {}
    }

    private void notifyPremium(String message) {
        evalJs("window.__retroPremiumState&&window.__retroPremiumState(" +
                (premiumEnabled ? "true" : "false") + "," + jsQuote(message) + ")");
    }

    private void setPremiumEnabled(boolean enabled, String message) {
        premiumEnabled = enabled;
        if (monetizationPrefs != null) {
            monetizationPrefs.edit().putBoolean("premium", enabled).apply();
        }
        if (enabled) destroyInterstitialAd();
        else preloadInterstitialAd();
        notifyPremium(message);
    }

    private void proceedRuStoreIntent(Intent intent) {
        if (intent == null) return;
        try {
            RuStorePayClient.Companion.getInstance().getIntentInteractor()
                    .proceedIntent(intent, SdkTheme.DARK);
        } catch (Throwable ignored) {}
    }

    private void refreshPremiumEntitlement() {
        try {
            RuStorePayClient.Companion.getInstance().getPurchaseInteractor()
                    .getPurchases(null, null, null)
                    .addOnSuccessListener(purchases -> {
                        boolean active = false;
                        for (Object item : purchases) {
                            if (!(item instanceof ProductPurchase)) continue;
                            ProductPurchase purchase = (ProductPurchase) item;
                            if (!PREMIUM_PRODUCT_ID.equals(purchase.getProductId().getValue())) continue;
                            String status = String.valueOf(purchase.getStatus());
                            if ("CONFIRMED".equals(status) || "PAID".equals(status)) {
                                active = true;
                                break;
                            }
                        }
                        setPremiumEnabled(active, active ? "Premium активирован — реклама отключена" : "");
                    })
                    .addOnFailureListener(error -> notifyPremium(""));
        } catch (Throwable ignored) {
            notifyPremium("");
        }
    }

    private void purchasePremium() {
        try {
            ProductPurchaseParams params = new ProductPurchaseParams(
                    new ProductId(PREMIUM_PRODUCT_ID), null, null, null, null, null);
            RuStorePayClient.Companion.getInstance().getPurchaseInteractor()
                    .purchase(params, PreferredPurchaseType.ONE_STEP, SdkTheme.DARK, null)
                    .addOnSuccessListener(result -> {
                        if (result != null && result.getProductId() != null &&
                                PREMIUM_PRODUCT_ID.equals(result.getProductId().getValue())) {
                            setPremiumEnabled(true, "Premium куплен — реклама отключена");
                        } else {
                            refreshPremiumEntitlement();
                        }
                    })
                    .addOnFailureListener(error -> notifyPremium("Покупка не завершена"));
        } catch (Throwable error) {
            notifyPremium("Не удалось открыть оплату RuStore");
        }
    }

    private void preloadInterstitialAd() {
        if (premiumEnabled || interstitialLoading || interstitialAd != null) return;
        try {
            if (interstitialAdLoader == null) interstitialAdLoader = new InterstitialAdLoader(this);
            interstitialLoading = true;
            AdRequest request = new AdRequest.Builder(INTERSTITIAL_AD_UNIT_ID).build();
            interstitialAdLoader.loadAd(request, new InterstitialAdLoadListener() {
                @Override
                public void onAdLoaded(InterstitialAd ad) {
                    interstitialLoading = false;
                    interstitialAd = ad;
                    if (launchWaitingForAd) showInterstitialThenContinue();
                }

                @Override
                public void onAdFailedToLoad(AdRequestError error) {
                    interstitialLoading = false;
                    if (launchWaitingForAd) continueGameLaunch();
                }
            });
        } catch (Throwable ignored) {
            interstitialLoading = false;
            if (launchWaitingForAd) continueGameLaunch();
        }
    }

    private void requestGameLaunchWithAd() {
        if (premiumEnabled) {
            continueGameLaunch();
            return;
        }
        launchWaitingForAd = true;
        if (interstitialAd != null) {
            showInterstitialThenContinue();
            return;
        }
        preloadInterstitialAd();
        if (root != null) {
            root.postDelayed(() -> {
                if (launchWaitingForAd && interstitialAd == null) continueGameLaunch();
            }, 2800L);
        }
    }

    private void showInterstitialThenContinue() {
        if (!launchWaitingForAd) return;
        final InterstitialAd ad = interstitialAd;
        if (ad == null || premiumEnabled) {
            continueGameLaunch();
            return;
        }
        try {
            ad.setAdEventListener(new InterstitialAdEventListener() {
                @Override public void onAdShown() {}

                @Override
                public void onAdFailedToShow(AdError adError) {
                    destroyInterstitialAd();
                    continueGameLaunch();
                    preloadInterstitialAd();
                }

                @Override
                public void onAdDismissed() {
                    destroyInterstitialAd();
                    continueGameLaunch();
                    preloadInterstitialAd();
                }

                @Override public void onAdClicked() {}
                @Override public void onAdImpression(ImpressionData impressionData) {}
            });
            ad.show(this);
        } catch (Throwable ignored) {
            destroyInterstitialAd();
            continueGameLaunch();
            preloadInterstitialAd();
        }
    }

    private void continueGameLaunch() {
        if (!launchWaitingForAd) {
            // Premium path can arrive without the wait flag.
            evalJs("window.__retroContinueLaunch&&window.__retroContinueLaunch()");
            return;
        }
        launchWaitingForAd = false;
        evalJs("window.__retroContinueLaunch&&window.__retroContinueLaunch()");
    }

    private void destroyInterstitialAd() {
        try {
            if (interstitialAd != null) interstitialAd.setAdEventListener(null);
        } catch (Throwable ignored) {}
        interstitialAd = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        proceedRuStoreIntent(intent);
        refreshPremiumEntitlement();
    }

'''
s = must_replace(
    s,
    '    @Override\n    public void onBackPressed() {\n',
    native_monetization + '    @Override\n    public void onBackPressed() {\n',
    'native monetization methods',
)

s = must_replace(
    s,
    '    protected void onDestroy() {\n        if (fileCallback != null) {',
    '    protected void onDestroy() {\n        destroyInterstitialAd();\n        interstitialAdLoader = null;\n        if (fileCallback != null) {',
    'ad cleanup',
)
p.write_text(s, encoding='utf-8')

# -----------------------------------------------------------------------------
# UI: premium card in Settings.
# -----------------------------------------------------------------------------
p = Path('app/src/main/assets/index.html')
s = p.read_text(encoding='utf-8')
premium_html = '''\n      <div class="premium-box" id="premiumBox">\n        <div><strong>Premium</strong><small id="premiumText">Отключает рекламу перед запуском игр</small></div>\n        <button class="secondary-button focusable" id="premiumButton" data-action="premium">Купить Premium</button>\n      </div>\n'''
s = must_replace(
    s,
    '      <div class="controller-box">',
    premium_html + '\n      <div class="controller-box">',
    'premium settings card',
)
p.write_text(s, encoding='utf-8')

p = Path('app/src/main/assets/styles.css')
s = p.read_text(encoding='utf-8')
if '/* Premium monetization */' not in s:
    s += '''\n/* Premium monetization */\n.premium-box{display:flex;align-items:center;justify-content:space-between;gap:14px;margin-top:12px;padding:12px 14px;border:1px solid #3a4352;border-radius:14px;background:#121720}.premium-box>div{display:flex;min-width:0;flex-direction:column;gap:3px}.premium-box strong{font-size:13px;color:#fff}.premium-box small{font-size:9px;color:#929cab}.premium-box.active{border-color:#5d7656;background:#142017}.premium-box.active strong{color:#d9ffd4}.premium-box button{flex:0 0 auto}.premium-box.active button{opacity:.72}\n'''
p.write_text(s, encoding='utf-8')

# -----------------------------------------------------------------------------
# Launcher: persistent BIOS, premium UI/ad gate, and scan-code gamepad mapping.
# -----------------------------------------------------------------------------
p = Path('app/src/main/assets/launcher.js')
s = p.read_text(encoding='utf-8')

# Persist BIOS in the same IndexedDB as ROMs. Bump schema version and add a second store.
s = must_replace(
    s,
    "const LIB_DB='retro3-rom-library',LIB_STORE='games',LIB_VERSION=1;",
    "const LIB_DB='retro3-rom-library',LIB_STORE='games',BIOS_STORE='bios',LIB_VERSION=2;",
    'BIOS store constants',
)
s = must_replace(
    s,
    "function openLibraryDb(){return new Promise((resolve,reject)=>{const q=indexedDB.open(LIB_DB,LIB_VERSION);q.onupgradeneeded=()=>{const db=q.result;if(!db.objectStoreNames.contains(LIB_STORE))db.createObjectStore(LIB_STORE,{keyPath:'id'})};q.onsuccess=()=>resolve(q.result);q.onerror=()=>reject(q.error||Error('Не удалось открыть библиотеку'))})}",
    "function openLibraryDb(){return new Promise((resolve,reject)=>{const q=indexedDB.open(LIB_DB,LIB_VERSION);q.onupgradeneeded=()=>{const db=q.result;if(!db.objectStoreNames.contains(LIB_STORE))db.createObjectStore(LIB_STORE,{keyPath:'id'});if(!db.objectStoreNames.contains(BIOS_STORE))db.createObjectStore(BIOS_STORE,{keyPath:'id'})};q.onsuccess=()=>resolve(q.result);q.onerror=()=>reject(q.error||Error('Не удалось открыть библиотеку'))})}",
    'BIOS object store creation',
)

bios_helpers = r'''function biosFileId(file){return `${String(file?.name||'bios').toLowerCase()}:${Number(file?.size)||0}`}
async function biosStoredAll(){const db=await openLibraryDb();try{return await idbReq(db.transaction(BIOS_STORE,'readonly').objectStore(BIOS_STORE).getAll())}finally{db.close()}}
async function biosStoreFile(file){const db=await openLibraryDb();try{await idbReq(db.transaction(BIOS_STORE,'readwrite').objectStore(BIOS_STORE).put({id:biosFileId(file),name:file.name,size:file.size,type:file.type||'application/octet-stream',blob:file,addedAt:Date.now()}))}finally{db.close()}}
async function biosDeleteStored(file){const db=await openLibraryDb();try{await idbReq(db.transaction(BIOS_STORE,'readwrite').objectStore(BIOS_STORE).delete(biosFileId(file)))}finally{db.close()}}
async function biosClearStored(){const db=await openLibraryDb();try{await idbReq(db.transaction(BIOS_STORE,'readwrite').objectStore(BIOS_STORE).clear())}finally{db.close()}}
async function restoreBiosFiles(){try{const rows=await biosStoredAll();biosFiles=rows.filter(r=>r?.blob&&r?.size>0).map(r=>r.blob instanceof File?r.blob:new File([r.blob],r.name,{type:r.type||'application/octet-stream'}));updateBiosState()}catch(_){updateBiosState()}}
'''
needle = "async function libraryAll(){"
pos = s.find(needle)
if pos < 0:
    raise SystemExit('v283 patch missing: BIOS helper insertion')
s = s[:pos] + bios_helpers + s[pos:]

old_pick = "function pickBios(){if(selected.core!=='psx')return;chooseFiles({accept:'.bin,.rom,.bios,application/octet-stream',multiple:true,onFiles(files){const valid=files.filter(f=>f.size>0);if(!valid.length)return showToast('Не удалось добавить BIOS: файлы пустые.',true);for(const file of valid)if(!biosFiles.some(o=>o.name===file.name&&o.size===file.size))biosFiles.push(file);updateBiosState();showToast(`BIOS добавлено: ${valid.length}`)}})}"
new_pick = "function pickBios(){if(selected.core!=='psx')return;chooseFiles({accept:'.bin,.rom,.bios,application/octet-stream',multiple:true,onFiles:async files=>{const valid=files.filter(f=>f.size>0);if(!valid.length)return showToast('Не удалось добавить BIOS: файлы пустые.',true);let added=0;for(const file of valid){if(!biosFiles.some(o=>o.name===file.name&&o.size===file.size)){biosFiles.push(file);added++}try{await biosStoreFile(file)}catch(_){}}updateBiosState();showToast(`BIOS сохранено: ${added||valid.length}`)}})}"
s = must_replace(s, old_pick, new_pick, 'persistent BIOS picker')
s = must_replace(
    s,
    "function clearBios(){biosFiles=[];updateBiosState();showToast('Список BIOS очищен')}",
    "async function clearBios(){biosFiles=[];try{await biosClearStored()}catch(_){}updateBiosState();showToast('Список BIOS очищен')}",
    'persistent BIOS clear',
)
s = must_replace(
    s,
    "function removeBios(index){const i=Number(index);if(Number.isInteger(i)&&i>=0&&i<biosFiles.length){biosFiles.splice(i,1);updateBiosState()}}",
    "async function removeBios(index){const i=Number(index);if(Number.isInteger(i)&&i>=0&&i<biosFiles.length){const file=biosFiles[i];biosFiles.splice(i,1);try{await biosDeleteStored(file)}catch(_){}updateBiosState()}}",
    'persistent BIOS remove',
)

# Premium UI.
s = must_replace(
    s,
    "function openSettings(){updateSettingsForm();updateGamepadStatus();settingsModal.hidden=false;gpFocus=0;vibrate()}",
    "function openSettings(){updateSettingsForm();updateGamepadStatus();try{window.RetroNative?.refreshPremium?.()}catch(_){}settingsModal.hidden=false;gpFocus=0;vibrate()}",
    'premium refresh on settings',
)
premium_js = r'''let premiumState=false,pendingLaunch=null;
function updatePremiumUi(on,message=''){premiumState=!!on;const box=$('#premiumBox'),text=$('#premiumText'),btn=$('#premiumButton');box?.classList.toggle('active',premiumState);if(text)text.textContent=premiumState?'Активирован · реклама отключена':'Отключает рекламу перед запуском игр';if(btn){btn.textContent=premiumState?'Premium активирован':'Купить Premium';btn.disabled=premiumState}if(message)showToast(message,true)}
function buyPremium(){if(premiumState)return showToast('Premium уже активирован');try{if(window.RetroNative?.buyPremium)window.RetroNative.buyPremium();else showToast('Покупка доступна только в Android-приложении.',true)}catch(_){showToast('Не удалось открыть покупку.',true)}}
window.__retroPremiumState=(on,message)=>updatePremiumUi(!!on,String(message||''));
window.__retroContinueLaunch=()=>{const pending=pendingLaunch;pendingLaunch=null;if(pending)startGameNow(pending.file,pending.opts)};
'''
insert_after = "let manualPaused=false,mapperDraft=null,captureState=null;\n"
s = must_replace(s, insert_after, insert_after + premium_js, 'premium JS state')

# Gate every user-initiated game launch behind native interstitial. NES compatibility-core
# retry bypasses the gate so one launch never causes two ads.
s = must_replace(s, 'function startGame(file,opts={}){if(playing)return;', 'function startGameNow(file,opts={}){if(playing)return;', 'rename raw game start')
start_wrapper = r'''function startGame(file,opts={}){if(playing||pendingLaunch)return;if(opts?.nesCore)return startGameNow(file,opts);try{if(window.RetroNative?.requestGameLaunch){pendingLaunch={file,opts};window.RetroNative.requestGameLaunch();return}}catch(_){}startGameNow(file,opts)}
'''
idx = s.find('function startGameNow(file,opts={})')
if idx < 0:
    raise SystemExit('v283 patch missing: start wrapper insertion')
s = s[:idx] + start_wrapper + s[idx:]

s = must_replace(
    s,
    "else if(a==='bios-remove')removeBios(e.dataset.index);else if(a==='bluetooth')",
    "else if(a==='bios-remove')removeBios(e.dataset.index);else if(a==='premium')buyPremium();else if(a==='bluetooth')",
    'premium click action',
)

# Native gamepad event now carries keyCode + hardware scanCode. Manual mapper stores the
# scan token when available. Runtime accepts both the scan token and older key-code tokens.
s = must_replace(
    s,
    "const ANDROID_PAD_NAMES={19:'D-pad ↑',20:'D-pad ↓',21:'D-pad ←',22:'D-pad →',96:'Android A',97:'Android B',98:'Android C',99:'Android X',100:'Android Y',101:'Android Z',102:'Android L1',103:'Android R1',104:'Android L2',105:'Android R2',106:'Android L3',107:'Android R3',108:'Android Start',109:'Android Select',110:'Android Mode'};function gamepadDisplay(v){if(!v)return'—';if(GP_DISPLAY[v])return GP_DISPLAY[v];const native=/^ANDROID_KEY_(\\d+)$/.exec(String(v));if(native)return ANDROID_PAD_NAMES[Number(native[1])]||`Android key ${native[1]}`;",
    "const ANDROID_PAD_NAMES={19:'D-pad ↑',20:'D-pad ↓',21:'D-pad ←',22:'D-pad →',96:'Android A',97:'Android B',98:'Android C',99:'Android X',100:'Android Y',101:'Android Z',102:'Android L1',103:'Android R1',104:'Android L2',105:'Android R2',106:'Android L3',107:'Android R3',108:'Android Start',109:'Android Select',110:'Android Mode'};function gamepadDisplay(v){if(!v)return'—';if(GP_DISPLAY[v])return GP_DISPLAY[v];const scan=/^ANDROID_SCAN_(\\d+)$/.exec(String(v));if(scan)return`Кнопка ${scan[1]}`;const native=/^ANDROID_KEY_(\\d+)$/.exec(String(v));if(native)return ANDROID_PAD_NAMES[Number(native[1])]||`Android key ${native[1]}`;",
    'scan-code display',
)
old_native = "window.__retroNativePad=(code,down)=>{const n=Number(code);if(!Number.isFinite(n))return;const token=`ANDROID_KEY_${n}`;if(captureState?.kind==='gamepad'){if(!down)return;const id=captureState.id;if(!mapperDraft?.[0]?.[id])mapperDraft[0][id]={};mapperDraft[0][id]={...mapperDraft[0][id],value2:token};closeCapture();updateMapperBinding(id,'gamepad');return}if(!playing)return;try{const p=loadMapperProfile(selected.core)?.[0]||{};if(selected.core==='psx'){const auto=(id,keyCode)=>{const cfg=p[id]||{},explicit=String(cfg.value2||'');if(explicit)return false;const autoToken=`ANDROID_KEY_${keyCode}`;const usedElsewhere=Object.entries(p).some(([other,c])=>Number(other)!==id&&c?.value2===autoToken);if(usedElsewhere)return false;if(n===keyCode){postToGame('retro-input',{index:id,value:down?1:0});return true}return false};if(auto(2,109)||auto(3,108))return}for(const [id,cfg] of Object.entries(p)){if(cfg?.value2!==token)continue;const k=Number(id);if(k===24){if(down)quickAction('retro-quick-save');continue}if(k===25){if(down)quickAction('retro-quick-load');continue}if(k>=0&&k<24)postToGame('retro-input',{index:k,value:down?1:0})}}catch(_){}};"
new_native = "window.__retroNativePad=(code,scanCode,down)=>{const n=Number(code),sc=Number(scanCode);if(!Number.isFinite(n))return;const keyToken=`ANDROID_KEY_${n}`,scanToken=sc>0?`ANDROID_SCAN_${sc}`:'',captureToken=scanToken||keyToken;if(captureState?.kind==='gamepad'){if(!down)return;const id=captureState.id;if(!mapperDraft?.[0]?.[id])mapperDraft[0][id]={};mapperDraft[0][id]={...mapperDraft[0][id],value2:captureToken};closeCapture();updateMapperBinding(id,'gamepad');return}if(!playing)return;try{const p=loadMapperProfile(selected.core)?.[0]||{};if(selected.core==='psx'){const auto=(id,keyCode)=>{const cfg=p[id]||{},explicit=String(cfg.value2||'');if(explicit)return false;const autoToken=`ANDROID_KEY_${keyCode}`;const usedElsewhere=Object.entries(p).some(([other,c])=>Number(other)!==id&&c?.value2===autoToken);if(usedElsewhere)return false;if(n===keyCode){postToGame('retro-input',{index:id,value:down?1:0});return true}return false};if(auto(2,109)||auto(3,108))return}for(const [id,cfg] of Object.entries(p)){if(cfg?.value2!==keyToken&&(!scanToken||cfg?.value2!==scanToken))continue;const k=Number(id);if(k===24){if(down)quickAction('retro-quick-save');continue}if(k===25){if(down)quickAction('retro-quick-load');continue}if(k>=0&&k<24)postToGame('retro-input',{index:k,value:down?1:0})}}catch(_){}};"
s = must_replace(s, old_native, new_native, 'raw scan-code gamepad mapper')

# Initial native state and BIOS restore after DOM is ready.
s = must_replace(
    s,
    "window.addEventListener('gamepadconnected',()=>{updateGamepadStatus();gpFocus=0});",
    "try{updatePremiumUi(!!window.RetroNative?.isPremium?.())}catch(_){}restoreBiosFiles();window.addEventListener('gamepadconnected',()=>{updateGamepadStatus();gpFocus=0});",
    'startup premium + BIOS restore',
)

p.write_text(s, encoding='utf-8')

print('Retro 3 v2.8.3: persistent BIOS + RuStore Premium + Yandex interstitial + raw gamepad scan codes applied')
