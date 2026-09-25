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
  const body = new THREE.Group();
  root.add(body);

  const suit = mat(0x16243a, .58, .34);
  const fabric = mat(0x263a55, .72, .16);
  const armor = mat(0x42c9e8, .24, .62, 0x0c6680, .58);
  const accent = mat(0xff4f9a, .26, .42, 0x8d184f, .95);
  const skin = mat(0xdba27e, .72, .02);
  const glove = mat(0x151b28, .48, .44);
  const boot = mat(0xe9f6ff, .30, .28, 0x42a8d8, .20);
  const sole = mat(0x101722, .72, .12);
  const visor = new THREE.MeshStandardMaterial({
    color: 0x67e8f9, roughness: .12, metalness: .46,
    emissive: 0x0a7184, emissiveIntensity: 1.1,
    transparent: true, opacity: .78
  });

  const round = (parent, sx, sy, sz, material, x=0, y=0, z=0, segments=18) => {
    const m = mesh(parent, new THREE.SphereGeometry(.5, segments, Math.max(10, Math.floor(segments*.7))), material, x, y, z);
    m.scale.set(sx, sy, sz);
    return m;
  };
  const limb = (parent, radiusTop, radiusBottom, length, material, x=0, y=0, z=0) =>
    mesh(parent, new THREE.CylinderGeometry(radiusTop, radiusBottom, length, 14), material, x, y, z);

  const pelvis = round(body, .52, .31, .36, suit, 0, .87, 0);
  mesh(body, new THREE.BoxGeometry(.82, .16, .48), armor, 0, .93, -.02);

  const spine = new THREE.Group();
  spine.position.set(0, 1.02, 0);
  body.add(spine);

  const torso = round(spine, .64, .78, .36, fabric, 0, .48, 0);
  round(spine, .69, .38, .39, armor, 0, .66, -.015);
  mesh(spine, new THREE.BoxGeometry(.74, .10, .40), accent, 0, .47, -.34);
  mesh(spine, new THREE.BoxGeometry(.48, .66, .10), suit, 0, .47, .34);

  const neck = new THREE.Group();
  neck.position.set(0, 1.05, 0);
  spine.add(neck);
  limb(neck, .105, .12, .19, skin, 0, .06, 0);

  const head = round(neck, .34, .40, .31, skin, 0, .37, 0, 22);
  round(neck, .355, .19, .325, suit, 0, .54, .005, 20);
  const face = round(neck, .285, .18, .275, visor, 0, .39, -.245, 20);
  face.scale.z = .34;
  mesh(neck, new THREE.BoxGeometry(.18, .055, .08), accent, 0, .60, -.27);

  function arm(side) {
    const shoulder = new THREE.Group();
    shoulder.position.set(side * .60, .90, 0);
    spine.add(shoulder);

    round(shoulder, .18, .18, .18, armor, 0, 0, 0, 14);
    limb(shoulder, .135, .115, .57, fabric, 0, -.29, 0);
    mesh(shoulder, new THREE.BoxGeometry(.15, .38, .10), armor, side*.06, -.25, -.10);

    const elbow = new THREE.Group();
    elbow.position.y = -.58;
    shoulder.add(elbow);
    round(elbow, .135, .135, .135, suit, 0, 0, 0, 12);
    limb(elbow, .105, .085, .53, skin, 0, -.27, 0);

    const wrist = new THREE.Group();
    wrist.position.y = -.55;
    elbow.add(wrist);
    limb(wrist, .11, .10, .14, glove, 0, -.07, 0);
    round(wrist, .15, .17, .13, glove, 0, -.19, -.01, 12);
    mesh(wrist, new THREE.BoxGeometry(.17, .055, .10), accent, 0, -.10, -.11);
    return { shoulder, elbow, wrist };
  }

  function leg(side) {
    const hip = new THREE.Group();
    hip.position.set(side * .26, .77, 0);
    body.add(hip);

    round(hip, .18, .18, .18, suit, 0, 0, 0, 12);
    limb(hip, .17, .145, .69, fabric, 0, -.35, 0);
    mesh(hip, new THREE.BoxGeometry(.18, .38, .12), armor, side*.045, -.28, -.11);

    const knee = new THREE.Group();
    knee.position.y = -.69;
    hip.add(knee);
    round(knee, .16, .145, .16, armor, 0, 0, -.03, 12);
    limb(knee, .13, .105, .64, suit, 0, -.32, 0);

    const ankle = new THREE.Group();
    ankle.position.y = -.63;
    knee.add(ankle);
    limb(ankle, .11, .10, .18, boot, 0, -.08, 0);
    const foot = round(ankle, .21, .13, .34, boot, 0, -.19, -.12, 14);
    foot.rotation.x = -.12;
    const footSole = mesh(ankle, new THREE.BoxGeometry(.39, .075, .61), sole, 0, -.29, -.15);
    footSole.rotation.x = -.03;
    return { hip, knee, ankle };
  }

  const leftArm = arm(-1), rightArm = arm(1), leftLeg = leg(-1), rightLeg = leg(1);

  const shield = mesh(root, new THREE.SphereGeometry(1.38, 28, 20), new THREE.MeshBasicMaterial({
    color: 0x66efff, transparent: true, opacity: .12, wireframe: true,
    depthWrite: false, blending: THREE.AdditiveBlending
  }), 0, 1.22, 0);
  shield.visible = false;

  const shadow = mesh(root, new THREE.CircleGeometry(.72, 30), new THREE.MeshBasicMaterial({
    color: 0x000000, transparent: true, opacity: .31, depthWrite: false
  }), 0, .015, .04);
  shadow.scale.set(1, .58, 1);
  shadow.rotation.x = -Math.PI / 2;

  return { root, body, spine, torso, pelvis, leftArm, rightArm, leftLeg, rightLeg, shield, shadow, laneX: 0 };
}

