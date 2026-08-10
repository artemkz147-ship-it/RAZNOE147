import * as THREE from 'three';
import RAPIER from '@dimforge/rapier3d-compat';
import { NeonApexGame } from './base-v2.generated';
import './style-v3.css';

type TrackTheme = 'city' | 'harbor' | 'mountain';
type TrackSpec = {
  name: string;
  short: string;
  theme: TrackTheme;
  roadWidth: number;
  sky: number;
  fog: number;
  accent: number;
  accent2: number;
  points: number[][];
};

type CarSpec = {
  name: string;
  role: string;
  color: number;
  accent: number;
  top: number;
  nitroTop: number;
  accel: number;
  grip: number;
  durability: number;
};

type Debris = { mesh: THREE.Mesh; body: RAPIER.RigidBody };

const clamp = THREE.MathUtils.clamp;
const Y = new THREE.Vector3(0, 1, 0);

const TRACKS: TrackSpec[] = [
  {
    name: 'NEON DOWNTOWN', short: 'ЦЕНТР', theme: 'city', roadWidth: 15,
    sky: 0x07142b, fog: 0x07172e, accent: 0x45e8ff, accent2: 0xff3c98,
    points: [[-90,0,0],[-77,0,-58],[-35,0,-96],[18,0,-104],[72,0,-84],[106,0,-38],[108,0,18],[86,0,65],[42,0,96],[-12,0,103],[-65,0,79],[-101,0,39]]
  },
  {
    name: 'MIDNIGHT HARBOR', short: 'ПОРТ', theme: 'harbor', roadWidth: 16,
    sky: 0x071526, fog: 0x092238, accent: 0x38e7ff, accent2: 0xffb13c,
    points: [[-112,0,-14],[-83,0,-72],[-22,0,-91],[48,0,-86],[103,0,-49],[118,0,4],[95,0,55],[39,0,82],[-25,0,78],[-83,0,54],[-120,0,22]]
  },
  {
    name: 'AURORA RIDGE', short: 'ГОРЫ', theme: 'mountain', roadWidth: 13.5,
    sky: 0x071424, fog: 0x0a2130, accent: 0x5fffc8, accent2: 0x8b6dff,
    points: [[-94,0,4],[-78,0,-44],[-45,0,-70],[-7,0,-55],[24,0,-87],[61,0,-68],[83,0,-30],[60,0,-2],[91,0,29],[64,0,67],[23,0,53],[-9,0,87],[-52,0,69],[-78,0,38]]
  }
];

const CARS: CarSpec[] = [
  { name: 'APEX R', role: 'СБАЛАНСИРОВАННАЯ', color: 0x1b9cff, accent: 0x65f5ff, top: 53, nitroTop: 78, accel: 1.0, grip: 1.0, durability: 1.0 },
  { name: 'RAZOR X', role: 'МАКСИМАЛЬНАЯ СКОРОСТЬ', color: 0xff315f, accent: 0xffd04f, top: 59, nitroTop: 87, accel: 1.12, grip: .93, durability: .82 },
  { name: 'TITAN GT', role: 'ПРОЧНОСТЬ И СЦЕПЛЕНИЕ', color: 0x6d54ff, accent: 0x72ffc4, top: 50, nitroTop: 73, accel: .92, grip: 1.11, durability: 1.42 }
];

const readIndex = (key: string, max: number) => clamp(Number(localStorage.getItem(key) || 0) || 0, 0, max - 1);
const TRACK_INDEX = readIndex('neonApex.track', TRACKS.length);
const CAR_INDEX = readIndex('neonApex.car', CARS.length);
const trackSpec = TRACKS[TRACK_INDEX];
const carSpec = CARS[CAR_INDEX];

function createSelectionUI() {
  const card = document.querySelector('#menu .menu-card');
  const start = document.getElementById('startBtn');
  if (!card || !start) return;
  const panel = document.createElement('div');
  panel.className = 'selection-panel';
  panel.innerHTML = `
    <div class="selection-title"><span>ГАРАЖ И ТРАССА</span><b id="creditsChip">${Number(localStorage.getItem('neonApex.credits') || 0).toLocaleString('ru-RU')} CR</b></div>
    <div class="selection-grid">
      <button id="trackSelect" class="selector-card"><small>ТРАССА</small><strong>${trackSpec.name}</strong><span>${TRACK_INDEX + 1} / ${TRACKS.length} • нажми для смены</span></button>
      <button id="carSelect" class="selector-card car-card"><small>МАШИНА</small><strong>${carSpec.name}</strong><span>${carSpec.role}</span></button>
    </div>
    <div class="car-stats">
      <div><span>СКОРОСТЬ</span><i style="--v:${Math.round(carSpec.top / 59 * 100)}%"></i></div>
      <div><span>РАЗГОН</span><i style="--v:${Math.round(carSpec.accel / 1.12 * 100)}%"></i></div>
      <div><span>СЦЕПЛЕНИЕ</span><i style="--v:${Math.round(carSpec.grip / 1.11 * 100)}%"></i></div>
      <div><span>ПРОЧНОСТЬ</span><i style="--v:${Math.round(carSpec.durability / 1.42 * 100)}%"></i></div>
    </div>`;
  card.insertBefore(panel, start);

  document.getElementById('trackSelect')?.addEventListener('click', () => {
    localStorage.setItem('neonApex.track', String((TRACK_INDEX + 1) % TRACKS.length));
    location.reload();
  });
  document.getElementById('carSelect')?.addEventListener('click', () => {
    localStorage.setItem('neonApex.car', String((CAR_INDEX + 1) % CARS.length));
    location.reload();
  });

  const hud = document.getElementById('hud');
  if (hud) {
    const badge = document.createElement('div');
    badge.className = 'track-badge glass';
    badge.innerHTML = `<span>${trackSpec.short}</span><b>${carSpec.name}</b>`;
    hud.appendChild(badge);
  }

  const finishCard = document.querySelector('#finishScreen .menu-card');
  const again = document.getElementById('againBtn');
  if (finishCard && again) {
    const reward = document.createElement('div');
    reward.id = 'rewardLine';
    reward.className = 'reward-line';
    finishCard.insertBefore(reward, again);
  }
}
createSelectionUI();

