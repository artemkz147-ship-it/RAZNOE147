import * as THREE from '../vendor/three.module.js';
import { computeCharacterPose, applyCharacterPose } from './character-rig.mjs';
import { ObjectPool } from './object-pool.mjs';
import { zoneForDistance } from './zones.mjs';

const LANES = [-2.15, 0, 2.15];
const PLAYER_Z = 2.25;
const WORLD_SPAN = 108;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const lerp = (a, b, t) => a + (b - a) * t;
const damp = (current, target, speed, dt) => lerp(current, target, 1 - Math.exp(-speed * dt));
const seeded = (i, salt = 0) => { const x = Math.sin((i + 1) * 9283.17 + salt * 413.91) * 43758.5453; return x - Math.floor(x); };

function mat(color, roughness = .55, metalness = .12, emissive = 0, emissiveIntensity = 0, transparent = false) {
  return new THREE.MeshStandardMaterial({ color, roughness, metalness, emissive, emissiveIntensity, transparent, opacity: 1 });
}
function mesh(parent, geometry, material, x = 0, y = 0, z = 0) {
  const m = new THREE.Mesh(geometry, material); m.position.set(x, y, z); parent.add(m); return m;
}
function mixColor(target, a, b, t) {
  const ca = new THREE.Color(a), cb = new THREE.Color(b); target.copy(ca).lerp(cb, t);
}

function createPlayer() {
  const root = new THREE.Group();
  const body = new THREE.Group(); root.add(body);
  const cyan = mat(0x38bdf8, .28, .36, 0x075b72, .75);
  const dark = mat(0x0c1426, .54, .48);
  const pink = mat(0xf472b6, .28, .34, 0x75144d, .9);
  const skin = mat(0xe8b58f, .68, .03);
  const shoe = mat(0xeaf8ff, .34, .22, 0x4bb6ff, .18);
  const pelvis = mesh(body, new THREE.BoxGeometry(.72, .38, .44), dark, 0, .88, 0);
  const spine = new THREE.Group(); spine.position.set(0, 1.0, 0); body.add(spine);
  const torso = mesh(spine, new THREE.BoxGeometry(.94, 1.02, .50), cyan, 0, .5, 0);
  mesh(spine, new THREE.BoxGeometry(.62, .09, .515), pink, 0, .57, .01);
  const neck = new THREE.Group(); neck.position.set(0, 1.06, 0); spine.add(neck);
  mesh(neck, new THREE.CylinderGeometry(.12, .13, .18, 10), skin, 0, .05, 0);
  const head = mesh(neck, new THREE.SphereGeometry(.31, 20, 14), skin, 0, .34, 0);
  head.scale.z = .92;
  mesh(neck, new THREE.BoxGeometry(.40, .13, .22), dark, 0, .57, -.03);
  mesh(spine, new THREE.BoxGeometry(.60, .76, .20), dark, 0, .48, .35);

  function arm(side) {
    const shoulder = new THREE.Group(); shoulder.position.set(side * .57, .91, 0); spine.add(shoulder);
    mesh(shoulder, new THREE.SphereGeometry(.14, 10, 8), cyan, 0, 0, 0);
    mesh(shoulder, new THREE.CylinderGeometry(.11, .135, .62, 10), cyan, 0, -.31, 0);
    const elbow = new THREE.Group(); elbow.position.y = -.62; shoulder.add(elbow);
    mesh(elbow, new THREE.SphereGeometry(.13, 10, 8), cyan, 0, 0, 0);
    mesh(elbow, new THREE.CylinderGeometry(.09, .11, .58, 10), skin, 0, -.29, 0);
    const wrist = new THREE.Group(); wrist.position.y = -.61; elbow.add(wrist);
    mesh(wrist, new THREE.SphereGeometry(.13, 12, 9), skin, 0, 0, 0);
    return { shoulder, elbow, wrist };
  }
  function leg(side) {
    const hip = new THREE.Group(); hip.position.set(side * .25, .78, 0); body.add(hip);
    mesh(hip, new THREE.CylinderGeometry(.15, .18, .73, 10), dark, 0, -.36, 0);
    const knee = new THREE.Group(); knee.position.y = -.72; hip.add(knee);
    mesh(knee, new THREE.SphereGeometry(.15, 10, 8), dark, 0, 0, 0);
    mesh(knee, new THREE.CylinderGeometry(.12, .14, .68, 10), dark, 0, -.34, 0);
    const ankle = new THREE.Group(); ankle.position.y = -.67; knee.add(ankle);
    const foot = mesh(ankle, new THREE.BoxGeometry(.34, .19, .56), shoe, 0, -.08, -.14); foot.rotation.x = .06;
    return { hip, knee, ankle };
  }
  const leftArm = arm(-1), rightArm = arm(1), leftLeg = leg(-1), rightLeg = leg(1);
  const shield = mesh(root, new THREE.SphereGeometry(1.36, 24, 18), new THREE.MeshBasicMaterial({ color: 0x66efff, transparent: true, opacity: .12, wireframe: true, depthWrite: false, blending: THREE.AdditiveBlending }), 0, 1.2, 0);
  shield.visible = false;
  const shadow = mesh(root, new THREE.CircleGeometry(.65, 24), new THREE.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: .34, depthWrite: false }), 0, .015, .03); shadow.rotation.x = -Math.PI / 2;
  return { root, body, spine, torso, pelvis, leftArm, rightArm, leftLeg, rightLeg, shield, shadow, laneX: 0 };
}

