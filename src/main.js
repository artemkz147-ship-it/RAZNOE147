import * as THREE from 'three';
import RAPIER from '@dimforge/rapier3d-compat';
import './styles.css';

const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
const lerp=(a,b,t)=>a+(b-a)*t;
const damp=(a,b,s,dt)=>lerp(a,b,1-Math.exp(-s*dt));
const rand=(a,b)=>a+Math.random()*(b-a);
const isTouch=matchMedia('(pointer: coarse)').matches;

const app=document.querySelector('#app');
app.innerHTML=`
<div id="hud">
  <div>
    <div class="topbar">
      <div class="glass brand">ECHO RUINS</div>
      <div class="glass stats">
        <div class="pill">❤ <span class="bar"><i id="hpbar"></i></span></div>
        <div class="pill">◆ <b id="shards">0/8</b></div>
        <div class="pill">⏱ <b id="timer">00:00</b></div>
      </div>
    </div>
    <div id="objective" class="glass objective">Собери 8 осколков и открой портал</div>
  </div>
  <div id="hint" class="glass hint">WASD — движение · Space — прыжок · Shift — рывок · E — удар · мышь — камера</div>
</div>
<div id="mobile">
  <div id="stickZone" class="stick-zone"><div class="stick-base"></div><div id="stickKnob" class="stick-knob"></div></div>
  <div class="actions"><button id="dash" class="action">РЫВОК</button><button id="attack" class="action">УДАР</button><button id="jump" class="action">ПРЫЖОК</button></div>
</div>
<div id="centerMessage"><div id="messageTitle" class="title"></div><div id="messageSub" class="sub"></div></div>`;

const ui={
  hp:document.querySelector('#hpbar'), shards:document.querySelector('#shards'), timer:document.querySelector('#timer'),
  objective:document.querySelector('#objective'), hint:document.querySelector('#hint'), msg:document.querySelector('#centerMessage'),
  msgTitle:document.querySelector('#messageTitle'), msgSub:document.querySelector('#messageSub')
};
if(isTouch) ui.hint.textContent='Левый стик — движение · веди пальцем справа — камера';
setTimeout(()=>ui.hint.classList.add('hidden'),6500);

class AudioFX{
  constructor(){this.ctx=null;this.master=.12}
  ensure(){if(!this.ctx)this.ctx=new (window.AudioContext||window.webkitAudioContext)(); if(this.ctx.state==='suspended')this.ctx.resume()}
  tone(freq=440,dur=.08,type='sine',gain=.18,slide=0){this.ensure();const t=this.ctx.currentTime,o=this.ctx.createOscillator(),g=this.ctx.createGain();o.type=type;o.frequency.setValueAtTime(freq,t);if(slide)o.frequency.exponentialRampToValueAtTime(Math.max(30,freq+slide),t+dur);g.gain.setValueAtTime(gain*this.master,t);g.gain.exponentialRampToValueAtTime(.0001,t+dur);o.connect(g).connect(this.ctx.destination);o.start(t);o.stop(t+dur+.02)}
  jump(){this.tone(280,.12,'triangle',.3,260)} pickup(){this.tone(620,.08,'sine',.25,420);setTimeout(()=>this.tone(980,.1,'sine',.18,250),45)}
  dash(){this.tone(150,.14,'sawtooth',.18,360)} hit(){this.tone(90,.16,'square',.28,-45)} attack(){this.tone(240,.08,'sawtooth',.14,-100)}
  checkpoint(){this.tone(430,.14,'sine',.18,260);setTimeout(()=>this.tone(720,.18,'triangle',.16,220),90)} portal(){this.tone(260,.4,'sine',.18,620)}
}
const sfx=new AudioFX();
window.addEventListener('pointerdown',()=>sfx.ensure(),{once:true});

await RAPIER.init();
const physicsWorld=new RAPIER.World({x:0,y:-9.81,z:0});