function createBarrier(kind) {
  const g = new THREE.Group();
  const steel = mat(0x252d3a, .42, .64);
  const dark = mat(0x10151d, .62, .34);
  const warning = mat(0xf59e0b, .34, .28, 0xff5b00, .7);
  const lamp = mat(0xff4d32, .18, .20, 0xff2a12, 2.8);
  const rubber = mat(0x080b10, .88, .06);

  if (kind === 'high') {
    // Low-clearance service gantry: crouch under it.
    mesh(g,new THREE.BoxGeometry(.20,1.82,.28),steel,-.78,.91,0);
    mesh(g,new THREE.BoxGeometry(.20,1.82,.28),steel,.78,.91,0);
    mesh(g,new THREE.BoxGeometry(1.82,.28,.34),steel,0,1.72,0);
    mesh(g,new THREE.BoxGeometry(1.48,.16,.37),warning,0,1.55,-.015);
    for(const x of[-.56,0,.56]) mesh(g,new THREE.SphereGeometry(.055,10,8),lamp,x,1.71,-.20);
  } else if (kind === 'low') {
    // Heavy road barrier: jump over it.
    mesh(g,new THREE.BoxGeometry(1.64,.48,.52),dark,0,.25,0);
    mesh(g,new THREE.BoxGeometry(1.72,.13,.57),warning,0,.54,0);
    for(const x of[-.62,.62]) {
      const foot=mesh(g,new THREE.BoxGeometry(.34,.12,.74),rubber,x,.06,.05);
      foot.rotation.y=x<0?.08:-.08;
      mesh(g,new THREE.SphereGeometry(.06,10,8),lamp,x,.57,-.31);
    }
  } else {
    // Dense construction crate / crash block.
    mesh(g,new THREE.BoxGeometry(1.48,1.42,.78),steel,0,.72,0);
    mesh(g,new THREE.BoxGeometry(1.34,.10,.82),warning,0,.28,-.02);
    mesh(g,new THREE.BoxGeometry(1.34,.10,.82),warning,0,1.08,-.02);
    mesh(g,new THREE.BoxGeometry(.10,1.18,.83),dark,-.56,.72,0);
    mesh(g,new THREE.BoxGeometry(.10,1.18,.83),dark,.56,.72,0);
    for(const x of[-.50,.50]) mesh(g,new THREE.SphereGeometry(.065,10,8),lamp,x,1.24,-.43);
  }
  return g;
}