function createBarrier(kind) {
  const g = new THREE.Group(); const metal = mat(0x26334b, .48, .55); const warning = mat(0xffae27, .36, .25, 0xff5e00, .7);
  if (kind === 'high') { mesh(g,new THREE.BoxGeometry(.24,2,.42),metal,-.72,1,0); mesh(g,new THREE.BoxGeometry(.24,2,.42),metal,.72,1,0); mesh(g,new THREE.BoxGeometry(1.7,.42,.46),warning,0,2,0); }
  else if (kind === 'low') { mesh(g,new THREE.BoxGeometry(1.55,.62,.58),warning,0,.31,0); mesh(g,new THREE.BoxGeometry(1.62,.10,.63),metal,0,.63,0); }
  else { mesh(g,new THREE.BoxGeometry(1.55,1.56,.68),metal,0,.78,0); mesh(g,new THREE.BoxGeometry(1.3,.18,.71),warning,0,.98,0); }
  return g;
}
function createEnemy(variant='guard') {
  const g=new THREE.Group(); const shell=mat(variant==='charger'?0x381629:0x182033,.25,.72); const glow=mat(variant==='sweeper'?0xffb32e:0xff315f,.22,.35,variant==='sweeper'?0xff8b00:0xff174f,2.4);
  mesh(g,new THREE.SphereGeometry(.45,16,12),shell,0,1.16,0); mesh(g,new THREE.BoxGeometry(.68,.14,.5),shell,0,1.16,0); mesh(g,new THREE.SphereGeometry(.10,12,8),glow,0,1.2,-.42);
  const ring=mesh(g,new THREE.TorusGeometry(.54,.038,8,28),new THREE.MeshBasicMaterial({color:glow.color,transparent:true,opacity:.82}),0,1.16,0); ring.rotation.x=Math.PI/2; g.userData.ring=ring; return g;
}
function createPickup(type='coin', air=false) {
  const g=new THREE.Group(); const colors={coin:0xffd65c,shield:0x54e8ff,boost:0xa7ff5b,magnet:0xf472b6}; const color=colors[type]||colors.coin; const m=mat(color,.22,.45,color,1.5); const y=air?3.02:.88;
  if(type==='coin'){const r=mesh(g,new THREE.TorusGeometry(.31,.09,10,24),m,0,y,0);r.rotation.y=Math.PI/2;}
  else if(type==='shield') mesh(g,new THREE.IcosahedronGeometry(.39,1),m,0,y,0);
  else if(type==='boost'){const c=mesh(g,new THREE.ConeGeometry(.34,.78,8),m,0,y,0);c.rotation.z=Math.PI;}
  else {const r=mesh(g,new THREE.TorusGeometry(.35,.11,10,20,Math.PI*1.55),m,0,y,0);r.rotation.z=.8;}
  const halo=mesh(g,new THREE.SphereGeometry(.6,14,10),new THREE.MeshBasicMaterial({color,transparent:true,opacity:.09,depthWrite:false,blending:THREE.AdditiveBlending}),0,y,0); g.userData.halo=halo; return g;
}

