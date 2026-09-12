from pathlib import Path


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'v274 patch missing: {label}')
    return text.replace(old, new, 1)

# --- game iframe: NES virtual controls + safe area ---
p = Path('app/src/main/assets/game.html')
s = p.read_text(encoding='utf-8')

s = must_replace(
    s,
    'html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#000;font-family:Inter,Roboto,system-ui,sans-serif}',
    ':root{--android-safe-left:0px;--android-safe-right:0px;--android-safe-top:0px;--android-safe-bottom:0px;--retro-safe-left:max(env(safe-area-inset-left,0px),var(--android-safe-left));--retro-safe-right:max(env(safe-area-inset-right,0px),var(--android-safe-right));--retro-safe-top:max(env(safe-area-inset-top,0px),var(--android-safe-top));--retro-safe-bottom:max(env(safe-area-inset-bottom,0px),var(--android-safe-bottom))}html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#000;font-family:Inter,Roboto,system-ui,sans-serif}',
    'game safe area vars',
)
s = must_replace(
    s,
    'body.layout-edit .ejs_virtualGamepad_parent{display:block!important;opacity:1!important;z-index:999990!important}',
    '.ejs_virtualGamepad_parent{left:var(--retro-safe-left)!important;right:var(--retro-safe-right)!important;width:auto!important;bottom:max(50px,var(--retro-safe-bottom))!important}body.layout-edit .ejs_virtualGamepad_parent{display:block!important;opacity:1!important;z-index:999990!important}',
    'virtual controls safe area',
)
s = must_replace(
    s,
    '#layoutEditor{position:fixed;z-index:1000000;left:50%;top:12px;',
    '#layoutEditor{position:fixed;z-index:1000000;left:50%;top:max(12px,var(--retro-safe-top));',
    'layout editor cutout',
)
s = must_replace(
    s,
    "let stickBridgeRAF=0,stickState=[false,false,false,false],layoutEditing=false,layoutHandlers=[],defaultLayoutStyles=[];",
    "let stickBridgeRAF=0,stickState=[false,false,false,false],layoutEditing=false,layoutHandlers=[],defaultLayoutStyles=[],nesVirtualHandlers=[];",
    'NES virtual handler state',
)

# v2.7 UI advertised FDS, but the iframe verifier still rejected unpacked .fds files.
s = must_replace(
    s,
    "core==='nes'?['nes','unif','unf','zip','7z','rar']",
    "core==='nes'?['nes','fds','unif','unf','zip','7z','rar']",
    'NES FDS verifier',
)

needle = 'const INPUT_DEFAULTS={'
bridge = r'''function removeNesVirtualBridge(){for(const [el,type,fn,opt] of nesVirtualHandlers){try{el.removeEventListener(type,fn,opt)}catch(_){}}nesVirtualHandlers=[]}
function nesListen(el,type,fn,opt=true){el.addEventListener(type,fn,opt);nesVirtualHandlers.push([el,type,fn,opt])}
function nesTouchPoint(e,el){const t=(e.targetTouches&&e.targetTouches[0])||(e.touches&&e.touches[0])||(e.changedTouches&&e.changedTouches[0]);if(!t)return null;const r=el.getBoundingClientRect();return{x:(t.clientX-(r.left+r.width/2))/Math.max(1,r.width/2),y:(t.clientY-(r.top+r.height/2))/Math.max(1,r.height/2)}}
function nesReleaseDirections(){manualInput(4,0);manualInput(5,0);manualInput(6,0);manualInput(7,0)}
function nesDirectionsFromTouch(e,el){const p=nesTouchPoint(e,el);if(!p){nesReleaseDirections();return}const dead=.17;manualInput(4,p.y<-dead);manualInput(5,p.y>dead);manualInput(6,p.x<-dead);manualInput(7,p.x>dead)}
function installNesVirtualBridge(){if(currentCore!=='nes'||!startConfirmed)return;removeNesVirtualBridge();configureNesJoypad();const bindButton=(selector,id)=>{const el=document.querySelector(selector);if(!el)return;el.style.setProperty('touch-action','none','important');const down=e=>{if(layoutEditing)return;e.preventDefault();e.stopImmediatePropagation();manualInput(id,1)};const up=e=>{if(layoutEditing)return;e.preventDefault();e.stopImmediatePropagation();manualInput(id,0)};nesListen(el,'touchstart',down,true);nesListen(el,'touchend',up,true);nesListen(el,'touchcancel',up,true)};bindButton('.b_a',8);bindButton('.b_b',0);bindButton('.b_start',3);bindButton('.b_select',2);const bindPad=selector=>{const el=document.querySelector(selector);if(!el)return;el.style.setProperty('touch-action','none','important');const move=e=>{if(layoutEditing)return;e.preventDefault();e.stopImmediatePropagation();nesDirectionsFromTouch(e,el)};const end=e=>{if(layoutEditing)return;e.preventDefault();e.stopImmediatePropagation();nesReleaseDirections()};nesListen(el,'touchstart',move,true);nesListen(el,'touchmove',move,true);nesListen(el,'touchend',end,true);nesListen(el,'touchcancel',end,true)};bindPad('.b_retro_dpad');bindPad('.b_retro_stick')}
'''
s = must_replace(s, needle, bridge + needle, 'direct NES virtual input bridge')

