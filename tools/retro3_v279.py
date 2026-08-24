from pathlib import Path


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'v279 patch missing: {label}')
    return text.replace(old, new, 1)

# --- game iframe: reliable NES SELECT/START pulses ---
p = Path('app/src/main/assets/game.html')
s = p.read_text(encoding='utf-8')

# Keep NES Start/Select clear of the app Menu button by giving them a real top offset.
s = must_replace(
    s,
    "{type:'button',text:'Start',id:'start',location:'center',left:60,fontSize:14,block:true,input_value:3},{type:'button',text:'Select',id:'select',location:'center',left:-5,fontSize:14,block:true,input_value:2}];if(core==='segaMD')",
    "{type:'button',text:'Start',id:'start',location:'center',left:60,top:62,fontSize:14,block:true,input_value:3},{type:'button',text:'Select',id:'select',location:'center',left:-5,top:62,fontSize:14,block:true,input_value:2}];if(core==='segaMD')",
    'NES system buttons default position',
)

s = must_replace(
    s,
    "let stickBridgeRAF=0,stickState=[false,false,false,false],layoutEditing=false,layoutHandlers=[],defaultLayoutStyles=[],nesVirtualHandlers=[],nesSources={virtual:{},keyboard:{},gamepad:{}},nesOutput={},nesPressMeta={virtual:{},keyboard:{},gamepad:{}};",
    "let stickBridgeRAF=0,stickState=[false,false,false,false],layoutEditing=false,layoutHandlers=[],defaultLayoutStyles=[],nesVirtualHandlers=[],nesSources={virtual:{},keyboard:{},gamepad:{}},nesOutput={},nesPressMeta={virtual:{},keyboard:{},gamepad:{}},nesSystemTimers={};",
    'NES system timer state',
)

# The v2.7.6 arbiter originally knew only three fixed source names. Make it generic so
# dedicated system-button pulses participate in the same merged NES controller state.
s = must_replace(
    s,
    "function nesSync(id){id=Number(id);const on=!!(nesSources.virtual?.[id]||nesSources.keyboard?.[id]||nesSources.gamepad?.[id]);if(!!nesOutput[id]===on)return;nesOutput[id]=on;rawInput(id,on)}",
    "function nesSync(id){id=Number(id);const on=Object.values(nesSources||{}).some(bag=>!!bag?.[id]);if(!!nesOutput[id]===on)return;nesOutput[id]=on;rawInput(id,on)}",
    'generic NES source arbitration',
)

needle = "function nesClearSource(source){if(currentCore!=='nes')return;const bag=nesSources[source]||{},metaBag=nesPressMeta[source]||{},ids=new Set([...Object.keys(bag),...Object.keys(metaBag)]);for(const m of Object.values(metaBag))if(m?.timer)clearTimeout(m.timer);nesSources[source]={};nesPressMeta[source]={};for(const id of ids)nesSync(Number(id))}\n"
insert = needle + "function nesSystemPulse(id){if(currentCore!=='nes')return;id=Number(id);if(id!==2&&id!==3)return;ensureNesInputFocus();if(nesSystemTimers[id])clearTimeout(nesSystemTimers[id]);nesSetSource('system',id,1);nesSystemTimers[id]=setTimeout(()=>{delete nesSystemTimers[id];nesSetSource('system',id,0)},110)}\n"
s = must_replace(s, needle, insert, 'NES system pulse helper')

s = must_replace(
    s,
    "function resetNesInput(release=true){for(const metaBag of Object.values(nesPressMeta||{}))for(const m of Object.values(metaBag||{}))if(m?.timer)clearTimeout(m.timer);if(release)for(const id of NES_INPUT_IDS)if(nesOutput[id])rawInput(id,0);nesSources={virtual:{},keyboard:{},gamepad:{}};nesOutput={};nesPressMeta={virtual:{},keyboard:{},gamepad:{}}}",
    "function resetNesInput(release=true){for(const t of Object.values(nesSystemTimers||{}))clearTimeout(t);nesSystemTimers={};for(const metaBag of Object.values(nesPressMeta||{}))for(const m of Object.values(metaBag||{}))if(m?.timer)clearTimeout(m.timer);if(release)for(const id of NES_INPUT_IDS)if(nesOutput[id])rawInput(id,0);nesSources={virtual:{},keyboard:{},gamepad:{}};nesOutput={};nesPressMeta={virtual:{},keyboard:{},gamepad:{}}}",
    'reset NES system timers',
)