function createParticles(scene) {
  const count=160, pos=new Float32Array(count*3), col=new Float32Array(count*3), life=new Float32Array(count), vel=new Float32Array(count*3); for(let i=0;i<count;i++)pos[i*3+1]=-100;
  const geo=new THREE.BufferGeometry(); geo.setAttribute('position',new THREE.BufferAttribute(pos,3)); geo.setAttribute('color',new THREE.BufferAttribute(col,3));
  const material=new THREE.PointsMaterial({size:.11,vertexColors:true,transparent:true,opacity:.92,depthWrite:false,blending:THREE.AdditiveBlending}); const points=new THREE.Points(geo,material); scene.add(points); let cursor=0; const c=new THREE.Color();
  return {
    burst(x,y,z,color,amount=18,force=1){c.set(color);for(let n=0;n<amount;n++){const i=cursor++%count,p=i*3;pos[p]=x;pos[p+1]=y;pos[p+2]=z;col[p]=c.r;col[p+1]=c.g;col[p+2]=c.b;life[i]=.35+Math.random()*.42;vel[p]=(Math.random()-.5)*3.5*force;vel[p+1]=(.5+Math.random()*2.6)*force;vel[p+2]=(Math.random()-.5)*3*force;}geo.attributes.position.needsUpdate=true;geo.attributes.color.needsUpdate=true;},
    update(dt){for(let i=0;i<count;i++){if(life[i]<=0)continue;const p=i*3;life[i]-=dt;if(life[i]<=0){pos[p+1]=-100;continue;}pos[p]+=vel[p]*dt;pos[p+1]+=vel[p+1]*dt;pos[p+2]+=vel[p+2]*dt;vel[p+1]-=4.2*dt;vel[p]*=.985;vel[p+2]*=.985;}geo.attributes.position.needsUpdate=true;},
    setQuality(level){material.size=level==='low'?.085:.11;}
  };
}

function createZoneLayer(scene, kind, count=24) {
  let geometry, material, sideScale=1;
  if(kind==='neon-city'){geometry=new THREE.BoxGeometry(1,1,1);material=mat(0x122443,.66,.24,0x173e66,.45,true);sideScale=1.2;}
  else if(kind==='industrial'){geometry=new THREE.CylinderGeometry(.22,.28,3.2,8);material=mat(0x5a4636,.58,.58,0x6d3212,.22,true);}
  else if(kind==='tunnel'){geometry=new THREE.BoxGeometry(.25,4.8,.32);material=mat(0x26324a,.5,.6,0x425d95,.25,true);}
  else if(kind==='rooftop'){geometry=new THREE.BoxGeometry(2.2,.45,1.4);material=mat(0x25324d,.62,.35,0xff2c9c,.12,true);}
  else {geometry=new THREE.BoxGeometry(1,1,1);material=mat(0x27183f,.45,.48,0x7d2fcf,.62,true);sideScale=1.35;}
  material.opacity=0;
  const inst=new THREE.InstancedMesh(geometry,material,count); inst.frustumCulled=false; scene.add(inst); const dummy=new THREE.Object3D();
  const bases=Array.from({length:count},(_,i)=>({depth:(i*4.31+seeded(i,2)*7)%WORLD_SPAN,side:i%2?-1:1,r:seeded(i,5),r2:seeded(i,8)}));
  function update(distance, weight, lowQuality=false){material.opacity=weight;inst.visible=weight>.015;const active=lowQuality?Math.ceil(count*.58):count;for(let i=0;i<count;i++){const b=bases[i];if(i>=active){dummy.scale.setScalar(0);dummy.updateMatrix();inst.setMatrixAt(i,dummy.matrix);continue;}let depth=(b.depth-distance)%WORLD_SPAN;if(depth<0)depth+=WORLD_SPAN;const z=PLAYER_Z+4-depth;let x=b.side*(6.1+b.r*6.2)*sideScale;let y=0,sx=1,sy=1,sz=1,rz=0;
      if(kind==='neon-city'||kind==='megacity'){sx=2+b.r*2.8;sy=(kind==='megacity'?7:4.8)+b.r2*(kind==='megacity'?12:8);sz=2.4+b.r*2.4;y=sy/2;}
      else if(kind==='industrial'){x=b.side*(5.6+b.r*3.8);y=1.6;rz=Math.PI/2*(i%3===0?1:0);}
      else if(kind==='tunnel'){x=b.side*4.72;y=2.35;sy=1+b.r*.15;}
      else if(kind==='rooftop'){x=b.side*(5.5+b.r*4.5);y=.25;sy=.8+b.r*.8;}
      dummy.position.set(x,y,z);dummy.scale.set(sx,sy,sz);dummy.rotation.set(0,b.r2*.08,rz);dummy.updateMatrix();inst.setMatrixAt(i,dummy.matrix);
    }inst.instanceMatrix.needsUpdate=true;}
  return {kind,inst,material,update};
}

