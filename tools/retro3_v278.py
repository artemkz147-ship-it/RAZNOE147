from pathlib import Path


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'v278 patch missing: {label}')
    return text.replace(old, new, 1)

p = Path('app/src/main/assets/game.html')
s = p.read_text(encoding='utf-8')

# EmulatorJS 4.2.x can ignore simulateInput while its actual render surface is not focused.
# This is most visible on title/options menus: gameplay may later work after a touch on the
# emulator, while the first menu presses are lost. Keep focus on the real canvas before a
# NES press, and guarantee every press is visible to the core for at least ~3 frames.
s = must_replace(
    s,
    "let stickBridgeRAF=0,stickState=[false,false,false,false],layoutEditing=false,layoutHandlers=[],defaultLayoutStyles=[],nesVirtualHandlers=[],nesSources={virtual:{},keyboard:{},gamepad:{}},nesOutput={};",
    "let stickBridgeRAF=0,stickState=[false,false,false,false],layoutEditing=false,layoutHandlers=[],defaultLayoutStyles=[],nesVirtualHandlers=[],nesSources={virtual:{},keyboard:{},gamepad:{}},nesOutput={},nesPressMeta={virtual:{},keyboard:{},gamepad:{}};",
    'NES focus/press state',
)

old = """const NES_INPUT_IDS=[0,2,3,4,5,6,7,8];
function rawInput(index,value){try{window.EJS_emulator?.gameManager?.simulateInput?.(0,Number(index),value?1:0)}catch(_){}}
function manualInput(index,value){rawInput(index,value)}
function disableNesNativeGamepad(){if(currentCore!=='nes')return;const emu=window.EJS_emulator;if(!emu)return;try{emu.gamepadSelection=['','','','']}catch(_){}}
function configureNesJoypad(){if(currentCore!=='nes')return;const gm=window.EJS_emulator?.gameManager;if(!gm)return;try{gm.setControllerPortDevice?.(0,1)}catch(_){}try{gm.functions?.setControllerPortDevice?.(0,1)}catch(_){}disableNesNativeGamepad()}
function nesSync(id){id=Number(id);const on=!!(nesSources.virtual?.[id]||nesSources.keyboard?.[id]||nesSources.gamepad?.[id]);if(!!nesOutput[id]===on)return;nesOutput[id]=on;rawInput(id,on)}
function nesSetSource(source,id,on){if(currentCore!=='nes')return;const bag=nesSources[source]||(nesSources[source]={});bag[Number(id)]=!!on;nesSync(id)}
function nesClearSource(source){if(currentCore!=='nes')return;const bag=nesSources[source]||{},ids=Object.keys(bag);nesSources[source]={};for(const id of ids)nesSync(Number(id))}
function resetNesInput(release=true){if(release)for(const id of NES_INPUT_IDS)if(nesOutput[id])rawInput(id,0);nesSources={virtual:{},keyboard:{},gamepad:{}};nesOutput={}}
"""
new = """const NES_INPUT_IDS=[0,2,3,4,5,6,7,8],NES_MIN_PRESS_MS=55;
function ensureNesInputFocus(){if(currentCore!=='nes'||layoutEditing)return;try{window.focus()}catch(_){}let el=document.querySelector('.ejs_canvas')||window.EJS_emulator?.canvas||document.querySelector('#game');if(!el)return;try{if(!el.hasAttribute('tabindex'))el.setAttribute('tabindex','-1');el.focus({preventScroll:true})}catch(_){try{el.focus()}catch(__){}}}
function rawInput(index,value){try{if(value&&currentCore==='nes')ensureNesInputFocus();window.EJS_emulator?.gameManager?.simulateInput?.(0,Number(index),value?1:0)}catch(_){}}
function manualInput(index,value){rawInput(index,value)}
function disableNesNativeGamepad(){if(currentCore!=='nes')return;const emu=window.EJS_emulator;if(!emu)return;try{emu.gamepadSelection=['','','','']}catch(_){}}
function configureNesJoypad(){if(currentCore!=='nes')return;const gm=window.EJS_emulator?.gameManager;if(!gm)return;try{gm.setControllerPortDevice?.(0,1)}catch(_){}try{gm.functions?.setControllerPortDevice?.(0,1)}catch(_){}disableNesNativeGamepad();ensureNesInputFocus()}
function nesSync(id){id=Number(id);const on=!!(nesSources.virtual?.[id]||nesSources.keyboard?.[id]||nesSources.gamepad?.[id]);if(!!nesOutput[id]===on)return;nesOutput[id]=on;rawInput(id,on)}
function nesSetSource(source,id,on){if(currentCore!=='nes')return;id=Number(id);const bag=nesSources[source]||(nesSources[source]={}),metaBag=nesPressMeta[source]||(nesPressMeta[source]={}),meta=metaBag[id]||(metaBag[id]={downAt:0,timer:0});if(on){if(meta.timer){clearTimeout(meta.timer);meta.timer=0}if(!bag[id])meta.downAt=performance.now();bag[id]=true;nesSync(id);return}if(!bag[id])return;const wait=Math.max(0,NES_MIN_PRESS_MS-(performance.now()-(meta.downAt||0)));if(wait>0){if(!meta.timer)meta.timer=setTimeout(()=>{meta.timer=0;bag[id]=false;nesSync(id)},wait);return}bag[id]=false;nesSync(id)}
function nesClearSource(source){if(currentCore!=='nes')return;const bag=nesSources[source]||{},metaBag=nesPressMeta[source]||{},ids=new Set([...Object.keys(bag),...Object.keys(metaBag)]);for(const m of Object.values(metaBag))if(m?.timer)clearTimeout(m.timer);nesSources[source]={};nesPressMeta[source]={};for(const id of ids)nesSync(Number(id))}
function resetNesInput(release=true){for(const metaBag of Object.values(nesPressMeta||{}))for(const m of Object.values(metaBag||{}))if(m?.timer)clearTimeout(m.timer);if(release)for(const id of NES_INPUT_IDS)if(nesOutput[id])rawInput(id,0);nesSources={virtual:{},keyboard:{},gamepad:{}};nesOutput={};nesPressMeta={virtual:{},keyboard:{},gamepad:{}}}
"""
s = must_replace(s, old, new, 'focus-aware minimum NES press')

# Prime focus after the core creates/replaces the canvas, and again shortly after startup.
s = must_replace(
    s,
    "if(currentCore==='nes'){configureNesJoypad();setTimeout(configureNesJoypad,100)}applySettings(currentSettings);",
    "if(currentCore==='nes'){configureNesJoypad();setTimeout(configureNesJoypad,100);setTimeout(ensureNesInputFocus,250);setTimeout(ensureNesInputFocus,700)}applySettings(currentSettings);",
    'prime NES canvas focus after start',
)

p.write_text(s, encoding='utf-8')

# Update in place using the same package and permanent release certificate.
g = Path('app/build.gradle')
t = g.read_text(encoding='utf-8')
t = must_replace(t, 'versionCode 18', 'versionCode 19', 'version code')
t = must_replace(t, "versionName '2.7.7'", "versionName '2.7.8'", 'version name')
g.write_text(t, encoding='utf-8')

print('Retro 3 v2.7.8 NES menu focus/input sampling fix applied')
