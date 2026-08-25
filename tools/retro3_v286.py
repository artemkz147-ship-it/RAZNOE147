from pathlib import Path


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'v286 patch missing: {label}')
    return text.replace(old, new, 1)

p = Path('app/src/main/assets/launcher.js')
s = p.read_text(encoding='utf-8')
s = must_replace(
    s,
    "function getSettings(){try{return normalizeSettings(JSON.parse(localStorage.getItem('retro-settings')||'{}'))}catch(_){return{...defaults}}}",
    "function getSettings(){try{const n=normalizeSettings(JSON.parse(localStorage.getItem('retro-settings')||'{}'));if(!localStorage.getItem('retro-ps1-both-v286')){n.virtualPs='both';localStorage.setItem('retro-settings',JSON.stringify(n));localStorage.setItem('retro-ps1-both-v286','1')}return n}catch(_){return{...defaults}}}",
    'PS1 both-controls one-time migration',
)
p.write_text(s, encoding='utf-8')

p = Path('app/src/main/assets/game.html')
s = p.read_text(encoding='utf-8')

s = must_replace(
    s,
    "body.dir-dpad .b_retro_stick{display:none!important}body.dir-stick .b_retro_dpad{display:none!important}body.dir-both .b_retro_dpad,body.dir-both .b_retro_stick{display:block}",
    "body.dir-dpad .b_retro_stick{display:none!important}body.dir-stick .b_retro_dpad{display:none!important}body.dir-both .b_retro_dpad,body.dir-both .b_retro_stick{display:block}.retro-ps-custom{position:fixed!important;z-index:999994!important;touch-action:none!important;user-select:none!important;-webkit-user-select:none!important}.retro-ps-dpad{left:calc(var(--retro-safe-left) + 118px);top:38%;width:126px;height:126px;opacity:.78}.retro-ps-dpad .ps-dpad-v,.retro-ps-dpad .ps-dpad-h{position:absolute;background:rgba(255,255,255,.28);border:1px solid rgba(255,255,255,.28);border-radius:7px}.retro-ps-dpad .ps-dpad-v{width:42px;height:126px;left:42px;top:0}.retro-ps-dpad .ps-dpad-h{width:126px;height:42px;left:0;top:42px}.retro-ps-dpad .ps-dpad-center{position:absolute;left:42px;top:42px;width:42px;height:42px;background:rgba(255,255,255,.28)}.retro-ps-dpad .ps-arrow{position:absolute;color:rgba(20,20,20,.7);font:900 24px/1 system-ui}.retro-ps-dpad .up{left:51px;top:5px}.retro-ps-dpad .down{left:51px;bottom:5px}.retro-ps-dpad .left{left:8px;top:51px}.retro-ps-dpad .right{right:8px;top:51px}.retro-ps-stick{left:calc(var(--retro-safe-left) + 118px);top:78%;width:118px;height:118px;border-radius:50%;background:rgba(255,255,255,.23);border:1px solid rgba(255,255,255,.2);opacity:.82}.retro-ps-stick .ps-stick-knob{position:absolute;left:50%;top:50%;width:62px;height:62px;margin-left:-31px;margin-top:-31px;border-radius:50%;background:rgba(255,255,255,.22);box-shadow:inset 0 0 0 1px rgba(255,255,255,.12);transform:translate(0,0);pointer-events:none}",
    'PS1 independent virtual controls CSS',
)

s = must_replace(
    s,
    "{type:'button',text:'△',id:'triangle',location:'right',right:75,top:10,bold:true,input_value:9},{type:'dpad',id:'retro_dpad',location:'left',left:'50%',top:'0%',joystickInput:false,inputValues:[4,5,6,7]},{type:'zone',id:'retro_stick',location:'left',left:'50%',top:'100%',color:'#e8edf5',joystickInput:true,inputValues:[16,17,18,19]},{type:'button',text:'L1'",
    "{type:'button',text:'△',id:'triangle',location:'right',right:75,top:10,bold:true,input_value:9},{type:'button',text:'L1'",
    'remove broken built-in PS1 left controls',
)

s = must_replace(
    s,
    "function migratePsTouchLayout284(){if(currentCore!=='psx')return;try{if(localStorage.getItem('retro-ps1-touch-layout-v285'))return;const data=getLayout();delete data.b_retro_dpad;delete data.b_retro_stick;localStorage.setItem(layoutKey(),JSON.stringify(data));localStorage.setItem('retro-ps1-touch-layout-v285','1')}catch(_){}}",
    "function migratePsTouchLayout284(){if(currentCore!=='psx')return;try{if(localStorage.getItem('retro-ps1-touch-layout-v286'))return;const data=getLayout();delete data.b_retro_dpad;delete data.b_retro_stick;localStorage.setItem(layoutKey(),JSON.stringify(data));localStorage.setItem('retro-ps1-touch-layout-v286','1')}catch(_){}}",
    'PS1 custom-control layout migration',
)

