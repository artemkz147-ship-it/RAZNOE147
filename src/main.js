import './style.css';
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { clone as cloneSkinned } from 'three/addons/utils/SkeletonUtils.js';
import { YandexBridge } from './yandex.js';
import { AudioDirector } from './audio.js';
import {
  HEROES, WEAPONS, PASSIVES, MAPS, ENEMIES, BOSSES, MODES, META_UPGRADES,
  DAILY_QUESTS, CAREER_QUESTS, ASSET_URLS, HELD_PROFILES, metaCost, weaponById, passiveById
} from './content.js';

const $ = s => document.querySelector(s);
const $$ = s => [...document.querySelectorAll(s)];
const clamp = THREE.MathUtils.clamp;
const TAU = Math.PI * 2;
const qs = new URLSearchParams(location.search);
const AUTO_START = qs.get('autostart') === '1';
const DEBUG_FAST = qs.get('debug-fast') === '1';
const DEBUG_MAP = qs.get('map');
const DEBUG_HERO = qs.get('hero');

const shuffle = a => {
  const b=[...a];
  for(let i=b.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[b[i],b[j]]=[b[j],b[i]];}
  return b;
};
const fmtTime = sec => `${String(Math.floor(sec/60)).padStart(2,'0')}:${String(Math.floor(sec%60)).padStart(2,'0')}`;
const todayKey = () => new Date().toISOString().slice(0,10);
const esc = s => String(s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':'&quot;',"'":"&#039;"}[c]));

class Input {
  constructor(){
    this.keys=new Set(); this.joy=new THREE.Vector2(); this.dashQueued=false; this.pauseQueued=false;
    addEventListener('keydown',e=>{
      if(['ArrowUp','ArrowDown','ArrowLeft','ArrowRight','Space'].includes(e.code))e.preventDefault();
      this.keys.add(e.code);
      if(e.code==='Space'&&!e.repeat)this.dashQueued=true;
      if(e.code==='Escape'&&!e.repeat)this.pauseQueued=true;
    },{passive:false});
    addEventListener('keyup',e=>this.keys.delete(e.code));
    this.setupJoystick();
  }
  setupJoystick(){
    const base=$('#joystick'),stick=$('#stick'); if(!base||!stick)return;
    let pid=null;
    const update=e=>{
      const r=base.getBoundingClientRect(),cx=r.left+r.width/2,cy=r.top+r.height/2,max=r.width*.30;
      let dx=e.clientX-cx,dy=e.clientY-cy,len=Math.hypot(dx,dy)||1;
      if(len>max){dx=dx/len*max;dy=dy/len*max;}
      this.joy.set(dx/max,-dy/max); stick.style.transform=`translate(${dx}px,${dy}px)`;
    };
    base.addEventListener('pointerdown',e=>{pid=e.pointerId;base.setPointerCapture(pid);update(e);});
    base.addEventListener('pointermove',e=>{if(e.pointerId===pid)update(e);});
    const end=e=>{if(e.pointerId!==pid)return;pid=null;this.joy.set(0,0);stick.style.transform='translate(0,0)';};
    base.addEventListener('pointerup',end); base.addEventListener('pointercancel',end);
    $('#dash-btn')?.addEventListener('pointerdown',e=>{e.preventDefault();this.dashQueued=true;});
  }
  vector(){
    const v=new THREE.Vector2(this.joy.x,this.joy.y);
    if(this.keys.has('KeyA')||this.keys.has('ArrowLeft'))v.x-=1;
    if(this.keys.has('KeyD')||this.keys.has('ArrowRight'))v.x+=1;
    if(this.keys.has('KeyW')||this.keys.has('ArrowUp'))v.y+=1;
    if(this.keys.has('KeyS')||this.keys.has('ArrowDown'))v.y-=1;
    if(v.lengthSq()>1)v.normalize(); return v;
  }
  consumeDash(){const q=this.dashQueued;this.dashQueued=false;return q;}
  consumePause(){const q=this.pauseQueued;this.pauseQueued=false;return q;}
}

class MerryMayhem3D {
  constructor(bridge){
    this.bridge=bridge; this.canvas=$('#game'); this.input=new Input();
    this.renderer=new THREE.WebGLRenderer({canvas:this.canvas,antialias:true,powerPreference:'high-performance'});
    this.renderer.outputColorSpace=THREE.SRGBColorSpace; this.renderer.toneMapping=THREE.ACESFilmicToneMapping; this.renderer.toneMappingExposure=1.16;
    this.scene=new THREE.Scene(); this.camera=new THREE.PerspectiveCamera(44,1,.1,320); this.camera.position.set(0,9.4,11.2);
    this.clock=new THREE.Clock(); this.loader=new GLTFLoader(); this.assets={}; this.audio=new AudioDirector();
    this.state='loading'; this.progress={}; this.settings={music:true,sfx:true,vibration:true,quality:'auto'};
    this.environment=null; this.menuPreview=null; this.player=null; this.boss=null;
    this.enemies=[]; this.projectiles=[]; this.enemyProjectiles=[]; this.pickups=[]; this.obstacles=[]; this.hazards=[]; this.effects=[];
    this.spawnClock=0;this.eliteClock=0;this.elapsed=0;this.kills=0;this.level=1;this.xp=0;this.nextXp=8;this.runCoins=0;this.runWon=false;
    this.selectedHero=HEROES[0].id;this.selectedMap=MAPS[0].id;this.selectedMode=MODES[0].id;
    this.lowPower=/Android|iPhone|iPad|iPod/i.test(navigator.userAgent)||matchMedia('(pointer:coarse)').matches||bridge.deviceType==='mobile';
    this.setupLights(); this.setupUI(); this.resize();
    addEventListener('resize',()=>this.resize()); addEventListener('contextmenu',e=>e.preventDefault());
    document.addEventListener('visibilitychange',()=>document.hidden?this.pauseExternal():this.resumeExternal());
    this.bridge.onPause=()=>this.pauseExternal();this.bridge.onResume=()=>this.resumeExternal();
    this.bridge.onAdOpen=()=>this.audio.suspend();this.bridge.onAdClose=()=>this.audio.resume();
  }

  setupLights(){
    this.hemi=new THREE.HemisphereLight(0xdaf4ff,0x31472e,1.12); this.scene.add(this.hemi);
    this.sun=new THREE.DirectionalLight(0xffe1ad,3.05); this.sun.position.set(-18,25,12); this.sun.castShadow=true;
    this.sun.shadow.mapSize.set(2048,2048); this.sun.shadow.camera.left=-38;this.sun.shadow.camera.right=38;this.sun.shadow.camera.top=38;this.sun.shadow.camera.bottom=-38;this.sun.shadow.bias=-.00025; this.scene.add(this.sun);
    this.rim=new THREE.DirectionalLight(0x79bdff,1.08); this.rim.position.set(15,10,-12);this.scene.add(this.rim);
  }

  setupUI(){
    $('#start-btn').onclick=()=>this.startRun();
    $('#hero-btn').onclick=()=>this.openOverlay('hero-panel',()=>this.renderHeroes());
    $('#map-btn').onclick=()=>this.openOverlay('map-panel',()=>this.renderMaps());
    $('#mode-btn').onclick=()=>this.openOverlay('mode-panel',()=>this.renderModes());
    $('#meta-btn').onclick=()=>this.openOverlay('meta-panel',()=>this.renderMeta());
    $('#quests-btn').onclick=()=>this.openOverlay('quests-panel',()=>this.renderQuests());
    $('#collection-btn').onclick=()=>this.openOverlay('collection-panel',()=>this.renderCollection('heroes'));
    $('#settings-btn').onclick=()=>this.openSettings(); $('#pause-settings-btn').onclick=()=>this.openSettings();
    $$('[data-close]').forEach(b=>b.onclick=()=>this.closeOverlay(b.dataset.close));
    $('#collection-tabs')?.addEventListener('click',e=>{const b=e.target.closest('[data-tab]');if(!b)return;$$('#collection-tabs button').forEach(x=>x.classList.toggle('active',x===b));this.renderCollection(b.dataset.tab);});
    $('#setting-music').onclick=()=>this.toggleSetting('music'); $('#setting-sfx').onclick=()=>this.toggleSetting('sfx');
    $('#setting-vibration').onclick=()=>this.toggleSetting('vibration'); $('#setting-quality').onclick=()=>this.cycleQuality(); $('#fullscreen-btn').onclick=()=>this.bridge.requestFullscreen();
    $('#pause-btn').onclick=()=>this.pauseGame(); $('#resume-btn').onclick=()=>this.resumeGame(); $('#quit-btn').onclick=()=>this.leaveToMenu();
    $('#restart-btn').onclick=()=>this.restartFromOver(); $('#result-menu-btn').onclick=()=>this.leaveToMenu();
    $('#revive-btn').onclick=()=>this.tryRevive(); $('#double-btn').onclick=()=>this.tryDoubleCoins(); $('#reroll-btn').onclick=()=>this.rerollUpgrade();
    $('#chest-continue').onclick=()=>{this.closeOverlay('chest-panel');this.state='playing';this.bridge.startGameplay();};
    $('#review-btn').onclick=()=>this.requestReview();
  }

  normalizeProgress(raw={}){
    const baseMeta=Object.fromEntries(META_UPGRADES.map(x=>[x.id,0]));
    const firstUnlocked=HEROES.slice(0,3).map(h=>h.id);
    return {
      version:2,coins:Number(raw.coins)||0,wins:Number(raw.wins)||0,runs:Number(raw.runs)||0,bestTime:Number(raw.bestTime)||0,bestKills:Number(raw.bestKills)||0,
      selectedHero:raw.selectedHero||HEROES[0].id,selectedMap:raw.selectedMap||MAPS[0].id,selectedMode:raw.selectedMode||MODES[0].id,
      unlockedHeroes:[...new Set([HEROES[0].id,...firstUnlocked,...(raw.unlockedHeroes||[])])],
      mapWins:{...(raw.mapWins||{})},meta:{...baseMeta,...(raw.meta||{})},
      career:{kills:0,wins:0,bosses:0,mapWins:0,levels:0,survive:0,...(raw.career||{})},
      daily:{date:todayKey(),kills:0,levels:0,survive:0,claimed:{},...(raw.daily||{})},
      claimedCareer:{...(raw.claimedCareer||{})},settings:{music:true,sfx:true,vibration:true,quality:'auto',...(raw.settings||{})},
      tutorialSeen:Boolean(raw.tutorialSeen)
    };
  }

  async load(raw={}){
    this.progress=this.normalizeProgress(raw); if(this.progress.daily.date!==todayKey())this.progress.daily={date:todayKey(),kills:0,levels:0,survive:0,claimed:{}};
    this.settings={...this.progress.settings}; this.audio.setMusic(this.settings.music);this.audio.setSfx(this.settings.sfx);
    this.selectedHero=HEROES.some(h=>h.id===this.progress.selectedHero)?this.progress.selectedHero:HEROES[0].id;
    this.selectedMap=MAPS.some(m=>m.id===this.progress.selectedMap)?this.progress.selectedMap:MAPS[0].id;
    this.selectedMode=MODES.some(m=>m.id===this.progress.selectedMode)?this.progress.selectedMode:MODES[0].id;
    if(AUTO_START&&DEBUG_MAP&&MAPS.some(x=>x.id===DEBUG_MAP))this.selectedMap=DEBUG_MAP;
    if(AUTO_START&&DEBUG_HERO&&HEROES.some(x=>x.id===DEBUG_HERO))this.selectedHero=DEBUG_HERO;
    this.applyQuality();this.updateSettingsUI();this.updateMenu();
    const entries=Object.entries(ASSET_URLS); let done=0;
    for(const [key,url] of entries){
      try{this.assets[key]=await this.loader.loadAsync(url);}catch(err){console.error('asset load failed',key,url,err);throw err;}
      this.setLoading(++done/entries.length);
    }
    await this.buildMenuPreview();
    this.state='menu';document.body.dataset.gameReady='true';document.body.dataset.gameState='menu';
    $('#loading').classList.remove('screen--visible');$('#menu').classList.add('screen--visible');
    this.bridge.ready();this.checkReview();this.animate();
    if(AUTO_START)setTimeout(()=>this.startRun(),180);
  }

  setLoading(p){$('#loading-fill').style.width=`${Math.round(p*100)}%`;$('#loading-text').textContent=`${Math.round(p*100)}%`;}

  cloneVisual(gltf,targetHeight,{shadow=true,tint=null}={}){
    const model=cloneSkinned(gltf.scene);model.updateMatrixWorld(true);
    const box=new THREE.Box3().setFromObject(model),size=new THREE.Vector3();box.getSize(size);model.scale.multiplyScalar(targetHeight/Math.max(size.y,.001));model.updateMatrixWorld(true);
    const b=new THREE.Box3().setFromObject(model);model.position.y-=b.min.y;
    model.traverse(o=>{if(!o.isMesh)return;o.castShadow=shadow;o.receiveShadow=shadow;o.frustumCulled=true;if(o.material){o.material=Array.isArray(o.material)?o.material.map(m=>m.clone()):o.material.clone();const arr=Array.isArray(o.material)?o.material:[o.material];for(const m of arr){if(m.color){if(tint)m.color.lerp(new THREE.Color(tint),.18);m.color.offsetHSL(0,0,.035);}if('roughness'in m)m.roughness=Math.max(.48,m.roughness??.62);if('metalness'in m)m.metalness=Math.min(.28,m.metalness??0);if(tint&&m.emissive){m.emissive.copy(new THREE.Color(tint));m.emissive.multiplyScalar(.055);m.emissiveIntensity=.22;}}}});
    const root=new THREE.Group();root.add(model);root.userData.model=model;return root;
  }

  prepareGround(gltf,targetSize,tint){
    const model=cloneSkinned(gltf.scene);model.updateMatrixWorld(true);const box=new THREE.Box3().setFromObject(model),s=new THREE.Vector3();box.getSize(s);const groundK=targetSize/Math.max(s.x,s.z,.001);model.scale.set(groundK,.18,groundK);model.updateMatrixWorld(true);
    const b=new THREE.Box3().setFromObject(model);model.position.y-=b.max.y;
    model.traverse(o=>{if(!o.isMesh)return;o.receiveShadow=true;o.castShadow=false;if(o.material){o.material=Array.isArray(o.material)?o.material.map(m=>m.clone()):o.material.clone();const arr=Array.isArray(o.material)?o.material:[o.material];for(const m of arr){if(m.color)m.color.lerp(new THREE.Color(tint),.58);if('roughness'in m)m.roughness=.92;}}});
    const root=new THREE.Group();root.add(model);return root;
  }

  prepareAttachment(gltf,targetHeight,tint=null){
    const root=this.cloneVisual(gltf,targetHeight,{shadow:true,tint});const model=root.userData.model;const b=new THREE.Box3().setFromObject(model),c=new THREE.Vector3();b.getCenter(c);model.position.sub(c);return root;
  }

  findClip(gltf,tokens){const clips=gltf?.animations||[];for(const token of tokens){const found=clips.find(c=>c.name.toLowerCase().includes(token));if(found)return found;}return clips[0]||null;}
  playActor(actor,kind,loop=true,fade=.12){
    if(!actor?.mixer||!actor.gltf)return;
    const map={idle:['idle','standing'],run:['run','walk','jog'],attack:['shoot','rifle','attack','spell','throw','punch','hit'],death:['death','die','dead']};
    const clip=this.findClip(actor.gltf,map[kind]||[kind]);if(!clip)return;
    if(actor.action?._clip===clip&&actor.kind===kind)return;
    const next=actor.mixer.clipAction(clip);next.reset();next.enabled=true;next.setLoop(loop?THREE.LoopRepeat:THREE.LoopOnce,loop?Infinity:1);next.clampWhenFinished=!loop;next.fadeIn(fade).play();actor.action?.fadeOut(fade);actor.action=next;actor.kind=kind;
  }

  findRightHand(root){
    let best=null,fallback=null;root.traverse(o=>{if(!o.isBone)return;const n=o.name.toLowerCase();if(!fallback&&(n.includes('hand')||n.includes('palm')||n.includes('wrist')))fallback=o;if(!best&&(n.includes('right')||n.includes('.r')||n.includes('_r')||n.endsWith('r'))&&(n.includes('hand')||n.includes('palm')||n.includes('wrist')))best=o;});return best||fallback;
  }

  heldProfile(w){const p=HELD_PROFILES[w?.id]||[w?.model||w?.projectile||'gem',.40,'spell'];return{asset:p[0],height:p[1],attack:p[2],pos:p[3]||[.06,-.08,-.03],rot:p[4]||[0,.1,.08],grip:p[5]||'center'};}
  playWeaponAttack(a,w){if(!a?.mixer||!a.gltf)return;const k=this.heldProfile(w).attack,t=k==='throw'?['throw','attack','punch','shoot']:k==='shoot'?['shoot','rifle','attack','throw','punch']:['spell','cast','attack','punch','shoot'];const clip=this.findClip(a.gltf,t);if(!clip)return;const n=a.mixer.clipAction(clip);n.reset();n.enabled=true;n.setLoop(THREE.LoopOnce,1);n.clampWhenFinished=true;n.fadeIn(.03).play();a.action?.fadeOut(.03);a.action=n;a.kind='attack';}
  prepareHeldWeapon(gltf,targetHeight,grip='center'){const root=this.cloneVisual(gltf,targetHeight,{shadow:true});const model=root.userData.model;model.updateMatrixWorld(true);const b=new THREE.Box3().setFromObject(model),c=new THREE.Vector3();b.getCenter(c);if(grip==='low')c.y=b.min.y+(b.max.y-b.min.y)*.18;else if(grip==='high')c.y=b.min.y+(b.max.y-b.min.y)*.78;model.position.sub(c);return root;}
  attachHeldWeapon(actor,w){actor.root.userData.weapon?.removeFromParent();const p=this.heldProfile(w),src=this.assets[p.asset]||this.assets[w.model]||this.assets[w.projectile]||this.assets.gem,weapon=this.prepareHeldWeapon(src,p.height,p.grip),hand=this.findRightHand(actor.root);if(hand){hand.add(weapon);weapon.position.fromArray(p.pos);weapon.rotation.set(p.rot[0],p.rot[1],p.rot[2]);}else{actor.root.add(weapon);weapon.position.set(.25,1,.10);}actor.root.userData.weapon=weapon;actor.root.userData.weaponHand=hand||null;return weapon;}
  projectileOrigin(a){const v=new THREE.Vector3(),w=a?.root?.userData?.weapon,h=a?.root?.userData?.weaponHand;if(w){w.updateWorldMatrix(true,false);w.getWorldPosition(v);v.y=Math.max(.72,v.y);return v;}if(h){h.updateWorldMatrix(true,false);h.getWorldPosition(v);v.y=Math.max(.72,v.y);return v;}v.copy(a.root.position);v.y=.95;return v;}


  async buildMenuPreview(){
    if(this.menuPreview)this.menuPreview.removeFromParent();
    const hero=this.currentHero();const root=this.cloneVisual(this.assets[hero.asset],2.65);root.position.set(-3.7,0,-3);root.rotation.y=.35;this.scene.add(root);
    const actor={root,gltf:this.assets[hero.asset],mixer:new THREE.AnimationMixer(root),action:null,kind:''};this.playActor(actor,'idle',true,.05);this.attachHeldWeapon(actor,weaponById(hero.weapon));this.menuPreview=actor;
    this.scene.background=new THREE.Color(0x172b34);this.scene.fog=new THREE.FogExp2(0x172b34,.018);this.camera.position.set(0,8.4,12.8);this.camera.lookAt(-2,1,-2);
  }

  heroPortrait(h){const n=String(Number(h.asset.replace(/\D/g,''))).padStart(2,'0');return `./portraits/hero-${n}.png`;}
  weaponPortrait(w){const i=Math.max(1,WEAPONS.findIndex(x=>x.id===w.id)+1);return `./portraits/weapon-${String(i).padStart(2,'0')}.png`;}
  currentHero(){return HEROES.find(h=>h.id===this.selectedHero)||HEROES[0];}
  currentMap(){return MAPS.find(m=>m.id===this.selectedMap)||MAPS[0];}
  currentMode(){return MODES.find(m=>m.id===this.selectedMode)||MODES[0];}

  updateMenu(){
    const h=this.currentHero(),m=this.currentMap(),mode=this.currentMode();
    $('#menu-hero-name').textContent=h.name;$('#menu-map-name').textContent=m.name;$('#menu-mode-name').textContent=mode.name;$('#menu-map-line').textContent=`${m.name} · ${h.name}`;
    $('#menu-coins').textContent=Math.floor(this.progress.coins||0);$('#menu-wins').textContent=this.progress.wins||0;$('#menu-best').textContent=fmtTime(this.progress.bestTime||0);
  }

  async selectHero(id){
    const h=HEROES.find(x=>x.id===id);if(!h)return;const unlocked=this.progress.unlockedHeroes.includes(id);
    if(!unlocked){if(this.progress.coins<h.cost){this.toast(`Нужно ${h.cost} монет`);return;}this.progress.coins-=h.cost;this.progress.unlockedHeroes.push(id);}
    this.selectedHero=id;this.progress.selectedHero=id;await this.save();this.updateMenu();this.renderHeroes();await this.buildMenuPreview();
  }
  async selectMap(id){const m=MAPS.find(x=>x.id===id);if(!m)return;if((this.progress.wins||0)<m.unlockWins){this.toast(`Откроется после ${m.unlockWins} побед`);return;}this.selectedMap=id;this.progress.selectedMap=id;await this.save();this.updateMenu();this.renderMaps();}
  async selectMode(id){const m=MODES.find(x=>x.id===id);if(!m)return;if((this.progress.wins||0)<m.unlockWins){this.toast(`Откроется после ${m.unlockWins} побед`);return;}this.selectedMode=id;this.progress.selectedMode=id;await this.save();this.updateMenu();this.renderModes();}

  renderHeroes(){
    $('#hero-coins').textContent=Math.floor(this.progress.coins);$('#hero-grid').innerHTML=HEROES.map(h=>{const unlocked=this.progress.unlockedHeroes.includes(h.id),sel=h.id===this.selectedHero,w=weaponById(h.weapon);return `<button class="hero-card ${sel?'selected':''} ${unlocked?'':'locked'}" data-hero="${h.id}"><div class="hero-art"><img src="${this.heroPortrait(h)}" alt=""><span>${esc(w.name)}</span></div><div class="hero-copy"><strong>${esc(h.name)}</strong><p>${esc(h.desc)}</p><b>${unlocked?(sel?'ВЫБРАН':'ВЫБРАТЬ'):`★ ${h.cost}`}</b></div></button>`;}).join('');
    $$('#hero-grid [data-hero]').forEach(b=>b.onclick=()=>this.selectHero(b.dataset.hero));
  }
  renderMaps(){
    $('#map-grid').innerHTML=MAPS.map(m=>{const unlocked=this.progress.wins>=m.unlockWins,sel=m.id===this.selectedMap;return `<button class="map-card ${sel?'selected':''} ${unlocked?'':'locked'}" data-map="${m.id}" style="--map:${m.ground};--sky:${m.sky}"><div class="map-thumb"><i></i><b></b></div><strong>${esc(m.name)}</strong><small>${esc(m.desc)}</small><span>${unlocked?(sel?'ВЫБРАНА':'ВЫБРАТЬ'):`Нужно побед: ${m.unlockWins}`}</span></button>`;}).join('');
    $$('#map-grid [data-map]').forEach(b=>b.onclick=()=>this.selectMap(b.dataset.map));
  }
  renderModes(){
    $('#mode-grid').innerHTML=MODES.map(m=>{const unlocked=this.progress.wins>=m.unlockWins,sel=m.id===this.selectedMode;return `<button class="mode-card ${sel?'selected':''} ${unlocked?'':'locked'}" data-mode="${m.id}"><strong>${esc(m.name)}</strong><span>${m.id==='endless'?'∞':fmtTime(m.seconds)}</span><small>Сложность ×${m.difficulty.toFixed(2)} · награда ×${m.reward}</small>${unlocked?'':`<em>Нужно побед: ${m.unlockWins}</em>`}</button>`;}).join('');
    $$('#mode-grid [data-mode]').forEach(b=>b.onclick=()=>this.selectMode(b.dataset.mode));
  }

  renderMeta(){
    $('#meta-coins').textContent=Math.floor(this.progress.coins);$('#meta-branches').innerHTML=META_UPGRADES.map(d=>{const rank=this.progress.meta[d.id]||0,cost=metaCost(d,rank),max=rank>=d.max;return `<button class="meta-card" data-meta="${d.id}" ${max?'disabled':''}><div><strong>${esc(d.name)}</strong><small>${esc(d.desc)}</small></div><div class="meta-ranks">${Array.from({length:d.max},(_,i)=>`<i class="${i<rank?'on':''}"></i>`).join('')}</div><span>${max?'МАКС.':`★ ${cost}`}</span></button>`;}).join('');
    $$('#meta-branches [data-meta]').forEach(b=>b.onclick=()=>this.buyMeta(b.dataset.meta));
  }
  async buyMeta(id){const d=META_UPGRADES.find(x=>x.id===id),r=this.progress.meta[id]||0;if(!d||r>=d.max)return;const c=metaCost(d,r);if(this.progress.coins<c){this.toast('Не хватает монет');return;}this.progress.coins-=c;this.progress.meta[id]=r+1;await this.save();this.renderMeta();this.updateMenu();}

  questValue(q){return q.id.startsWith('daily_')?(this.progress.daily[q.stat]||0):(this.progress.career[q.stat]||0);}
  questClaimed(q){return q.id.startsWith('daily_')?Boolean(this.progress.daily.claimed[q.id]):Boolean(this.progress.claimedCareer[q.id]);}
  renderQuests(){
    const render=(q,compact=false)=>{const v=Math.min(q.target,this.questValue(q)),done=v>=q.target,claimed=this.questClaimed(q);return `<button class="quest ${done?'done':''} ${claimed?'claimed':''}" data-quest="${q.id}" ${!done||claimed?'disabled':''}><div><strong>${esc(q.name)}</strong><small>${esc(q.text)}</small><div class="quest-bar"><i style="width:${v/q.target*100}%"></i></div></div><span>${claimed?'ПОЛУЧЕНО':done?`ЗАБРАТЬ ★${q.reward}`:`${Math.floor(v)} / ${q.target}`}</span></button>`;};
    $('#daily-quests').innerHTML=DAILY_QUESTS.map(q=>render(q)).join('');$('#career-quests').innerHTML=CAREER_QUESTS.map(q=>render(q,true)).join('');
    $$('.quest[data-quest]').forEach(b=>b.onclick=()=>this.claimQuest(b.dataset.quest));
  }
  async claimQuest(id){const q=[...DAILY_QUESTS,...CAREER_QUESTS].find(x=>x.id===id);if(!q||this.questValue(q)<q.target||this.questClaimed(q))return;this.progress.coins+=q.reward;if(id.startsWith('daily_'))this.progress.daily.claimed[id]=true;else this.progress.claimedCareer[id]=true;await this.save();this.renderQuests();this.updateMenu();this.toast(`+${q.reward} монет`);}

  renderCollection(tab){
    let items=[];
    if(tab==='heroes')items=HEROES.map(h=>({name:h.name,sub:weaponById(h.weapon).name,open:this.progress.unlockedHeroes.includes(h.id),art:this.heroPortrait(h)}));
    if(tab==='weapons')items=WEAPONS.map(w=>({name:w.name,sub:`Эволюция: ${w.evolution}`,open:true,art:this.weaponPortrait(w)}));
    if(tab==='maps')items=MAPS.map(m=>({name:m.name,sub:m.desc,open:this.progress.wins>=m.unlockWins}));
    if(tab==='enemies')items=ENEMIES.map(e=>({name:e.name,sub:`Поведение: ${e.behavior}`,open:true}));
    if(tab==='bosses')items=BOSSES.map((b,i)=>({name:b.name,sub:`Стиль: ${b.pattern}`,open:this.progress.wins>=Math.max(0,i-1)}));
    $('#collection-grid').innerHTML=items.map(x=>`<div class="collection-card ${x.open?'':'locked'}">${x.art&&x.open?`<img class="collection-art" src="${x.art}" alt="">`:`<div class="collection-icon">${x.open?'◆':'?'}</div>`}<strong>${x.open?esc(x.name):'???'}</strong><small>${x.open?esc(x.sub):'Ещё не открыто'}</small></div>`).join('');
  }

  openOverlay(id,cb){if(this.state==='playing')this.bridge.stopGameplay();$('#'+id)?.classList.add('screen--visible');cb?.();}
  closeOverlay(id){$('#'+id)?.classList.remove('screen--visible');if(this.state==='playing')this.bridge.startGameplay();}
  openSettings(){this.openOverlay('settings-panel',()=>this.updateSettingsUI());}
  updateSettingsUI(){$('#music-value').textContent=this.settings.music?'ON':'OFF';$('#sfx-value').textContent=this.settings.sfx?'ON':'OFF';$('#vibration-value').textContent=this.settings.vibration?'ON':'OFF';$('#quality-value').textContent={auto:'Авто',high:'Высокое',low:'Экономное'}[this.settings.quality]||'Авто';}
  async toggleSetting(k){this.settings[k]=!this.settings[k];if(k==='music')this.audio.setMusic(this.settings[k]);if(k==='sfx')this.audio.setSfx(this.settings[k]);this.progress.settings={...this.settings};this.updateSettingsUI();await this.save();}
  async cycleQuality(){this.settings.quality={auto:'high',high:'low',low:'auto'}[this.settings.quality]||'auto';this.progress.settings={...this.settings};this.applyQuality();this.updateSettingsUI();await this.save();}
  applyQuality(){const q=this.settings.quality==='auto'?(this.lowPower?'low':'high'):this.settings.quality;this.renderer.setPixelRatio(q==='high'?Math.min(devicePixelRatio,1.7):1);this.sun.castShadow=q==='high';this.renderer.shadowMap.enabled=q==='high';this.renderer.shadowMap.type=THREE.PCFSoftShadowMap;}

  async save(){await this.bridge.saveProgress(this.progress);}
  toast(text){const t=$('#toast');t.textContent=text;t.classList.add('show');clearTimeout(this.toastTimer);this.toastTimer=setTimeout(()=>t.classList.remove('show'),1800);}
  runToast(text){const t=$('#run-toast');t.textContent=text;t.classList.add('show');clearTimeout(this.runToastTimer);this.runToastTimer=setTimeout(()=>t.classList.remove('show'),1500);}

  clearWorld(){
    this.environment?.removeFromParent();this.environment=null;this.player?.root?.removeFromParent();this.player=null;this.boss=null;
    for(const list of [this.enemies,this.projectiles,this.enemyProjectiles,this.pickups,this.effects])for(const x of list)x.root?.removeFromParent?.();
    this.enemies=[];this.projectiles=[];this.enemyProjectiles=[];this.pickups=[];this.effects=[];this.obstacles=[];this.hazards=[];
  }

  buildEnvironment(map){
    this.environment=new THREE.Group();this.scene.add(this.environment);
    this.scene.background=new THREE.Color(map.sky);this.scene.fog=new THREE.FogExp2(map.sky,this.lowPower?.0085:.0068);this.hemi.color.set(map.sky);this.hemi.groundColor.set(map.ground);
    const groundSize=Math.max(map.width,map.height)+76,ground=this.prepareGround(this.assets.floor,groundSize,map.ground);ground.position.y=-.06;this.environment.add(ground);
    for(const [asset,x,z,height] of map.landmarks){const r=this.cloneVisual(this.assets[asset]||this.assets.rock,height,{tint:map.accent});r.position.set(x,0,z);r.rotation.y=(x*19+z*13)%TAU;this.environment.add(r);this.obstacles.push({x,z,radius:Math.max(.8,height*.24)});}
    this.buildMacroClusters(map);this.buildV3SetPieces(map);this.buildBoundary(map);
    if(map.hazard)this.buildHazard(map,map.hazard);
    this.populateMapDecor(map);
  }

  buildV3SetPieces(map){
    const sets={
      forest:[['bridge',-.18,.29,2.2,.1],['tent',.24,.31,2.0,-.5],['campfire',.18,.25,1.0,0],['wagon',-.34,-.24,2.3,.6],['fence',.34,-.24,1.4,1.5],['well',-.28,.05,1.8,0]],
      park:[['stall',-.30,-.24,2.3,.3],['bench',.27,-.20,1.2,1.3],['well',.29,.24,1.8,0],['sign',-.32,.20,1.4,.2],['fence',0,.34,1.4,0],['wagon',.06,-.33,2.1,-.2]],
      village:[['house',-.34,-.27,5.0,.25],['house',.34,-.23,4.7,-.4],['wagon',-.18,.31,2.4,.3],['well',.10,.28,2.0,0],['fence',.31,.17,1.5,1.4],['stall',-.31,.12,2.4,.2],['barrel',.20,-.03,1.0,0]],
      snow:[['house',-.32,-.27,4.7,.2],['tent',.29,.25,2.0,-.4],['campfire',.20,.18,1.0,0],['fence',-.30,.22,1.4,.2],['wagon',.31,-.12,2.3,1.2]],
      castle:[['house',-.35,-.25,4.6,.15],['house',.35,-.25,4.6,-.15],['fence',-.34,.18,1.7,1.5],['fence',.34,.18,1.7,1.5],['bench',-.18,.30,1.3,.2],['bench',.18,.30,1.3,-.2],['barrel',.28,.02,1.0,0]],
      beach:[['raft',-.28,.30,2.5,.2],['tent',.25,.27,2.0,-.5],['campfire',.17,.20,1.0,0],['barrel',-.30,-.18,1.0,0],['crate2',-.24,-.23,1.1,.3],['sign',.32,-.12,1.4,.2]],
      moon:[['crate2',-.28,.27,1.2,.2],['sign',.29,-.23,1.5,-.4],['campfire',0,.31,1.0,0]],
      clock:[['bench',-.30,-.24,1.3,.3],['bench',.30,-.24,1.3,-.3],['well',0,.31,2.0,0],['sign',-.31,.17,1.4,.4],['barrel',.31,.17,1.0,0]],
      canyon:[['tent',-.31,-.22,2.0,.2],['campfire',-.22,-.15,1.0,0],['wagon',.31,.22,2.3,-.6],['sign',.26,.29,1.4,0],['barrel',-.33,.23,1.0,0]],
      cave:[['bridge',0,.31,2.2,0],['chest',-.28,-.22,1.3,.2],['barrel',.30,-.22,1.0,0],['campfire',-.22,.25,1.0,0]],
      sky:[['bench',-.28,-.25,1.3,.2],['bench',.28,-.25,1.3,-.2],['well',0,.31,2.0,0],['sign',-.30,.20,1.4,.2]],
      desert:[['tent',-.31,-.24,2.1,.2],['campfire',-.22,-.18,1.0,0],['wagon',.30,.25,2.3,-.5],['well',.22,-.28,1.9,0],['sign',-.32,.19,1.4,.2]],
      swamp:[['bridge',0,.30,2.4,0],['tent',-.29,-.23,2.0,.3],['campfire',-.21,-.16,1.0,0],['barrel',.31,.21,1.0,0],['sign',.29,-.20,1.4,-.2]],
      fair:[['stall',-.32,-.22,2.4,.2],['stall',.32,-.22,2.4,-.2],['wagon',-.28,.25,2.2,.5],['bench',.28,.25,1.3,-.2],['sign',0,.32,1.5,0],['barrel',.20,.02,1.0,0]],
      crystal:[['bridge',-.12,.31,2.2,0],['chest',.28,-.22,1.3,.3],['tent',-.30,-.23,2.0,.2],['campfire',-.22,-.16,1.0,0]],
      rooftop:[['bench',-.30,-.22,1.3,.2],['crate2',.30,-.22,1.2,.2],['barrel',-.28,.24,1.0,0],['sign',.28,.24,1.4,0],['stall',0,.32,2.3,0]],
      festival:[['stall',-.33,-.23,2.4,.2],['stall',.33,-.23,2.4,-.2],['wagon',-.29,.24,2.3,.4],['bench',.29,.24,1.3,-.3],['sign',0,.32,1.5,0],['well',0,-.31,2.0,0],['barrel',.23,.04,1.0,0]]
    };
    const list=sets[map.layout]||sets.forest,hx=map.width*.5,hz=map.height*.5;
    for(const [asset,nx,nz,height,rot] of list){const x=nx*map.width,z=nz*map.height;if(map.hazard){const coord=map.hazard.axis==='x'?x:z;if(Math.abs(coord-map.hazard.at)<map.hazard.width*.85)continue;}const root=this.cloneVisual(this.assets[asset]||this.assets.rock,height,{tint:null});root.position.set(x,0,z);root.rotation.y=rot||0;this.environment.add(root);const solid=!['campfire','sign','barrel','bench'].includes(asset);if(solid)this.obstacles.push({x,z,radius:asset==='house'?1.8:asset==='bridge'?1.25:asset==='wagon'?1.1:.8});}
    // Scenic chains create authored-looking zones instead of isolated props.
    const edgeAsset=['village','castle','fair','festival','rooftop','clock','sky'].includes(map.layout)?'fence':['forest','snow','swamp','beach'].includes(map.layout)?'bush':'rock';
    for(let i=0;i<(this.lowPower?10:18);i++){const side=i%2?-1:1,x=side*(hx*.62+(i%4)*1.45),z=-hz*.08+(i-8)*2.2;if(Math.abs(z)>hz*.63)continue;const root=this.cloneVisual(this.assets[edgeAsset]||this.assets.rock,edgeAsset==='fence'?1.15:edgeAsset==='bush'?.85:1.05,{shadow:true});root.position.set(x,0,z);root.rotation.y=side>0?1.45:-1.45;this.environment.add(root);}
  }

  buildMacroClusters(map){const P={forest:[['tree',-.35,-.28,11,7,4],['birch',.34,.26,10,7,3.6],['mossRock',.28,-.30,7,5,1.4]],park:[['birch',-.35,-.28,8,6,3.3],['food_cookie',.32,.26,8,6,1.35],['bush',-.28,.30,10,6,1]],village:[['birchAutumn',-.34,-.27,9,6,3.4],['deadTree',.34,.26,8,6,3.2],['food_pumpkin',-.28,.31,9,5,1.2]],snow:[['pineSnow',-.35,-.28,12,7,4],['pineSnow',.34,.27,12,7,4],['snowRock',-.27,.31,9,5,1.3]],castle:[['column',-.36,-.26,8,6,3.2],['column',.36,.26,8,6,3.2],['arch',0,.34,6,5,3.6]],beach:[['palm',-.35,-.27,10,7,4],['palm',.34,.27,10,7,4],['rock',.28,-.31,8,5,1.3]],moon:[['rock',-.35,-.27,13,7,1.6],['gem',.34,.27,11,6,1.3]],clock:[['column',-.35,-.27,9,6,3.2],['arch',.34,.27,7,6,3.6]],canyon:[['rock',-.38,-.10,16,10,1.9],['rock',.38,.10,16,10,1.9],['cactus',-.28,.32,8,5,2]],cave:[['mossRock',-.35,-.27,13,7,1.7],['gem',.34,.27,12,6,1.4]],sky:[['arch',-.35,-.27,7,6,3.8],['column',.34,.27,9,6,3]],desert:[['cactus',-.35,-.27,11,7,2.2],['palm',.34,.27,8,6,4],['rock',-.28,.31,9,5,1.3]],swamp:[['willow',-.35,-.27,10,7,4],['willow',.34,.27,10,7,4],['mossRock',-.28,.31,9,5,1.3]],fair:[['arch',-.35,-.27,7,6,3.8],['birch',.34,.27,8,6,3.3],['food_donut',-.28,.31,8,5,1.2]],crystal:[['gem',-.35,-.27,14,7,1.6],['gem',.34,.27,14,7,1.6],['mossRock',-.28,.31,8,5,1.3]],rooftop:[['column',-.35,-.27,9,6,3.1],['arch',.34,.27,7,6,3.6],['torch',-.28,.31,9,5,1.4]],festival:[['arch',-.35,-.27,7,6,3.8],['birch',.34,.27,8,6,3.3],['gem',-.28,.31,10,5,1.3]]},plan=P[map.layout]||P.forest,hx=map.width*.5,hz=map.height*.5;for(let g=0;g<plan.length;g++){const[a,nx,nz,n,r,h]=plan[g],cx=nx*map.width,cz=nz*map.height;for(let i=0;i<n;i++){const q=i*2.4+g*.8,rr=r*(.28+((i*37)%63)/100),x=clamp(cx+Math.cos(q)*rr,-hx+5,hx-5),z=clamp(cz+Math.sin(q)*rr,-hz+5,hz-5);if(Math.hypot(x,z)<7)continue;const o=this.cloneVisual(this.assets[a]||this.assets.rock,h*(.82+(i%5)*.06),{tint:null});o.position.set(x,0,z);o.rotation.y=q;this.environment.add(o);if(h>1.45||['rock','mossRock','cactus'].includes(a))this.obstacles.push({x,z,radius:Math.min(1.25,h*.18)});}}}

  populateMapDecor(map){
    const themes={
      forest:{cover:['grass','grassShort','flower'],props:['birch','tree','mossRock','bush']},
      park:{cover:['grassShort','flower','grass'],props:['birch','food_donut','food_cookie','bush']},
      village:{cover:['grassShort','flower','grass'],props:['birchAutumn','deadTree','food_pumpkin','rock']},
      snow:{cover:['snowRock','grassShort','snowRock'],props:['pineSnow','snowRock','gem','pineSnow']},
      castle:{cover:['rock','chest','torch'],props:['column','arch','chest','torch']},
      beach:{cover:['grassShort','rock','flower'],props:['palm','food_watermelon','rock','palm']},
      moon:{cover:['rock','gem','rock'],props:['rock','gem','star','rock']},
      clock:{cover:['gem','torch','rock'],props:['column','arch','chest','gem']},
      canyon:{cover:['rock','cactus','rock'],props:['cactus','deadTree','rock','mossRock']},
      cave:{cover:['gem','rock','gem'],props:['mossRock','gem','rock','gem']},
      sky:{cover:['gem','star','flower'],props:['arch','column','gem','arch']},
      desert:{cover:['cactus','rock','grassShort'],props:['cactus','palm','deadTree','rock']},
      swamp:{cover:['grass','grassShort','mossRock'],props:['willow','bush','mossRock','willow']},
      fair:{cover:['flower','grassShort','food_cookie'],props:['arch','food_donut','torch','birch']},
      crystal:{cover:['gem','rock','gem'],props:['gem','mossRock','star','gem']},
      rooftop:{cover:['torch','chest','rock'],props:['column','arch','torch','chest']},
      festival:{cover:['flower','grassShort','food_donut'],props:['arch','birch','gem','torch']}
    };
    const theme=themes[map.layout]||themes.forest,hx=map.width*.5,hz=map.height*.5;
    // Ground cover is dense but non-colliding and deterministic: it masks the
    // modular base surface while leaving readable lanes for combat.
    if(!['castle','clock','sky','rooftop'].includes(map.layout)){
      const coverCount=this.lowPower?118:238;
      for(let i=0;i<coverCount;i++){
        const a=i*2.3999632297+map.enemyTier*.47,rad=.13+((i*37)%100)/100*.73;
        let x=Math.cos(a)*hx*rad*.93,z=Math.sin(a)*hz*rad*.93;
        x+=Math.sin(i*5.17+map.enemyTier)*2.8;z+=Math.cos(i*4.31-map.enemyTier)*2.4;
        if(Math.hypot(x,z)<7.5||Math.abs(x)>hx-4||Math.abs(z)>hz-4)continue;
        if(map.hazard){const coord=map.hazard.axis==='x'?x:z;if(Math.abs(coord-map.hazard.at)<map.hazard.width*.72)continue;}
        const key=theme.cover[i%theme.cover.length],h=key.includes('grass')||key==='flower'?.34+(i%4)*.08:.48+(i%5)*.09;
        const root=this.cloneVisual(this.assets[key]||this.assets.bush,h,{shadow:false,tint:map.accent});root.position.set(x,.01,z);root.rotation.y=(i*.91)%TAU;root.scale.multiplyScalar(.78+((i*13)%29)/100);this.environment.add(root);
      }
    }
    const propCount=this.lowPower?46:86;
    for(let i=0;i<propCount;i++){
      const ring=.25+((i*29)%70)/100*.65,a=i*2.117+map.enemyTier*.71;
      let x=Math.cos(a)*hx*ring*.91,z=Math.sin(a)*hz*ring*.91;
      x+=Math.sin(i*1.91)*3.2;z+=Math.cos(i*2.23)*2.7;
      if(Math.hypot(x,z)<9||Math.abs(x)>hx-5||Math.abs(z)>hz-5)continue;
      if(map.hazard){const coord=map.hazard.axis==='x'?x:z;if(Math.abs(coord-map.hazard.at)<map.hazard.width*.8)continue;}
      const key=theme.props[(i+map.enemyTier)%theme.props.length],large=['tree','deadTree','pineSnow','birch','birchAutumn','willow','palm','column','arch'].includes(key);
      const height=large?2.7+(i%5)*.31:.68+(i%5)*.12,root=this.cloneVisual(this.assets[key]||this.assets.rock,height,{tint:map.accent});
      root.position.set(x,0,z);root.rotation.y=(i*1.43+map.enemyTier*.33)%TAU;this.environment.add(root);
      if(large||['rock','mossRock','cactus'].includes(key))this.obstacles.push({x,z,radius:large?.7:.48});
    }
  }

  buildBoundary(map){const S={forest:['tree','birch','bush','mossRock'],park:['birch','bush','food_cookie','rock'],village:['birchAutumn','deadTree','rock','food_pumpkin'],snow:['pineSnow','snowRock','pineSnow','rock'],castle:['column','arch','rock','column'],beach:['palm','rock','palm','bush'],moon:['rock','gem','rock','star'],clock:['column','arch','rock','gem'],canyon:['rock','mossRock','cactus','rock'],cave:['rock','mossRock','gem','rock'],sky:['arch','column','gem','arch'],desert:['cactus','rock','deadTree','palm'],swamp:['willow','bush','mossRock','willow'],fair:['arch','birch','food_cookie','torch'],crystal:['gem','mossRock','rock','gem'],rooftop:['column','arch','torch','column'],festival:['arch','birch','gem','food_donut']},A=S[map.layout]||S.forest,hx=map.width/2,hz=map.height/2,step=this.lowPower?6.7:5.3;const add=(a,x,z,r,h)=>{const o=this.cloneVisual(this.assets[a]||this.assets.rock,h,{tint:null});o.position.set(x,0,z);o.rotation.y=r;this.environment.add(o);};for(let ring=0;ring<4;ring++){const out=2+ring*4.8;let k=0;for(let x=-hx-4;x<=hx+4;x+=step){const j=Math.sin((x+ring*13)*.36)*1.3,a=A[(k++ +ring)%A.length],h=['tree','birch','birchAutumn','pineSnow','willow','palm'].includes(a)?4.5:a==='column'||a==='arch'?3.7:1.8;add(a,x+j,-hz-out,.2,h);add(A[k%A.length],x-j,hz+out,3.3,h);}for(let z=-hz;z<=hz;z+=step){const j=Math.cos((z-ring*11)*.31)*1.2,a=A[(k++ +ring)%A.length],h=['tree','birch','birchAutumn','pineSnow','willow','palm'].includes(a)?4.5:a==='column'||a==='arch'?3.7:1.8;add(a,-hx-out,z+j,1.57,h);add(A[k%A.length],hx+out,z-j,-1.57,h);}}}

  buildHazard(map,hz){
    const tint=hz.type==='lava'?0xff6a25:0x4baee8;const axis=hz.axis;const span=axis==='x'?map.height:map.width;const base=this.prepareGround(this.assets.floor,5,tint);
    for(let p=-span/2+8;p<span/2-8;p+=5){const r=base.clone(true);r.position.set(axis==='x'?hz.at:p,.08,axis==='x'?p:hz.at);r.scale.set(axis==='x'?hz.width/5:1,1,axis==='x'?1:hz.width/5);this.environment.add(r);}
    this.hazards.push({...hz,damage:hz.type==='lava'?18:0,slow:hz.type==='water'?.58:1});
  }

  buildPlayer(){
    const hero=this.currentHero(),root=this.cloneVisual(this.assets[hero.asset],2.48);root.position.set(0,0,0);root.rotation.y=Math.PI;this.scene.add(root);
    const actor={root,gltf:this.assets[hero.asset],mixer:new THREE.AnimationMixer(root),action:null,kind:''};
    const meta=this.progress.meta;
    const p={...actor,hero,speed:6.15*(1+(meta.speed||0)*.025),speedMul:1,maxHp:100+(meta.health||0)*10,hp:100+(meta.health||0)*10,damageMul:1+(meta.power||0)*.04,cooldownMul:1-(meta.cooldown||0)*.025,
      damageTaken:1-(meta.armor||0)*.025,areaMul:1+(meta.area||0)*.04,xpMul:1+(meta.xp||0)*.05,pickup:3.2*(1+(meta.pickup||0)*.08),crit:.05+(meta.crit||0)*.02,regen:(meta.regen||0)*.08,extraProjectiles:0,extraPierce:0,duration:1,
      splashMul:1,slowBonus:0,homingBonus:0,projectileSpeedMul:1,dodge:0,knockbackMul:1,luck:(meta.luck||0)*.04,dashCd:2.25,dashTimer:0,dashTime:0,invuln:0,weaponRanks:{},passiveRanks:{},weaponTimers:{},weapons:[],freeRerolls:meta.reroll||0,rerolls:0};
    const b=hero.bonus||{};p.damageMul*=1+(b.damage||0);p.damageTaken*=1-(b.armor||b.damageReduction||0);p.maxHp*=1+(b.hp||0);p.hp=p.maxHp;p.speed*=1+(b.speed||0);p.cooldownMul*=1-(b.cooldown||0);p.xpMul*=1+(b.xp||0);p.pickup*=1+(b.pickup||0);p.crit+=b.crit||0;p.regen+=b.regen||0;p.extraProjectiles+=b.projectiles||0;p.extraPierce+=b.pierce||0;p.duration*=1+(b.duration||0);p.luck+=b.luck||0;p.splashMul*=1+(b.splash||0);p.slowBonus+=b.slow||0;p.homingBonus+=b.homing||0;p.projectileSpeedMul*=1+(b.projectileSpeed||0);p.dodge+=b.dodge||0;p.knockbackMul*=1+(b.knockback||0);
    p.weapons=[hero.weapon];p.weaponRanks[hero.weapon]=1;this.player=p;this.playActor(p,'idle');this.attachHeldWeapon(p,weaponById(hero.weapon));this.syncLoadoutUI();
  }

  async startRun(){
    const map=this.currentMap(),mode=this.currentMode();if(!(AUTO_START&&DEBUG_MAP)&&(this.progress.wins<map.unlockWins||this.progress.wins<mode.unlockWins))return;
    this.audio.resume();this.audio.startMusic();this.clearWorld();this.menuPreview?.root?.removeFromParent();this.menuPreview=null;this.buildEnvironment(map);this.buildPlayer();
    this.elapsed=0;this.kills=0;this.level=1;this.xp=0;this.nextXp=8;this.runCoins=0;this.spawnClock=0;this.eliteClock=0;this.runWon=false;this.reviveUsed=false;this.doubleUsed=false;this.boss=null;
    this.state='playing';document.body.dataset.gameState='playing';$('#menu').classList.remove('screen--visible');$('#gameover').classList.remove('screen--visible');$('#pause-panel').classList.remove('screen--visible');$('#hud').classList.remove('hidden');
    if(this.lowPower)$('#mobile-controls').classList.remove('hidden');this.bridge.startGameplay();this.camera.position.set(0,this.lowPower?10.2:9.2,this.lowPower?12.0:10.6);if(DEBUG_FAST){for(let q=0;q<3;q++){const e=this.spawnEnemy(false,ENEMIES[0]);e.root.position.set(2.8+q*1.25,0,-1.3+q*1.3);e.hp=4;e.maxHp=4;e.speed=0;e.damage=1;e.def={...e.def,behavior:'chase'};}}this.updateHUD();
  }

  resolveObstacles(root,radius=.55){for(const o of this.obstacles){const dx=root.position.x-o.x,dz=root.position.z-o.z,d=Math.hypot(dx,dz),min=o.radius+radius;if(d<min&&d>.001){root.position.x=o.x+dx/d*min;root.position.z=o.z+dz/d*min;}}}

  updatePlayer(dt){
    const p=this.player;if(!p)return;p.mixer.update(dt);p.invuln=Math.max(0,p.invuln-dt);p.dashTimer=Math.max(0,p.dashTimer-dt);
    const map=this.currentMap(),v=this.input.vector();let terrainMul=1;
    for(const h of this.hazards){const coord=h.axis==='x'?p.root.position.x:p.root.position.z;if(Math.abs(coord-h.at)<h.width*.5)terrainMul*=h.slow;}
    if(this.input.consumeDash()&&p.dashTimer<=0){p.dashTimer=p.dashCd;p.dashTime=.22;p.invuln=.30;this.audio.dash();this.vibrate(18);}
    p.dashTime=Math.max(0,p.dashTime-dt);const mul=p.dashTime>0?2.9:1;
    if(v.lengthSq()>.02){const dir=new THREE.Vector3(v.x,0,-v.y).normalize();p.root.position.addScaledVector(dir,p.speed*p.speedMul*mul*terrainMul*dt);p.root.rotation.y=Math.atan2(dir.x,dir.z);this.playActor(p,p.dashTime>0?'run':'run');}else this.playActor(p,'idle');
    const hx=map.width/2-3.3,hz=map.height/2-3.3;p.root.position.x=clamp(p.root.position.x,-hx,hx);p.root.position.z=clamp(p.root.position.z,-hz,hz);
    this.resolveObstacles(p.root,.58);
    for(const h of this.hazards){const coord=h.axis==='x'?p.root.position.x:p.root.position.z;if(Math.abs(coord-h.at)<h.width*.5&&h.damage&&p.invuln<=0)this.hurtPlayer(h.damage*dt);}
    if(p.regen>0)p.hp=Math.min(p.maxHp,p.hp+p.regen*dt);
    for(const id of p.weapons)this.updateWeapon(id,dt);
    const dashPct=p.dashTimer<=0?0:p.dashTimer/p.dashCd;$('#dash-cooldown').style.transform=`scaleY(${dashPct})`;
  }

  nearestEnemy(pos,range=999,exclude=null){let best=null,bd=range*range;for(const e of this.enemies){if(e.dead||e===exclude)continue;const d=e.root.position.distanceToSquared(pos);if(d<bd){bd=d;best=e;}}return best;}

  updateWeapon(id,dt){
    const p=this.player,w=weaponById(id);if(!w)return;const rank=p.weaponRanks[id]||1;let timer=(p.weaponTimers[id]||0)-dt;p.weaponTimers[id]=timer;if(timer>0)return;
    const target=this.nearestEnemy(p.root.position,w.range*(1+rank*.018));if(!target)return;
    p.weaponTimers[id]=Math.max(.10,w.cooldown*p.cooldownMul*Math.pow(.94,rank-1));this.fireWeapon(w,rank,target);document.body.dataset.qaShots=String((Number(document.body.dataset.qaShots)||0)+1);this.playWeaponAttack(p,w);setTimeout(()=>{if(this.state==='playing'&&this.player===p)this.playActor(p,'idle',true,.08);},160);
    this.audio.attack();
  }

  fireWeapon(w,rank,target){
    const p=this.player,count=Math.max(1,(w.count||1)+(p.extraProjectiles||0)+(rank>=3?1:0)),damage=w.damage*p.damageMul*(1+(rank-1)*.22),area=p.areaMul*(1+(rank-1)*.035),pierce=(w.pierce||0)+p.extraPierce+(rank>=5?1:0);
    const from=this.projectileOrigin(p);const baseDir=target.root.position.clone().sub(from);baseDir.y=0;baseDir.normalize();
    const spawn=(dir,opts={})=>this.spawnProjectile(w,from,dir,{damage,area,pierce,rank,...opts});
    if(w.type==='radial'){for(let i=0;i<count;i++){const a=i/count*TAU;spawn(new THREE.Vector3(Math.sin(a),0,Math.cos(a)));}return;}
    if(w.type==='mine'){const dir=baseDir.clone();spawn(dir,{mine:true});return;}
    if(w.type==='chain'){spawn(baseDir,{chain:w.chain||3});return;}
    for(let i=0;i<count;i++){const offset=(i-(count-1)/2)*(w.spread||.10);const dir=baseDir.clone().applyAxisAngle(new THREE.Vector3(0,1,0),offset);spawn(dir,{homing:w.homing||0,boomerang:w.type==='boomerang'});}
  }

  spawnProjectile(w,from,dir,opts){
    const root=this.cloneVisual(this.assets[w.projectile]||this.assets.gem,w.scale||.25,{shadow:false,tint:w.fx||null});root.position.copy(from);root.scale.multiplyScalar(opts.area||1);root.rotation.y=Math.atan2(dir.x,dir.z);this.scene.add(root);
    const shotSpeed=(w.speed||18)*this.player.projectileSpeedMul;
    this.projectiles.push({root,w,vel:dir.clone().multiplyScalar(shotSpeed),life:(w.range||24)/shotSpeed*this.player.duration,damage:opts.damage,pierce:opts.pierce||0,hit:new Set(),homing:(opts.homing||0)+this.player.homingBonus,chain:opts.chain||0,boomerang:opts.boomerang,age:0,mine:opts.mine,slow:Math.min(.8,(w.slow||0)+this.player.slowBonus),splash:(w.splash||0)*(opts.area||1)*this.player.splashMul,knockback:(w.knockback||0)*this.player.knockbackMul,crit:(w.crit||0)+this.player.crit});
  }

  updateProjectiles(dt){
    for(let i=this.projectiles.length-1;i>=0;i--){const p=this.projectiles[i];p.age+=dt;p.life-=dt;if(p.mine){p.vel.multiplyScalar(Math.max(0,1-dt*3));}
      if(p.homing){const t=this.nearestEnemy(p.root.position,12);if(t){const desired=t.root.position.clone().sub(p.root.position);desired.y=0;desired.normalize().multiplyScalar(p.w.speed||18);p.vel.lerp(desired,clamp(dt*p.homing,0,1));}}
      if(p.boomerang&&p.life<.55){const desired=this.player.root.position.clone().sub(p.root.position);desired.y=0;desired.normalize().multiplyScalar(p.w.speed||18);p.vel.lerp(desired,clamp(dt*5,0,1));}
      p.root.position.addScaledVector(p.vel,dt);p.root.rotation.x+=dt*5;p.root.rotation.z+=dt*3;
      const expired=p.life<=0;let remove=expired;
      for(const e of this.enemies){if(e.dead||p.hit.has(e))continue;const rr=(e.radius||.75)+.58*(p.root.scale.x||1);const dx=e.root.position.x-p.root.position.x,dz=e.root.position.z-p.root.position.z;if(dx*dx+dz*dz<rr*rr){p.hit.add(e);let dmg=p.damage;if(Math.random()<p.crit)dmg*=2;this.damageEnemy(e,dmg,p);if(p.chain>0)this.chainFrom(e,p);if(p.splash>0)this.splashDamage(e.root.position,p.splash,dmg*.62,e);if(p.knockback){const d=e.root.position.clone().sub(this.player.root.position);d.y=0;d.normalize();e.root.position.addScaledVector(d,p.knockback);}p.pierce--;if(p.pierce<0){remove=true;break;}}}
      if(remove){if(p.mine&&expired&&p.splash>0)this.splashDamage(p.root.position,p.splash,p.damage*.62,null);p.root.removeFromParent();this.projectiles.splice(i,1);}
    }
  }
  spawnImpact(pos,color=0xffffff,strong=false){if(this.lowPower&&this.effects.length>28)return;const root=this.cloneVisual(this.assets.star||this.assets.gem,strong?.26:.15,{shadow:false,tint:color});root.position.copy(pos);root.position.y=.72;root.scale.setScalar(.35);this.scene.add(root);this.effects.push({root,life:strong?.34:.20,max:strong?.34:.20,spin:5+Math.random()*4});}
  updateEffects(dt){for(let i=this.effects.length-1;i>=0;i--){const e=this.effects[i];e.life-=dt;e.root.rotation.y+=dt*e.spin;e.root.rotation.z+=dt*e.spin*.55;const t=1-e.life/e.max;e.root.scale.setScalar(.35+t*(t<.55?1.35:.65));e.root.position.y+=dt*.9;if(e.life<=0){e.root.removeFromParent();this.effects.splice(i,1);}}}

  chainFrom(first,p){let prev=first;for(let n=0;n<p.chain;n++){const t=this.nearestEnemy(prev.root.position,p.w.chainRadius||6,prev);if(!t||p.hit.has(t))break;p.hit.add(t);this.damageEnemy(t,p.damage*.72,p);prev=t;}}
  splashDamage(pos,radius,damage,exclude){const r2=radius*radius;for(const e of this.enemies){if(e.dead||e===exclude)continue;if(e.root.position.distanceToSquared(pos)<=r2)this.damageEnemy(e,damage,null);}}

  spawnEnemy(elite=false,defOverride=null){
    const map=this.currentMap(),mode=this.currentMode(),tier=Math.min(ENEMIES.length-1,Math.floor(this.elapsed/(DEBUG_FAST?10:55))+Math.floor(map.enemyTier/2));
    const pool=ENEMIES.slice(0,Math.max(5,tier+1));const def=defOverride||pool[Math.floor(Math.random()*pool.length)];
    const root=this.cloneVisual(this.assets[def.asset],def.scale*(elite?1.45:1),{tint:elite?0xffd76a:null});const hx=map.width/2-6,hz=map.height/2-6;let x,z;
    const side=Math.floor(Math.random()*4);if(side===0){x=-hx;z=(Math.random()*2-1)*hz;}else if(side===1){x=hx;z=(Math.random()*2-1)*hz;}else if(side===2){z=-hz;x=(Math.random()*2-1)*hx;}else{z=hz;x=(Math.random()*2-1)*hx;}
    root.position.set(x,0,z);this.scene.add(root);const hp=def.hp*mode.difficulty*(1+this.elapsed/(DEBUG_FAST?55:680))*(elite?5.2:1),actor={root,gltf:this.assets[def.asset],mixer:new THREE.AnimationMixer(root),action:null,kind:''};
    const e={...actor,def,hp,maxHp:hp,speed:def.speed*(elite?1.07:1),damage:def.damage*mode.difficulty*(elite?1.7:1),radius:.7*def.scale*(elite?1.4:1),elite,dead:false,brain:Math.random()*10,attackCd:Math.random(),dashCd:1+Math.random()*2,shield:def.behavior==='shield'?hp*.45:0};
    this.playActor(e,'run');this.enemies.push(e);return e;
  }

  updateEnemies(dt){
    const pp=this.player.root.position;
    for(const e of this.enemies){if(e.dead)continue;if(e.boss)continue;e.mixer.update(dt);e.brain+=dt;e.attackCd-=dt;e.dashCd-=dt;const dvec=pp.clone().sub(e.root.position);dvec.y=0;const dist=Math.max(.001,dvec.length()),dir=dvec.clone().divideScalar(dist),tangent=new THREE.Vector3(-dir.z,0,dir.x);let move=dir.clone(),speed=e.speed;
      switch(e.def.behavior){
        case'zigzag':move.addScaledVector(tangent,Math.sin(e.brain*4)*.95).normalize();break;
        case'dash':if(e.dashCd<=0&&dist<11){speed*=3.4;e.dashCd=3.4;}break;
        case'ranged':case'sniper':if(dist<9)move.multiplyScalar(-1);else if(dist<(e.def.behavior==='sniper'?17:13))move.set(0,0,0);if(e.attackCd<=0&&dist<24){this.enemyShot(e,dir,e.def.behavior==='sniper'?1.45:1);e.attackCd=e.def.behavior==='sniper'?2.1:1.55;}break;
        case'charger':if(e.dashCd<=0&&dist<14){speed*=4.1;e.dashCd=4.2;}break;
        case'swarm':speed*=1.35;break;
        case'orbit':move=tangent.multiplyScalar(dist>8?1:dist<5?-1:1).addScaledVector(dir,(dist-7)*.15).normalize();break;
        case'healer':{const ally=this.enemies.find(a=>a!==e&&!a.dead&&a.hp<a.maxHp*.65&&a.root.position.distanceToSquared(e.root.position)<45);if(ally&&e.attackCd<=0){ally.hp=Math.min(ally.maxHp,ally.hp+ally.maxHp*.12);e.attackCd=3;}break;}
        case'bomber':if(dist<3.4){this.splashEnemyExplosion(e);this.killEnemy(e,false);continue;}break;
      }
      e.root.position.addScaledVector(move,speed*dt);this.resolveObstacles(e.root,Math.max(.35,e.radius*.55));if(move.lengthSq()>.01)e.root.rotation.y=Math.atan2(move.x,move.z);
      if(dist<e.radius+.62&&this.player.invuln<=0){this.hurtPlayer(e.damage);this.player.invuln=.62;const push=dir.clone();this.player.root.position.addScaledVector(push,1.2);}
    }
  }

  enemyShot(e,dir,mul=1){const root=this.cloneVisual(this.assets.gem,.22,{shadow:false,tint:0xff6b78});root.position.copy(e.root.position).add(new THREE.Vector3(0,1,0));this.scene.add(root);this.enemyProjectiles.push({root,vel:dir.clone().multiplyScalar(8.5*mul),life:4,damage:e.damage*.7});}
  updateEnemyProjectiles(dt){for(let i=this.enemyProjectiles.length-1;i>=0;i--){const p=this.enemyProjectiles[i];p.life-=dt;p.root.position.addScaledVector(p.vel,dt);p.root.rotation.y+=dt*4;if(p.root.position.distanceToSquared(this.player.root.position)<.8&&this.player.invuln<=0){this.hurtPlayer(p.damage);this.player.invuln=.42;p.life=0;}if(p.life<=0){p.root.removeFromParent();this.enemyProjectiles.splice(i,1);}}}
  splashEnemyExplosion(e){const d=e.root.position.distanceTo(this.player.root.position);if(d<4.2)this.hurtPlayer(e.damage*1.35);}

  damageEnemy(e,amount,proj){if(e.dead)return;document.body.dataset.qaHits=String((Number(document.body.dataset.qaHits)||0)+1);this.spawnImpact(e.root.position,proj?.w?.fx||e.def?.color||0xffffff,amount>35);if(e.shield>0){const used=Math.min(e.shield,amount);e.shield-=used;amount-=used;}e.hp-=amount;if(e.hp<=0)this.killEnemy(e,true);}
  killEnemy(e,reward=true){
    if(e.dead)return;const deathPos=e.root.position.clone(),shouldSplit=reward&&!e.elite&&e.def.behavior==='split';e.dead=true;this.playActor(e,'death',false,.03);
    if(reward){this.kills++;document.body.dataset.qaKills=String(this.kills);this.runCoins+=e.elite?16:1+Math.floor(e.def.xp/2);const xpValue=e.def.xp*(e.elite?7:1);this.spawnPickup(deathPos,'xp',xpValue);if(e.elite)this.spawnPickup(deathPos,'coin',8);this.audio.kill();this.progress.daily.kills++;this.progress.career.kills++;if(e.elite)setTimeout(()=>this.openChest(),280);}
    if(shouldSplit){for(let n=0;n<2;n++){const child=this.spawnEnemy(false,e.def);child.root.position.copy(deathPos).add(new THREE.Vector3(n?1:-1,0,(Math.random()-.5)*1.4));child.root.scale.multiplyScalar(.62);child.radius*=.62;child.hp=Math.max(12,e.maxHp*.26);child.maxHp=child.hp;child.damage*=.72;child.speed*=1.18;child.def={...child.def,behavior:'chase',xp:Math.max(1,Math.floor(e.def.xp*.55))};}}
    setTimeout(()=>{e.root.removeFromParent();const i=this.enemies.indexOf(e);if(i>=0)this.enemies.splice(i,1);},240);
  }

  spawnPickup(pos,type='xp',value=1){const root=this.cloneVisual(this.assets[type==='coin'?'food_donut':'gem'],type==='coin'?.28:.18,{shadow:false,tint:type==='coin'?0xffd55d:0x78e8ff});root.position.copy(pos);root.position.y=.35;this.scene.add(root);this.pickups.push({root,type,value,phase:Math.random()*TAU});}
  updatePickups(dt){for(let i=this.pickups.length-1;i>=0;i--){const p=this.pickups[i];p.phase+=dt*4;p.root.rotation.y+=dt*3;p.root.position.y=.34+Math.sin(p.phase)*.08;const d=p.root.position.distanceTo(this.player.root.position);if(d<this.player.pickup){const dir=this.player.root.position.clone().sub(p.root.position);dir.y=0;p.root.position.addScaledVector(dir.normalize(),dt*(7+(this.player.pickup-d)*2));}if(d<.9){if(p.type==='xp')this.addXp(p.value);else this.runCoins+=p.value;this.audio.pickup();p.root.removeFromParent();this.pickups.splice(i,1);}}}

  addXp(v){if(this.state!=='playing')return;this.xp+=v*this.player.xpMul;while(this.xp>=this.nextXp&&this.state==='playing'){this.xp-=this.nextXp;this.level++;this.nextXp=Math.floor(this.nextXp*1.24+3);this.progress.daily.levels++;this.progress.career.levels++;this.offerUpgrade();}}

  offerUpgrade(){
    this.state='upgrade';this.bridge.stopGameplay();this.audio.level();$('#upgrade-level').textContent=this.level;$('#upgrade').classList.add('screen--visible');this.renderUpgradeChoices();
  }
  upgradeCandidates(){
    const p=this.player,c=[];
    if(p.weapons.length<6)for(const w of WEAPONS)if(!p.weapons.includes(w.id))c.push({kind:'newWeapon',id:w.id,rarity:2});
    for(const id of p.weapons){const r=p.weaponRanks[id]||1,w=weaponById(id),recipeReady=(p.passiveRanks[w.passive]||0)>0;if(r<7||(r===7&&recipeReady))c.push({kind:'weapon',id,rarity:r>=7?4:2});}
    for(const pa of PASSIVES){const r=p.passiveRanks[pa.id]||0;if(r<pa.max)c.push({kind:'passive',id:pa.id,rarity:1});}
    return c;
  }
  renderUpgradeChoices(){const choices=shuffle(this.upgradeCandidates()).slice(0,3);$('#upgrade-cards').innerHTML=choices.map(c=>this.upgradeCardHTML(c)).join('');$$('#upgrade-cards [data-upgrade]').forEach(b=>b.onclick=()=>this.takeUpgrade(JSON.parse(decodeURIComponent(b.dataset.upgrade))));}
  upgradeCardHTML(c){if(c.kind==='newWeapon'){const w=weaponById(c.id);return `<button class="upgrade-card rare" data-upgrade="${encodeURIComponent(JSON.stringify(c))}"><img class="upgrade-weapon-art" src="${this.weaponPortrait(w)}" alt=""><span>НОВОЕ ОРУЖИЕ</span><strong>${esc(w.name)}</strong><p>Добавляет новый дальнобойный стиль атаки.</p><em>${esc(w.evolution)}</em></button>`;}if(c.kind==='weapon'){const w=weaponById(c.id),r=this.player.weaponRanks[c.id]||1;return `<button class="upgrade-card ${r>=7?'legendary':''}" data-upgrade="${encodeURIComponent(JSON.stringify(c))}"><img class="upgrade-weapon-art" src="${this.weaponPortrait(w)}" alt=""><span>${r>=7?'ЭВОЛЮЦИЯ':`ОРУЖИЕ · ${r+1}/8`}</span><strong>${esc(r>=7?w.evolution:w.name)}</strong><p>${esc(w.levels[Math.min(7,r)])}</p><em>${esc(passiveById(w.passive)?.name||'')}</em></button>`;}const p=passiveById(c.id),r=this.player.passiveRanks[c.id]||0;return `<button class="upgrade-card" data-upgrade="${encodeURIComponent(JSON.stringify(c))}"><span>ПАССИВ · ${r+1}/${p.max}</span><strong>${p.icon} ${esc(p.name)}</strong><p>${esc(p.desc)}</p></button>`;}
  takeUpgrade(c){const p=this.player;if(c.kind==='newWeapon'){p.weapons.push(c.id);p.weaponRanks[c.id]=1;}else if(c.kind==='weapon'){p.weaponRanks[c.id]=(p.weaponRanks[c.id]||1)+1;if(p.weaponRanks[c.id]>=8)this.runToast(`${weaponById(c.id).evolution}!`);}else{p.passiveRanks[c.id]=(p.passiveRanks[c.id]||0)+1;passiveById(c.id)?.apply(p);}this.syncLoadoutUI();$('#upgrade').classList.remove('screen--visible');this.state='playing';this.bridge.startGameplay();}
  rerollUpgrade(){if(this.player.freeRerolls>this.player.rerolls){this.player.rerolls++;this.renderUpgradeChoices();return;}this.tryAdReroll();}
  async tryAdReroll(){const ok=await this.bridge.showRewarded();if(ok){this.renderUpgradeChoices();this.bridge.startGameplay();}else this.toast('Реклама сейчас недоступна');}

  syncLoadoutUI(){if(!this.player)return;$('#weapon-slots').innerHTML=this.player.weapons.map(id=>{const w=weaponById(id);return `<div title="${esc(w.name)}"><b>${this.player.weaponRanks[id]||1}</b><img src="${this.weaponPortrait(w)}" alt=""></div>`;}).join('');$('#passive-slots').innerHTML=Object.entries(this.player.passiveRanks).filter(([,r])=>r>0).map(([id,r])=>`<div title="${esc(passiveById(id)?.name||id)}"><b>${r}</b><span>${passiveById(id)?.icon||'◆'}</span></div>`).join('');}

  openChest(){if(this.state!=='playing')return;this.state='chest';this.bridge.stopGameplay();const cand=shuffle(this.upgradeCandidates()).slice(0,1)[0];if(!cand){this.state='playing';this.bridge.startGameplay();return;}$('#chest-panel').classList.add('screen--visible');$('#chest-title').textContent=cand.kind==='newWeapon'?'НОВОЕ ОРУЖИЕ':cand.kind==='weapon'?'УСИЛЕНИЕ ОРУЖИЯ':'НОВАЯ СИЛА';$('#chest-reward').innerHTML=this.upgradeCardHTML(cand).replace(/<button|<\/button>/g,m=>m.startsWith('</')?'</div>':'<div');const btn=$('#chest-reward [data-upgrade]');btn?.removeAttribute('data-upgrade');this.takeChestReward(cand);}
  takeChestReward(c){const p=this.player;if(c.kind==='newWeapon'){p.weapons.push(c.id);p.weaponRanks[c.id]=1;}else if(c.kind==='weapon')p.weaponRanks[c.id]=(p.weaponRanks[c.id]||1)+1;else{p.passiveRanks[c.id]=(p.passiveRanks[c.id]||0)+1;passiveById(c.id)?.apply(p);}this.syncLoadoutUI();}

  updateSpawning(dt){
    if(this.boss)return;const mode=this.currentMode(),fast=DEBUG_FAST?4:1;this.spawnClock-=dt;this.eliteClock-=dt;
    const interval=Math.max(.18,(1.15-this.elapsed/(DEBUG_FAST?55:1100))/mode.difficulty)/fast;if(this.spawnClock<=0){this.spawnClock=interval;const count=Math.min(this.lowPower?4:7,1+Math.floor(this.elapsed/(DEBUG_FAST?20:180)));for(let i=0;i<count;i++)this.spawnEnemy(false);}
    if(this.eliteClock<=0){this.eliteClock=DEBUG_FAST?12:62;this.spawnEnemy(true);}
    if(this.currentMode().id!=='endless'&&this.elapsed>=this.currentMode().seconds)this.spawnBoss();
    if(this.currentMode().id==='endless'&&this.elapsed>0&&Math.floor(this.elapsed)%240===0&&!this.boss)this.spawnBoss();
  }

  spawnBoss(){if(this.boss)return;const def=BOSSES[this.currentMap().bossIndex];const e=this.spawnEnemy(false,ENEMIES[(this.currentMap().bossIndex*3)%ENEMIES.length]);e.root.scale.multiplyScalar(def.scale/e.def.scale);e.hp=def.hp*this.currentMode().difficulty;e.maxHp=e.hp;e.damage=def.damage;e.speed=def.speed;e.radius=1.5;e.boss=true;e.bossDef=def;e.phase=1;e.patternClock=1.3;e.def={...e.def,behavior:'boss'};this.boss=e;$('#boss-name').textContent=def.name;$('#boss-phase').textContent='I';$('#boss-wrap').classList.remove('hidden');this.audio.boss();this.runToast(def.name.toUpperCase());}
  updateBoss(dt){const b=this.boss;if(!b||b.dead)return;b.mixer.update(dt);b.patternClock-=dt;const ratio=b.hp/b.maxHp;const phase=ratio>.66?1:ratio>.33?2:3;if(phase!==b.phase){b.phase=phase;$('#boss-phase').textContent=['','I','II','III'][phase];this.runToast(`ФАЗА ${phase}`);}const pp=this.player.root.position,dir=pp.clone().sub(b.root.position);dir.y=0;const dist=dir.length();dir.normalize();b.root.rotation.y=Math.atan2(dir.x,dir.z);
    const pattern=b.bossDef.pattern;if(pattern==='charge'||pattern==='dash')b.root.position.addScaledVector(dir,b.speed*(b.patternClock<.25?4.5:1)*dt);else if(dist>10)b.root.position.addScaledVector(dir,b.speed*dt);
    if(b.patternClock<=0){b.patternClock=Math.max(.45,2.2-b.phase*.35);if(pattern==='rings')this.bossRing(b,6+b.phase*2);if(pattern==='spiral')this.bossRing(b,8+b.phase*3,b.brain*.7);if(pattern==='summon')for(let i=0;i<2+b.phase;i++)this.spawnEnemy();if(pattern==='meteors')for(let i=0;i<3+b.phase;i++)this.enemyShot(b,new THREE.Vector3(Math.sin(i*TAU/(3+b.phase)),0,Math.cos(i*TAU/(3+b.phase))),.8);if(pattern==='charge'||pattern==='dash')b.dashCd=.2;}
    if(dist<b.radius+.75&&this.player.invuln<=0){this.hurtPlayer(b.damage);this.player.invuln=.7;}$('#boss-fill').style.width=`${Math.max(0,ratio)*100}%`;
  }
  bossRing(b,count,offset=0){for(let i=0;i<count;i++){const a=i/count*TAU+offset;this.enemyShot(b,new THREE.Vector3(Math.sin(a),0,Math.cos(a)),.8);}}

  hurtPlayer(amount){const p=this.player;if(!p||p.invuln>0)return;if(p.dodge>0&&Math.random()<p.dodge){this.runToast('УКЛОНЕНИЕ');return;}p.hp-=amount*p.damageTaken;$('#damage-flash').classList.add('show');setTimeout(()=>$('#damage-flash').classList.remove('show'),70);this.audio.hurt();this.vibrate(25);if(p.hp<=0)this.endRun(false);}

  async endRun(victory){if(this.state==='gameover')return;this.state='gameover';document.body.dataset.gameState=victory?'victory':'gameover';this.bridge.stopGameplay();$('#hud').classList.add('hidden');$('#mobile-controls').classList.add('hidden');$('#boss-wrap').classList.add('hidden');
    const mode=this.currentMode(),coins=Math.floor((this.runCoins+this.kills*.22+this.level*3)*mode.reward);this.runCoins=coins;this.progress.runs++;this.progress.bestTime=Math.max(this.progress.bestTime,this.elapsed);this.progress.bestKills=Math.max(this.progress.bestKills,this.kills);this.progress.coins+=coins;this.progress.career.survive+=Math.floor(this.elapsed);this.progress.daily.survive+=Math.floor(this.elapsed);
    if(victory){this.progress.wins++;this.progress.career.wins++;this.progress.career.bosses++;this.progress.mapWins[this.currentMap().id]=(this.progress.mapWins[this.currentMap().id]||0)+1;this.progress.career.mapWins=Object.keys(this.progress.mapWins).filter(k=>this.progress.mapWins[k]>0).length;this.audio.victory();}
    await this.save();$('#result-kicker').textContent=victory?'ПОБЕДА НА КАРТЕ':'ЗАБЕГ ОКОНЧЕН';$('#result-title').textContent=victory?'БОСС ПОВЕРЖЕН':'ГЕРОЙ ПАЛ';$('#result-time').textContent=fmtTime(this.elapsed);$('#result-kills').textContent=this.kills;$('#result-level').textContent=this.level;$('#result-coins').textContent=`+${coins}`;$('#revive-btn').classList.toggle('hidden',victory||this.reviveUsed);$('#double-btn').classList.toggle('hidden',this.doubleUsed);$('#gameover').classList.add('screen--visible');}

  async tryRevive(){if(this.reviveUsed)return;const ok=await this.bridge.showRewarded();if(!ok){this.toast('Реклама сейчас недоступна');return;}this.reviveUsed=true;this.player.hp=this.player.maxHp*.62;this.player.invuln=2.5;$('#gameover').classList.remove('screen--visible');$('#hud').classList.remove('hidden');if(this.lowPower)$('#mobile-controls').classList.remove('hidden');this.state='playing';document.body.dataset.gameState='playing';this.bridge.startGameplay();}
  async tryDoubleCoins(){if(this.doubleUsed)return;const ok=await this.bridge.showRewarded();if(!ok){this.toast('Реклама сейчас недоступна');return;}this.doubleUsed=true;this.progress.coins+=this.runCoins;await this.save();$('#result-coins').textContent=`+${this.runCoins*2}`;$('#double-btn').classList.add('hidden');this.updateMenu();}
  async restartFromOver(){$('#gameover').classList.remove('screen--visible');await this.startRun();}

  pauseGame(){if(this.state!=='playing')return;this.state='paused';this.bridge.stopGameplay();$('#pause-panel').classList.add('screen--visible');}
  resumeGame(){if(this.state!=='paused')return;$('#pause-panel').classList.remove('screen--visible');this.state='playing';this.clock.getDelta();this.bridge.startGameplay();}
  pauseExternal(){if(this.state==='playing'){this.externalPaused=true;this.bridge.stopGameplay();this.state='externalPaused';}}
  resumeExternal(){if(this.state==='externalPaused'&&this.externalPaused){this.externalPaused=false;this.state='playing';this.clock.getDelta();this.bridge.startGameplay();}}
  async leaveToMenu(){this.bridge.stopGameplay();this.clearWorld();$('#gameover').classList.remove('screen--visible');$('#pause-panel').classList.remove('screen--visible');$('#hud').classList.add('hidden');$('#mobile-controls').classList.add('hidden');this.state='menu';document.body.dataset.gameState='menu';$('#menu').classList.add('screen--visible');await this.buildMenuPreview();this.updateMenu();}

  updateHUD(){if(!this.player)return;const remain=this.currentMode().id==='endless'?this.elapsed:Math.max(0,this.currentMode().seconds-this.elapsed);$('#time').textContent=this.currentMode().id==='endless'?fmtTime(this.elapsed):fmtTime(remain);$('#level').textContent=this.level;$('#kills').textContent=this.kills;$('#run-coins').textContent=Math.floor(this.runCoins);$('#hp-fill').style.width=`${clamp(this.player.hp/this.player.maxHp,0,1)*100}%`;$('#hp-text').textContent=`${Math.ceil(Math.max(0,this.player.hp))} / ${Math.ceil(this.player.maxHp)}`;$('#xp-fill').style.width=`${clamp(this.xp/this.nextXp,0,1)*100}%`;$('#xp-text').textContent=`${Math.floor(this.xp)} / ${this.nextXp}`;document.body.dataset.qaKills=String(this.kills);document.body.dataset.qaProjectiles=String(this.projectiles.length);}

  updateCamera(dt){if(!this.player)return;const pos=this.player.root.position,target=new THREE.Vector3(pos.x,pos.y+(this.lowPower?9.7:7.4),pos.z+(this.lowPower?12.2:9.8)),look=new THREE.Vector3(pos.x,pos.y+1.15,pos.z-2.9);this.camera.position.lerp(target,1-Math.exp(-dt*7));const q=new THREE.Quaternion();const m=new THREE.Matrix4().lookAt(this.camera.position,look,new THREE.Vector3(0,1,0));q.setFromRotationMatrix(m);this.camera.quaternion.slerp(q,1-Math.exp(-dt*9));}
  vibrate(ms){if(this.settings.vibration&&navigator.vibrate)navigator.vibrate(ms);}

  animate(){
    requestAnimationFrame(()=>this.animate());const dt=Math.min(.033,this.clock.getDelta()||.016);
    if(this.menuPreview){this.menuPreview.mixer.update(dt);this.menuPreview.root.rotation.y+=dt*.12;}
    if(this.state==='playing'){
      this.elapsed+=dt;this.updatePlayer(dt);this.updateSpawning(dt);this.updateEnemies(dt);this.updateBoss(dt);this.updateProjectiles(dt);this.updateEnemyProjectiles(dt);this.updatePickups(dt);this.updateEffects(dt);this.updateCamera(dt);this.updateHUD();
      // boss death detection is separate because boss is also in enemies list
      if(this.boss?.dead&&!this.runWon){this.runWon=true;setTimeout(()=>this.endRun(true),350);}
      if(this.input.consumePause())this.pauseGame();
    }else if(this.state==='paused'&&this.input.consumePause())this.resumeGame();
    this.renderer.render(this.scene,this.camera);
  }

  resize(){const w=innerWidth,h=innerHeight;this.renderer.setSize(w,h,false);this.camera.aspect=w/h;this.camera.updateProjectionMatrix();}
  async requestReview(){const ok=await this.bridge.requestReview();if(ok)this.toast('Спасибо за оценку!');}
  async checkReview(){if((this.progress.wins||0)>=2&&await this.bridge.canReview())$('#review-btn').classList.remove('hidden');}
}

const boot=async()=>{
  const bridge=new YandexBridge();await bridge.init();const game=new MerryMayhem3D(bridge);window.__merryMayhem=game;
  try{const progress=await bridge.loadProgress();await game.load(progress);}catch(err){console.error(err);$('#loading-text').textContent='ОШИБКА ЗАГРУЗКИ';}
};
boot();