class NeonApexV3 extends NeonApexGame {
  selectedTrack = trackSpec;
  selectedCar = carSpec;
  debris: Debris[] = [];

  buildWorld() {
    super.buildWorld();
    this.scene.background = new THREE.Color(this.selectedTrack.sky);
    this.scene.fog = new THREE.FogExp2(this.selectedTrack.fog, this.selectedTrack.theme === 'mountain' ? .0072 : .0052);

    const themeLight = new THREE.PointLight(this.selectedTrack.accent, 26, 115, 1.7);
    themeLight.position.set(0, 25, 0);
    this.scene.add(themeLight);

    const starsGeo = new THREE.BufferGeometry();
    const stars: number[] = [];
    for (let i = 0; i < 520; i++) {
      const a = i * 12.9898;
      const r = 110 + ((i * 37) % 190);
      stars.push(Math.sin(a) * r, 48 + ((i * 53) % 100), Math.cos(a * 1.31) * r);
    }
    starsGeo.setAttribute('position', new THREE.Float32BufferAttribute(stars, 3));
    this.scene.add(new THREE.Points(starsGeo, new THREE.PointsMaterial({ color: 0xbbe8ff, size: .55, transparent: true, opacity: .7 })));

    if (this.selectedTrack.theme === 'harbor') {
      const waterMat = new THREE.MeshStandardMaterial({ color: 0x08344d, roughness: .3, metalness: .4, emissive: 0x021b2e, emissiveIntensity: .75 });
      for (const [x,z,sx,sz] of [[-150,-120,120,70],[140,-80,110,90],[-145,110,95,80],[135,120,110,75]]) {
        const water = new THREE.Mesh(new THREE.PlaneGeometry(sx, sz), waterMat);
        water.rotation.x = -Math.PI/2; water.position.set(x,-.015,z); this.scene.add(water);
      }
    }
    if (this.selectedTrack.theme === 'mountain') {
      const terrain = new THREE.Mesh(new THREE.PlaneGeometry(430,430), new THREE.MeshStandardMaterial({ color:0x0c211b, roughness:.94, metalness:.02 }));
      terrain.rotation.x = -Math.PI/2; terrain.position.y = -.02; terrain.receiveShadow = true; this.scene.add(terrain);
    }
  }

