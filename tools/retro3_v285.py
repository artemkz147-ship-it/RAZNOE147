from pathlib import Path


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'v285 patch missing: {label}')
    return text.replace(old, new, 1)

p = Path('app/src/main/assets/game.html')
s = p.read_text(encoding='utf-8')

# v2.8.4 accidentally pushed both PS1 movement controls outside the usable left zone:
# D-pad to -20% and analog zone to 145%.  EmulatorJS itself uses 0% / 100% for the
# proven N64 two-control left layout, with the same dpad/zone types and input values.
s = must_replace(
    s,
    "{type:'dpad',id:'retro_dpad',location:'left',left:'50%',top:'-20%',joystickInput:false,inputValues:[4,5,6,7]},{type:'zone',id:'retro_stick',location:'left',left:'50%',top:'145%',color:'#e8edf5',joystickInput:true,inputValues:[16,17,18,19]}",
    "{type:'dpad',id:'retro_dpad',location:'left',left:'50%',top:'0%',joystickInput:false,inputValues:[4,5,6,7]},{type:'zone',id:'retro_stick',location:'left',left:'50%',top:'100%',color:'#e8edf5',joystickInput:true,inputValues:[16,17,18,19]}",
    'restore proven EmulatorJS PS1 left control geometry',
)

# Users who already launched 2.8.4 have its migration marker.  Run one new migration so
# stale manual positions of these two controls cannot override the repaired factory layout.
s = must_replace(
    s,
    "function migratePsTouchLayout284(){if(currentCore!=='psx')return;try{if(localStorage.getItem('retro-ps1-touch-layout-v284'))return;const data=getLayout();delete data.b_retro_dpad;delete data.b_retro_stick;localStorage.setItem(layoutKey(),JSON.stringify(data));localStorage.setItem('retro-ps1-touch-layout-v284','1')}catch(_){}}",
    "function migratePsTouchLayout284(){if(currentCore!=='psx')return;try{if(localStorage.getItem('retro-ps1-touch-layout-v285'))return;const data=getLayout();delete data.b_retro_dpad;delete data.b_retro_stick;localStorage.setItem(layoutKey(),JSON.stringify(data));localStorage.setItem('retro-ps1-touch-layout-v285','1')}catch(_){}}",
    'force one-time repaired PS1 touch layout migration',
)

p.write_text(s, encoding='utf-8')

g = Path('app/build.gradle')
t = g.read_text(encoding='utf-8')
t = must_replace(t, 'versionCode 25', 'versionCode 26', 'version code')
t = must_replace(t, "versionName '2.8.4'", "versionName '2.8.5'", 'version name')
g.write_text(t, encoding='utf-8')

print('Retro 3 v2.8.5 restores PS1 D-pad + analog stick to proven EmulatorJS geometry')
