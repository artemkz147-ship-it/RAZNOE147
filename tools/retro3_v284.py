from pathlib import Path


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'v284 patch missing: {label}')
    return text.replace(old, new, 1)

p = Path('app/src/main/assets/game.html')
s = p.read_text(encoding='utf-8')

# Do not move already-created EmulatorJS controls with fixed positioning/transforms.
# That visually moves the wrappers but breaks their real touch hit boxes, especially
# the nipplejs analog zone.  Put PS1 controls apart in the native VirtualGamepad config.
s = must_replace(
    s,
    "{type:'dpad',id:'retro_dpad',location:'left',left:'50%',top:'0%',joystickInput:false,inputValues:[4,5,6,7]},{type:'zone',id:'retro_stick',location:'left',left:'50%',top:'100%',color:'#e8edf5',joystickInput:true,inputValues:[16,17,18,19]}",
    "{type:'dpad',id:'retro_dpad',location:'left',left:'50%',top:'-20%',joystickInput:false,inputValues:[4,5,6,7]},{type:'zone',id:'retro_stick',location:'left',left:'50%',top:'145%',color:'#e8edf5',joystickInput:true,inputValues:[16,17,18,19]}",
    'PS1 native virtual control spacing',
)

old_helpers = "function applyPsFactoryLayout(){if(currentCore!=='psx')return;const d=document.querySelector('.b_retro_dpad'),st=document.querySelector('.b_retro_stick');if(!d||!st)return;const h=Math.max(1,innerHeight),w=Math.max(1,innerWidth),compact=h<520,left=Math.max(125,Math.min(185,w*.13)),dy=compact?h*.32:h*.40,sy=compact?h*.82:h*.80,dScale=compact?.78:1,sScale=compact?.72:.90;const place=(el,y,scale)=>{el.style.setProperty('position','fixed','important');el.style.setProperty('left',`${left}px`,'important');el.style.setProperty('top',`${y}px`,'important');el.style.setProperty('right','auto','important');el.style.setProperty('bottom','auto','important');el.style.setProperty('transform',`translate(-50%,-50%) scale(${scale})`,'important');el.style.setProperty('transform-origin','center center','important')};place(d,dy,dScale);place(st,sy,sScale)}\nfunction migratePsOverlappingLayout(){if(currentCore!=='psx')return;try{const data=getLayout(),d=data.b_retro_dpad,st=data.b_retro_stick;if(!d||!st)return;const dx=(Number(d.x)-Number(st.x))*innerWidth/100,dy=(Number(d.y)-Number(st.y))*innerHeight/100;if(Math.hypot(dx,dy)<190){delete data.b_retro_dpad;delete data.b_retro_stick;localStorage.setItem(layoutKey(),JSON.stringify(data))}}catch(_){}}\n"
new_helpers = "function migratePsTouchLayout284(){if(currentCore!=='psx')return;try{if(localStorage.getItem('retro-ps1-touch-layout-v284'))return;const data=getLayout();delete data.b_retro_dpad;delete data.b_retro_stick;localStorage.setItem(layoutKey(),JSON.stringify(data));localStorage.setItem('retro-ps1-touch-layout-v284','1')}catch(_){}}\n"
s = must_replace(s, old_helpers, new_helpers, 'remove broken PS1 DOM layout transforms')

s = must_replace(
    s,
    "startConfirmed=true;if(currentCore==='psx'){applyPsFactoryLayout();migratePsOverlappingLayout()}captureDefaultLayout(true);",
    "startConfirmed=true;if(currentCore==='psx')migratePsTouchLayout284();captureDefaultLayout(true);",
    'PS1 clean factory start',
)

s = must_replace(
    s,
    "new MutationObserver(()=>{if(startConfirmed){keepAspect();if(currentCore==='psx'&&!layoutEditing)applyPsFactoryLayout();applySavedLayout()}})",
    "new MutationObserver(()=>{if(startConfirmed){keepAspect();applySavedLayout()}})",
    'do not reposition PS1 controls on DOM mutations',
)

s = must_replace(
    s,
    "window.addEventListener('resize',()=>{if(startConfirmed)setTimeout(()=>{keepAspect();if(currentCore==='psx'&&!layoutEditing)applyPsFactoryLayout();applySavedLayout()},30)});",
    "window.addEventListener('resize',()=>{if(startConfirmed)setTimeout(()=>{keepAspect();applySavedLayout()},30)});",
    'do not reposition PS1 controls on resize',
)

p.write_text(s, encoding='utf-8')

g = Path('app/build.gradle')
t = g.read_text(encoding='utf-8')
t = must_replace(t, 'versionCode 24', 'versionCode 25', 'version code')
t = must_replace(t, "versionName '2.8.3'", "versionName '2.8.4'", 'version name')
g.write_text(t, encoding='utf-8')

print('Retro 3 v2.8.4 PS1 virtual D-pad/analog touch repair applied')
