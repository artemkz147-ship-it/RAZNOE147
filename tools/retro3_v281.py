from pathlib import Path


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'v281 patch missing: {label}')
    return text.replace(old, new, 1)

# --- Android: expose the real physical gamepad key codes to JavaScript ---
p = Path('app/src/main/java/ru/retro/threeinone/MainActivity.java')
s = p.read_text(encoding='utf-8')
s = must_replace(
    s,
    'import android.view.KeyEvent;\nimport android.view.View;',
    'import android.view.InputDevice;\nimport android.view.KeyEvent;\nimport android.view.View;',
    'InputDevice import',
)
old_dispatch = '''    @Override\n    public boolean dispatchKeyEvent(KeyEvent event) {\n        try {\n            if (webView != null && event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {\n                String label = nativePadLabel(event.getKeyCode());\n                if (label != null) {\n                    final String js = "window.__retroNativePad&&window.__retroNativePad('" + label + "')";\n                    webView.evaluateJavascript(js, null);\n                }\n            }\n        } catch (Throwable ignored) {}\n        return super.dispatchKeyEvent(event);\n    }\n'''
new_dispatch = '''    private boolean isNativeGamepadKey(KeyEvent event) {\n        if (event == null) return false;\n        final int source = event.getSource();\n        final boolean fromPad =\n                (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||\n                (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||\n                (source & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;\n        if (!fromPad) return false;\n        final int code = event.getKeyCode();\n        return KeyEvent.isGamepadButton(code) ||\n                code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN ||\n                code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT ||\n                code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU ||\n                code == KeyEvent.KEYCODE_ENTER;\n    }\n\n    @Override\n    public boolean dispatchKeyEvent(KeyEvent event) {\n        try {\n            if (webView != null && isNativeGamepadKey(event) &&\n                    (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP)) {\n                if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() == 0) {\n                    final int code = event.getKeyCode();\n                    final boolean down = event.getAction() == KeyEvent.ACTION_DOWN;\n                    final String js = "window.__retroNativePad&&window.__retroNativePad(" + code + "," + (down ? "true" : "false") + ")";\n                    webView.evaluateJavascript(js, null);\n                }\n                // Some inexpensive Android gamepads expose their center buttons as BACK/MENU.\n                // Never let those two pad keys close the app while they are being used as controls.\n                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK || event.getKeyCode() == KeyEvent.KEYCODE_MENU) {\n                    return true;\n                }\n            }\n        } catch (Throwable ignored) {}\n        return super.dispatchKeyEvent(event);\n    }\n'''
s = must_replace(s, old_dispatch, new_dispatch, 'native gamepad keycode bridge')
p.write_text(s, encoding='utf-8')

# --- Launcher: PS1 Start/Select use real Android keys, not broken browser button 8/9 ---
p = Path('app/src/main/assets/launcher.js')
s = p.read_text(encoding='utf-8')

old_profiles = """function normalizeControlProfile(core,raw){const base=clone(BASE_CONTROLS),src=raw&&typeof raw==='object'?(raw[0]||raw['0']||{}):{};for(const [id,v] of Object.entries(src))if(v&&typeof v==='object')base[0][id]={...base[0][id],...v};return base}\nfunction loadControlProfile(core){try{const raw=JSON.parse(localStorage.getItem(`retro-controls-${core}`)||'null');return raw&&typeof raw==='object'?normalizeControlProfile(core,raw):null}catch(_){return null}}\nfunction loadMapperProfile(core){return loadControlProfile(core)||normalizeControlProfile(core,null)}\n"""
new_profiles = """function normalizeControlProfile(core,raw){const base=clone(BASE_CONTROLS);if(core==='psx'){base[0][2]={...base[0][2],value2:''};base[0][3]={...base[0][3],value2:''}}const src=raw&&typeof raw==='object'?(raw[0]||raw['0']||{}):{};for(const [id,v] of Object.entries(src))if(v&&typeof v==='object')base[0][id]={...base[0][id],...v};return base}\nfunction loadControlProfile(core){try{const key=`retro-controls-${core}`,raw=JSON.parse(localStorage.getItem(key)||'null');if(!raw||typeof raw!=='object')return null;const p=normalizeControlProfile(core,raw);if(core==='psx'&&!localStorage.getItem('retro-ps1-native-system-v281')){if(p[0]?.[2]?.value2==='SELECT')p[0][2].value2='';if(p[0]?.[3]?.value2==='START')p[0][3].value2='';localStorage.setItem(key,JSON.stringify(p));localStorage.setItem('retro-ps1-native-system-v281','1')}return p}catch(_){return null}}\nfunction loadMapperProfile(core){return loadControlProfile(core)||normalizeControlProfile(core,null)}\n"""
s = must_replace(s, old_profiles, new_profiles, 'PS1 system binding migration')