const scene=new THREE.Scene();
scene.background=new THREE.Color(0x07131d);
scene.fog=new THREE.FogExp2(0x07131d,0.0135);
const camera=new THREE.PerspectiveCamera(62,innerWidth/innerHeight,.08,280);
const renderer=new THREE.WebGLRenderer({antialias:!isTouch,powerPreference:'high-performance'});
renderer.setPixelRatio(Math.min(devicePixelRatio,isTouch?1.45:1.9));
renderer.setSize(innerWidth,innerHeight);
renderer.shadowMap.enabled=true;renderer.shadowMap.type=THREE.PCFSoftShadowMap;
renderer.outputColorSpace=THREE.SRGBColorSpace;renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=1.12;
app.prepend(renderer.domElement);

scene.add(new THREE.HemisphereLight(0x88dcff,0x18213a,1.35));
const sun=new THREE.DirectionalLight(0xfff1d6,2.3);sun.position.set(-20,38,18);sun.castShadow=true;sun.shadow.mapSize.set(isTouch?1024:2048,isTouch?1024:2048);sun.shadow.camera.left=-34;sun.shadow.camera.right=34;sun.shadow.camera.top=34;sun.shadow.camera.bottom=-34;sun.shadow.camera.far=100;scene.add(sun);
const rim=new THREE.DirectionalLight(0x39e6ff,1.0);rim.position.set(24,8,-28);scene.add(rim);

const palette={stone:0x213340,stone2:0x182932,top:0x33555a,cyan:0x66f4f1,blue:0x3c9dff,pink:0xff5da8,gold:0xffc96b,dark:0x071018,enemy:0xff5a72};
const mats={
 stone:new THREE.MeshStandardMaterial({color:palette.stone,roughness:.78,metalness:.18}),
 stone2:new THREE.MeshStandardMaterial({color:palette.stone2,roughness:.9,metalness:.08}),
 top:new THREE.MeshStandardMaterial({color:palette.top,roughness:.72,metalness:.05}),
 cyan:new THREE.MeshStandardMaterial({color:palette.cyan,emissive:0x1dc7cf,emissiveIntensity:1.5,roughness:.28,metalness:.38}),
 pink:new THREE.MeshStandardMaterial({color:palette.pink,emissive:0x9a174f,emissiveIntensity:1.45,roughness:.26,metalness:.32}),
 gold:new THREE.MeshStandardMaterial({color:palette.gold,emissive:0x9a5a12,emissiveIntensity:.9,roughness:.35,metalness:.55}),
 dark:new THREE.MeshStandardMaterial({color:palette.dark,roughness:.5,metalness:.72}),
 enemy:new THREE.MeshStandardMaterial({color:palette.enemy,emissive:0x7e1224,emissiveIntensity:1.15,roughness:.35,metalness:.5})
};

function mesh(geo,mat,cast=true,receive=true){const m=new THREE.Mesh(geo,mat);m.castShadow=cast;m.receiveShadow=receive;return m}
function addStars(){const n=isTouch?700:1200,p=new Float32Array(n*3);for(let i=0;i<n;i++){const r=rand(70,190),a=rand(0,Math.PI*2),y=rand(-25,95);p[i*3]=Math.cos(a)*r;p[i*3+1]=y;p[i*3+2]=Math.sin(a)*r}const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.BufferAttribute(p,3));const ps=new THREE.Points(g,new THREE.PointsMaterial({color:0xb8f3ff,size:.18,sizeAttenuation:true,transparent:true,opacity:.72}));scene.add(ps)}
addStars();

const ruinGroup=new THREE.Group();scene.add(ruinGroup);
for(let i=0;i<24;i++){const h=rand(4,18),r=rand(.35,1.1),x=rand(-42,42),z=rand(-85,18);if(Math.abs(x)<10&&z>-12)continue;const c=mesh(new THREE.CylinderGeometry(r,r*1.2,h,6),mats.stone2,false,true);c.position.set(x,rand(-11,-2),z);c.rotation.z=rand(-.12,.12);ruinGroup.add(c);if(Math.random()>.45){const glow=mesh(new THREE.BoxGeometry(.08,h*.5,.08),Math.random()>.5?mats.cyan:mats.pink,false,false);glow.position.set(x+r*.65,c.position.y+h*.08,z);ruinGroup.add(glow)}}