  buildTrack() {
    this.roadWidth = this.selectedTrack.roadWidth;
    const pts = this.selectedTrack.points.map(p => new THREE.Vector3(p[0], p[1], p[2]));
    this.track = new THREE.CatmullRomCurve3(pts, true, 'catmullrom', .23);
    this.trackLength = this.track.getLength();
    const N = 420;
    this.trackSamples = Array.from({ length: N }, (_, i) => this.track.getPointAt(i / N));
    this.trackTangents = Array.from({ length: N }, (_, i) => this.track.getTangentAt(i / N).normalize());

    const verts: number[] = [], uvs: number[] = [], indices: number[] = [];
    for (let i = 0; i <= N; i++) {
      const idx = i % N, p = this.trackSamples[idx], t = this.trackTangents[idx], n = new THREE.Vector3(-t.z,0,t.x);
      const left = p.clone().addScaledVector(n, this.roadWidth*.5), right = p.clone().addScaledVector(n,-this.roadWidth*.5);
      verts.push(left.x,.055,left.z,right.x,.055,right.z); uvs.push(0,i/7,1,i/7);
      if (i < N) { const a=i*2,b=a+1,c=a+2,d=a+3; indices.push(a,b,c,b,d,c); }
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(verts,3)); geo.setAttribute('uv',new THREE.Float32BufferAttribute(uvs,2)); geo.setIndex(indices); geo.computeVertexNormals();
    const roadMat = new THREE.MeshStandardMaterial({ color: this.selectedTrack.theme === 'mountain' ? 0x252b2d : 0x202a34, roughness:.54, metalness:.26 });
    const road = new THREE.Mesh(geo,roadMat); road.receiveShadow=true; this.scene.add(road);

    const q=new THREE.Quaternion(), m=new THREE.Matrix4(), s=new THREE.Vector3(1,1,1);
    const markerGeo=new THREE.BoxGeometry(.18,.035,3.1), markerMat=new THREE.MeshStandardMaterial({color:0xf4fbff,emissive:this.selectedTrack.accent,emissiveIntensity:1.05});
    const markers=new THREE.InstancedMesh(markerGeo,markerMat,Math.ceil(N/4)); let mi=0;
    for(let i=0;i<N;i+=4){const p=this.trackSamples[i],t=this.trackTangents[i],yaw=Math.atan2(-t.x,-t.z);q.setFromAxisAngle(Y,yaw);m.compose(new THREE.Vector3(p.x,.09,p.z),q,s);markers.setMatrixAt(mi++,m);} markers.count=mi; this.scene.add(markers);

    const curbGeo=new THREE.BoxGeometry(1.5,.15,.58), curbMats=[this.selectedTrack.accent,this.selectedTrack.accent2].map(c=>new THREE.MeshStandardMaterial({color:c,emissive:c,emissiveIntensity:.32,roughness:.42}));
    for(let phase=0;phase<2;phase++){
      const curbs=new THREE.InstancedMesh(curbGeo,curbMats[phase],N);let ci=0;
      for(let i=phase*3;i<N;i+=6){const p=this.trackSamples[i],t=this.trackTangents[i],n=new THREE.Vector3(-t.z,0,t.x),yaw=Math.atan2(-t.x,-t.z);q.setFromAxisAngle(Y,yaw);for(const side of [-1,1]){const cp=p.clone().addScaledVector(n,side*(this.roadWidth*.5-.22));m.compose(new THREE.Vector3(cp.x,.13,cp.z),q,s);curbs.setMatrixAt(ci++,m);}}
      curbs.count=ci;this.scene.add(curbs);
    }

    const railGeo=new THREE.BoxGeometry(5.4,.82,.24), railMat=new THREE.MeshStandardMaterial({color:0x52647a,metalness:.82,roughness:.25});
    const rails=new THREE.InstancedMesh(railGeo,railMat,Math.ceil(N/5)*2);let ri=0;
    for(let i=0;i<N;i+=5){const p=this.trackSamples[i],t=this.trackTangents[i],n=new THREE.Vector3(-t.z,0,t.x),yaw=Math.atan2(-t.x,-t.z);q.setFromAxisAngle(Y,yaw);for(const side of [-1,1]){const rp=p.clone().addScaledVector(n,side*(this.roadWidth*.5+.75));m.compose(new THREE.Vector3(rp.x,.48,rp.z),q,s);rails.setMatrixAt(ri++,m);}}
    rails.count=ri;rails.castShadow=true;rails.receiveShadow=true;this.scene.add(rails);

    const edgeGeo=new THREE.BoxGeometry(.11,.025,2.3), edgeMat=new THREE.MeshBasicMaterial({color:this.selectedTrack.accent,transparent:true,opacity:.72});
    const edges=new THREE.InstancedMesh(edgeGeo,edgeMat,Math.ceil(N/6)*2);let ei=0;
    for(let i=0;i<N;i+=6){const p=this.trackSamples[i],t=this.trackTangents[i],n=new THREE.Vector3(-t.z,0,t.x),yaw=Math.atan2(-t.x,-t.z);q.setFromAxisAngle(Y,yaw);for(const side of [-1,1]){const ep=p.clone().addScaledVector(n,side*(this.roadWidth*.5-.85));m.compose(new THREE.Vector3(ep.x,.082,ep.z),q,s);edges.setMatrixAt(ei++,m);}}
    edges.count=ei;this.scene.add(edges);

    const start=this.trackSamples[0],tan=this.trackTangents[0],yaw=Math.atan2(-tan.x,-tan.z);
    const startLine=new THREE.Mesh(new THREE.BoxGeometry(this.roadWidth-1,.03,1.9),new THREE.MeshStandardMaterial({color:0xffffff,emissive:this.selectedTrack.accent,emissiveIntensity:.85}));
    startLine.position.set(start.x,.09,start.z);startLine.rotation.y=yaw;this.scene.add(startLine);
  }

  buildScenery() {
    if (this.selectedTrack.theme !== 'mountain') super.buildScenery();
    this.addRaceGates();
    if (this.selectedTrack.theme === 'city') this.buildDowntownDetails();
    else if (this.selectedTrack.theme === 'harbor') this.buildHarborDetails();
    else this.buildMountainDetails();
  }