old_display = "function gamepadDisplay(v){if(!v)return'—';if(GP_DISPLAY[v])return GP_DISPLAY[v];return String(v).replace('LEFT_STICK_X:+1','L-stick →').replace('LEFT_STICK_X:-1','L-stick ←').replace('LEFT_STICK_Y:+1','L-stick ↓').replace('LEFT_STICK_Y:-1','L-stick ↑').replace('RIGHT_STICK_X:+1','R-stick →').replace('RIGHT_STICK_X:-1','R-stick ←').replace('RIGHT_STICK_Y:+1','R-stick ↓').replace('RIGHT_STICK_Y:-1','R-stick ↑')}"
new_display = "const ANDROID_PAD_NAMES={19:'D-pad ↑',20:'D-pad ↓',21:'D-pad ←',22:'D-pad →',96:'Android A',97:'Android B',98:'Android C',99:'Android X',100:'Android Y',101:'Android Z',102:'Android L1',103:'Android R1',104:'Android L2',105:'Android R2',106:'Android L3',107:'Android R3',108:'Android Start',109:'Android Select',110:'Android Mode'};function gamepadDisplay(v){if(!v)return'—';if(GP_DISPLAY[v])return GP_DISPLAY[v];const native=/^ANDROID_KEY_(\\d+)$/.exec(String(v));if(native)return ANDROID_PAD_NAMES[Number(native[1])]||`Android key ${native[1]}`;return String(v).replace('LEFT_STICK_X:+1','L-stick →').replace('LEFT_STICK_X:-1','L-stick ←').replace('LEFT_STICK_Y:+1','L-stick ↓').replace('LEFT_STICK_Y:-1','L-stick ↑').replace('RIGHT_STICK_X:+1','R-stick →').replace('RIGHT_STICK_X:-1','R-stick ←').replace('RIGHT_STICK_Y:+1','R-stick ↓').replace('RIGHT_STICK_Y:-1','R-stick ↑')}"
s = must_replace(s, old_display, new_display, 'native Android button display')

# For PS1 Start/Select, do not trust browser Gamepad button indexes 8/9 at all.
# The real center buttons are captured through Activity.dispatchKeyEvent instead.
s = must_replace(
    s,
    "function pollCaptureGamepad(){if(!captureState||captureState.kind!=='gamepad')return;const pad=getPads()[0];",
    "function pollCaptureGamepad(){if(!captureState||captureState.kind!=='gamepad')return;if(selected.core==='psx'&&(Number(captureState.id)===2||Number(captureState.id)===3))return;const pad=getPads()[0];",
    'PS1 native-only Start Select capture',
)

# Always hand an explicit normalized profile to the iframe. For PS1 this means browser
# button indexes 8/9 are blank until the user assigns the real Android center buttons.
s = must_replace(
    s,
    "settings,controlProfile:loadControlProfile(selected.core),nesCore:opts.nesCore||''",
    "settings,controlProfile:loadMapperProfile(selected.core),nesCore:opts.nesCore||''",
    'explicit PS1 safe profile handoff',
)

# Browser button 8/9 is unreliable on this class of controller, so only Sega keeps the
# outer standard system-button relay. PS1 system buttons come from native Android keycodes.
s = must_replace(
    s,
    "if(selected.core!=='nes')syncRuntimeSystemPad(gp||null);",
    "if(selected.core==='segaMD')syncRuntimeSystemPad(gp||null);else syncRuntimeSystemPad(null);",
    'disable PS1 broken standard system relay',
)

old_native = "window.__retroNativePad=label=>{if(!playing||selected.core!=='nes'||captureState||!label)return;try{const p=loadControlProfile('nes')?.[0]||{};if(p[2]?.value2===label)postToGame('retro-input',{index:2,value:1});if(p[3]?.value2===label)postToGame('retro-input',{index:3,value:1})}catch(_){}};"
new_native = "window.__retroNativePad=(code,down)=>{const n=Number(code);if(!Number.isFinite(n))return;const token=`ANDROID_KEY_${n}`;if(captureState?.kind==='gamepad'){if(!down)return;const id=captureState.id;if(!mapperDraft?.[0]?.[id])mapperDraft[0][id]={};mapperDraft[0][id]={...mapperDraft[0][id],value2:token};closeCapture();updateMapperBinding(id,'gamepad');return}if(!playing)return;try{const p=loadMapperProfile(selected.core)?.[0]||{};for(const [id,cfg] of Object.entries(p)){if(cfg?.value2!==token)continue;const k=Number(id);if(k===24){if(down)quickAction('retro-quick-save');continue}if(k===25){if(down)quickAction('retro-quick-load');continue}if(k>=0&&k<24)postToGame('retro-input',{index:k,value:down?1:0})}}catch(_){}};"
s = must_replace(s, old_native, new_native, 'generic native Android mapper and runtime input')
p.write_text(s, encoding='utf-8')