const platforms=[];
const platformGroup=new THREE.Group();scene.add(platformGroup);
function addPlatform(x,y,z,sx,sy,sz,opts={}){
  const g=new THREE.Group();g.position.set(x,y,z);platformGroup.add(g);
  const base=mesh(new THREE.BoxGeometry(sx,sy,sz),mats.stone);g.add(base);
  const top=mesh(new THREE.BoxGeometry(sx*.96,.12,sz*.96),mats.top);top.position.y=sy*.5+.055;g.add(top);
  const edgeMat=opts.accent==='pink'?mats.pink:mats.cyan;
  const edge=mesh(new THREE.BoxGeometry(sx*.72,.055,.07),edgeMat,false,false);edge.position.set(0,sy*.5+.13,sz*.5-.04);g.add(edge);
  const teeth=opts.teeth??Math.floor(sx/2.4);for(let i=0;i<teeth;i++){const b=mesh(new THREE.BoxGeometry(rand(.3,.8),rand(.5,1.7),rand(.3,.8)),mats.stone2,false,true);b.position.set(rand(-sx*.42,sx*.42),-sy*.5-rand(.3,1.2),rand(-sz*.4,sz*.4));b.rotation.y=rand(0,Math.PI);g.add(b)}
  const p={x,y,z,sx,sy,sz,baseX:x,baseY:y,baseZ:z,group:g,motion:opts.motion||null,lastX:x,lastY:y,lastZ:z,dx:0,dy:0,dz:0};platforms.push(p);
  const rb=physicsWorld.createRigidBody(RAPIER.RigidBodyDesc.fixed().setTranslation(x,y,z));physicsWorld.createCollider(RAPIER.ColliderDesc.cuboid(sx/2,sy/2,sz/2),rb);
  return p;
}

addPlatform(0,0,2,9,1,9);
addPlatform(0,1.0,-7,5.4,1,4.2);
addPlatform(4.8,2.2,-13,5.2,1,4.4,{accent:'pink'});
addPlatform(9.1,3.7,-19,4.7,1,4.1);
addPlatform(4.7,5.2,-25,4.4,1,4.2,{motion:{axis:'x',amp:2.1,speed:.72}});
addPlatform(-1.4,6.5,-30.5,5.3,1,4.5);
addPlatform(-7.2,7.8,-34.5,4.6,1,4.1,{accent:'pink'});
addPlatform(-11,9.0,-41,5.3,1,5.1);
addPlatform(-5.4,10.2,-47,4.2,1,4.0,{motion:{axis:'y',amp:1.25,speed:.95}});
addPlatform(1,11.1,-51,5.2,1,4.5);
addPlatform(6.8,12.2,-56.5,4.8,1,4.6,{accent:'pink'});
addPlatform(12,13.6,-62,5.8,1,5.0);
addPlatform(7.2,15.0,-68,4.0,1,4.0,{motion:{axis:'x',amp:2.3,speed:.8}});
addPlatform(0.4,16.1,-72,5.4,1,4.8);
addPlatform(-6.1,17.4,-77,5.1,1,4.8,{accent:'pink'});
addPlatform(-12.2,18.7,-82.5,6.4,1,5.8);

for(const p of platforms){if(Math.random()>.42){const count=Math.floor(rand(1,4));for(let i=0;i<count;i++){const crystal=new THREE.Group();const h=rand(.45,1.2);const c=mesh(new THREE.ConeGeometry(rand(.12,.28),h,5),Math.random()>.5?mats.cyan:mats.pink,false,false);c.position.y=h/2;crystal.add(c);crystal.position.set(rand(-p.sx*.4,p.sx*.4),p.sy*.5+.06,rand(-p.sz*.38,p.sz*.38));crystal.rotation.z=rand(-.2,.2);p.group.add(crystal)}}}

function aabbFor(p){return{minX:p.x-p.sx/2,maxX:p.x+p.sx/2,minY:p.y-p.sy/2,maxY:p.y+p.sy/2,minZ:p.z-p.sz/2,maxZ:p.z+p.sz/2}}
const PLAYER_HALF={x:.42,y:.88,z:.42};
function overlaps(pos,h,b){return pos.x+h.x>b.minX&&pos.x-h.x<b.maxX&&pos.y+h.y>b.minY&&pos.y-h.y<b.maxY&&pos.z+h.z>b.minZ&&pos.z-h.z<b.maxZ}