  addRaceGates() {
    for(let i=0;i<12;i++){
      const t=(i+.45)/12,p=this.track.getPointAt(t),tan=this.track.getTangentAt(t).normalize(),yaw=Math.atan2(-tan.x,-tan.z),color=i%2?this.selectedTrack.accent:this.selectedTrack.accent2;
      const g=new THREE.Group(),mat=new THREE.MeshBasicMaterial({color});
      const top=new THREE.Mesh(new THREE.BoxGeometry(this.roadWidth+2,.18,.18),mat);top.position.y=5.8;g.add(top);
      for(const side of [-1,1]){const post=new THREE.Mesh(new THREE.BoxGeometry(.18,5.8,.18),mat);post.position.set(side*(this.roadWidth*.5+.9),2.9,0);g.add(post);}
      g.position.copy(p);g.rotation.y=yaw;this.scene.add(g);
    }
  }

  buildDowntownDetails() {
    const colors=[0x3ce7ff,0xff3a8b,0xffc13d,0x9564ff];
    for(let i=0;i<42;i++){
      const t=(.013+i*.0237)%1,p=this.track.getPointAt(t),tan=this.track.getTangentAt(t).normalize(),n=new THREE.Vector3(-tan.z,0,tan.x),side=i%2?1:-1,yaw=Math.atan2(-tan.x,-tan.z);
      const shop=new THREE.Group(),w=4+(i%4),h=2.8+(i%3)*.5,color=colors[i%colors.length];
      const shell=new THREE.Mesh(new THREE.BoxGeometry(w,h,3.4),new THREE.MeshStandardMaterial({color:0x172236,roughness:.58,metalness:.3}));shell.position.y=h*.5;shop.add(shell);
      const glass=new THREE.Mesh(new THREE.BoxGeometry(w*.82,h*.48,.05),new THREE.MeshBasicMaterial({color,transparent:true,opacity:.7}));glass.position.set(0,h*.48,-1.73);shop.add(glass);
      const awning=new THREE.Mesh(new THREE.BoxGeometry(w*.92,.12,.55),new THREE.MeshBasicMaterial({color}));awning.position.set(0,h*.72,-1.95);shop.add(awning);
      shop.position.copy(p).addScaledVector(n,side*(this.roadWidth*.5+7+(i%5)));shop.rotation.y=yaw+(side>0?Math.PI:0);this.scene.add(shop);
    }
    this.addOverpass(.19,0x4eeaff);
    this.addOverpass(.68,0xff3c98);
    for(let c=0;c<4;c++){
      const t=.1+c*.23,p=this.track.getPointAt(t),tan=this.track.getTangentAt(t).normalize(),n=new THREE.Vector3(-tan.z,0,tan.x),side=c%2?1:-1;
      const base=p.clone().addScaledVector(n,side*27);const crane=new THREE.Group(),mat=new THREE.MeshStandardMaterial({color:0xffb53b,metalness:.7,roughness:.35});
      const mast=new THREE.Mesh(new THREE.BoxGeometry(.7,18,.7),mat);mast.position.y=9;crane.add(mast);const arm=new THREE.Mesh(new THREE.BoxGeometry(15,.55,.55),mat);arm.position.set(5.5,17.5,0);crane.add(arm);crane.position.copy(base);this.scene.add(crane);
    }
    const t=.46,p=this.track.getPointAt(t),tan=this.track.getTangentAt(t).normalize(),n=new THREE.Vector3(-tan.z,0,tan.x);
    const park=new THREE.Group(),pm=new THREE.MeshStandardMaterial({color:0x263248,metalness:.45,roughness:.5});
    for(let y=0;y<4;y++){const deck=new THREE.Mesh(new THREE.BoxGeometry(18,.5,12),pm);deck.position.y=1.3+y*3;park.add(deck);}for(const x of [-8,8])for(const z of [-5,5]){const col=new THREE.Mesh(new THREE.BoxGeometry(.5,11,.5),pm);col.position.set(x,5.5,z);park.add(col);}park.position.copy(p).addScaledVector(n,-30);this.scene.add(park);
  }

  addOverpass(t:number,color:number){
    const p=this.track.getPointAt(t),tan=this.track.getTangentAt(t).normalize(),n=new THREE.Vector3(-tan.z,0,tan.x),yaw=Math.atan2(-tan.x,-tan.z),g=new THREE.Group();
    const deck=new THREE.Mesh(new THREE.BoxGeometry(this.roadWidth+18,.65,6),new THREE.MeshStandardMaterial({color:0x34445b,metalness:.58,roughness:.42}));deck.position.y=7;g.add(deck);
    const glow=new THREE.Mesh(new THREE.BoxGeometry(this.roadWidth+12,.12,6.15),new THREE.MeshBasicMaterial({color,transparent:true,opacity:.7}));glow.position.y=6.62;g.add(glow);
    for(const side of [-1,1]){const col=new THREE.Mesh(new THREE.BoxGeometry(1.2,7,1.2),new THREE.MeshStandardMaterial({color:0x27364b,roughness:.65}));col.position.set(side*(this.roadWidth*.5+5),3.5,0);g.add(col);}g.position.copy(p).addScaledVector(n,0);g.rotation.y=yaw;this.scene.add(g);
  }

