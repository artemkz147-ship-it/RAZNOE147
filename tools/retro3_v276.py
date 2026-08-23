from pathlib import Path


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'v276 patch missing: {label}')
    return text.replace(old, new, 1)

p = Path('app/src/main/assets/game.html')
s = p.read_text(encoding='utf-8')

# NES had three independent input paths at once: EmulatorJS native gamepad events,
# our polling bridge, and custom virtual-touch handlers. They could overwrite each
# other's held state. Keep one native libretro output and merge virtual/keyboard/
# physical-gamepad sources before calling simulateInput.
s = must_replace(
    s,
    "let stickBridgeRAF=0,stickState=[false,false,false,false],layoutEditing=false,layoutHandlers=[],defaultLayoutStyles=[],nesVirtualHandlers=[];",
    "let stickBridgeRAF=0,stickState=[false,false,false,false],layoutEditing=false,layoutHandlers=[],defaultLayoutStyles=[],nesVirtualHandlers=[],nesSources={virtual:{},keyboard:{},gamepad:{}},nesOutput={};",
    'NES source state',
)

old = """function manualInput(index,value){try{if(currentCore==='nes')ensureNesGamepadSelection();document.querySelector('#game')?.focus?.();window.EJS_emulator?.gameManager?.simulateInput?.(0,Number(index),value?1:0)}catch(_){}}
function ensureNesGamepadSelection(){if(currentCore!=='nes')return;const emu=window.EJS_emulator;if(!emu)return;try{const pad=emu?.gamepad?.gamepads?.find?.(Boolean);if(pad){let sel=emu.getGamepadSelectionValue?.(pad.index)||`${pad.id}_${pad.index}`;if(!Array.isArray(emu.gamepadSelection))emu.gamepadSelection=['','','',''];while(emu.gamepadSelection.length<4)emu.gamepadSelection.push('');emu.gamepadSelection[0]=sel}}catch(_){}}
function configureNesJoypad(){if(currentCore!=='nes')return;const gm=window.EJS_emulator?.gameManager;if(!gm)return;try{gm.setControllerPortDevice?.(0,1)}catch(_){}try{gm.functions?.setControllerPortDevice?.(0,1)}catch(_){}ensureNesGamepadSelection()}
"""
new = """const NES_INPUT_IDS=[0,2,3,4,5,6,7,8];
function rawInput(index,value){try{window.EJS_emulator?.gameManager?.simulateInput?.(0,Number(index),value?1:0)}catch(_){}}
function manualInput(index,value){rawInput(index,value)}
function disableNesNativeGamepad(){if(currentCore!=='nes')return;const emu=window.EJS_emulator;if(!emu)return;try{emu.gamepadSelection=['','','','']}catch(_){}}
function configureNesJoypad(){if(currentCore!=='nes')return;const gm=window.EJS_emulator?.gameManager;if(!gm)return;try{gm.setControllerPortDevice?.(0,1)}catch(_){}try{gm.functions?.setControllerPortDevice?.(0,1)}catch(_){}disableNesNativeGamepad()}
function nesSync(id){id=Number(id);const on=!!(nesSources.virtual?.[id]||nesSources.keyboard?.[id]||nesSources.gamepad?.[id]);if(!!nesOutput[id]===on)return;nesOutput[id]=on;rawInput(id,on)}
function nesSetSource(source,id,on){if(currentCore!=='nes')return;const bag=nesSources[source]||(nesSources[source]={});bag[Number(id)]=!!on;nesSync(id)}
function nesClearSource(source){if(currentCore!=='nes')return;const bag=nesSources[source]||{},ids=Object.keys(bag);nesSources[source]={};for(const id of ids)nesSync(Number(id))}
function resetNesInput(release=true){if(release)for(const id of NES_INPUT_IDS)if(nesOutput[id])rawInput(id,0);nesSources={virtual:{},keyboard:{},gamepad:{}};nesOutput={}}
"""
s = must_replace(s, old, new, 'single NES input backend')

