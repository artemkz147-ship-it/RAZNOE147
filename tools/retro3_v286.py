from pathlib import Path


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'v286 patch missing: {label}')
    return text.replace(old, new, 1)

# PS1 compatibility policy for this app:
# - force controller port 1 to the plain RETRO_DEVICE_JOYPAD
# - virtual D-pad + TWO virtual sticks all send only D-pad directions 4..7
# - BOTH physical sticks + physical D-pad also send only directions 4..7
# - do not expose/send PS1 analog axes or L3/R3 from our custom bridge

p = Path('app/src/main/assets/game.html')
s = p.read_text(encoding='utf-8')

s = must_replace(
    s,
    "body.dir-dpad .b_retro_stick{display:none!important}body.dir-stick .b_retro_dpad{display:none!important}body.dir-both .b_retro_dpad,body.dir-both .b_retro_stick{display:block}",
    "body.dir-dpad .b_retro_stick,body.dir-dpad .b_retro_stick2{display:none!important}body.dir-stick .b_retro_dpad{display:none!important}body.dir-both .b_retro_dpad,body.dir-both .b_retro_stick,body.dir-both .b_retro_stick2{display:block}",
    'direction visibility for two PS1 sticks',
)

old_ps = "{type:'dpad',id:'retro_dpad',location:'left',left:'50%',top:'0%',joystickInput:false,inputValues:[4,5,6,7]},{type:'zone',id:'retro_stick',location:'left',left:'50%',top:'100%',color:'#e8edf5',joystickInput:true,inputValues:[16,17,18,19]}"
new_ps = "{type:'dpad',id:'retro_dpad',location:'left',left:'50%',top:'0%',joystickInput:false,inputValues:[4,5,6,7]},{type:'zone',id:'retro_stick',location:'left',left:'18%',top:'100%',color:'#e8edf5',joystickInput:false,inputValues:[4,5,6,7]},{type:'zone',id:'retro_stick2',location:'left',left:'82%',top:'100%',color:'#e8edf5',joystickInput:false,inputValues:[4,5,6,7]}"
s = must_replace(s, old_ps, new_ps, 'PS1 two digital virtual sticks')

s = must_replace(
    s,
    "function coreActionIds(){if(currentCore==='nes')return[0,2,3,4,5,6,7,8,24,25];if(currentCore==='segaMD')return[0,1,2,3,4,5,6,7,8,9,10,11,24,25];return[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25]}",
    "function coreActionIds(){if(currentCore==='nes')return[0,2,3,4,5,6,7,8,24,25];if(currentCore==='segaMD')return[0,1,2,3,4,5,6,7,8,9,10,11,24,25];return[0,1,2,3,4,5,6,7,8,9,10,11,12,13,24,25]}",
    'PS1 digital-only action set',
)

nes_helper = "function configureNesJoypad(){if(currentCore!=='nes')return;const gm=window.EJS_emulator?.gameManager;if(!gm)return;try{gm.setControllerPortDevice?.(0,1)}catch(_){}try{gm.functions?.setControllerPortDevice?.(0,1)}catch(_){}disableNesNativeGamepad();ensureNesInputFocus()}"
s = must_replace(
    s,
    nes_helper,
    nes_helper + "\nfunction configurePs1DigitalJoypad(){if(currentCore!=='psx')return;const emu=window.EJS_emulator,gm=emu?.gameManager;if(!gm)return;try{gm.setControllerPortDevice?.(0,1)}catch(_){}try{gm.functions?.setControllerPortDevice?.(0,1)}catch(_){}try{emu.gamepadSelection=[]}catch(_){}}",
    'PS1 standard digital controller helper',
)

old_loop = "if(pad&&['nes','segaMD'].includes(currentCore)&&id>=4&&id<=7){if(id===4)on=on||!!bs[12]?.pressed||(ax[1]??0)<-.45;if(id===5)on=on||!!bs[13]?.pressed||(ax[1]??0)>.45;if(id===6)on=on||!!bs[14]?.pressed||(ax[0]??0)<-.45;if(id===7)on=on||!!bs[15]?.pressed||(ax[0]??0)>.45}"
new_loop = "if(pad&&['nes','segaMD','psx'].includes(currentCore)&&id>=4&&id<=7){const both=currentCore==='psx';if(id===4)on=on||!!bs[12]?.pressed||(ax[1]??0)<-.45||(both&&(ax[3]??0)<-.45);if(id===5)on=on||!!bs[13]?.pressed||(ax[1]??0)>.45||(both&&(ax[3]??0)>.45);if(id===6)on=on||!!bs[14]?.pressed||(ax[0]??0)<-.45||(both&&(ax[2]??0)<-.45);if(id===7)on=on||!!bs[15]?.pressed||(ax[0]??0)>.45||(both&&(ax[2]??0)>.45)}"
s = must_replace(s, old_loop, new_loop, 'both physical PS1 sticks duplicate D-pad')

