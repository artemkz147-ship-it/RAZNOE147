from pathlib import Path


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'v286 patch missing: {label}')
    return text.replace(old, new, 1)

p = Path('app/src/main/assets/game.html')
s = p.read_text(encoding='utf-8')

# PS1 must expose the complete DualShock: D-pad + left analog + right analog.
old = "{type:'dpad',id:'retro_dpad',location:'left',left:'50%',top:'0%',joystickInput:false,inputValues:[4,5,6,7]},{type:'zone',id:'retro_stick',location:'left',left:'50%',top:'100%',color:'#e8edf5',joystickInput:true,inputValues:[16,17,18,19]}"
new = "{type:'dpad',id:'retro_dpad',location:'left',left:'50%',top:'50%',joystickInput:false,inputValues:[4,5,6,7]},{type:'zone',id:'retro_lstick',location:'left',left:'50%',top:'50%',color:'#e8edf5',joystickInput:true,inputValues:[16,17,18,19]},{type:'zone',id:'retro_rstick',location:'right',left:'50%',top:'50%',color:'#e8edf5',joystickInput:true,inputValues:[20,21,22,23]}"
s = must_replace(s, old, new, 'PS1 DualShock virtual controls')

old = "body.dir-dpad .b_retro_stick{display:none!important}body.dir-stick .b_retro_dpad{display:none!important}body.dir-both .b_retro_dpad,body.dir-both .b_retro_stick{display:block}"
new = "body.dir-dpad .b_retro_lstick,body.dir-dpad .b_retro_rstick{display:none!important}body.dir-stick .b_retro_dpad{display:none!important}body.dir-stick .b_retro_lstick,body.dir-stick .b_retro_rstick,body.dir-both .b_retro_dpad,body.dir-both .b_retro_lstick,body.dir-both .b_retro_rstick{display:block!important}"
s = must_replace(s, old, new, 'PS1 D-pad/sticks visibility')

# EmulatorJS side containers are only ~125px high by default. Putting a dpad and a zone
# into the same left container therefore guarantees overlap. Enlarge both PS1 columns and
# give every movement control its own positioned 125x125 hit box before nipplejs is made.
css = """
body.core-psx .ejs_virtualGamepad_left,body.core-psx .ejs_virtualGamepad_right{height:330px!important;width:150px!important;bottom:max(42px,var(--retro-safe-bottom))!important}
body.core-psx .ejs_virtualGamepad_left{left:max(10px,var(--retro-safe-left))!important}
body.core-psx .ejs_virtualGamepad_right{right:max(10px,var(--retro-safe-right))!important}
body.core-psx .b_retro_dpad,body.core-psx .b_retro_lstick,body.core-psx .b_retro_rstick{position:absolute!important;width:125px!important;height:125px!important;left:50%!important;right:auto!important;transform:translateX(-50%)!important}
body.core-psx .b_retro_dpad{top:0!important;bottom:auto!important}
body.core-psx .b_retro_lstick,body.core-psx .b_retro_rstick{top:auto!important;bottom:0!important}
@media(max-height:520px){body.core-psx .ejs_virtualGamepad_left,body.core-psx .ejs_virtualGamepad_right{height:250px!important;width:118px!important}body.core-psx .b_retro_dpad,body.core-psx .b_retro_lstick,body.core-psx .b_retro_rstick{width:105px!important;height:105px!important}}
""".strip()
s = must_replace(s, '</style>', css + '</style>', 'PS1 real touch geometry CSS')

# The class must already exist when EmulatorJS constructs nipplejs zones.
s = must_replace(s, "currentCore=config.core;currentStateKey=", "currentCore=config.core;document.body.classList.toggle('core-psx',currentCore==='psx');currentStateKey=", 'PS1 body class')

# PCSX-ReARMed otherwise defaults to a digital Standard controller, where analog values
# are ignored. Make pad 1 a DualShock before core settings are written.
s = must_replace(
    s,
    "window.EJS_defaultOptions={...(config.nesCore?{'retroarch_core':config.nesCore}:{}),'save-state-location':'browser'",
    "window.EJS_defaultOptions={...(config.nesCore?{'retroarch_core':config.nesCore}:{}),...(config.core==='psx'?{'pcsx_rearmed_pad1type':'dualshock'}:{}),'save-state-location':'browser'",
    'PCSX-ReARMed DualShock core option',
)