function createCar(variant=0) {
  const g=new THREE.Group();
  const paint=mat(variant%2?0x34445e:0x5b243f,.24,.64,variant%2?0x10254b:0x441226,.18);
  const glass=mat(0x79cfea,.10,.52,0x1c6b8b,.42,true); glass.opacity=.72;
  const rubber=mat(0x090b0e,.9,.03);
  const chrome=mat(0xb7c4d6,.22,.78);
  const tail=mat(0xff3b2f,.16,.20,0xff1d12,3);
  mesh(g,new THREE.BoxGeometry(1.18,.34,2.18),paint,0,.32,0);
  const cabin=mesh(g,new THREE.BoxGeometry(.88,.35,1.02),paint,0,.61,-.18); cabin.rotation.x=-.025;
  mesh(g,new THREE.BoxGeometry(.77,.24,.04),glass,0,.66,-.70);
  mesh(g,new THREE.BoxGeometry(.68,.20,.04),glass,0,.66,.35);
  mesh(g,new THREE.BoxGeometry(1.03,.06,2.05),chrome,0,.15,0);
  for(const x of[-.56,.56]) for(const z of[-.70,.70]) {
    const w=mesh(g,new THREE.CylinderGeometry(.19,.19,.14,12),rubber,x,.16,z); w.rotation.z=Math.PI/2;
  }
  for(const x of[-.37,.37]) mesh(g,new THREE.BoxGeometry(.18,.09,.035),tail,x,.39,.96);
  return g;
}

function createTruck(variant=0) {
  const g=new THREE.Group();
  const body=mat(variant%2?0x3c4656:0x28364b,.52,.46);
  const cab=mat(variant%2?0x8b3d31:0x334d70,.34,.52);
  const glass=mat(0x83d9f5,.12,.48,0x176581,.34,true); glass.opacity=.72;
  const rubber=mat(0x080a0d,.9,.02);
  const light=mat(0xff4b32,.16,.2,0xff210f,2.8);
  mesh(g,new THREE.BoxGeometry(1.65,1.55,3.00),body,0,1.02,.58);
  mesh(g,new THREE.BoxGeometry(1.58,1.26,1.25),cab,0,.84,-1.56);
  mesh(g,new THREE.BoxGeometry(1.18,.40,.04),glass,0,1.13,-2.20);
  mesh(g,new THREE.BoxGeometry(1.34,.08,2.70),mat(0xa8b4c4,.3,.62),0,.27,.65);
  for(const x of[-.76,.76]) for(const z of[-1.45,.10,1.25]) {
    const w=mesh(g,new THREE.CylinderGeometry(.25,.25,.17,12),rubber,x,.25,z); w.rotation.z=Math.PI/2;
  }
  for(const x of[-.52,.52]) mesh(g,new THREE.BoxGeometry(.20,.11,.05),light,x,.57,2.11);
  return g;
}

function createTrainCar() {
  const g=new THREE.Group();
  const shell=mat(0x8a99aa,.26,.72);
  const band=mat(0x263b5e,.28,.58,0x244f86,.38);
  const glass=mat(0x83ddff,.08,.52,0x184e6b,.52,true); glass.opacity=.72;
  const light=mat(0xff4438,.16,.15,0xff2017,3);
  mesh(g,new THREE.BoxGeometry(2.02,2.05,6.6),shell,0,1.28,0);
  mesh(g,new THREE.BoxGeometry(2.06,.30,6.25),band,0,1.48,0);
  for(let i=0;i<6;i++){
    const z=-2.45+i*.98;
    mesh(g,new THREE.BoxGeometry(2.08,.54,.56),glass,0,1.73,z);
  }
  mesh(g,new THREE.BoxGeometry(1.78,.22,.12),band,0,.55,-3.35);
  for(const x of[-.52,.52]) mesh(g,new THREE.SphereGeometry(.08,10,8),light,x,.80,-3.38);
  return g;
}