old_bridge = "function installNesVirtualBridge(){if(currentCore!=='nes'||!startConfirmed)return;removeNesVirtualBridge();configureNesJoypad();const bindButton=(selector,id)=>{const el=document.querySelector(selector);if(!el)return;el.style.setProperty('touch-action','none','important');const down=e=>{if(layoutEditing)return;e.preventDefault();e.stopImmediatePropagation();nesSetSource('virtual',id,1)};const up=e=>{if(layoutEditing)return;e.preventDefault();e.stopImmediatePropagation();nesSetSource('virtual',id,0)};nesListen(el,'touchstart',down,true);nesListen(el,'touchend',up,true);nesListen(el,'touchcancel',up,true)};bindButton('.b_a',8);bindButton('.b_b',0);bindButton('.b_start',3);bindButton('.b_select',2);const host=document.querySelector('.b_retro_dpad');const dpad=host?.querySelector('.ejs_dpad_main')||host;if(dpad){dpad.style.setProperty('touch-action','none','important');const move=e=>{if(layoutEditing)return;e.preventDefault();e.stopImmediatePropagation();nesDirectionsFromTouch(e,dpad)};const end=e=>{if(layoutEditing)return;e.preventDefault();e.stopImmediatePropagation();nesReleaseDirections(dpad)};nesListen(dpad,'touchstart',move,true);nesListen(dpad,'touchmove',move,true);nesListen(dpad,'touchend',end,true);nesListen(dpad,'touchcancel',end,true)}}"
new_bridge = "function installNesVirtualBridge(){if(currentCore!=='nes'||!startConfirmed)return;removeNesVirtualBridge();configureNesJoypad();const bindButton=(selector,id)=>{const el=document.querySelector(selector);if(!el)return;el.style.setProperty('touch-action','none','important');const down=e=>{if(layoutEditing)return;e.preventDefault();e.stopImmediatePropagation();nesSetSource('virtual',id,1)};const up=e=>{if(layoutEditing)return;e.preventDefault();e.stopImmediatePropagation();nesSetSource('virtual',id,0)};nesListen(el,'touchstart',down,true);nesListen(el,'touchend',up,true);nesListen(el,'touchcancel',up,true)};const bindSystem=(selector,id)=>{const el=document.querySelector(selector);if(!el)return;el.style.setProperty('touch-action','none','important');let last=0;const fire=e=>{if(layoutEditing)return;e.preventDefault();e.stopImmediatePropagation();const now=performance.now();if(now-last<45)return;last=now;nesSystemPulse(id)};const suppress=e=>{if(layoutEditing)return;e.preventDefault();e.stopImmediatePropagation()};nesListen(el,'pointerdown',fire,true);nesListen(el,'touchstart',fire,true);nesListen(el,'touchend',suppress,true);nesListen(el,'touchcancel',suppress,true);nesListen(el,'pointerup',suppress,true);nesListen(el,'pointercancel',suppress,true)};bindButton('.b_a',8);bindButton('.b_b',0);bindSystem('.b_start',3);bindSystem('.b_select',2);const host=document.querySelector('.b_retro_dpad');const dpad=host?.querySelector('.ejs_dpad_main')||host;if(dpad){dpad.style.setProperty('touch-action','none','important');const move=e=>{if(layoutEditing)return;e.preventDefault();e.stopImmediatePropagation();nesDirectionsFromTouch(e,dpad)};const end=e=>{if(layoutEditing)return;e.preventDefault();e.stopImmediatePropagation();nesReleaseDirections(dpad)};nesListen(dpad,'touchstart',move,true);nesListen(dpad,'touchmove',move,true);nesListen(dpad,'touchend',end,true);nesListen(dpad,'touchcancel',end,true)}}"
s = must_replace(s, old_bridge, new_bridge, 'direct NES Start Select virtual pulses')