function createPlayer(){
  const root=new THREE.Group(), body=new THREE.Group();root.add(body);scene.add(root);
  const torso=mesh(new THREE.CapsuleGeometry(.38,.62,5,9),mats.dark);torso.rotation.z=Math.PI/2;body.add(torso);
  const chest=mesh(new THREE.BoxGeometry(.58,.48,.48),mats.cyan);chest.position.y=.06;body.add(chest);
  const head=mesh(new THREE.BoxGeometry(.48,.42,.48),mats.dark);head.position.y=.72;body.add(head);
  const visor=mesh(new THREE.BoxGeometry(.38,.10,.05),mats.cyan,false,false);visor.position.set(0,.75,-.255);body.add(visor);
  const limbs=[];for(const side of[-1,1]){const arm=new THREE.Group(),leg=new THREE.Group();arm.position.set(side*.42,.15,0);leg.position.set(side*.22,-.56,0);const am=mesh(new THREE.CapsuleGeometry(.10,.48,3,6),mats.stone);am.position.y=-.23;arm.add(am);const lm=mesh(new THREE.CapsuleGeometry(.12,.52,3,6),mats.stone);lm.position.y=-.27;leg.add(lm);body.add(arm,leg);limbs.push({arm,leg,side})}
  const ring=mesh(new THREE.TorusGeometry(.55,.04,8,24),mats.pink,false,false);ring.rotation.x=Math.PI/2;ring.position.y=.08;body.add(ring);
  return{root,body,limbs,ring};
}
const playerVisual=createPlayer();
const player={pos:new THREE.Vector3(0,1.5,2),vel:new THREE.Vector3(),hp:100,grounded:false,groundPlatform:null,jumps:0,coyote:0,jumpBuffer:0,dashTimer:0,dashCooldown:0,attackTimer:0,invuln:0,checkpoint:new THREE.Vector3(0,1.5,2),shards:0,dead:false,won:false,moveBlend:0};

const shards=[];const shardGeo=new THREE.OctahedronGeometry(.28,0);
function addShard(x,y,z){const g=new THREE.Group();const core=mesh(shardGeo,mats.gold,false,false);const ring=mesh(new THREE.TorusGeometry(.44,.025,6,22),mats.cyan,false,false);ring.rotation.x=Math.PI/2;g.add(core,ring);g.position.set(x,y,z);scene.add(g);shards.push({g,collected:false,phase:rand(0,6)});}
[
 [0,2.2,-7],[5,3.4,-13],[9,4.9,-19],[-1,7.7,-30.5],[-11,10.2,-41],[1,12.4,-51],[12,14.9,-62],[-6,18.7,-77]
].forEach(v=>addShard(...v));

const enemies=[];
function addEnemy(x,y,z,axis='x',range=2.0){const g=new THREE.Group();const core=mesh(new THREE.SphereGeometry(.42,12,8),mats.enemy);g.add(core);const eye=mesh(new THREE.BoxGeometry(.35,.08,.05),mats.gold,false,false);eye.position.z=-.4;g.add(eye);for(const s of[-1,1]){const wing=mesh(new THREE.BoxGeometry(.48,.08,.24),mats.dark);wing.position.x=s*.48;wing.rotation.z=s*.22;g.add(wing)}scene.add(g);g.position.set(x,y,z);enemies.push({g,origin:new THREE.Vector3(x,y,z),axis,range,phase:rand(0,6),alive:true,hp:2,cooldown:0});}
addEnemy(4.7,3.35,-13,'x',1.4);addEnemy(-1.2,7.65,-30.5,'z',1.2);addEnemy(-10.8,10.15,-41,'x',1.6);addEnemy(6.8,13.35,-56.5,'z',1.4);addEnemy(-6,18.55,-77,'x',1.3);

const checkpoints=[];
function addCheckpoint(x,y,z){const g=new THREE.Group();const pole=mesh(new THREE.CylinderGeometry(.07,.09,1.8,8),mats.stone);pole.position.y=.9;g.add(pole);const ring=mesh(new THREE.TorusGeometry(.47,.07,8,24),mats.cyan,false,false);ring.position.y=1.35;ring.rotation.y=Math.PI/2;g.add(ring);g.position.set(x,y,z);scene.add(g);checkpoints.push({g,active:false,spawn:new THREE.Vector3(x,y+.95,z)});}
addCheckpoint(-11,9.55,-41);addCheckpoint(0.4,16.65,-72);