old = "function configureNesJoypad(){if(currentCore!=='nes')return;const gm=window.EJS_emulator?.gameManager;if(!gm)return;try{gm.setControllerPortDevice?.(0,1)}catch(_){}try{gm.functions?.setControllerPortDevice?.(0,1)}catch(_){}disableNesNativeGamepad();ensureNesInputFocus()}"
new = old + "\nfunction configurePsxDualShock(){if(currentCore!=='psx')return;const gm=window.EJS_emulator?.gameManager;if(!gm)return;try{gm.setControllerPortDevice?.(0,517)}catch(_){}try{gm.functions?.setControllerPortDevice?.(0,517)}catch(_){}}"
s = must_replace(s, old, new, 'runtime PS1 DualShock device')

s = must_replace(
    s,
    "if(currentCore==='nes'){configureNesJoypad();setTimeout(configureNesJoypad,100);setTimeout(ensureNesInputFocus,250);setTimeout(ensureNesInputFocus,700)}applySettings(currentSettings);",
    "if(currentCore==='nes'){configureNesJoypad();setTimeout(configureNesJoypad,100);setTimeout(ensureNesInputFocus,250);setTimeout(ensureNesInputFocus,700)}if(currentCore==='psx'){configurePsxDualShock();setTimeout(configurePsxDualShock,120);setTimeout(configurePsxDualShock,600)}applySettings(currentSettings);",
    'activate PS1 DualShock on game start',
)

# Our outer physical-gamepad bridge used 1 for analog IDs. Libretro expects a signed
# 16-bit analog range; full deflection is 32767.
old = "function emitInput(id,on){const prev=!!inputState[id];if(prev===!!on)return;inputState[id]=!!on;if(id===24&&on){quickSave(currentSettings.quickSlot);return}if(id===25&&on){quickLoad(currentSettings.quickSlot);return}try{window.EJS_emulator?.gameManager?.simulateInput?.(0,id,on?1:0)}catch(_){}}"
new = "function emitInput(id,on){const prev=!!inputState[id];if(prev===!!on)return;inputState[id]=!!on;if(id===24&&on){quickSave(currentSettings.quickSlot);return}if(id===25&&on){quickLoad(currentSettings.quickSlot);return}const analog=id>=16&&id<=23;try{window.EJS_emulator?.gameManager?.simulateInput?.(0,id,on?(analog?32767:1):0)}catch(_){}}"
s = must_replace(s, old, new, 'full-scale physical analog input')

# Discard only obsolete PS1 movement positions. Do not touch face/shoulder/Start/Select.
old = "function migratePsTouchLayout284(){if(currentCore!=='psx')return;try{if(localStorage.getItem('retro-ps1-touch-layout-v285'))return;const data=getLayout();delete data.b_retro_dpad;delete data.b_retro_stick;localStorage.setItem(layoutKey(),JSON.stringify(data));localStorage.setItem('retro-ps1-touch-layout-v285','1')}catch(_){}}"
new = "function migratePsTouchLayout284(){if(currentCore!=='psx')return;try{if(localStorage.getItem('retro-ps1-touch-layout-v286'))return;const data=getLayout();delete data.b_retro_dpad;delete data.b_retro_stick;delete data.b_retro_lstick;delete data.b_retro_rstick;localStorage.setItem(layoutKey(),JSON.stringify(data));localStorage.setItem('retro-ps1-touch-layout-v286','1')}catch(_){}}"
s = must_replace(s, old, new, 'PS1 movement layout migration v286')

p.write_text(s, encoding='utf-8')

g = Path('app/build.gradle')
t = g.read_text(encoding='utf-8')
t = must_replace(t, 'versionCode 26', 'versionCode 27', 'version code')
t = must_replace(t, "versionName '2.8.5'", "versionName '2.8.6'", 'version name')
g.write_text(t, encoding='utf-8')

print('Retro 3 v2.8.6 PS1 DualShock: separated D-pad + two analog sticks')