# Parent/top-level input is a reliable fallback for Android gamepads whose START button
# is visible to the outer WebView but inconsistently exposed inside the iframe.
s = must_replace(
    s,
    "else if(d.type==='retro-input')manualInput(d.index,d.value);else if(d.type==='retro-edit-layout')",
    "else if(d.type==='retro-input'){const i=Number(d.index);if(currentCore==='nes'&&(i===2||i===3)){if(d.value)nesSystemPulse(i)}else manualInput(i,d.value)}else if(d.type==='retro-edit-layout')",
    'parent NES system button pulse',
)
p.write_text(s, encoding='utf-8')

# --- launcher: remove focus border and relay physical START/SELECT from top WebView ---
p = Path('app/src/main/assets/launcher.js')
s = p.read_text(encoding='utf-8')

s = must_replace(
    s,
    "let gpPrev={u:false,d:false,l:false,r:false,a:false,b:false,start:false},gpFocus=0,gpLast=0,lastPadName='';",
    "let gpPrev={u:false,d:false,l:false,r:false,a:false,b:false,start:false},gpFocus=0,gpLast=0,lastPadName='',runtimeSystemPadPrev={select:false,start:false};",
    'runtime physical system pad state',
)

# Chromium/WebView draws a yellow/orange focus ring around the focused iframe. The focus
# itself is required for reliable NES input, but the visual ring is not.
s = must_replace(
    s,
    "#playScreen{position:fixed;inset:0;background:#000;z-index:9999;overflow:hidden;font-family:Inter,Roboto,system-ui,sans-serif}#gameFrame{display:block;position:absolute;inset:0;width:100%;height:100%;border:0;background:#000;visibility:hidden}",
    "#playScreen{position:fixed;inset:0;background:#000;z-index:9999;overflow:hidden;font-family:Inter,Roboto,system-ui,sans-serif;outline:0!important;box-shadow:none!important}#gameFrame{display:block;position:absolute;inset:0;width:100%;height:100%;border:0;background:#000;visibility:hidden;outline:0!important;box-shadow:none!important;-webkit-tap-highlight-color:transparent}#gameFrame:focus,#gameFrame:focus-visible,#playScreen:focus,#playScreen:focus-visible{outline:0!important;box-shadow:none!important}",
    'remove focused iframe yellow border',
)

axis_needle = "function gpAxisLabel(i,v){const n=['LEFT_STICK_X','LEFT_STICK_Y','RIGHT_STICK_X','RIGHT_STICK_Y'][i]||`EXTRA_STICK_${i}`;return`${n}:${v>0?'+1':'-1'}`}\n"
axis_insert = axis_needle + "const RUNTIME_PAD_BUTTONS={BUTTON_1:0,BUTTON_2:1,BUTTON_3:2,BUTTON_4:3,LEFT_TOP_SHOULDER:4,RIGHT_TOP_SHOULDER:5,LEFT_BOTTOM_SHOULDER:6,RIGHT_BOTTOM_SHOULDER:7,SELECT:8,START:9,LEFT_STICK:10,RIGHT_STICK:11,DPAD_UP:12,DPAD_DOWN:13,DPAD_LEFT:14,DPAD_RIGHT:15};\nfunction runtimePadValue(pad,label){if(!pad||!label)return false;if(label in RUNTIME_PAD_BUTTONS)return!!pad.buttons?.[RUNTIME_PAD_BUTTONS[label]]?.pressed;const m=/^(LEFT|RIGHT)_STICK_([XY]):([+-]1)$/.exec(label);if(m){const axis=(m[1]==='LEFT'?0:2)+(m[2]==='Y'?1:0),v=pad.axes?.[axis]||0;return m[3]==='+1'?v>.55:v<-.55}const g=/^GAMEPAD_(\\d+)$/.exec(label);return g?!!pad.buttons?.[Number(g[1])]?.pressed:false}\nfunction syncRuntimeSystemPad(pad){const active=!!(playing&&!captureState&&!activeMenuRoot()&&pad);if(!active){if(runtimeSystemPadPrev.select)postToGame('retro-input',{index:2,value:0});if(runtimeSystemPadPrev.start)postToGame('retro-input',{index:3,value:0});runtimeSystemPadPrev={select:false,start:false};return}const p=loadControlProfile(selected.core)?.[0]||{},next={select:runtimePadValue(pad,p[2]?.value2),start:runtimePadValue(pad,p[3]?.value2)};if(next.select!==runtimeSystemPadPrev.select)postToGame('retro-input',{index:2,value:next.select?1:0});if(next.start!==runtimeSystemPadPrev.start)postToGame('retro-input',{index:3,value:next.start?1:0});runtimeSystemPadPrev=next}\n"
s = must_replace(s, axis_needle, axis_insert, 'top-level mapped START SELECT relay')