s = must_replace(
    s,
    "function removeNesVirtualBridge(){for(const [el,type,fn,opt] of nesVirtualHandlers){try{el.removeEventListener(type,fn,opt)}catch(_){}}nesVirtualHandlers=[]}",
    "function removeNesVirtualBridge(){for(const [el,type,fn,opt] of nesVirtualHandlers){try{el.removeEventListener(type,fn,opt)}catch(_){}}nesVirtualHandlers=[];nesClearSource('virtual')}",
    'clear virtual NES state',
)
s = must_replace(
    s,
    "function nesReleaseDirections(el=null){manualInput(4,0);manualInput(5,0);manualInput(6,0);manualInput(7,0);if(el){el.classList.remove('ejs_dpad_up_pressed','ejs_dpad_down_pressed','ejs_dpad_left_pressed','ejs_dpad_right_pressed')}}",
    "function nesReleaseDirections(el=null){nesSetSource('virtual',4,0);nesSetSource('virtual',5,0);nesSetSource('virtual',6,0);nesSetSource('virtual',7,0);if(el){el.classList.remove('ejs_dpad_up_pressed','ejs_dpad_down_pressed','ejs_dpad_left_pressed','ejs_dpad_right_pressed')}}",
    'NES direction release arbitration',
)
s = must_replace(
    s,
    "manualInput(4,up);manualInput(5,down);manualInput(6,left);manualInput(7,right)",
    "nesSetSource('virtual',4,up);nesSetSource('virtual',5,down);nesSetSource('virtual',6,left);nesSetSource('virtual',7,right)",
    'NES dpad arbitration',
)
s = must_replace(s, "manualInput(id,1)", "nesSetSource('virtual',id,1)", 'NES virtual button down')
s = must_replace(s, "manualInput(id,0)", "nesSetSource('virtual',id,0)", 'NES virtual button up')