s = must_replace(
    s,
    "if(currentCore==='nes'){configureNesJoypad();setTimeout(configureNesJoypad,80);setTimeout(configureNesJoypad,300)}applySettings(currentSettings);if(currentProfile)applyControlProfile(currentProfile);startUnifiedInputBridge();",
    "if(currentCore==='nes'){configureNesJoypad();setTimeout(configureNesJoypad,80);setTimeout(configureNesJoypad,300)}applySettings(currentSettings);if(currentProfile)applyControlProfile(currentProfile);startUnifiedInputBridge();if(currentCore==='nes'){installNesVirtualBridge();setTimeout(installNesVirtualBridge,120);setTimeout(installNesVirtualBridge,500)}",
    'activate NES virtual bridge',
)
s = must_replace(
    s,
    "else if(d.type==='retro-stop'){clearTimeout(watchdog);stopUnifiedInputBridge();try{window.EJS_emulator?.gameManager?.exit?.()}catch(_){}}",
    "else if(d.type==='retro-stop'){clearTimeout(watchdog);removeNesVirtualBridge();nesReleaseDirections();stopUnifiedInputBridge();try{window.EJS_emulator?.gameManager?.exit?.()}catch(_){}}",
    'cleanup NES virtual bridge',
)
p.write_text(s, encoding='utf-8')

# --- launcher overlays: respect top/side/bottom cutouts too ---
p = Path('app/src/main/assets/launcher.js')
s = p.read_text(encoding='utf-8')
s = must_replace(
    s,
    '#runtimeBar{position:absolute;inset:10px max(10px,env(safe-area-inset-right)) auto max(10px,env(safe-area-inset-left));',
    '#runtimeBar{position:absolute;inset:max(10px,env(safe-area-inset-top)) max(10px,env(safe-area-inset-right)) auto max(10px,env(safe-area-inset-left));',
    'runtime toolbar cutout top',
)
s = must_replace(
    s,
    '.overlayScreen{position:absolute;inset:0;z-index:100006;background:rgba(0,0,0,.76);backdrop-filter:blur(9px);display:grid;place-items:center;padding:12px}',
    '.overlayScreen{position:absolute;inset:0;z-index:100006;background:rgba(0,0,0,.76);backdrop-filter:blur(9px);display:grid;place-items:center;padding:max(12px,env(safe-area-inset-top)) max(12px,env(safe-area-inset-right)) max(12px,env(safe-area-inset-bottom)) max(12px,env(safe-area-inset-left))}',
    'runtime overlay cutout padding',
)
p.write_text(s, encoding='utf-8')

# --- Android: make display-cutout handling deterministic instead of relying on WebView CSS env() ---
p = Path('app/src/main/java/ru/retro/threeinone/MainActivity.java')
s = p.read_text(encoding='utf-8')
s = must_replace(
    s,
    'import android.view.ViewGroup;\nimport android.view.WindowInsets;',
    'import android.view.ViewGroup;\nimport android.view.WindowInsets;\nimport android.view.WindowManager;',
    'WindowManager import',
)
s = must_replace(
    s,
    '        setContentView(root);\n\n        assetLoader = new WebViewAssetLoader.Builder()',
    '        setContentView(root);\n        configureDisplayCutout();\n\n        assetLoader = new WebViewAssetLoader.Builder()',
    'install cutout handler',
)
method = r'''
    private void configureDisplayCutout() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        try {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                int left = 0, top = 0, right = 0, bottom = 0;
                try {
                    android.view.DisplayCutout cutout = insets.getDisplayCutout();
                    if (cutout != null) {
                        left = cutout.getSafeInsetLeft();
                        top = cutout.getSafeInsetTop();
                        right = cutout.getSafeInsetRight();
                        bottom = cutout.getSafeInsetBottom();
                    }
                } catch (Throwable ignored) {}
                if (view.getPaddingLeft() != left || view.getPaddingTop() != top ||
                        view.getPaddingRight() != right || view.getPaddingBottom() != bottom) {
                    view.setPadding(left, top, right, bottom);
                }
                return insets;
            });
            root.requestApplyInsets();
        } catch (Throwable ignored) {
            // A cutout is cosmetic; never let inset handling crash emulation.
        }
    }

'''
s = must_replace(
    s,
    '    private void enterImmersiveSafely() {\n',
    method + '    private void enterImmersiveSafely() {\n',
    'native cutout method',
)
p.write_text(s, encoding='utf-8')

# Update in place with the same release certificate.
g = Path('app/build.gradle')
t = g.read_text(encoding='utf-8')
t = must_replace(t, 'versionCode 14', 'versionCode 15', 'version code')
t = must_replace(t, "versionName '2.7.3'", "versionName '2.7.4'", 'version name')
g.write_text(t, encoding='utf-8')

print('Retro 3 v2.7.4 NES virtual input + cutout fix applied')
