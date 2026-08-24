from pathlib import Path


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'v279 patch missing: {label}')
    return text.replace(old, new, 1)

p = Path('app/src/main/assets/game.html')
s = p.read_text(encoding='utf-8')

# NES title screens such as Super Mario Bros use SELECT to switch 1P/2P.
# Keep START/SELECT out from under the app's top-left Menu button by moving their
# default virtual positions down. Saved user layouts still override these defaults.
s = must_replace(
    s,
    "{type:'button',text:'Start',id:'start',location:'center',left:60,fontSize:14,block:true,input_value:3},{type:'button',text:'Select',id:'select',location:'center',left:-5,fontSize:14,block:true,input_value:2}];if(core==='segaMD')",
    "{type:'button',text:'Start',id:'start',location:'center',left:60,top:62,fontSize:14,block:true,input_value:3},{type:'button',text:'Select',id:'select',location:'center',left:-5,top:62,fontSize:14,block:true,input_value:2}];if(core==='segaMD')",
    'NES system buttons default position',
)

# Add a dedicated, frame-safe pulse for NES system buttons. These buttons are
# edge-triggered in many title/options menus and should never depend on a short
# touchend or on the generic held-input bridge.
s = must_replace(
    s,
    "let stickBridgeRAF=0,stickState=[false,false,false,false],layoutEditing=false,layoutHandlers=[],defaultLayoutStyles=[],nesVirtualHandlers=[],nesSources={virtual:{},keyboard:{},gamepad:{}},nesOutput={},nesPressMeta={virtual:{},keyboard:{},gamepad:{}};",
    "let stickBridgeRAF=0,stickState=[false,false,false,false],layoutEditing=false,layoutHandlers=[],defaultLayoutStyles=[],nesVirtualHandlers=[],nesSources={virtual:{},keyboard:{},gamepad:{}},nesOutput={},nesPressMeta={virtual:{},keyboard:{},gamepad:{}},nesSystemTimers={};",
    'NES system timer state',
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

s = must_replace(
    s,
    "else if(d.type==='retro-input')manualInput(d.index,d.value);else if(d.type==='retro-edit-layout')",
    "else if(d.type==='retro-input'){const i=Number(d.index);if(currentCore==='nes'&&(i===2||i===3)){if(d.value)nesSystemPulse(i)}else manualInput(i,d.value)}else if(d.type==='retro-edit-layout')",
    'parent NES system button pulse',
)

p.write_text(s, encoding='utf-8')

# Same package, same permanent signing key; only bump app version.
g = Path('app/build.gradle')
t = g.read_text(encoding='utf-8')
t = must_replace(t, 'versionCode 19', 'versionCode 20', 'version code')
t = must_replace(t, "versionName '2.7.8'", "versionName '2.7.9'", 'version name')
g.write_text(t, encoding='utf-8')

print('Retro 3 v2.7.9 NES title-menu SELECT/START fix applied')