old = """function startUnifiedInputBridge(){stopUnifiedInputBridge();inputState={};const down=e=>{if(!startConfirmed||layoutEditing)return;const k=keyLabel(e);if(!k)return;let hit=false;for(const id of coreActionIds()){if(inputCfg(id).value===k){emitInput(id,true);hit=true}}if(hit){e.preventDefault();e.stopImmediatePropagation()}};const up=e=>{if(!startConfirmed)return;const k=keyLabel(e);if(!k)return;let hit=false;for(const id of coreActionIds()){if(inputCfg(id).value===k){emitInput(id,false);hit=true}}if(hit){e.preventDefault();e.stopImmediatePropagation()}};window.addEventListener('keydown',down,true);window.addEventListener('keyup',up,true);keyboardHandlers.push(['keydown',down],['keyup',up]);if(currentCore!=='nes')try{window.EJS_emulator.gamepadSelection=[]}catch(_){}else ensureNesGamepadSelection();const loop=()=>{const gm=window.EJS_emulator?.gameManager;if(gm&&startConfirmed&&!layoutEditing){if(currentCore==='nes')ensureNesGamepadSelection();let pad=null;try{pad=[...(navigator.getGamepads?.()||[])].filter(Boolean)[0]}catch(_){}if(pad){const ax=pad.axes||[],bs=pad.buttons||[];for(const id of coreActionIds()){let on=padValue(pad,inputCfg(id).value2);if(['nes','segaMD'].includes(currentCore)&&id>=4&&id<=7){if(id===4)on=on||!!bs[12]?.pressed||(ax[1]??0)<-.45;if(id===5)on=on||!!bs[13]?.pressed||(ax[1]??0)>.45;if(id===6)on=on||!!bs[14]?.pressed||(ax[0]??0)<-.45;if(id===7)on=on||!!bs[15]?.pressed||(ax[0]??0)>.45}emitInput(id,on)}}}stickBridgeRAF=requestAnimationFrame(loop)};stickBridgeRAF=requestAnimationFrame(loop)}
function stopUnifiedInputBridge(){cancelAnimationFrame(stickBridgeRAF);for(const [t,h] of keyboardHandlers)window.removeEventListener(t,h,true);keyboardHandlers=[];for(const id of Object.keys(inputState))if(inputState[id]&&Number(id)<24)try{window.EJS_emulator?.gameManager?.simulateInput?.(0,Number(id),0)}catch(_){}inputState={}}
"""
new = """function bridgeInput(source,id,on){if(currentCore==='nes'&&Number(id)<24)nesSetSource(source,id,on);else emitInput(id,on)}
function startUnifiedInputBridge(){stopUnifiedInputBridge();inputState={};if(currentCore==='nes'){resetNesInput(false);disableNesNativeGamepad()}else try{window.EJS_emulator.gamepadSelection=[]}catch(_){}const down=e=>{if(!startConfirmed||layoutEditing)return;const k=keyLabel(e);if(!k)return;let hit=false;for(const id of coreActionIds()){if(inputCfg(id).value===k){bridgeInput('keyboard',id,true);hit=true}}if(hit){e.preventDefault();e.stopImmediatePropagation()}};const up=e=>{if(!startConfirmed)return;const k=keyLabel(e);if(!k)return;let hit=false;for(const id of coreActionIds()){if(inputCfg(id).value===k){bridgeInput('keyboard',id,false);hit=true}}if(hit){e.preventDefault();e.stopImmediatePropagation()}};window.addEventListener('keydown',down,true);window.addEventListener('keyup',up,true);keyboardHandlers.push(['keydown',down],['keyup',up]);const loop=()=>{const gm=window.EJS_emulator?.gameManager;if(gm&&startConfirmed&&!layoutEditing){if(currentCore==='nes')disableNesNativeGamepad();let pad=null;try{pad=[...(navigator.getGamepads?.()||[])].filter(Boolean)[0]}catch(_){}const ax=pad?.axes||[],bs=pad?.buttons||[];for(const id of coreActionIds()){let on=pad?padValue(pad,inputCfg(id).value2):false;if(pad&&['nes','segaMD'].includes(currentCore)&&id>=4&&id<=7){if(id===4)on=on||!!bs[12]?.pressed||(ax[1]??0)<-.45;if(id===5)on=on||!!bs[13]?.pressed||(ax[1]??0)>.45;if(id===6)on=on||!!bs[14]?.pressed||(ax[0]??0)<-.45;if(id===7)on=on||!!bs[15]?.pressed||(ax[0]??0)>.45}bridgeInput('gamepad',id,on)}}stickBridgeRAF=requestAnimationFrame(loop)};stickBridgeRAF=requestAnimationFrame(loop)}
function stopUnifiedInputBridge(){cancelAnimationFrame(stickBridgeRAF);for(const [t,h] of keyboardHandlers)window.removeEventListener(t,h,true);keyboardHandlers=[];if(currentCore==='nes')resetNesInput(true);else{for(const id of Object.keys(inputState))if(inputState[id]&&Number(id)<24)try{window.EJS_emulator?.gameManager?.simulateInput?.(0,Number(id),0)}catch(_){}inputState={}}}
"""
s = must_replace(s, old, new, 'unified NES keyboard/gamepad bridge')

# Configure NES port once after startup. Never repopulate EmulatorJS gamepadSelection;
# all physical controls now come through the single bridge above.
s = must_replace(
    s,
    "if(currentCore==='nes'){configureNesJoypad();setTimeout(configureNesJoypad,80);setTimeout(configureNesJoypad,300)}",
    "if(currentCore==='nes'){configureNesJoypad();setTimeout(configureNesJoypad,100)}",
    'stable NES port init',
)

p.write_text(s, encoding='utf-8')

# Update in place with the same package and permanent signing certificate.
g = Path('app/build.gradle')
t = g.read_text(encoding='utf-8')
t = must_replace(t, 'versionCode 16', 'versionCode 17', 'version code')
t = must_replace(t, "versionName '2.7.5'", "versionName '2.7.6'", 'version name')
g.write_text(t, encoding='utf-8')

print('Retro 3 v2.7.6 single NES input path applied')