const portal=new THREE.Group();scene.add(portal);portal.position.set(-12.2,20.2,-84.0);
const portalRing=mesh(new THREE.TorusGeometry(1.05,.16,12,40),mats.pink);portal.add(portalRing);const portalInner=mesh(new THREE.CircleGeometry(.86,32),new THREE.MeshBasicMaterial({color:0x5af6ff,transparent:true,opacity:.08,side:THREE.DoubleSide,depthWrite:false}),false,false);portalInner.position.z=.02;portal.add(portalInner);portal.visible=true;

const particles=[];
function burst(pos,color=0x66f4f1,count=10,speed=4){for(let i=0;i<count;i++){const m=mesh(new THREE.IcosahedronGeometry(rand(.025,.07),0),new THREE.MeshBasicMaterial({color}),false,false);m.position.copy(pos);scene.add(m);const v=new THREE.Vector3(rand(-1,1),rand(.15,1.2),rand(-1,1)).normalize().multiplyScalar(rand(speed*.4,speed));particles.push({m,v,life:rand(.25,.65),max:1})}}

const input={x:0,z:0,jump:false,jumpPressed:false,dashPressed:false,attackPressed:false,keys:new Set(),lookX:0,lookY:0};
addEventListener('keydown',e=>{input.keys.add(e.code);if(e.code==='Space'){input.jump=true;input.jumpPressed=true}if(e.code==='ShiftLeft'||e.code==='ShiftRight')input.dashPressed=true;if(e.code==='KeyE'||e.code==='KeyF')input.attackPressed=true});
addEventListener('keyup',e=>{input.keys.delete(e.code);if(e.code==='Space')input.jump=false});

const stick={id:null,cx:71,cy:71};const stickZone=document.querySelector('#stickZone'),knob=document.querySelector('#stickKnob');
function stickMove(e){if(stick.id!==e.pointerId)return;const r=stickZone.getBoundingClientRect(),x=e.clientX-r.left-71,y=e.clientY-r.top-71,len=Math.hypot(x,y),max=43,s=Math.min(1,max/(len||1));const dx=x*s,dy=y*s;knob.style.transform=`translate(${dx}px,${dy}px)`;input.x=clamp(dx/max,-1,1);input.z=clamp(dy/max,-1,1)}
stickZone.addEventListener('pointerdown',e=>{stick.id=e.pointerId;stickZone.setPointerCapture(e.pointerId);stickMove(e)});stickZone.addEventListener('pointermove',stickMove);stickZone.addEventListener('pointerup',e=>{if(e.pointerId===stick.id){stick.id=null;input.x=input.z=0;knob.style.transform=''}});stickZone.addEventListener('pointercancel',()=>{stick.id=null;input.x=input.z=0;knob.style.transform=''});
function button(id,onPress,onRelease){const b=document.querySelector(id);b.addEventListener('pointerdown',e=>{e.preventDefault();onPress();b.setPointerCapture(e.pointerId)});b.addEventListener('pointerup',e=>{e.preventDefault();onRelease?.()});b.addEventListener('pointercancel',()=>onRelease?.())}
button('#jump',()=>{input.jump=true;input.jumpPressed=true},()=>input.jump=false);button('#dash',()=>input.dashPressed=true);button('#attack',()=>input.attackPressed=true);

let lookPointer=null,lastLookX=0,lastLookY=0,cameraYaw=0.15,cameraPitch=.28;
renderer.domElement.addEventListener('pointerdown',e=>{if(e.clientX<innerWidth*.36&&isTouch)return;lookPointer=e.pointerId;lastLookX=e.clientX;lastLookY=e.clientY;renderer.domElement.setPointerCapture(e.pointerId)});
renderer.domElement.addEventListener('pointermove',e=>{if(e.pointerId!==lookPointer)return;const dx=e.clientX-lastLookX,dy=e.clientY-lastLookY;lastLookX=e.clientX;lastLookY=e.clientY;cameraYaw-=dx*(isTouch?.006:.004);cameraPitch=clamp(cameraPitch+dy*(isTouch?.004:.003),-.15,.66)});
renderer.domElement.addEventListener('pointerup',e=>{if(e.pointerId===lookPointer)lookPointer=null});
addEventListener('wheel',e=>{cameraPitch=clamp(cameraPitch+Math.sign(e.deltaY)*.035,-.12,.64)},{passive:true});

