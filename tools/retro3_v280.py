from pathlib import Path


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'v280 patch missing: {label}')
    return text.replace(old, new, 1)

# --- Android: remove native WebView focus rectangle and provide a native gamepad fallback ---
p = Path('app/src/main/java/ru/retro/threeinone/MainActivity.java')
s = p.read_text(encoding='utf-8')
s = must_replace(
    s,
    'import android.view.View;\nimport android.view.ViewGroup;',
    'import android.view.KeyEvent;\nimport android.view.View;\nimport android.view.ViewGroup;',
    'KeyEvent import',
)
s = must_replace(
    s,
    '            webView.setFocusable(true);\n            webView.setFocusableInTouchMode(true);\n            root.addView(webView, new FrameLayout.LayoutParams(',
    '''            webView.setFocusable(true);\n            webView.setFocusableInTouchMode(true);\n            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {\n                webView.setDefaultFocusHighlightEnabled(false);\n                root.setDefaultFocusHighlightEnabled(false);\n            }\n            root.addView(webView, new FrameLayout.LayoutParams(''',
    'disable Android focus highlight',
)

native_methods = r'''
    private String nativePadLabel(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return "BUTTON_1";
            case KeyEvent.KEYCODE_BUTTON_B: return "BUTTON_2";
            case KeyEvent.KEYCODE_BUTTON_X: return "BUTTON_3";
            case KeyEvent.KEYCODE_BUTTON_Y: return "BUTTON_4";
            case KeyEvent.KEYCODE_BUTTON_L1: return "LEFT_TOP_SHOULDER";
            case KeyEvent.KEYCODE_BUTTON_R1: return "RIGHT_TOP_SHOULDER";
            case KeyEvent.KEYCODE_BUTTON_L2: return "LEFT_BOTTOM_SHOULDER";
            case KeyEvent.KEYCODE_BUTTON_R2: return "RIGHT_BOTTOM_SHOULDER";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "SELECT";
            case KeyEvent.KEYCODE_BUTTON_START: return "START";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "LEFT_STICK";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "RIGHT_STICK";
            case KeyEvent.KEYCODE_DPAD_UP: return "DPAD_UP";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "DPAD_DOWN";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "DPAD_LEFT";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "DPAD_RIGHT";
            default: return null;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        try {
            if (webView != null && event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                String label = nativePadLabel(event.getKeyCode());
                if (label != null) {
                    final String js = "window.__retroNativePad&&window.__retroNativePad('" + label + "')";
                    webView.evaluateJavascript(js, null);
                }
            }
        } catch (Throwable ignored) {}
        return super.dispatchKeyEvent(event);
    }

'''
s = must_replace(
    s,
    '    @Override\n    public void onBackPressed() {\n',
    native_methods + '    @Override\n    public void onBackPressed() {\n',
    'native gamepad event bridge',
)
p.write_text(s, encoding='utf-8')

# --- Launcher: stable remapping and native Start/Select fallback ---
p = Path('app/src/main/assets/launcher.js')
s = p.read_text(encoding='utf-8')