  buildHarborDetails(){
    const containerColors=[0x1db1cb,0xe85c32,0xe5a930,0x475abf,0x35a269];
    for(let i=0;i<90;i++){
      const t=(.012+i*.0108)%1,p=this.track.getPointAt(t),tan=this.track.getTangentAt(t).normalize(),n=new THREE.Vector3(-tan.z,0,tan.x),side=i%2?1:-1,offset=this.roadWidth*.5+11+(i%5)*3;
      const cont=new THREE.Mesh(new THREE.BoxGeometry(5.8,2.4,2.45),new THREE.MeshStandardMaterial({color:containerColors[i%containerColors.length],metalness:.48,roughness:.55}));
      cont.position.copy(p).addScaledVector(n,side*offset);cont.position.y=1.22+((i%11===0)?2.45:0);cont.rotation.y=Math.atan2(-tan.x,-tan.z)+(i%3===0?Math.PI/2:0);cont.castShadow=true;this.scene.add(cont);
    }
    for(let i=0;i<7;i++){
      const t=.05+i*.135,p=this.track.getPointAt(t),tan=this.track.getTangentAt(t).normalize(),n=new THREE.Vector3(-tan.z,0,tan.x),side=i%2?1:-1,crane=new THREE.Group(),mat=new THREE.MeshStandardMaterial({color:i%2?0xffc33b:0x65dfff,metalness:.72,roughness:.32});
      for(const x of [-4,4]){const leg=new THREE.Mesh(new THREE.BoxGeometry(.8,17,.8),mat);leg.position.set(x,8.5,0);leg.rotation.z=x>0?-.18:.18;crane.add(leg);}const beam=new THREE.Mesh(new THREE.BoxGeometry(13,.8,.8),mat);beam.position.y=16.3;crane.add(beam);const arm=new THREE.Mesh(new THREE.BoxGeometry(19,.55,.55),mat);arm.position.set(5,18,0);crane.add(arm);crane.position.copy(p).addScaledVector(n,side*32);this.scene.add(crane);
    }
    for(let i=0;i<10;i++){
      const t=.03+i*.097,p=this.track.getPointAt(t),tan=this.track.getTangentAt(t).normalize(),n=new THREE.Vector3(-tan.z,0,tan.x),side=i%2?1:-1;
      const tank=new THREE.Mesh(new THREE.CylinderGeometry(3.1,3.1,7,18),new THREE.MeshStandardMaterial({color:0xa7b9c6,metalness:.7,roughness:.34}));tank.position.copy(p).addScaledVector(n,side*(28+(i%4)*5));tank.position.y=3.5;this.scene.add(tank);
    }
    this.addOverpass(.55,0xffb13c);
  }

  buildMountainDetails(){
    const trunkMat=new THREE.MeshStandardMaterial({color:0x5b3f2a,roughness:.9}),leafMats=[0x163d31,0x1b503c,0x244b44].map(c=>new THREE.MeshStandardMaterial({color:c,roughness:.9}));
    for(let i=0;i<220;i++){
      const t=(i/220+Math.sin(i*2.7)*.02+1)%1,p=this.track.getPointAt(t),tan=this.track.getTangentAt(t).normalize(),n=new THREE.Vector3(-tan.z,0,tan.x),side=i%2?1:-1,dist=this.roadWidth*.5+7+((i*17)%38);
      const tree=new THREE.Group(),trunk=new THREE.Mesh(new THREE.CylinderGeometry(.16,.25,2.6,7),trunkMat);trunk.position.y=1.3;tree.add(trunk);const crown=new THREE.Mesh(new THREE.ConeGeometry(1.25+(i%4)*.15,4.4+(i%3)*.5,8),leafMats[i%leafMats.length]);crown.position.y=4;tree.add(crown);tree.position.copy(p).addScaledVector(n,side*dist);tree.rotation.y=i*.73;this.scene.add(tree);
    }
    for(let i=0;i<100;i++){
      const t=(.004+i*.0097)%1,p=this.track.getPointAt(t),tan=this.track.getTangentAt(t).normalize(),n=new THREE.Vector3(-tan.z,0,tan.x),side=i%2?1:-1;
      const rock=new THREE.Mesh(new THREE.DodecahedronGeometry(1+(i%5)*.35,0),new THREE.MeshStandardMaterial({color:0x344346,roughness:.96}));rock.position.copy(p).addScaledVector(n,side*(this.roadWidth*.5+5+(i%7)*2.5));rock.position.y=.7;rock.scale.y=.65+(i%3)*.2;rock.rotation.set(i*.23,i*.61,i*.11);this.scene.add(rock);
    }
    this.addTunnel(.18);this.addTunnel(.72);
    for(let i=0;i<24;i++){
      const t=(i+.5)/24,p=this.track.getPointAt(t),tan=this.track.getTangentAt(t).normalize(),n=new THREE.Vector3(-tan.z,0,tan.x),lamp=new THREE.PointLight(i%2?0x65ffc4:0x806dff,7,20,2);lamp.position.copy(p).addScaledVector(n,(i%2?1:-1)*(this.roadWidth*.5+2.4));lamp.position.y=3.2;this.scene.add(lamp);
    }
  }