function showMessage(title,sub='',ms=1500){ui.msgTitle.textContent=title;ui.msgSub.textContent=sub;ui.msg.style.opacity='1';clearTimeout(showMessage.t);showMessage.t=setTimeout(()=>ui.msg.style.opacity='0',ms)}
function updateUI(time){ui.hp.style.width=`${player.hp}%`;ui.shards.textContent=`${player.shards}/8`;const s=Math.max(0,Math.floor(time));ui.timer.textContent=`${String(Math.floor(s/60)).padStart(2,'0')}:${String(s%60).padStart(2,'0')}`;ui.objective.textContent=player.shards>=8?'Портал открыт — доберись до вершины':'Собери 8 осколков и открой портал'}

let gameTime=0,started=false,respawnTimer=0;
const best=Number(localStorage.getItem('echoRuinsBest')||0);
if(best>0)showMessage('ECHO RUINS',`Лучшее время: ${Math.floor(best/60)}:${String(Math.floor(best%60)).padStart(2,'0')}`,2400);

function resetPlayer(){player.pos.copy(player.checkpoint);player.vel.set(0,0,0);player.hp=100;player.dead=false;player.invuln=1;player.dashTimer=0;playerVisual.root.visible=true;showMessage('ВОЗВРАТ К ЭХУ','Чекпоинт восстановлен',900)}
function damage(amount,source){if(player.invuln>0||player.dead||player.won)return;player.hp=clamp(player.hp-amount,0,100);player.invuln=.85;sfx.hit();burst(player.pos,0xff5577,12,5);const away=player.pos.clone().sub(source).setY(.2).normalize();player.vel.x+=away.x*7;player.vel.z+=away.z*7;player.vel.y=7;if(player.hp<=0){player.dead=true;playerVisual.root.visible=false;respawnTimer=1.15;showMessage('СИГНАЛ ПОТЕРЯН','Возврат к последнему чекпоинту…',1100)}}

function updatePlatforms(t,dt){for(const p of platforms){p.lastX=p.x;p.lastY=p.y;p.lastZ=p.z;if(p.motion){const v=Math.sin(t*p.motion.speed+p.z*.17)*p.motion.amp;if(p.motion.axis==='x')p.x=p.group.position.x=p.baseX+v;else if(p.motion.axis==='y')p.y=p.group.position.y=p.baseY+v;else p.z=p.group.position.z=p.baseZ+v}p.dx=p.x-p.lastX;p.dy=p.y-p.lastY;p.dz=p.z-p.lastZ}}

function resolveAxis(axis,delta){if(delta===0)return;player.pos[axis]+=delta;for(const p of platforms){const b=aabbFor(p);if(!overlaps(player.pos,PLAYER_HALF,b))continue;if(axis==='y'){if(delta<0){player.pos.y=b.maxY+PLAYER_HALF.y;player.vel.y=0;player.grounded=true;player.groundPlatform=p;player.jumps=0}else{player.pos.y=b.minY-PLAYER_HALF.y;player.vel.y=Math.min(0,player.vel.y)}}else if(axis==='x'){player.pos.x=delta>0?b.minX-PLAYER_HALF.x:b.maxX+PLAYER_HALF.x;player.vel.x=0}else{player.pos.z=delta>0?b.minZ-PLAYER_HALF.z:b.maxZ+PLAYER_HALF.z;player.vel.z=0}}}