# The v2.7.9 capture waited for *every* button/axis to become neutral. Noisy sticks,
# triggers or controllers with non-zero resting axes could therefore make assignment
# appear completely broken. Only block controls that were actually active when the
# capture window opened; unblock each one independently after it is released/centered.
s = must_replace(
    s,
    "captureState={id,kind,baselinePads:snapshotPads(),keyboardHandler:null,armed:kind==='keyboard'};",
    "captureState={id,kind,baselinePads:snapshotPads(),keyboardHandler:null};",
    'remove global capture arming',
)
old_poll = "function pollCaptureGamepad(){if(!captureState||captureState.kind!=='gamepad')return;const pad=getPads()[0];if(!pad)return;if(!captureState.armed){const busy=[...(pad.buttons||[])].some(b=>!!b?.pressed)||[...(pad.axes||[])].some(v=>Math.abs(v||0)>.55);if(!busy){captureState.armed=true;captureState.baselinePads=snapshotPads()}return}const base=captureState.baselinePads.find(x=>x.index===pad.index)||{buttons:[],axes:[]};for(let i=0;i<(pad.buttons||[]).length;i++){if(pad.buttons[i]?.pressed&&!base.buttons[i]){const id=captureState.id;mapperDraft[0][id]={...(mapperDraft[0][id]||{}),value2:gpButtonLabel(i)};closeCapture();updateMapperBinding(id,'gamepad');return}}for(let i=0;i<(pad.axes||[]).length;i++){const v=pad.axes[i]||0,b=base.axes[i]||0;if(Math.abs(v)>.7&&Math.abs(b)<.5){const id=captureState.id;mapperDraft[0][id]={...(mapperDraft[0][id]||{}),value2:gpAxisLabel(i,v)};closeCapture();updateMapperBinding(id,'gamepad');return}}}"
new_poll = "function pollCaptureGamepad(){if(!captureState||captureState.kind!=='gamepad')return;const pad=getPads()[0];if(!pad)return;let base=captureState.baselinePads.find(x=>x.index===pad.index);if(!base){base={index:pad.index,buttons:[...(pad.buttons||[])].map(b=>!!b?.pressed),axes:[...(pad.axes||[])].map(v=>Number(v)||0)};captureState.baselinePads.push(base)}for(let i=0;i<(pad.buttons||[]).length;i++){const pressed=!!pad.buttons[i]?.pressed;if(base.buttons[i]){if(!pressed)base.buttons[i]=false;continue}if(pressed){const id=captureState.id;mapperDraft[0][id]={...(mapperDraft[0][id]||{}),value2:gpButtonLabel(i)};closeCapture();updateMapperBinding(id,'gamepad');return}}for(let i=0;i<(pad.axes||[]).length;i++){const v=Number(pad.axes[i]||0),b=Number(base.axes[i]||0);if(Math.abs(b)>.55){if(Math.abs(v)<.35)base.axes[i]=0;continue}if(Math.abs(v)>.72){const id=captureState.id;mapperDraft[0][id]={...(mapperDraft[0][id]||{}),value2:gpAxisLabel(i,v)};closeCapture();updateMapperBinding(id,'gamepad');return}}}"
s = must_replace(s, old_poll, new_poll, 'independent physical capture gating')

# Keep the outer Gamepad API relay disabled for NES: on some Android WebViews the pad
# is exposed only to the focused iframe. Native Android KeyEvents below are the reliable
# fallback, while the iframe keeps its normal gamepad polling for gameplay.
s = s.replace("syncRuntimeSystemPad(gp||null);", "if(selected.core!=='nes')syncRuntimeSystemPad(gp||null);", 1)

native_hook = r'''window.__retroNativePad=label=>{if(!playing||selected.core!=='nes'||captureState||!label)return;try{const p=loadControlProfile('nes')?.[0]||{};if(p[2]?.value2===label)postToGame('retro-input',{index:2,value:1});if(p[3]?.value2===label)postToGame('retro-input',{index:3,value:1})}catch(_){}};
'''
s = must_replace(
    s,
    "window.addEventListener('gamepadconnected',()=>{updateGamepadStatus();gpFocus=0});",
    native_hook + "window.addEventListener('gamepadconnected',()=>{updateGamepadStatus();gpFocus=0});",
    'native mapped NES Start Select hook',
)

# No yellow focus treatment in runtime UI either. Keep a subtle neutral indication for
# gamepad navigation, but never draw a yellow frame around the play surface or controls.
s = must_replace(
    s,
    ".gpFocus{outline:3px solid #ffe45c!important;outline-offset:3px!important}",
    ".gpFocus{outline:2px solid rgba(255,255,255,.55)!important;outline-offset:2px!important}",
    'neutral runtime gamepad focus',
)
p.write_text(s, encoding='utf-8')

# --- Inner game document: suppress any browser/canvas focus painting as a second layer ---
p = Path('app/src/main/assets/game.html')
s = p.read_text(encoding='utf-8')
s = must_replace(
    s,
    '#game,.ejs_parent,.ejs_game,.ejs_canvas_parent{position:fixed!important;',
    'html:focus,body:focus,#game:focus,#game:focus-visible,.ejs_canvas:focus,.ejs_canvas:focus-visible,#game canvas:focus,#game canvas:focus-visible{outline:none!important;box-shadow:none!important}#game,.ejs_parent,.ejs_game,.ejs_canvas_parent{position:fixed!important;',
    'suppress inner focus paint',
)
p.write_text(s, encoding='utf-8')

# Same package and permanent signing certificate.
g = Path('app/build.gradle')
t = g.read_text(encoding='utf-8')
t = must_replace(t, 'versionCode 20', 'versionCode 21', 'version code')
t = must_replace(t, "versionName '2.7.9'", "versionName '2.8.0'", 'version name')
g.write_text(t, encoding='utf-8')

print('Retro 3 v2.8.0 focus highlight + native Start + stable mapper fix applied')