# --- Inner game: PS1 default system buttons safe + separated virtual D-pad/stick ---
p = Path('app/src/main/assets/game.html')
s = p.read_text(encoding='utf-8')

s = must_replace(
    s,
    "function inputCfg(id){const custom=currentProfile?.[0]?.[id]||currentProfile?.['0']?.[id]||{};return{...(INPUT_DEFAULTS[id]||{}),...custom}}",
    "function inputCfg(id){const base={...(INPUT_DEFAULTS[id]||{})};if(currentCore==='psx'&&(Number(id)===2||Number(id)===3))base.value2='';const custom=currentProfile?.[0]?.[id]||currentProfile?.['0']?.[id]||{};return{...base,...custom}}",
    'PS1 no implicit browser Start Select',
)

layout_needle = "function captureDefaultLayout(force=false){const els=movableControls();if(force||!defaultLayoutStyles.length)defaultLayoutStyles=els.map(el=>({id:controlId(el),style:el.getAttribute('style')||''}))}\n"
layout_helpers = r'''function applyPsFactoryLayout(){if(currentCore!=='psx')return;const d=document.querySelector('.b_retro_dpad'),st=document.querySelector('.b_retro_stick');if(!d||!st)return;const h=Math.max(1,innerHeight),w=Math.max(1,innerWidth),compact=h<520,left=Math.max(125,Math.min(185,w*.13)),dy=compact?h*.32:h*.40,sy=compact?h*.82:h*.80,dScale=compact?.78:1,sScale=compact?.72:.90;const place=(el,y,scale)=>{el.style.setProperty('position','fixed','important');el.style.setProperty('left',`${left}px`,'important');el.style.setProperty('top',`${y}px`,'important');el.style.setProperty('right','auto','important');el.style.setProperty('bottom','auto','important');el.style.setProperty('transform',`translate(-50%,-50%) scale(${scale})`,'important');el.style.setProperty('transform-origin','center center','important')};place(d,dy,dScale);place(st,sy,sScale)}
function migratePsOverlappingLayout(){if(currentCore!=='psx')return;try{const data=getLayout(),d=data.b_retro_dpad,st=data.b_retro_stick;if(!d||!st)return;const dx=(Number(d.x)-Number(st.x))*innerWidth/100,dy=(Number(d.y)-Number(st.y))*innerHeight/100;if(Math.hypot(dx,dy)<190){delete data.b_retro_dpad;delete data.b_retro_stick;localStorage.setItem(layoutKey(),JSON.stringify(data))}}catch(_){}}
'''
s = must_replace(s, layout_needle, layout_helpers + layout_needle, 'PS1 factory virtual separation helpers')

s = must_replace(
    s,
    "startConfirmed=true;captureDefaultLayout(true);",
    "startConfirmed=true;if(currentCore==='psx'){applyPsFactoryLayout();migratePsOverlappingLayout()}captureDefaultLayout(true);",
    'capture separated PS1 factory layout',
)

# Reapply the factory separation before any saved user positions on resize/DOM changes.
s = must_replace(
    s,
    "new MutationObserver(()=>{if(startConfirmed){keepAspect();applySavedLayout()}})",
    "new MutationObserver(()=>{if(startConfirmed){keepAspect();if(currentCore==='psx'&&!layoutEditing)applyPsFactoryLayout();applySavedLayout()}})",
    'PS1 separation on recreated controls',
)
s = must_replace(
    s,
    "window.addEventListener('resize',()=>{if(startConfirmed)setTimeout(()=>{keepAspect();applySavedLayout()},30)});",
    "window.addEventListener('resize',()=>{if(startConfirmed)setTimeout(()=>{keepAspect();if(currentCore==='psx'&&!layoutEditing)applyPsFactoryLayout();applySavedLayout()},30)});",
    'PS1 separation on resize',
)
p.write_text(s, encoding='utf-8')

# Same package and permanent signing certificate.
g = Path('app/build.gradle')
t = g.read_text(encoding='utf-8')
t = must_replace(t, 'versionCode 21', 'versionCode 22', 'version code')
t = must_replace(t, "versionName '2.8.0'", "versionName '2.8.1'", 'version name')
g.write_text(t, encoding='utf-8')

print('Retro 3 v2.8.1 PS1 native Start/Select + separated virtual controls applied')