s = must_replace(
    s,
    "function migratePsTouchLayout284(){if(currentCore!=='psx')return;try{if(localStorage.getItem('retro-ps1-touch-layout-v285'))return;const data=getLayout();delete data.b_retro_dpad;delete data.b_retro_stick;localStorage.setItem(layoutKey(),JSON.stringify(data));localStorage.setItem('retro-ps1-touch-layout-v285','1')}catch(_){}}",
    "function migratePsTouchLayout284(){if(currentCore!=='psx')return;try{if(localStorage.getItem('retro-ps1-touch-layout-v286'))return;const data=getLayout();delete data.b_retro_dpad;delete data.b_retro_stick;delete data.b_retro_stick2;localStorage.setItem(layoutKey(),JSON.stringify(data));localStorage.setItem('retro-ps1-touch-layout-v286','1')}catch(_){}}",
    'PS1 two-stick layout migration',
)

s = must_replace(
    s,
    "if(currentCore==='nes'){configureNesJoypad();setTimeout(configureNesJoypad,100);setTimeout(ensureNesInputFocus,250);setTimeout(ensureNesInputFocus,700)}applySettings(currentSettings);",
    "if(currentCore==='nes'){configureNesJoypad();setTimeout(configureNesJoypad,100);setTimeout(ensureNesInputFocus,250);setTimeout(ensureNesInputFocus,700)}if(currentCore==='psx'){configurePs1DigitalJoypad();setTimeout(configurePs1DigitalJoypad,120);setTimeout(configurePs1DigitalJoypad,600)}applySettings(currentSettings);",
    'activate PS1 digital controller',
)

p.write_text(s, encoding='utf-8')

# Remove analog-only rows from the PS1 mapper. D-pad mapping remains; both sticks are
# automatic aliases and therefore do not need separate assignments.
p = Path('app/src/main/assets/launcher.js')
s = p.read_text(encoding='utf-8')

s = must_replace(
    s,
    "['Плечи','L2',12],['Плечи','R2',13],['Стики','L3',14],['Стики','R3',15],['Левый стик','Вправо',16],['Левый стик','Влево',17],['Левый стик','Вниз',18],['Левый стик','Вверх',19],['Правый стик','Вправо',20],['Правый стик','Влево',21],['Правый стик','Вниз',22],['Правый стик','Вверх',23],['Система','Start',3]",
    "['Плечи','L2',12],['Плечи','R2',13],['Система','Start',3]",
    'remove PS1 analog mapper rows',
)

s = must_replace(
    s,
    "return selected.core==='psx'?`<option value=\"both\"${s.virtualPs==='both'?' selected':''}>Крестовина + стик</option><option value=\"dpad\"${s.virtualPs==='dpad'?' selected':''}>Только крестовина</option><option value=\"stick\"${s.virtualPs==='stick'?' selected':''}>Только стик</option>`:",
    "return selected.core==='psx'?`<option value=\"both\"${s.virtualPs==='both'?' selected':''}>Крестовина + 2 стика</option><option value=\"dpad\"${s.virtualPs==='dpad'?' selected':''}>Только крестовина</option><option value=\"stick\"${s.virtualPs==='stick'?' selected':''}>Только 2 стика</option>`:",
    'runtime PS1 direction options',
)

s = must_replace(
    s,
    "${isPs?'Крестовина, стик или оба':'Физический стик и крестовина работают одновременно'}",
    "${isPs?'Оба стика дублируют крестовину — без Analog Controller':'Физический стик и крестовина работают одновременно'}",
    'runtime PS1 movement help',
)

p.write_text(s, encoding='utf-8')

p = Path('app/src/main/assets/index.html')
s = p.read_text(encoding='utf-8')
s = must_replace(s, '<span><strong>PS1 — движение</strong><small>Крестовина, стик или оба</small></span>', '<span><strong>PS1 — движение</strong><small>Оба стика работают как крестовина</small></span>', 'PS1 global movement help')
s = must_replace(s, '<option value="both">Крестовина + стик</option>', '<option value="both">Крестовина + 2 стика</option>', 'PS1 both option')
s = must_replace(s, '<option value="stick">Только стик</option>', '<option value="stick">Только 2 стика</option>', 'PS1 sticks option')
p.write_text(s, encoding='utf-8')

p = Path('app/build.gradle')
s = p.read_text(encoding='utf-8')
s = must_replace(s, 'versionCode 26', 'versionCode 27', 'version code')
s = must_replace(s, "versionName '2.8.5'", "versionName '2.8.6'", 'version name')
p.write_text(s, encoding='utf-8')

print('Retro 3 v2.8.6: PS1 digital pad + two virtual/physical stick D-pad aliases applied')
