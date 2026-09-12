from pathlib import Path


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'v271 patch missing: {label}')
    return text.replace(old, new, 1)


p = Path('app/src/main/assets/game.html')
s = p.read_text(encoding='utf-8')

s = must_replace(
    s,
    "let stickBridgeRAF=0,stickState=[false,false,false,false],layoutEditing=false,layoutHandlers=[];",
    "let stickBridgeRAF=0,stickState=[false,false,false,false],layoutEditing=false,layoutHandlers=[],defaultLayoutStyles=[];",
    'layout state',
)

# v2.7 accidentally dropped these helpers. Restore them and explicitly attach NES port 1
# to a standard Libretro joypad. This is intentionally NES-only: Sega/PS1 startup stays untouched.
needle = "const INPUT_DEFAULTS={"
helpers = """function quickSave(slot){const emu=window.EJS_emulator;try{const s=String(Math.max(1,Math.min(9,Number(slot||1))));emu?.changeSettingOption?.('save-state-slot',s);const ok=!!emu?.gameManager?.quickSave?.(s);send('retro-quick-result',{ok,message:ok?`Сохранено в слот ${s}`:'Не удалось быстро сохранить.'})}catch(e){send('retro-quick-result',{ok:false,message:safeMessage(e)})}}\nfunction quickLoad(slot){const emu=window.EJS_emulator;try{const s=String(Math.max(1,Math.min(9,Number(slot||1))));emu?.changeSettingOption?.('save-state-slot',s);emu?.gameManager?.quickLoad?.(s);send('retro-quick-result',{ok:true,message:`Загружен слот ${s}`})}catch(e){send('retro-quick-result',{ok:false,message:safeMessage(e)})}}\nfunction manualInput(index,value){try{if(currentCore==='nes')ensureNesGamepadSelection();document.querySelector('#game')?.focus?.();window.EJS_emulator?.gameManager?.simulateInput?.(0,Number(index),value?1:0)}catch(_){}}\nfunction ensureNesGamepadSelection(){if(currentCore!=='nes')return;const emu=window.EJS_emulator;if(!emu)return;try{const pad=emu?.gamepad?.gamepads?.find?.(Boolean);if(pad){let sel=emu.getGamepadSelectionValue?.(pad.index)||`${pad.id}_${pad.index}`;if(!Array.isArray(emu.gamepadSelection))emu.gamepadSelection=['','','',''];while(emu.gamepadSelection.length<4)emu.gamepadSelection.push('');emu.gamepadSelection[0]=sel}}catch(_){}}\nfunction configureNesJoypad(){if(currentCore!=='nes')return;const gm=window.EJS_emulator?.gameManager;if(!gm)return;try{gm.setControllerPortDevice?.(0,1)}catch(_){}try{gm.functions?.setControllerPortDevice?.(0,1)}catch(_){}ensureNesGamepadSelection()}\n"""
s = must_replace(s, needle, helpers + needle, 'restore input helpers')

# Keep EmulatorJS' native gamepad path alive for NES. Our extra loop remains only as a
# compatibility bridge and also maps the left analog stick to the D-pad. Do not reconfigure
# the Libretro controller port every animation frame: that can clear live input state.
s = must_replace(
    s,
    "try{window.EJS_emulator.gamepadSelection=[]}catch(_){}const loop=()=>{const gm=window.EJS_emulator?.gameManager;if(gm&&startConfirmed&&!layoutEditing){",
    "if(currentCore!=='nes')try{window.EJS_emulator.gamepadSelection=[]}catch(_){}else ensureNesGamepadSelection();const loop=()=>{const gm=window.EJS_emulator?.gameManager;if(gm&&startConfirmed&&!layoutEditing){if(currentCore==='nes')ensureNesGamepadSelection();",
    'keep native NES gamepad active',
)

# The old Reset removed the original inline coordinates created by EmulatorJS itself.
# Capture those coordinates before any saved user layout is applied, then restore them exactly.
s = must_replace(
    s,
    "function clearLayoutStyles(){movableControls().forEach(el=>['position','left','top','right','bottom','transform','z-index'].forEach(p=>el.style.removeProperty(p)))}",
    "function captureDefaultLayout(force=false){const els=movableControls();if(force||!defaultLayoutStyles.length)defaultLayoutStyles=els.map(el=>({id:controlId(el),style:el.getAttribute('style')||''}))}\nfunction restoreDefaultLayout(){const els=movableControls();els.forEach((el,i)=>{let rec=defaultLayoutStyles[i];if(!rec||rec.id!==controlId(el))rec=defaultLayoutStyles.find(x=>x.id===controlId(el));if(!rec)return;if(rec.style)el.setAttribute('style',rec.style);else el.removeAttribute('style')})}\nfunction clearLayoutStyles(){restoreDefaultLayout()}",
    'preserve factory virtual layout',
)
s = must_replace(
    s,
    "if(reset){try{localStorage.removeItem(layoutKey())}catch(_){}clearLayoutStyles()}else applySavedLayout();",
    "if(reset){try{localStorage.removeItem(layoutKey())}catch(_){}restoreDefaultLayout()}else applySavedLayout();",
    'reset to factory positions',
)
s = must_replace(
    s,
    "setTimeout(()=>{applySavedLayout();document.body.insertAdjacentHTML('beforeend','<div id=\"layoutEditor\">",
    "setTimeout(()=>{if(!defaultLayoutStyles.length)captureDefaultLayout(true);applySavedLayout();document.body.insertAdjacentHTML('beforeend','<div id=\"layoutEditor\">",
    'layout editor fallback capture',
)

# At the game start event the virtual controls already exist, but no user layout has been
# reapplied yet. This is the safe point to take the factory-layout snapshot and enable NES P1.
s = must_replace(
    s,
    "startConfirmed=true;applySettings(currentSettings);if(currentProfile)applyControlProfile(currentProfile);startUnifiedInputBridge();",
    "startConfirmed=true;captureDefaultLayout(true);setTimeout(()=>{if(!defaultLayoutStyles.length)captureDefaultLayout(true)},20);if(currentCore==='nes'){configureNesJoypad();setTimeout(configureNesJoypad,80);setTimeout(configureNesJoypad,300)}applySettings(currentSettings);if(currentProfile)applyControlProfile(currentProfile);startUnifiedInputBridge();",
    'NES P1 activation and factory layout snapshot',
)

p.write_text(s, encoding='utf-8')

# Increment version so the persistent signing certificate can update v2.7.0 in place.
g = Path('app/build.gradle')
t = g.read_text(encoding='utf-8')
t = must_replace(t, 'versionCode 11', 'versionCode 12', 'version code')
t = must_replace(t, "versionName '2.7.0'", "versionName '2.7.1'", 'version name')
g.write_text(t, encoding='utf-8')

print('Retro 3 v2.7.1 patch applied')