  addTunnel(t:number){
    const p=this.track.getPointAt(t),tan=this.track.getTangentAt(t).normalize(),yaw=Math.atan2(-tan.x,-tan.z),g=new THREE.Group(),mat=new THREE.MeshStandardMaterial({color:0x29373a,roughness:.82,metalness:.18});
    const roof=new THREE.Mesh(new THREE.BoxGeometry(this.roadWidth+6,1.4,16),mat);roof.position.y=6.8;g.add(roof);for(const side of [-1,1]){const wall=new THREE.Mesh(new THREE.BoxGeometry(2.2,6.5,16),mat);wall.position.set(side*(this.roadWidth*.5+1.8),3.2,0);g.add(wall);}const glow=new THREE.Mesh(new THREE.BoxGeometry(this.roadWidth,.12,15.6),new THREE.MeshBasicMaterial({color:this.selectedTrack.accent,transparent:true,opacity:.32}));glow.position.y=6;g.add(glow);g.position.copy(p);g.rotation.y=yaw;this.scene.add(g);
  }

  makeCar(color:number,accent:number){
    const g=super.makeCar(color,accent);
    const dark=new THREE.MeshStandardMaterial({color:0x07090d,metalness:.75,roughness:.2}),neon=new THREE.MeshBasicMaterial({color:accent});
    const grill=new THREE.Mesh(new THREE.BoxGeometry(1.35,.28,.08),dark);grill.position.set(0,.62,-2.18);g.add(grill);
    for(const x of [-.72,.72]){const lamp=new THREE.Mesh(new THREE.BoxGeometry(.5,.16,.07),new THREE.MeshBasicMaterial({color:0xd9fbff}));lamp.position.set(x,.78,-2.18);g.add(lamp);const mirror=new THREE.Mesh(new THREE.BoxGeometry(.22,.17,.42),dark);mirror.position.set(x>0?1.02:-1.02,1.18,-.15);g.add(mirror);}
    for(const x of [-.46,.46]){const vent=new THREE.Mesh(new THREE.BoxGeometry(.42,.035,.65),dark);vent.position.set(x,1.07,-1.28);vent.rotation.x=-.08;g.add(vent);}
    const frontBumper=new THREE.Mesh(new THREE.BoxGeometry(2.22,.18,.24),dark);frontBumper.position.set(0,.48,-2.17);g.add(frontBumper);
    const rearBumper=new THREE.Mesh(new THREE.BoxGeometry(2.16,.16,.25),dark);rearBumper.position.set(0,.48,2.17);g.add(rearBumper);
    const skirtL=new THREE.Mesh(new THREE.BoxGeometry(.12,.16,3.4),dark);skirtL.position.set(-1.08,.45,.05);g.add(skirtL);const skirtR=skirtL.clone();skirtR.position.x=1.08;g.add(skirtR);
    for(const x of [-.58,.58]){const pipe=new THREE.Mesh(new THREE.CylinderGeometry(.09,.09,.28,10),dark);pipe.rotation.x=Math.PI/2;pipe.position.set(x,.48,2.3);g.add(pipe);}
    for(const x of [-1.065,1.065])for(const z of [-1.34,1.32]){const rim=new THREE.Mesh(new THREE.CylinderGeometry(.23,.23,.335,12),neon);rim.rotation.z=Math.PI/2;rim.position.set(x,.48,z);g.add(rim);}
    const smoke=new THREE.Group();
    for(let i=0;i<6;i++){const puff=new THREE.Mesh(new THREE.SphereGeometry(.22+i*.025,7,5),new THREE.MeshBasicMaterial({color:0xaab4bd,transparent:true,opacity:.18,depthWrite:false}));smoke.add(puff);}smoke.visible=false;smoke.position.set(0,1.28,-.25);g.add(smoke);
    g.userData.frontBumper=frontBumper;g.userData.rearBumper=rearBumper;g.userData.smoke=smoke;
    return g;
  }

  setCarDamage(mesh:THREE.Group,damage:number){
    super.setCarDamage(mesh,damage);const d=clamp(damage,0,100)/100;
    const front=mesh.userData.frontBumper as THREE.Mesh|undefined,rear=mesh.userData.rearBumper as THREE.Mesh|undefined,smoke=mesh.userData.smoke as THREE.Group|undefined;
    if(front){front.position.z=-2.17+d*.18;front.rotation.y=d*.12;front.rotation.z=-d*.08;}
    if(rear){rear.position.z=2.17-d*.08;rear.rotation.z=d*.07;}
    if(smoke)smoke.visible=damage>42;
  }