function updatePlayer(dt){
  if(player.dead){respawnTimer-=dt;if(respawnTimer<=0)resetPlayer();return}
  if(player.won)return;
  if(!started&&(input.keys.size||Math.hypot(input.x,input.z)>.05||input.jumpPressed))started=true;
  player.invuln=Math.max(0,player.invuln-dt);player.dashCooldown=Math.max(0,player.dashCooldown-dt);player.attackTimer=Math.max(0,player.attackTimer-dt);
  if(player.groundPlatform){player.pos.x+=player.groundPlatform.dx;player.pos.y+=player.groundPlatform.dy;player.pos.z+=player.groundPlatform.dz}
  player.groundPlatform=null;player.grounded=false;
  const kx=(input.keys.has('KeyD')?1:0)-(input.keys.has('KeyA')?1:0),kz=(input.keys.has('KeyS')?1:0)-(input.keys.has('KeyW')?1:0);let ix=isTouch?input.x:kx,iz=isTouch?input.z:kz;const l=Math.hypot(ix,iz);if(l>1){ix/=l;iz/=l}
  const forward=new THREE.Vector3(-Math.sin(cameraYaw),0,-Math.cos(cameraYaw)),right=new THREE.Vector3(Math.cos(cameraYaw),0,-Math.sin(cameraYaw));const move=right.multiplyScalar(ix).add(forward.multiplyScalar(-iz));if(move.lengthSq()>.001)move.normalize();
  player.coyote=Math.max(0,player.coyote-dt);player.jumpBuffer=Math.max(0,player.jumpBuffer-dt);if(input.jumpPressed)player.jumpBuffer=.13;
  if(input.dashPressed&&player.dashCooldown<=0){player.dashTimer=.21;player.dashCooldown=.72;const dir=move.lengthSq()>.01?move.clone():new THREE.Vector3(-Math.sin(cameraYaw),0,-Math.cos(cameraYaw));player.vel.x=dir.x*18;player.vel.z=dir.z*18;player.vel.y=Math.max(player.vel.y,1.2);sfx.dash();burst(player.pos,0x66f4f1,9,3.5)}
  if(input.attackPressed&&player.attackTimer<=0){player.attackTimer=.24;sfx.attack();for(const e of enemies){if(!e.alive)continue;if(e.g.position.distanceTo(player.pos)<1.65){e.hp--;burst(e.g.position,0xffc96b,9,4);if(e.hp<=0){e.alive=false;e.g.visible=false;burst(e.g.position,0xff5a72,18,6)}}}}
  if(player.dashTimer>0){player.dashTimer-=dt;player.vel.y+=-4*dt}else{const accel=player.grounded?18:8.5,targetSpeed=player.grounded?6.6:6.2;player.vel.x=damp(player.vel.x,move.x*targetSpeed,accel,dt);player.vel.z=damp(player.vel.z,move.z*targetSpeed,accel,dt);player.vel.y+=-27*dt}
  if(player.jumpBuffer>0&&(player.grounded||player.coyote>0||player.jumps<1)){const first=player.grounded||player.coyote>0;player.vel.y=first?10.8:10.2;player.jumpBuffer=0;player.grounded=false;player.coyote=0;if(!first)player.jumps=1;else player.jumps=0;sfx.jump();burst(player.pos.clone().add(new THREE.Vector3(0,-.65,0)),0x66f4f1,7,2.5)}
  resolveAxis('y',player.vel.y*dt);if(player.grounded)player.coyote=.12;resolveAxis('x',player.vel.x*dt);resolveAxis('z',player.vel.z*dt);
  if(player.pos.y<-18){player.hp=0;player.dead=true;playerVisual.root.visible=false;respawnTimer=.9;showMessage('ПАДЕНИЕ В БЕЗДНУ','Восстанавливаю последнюю точку…',850)}
  const planar=Math.hypot(player.vel.x,player.vel.z);player.moveBlend=damp(player.moveBlend,clamp(planar/6,0,1),10,dt);if(planar>.35){const target=Math.atan2(-player.vel.x,-player.vel.z);let d=((target-playerVisual.root.rotation.y+Math.PI)%(Math.PI*2))-Math.PI;playerVisual.root.rotation.y+=d*Math.min(1,dt*12)}
  playerVisual.root.position.copy(player.pos);const cycle=performance.now()*.012*player.moveBlend;for(const lmb of playerVisual.limbs){lmb.arm.rotation.x=Math.sin(cycle)*.72*lmb.side*player.moveBlend;lmb.leg.rotation.x=-Math.sin(cycle)*.72*lmb.side*player.moveBlend}playerVisual.body.position.y=Math.abs(Math.sin(cycle*2))*.035*player.moveBlend;playerVisual.ring.rotation.z+=dt*(player.dashTimer>0?10:2.2);const flash=player.invuln>0&&Math.floor(player.invuln*18)%2===0;playerVisual.body.visible=!flash;
  input.jumpPressed=input.dashPressed=input.attackPressed=false;
}