# Gamepad capture: wait until the button used to enter capture is actually released.
s = must_replace(
    s,
    "captureState={id,kind,baselinePads:snapshotPads(),keyboardHandler:null};",
    "captureState={id,kind,baselinePads:snapshotPads(),keyboardHandler:null,armed:kind==='keyboard'};",
    'release-first capture state',
)
old_poll = "function pollCaptureGamepad(){if(!captureState||captureState.kind!=='gamepad')return;const pad=getPads()[0];if(!pad)return;const base=captureState.baselinePads.find(x=>x.index===pad.index)||{buttons:[],axes:[]};for(let i=0;i<(pad.buttons||[]).length;i++){if(pad.buttons[i]?.pressed&&!base.buttons[i]){const id=captureState.id;mapperDraft[0][id]={...(mapperDraft[0][id]||{}),value2:gpButtonLabel(i)};closeCapture();updateMapperBinding(id,'gamepad');return}}for(let i=0;i<(pad.axes||[]).length;i++){const v=pad.axes[i]||0,b=base.axes[i]||0;if(Math.abs(v)>.7&&Math.abs(b)<.5){const id=captureState.id;mapperDraft[0][id]={...(mapperDraft[0][id]||{}),value2:gpAxisLabel(i,v)};closeCapture();updateMapperBinding(id,'gamepad');return}}}"
new_poll = "function pollCaptureGamepad(){if(!captureState||captureState.kind!=='gamepad')return;const pad=getPads()[0];if(!pad)return;if(!captureState.armed){const busy=[...(pad.buttons||[])].some(b=>!!b?.pressed)||[...(pad.axes||[])].some(v=>Math.abs(v||0)>.55);if(!busy){captureState.armed=true;captureState.baselinePads=snapshotPads()}return}const base=captureState.baselinePads.find(x=>x.index===pad.index)||{buttons:[],axes:[]};for(let i=0;i<(pad.buttons||[]).length;i++){if(pad.buttons[i]?.pressed&&!base.buttons[i]){const id=captureState.id;mapperDraft[0][id]={...(mapperDraft[0][id]||{}),value2:gpButtonLabel(i)};closeCapture();updateMapperBinding(id,'gamepad');return}}for(let i=0;i<(pad.axes||[]).length;i++){const v=pad.axes[i]||0,b=base.axes[i]||0;if(Math.abs(v)>.7&&Math.abs(b)<.5){const id=captureState.id;mapperDraft[0][id]={...(mapperDraft[0][id]||{}),value2:gpAxisLabel(i,v)};closeCapture();updateMapperBinding(id,'gamepad');return}}}"
s = must_replace(s, old_poll, new_poll, 'release-first physical mapping capture')