  spawnPlayer(){
    this.playerMesh=this.makeCar(this.selectedCar.color,this.selectedCar.accent);this.scene.add(this.playerMesh);
    const p=this.trackSamples[0],t=this.trackTangents[0],yaw=Math.atan2(-t.x,-t.z);
    const desc=RAPIER.RigidBodyDesc.dynamic().setTranslation(p.x,.8,p.z).setRotation({x:0,y:Math.sin(yaw/2),z:0,w:Math.cos(yaw/2)}).setLinearDamping(.18).setAngularDamping(4).setCcdEnabled(true).setCanSleep(false);
    this.playerBody=this.world.createRigidBody(desc);this.world.createCollider(RAPIER.ColliderDesc.cuboid(1,.42,2).setDensity(120).setFriction(.75).setRestitution(.05),this.playerBody);
  }

  updatePlayer(dt:number){
    const input=this.input(),q0=this.playerBody.rotation(),q=new THREE.Quaternion(q0.x,q0.y,q0.z,q0.w),forward=new THREE.Vector3(0,0,-1).applyQuaternion(q).setY(0).normalize(),right=new THREE.Vector3(1,0,0).applyQuaternion(q).setY(0).normalize();
    const rv=this.playerBody.linvel(),vel=new THREE.Vector3(rv.x,rv.y,rv.z);let longitudinal=vel.dot(forward),lateral=vel.dot(right);const steer=(input.left?1:0)-(input.right?1:0),nitroOn=input.nitro&&this.nitro>.25;this.nitroActive=nitroOn;
    const damagePenalty=1-this.playerDamage*.0022,normalTop=this.selectedCar.top*damagePenalty,nitroTop=this.selectedCar.nitroTop*damagePenalty,top=nitroOn?nitroTop:normalTop;
    if(input.gas)longitudinal+=(longitudinal<18?27:17)*this.selectedCar.accel*dt;else longitudinal*=Math.pow(.982,dt*60);if(input.brake){if(longitudinal>2)longitudinal-=35*dt;else longitudinal-=13*dt;}
    longitudinal=clamp(longitudinal+(nitroOn?(longitudinal<16?40:31)*this.selectedCar.accel*dt:0),-12,top);this.nitro=clamp(this.nitro+(nitroOn?-23*dt:9.5*dt),0,100);
    const drift=input.brake&&Math.abs(longitudinal)>11,gripBase=clamp(.84-(this.selectedCar.grip-1)*.1,.76,.89),grip=drift?Math.pow(.982,dt*60):Math.pow(gripBase,dt*60);lateral*=grip;this.skid=THREE.MathUtils.lerp(this.skid,drift?1:0,1-Math.pow(.06,dt));
    const speedFactor=clamp(Math.abs(longitudinal)/32,.12,1.25),yawRate=steer*(.45+1.55*speedFactor)*(longitudinal>=0?1:-1)*(drift?1.24:1);this.playerBody.setAngvel({x:0,y:yawRate,z:0},true);
    const newVel=forward.multiplyScalar(longitudinal).add(right.multiplyScalar(lateral));newVel.y=rv.y;this.playerBody.setLinvel({x:newVel.x,y:newVel.y,z:newVel.z},true);
    const rotNow=this.playerBody.rotation(),e=new THREE.Euler().setFromQuaternion(new THREE.Quaternion(rotNow.x,rotNow.y,rotNow.z,rotNow.w),'YXZ'),yaw=e.y;this.playerBody.setRotation({x:0,y:Math.sin(yaw/2),z:0,w:Math.cos(yaw/2)},true);
    const p=this.playerBody.translation();if(p.y<-2)this.respawnAtNearest();this.updateProgress(longitudinal);
    const speedEl=document.getElementById('speed'),nitroText=document.getElementById('nitroText'),nitroBar=document.getElementById('nitroBar');if(speedEl)speedEl.textContent=String(Math.round(Math.max(0,longitudinal*3.6)));if(nitroText)nitroText.textContent=`${Math.round(this.nitro)}%`;if(nitroBar)nitroBar.style.transform=`scaleX(${this.nitro/100})`;
    const flames=this.playerMesh.userData.flames as THREE.Group|undefined;if(flames){flames.visible=nitroOn;this.nitroPulse+=dt*26;const pulse=.88+Math.sin(this.nitroPulse)*.22;flames.scale.set(1,pulse,1);}
  }

  checkImpacts(){
    const v=this.playerBody.linvel(),speedNow=Math.hypot(v.x,v.y,v.z),input=this.input(),drop=this.lastPlayerSpeed-speedNow,now=performance.now();
    if(!input.brake&&drop>7.2&&this.lastPlayerSpeed>13&&now-this.lastImpactAt>210){this.lastImpactAt=now;this.playerDamage=clamp(this.playerDamage+(drop*1.85)/this.selectedCar.durability,0,100);this.setCarDamage(this.playerMesh,this.playerDamage);this.camera.position.x+=(Math.random()-.5)*.55;this.camera.position.y+=Math.random()*.28;}
    const pp=this.playerBody.translation();for(const a of this.ai as any[]){const ap=a.body.translation(),dx=pp.x-ap.x,dz=pp.z-ap.z;if(dx*dx+dz*dz<8.4&&speedNow>10&&now-this.lastImpactAt>160){const hit=clamp(speedNow*.16,2,12);a.damage=clamp(a.damage+hit,0,100);this.setCarDamage(a.mesh,a.damage);}}
    this.lastPlayerSpeed=speedNow;const integrity=100-this.playerDamage,damageText=document.getElementById('damageText'),damageBar=document.getElementById('damageBar');if(damageText)damageText.textContent=`${Math.round(integrity)}%`;if(damageBar)damageBar.style.transform=`scaleX(${integrity/100})`;
  }

