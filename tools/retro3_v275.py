from pathlib import Path


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'v275 patch missing: {label}')
    return text.replace(old, new, 1)

p = Path('app/src/main/assets/game.html')
s = p.read_text(encoding='utf-8')

# v2.7.4 measured touches against .b_retro_dpad. That wrapper can have a near-zero
# height because the visible cross (.ejs_dpad_main) is absolutely positioned inside it.
# As a result Y coordinates were wrong: UP could become DOWN and horizontal presses
# could effectively disappear. Measure against the actual visible d-pad and mirror
# EmulatorJS' own proven direction algorithm exactly.
old = """function nesTouchPoint(e,el){const t=(e.targetTouches&&e.targetTouches[0])||(e.touches&&e.touches[0])||(e.changedTouches&&e.changedTouches[0]);if(!t)return null;const r=el.getBoundingClientRect();return{x:(t.clientX-(r.left+r.width/2))/Math.max(1,r.width/2),y:(t.clientY-(r.top+r.height/2))/Math.max(1,r.height/2)}}
function nesReleaseDirections(){manualInput(4,0);manualInput(5,0);manualInput(6,0);manualInput(7,0)}
function nesDirectionsFromTouch(e,el){const p=nesTouchPoint(e,el);if(!p){nesReleaseDirections();return}const dead=.17;manualInput(4,p.y<-dead);manualInput(5,p.y>dead);manualInput(6,p.x<-dead);manualInput(7,p.x>dead)}
"""
new = """function nesReleaseDirections(el=null){manualInput(4,0);manualInput(5,0);manualInput(6,0);manualInput(7,0);if(el){el.classList.remove('ejs_dpad_up_pressed','ejs_dpad_down_pressed','ejs_dpad_left_pressed','ejs_dpad_right_pressed')}}
function nesDpadRect(el){let r=el.getBoundingClientRect();if(r.width<40||r.height<40){const host=el.closest('.ejs_virtualGamepad_left');if(host){const hr=host.getBoundingClientRect();if(hr.width>=40&&hr.height>=40)r=hr}}return r}
function nesDirectionsFromTouch(e,el){const touch=(e.targetTouches&&e.targetTouches[0])||(e.touches&&e.touches[0])||(e.changedTouches&&e.changedTouches[0]);if(!touch){nesReleaseDirections(el);return}const r=nesDpadRect(el),x=touch.clientX-r.left-r.width/2,y=touch.clientY-r.top-r.height/2;let up=0,down=0,left=0,right=0,angle=Math.atan(x/y)/(Math.PI/180);if(y<=-10)up=1;if(y>=10)down=1;if(x>=10){right=1;left=0;if((angle<0&&angle>=-35)||(angle>0&&angle<=35))right=0;up=(angle<0&&angle>=-55)?1:0;down=(angle>0&&angle<=55)?1:0}if(x<=-10){right=0;left=1;if((angle<0&&angle>=-35)||(angle>0&&angle<=35))left=0;up=(angle>0&&angle<=55)?1:0;down=(angle<0&&angle>=-55)?1:0}el.classList.toggle('ejs_dpad_up_pressed',!!up);el.classList.toggle('ejs_dpad_down_pressed',!!down);el.classList.toggle('ejs_dpad_left_pressed',!!left);el.classList.toggle('ejs_dpad_right_pressed',!!right);manualInput(4,up);manualInput(5,down);manualInput(6,left);manualInput(7,right)}
"""
s = must_replace(s, old, new, 'replace buggy NES dpad geometry')

# Listen on the real d-pad hit surface. Do not add a second touch bridge to the
# virtual stick: nipplejs/EmulatorJS already handles the stick and the NES port is
# now explicitly configured, so a second handler only creates conflicting input.
old = """const bindPad=selector=>{const el=document.querySelector(selector);if(!el)return;el.style.setProperty('touch-action','none','important');const move=e=>{if(layoutEditing)return;e.preventDefault();e.stopImmediatePropagation();nesDirectionsFromTouch(e,el)};const end=e=>{if(layoutEditing)return;e.preventDefault();e.stopImmediatePropagation();nesReleaseDirections()};nesListen(el,'touchstart',move,true);nesListen(el,'touchmove',move,true);nesListen(el,'touchend',end,true);nesListen(el,'touchcancel',end,true)};bindPad('.b_retro_dpad');bindPad('.b_retro_stick')"""
new = """const host=document.querySelector('.b_retro_dpad');const dpad=host?.querySelector('.ejs_dpad_main')||host;if(dpad){dpad.style.setProperty('touch-action','none','important');const move=e=>{if(layoutEditing)return;e.preventDefault();e.stopImmediatePropagation();nesDirectionsFromTouch(e,dpad)};const end=e=>{if(layoutEditing)return;e.preventDefault();e.stopImmediatePropagation();nesReleaseDirections(dpad)};nesListen(dpad,'touchstart',move,true);nesListen(dpad,'touchmove',move,true);nesListen(dpad,'touchend',end,true);nesListen(dpad,'touchcancel',end,true)}"""
s = must_replace(s, old, new, 'bind NES bridge to real dpad surface only')

p.write_text(s, encoding='utf-8')

# Update in place with the same release certificate.
g = Path('app/build.gradle')
t = g.read_text(encoding='utf-8')
t = must_replace(t, 'versionCode 15', 'versionCode 16', 'version code')
t = must_replace(t, "versionName '2.7.4'", "versionName '2.7.5'", 'version name')
g.write_text(t, encoding='utf-8')

print('Retro 3 v2.7.5 NES dpad geometry fix applied')