export function createRenderer(canvas) {
  const renderer=new THREE.WebGLRenderer({canvas,antialias:true,alpha:false,powerPreference:'high-performance',precision:'mediump'});
  const maxRatio=Math.min(window.devicePixelRatio||1,1.45); renderer.setPixelRatio(maxRatio); renderer.outputColorSpace=THREE.SRGBColorSpace; renderer.toneMapping=THREE.ACESFilmicToneMapping; renderer.toneMappingExposure=1.18;
  const scene=new THREE.Scene(); scene.background=new THREE.Color(0x07101d); scene.fog=new THREE.FogExp2(0x07101d,.024);
  const camera=new THREE.PerspectiveCamera(61,1,.1,150); camera.position.set(0,3.15,8.2); const lookTarget=new THREE.Vector3(0,1.25,-8);
  const hemi=new THREE.HemisphereLight(0xa8d8ff,0x101020,1.45); scene.add(hemi); const key=new THREE.DirectionalLight(0xffffff,1.65); key.position.set(-4,8,5); scene.add(key); const neon=new THREE.PointLight(0x22d3ee,3.1,18,2); neon.position.set(0,2.3,3.4); scene.add(neon);
  const roadMat=mat(0x131a29,.82,.18); mesh(scene,new THREE.BoxGeometry(9.1,.18,116),roadMat,0,-.10,-48); const sidewalkMat=mat(0x20283a,.78,.14); mesh(scene,new THREE.BoxGeometry(1.6,.30,116),sidewalkMat,-5.2,0,-48); mesh(scene,new THREE.BoxGeometry(1.6,.30,116),sidewalkMat,5.2,0,-48);
  const laneMat=mat(0xd8f7ff,.42,.18,0x4cc9ff,.35); const markers=[]; for(let i=0;i<40;i++)for(const x of[-1.08,1.08]){const m=mesh(scene,new THREE.BoxGeometry(.075,.035,1.25),laneMat,x,.015,0);markers.push({mesh:m,base:i*3.1+(x>0?1.55:0)});}
  const edgeMat=mat(0x22d3ee,.28,.45,0x00c7ff,1.8); mesh(scene,new THREE.BoxGeometry(.08,.045,116),edgeMat,-4.47,.05,-48); mesh(scene,new THREE.BoxGeometry(.08,.045,116),edgeMat,4.47,.05,-48);
  const zoneLayers=['neon-city','industrial','tunnel','rooftop','megacity'].map((z)=>createZoneLayer(scene,z,z==='tunnel'?30:24));
  const traffic=[]; const carMat=mat(0x9d3cff,.26,.64,0x6b28c8,1.2); for(let i=0;i<10;i++){const g=new THREE.Group();mesh(g,new THREE.BoxGeometry(.75,.28,1.4),carMat,0,.25,0);mesh(g,new THREE.BoxGeometry(.5,.14,.7),mat(0x82efff,.25,.5,0x45cfff,1.4),0,.45,-.05);g.userData.base=seeded(i,31)*WORLD_SPAN;g.userData.side=i%2?-1:1;scene.add(g);traffic.push(g);}
  const player=createPlayer(); player.root.position.set(0,.04,PLAYER_Z); scene.add(player.root);
  const particles=createParticles(scene); const entityObjects=new Map(), pools=new Map();
  const speedMat=new THREE.MeshBasicMaterial({color:0xb8fbff,transparent:true,opacity:0,depthWrite:false,blending:THREE.AdditiveBlending}); const speedLines=[];for(let i=0;i<28;i++){const m=mesh(scene,new THREE.BoxGeometry(.025,.025,2.5+seeded(i,14)*4),speedMat);m.userData.base=seeded(i,15)*42;m.userData.x=(seeded(i,16)-.5)*8.3;m.userData.y=.3+seeded(i,17)*3.5;speedLines.push(m);}
  let lastTime=performance.now()/1000,shake=0,flash=0,fovKick=0,lastHealth=3,quality='high',fpsEma=60,slowTime=0,fastTime=0,currentZone='neon-city';

  function entityKey(e){if(e.type==='obstacle')return`obstacle:${e.obstacle||'solid'}`;if(e.type==='enemy')return`enemy:${e.variant||'guard'}`;if(e.type==='airPickup')return`pickup:${e.pickup||'coin'}:air`;return`pickup:${e.pickup||'coin'}:ground`;}
  function makePool(key){return new ObjectPool(()=>{const [type,a,b]=key.split(':');if(type==='obstacle')return createBarrier(a);if(type==='enemy')return createEnemy(a);return createPickup(a,b==='air');},(o,released)=>{o.visible=!released;o.position.set(0,0,0);o.rotation.set(0,0,0);},24);}
  function acquire(e){const key=entityKey(e);let pool=pools.get(key);if(!pool){pool=makePool(key);pools.set(key,pool);}const object=pool.acquire();scene.add(object);entityObjects.set(e.id,{object,key});return object;}
  function release(id,record){scene.remove(record.object);pools.get(record.key)?.release(record.object);entityObjects.delete(id);}

  function resize(){const w=Math.max(1,canvas.clientWidth||window.innerWidth),h=Math.max(1,canvas.clientHeight||window.innerHeight),r=renderer.getPixelRatio();if(canvas.width!==Math.floor(w*r)||canvas.height!==Math.floor(h*r)){renderer.setSize(w,h,false);camera.aspect=w/h;camera.updateProjectionMatrix();}}
  function updateZone(view){const zone=zoneForDistance(view.distance);currentZone=zone.name;const t=zone.progress;mixColor(scene.background,zone.style.bg,zone.nextStyle.bg,t);mixColor(scene.fog.color,zone.style.fog,zone.nextStyle.fog,t);scene.fog.density=lerp(zone.style.fogDensity,zone.nextStyle.fogDensity,t);mixColor(roadMat.color,zone.style.road,zone.nextStyle.road,t);mixColor(neon.color,zone.style.neon,zone.nextStyle.neon,t);mixColor(key.color,zone.style.key,zone.nextStyle.key,t);neon.intensity=3+Math.sin(view.elapsed*1.7)*.35;
    for(const layer of zoneLayers){let weight=0;if(layer.kind===zone.name)weight=1-t;if(layer.kind===zone.next)weight=Math.max(weight,t);layer.update(view.distance,weight,quality==='low');}
    const trafficVisible=['industrial','tunnel','megacity'].includes(zone.name)||t>.45;for(let i=0;i<traffic.length;i++){const car=traffic[i];car.visible=trafficVisible&&(quality==='high'||i%2===0);let depth=(car.userData.base-view.distance*(1.15+i%3*.08))%WORLD_SPAN;if(depth<0)depth+=WORLD_SPAN;car.position.set(car.userData.side*(5.0+(i%3)*.8),.18,PLAYER_Z+2-depth);car.rotation.y=car.userData.side>0?Math.PI:0;}
  }
  function updateWorld(view,dt){for(const marker of markers){let depth=(marker.base-view.distance)%62;if(depth<0)depth+=62;marker.mesh.position.z=PLAYER_Z+1.5-depth;}const boosting=view.effects.boostRemaining>0;speedMat.opacity=damp(speedMat.opacity,boosting?.56:0,8,dt);for(let i=0;i<speedLines.length;i++){const line=speedLines[i];line.visible=quality==='high'||i%2===0;let depth=(line.userData.base-view.distance*(boosting?1.65:1))%42;if(depth<0)depth+=42;line.position.set(line.userData.x,line.userData.y,PLAYER_Z+2-depth);}updateZone(view);}
  function updatePlayer(view,motion,dt,time){const p=view.player,targetX=LANES[p.lane+1]??0;player.laneX=damp(player.laneX,targetX,17,dt);player.root.position.x=player.laneX;player.root.position.y=.04+(p.y||0);const pose=computeCharacterPose(view,motion,time);applyCharacterPose(player,pose,damp,dt);player.shield.visible=view.effects.shieldRemaining>0;if(player.shield.visible){player.shield.rotation.y+=dt*1.4;player.shield.rotation.x+=dt*.4;}const shadowScale=clamp(1-(p.y||0)*.06,.60,1);player.shadow.scale.setScalar(shadowScale);player.shadow.material.opacity=clamp(.34-(p.y||0)*.05,.10,.34);if(pose.landing>.2)shake=Math.max(shake,.06+pose.landing*.08);}
  function syncEntities(view,time,dt){const alive=new Set();for(const e of view.entities){alive.add(e.id);let rec=entityObjects.get(e.id);const object=rec?.object||acquire(e);object.visible=true;object.position.x=damp(object.position.x,LANES[e.lane+1]??0,20,dt);object.position.z=PLAYER_Z-e.z;if(e.type==='enemy'){object.position.y=.02+Math.sin(time*5+e.z)*.10;object.rotation.y=Math.sin(time*2+e.z)*.12;if(object.userData.ring)object.userData.ring.rotation.z+=dt*(e.variant==='charger'?6:3.4);}else{object.position.y=0;if(e.type==='pickup'||e.type==='airPickup')object.rotation.y+=dt*2.8;if(object.userData.halo){const s=1+Math.sin(time*7+e.z)*.11;object.userData.halo.scale.setScalar(s);}}}for(const [id,record] of entityObjects)if(!alive.has(id))release(id,record);}
  function processEvents(view){for(const event of view.events||[]){if(event.type==='pickup'){const c=event.pickup==='boost'?0xa7ff5b:event.pickup==='shield'?0x54e8ff:event.pickup==='magnet'?0xf472b6:0xffd65c;particles.burst(player.root.position.x,1.2+view.player.y,PLAYER_Z-.4,c,quality==='low'?10:18,1);flash=Math.max(flash,.07);}else if(event.type==='enemy-hit'){particles.burst(player.root.position.x,1.3+view.player.y,PLAYER_Z-.7,0xff466e,quality==='low'?12:24,1.3);shake=Math.max(shake,.13);}else if(event.type==='near-miss'){particles.burst(player.root.position.x,1.0,PLAYER_Z-.9,0x8cf7ff,quality==='low'?8:16,.8);shake=Math.max(shake,.12);fovKick=Math.max(fovKick,2.5);}else if(event.type==='hit'){particles.burst(player.root.position.x,1.1+view.player.y,PLAYER_Z,0xff3b5c,quality==='low'?14:28,1.5);shake=Math.max(shake,.34);flash=Math.max(flash,.16);}else if(event.type==='shield-break'){particles.burst(player.root.position.x,1.2,PLAYER_Z,0x54e8ff,quality==='low'?16:32,1.55);shake=Math.max(shake,.20);}}if(view.health<lastHealth)shake=Math.max(shake,.28);lastHealth=view.health;}
  function updateCamera(view,motion,dt){const boosting=view.effects.boostRemaining>0;fovKick=Math.max(0,fovKick-dt*5);camera.fov=damp(camera.fov,(boosting?69:61)+fovKick,5.8,dt);camera.updateProjectionMatrix();shake=Math.max(0,shake-dt*1.8);flash=Math.max(0,flash-dt*1.55);const sx=shake?(Math.random()-.5)*shake:0,sy=shake?(Math.random()-.5)*shake*.55:0;camera.position.x=damp(camera.position.x,player.laneX*.12,7,dt)+sx;camera.position.y=damp(camera.position.y,3.15+Math.min(view.player.y*.09,.25),8,dt)+sy;camera.position.z=damp(camera.position.z,boosting?8.85:8.2,5,dt);lookTarget.x=damp(lookTarget.x,player.laneX*.08,7,dt);lookTarget.y=damp(lookTarget.y,1.25+Math.min(view.player.y*.06,.18),7,dt);camera.lookAt(lookTarget);const conf=clamp(Number(motion?.conf||0),0,1);camera.rotation.z=damp(camera.rotation.z,-clamp(Number(motion?.lean||0),-1,1)*.018*conf,8,dt);}
  function updateQuality(dt){const fps=1/Math.max(dt,1/240);fpsEma=fpsEma*.94+fps*.06;if(fpsEma<45){slowTime+=dt;fastTime=0;}else if(fpsEma>56){fastTime+=dt;slowTime=Math.max(0,slowTime-dt);}else{slowTime=Math.max(0,slowTime-dt*.5);fastTime=0;}if(quality==='high'&&slowTime>3){quality='low';renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,1));particles.setQuality('low');}else if(quality==='low'&&fastTime>7){quality='high';renderer.setPixelRatio(maxRatio);particles.setQuality('high');slowTime=0;fastTime=0;}}

  return {
    render(view,timeSeconds,motion=null){resize();const now=Number.isFinite(timeSeconds)?timeSeconds:performance.now()/1000;const dt=clamp(now-lastTime,1/120,.05);lastTime=now;updateQuality(dt);updateWorld(view,dt);updatePlayer(view,motion,dt,now);syncEntities(view,now,dt);processEvents(view);particles.update(dt);updateCamera(view,motion,dt);renderer.toneMappingExposure=1.18+flash*1.75;renderer.render(scene,camera);},
    getMetrics(){return{gameFps:fpsEma,quality,zone:currentZone,pools:[...pools.values()].reduce((n,p)=>n+p.created,0)};}
  };
}