  resetDestructibles(){
    this.clearDebris();super.resetDestructibles();for(const d of (this as any).destructibles as any[]){d.mesh.visible=true;d.mesh.userData.fractured=false;}
  }

  clearDebris(){
    for(const d of this.debris){this.scene.remove(d.mesh);try{this.world.removeRigidBody(d.body);}catch{d.body.setTranslation({x:0,y:-30,z:0},false);}}this.debris=[];
  }

  fractureObject(d:any){
    if(d.mesh.userData.fractured)return;d.mesh.userData.fractured=true;d.mesh.visible=false;const p=d.body.translation(),v=d.body.linvel(),material=(d.mesh as THREE.Mesh).material as THREE.Material;
    d.body.setTranslation({x:p.x,y:-20,z:p.z},true);d.body.setLinvel({x:0,y:0,z:0},true);
    const pieces=d.kind==='crate'?7:5;
    for(let i=0;i<pieces;i++){
      const sx=.28+(i%3)*.11,sy=.22+((i+1)%3)*.12,sz=.3+((i+2)%3)*.1,mesh=new THREE.Mesh(new THREE.BoxGeometry(sx*2,sy*2,sz*2),material.clone());mesh.position.set(p.x+(Math.random()-.5)*.7,p.y+(Math.random()-.25)*.6,p.z+(Math.random()-.5)*.7);mesh.castShadow=true;this.scene.add(mesh);
      const body=this.world.createRigidBody(RAPIER.RigidBodyDesc.dynamic().setTranslation(mesh.position.x,mesh.position.y,mesh.position.z).setLinearDamping(.18).setAngularDamping(.22));this.world.createCollider(RAPIER.ColliderDesc.cuboid(sx,sy,sz).setDensity(12).setRestitution(.25).setFriction(.7),body);body.setLinvel({x:v.x+(Math.random()-.5)*8,y:3+Math.random()*6,z:v.z+(Math.random()-.5)*8},true);body.setAngvel({x:(Math.random()-.5)*10,y:(Math.random()-.5)*10,z:(Math.random()-.5)*10},true);this.debris.push({mesh,body});
    }
  }

  syncDestructibles(){
    super.syncDestructibles();for(const d of (this as any).destructibles as any[]){if(d.mesh.userData.fractured||d.kind==='bollard')continue;const v=d.body.linvel(),p=d.body.translation(),dx=p.x-d.initialPosition.x,dz=p.z-d.initialPosition.z;if(Math.hypot(v.x,v.z)>11||Math.hypot(dx,dz)>4.5)this.fractureObject(d);}
    for(const d of [...this.debris]){const p=d.body.translation(),q=d.body.rotation();d.mesh.position.set(p.x,p.y,p.z);d.mesh.quaternion.set(q.x,q.y,q.z,q.w);if(p.y<-4){this.scene.remove(d.mesh);try{this.world.removeRigidBody(d.body);}catch{}this.debris.splice(this.debris.indexOf(d),1);}}
  }

  syncPlayerMesh(){
    super.syncPlayerMesh();const smoke=this.playerMesh.userData.smoke as THREE.Group|undefined;if(smoke&&smoke.visible){const t=performance.now()*.001;smoke.children.forEach((o,i)=>{const m=o as THREE.Mesh,phase=t*1.3+i*.73;m.position.set(Math.sin(phase)*.18,.15+(i%3)*.24+Math.sin(phase*1.7)*.08,Math.cos(phase*.8)*.15);const mat=m.material as THREE.MeshBasicMaterial;mat.opacity=.11+this.playerDamage*.0012;});}
  }

  finishRace(){
    if(this.state==='finish')return;super.finishRace();const key=`neonApex.best.${TRACK_INDEX}`,old=Number(localStorage.getItem(key)||0);if(!old||this.raceSeconds<old)localStorage.setItem(key,String(this.raceSeconds));const reward=Math.max(150,(7-this.bestPosition)*220),credits=Number(localStorage.getItem('neonApex.credits')||0)+reward;localStorage.setItem('neonApex.credits',String(credits));const line=document.getElementById('rewardLine');if(line)line.innerHTML=`<span>НАГРАДА</span><b>+${reward} CR</b><small>${!old||this.raceSeconds<old?'НОВЫЙ РЕКОРД':'РЕКОРД '+this.formatTime(old)}</small>`;
  }
}

const game = new NeonApexV3();
game.init().catch(err => {
  console.error(err);
  const status=document.getElementById('bootStatus');
  if(status) status.textContent=`Ошибка запуска: ${err instanceof Error?err.message:String(err)}`;
});