function updateWorld(t,dt){
  for(const s of shards){if(s.collected)continue;s.g.rotation.y+=dt*1.6;s.g.children[1].rotation.z+=dt*.8;s.g.position.y+=Math.sin(t*2+s.phase)*.0015;if(s.g.position.distanceTo(player.pos)<1.05){s.collected=true;s.g.visible=false;player.shards++;sfx.pickup();burst(s.g.position,0xffc96b,14,4);if(player.shards===8){sfx.portal();showMessage('ПОРТАЛ ПРОБУЖДЁН','Доберись до вершины руин',2000)}}}
  for(const c of checkpoints){c.g.children[1].rotation.z+=dt*1.2;if(!c.active&&c.g.position.distanceTo(player.pos)<1.35){for(const other of checkpoints){other.active=false;other.g.children[1].material=mats.stone}c.active=true;c.g.children[1].material=mats.cyan;player.checkpoint.copy(c.spawn);sfx.checkpoint();showMessage('ЭХО СОХРАНЕНО','Новая точка возврата',1200)}}
  for(const e of enemies){if(!e.alive)continue;e.cooldown=Math.max(0,e.cooldown-dt);const v=Math.sin(t*.95+e.phase)*e.range;e.g.position.copy(e.origin);e.g.position[e.axis]+=v;e.g.position.y+=Math.sin(t*3+e.phase)*.08;e.g.rotation.y=Math.sin(t*.95+e.phase)>0?Math.PI/2:-Math.PI/2;if(e.g.position.distanceTo(player.pos)<1.0&&e.cooldown<=0){e.cooldown=1.0;damage(24,e.g.position)}}
  portalRing.rotation.z+=dt*.75;portalInner.material.opacity=player.shards>=8?.22+.08*Math.sin(t*4):.045;portalRing.material=player.shards>=8?mats.cyan:mats.pink;if(player.shards>=8&&portal.position.distanceTo(player.pos)<1.7&&!player.won){player.won=true;sfx.portal();const final=gameTime;if(!best||final<best)localStorage.setItem('echoRuinsBest',String(final));showMessage('РУИНЫ ПРОЙДЕНЫ',`Время ${Math.floor(final/60)}:${String(Math.floor(final%60)).padStart(2,'0')} · Echo restored`,100000);ui.objective.textContent='Испытание завершено'}
  for(let i=particles.length-1;i>=0;i--){const p=particles[i];p.life-=dt;p.v.y-=8*dt;p.m.position.addScaledVector(p.v,dt);p.m.scale.setScalar(clamp(p.life*2,0,1));if(p.life<=0){scene.remove(p.m);p.m.geometry.dispose();p.m.material.dispose();particles.splice(i,1)}}
}

const camTarget=new THREE.Vector3();
function updateCamera(dt){const focus=player.pos.clone().add(new THREE.Vector3(0,.65,0));const dist=isTouch?7.5:7.2;const cp=Math.cos(cameraPitch),offset=new THREE.Vector3(Math.sin(cameraYaw)*cp,Math.sin(cameraPitch),Math.cos(cameraYaw)*cp).multiplyScalar(dist);offset.y+=1.4;camTarget.copy(focus).add(offset);camera.position.lerp(camTarget,1-Math.exp(-7*dt));camera.lookAt(focus)}

let last=performance.now(),acc=0;const FIXED=1/60;
function frame(now){const raw=Math.min(.05,(now-last)/1000);last=now;acc+=raw;while(acc>=FIXED){if(started&&!player.dead&&!player.won)gameTime+=FIXED;updatePlatforms(gameTime,FIXED);updatePlayer(FIXED);updateWorld(gameTime,FIXED);physicsWorld.step();acc-=FIXED}updateCamera(raw);updateUI(gameTime);renderer.render(scene,camera);requestAnimationFrame(frame)}
requestAnimationFrame(frame);

addEventListener('resize',()=>{camera.aspect=innerWidth/innerHeight;camera.updateProjectionMatrix();renderer.setSize(innerWidth,innerHeight);renderer.setPixelRatio(Math.min(devicePixelRatio,isTouch?1.45:1.9))});
document.addEventListener('visibilitychange',()=>{last=performance.now();acc=0});