function createTrackBed() {
  const g=new THREE.Group();
  const rail=mat(0xb0bbc9,.22,.82);
  const sleeper=mat(0x30343a,.76,.22);
  mesh(g,new THREE.BoxGeometry(.10,.10,116),rail,-.62,.02,-48);
  mesh(g,new THREE.BoxGeometry(.10,.10,116),rail,.62,.02,-48);
  for(let i=0;i<38;i++) mesh(g,new THREE.BoxGeometry(1.55,.10,.22),sleeper,0,-.02,6-i*3.05);
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

function createFacadeTower(seedIndex=0) {
  const g=new THREE.Group();
  const h=5.4+seeded(seedIndex,91)*7.5;
  const w=2.1+seeded(seedIndex,92)*2.4;
  const d=2.0+seeded(seedIndex,93)*2.7;
  const shell=mat(seedIndex%3===0?0x17243a:seedIndex%3===1?0x222936:0x202034,.52,.46);
  const trim=mat(0x39485c,.32,.64);
  const windowMat=mat(seedIndex%4===0?0x67d8ff:seedIndex%4===1?0xff8bc7:0x9bc7ff,.16,.28,
                      seedIndex%4===0?0x1688b8:seedIndex%4===1?0x8b205b:0x315d99,1.55,true);
  windowMat.opacity=.78;
  mesh(g,new THREE.BoxGeometry(w,h,d),shell,0,h*.5,0);
  mesh(g,new THREE.BoxGeometry(w+.08,.12,d+.08),trim,0,h-.06,0);
  const rows=Math.max(4,Math.floor(h/.78));
  for(let row=0;row<rows;row++){
    const y=.52+row*.72;
    if(y>h-.42)break;
    for(const side of[-1,1]){
      const pane=mesh(g,new THREE.BoxGeometry(.045,.28,d*.68),windowMat,side*(w*.505),y,0);
      pane.material=windowMat;
    }
  }
  if(seedIndex%3===0){
    const sign=mesh(g,new THREE.BoxGeometry(.06,1.35,.56),mat(0x65e8ff,.12,.22,0x1592b8,2.0,true),w*.515,h*.62,-d*.12);
    sign.material.opacity=.82;
  }
  return g;
}

function createStreetLight(side=1, warm=false) {
  const g=new THREE.Group();
  const steel=mat(0x2c3542,.34,.68);
  const glowColor=warm?0xffb45a:0x8ae7ff;
  const glow=mat(glowColor,.12,.18,glowColor,2.4,true); glow.opacity=.9;
  mesh(g,new THREE.CylinderGeometry(.055,.075,2.65,10),steel,0,1.32,0);
  const arm=mesh(g,new THREE.BoxGeometry(.58,.07,.08),steel,side*.26,2.58,0);
  arm.rotation.z=side>0?-.07:.07;
  mesh(g,new THREE.BoxGeometry(.30,.055,.16),glow,side*.54,2.51,0);
  mesh(g,new THREE.BoxGeometry(.18,.12,.22),steel,side*.49,2.56,0);
  return g;
}

function createIndustrialModule(seedIndex=0) {
  const g=new THREE.Group();
  const steel=mat(0x3d4652,.46,.62);
  const dark=mat(0x151a21,.66,.34);
  const hot=mat(0xff8737,.22,.28,0xff5417,1.5);
  if(seedIndex%3===0){
    for(const x of[-.42,.42]) mesh(g,new THREE.CylinderGeometry(.16,.18,2.5,10),steel,x,1.25,0);
    mesh(g,new THREE.BoxGeometry(1.22,.18,.62),dark,0,2.34,0);
    mesh(g,new THREE.BoxGeometry(.72,.10,.66),hot,0,1.60,-.02);
  } else if(seedIndex%3===1){
    mesh(g,new THREE.BoxGeometry(1.45,.38,1.10),dark,0,.20,0);
    const fan=mesh(g,new THREE.TorusGeometry(.47,.08,10,24),steel,0,.78,0);
    fan.rotation.x=Math.PI/2;
    for(let i=0;i<4;i++){
      const blade=mesh(g,new THREE.BoxGeometry(.12,.62,.05),steel,0,.78,0);
      blade.rotation.z=i*Math.PI/2;
    }
    mesh(g,new THREE.SphereGeometry(.11,10,8),hot,0,.78,-.16);
  } else {
    mesh(g,new THREE.BoxGeometry(1.65,.22,.52),steel,0,.22,0);
    mesh(g,new THREE.BoxGeometry(.20,2.2,.24),steel,-.65,1.18,0);
    mesh(g,new THREE.BoxGeometry(.20,2.2,.24),steel,.65,1.18,0);
    mesh(g,new THREE.BoxGeometry(1.52,.16,.30),hot,0,2.13,0);
  }
  return g;
}

function createRain(scene, count=180) {
  const positions=new Float32Array(count*6);
  const speeds=new Float32Array(count);
  const seeds=new Float32Array(count*3);
  for(let i=0;i<count;i++){
    seeds[i*3]=(seeded(i,121)-.5)*13;
    seeds[i*3+1]=1.2+seeded(i,122)*7.2;
    seeds[i*3+2]=-3-seeded(i,123)*34;
    speeds[i]=8+seeded(i,124)*10;
  }
  const geo=new THREE.BufferGeometry();
  geo.setAttribute('position',new THREE.BufferAttribute(positions,3));
  const material=new THREE.LineBasicMaterial({color:0x9fdcff,transparent:true,opacity:.23,depthWrite:false,blending:THREE.AdditiveBlending});
  const lines=new THREE.LineSegments(geo,material);
  lines.frustumCulled=false;
  scene.add(lines);
  return {
    update(time,dt,intensity=1,low=false){
      const active=low?Math.floor(count*.46):count;
      material.opacity=.05+.20*intensity;
      lines.visible=intensity>.03;
      for(let i=0;i<count;i++){
        const p=i*6;
        if(i>=active){positions[p+1]=positions[p+4]=-100;continue;}
        const sx=seeds[i*3], sy=seeds[i*3+1], sz=seeds[i*3+2];
        const fall=((time*speeds[i])%(sy+7));
        const y=sy-fall+1.2;
        const wind=Math.sin(time*.7+i*.19)*.10;
        positions[p]=sx+wind; positions[p+1]=y; positions[p+2]=sz;
        positions[p+3]=sx+wind-.08; positions[p+4]=y-.72; positions[p+5]=sz+.10;
      }
      geo.attributes.position.needsUpdate=true;
    }
  };
}

function createRoadPatches(scene) {
  const patches=[];
  const colors=[0x27c8ff,0xff4ea6,0xffa73c];
  for(let i=0;i<16;i++){
    const material=new THREE.MeshStandardMaterial({
      color:colors[i%colors.length],roughness:.10,metalness:.52,
      emissive:colors[i%colors.length],emissiveIntensity:.10,
      transparent:true,opacity:.10,depthWrite:false
    });
    const patch=mesh(scene,new THREE.PlaneGeometry(.8+seeded(i,131)*1.5,2.0+seeded(i,132)*3.4),material,0,.018,0);
    patch.rotation.x=-Math.PI/2;
    patch.rotation.z=(seeded(i,133)-.5)*.18;
    patch.userData.base=(i*7.2+seeded(i,134)*5)%WORLD_SPAN;
    patch.userData.x=(seeded(i,135)-.5)*6.6;
    patches.push(patch);
  }
  return patches;
}

function createNeonBillboard(seedIndex=0) {
  const g=new THREE.Group();
  const frame=mat(0x303946,.32,.72);
  const palette=[0x55dfff,0xff5aa7,0xffb34c,0x8f7dff];
  const color=palette[seedIndex%palette.length];
  const glow=mat(color,.12,.18,color,2.1,true); glow.opacity=.80;
  mesh(g,new THREE.BoxGeometry(1.95,1.12,.12),frame,0,0,0);
  mesh(g,new THREE.BoxGeometry(1.72,.88,.055),glow,0,0,-.09);
  const stripe=mesh(g,new THREE.BoxGeometry(1.20,.09,.065),frame,0,-.22,-.125);
  stripe.rotation.z=(seedIndex%2?.04:-.04);
  const bar=mesh(g,new THREE.BoxGeometry(.68,.07,.065),frame,-.30,.10,-.125);
  bar.rotation.z=.03;
  return g;
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
  const roadMat=mat(0x111722,.38,.34); mesh(scene,new THREE.BoxGeometry(9.1,.18,116),roadMat,0,-.10,-48);
  const roadSheen=new THREE.MeshStandardMaterial({color:0x172231,roughness:.16,metalness:.48,transparent:true,opacity:.24});
  mesh(scene,new THREE.BoxGeometry(8.88,.025,116),roadSheen,0,.005,-48);
  const sidewalkMat=mat(0x20283a,.68,.20); mesh(scene,new THREE.BoxGeometry(1.6,.30,116),sidewalkMat,-5.2,0,-48); mesh(scene,new THREE.BoxGeometry(1.6,.30,116),sidewalkMat,5.2,0,-48);
  const laneMat=mat(0xd8f7ff,.42,.18,0x4cc9ff,.35); const markers=[]; for(let i=0;i<40;i++)for(const x of[-1.08,1.08]){const m=mesh(scene,new THREE.BoxGeometry(.075,.035,1.25),laneMat,x,.015,0);markers.push({mesh:m,base:i*3.1+(x>0?1.55:0)});}
  const edgeMat=mat(0x22d3ee,.28,.45,0x00c7ff,1.8); mesh(scene,new THREE.BoxGeometry(.08,.045,116),edgeMat,-4.47,.05,-48); mesh(scene,new THREE.BoxGeometry(.08,.045,116),edgeMat,4.47,.05,-48);
  const zoneLayers=['neon-city','industrial','tunnel','rooftop','megacity'].map((z)=>createZoneLayer(scene,z,z==='tunnel'?30:24));
  const traffic=[];
  for(let i=0;i<12;i++){
    const kind=i%5===0?'truck':'car';
    const g=kind==='truck'?createTruck(i):createCar(i);
    g.userData.base=seeded(i,31)*WORLD_SPAN;
    g.userData.side=i%2?-1:1;
    g.userData.kind=kind;
    scene.add(g);
    traffic.push(g);
  }
  const railBeds=[];
  for(const side of[-1,1]){
    const bed=createTrackBed(); bed.position.x=side*7.15; scene.add(bed); railBeds.push(bed);
  }
  const trains=[];
  for(let i=0;i<3;i++){
    const train=createTrainCar();
    train.userData.base=(i*36+seeded(i,71)*18)%WORLD_SPAN;
    train.userData.side=i%2?-1:1;
    train.position.x=train.userData.side*7.15;
    scene.add(train); trains.push(train);
  }
  const gantries=[];
  for(let i=0;i<7;i++){
    const g=new THREE.Group();
    const steel=mat(0x253142,.48,.58);
    const light=mat(0x49c7ff,.18,.22,0x1684b8,1.8);
    mesh(g,new THREE.BoxGeometry(.18,3.1,.22),steel,-4.65,1.55,0);
    mesh(g,new THREE.BoxGeometry(.18,3.1,.22),steel,4.65,1.55,0);
    mesh(g,new THREE.BoxGeometry(9.5,.18,.22),steel,0,3.02,0);
    for(const x of[-3,-1,1,3]) mesh(g,new THREE.BoxGeometry(.42,.08,.08),light,x,2.88,-.13);
    g.userData.base=i*17.1;
    scene.add(g); gantries.push(g);
  }
  const facades=[];
  for(let i=0;i<18;i++){
    const g=createFacadeTower(i);
    g.userData.base=(i*7.4+seeded(i,101)*9)%WORLD_SPAN;
    g.userData.side=i%2?-1:1;
    g.userData.offset=8.0+seeded(i,102)*4.5;
    scene.add(g); facades.push(g);
  }
  const streetLights=[];
  for(let i=0;i<22;i++){
    const side=i%2?-1:1;
    const g=createStreetLight(-side,i%4===0);
    g.userData.base=(i*5.25)%WORLD_SPAN;
    g.userData.side=side;
    scene.add(g); streetLights.push(g);
  }
  const industrialModules=[];
  for(let i=0;i<12;i++){
    const g=createIndustrialModule(i);
    g.userData.base=(i*9.1+seeded(i,111)*8)%WORLD_SPAN;
    g.userData.side=i%2?-1:1;
    scene.add(g); industrialModules.push(g);
  }
  const billboards=[];
  for(let i=0;i<10;i++){
    const g=createNeonBillboard(i);
    g.userData.base=(i*11.4+seeded(i,141)*6)%WORLD_SPAN;
    g.userData.side=i%2?-1:1;
    g.userData.y=2.2+seeded(i,142)*3.5;
    scene.add(g); billboards.push(g);
  }
  const roadPatches=createRoadPatches(scene);
  const rain=createRain(scene,180);
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
    const trafficVisible=['neon-city','industrial','tunnel','megacity'].includes(zone.name)||t>.45;
    for(let i=0;i<traffic.length;i++){
      const vehicle=traffic[i];
      vehicle.visible=trafficVisible&&(quality==='high'||i%2===0);
      let depth=(vehicle.userData.base-view.distance*(1.05+i%3*.07))%WORLD_SPAN;if(depth<0)depth+=WORLD_SPAN;
      const laneOffset=vehicle.userData.kind==='truck'?6.15:5.35+(i%3)*.46;
      vehicle.position.set(vehicle.userData.side*laneOffset,.02,PLAYER_Z+3-depth);
      vehicle.rotation.y=vehicle.userData.side>0?Math.PI:0;
    }
    const railVisible=['neon-city','industrial','megacity'].includes(zone.name)||(['neon-city','industrial','megacity'].includes(zone.next)&&t>.35);
    for(const bed of railBeds) bed.visible=railVisible;
    for(let i=0;i<trains.length;i++){
      const train=trains[i]; train.visible=railVisible&&(quality==='high'||i!==2);
      let depth=(train.userData.base-view.distance*(1.34+i*.07))%WORLD_SPAN;if(depth<0)depth+=WORLD_SPAN;
      train.position.z=PLAYER_Z+5-depth;
      train.rotation.y=train.userData.side>0?Math.PI:0;
    }
  }
  function updateWorld(view,dt){
    for(const marker of markers){let depth=(marker.base-view.distance)%62;if(depth<0)depth+=62;marker.mesh.position.z=PLAYER_Z+1.5-depth;}
    updateZone(view);
    const urbanVisible=['neon-city','rooftop','megacity'].includes(currentZone);
    const industrialVisible=['industrial','tunnel'].includes(currentZone);
    const rainIntensity=currentZone==='tunnel'?.08:currentZone==='industrial'?.55:currentZone==='rooftop'?1:.78;
    rain.update(view.elapsed,dt,rainIntensity,quality==='low');
    for(let i=0;i<roadPatches.length;i++){
      const patch=roadPatches[i];
      let depth=(patch.userData.base-view.distance)%WORLD_SPAN;if(depth<0)depth+=WORLD_SPAN;
      patch.position.set(patch.userData.x,.018,PLAYER_Z+2.4-depth);
      patch.visible=currentZone!=='tunnel'&&(quality==='high'||i%2===0);
      patch.material.opacity=currentZone==='rooftop'?.16:.09;
    }
    for(let i=0;i<billboards.length;i++){
      const board=billboards[i];
      board.visible=urbanVisible&&(quality==='high'||i%2===0);
      let depth=(board.userData.base-view.distance*.97)%WORLD_SPAN;if(depth<0)depth+=WORLD_SPAN;
      board.position.set(board.userData.side*6.1,board.userData.y,PLAYER_Z+2.6-depth);
      board.rotation.y=board.userData.side>0?-Math.PI/2:Math.PI/2;
    }
    for(let i=0;i<facades.length;i++){
      const building=facades[i];
      building.visible=urbanVisible&&(quality==='high'||i%2===0);
      let depth=(building.userData.base-view.distance*.96)%WORLD_SPAN;if(depth<0)depth+=WORLD_SPAN;
      building.position.set(building.userData.side*building.userData.offset,0,PLAYER_Z+3-depth);
      building.rotation.y=building.userData.side>0?-.08:.08;
    }
    for(let i=0;i<streetLights.length;i++){
      const lamp=streetLights[i];
      lamp.visible=quality==='high'||i%2===0;
      let depth=(lamp.userData.base-view.distance)%WORLD_SPAN;if(depth<0)depth+=WORLD_SPAN;
      lamp.position.set(lamp.userData.side*4.82,0,PLAYER_Z+3-depth);
    }
    for(let i=0;i<industrialModules.length;i++){
      const module=industrialModules[i];
      module.visible=industrialVisible&&(quality==='high'||i%2===0);
      let depth=(module.userData.base-view.distance*.99)%WORLD_SPAN;if(depth<0)depth+=WORLD_SPAN;
      module.position.set(module.userData.side*(5.25+(i%3)*.58),0,PLAYER_Z+3-depth);
      module.rotation.y=module.userData.side>0?-.10:.10;
    }
    for(const gantry of gantries){let depth=(gantry.userData.base-view.distance)%119;if(depth<0)depth+=119;gantry.position.z=PLAYER_Z+4-depth;}
    const boosting=view.effects.boostRemaining>0;speedMat.opacity=damp(speedMat.opacity,boosting?.56:0,8,dt);
    for(let i=0;i<speedLines.length;i++){const line=speedLines[i];line.visible=quality==='high'||i%2===0;let depth=(line.userData.base-view.distance*(boosting?1.65:1))%42;if(depth<0)depth+=42;line.position.set(line.userData.x,line.userData.y,PLAYER_Z+2-depth);}
  }
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