# Always read START/SELECT in the outer WebView while a game is running. This is the same
# context where the mapping screen captured the user's physical button, so custom pads work.
old_loop = "function gamepadMenuLoop(t){if(t-gpLast>45){gpLast=t;updateGamepadStatus();pollCaptureGamepad();if(!captureState){const gp=getPads()[0];if(gp){const a=gp.axes||[],b=gp.buttons||[],s={u:!!b[12]?.pressed||(a[1]??0)<-.6,d:!!b[13]?.pressed||(a[1]??0)>.6,l:!!b[14]?.pressed||(a[0]??0)<-.6,r:!!b[15]?.pressed||(a[0]??0)>.6,a:!!b[0]?.pressed,b:!!b[1]?.pressed,start:!!b[9]?.pressed};const root=activeMenuRoot();if(root){const items=menuItems(root);if(!items.length)gpFocus=0;else{gpFocus=Math.min(gpFocus,items.length-1);const cur=items[gpFocus];if(s.u&&!gpPrev.u)moveFocus(items,-1);if(s.d&&!gpPrev.d)moveFocus(items,1);if(s.l&&!gpPrev.l){if(!adjustFocused(cur,-1))moveFocus(items,-1)}if(s.r&&!gpPrev.r){if(!adjustFocused(cur,1))moveFocus(items,1)}if((s.a&&!gpPrev.a)||(s.start&&!gpPrev.start)){const x=items[gpFocus];if(x?.type==='checkbox'){x.checked=!x.checked;x.dispatchEvent(new Event('change',{bubbles:true}))}else x?.click?.()}if(s.b&&!gpPrev.b)backFromGamepad()}}gpPrev=s}else gpPrev={u:false,d:false,l:false,r:false,a:false,b:false,start:false}}}requestAnimationFrame(gamepadMenuLoop)}"
new_loop = "function gamepadMenuLoop(t){if(t-gpLast>45){gpLast=t;updateGamepadStatus();pollCaptureGamepad();if(!captureState){const gp=getPads()[0];syncRuntimeSystemPad(gp||null);if(gp){const a=gp.axes||[],b=gp.buttons||[],s={u:!!b[12]?.pressed||(a[1]??0)<-.6,d:!!b[13]?.pressed||(a[1]??0)>.6,l:!!b[14]?.pressed||(a[0]??0)<-.6,r:!!b[15]?.pressed||(a[0]??0)>.6,a:!!b[0]?.pressed,b:!!b[1]?.pressed,start:!!b[9]?.pressed};const root=activeMenuRoot();if(root){const items=menuItems(root);if(!items.length)gpFocus=0;else{gpFocus=Math.min(gpFocus,items.length-1);const cur=items[gpFocus];if(s.u&&!gpPrev.u)moveFocus(items,-1);if(s.d&&!gpPrev.d)moveFocus(items,1);if(s.l&&!gpPrev.l){if(!adjustFocused(cur,-1))moveFocus(items,-1)}if(s.r&&!gpPrev.r){if(!adjustFocused(cur,1))moveFocus(items,1)}if((s.a&&!gpPrev.a)||(s.start&&!gpPrev.start)){const x=items[gpFocus];if(x?.type==='checkbox'){x.checked=!x.checked;x.dispatchEvent(new Event('change',{bubbles:true}))}else x?.click?.()}if(s.b&&!gpPrev.b)backFromGamepad()}}gpPrev=s}else gpPrev={u:false,d:false,l:false,r:false,a:false,b:false,start:false}}else syncRuntimeSystemPad(null)}requestAnimationFrame(gamepadMenuLoop)}"
s = must_replace(s, old_loop, new_loop, 'outer WebView physical START SELECT relay loop')

s = must_replace(
    s,
    "history.replaceState(null,'',location.pathname+location.search);gpFocus=0}",
    "history.replaceState(null,'',location.pathname+location.search);runtimeSystemPadPrev={select:false,start:false};gpFocus=0}",
    'reset runtime system pad on exit',
)

p.write_text(s, encoding='utf-8')

# Same package, same permanent signing key; only bump app version.
g = Path('app/build.gradle')
t = g.read_text(encoding='utf-8')
t = must_replace(t, 'versionCode 19', 'versionCode 20', 'version code')
t = must_replace(t, "versionName '2.7.8'", "versionName '2.7.9'", 'version name')
g.write_text(t, encoding='utf-8')

print('Retro 3 v2.7.9 focus-frame + physical START/SELECT fix applied')