needle = "function getLayout(){try{return JSON.parse(localStorage.getItem(layoutKey())||'{}')||{}}catch(_){return{}}}"
bridge = r'''function psSim(index,value){try{window.EJS_emulator?.gameManager?.simulateInput?.(0,index,value)}catch(_){}}
function psReleaseMovement(){for(const i of[4,5,6,7,16,17,18,19])psSim(i,0)}
function installPsVirtualBridge(){if(currentCore!=='psx'||!startConfirmed)return;const parent=document.querySelector('.ejs_virtualGamepad_parent');if(!parent)return;parent.querySelectorAll('.retro-ps-custom').forEach(el=>el.remove());const d=document.createElement('div');d.className='b_retro_dpad retro-ps-custom retro-ps-dpad';d.innerHTML='<div class="ps-dpad-v"></div><div class="ps-dpad-h"></div><div class="ps-dpad-center"></div><span class="ps-arrow up">▲</span><span class="ps-arrow down">▼</span><span class="ps-arrow left">◀</span><span class="ps-arrow right">▶</span>';const st=document.createElement('div');st.className='b_retro_stick retro-ps-custom retro-ps-stick';st.innerHTML='<div class="ps-stick-knob"></div>';parent.appendChild(d);parent.appendChild(st);let dp=null,sp=null;const dmove=e=>{if(layoutEditing||dp!==e.pointerId)return;e.preventDefault();const r=d.getBoundingClientRect(),x=(e.clientX-(r.left+r.width/2))/(r.width/2),y=(e.clientY-(r.top+r.height/2))/(r.height/2),ax=Math.abs(x),ay=Math.abs(y);let up=y<-.18,down=y>.18,left=x<-.18,right=x>.18;if(ax>ay*1.75){up=down=false}else if(ay>ax*1.75){left=right=false}psSim(4,up?1:0);psSim(5,down?1:0);psSim(6,left?1:0);psSim(7,right?1:0)};const dend=e=>{if(dp!==e.pointerId)return;e.preventDefault();dp=null;for(const i of[4,5,6,7])psSim(i,0)};d.addEventListener('pointerdown',e=>{if(layoutEditing)return;e.preventDefault();dp=e.pointerId;try{d.setPointerCapture(dp)}catch(_){};dmove(e)},true);d.addEventListener('pointermove',dmove,true);d.addEventListener('pointerup',dend,true);d.addEventListener('pointercancel',dend,true);const knob=st.querySelector('.ps-stick-knob');const smove=e=>{if(layoutEditing||sp!==e.pointerId)return;e.preventDefault();const r=st.getBoundingClientRect(),rad=Math.min(r.width,r.height)*.34,dx=e.clientX-(r.left+r.width/2),dy=e.clientY-(r.top+r.height/2),len=Math.hypot(dx,dy)||1,scale=Math.min(1,rad/len),px=dx*scale,py=dy*scale,nx=Math.max(-1,Math.min(1,px/rad)),ny=Math.max(-1,Math.min(1,py/rad));knob.style.transform=`translate(${px}px,${py}px)`;psSim(16,nx>0?Math.round(nx*32767):0);psSim(17,nx<0?Math.round(-nx*32767):0);psSim(18,ny>0?Math.round(ny*32767):0);psSim(19,ny<0?Math.round(-ny*32767):0);const th=.28;psSim(4,ny<-th?1:0);psSim(5,ny>th?1:0);psSim(6,nx<-th?1:0);psSim(7,nx>th?1:0)};const send=e=>{if(sp!==e.pointerId)return;e.preventDefault();sp=null;knob.style.transform='translate(0,0)';psReleaseMovement()};st.addEventListener('pointerdown',e=>{if(layoutEditing)return;e.preventDefault();sp=e.pointerId;try{st.setPointerCapture(sp)}catch(_){};smove(e)},true);st.addEventListener('pointermove',smove,true);st.addEventListener('pointerup',send,true);st.addEventListener('pointercancel',send,true);applyDirection(currentSettings);applySavedLayout()}
'''
s = must_replace(s, needle, bridge + needle, 'direct PS1 virtual input bridge')

s = must_replace(
    s,
    "startConfirmed=true;if(currentCore==='psx')migratePsTouchLayout284();captureDefaultLayout(true);",
    "startConfirmed=true;if(currentCore==='psx'){migratePsTouchLayout284();installPsVirtualBridge()}captureDefaultLayout(true);",
    'install PS1 controls before factory layout capture',
)
s = must_replace(
    s,
    "if(currentCore==='nes'){installNesVirtualBridge();setTimeout(installNesVirtualBridge,120);setTimeout(installNesVirtualBridge,500)}try{document.querySelector('#game')?.focus?.()}",
    "if(currentCore==='nes'){installNesVirtualBridge();setTimeout(installNesVirtualBridge,120);setTimeout(installNesVirtualBridge,500)}if(currentCore==='psx'){setTimeout(installPsVirtualBridge,120);setTimeout(installPsVirtualBridge,500)}try{document.querySelector('#game')?.focus?.()}",
    'reinstall PS1 controls after emulator settles',
)

p.write_text(s, encoding='utf-8')

g = Path('app/build.gradle')
t = g.read_text(encoding='utf-8')
t = must_replace(t, 'versionCode 26', 'versionCode 27', 'version code')
t = must_replace(t, "versionName '2.8.5'", "versionName '2.8.6'", 'version name')
g.write_text(t, encoding='utf-8')

print('Retro 3 v2.8.6 installs independent PS1 D-pad + stick with direct core input')
