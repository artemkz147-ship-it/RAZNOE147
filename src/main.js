import './style.css';
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { clone as cloneSkinned } from 'three/addons/utils/SkeletonUtils.js';
import { YandexBridge } from './yandex.js';
import { AudioDirector } from './audio.js';
import { I18N, UPGRADE_POOL, META_UPGRADES, ENEMY_DEFS, ASSET_URLS, metaCost } from './data.js';

const $ = s => document.querySelector(s);
const $$ = s => [...document.querySelectorAll(s)];
const clamp = THREE.MathUtils.clamp;
const TAU = Math.PI * 2;
const qs = new URLSearchParams(location.search);
const DEBUG_FAST = qs.get('debug-fast') === '1';
const AUTO_START = qs.get('autostart') === '1';

class Input {
  constructor() {
    this.keys = new Set();
    this.joy = new THREE.Vector2();
    this.dashQueued = false;
    this.pauseQueued = false;
    addEventListener('keydown', e => {
      if (['ArrowUp','ArrowDown','ArrowLeft','ArrowRight','Space'].includes(e.code)) e.preventDefault();
      this.keys.add(e.code);
      if (e.code === 'Space' && !e.repeat) this.dashQueued = true;
      if (e.code === 'Escape' && !e.repeat) this.pauseQueued = true;
    }, { passive:false });
    addEventListener('keyup', e => this.keys.delete(e.code));
    this.setupJoystick();
  }
  setupJoystick() {
    const base = $('#joystick'), stick = $('#stick');
    let pid = null;
    const update = e => {
      const r = base.getBoundingClientRect(), cx = r.left + r.width / 2, cy = r.top + r.height / 2, max = r.width * .31;
      let dx = e.clientX - cx, dy = e.clientY - cy;
      const len = Math.hypot(dx, dy) || 1;
      if (len > max) { dx = dx / len * max; dy = dy / len * max; }
      this.joy.set(dx / max, -dy / max);
      stick.style.transform = `translate(${dx}px,${dy}px)`;
    };
    base.addEventListener('pointerdown', e => { pid = e.pointerId; base.setPointerCapture(pid); update(e); });
    base.addEventListener('pointermove', e => { if (e.pointerId === pid) update(e); });
    const end = e => { if (e.pointerId !== pid) return; pid = null; this.joy.set(0,0); stick.style.transform = 'translate(0,0)'; };
    base.addEventListener('pointerup', end); base.addEventListener('pointercancel', end);
    $('#dash-btn').addEventListener('pointerdown', e => { e.preventDefault(); this.dashQueued = true; });
  }
  vector() {
    const v = new THREE.Vector2(this.joy.x, this.joy.y);
    if (this.keys.has('KeyA') || this.keys.has('ArrowLeft')) v.x -= 1;
    if (this.keys.has('KeyD') || this.keys.has('ArrowRight')) v.x += 1;
    if (this.keys.has('KeyW') || this.keys.has('ArrowUp')) v.y += 1;
    if (this.keys.has('KeyS') || this.keys.has('ArrowDown')) v.y -= 1;
    if (v.lengthSq() > 1) v.normalize();
    return v;
  }
  consumeDash(){ const q=this.dashQueued; this.dashQueued=false; return q; }
  consumePause(){ const q=this.pauseQueued; this.pauseQueued=false; return q; }
}

class Game {
  constructor(bridge) {
    this.bridge = bridge;
    this.canvas = $('#game');
    this.renderer = new THREE.WebGLRenderer({ canvas:this.canvas, antialias:true, powerPreference:'high-performance', alpha:false });
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.08;
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x0b1114);
    this.scene.fog = new THREE.FogExp2(0x0b1114, .0205);
    this.camera = new THREE.PerspectiveCamera(50, 1, .1, 140);
    this.camera.position.set(0, 10.6, 11.8);
    this.clock = new THREE.Clock();
    this.loader = new GLTFLoader();
    this.input = new Input();
    this.assets = {};
    this.state = 'loading';
    this.progress = {};
    this.settings = { music:true, sfx:true, vibration:true, quality:'auto' };
    this.audio = new AudioDirector(this.settings);
    this.enemies = []; this.pickups = []; this.projectiles = []; this.orbitBlades = []; this.obstacles = [];
    this.spawnClock = 0; this.elapsed = 0; this.kills = 0; this.level = 1; this.xp = 0; this.nextXp = 7;
    this.lastAttack = 0; this.lastBolt = 0; this.bossStage = 0; this.boss = null; this.reviveUsed = false; this.doubleUsedRun = false;
    this.runWon = false; this.cameraKick = 0; this.runSoulsRaw = 0; this.rewardGranted = 0; this.runRewardTarget = 0;
    this.lowPower = /Android|iPhone|iPad|iPod/i.test(navigator.userAgent) || matchMedia('(pointer:coarse)').matches || this.bridge.deviceType === 'mobile';
    this.setupScene(); this.setupUI(); this.resize();
    addEventListener('resize', () => this.resize());
    addEventListener('contextmenu', e => e.preventDefault());
    document.addEventListener('visibilitychange', () => document.hidden ? this.pauseExternal() : this.resumeExternal());
    this.bridge.onPause = () => this.pauseExternal();
    this.bridge.onResume = () => this.resumeExternal();
    this.bridge.onAdOpen = () => this.audio.suspend();
    this.bridge.onAdClose = () => this.audio.resume();
  }

  setupScene() {
    this.hemi = new THREE.HemisphereLight(0x9bc9c5, 0x171210, 1.25); this.scene.add(this.hemi);
    this.sun = new THREE.DirectionalLight(0xffe1ae, 2.15); this.sun.position.set(-9,15,7); this.sun.castShadow = true; this.scene.add(this.sun);
    this.rim = new THREE.DirectionalLight(0x497f94, .85); this.rim.position.set(10,7,-10); this.scene.add(this.rim);
    this.warm = new THREE.PointLight(0xd78945, 1.1, 18, 2); this.warm.position.set(0,3,-3); this.scene.add(this.warm);
  }

  setupUI() {
    $('#start-btn').onclick = () => this.startRun();
    $('#how-btn').onclick = () => this.openOverlay('controls-panel');
    $('#close-controls').onclick = () => this.closeOverlay('controls-panel');
    $('#settings-btn').onclick = () => this.openSettings();
    $('#pause-settings-btn').onclick = () => this.openSettings();
    $('#close-settings').onclick = () => this.closeOverlay('settings-panel');
    $('#altar-btn').onclick = () => this.openAltar();
    $('#close-altar').onclick = () => this.closeOverlay('altar-panel');
    $('#pause-btn').onclick = () => this.pauseGame();
    $('#resume-btn').onclick = () => this.resumeGame();
    $('#quit-btn').onclick = () => this.leaveToMenu();
    $('#restart-btn').onclick = () => this.restartFromOver();
    $('#result-menu-btn').onclick = () => this.leaveToMenu();
    $('#revive-btn').onclick = () => this.tryRevive();
    $('#double-btn').onclick = () => this.tryDoubleSouls();
    $('#reroll-btn').onclick = () => this.tryReroll();
    $('#review-btn').onclick = () => this.requestReview();
    $('#fullscreen-btn').onclick = () => this.bridge.requestFullscreen();
    $('#setting-music').onclick = () => this.toggleSetting('music');
    $('#setting-sfx').onclick = () => this.toggleSetting('sfx');
    $('#setting-vibration').onclick = () => this.toggleSetting('vibration');
    $('#setting-quality').onclick = () => this.cycleQuality();
  }

  t(k){ return I18N[this.lang]?.[k] ?? I18N.en[k] ?? k; }

  localize(lang) {
    this.lang = ['ru','be','uk','kk'].includes(lang) ? 'ru' : 'en';
    document.documentElement.lang = this.lang;
    $$('[data-i18n]').forEach(el => { const k = el.dataset.i18n; if (this.t(k)) el.textContent = this.t(k); });
  }

  normalizeProgress(raw={}) {
    return {
      bestTime:Number(raw.bestTime)||0,
      bestKills:Number(raw.bestKills)||0,
      runs:Number(raw.runs)||0,
      wins:Number(raw.wins)||0,
      souls:Number(raw.souls)||0,
      meta:{ might:0,vitality:0,agility:0,ward:0,fortune:0,...(raw.meta||{}) },
      settings:{ music:true,sfx:true,vibration:true,quality:'auto',...(raw.settings||{}) },
      tutorialSeen:Boolean(raw.tutorialSeen)
    };
  }

  async load(progress={}) {
    this.progress = this.normalizeProgress(progress);
    this.settings = { ...this.progress.settings };
    this.audio.musicOn = this.settings.music; this.audio.sfxOn = this.settings.sfx;
    this.updateMenuStats(); this.updateSettingsUI(); this.applyQuality();
    const entries = Object.entries(ASSET_URLS); let done = 0;
    for (const [key,url] of entries) {
      this.assets[key] = await this.loader.loadAsync(url);
      this.setLoading(++done / entries.length);
    }
    this.buildEnvironment();
    document.body.dataset.gameReady='true';
    this.state = 'menu'; document.body.dataset.gameState='menu';
    $('#loading').classList.remove('screen--visible'); $('#menu').classList.add('active');
    this.bridge.ready();
    this.checkReview();
    this.animate();
    if (AUTO_START) setTimeout(()=>this.startRun(), 120);
  }

  setLoading(p){ $('#loading-fill').style.width=`${Math.round(p*100)}%`; $('#loading-text').textContent=`${Math.round(p*100)}%`; }

  cloneMaterials(model) {
    model.traverse(o => {
      if (o.isMesh) {
        if (Array.isArray(o.material)) o.material = o.material.map(m=>m.clone());
        else if (o.material) o.material = o.material.clone();
        const mats=Array.isArray(o.material)?o.material:[o.material];
        for(const m of mats){const n=(m?.name||'').toLowerCase();if(m?.color&&n==='armor'){m.color.setHex(0x60747d);m.roughness=.72;m.metalness=.08;}if(m?.color&&n==='boots')m.color.setHex(0x563425);if(m?.color&&n==='skin')m.color.setHex(0xc8925f);}
      }
    });
  }

  prepareVisual(gltf, targetHeight, {shadow=true}={}) {
    const model = cloneSkinned(gltf.scene); this.cloneMaterials(model); model.updateMatrixWorld(true);
    const box = new THREE.Box3().setFromObject(model), size = new THREE.Vector3(); box.getSize(size);
    model.scale.multiplyScalar(targetHeight / Math.max(size.y,.001)); model.updateMatrixWorld(true);
    const b = new THREE.Box3().setFromObject(model); model.position.y -= b.min.y;
    model.traverse(o => { if(o.isMesh){ o.castShadow=shadow; o.receiveShadow=shadow; o.frustumCulled=true; } });
    const root = new THREE.Group(); root.add(model); root.userData.model=model; return root;
  }

  prepareGround(gltf, targetSize) {
    const model = cloneSkinned(gltf.scene); this.cloneMaterials(model); model.updateMatrixWorld(true);
    const box = new THREE.Box3().setFromObject(model), size = new THREE.Vector3(); box.getSize(size);
    model.scale.multiplyScalar(targetSize / Math.max(size.x,size.z,.001)); model.updateMatrixWorld(true);
    const b = new THREE.Box3().setFromObject(model); model.position.y -= b.max.y;
    model.traverse(o => { if(o.isMesh){o.receiveShadow=true;o.castShadow=false;} });
    const root = new THREE.Group(); root.add(model); return root;
  }

  prepareAttachment(gltf, targetHeight) {
    const model = cloneSkinned(gltf.scene); this.cloneMaterials(model); model.updateMatrixWorld(true);
    let b = new THREE.Box3().setFromObject(model), s = new THREE.Vector3(); b.getSize(s);
    model.scale.multiplyScalar(targetHeight / Math.max(s.y,.001)); model.updateMatrixWorld(true);
    b = new THREE.Box3().setFromObject(model); const center = new THREE.Vector3(); b.getCenter(center); model.position.sub(center);
    model.traverse(o=>{if(o.isMesh){o.castShadow=true;o.receiveShadow=true;}});
    const root = new THREE.Group(); root.add(model); return root;
  }

  attachSword(characterRoot) {
    const sword = this.prepareAttachment(this.assets.sword, .92);
    let right = null, fallback = null;
    characterRoot.traverse(o => {
      if (!o.isBone) return;
      const n = o.name.toLowerCase();
      if (!right && (n.includes('palm.r') || n.includes('palm_r') || n.includes('palm r'))) right = o;
      if (!fallback && (n.includes('hand') || n.includes('palm'))) fallback = o;
      if (!right && (n.includes('hand') || n.includes('palm')) && (n.includes('right') || n.includes('.r') || n.includes('_r') || n.endsWith('r'))) right = o;
    });
    const bone = right || fallback;
    if (bone) {
      if (sword.children[0]) sword.children[0].position.set(0,0,0);
      bone.add(sword); sword.position.set(0,0,0); sword.rotation.set(0,0,0); sword.scale.setScalar(.82);
    } else {
      characterRoot.add(sword); sword.position.set(.48,1.02,.12); sword.rotation.set(Math.PI/2,0,-.18);
    }
    return sword;
  }

  buildEnvironment() {
    this.environment = new THREE.Group(); this.scene.add(this.environment);
    const tile = this.prepareGround(this.assets.floor, 7.05);
    for(let x=-4;x<=4;x++) for(let z=-4;z<=4;z++) { const t=tile.clone(true); t.position.set(x*7,0,z*7); t.rotation.y=((x+z)&1)?Math.PI/2:0; this.environment.add(t); }

    const props = [
      ['tree',5.4,-26,-19,.2],['tree',4.8,-18,-27,.9],['tree',5.8,-7,-29,1.7],['tree',4.9,7,-29,2.4],['tree',5.6,19,-25,3.1],['tree',5.1,28,-16,3.8],
      ['tree',5.5,29,-2,4.5],['tree',4.8,28,13,5.2],['tree',5.8,20,25,5.9],['tree',5.0,7,29,.6],['tree',5.3,-8,29,1.3],['tree',5.7,-21,25,2.0],
      ['tree',5.0,-29,15,2.7],['tree',5.5,-30,2,3.4],['deadTree',4.8,-20,-18,1.1],['deadTree',5.3,20,18,4.1],['deadTree',4.5,24,-9,2.2],['deadTree',5.0,-13,24,5.2],
      ['bush',1.25,-17,-15,.2],['bush',1.05,15,-18,1.4],['bush',1.3,18,14,2.6],['bush',1.1,-16,17,3.8],['bush',1.2,2,-24,5.0],['bush',1.15,-24,4,.9],
      ['rock',1.55,-11,-12,.5],['rock',1.15,12,-14,1.7],['rock',1.45,15,8,2.9],['rock',1.25,-15,9,4.1],['rock',1.5,-6,19,5.3],['rock',1.25,8,19,.8],
      ['arch',4.4,0,-25,0],['arch',4.4,25,0,Math.PI/2],['arch',4.4,0,25,Math.PI],['arch',4.4,-25,0,-Math.PI/2],
      ['column',3.7,-7,-5,.1],['column',3.7,7,-5,-.1],['column',3.7,-7,8,.12],['column',3.7,7,8,-.12],
      ['chest',1.15,-18,0,.8],['chest',1.15,18,2,3.7],['torch',2.5,-4,-22,0],['torch',2.5,4,-22,0],['torch',2.5,-4,22,Math.PI],['torch',2.5,4,22,Math.PI]
    ];
    for(const [asset,h,x,z,r] of props){ const p=this.prepareVisual(this.assets[asset],h,{shadow:true}); p.position.set(x,0,z); p.rotation.y=r; this.environment.add(p); }
    this.obstacles = [{x:-7,z:-5,r:1.15},{x:7,z:-5,r:1.15},{x:-7,z:8,r:1.15},{x:7,z:8,r:1.15}];

    const fires=[[-4,-21.2],[4,-21.2],[-4,21.2],[4,21.2]];
    for(const [x,z] of fires){ const f=this.prepareVisual(this.assets.fire,.95,{shadow:false}); f.position.set(x,.06,z); this.environment.add(f); }

    this.menuHero = { root:this.prepareVisual(this.assets.hero,2.35), mixer:null, action:null };
    this.menuHero.root.position.set(0,0,2.2); this.menuHero.root.rotation.y=-.45; this.scene.add(this.menuHero.root); this.attachSword(this.menuHero.root);
    this.menuHero.mixer = new THREE.AnimationMixer(this.menuHero.root); const idle=this.findClip(this.assets.hero,['idle']); if(idle){this.menuHero.action=this.menuHero.mixer.clipAction(idle);this.menuHero.action.play();this.menuHero.action.time=.55;this.menuHero.mixer.update(0);}
  }

  findClip(gltf, words) {
    const clips=gltf.animations||[];
    for(const word of words){ const w=word.toLowerCase(); const hit=clips.find(c=>c.name.toLowerCase().includes(w)); if(hit)return hit; }
    return clips[0]||null;
  }

  playPlayerAnim(kind, loop=true, fade=.1) {
    const map={ idle:['idle'], run:['run_swordright','run','walk'], attack:['swordattack','attack','slash','hit'], death:['death','die'] };
    const clip=this.findClip(this.assets.hero,map[kind]||[kind]); if(!clip)return;
    const next=this.player.mixer.clipAction(clip); if(this.player.action===next&&next.isRunning())return;
    next.reset().setLoop(loop?THREE.LoopRepeat:THREE.LoopOnce,loop?Infinity:1); next.clampWhenFinished=!loop;
    if(this.player.action&&this.player.action!==next)this.player.action.fadeOut(fade); next.fadeIn(fade).play(); this.player.action=next;
  }

  resetRunState() {
    if(this.player?.root)this.scene.remove(this.player.root);
    [...this.enemies,...this.pickups,...this.projectiles,...this.orbitBlades].forEach(o=>this.scene.remove(o.root));
    this.enemies=[];this.pickups=[];this.projectiles=[];this.orbitBlades=[];
    this.elapsed=0;this.kills=0;this.level=1;this.xp=0;this.nextXp=7;this.spawnClock=.25;this.lastAttack=0;this.lastBolt=0;this.bossStage=0;this.boss=null;
    this.reviveUsed=false;this.doubleUsedRun=false;this.runWon=false;this.bossEnraged=false;this.runSoulsRaw=0;this.rewardGranted=0;this.runRewardTarget=0;this.upgradeRanks={};
    const m=this.progress.meta;
    this.player={
      root:this.prepareVisual(this.assets.hero,1.86),mixer:null,action:null,pos:new THREE.Vector3(),
      hp:100*(1+m.vitality*.06),maxHp:100*(1+m.vitality*.06),speed:5.15*(1+m.agility*.03),damage:24*(1+m.might*.05),attackDelay:.69,attackRange:2.35,cleave:1,
      damageTaken:Math.max(.68,1-m.ward*.02),crit:.06,dashCooldown:2.1,dashTimer:0,dashTime:0,invuln:0,facing:new THREE.Vector3(0,0,1),
      pickupRadius:4.1,regen:0,boltRank:0,boltDamage:18,boltDelay:1.75,orbitRank:0,soulGain:1,attackAnimUntil:0
    };
    this.scene.add(this.player.root); this.attachSword(this.player.root); this.player.mixer=new THREE.AnimationMixer(this.player.root); this.playPlayerAnim('idle',true,.12); if(this.player.action){this.player.action.time=.55;this.player.mixer.update(0);} if(this.player.action){this.player.action.time=.55;this.player.mixer.update(0);}
    this.updateHud();
  }

  async startRun() {
    await this.audio.resume(); this.audio.startMusic();
    $('#menu').classList.remove('active'); $('#gameover').classList.remove('active'); $('#upgrade').classList.remove('active'); $('#pause-panel').classList.remove('active');
    this.closeOverlay('settings-panel'); this.closeOverlay('controls-panel'); this.closeOverlay('altar-panel');
    $('#hud').classList.remove('hidden'); this.showMobileControls(true); if(this.menuHero)this.menuHero.root.visible=false;
    this.resetRunState(); this.state='playing'; document.body.dataset.gameState='playing'; this.clock.getDelta(); this.bridge.startGameplay();
    this.progress.runs++; this.saveProgress();
    if(!this.progress.tutorialSeen){ this.progress.tutorialSeen=true; this.saveProgress(); setTimeout(()=>this.toast(this.t('firstMove')),650); setTimeout(()=>this.toast(this.t('firstDash')),3300); }
  }

  showMobileControls(show){ if(this.lowPower&&show)$('#mobile-controls').classList.remove('hidden'); else $('#mobile-controls').classList.add('hidden'); }

  pauseGame(){ if(this.state!=='playing')return; this.state='paused';document.body.dataset.gameState='paused';this.bridge.stopGameplay();this.showMobileControls(false);$('#pause-panel').classList.add('active'); }
  resumeGame(){ if(this.state!=='paused')return; $('#pause-panel').classList.remove('active');this.state='playing';document.body.dataset.gameState='playing';this.clock.getDelta();this.bridge.startGameplay();this.showMobileControls(true); }
  pauseExternal(){ this.audio.suspend(); if(this.state==='playing'){this.state='paused-external';this.bridge.stopGameplay();this.showMobileControls(false);} }
  resumeExternal(){ this.audio.resume(); if(this.state==='paused-external'){this.state='playing';this.clock.getDelta();this.bridge.startGameplay();this.showMobileControls(true);} }

  clearRunVisuals(){
    if(this.player?.root){this.scene.remove(this.player.root);this.player=null;}
    for(const arr of [this.enemies,this.pickups,this.projectiles,this.orbitBlades]){for(const o of arr)this.scene.remove(o.root);arr.length=0;}
    this.boss=null;
  }

  leaveToMenu(){
    this.bridge.stopGameplay(); this.clearRunVisuals(); this.state='menu';document.body.dataset.gameState='menu'; $('#hud').classList.add('hidden');this.showMobileControls(false);$('#gameover').classList.remove('active');$('#pause-panel').classList.remove('active');$('#upgrade').classList.remove('active');
    $('#menu').classList.add('active'); if(this.menuHero)this.menuHero.root.visible=true; this.updateMenuStats(); this.checkReview();
  }

  openOverlay(id){ $('#'+id).classList.add('active'); }
  closeOverlay(id){ $('#'+id)?.classList.remove('active'); }
  openSettings(){ this.updateSettingsUI(); this.openOverlay('settings-panel'); }

  applyQuality(){
    const q=this.settings.quality; const high=q==='high'||(q==='auto'&&!this.lowPower); const ratio=high?Math.min(devicePixelRatio,1.65):Math.min(devicePixelRatio,1.15);
    this.renderer.setPixelRatio(ratio); this.renderer.shadowMap.enabled=true; this.renderer.shadowMap.type=THREE.PCFSoftShadowMap;
    this.sun.shadow.mapSize.set(high?2048:1024,high?2048:1024);this.sun.shadow.camera.left=-22;this.sun.shadow.camera.right=22;this.sun.shadow.camera.top=22;this.sun.shadow.camera.bottom=-22;this.sun.shadow.bias=-.00035;
    this.highQuality=high; this.resize();
  }

  toggleSetting(k){ this.settings[k]=!this.settings[k];this.progress.settings={...this.settings};if(k==='music')this.audio.setMusic(this.settings.music);if(k==='sfx')this.audio.setSfx(this.settings.sfx);this.updateSettingsUI();this.saveProgress(); }
  cycleQuality(){ const order=['auto','high','low'];this.settings.quality=order[(order.indexOf(this.settings.quality)+1)%order.length];this.progress.settings={...this.settings};this.applyQuality();this.updateSettingsUI();this.saveProgress(); }
  updateSettingsUI(){
    $('#music-value').textContent=this.settings.music?'ON':'OFF';$('#sfx-value').textContent=this.settings.sfx?'ON':'OFF';$('#vibration-value').textContent=this.settings.vibration?'ON':'OFF';
    const q=this.settings.quality;$('#quality-value').textContent=q==='high'?this.t('qualityHigh'):q==='low'?this.t('qualityLow'):this.t('qualityAuto');
  }
  vibrate(pattern=20){ if(this.settings.vibration&&navigator.vibrate)try{navigator.vibrate(pattern);}catch(_){} }

  openAltar(){ this.renderAltar();this.openOverlay('altar-panel'); }
  renderAltar(){
    $('#altar-souls').textContent=Math.floor(this.progress.souls);
    const wrap=$('#meta-cards');wrap.innerHTML='';
    for(const d of META_UPGRADES){
      const rank=clamp(Number(this.progress.meta[d.id])||0,0,d.max),cost=rank>=d.max?0:metaCost(d,rank); const card=document.createElement('div');card.className='meta-card';
      card.innerHTML=`<div class="meta-icon">${d.icon}</div><div class="meta-copy"><b>${d.name[this.lang]}</b><span>${d.desc[this.lang]}</span><small>${this.t('rank')} ${rank}/${d.max}</small></div><button ${rank>=d.max?'disabled':''}>${rank>=d.max?this.t('max'):`${this.t('buy')} · ${cost} ◇`}</button>`;
      card.querySelector('button').onclick=()=>this.buyMeta(d);wrap.appendChild(card);
    }
  }
  buyMeta(d){ const rank=this.progress.meta[d.id]||0;if(rank>=d.max)return;const cost=metaCost(d,rank);if(this.progress.souls<cost){this.toast(this.t('needSouls'));return;}this.progress.souls-=cost;this.progress.meta[d.id]=rank+1;this.audio.level();this.vibrate([20,35,20]);this.saveProgress();this.renderAltar();this.updateMenuStats();this.toast(this.t('upgraded')); }

  updateMenuStats(){ $('#best-time').textContent=this.format(this.progress.bestTime||0);$('#best-kills').textContent=this.progress.bestKills||0;$('#menu-souls').textContent=Math.floor(this.progress.souls||0); }

  async checkReview(){ const can=await this.bridge.canReview();$('#review-btn').classList.toggle('hidden',!can); }
  async requestReview(){ const ok=await this.bridge.requestReview();if(ok)this.toast(this.t('reviewThanks'));$('#review-btn').classList.add('hidden'); }

  resolveObstacle(pos, radius=.55){
    for(const o of this.obstacles){const dx=pos.x-o.x,dz=pos.z-o.z,d=Math.hypot(dx,dz),min=o.r+radius;if(d<min&&d>.001){pos.x=o.x+dx/d*min;pos.z=o.z+dz/d*min;}}
  }

  movePlayer(dt){
    const p=this.player;p.invuln=Math.max(0,p.invuln-dt);p.dashTimer=Math.max(0,p.dashTimer-dt);
    const v=this.input.vector();if(this.input.consumeDash()&&p.dashTimer<=0&&v.lengthSq()>.05){p.dashTimer=p.dashCooldown;p.dashTime=.18;p.invuln=Math.max(p.invuln,.30);this.audio.dash();this.vibrate(14);}
    if(p.dashTime>0)p.dashTime-=dt;const mul=p.dashTime>0?2.7:1;
    if(v.lengthSq()>.02){const dir=new THREE.Vector3(v.x,0,-v.y).normalize();p.facing.lerp(dir,Math.min(1,dt*11)).normalize();p.pos.addScaledVector(dir,p.speed*mul*dt);if(p.pos.length()>28.2)p.pos.normalize().multiplyScalar(28.2);this.resolveObstacle(p.pos);p.root.rotation.y=Math.atan2(p.facing.x,p.facing.z);if(this.elapsed>=p.attackAnimUntil)this.playPlayerAnim('run');}
    else if(this.elapsed>=p.attackAnimUntil)this.playPlayerAnim('idle');
    p.root.position.copy(p.pos);
    if(p.regen>0&&p.hp>0&&p.hp<p.maxHp)p.hp=Math.min(p.maxHp,p.hp+p.regen*dt);
  }

  enemyScale(){ return 1 + this.elapsed/620; }

  spawnEnemy(type='skeleton', elite=false, boss=false, at=null) {
    const isBoss=boss||type==='boss';if(isBoss)type='boss';const d=ENEMY_DEFS[type],angle=Math.random()*TAU,radius=at?0:25+Math.random()*3;
    const pos=at?at.clone():new THREE.Vector3(Math.sin(angle)*radius,0,Math.cos(angle)*radius),root=this.prepareVisual(this.assets[d.asset],isBoss?d.height:d.height*(elite?1.24:1));
    pos.y=d.float||0;root.position.copy(pos);this.scene.add(root);
    const mixer=new THREE.AnimationMixer(root),clip=this.findClip(this.assets[d.asset],['running','run','walk','idle']),action=clip?mixer.clipAction(clip):null;action?.play();
    const sc=this.enemyScale(),e={id:globalThis.crypto?.randomUUID?.()||`${Date.now()}-${Math.random()}`,root,pos,mixer,action,type,elite,boss:isBoss,hp:d.hp*sc*(elite?2.25:1),maxHp:d.hp*sc*(elite?2.25:1),speed:d.speed*(1+this.elapsed/1900)*(elite?1.08:1),damage:d.damage*(1+this.elapsed/900)*(elite?1.25:1),attackTimer:.4,shootTimer:1.2,summonTimer:5.5,dead:false,def:d,baseGltf:this.assets[d.asset],lastOrbitHit:-9};
    if(isBoss){e.hp=d.hp*(1+this.elapsed/1400);e.maxHp=e.hp;this.boss=e;this.bossStage=3;this.audio.boss();this.vibrate([80,60,80]);}
    this.enemies.push(e); return e;
  }

  spawnLogic(dt){
    this.spawnClock-=dt;if(this.spawnClock<=0){
      this.spawnClock=Math.max(.29,.98-this.elapsed/900);const cap=this.highQuality?56:34;
      if(this.enemies.length<cap){const t=this.elapsed;let type='skeleton',r=Math.random();if(t>190&&r<.22)type='slime';if(t>300&&r<.18)type='bat';if(t>410&&r<.08)type='dragon';this.spawnEnemy(type);}
    }
    const t=this.elapsed;
    if(t>(DEBUG_FAST?12:210)&&this.bossStage<1){this.bossStage=1;this.spawnEnemy('skeleton',true);this.toast(this.t('elite'));}
    if(t>(DEBUG_FAST?24:410)&&this.bossStage<2){this.bossStage=2;this.spawnEnemy('slime',true);this.spawnEnemy('bat',true);this.toast(this.t('elite'));}
    if(t>(DEBUG_FAST?38:510)&&this.bossStage<3){$('#boss-warning').classList.remove('hidden');setTimeout(()=>$('#boss-warning').classList.add('hidden'),3200);this.spawnEnemy('boss');}
    if(this.boss&&t>(DEBUG_FAST?58:650)&&!this.bossEnraged){this.bossEnraged=true;this.toast(this.t('bossEnrage'));this.boss.speed*=1.16;this.boss.damage*=1.22;this.boss.shootTimer=.2;}
  }

  updateEnemies(dt){
    const p=this.player;
    for(const e of this.enemies){
      if(e.dead)continue;e.mixer.update(dt);e.attackTimer=Math.max(0,e.attackTimer-dt);e.shootTimer=Math.max(0,e.shootTimer-dt);e.summonTimer=Math.max(0,e.summonTimer-dt);
      const flatPos=e.pos.clone();flatPos.y=0;const to=p.pos.clone().sub(flatPos),dist=to.length();if(dist>.001)to.multiplyScalar(1/dist);
      if(e.def.ranged){
        const desired=e.boss?8.2:6.3;
        if(dist>desired+1.1){e.pos.x+=to.x*e.speed*dt;e.pos.z+=to.z*e.speed*dt;}
        else if(dist<desired-1.4){e.pos.x-=to.x*e.speed*.7*dt;e.pos.z-=to.z*e.speed*.7*dt;}
        if(e.shootTimer<=0){const hpRatio=e.hp/e.maxHp,phase=e.boss?(hpRatio<.32?3:hpRatio<.66?2:1):1;e.shootTimer=e.boss?Math.max(.58,1.42-phase*.18):2.15;const count=e.boss?phase:1;for(let i=0;i<count;i++)this.spawnEnemyProjectile(e,(i-(count-1)/2)*.18);}
        if(e.boss&&e.summonTimer<=0){e.summonTimer=e.hp/e.maxHp<.5?5.2:7.1;const n=e.hp/e.maxHp<.35?4:2;for(let i=0;i<n;i++){const a=TAU*i/n+Math.random()*.3,sp=e.pos.clone();sp.x+=Math.sin(a)*3.3;sp.z+=Math.cos(a)*3.3;sp.y=0;this.spawnEnemy(i%2?'bat':'skeleton',false,false,sp);}}
      } else {
        if(dist>e.def.contact){e.pos.x+=to.x*e.speed*dt;e.pos.z+=to.z*e.speed*dt;}
        else if(e.attackTimer<=0){e.attackTimer=e.elite?.72:.9;this.hurtPlayer(e.damage);}
      }
      const ground=new THREE.Vector3(e.pos.x,0,e.pos.z);this.resolveObstacle(ground,e.boss?1.2:.5);e.pos.x=ground.x;e.pos.z=ground.z;e.root.position.copy(e.pos);e.root.rotation.y=Math.atan2(to.x,to.z);
    }
  }

  hurtPlayer(amount){const p=this.player;if(p.invuln>0||this.state!=='playing')return;p.hp-=amount*p.damageTaken;p.invuln=.36;this.cameraKick=.25;this.audio.hurt();this.vibrate(28);$('#damage-flash').classList.add('show');setTimeout(()=>$('#damage-flash').classList.remove('show'),90);if(p.hp<=0){p.hp=0;this.gameOver(false);} }

  spawnEnemyProjectile(e,spread=0){
    const root=this.prepareVisual(this.assets.gem,e.boss?.42:.28,{shadow:false}),start=new THREE.Vector3(e.pos.x,e.boss?1.5:1.0,e.pos.z),target=this.player.pos.clone();target.y=.65;
    const dir=target.sub(start).normalize();const c=Math.cos(spread),s=Math.sin(spread),x=dir.x*c-dir.z*s,z=dir.x*s+dir.z*c;dir.x=x;dir.z=z;
    root.position.copy(start);this.scene.add(root);this.projectiles.push({root,pos:start,vel:dir.multiplyScalar(e.boss?7.2:6.0),ttl:4,enemy:true,damage:e.damage*(e.boss?.78:.7),target:null});
  }

  spawnPlayerBolt(){
    if(this.player.boltRank<=0||this.elapsed-this.lastBolt<this.player.boltDelay)return;
    const candidates=this.enemies.filter(e=>!e.dead).map(e=>({e,d:e.pos.distanceToSquared(this.player.pos)})).filter(x=>x.d<170).sort((a,b)=>a.d-b.d);
    if(!candidates.length)return;this.lastBolt=this.elapsed;const n=Math.min(1+Math.floor((this.player.boltRank-1)/2),3);
    for(let i=0;i<n&&i<candidates.length;i++){const e=candidates[i].e,root=this.prepareVisual(this.assets.gem,.24,{shadow:false}),start=this.player.pos.clone();start.y=.8;const target=e.pos.clone();target.y=.6;const dir=target.sub(start).normalize();root.position.copy(start);this.scene.add(root);this.projectiles.push({root,pos:start,vel:dir.multiplyScalar(9.5),ttl:2.2,enemy:false,damage:this.player.boltDamage,target:e});}
  }

  updateProjectiles(dt){
    for(let i=this.projectiles.length-1;i>=0;i--){const o=this.projectiles[i];o.ttl-=dt;if(o.ttl<=0){this.scene.remove(o.root);this.projectiles.splice(i,1);continue;}
      if(!o.enemy&&o.target&&!o.target.dead){const target=o.target.pos.clone();target.y=.55;const desired=target.sub(o.pos).normalize().multiplyScalar(9.5);o.vel.lerp(desired,Math.min(1,dt*4));}
      o.pos.addScaledVector(o.vel,dt);o.root.position.copy(o.pos);o.root.rotation.x+=dt*5;o.root.rotation.y+=dt*7;
      if(o.enemy){const dx=o.pos.x-this.player.pos.x,dz=o.pos.z-this.player.pos.z;if(dx*dx+dz*dz<.55){this.hurtPlayer(o.damage);this.scene.remove(o.root);this.projectiles.splice(i,1);}}
      else {let hit=null;for(const e of this.enemies){if(e.dead)continue;const dx=o.pos.x-e.pos.x,dz=o.pos.z-e.pos.z;if(dx*dx+dz*dz<(e.boss?1.8:.7)){hit=e;break;}}if(hit){this.damageEnemy(hit,o.damage);this.scene.remove(o.root);this.projectiles.splice(i,1);}}
    }
  }

  autoAttack(){
    if(this.elapsed-this.lastAttack<this.player.attackDelay)return;const p=this.player,targets=this.enemies.filter(e=>!e.dead).map(e=>({e,d:e.pos.distanceToSquared(p.pos)})).filter(x=>x.d<=p.attackRange*p.attackRange).sort((a,b)=>a.d-b.d).slice(0,p.cleave);if(!targets.length)return;
    this.lastAttack=this.elapsed;const aim=targets[0].e.pos.clone().sub(p.pos);aim.y=0;if(aim.lengthSq()>.01){aim.normalize();p.facing.copy(aim);p.root.rotation.y=Math.atan2(aim.x,aim.z);}p.attackAnimUntil=this.elapsed+Math.min(.44,p.attackDelay*.72);this.playPlayerAnim('attack',false,.05);this.audio.attack();
    for(const {e} of targets)this.damageEnemy(e,p.damage*(Math.random()<p.crit?2:1));
  }

  syncOrbitBlades(){
    while(this.orbitBlades.length<this.player.orbitRank){const root=this.prepareAttachment(this.assets.sword,.82);this.scene.add(root);this.orbitBlades.push({root,offset:this.orbitBlades.length/Math.max(1,this.player.orbitRank)*TAU});}
    this.orbitBlades.forEach((b,i)=>b.offset=i/Math.max(1,this.orbitBlades.length)*TAU);
  }

  updateOrbitBlades(dt){
    if(!this.orbitBlades.length)return;const n=this.orbitBlades.length;
    for(let i=0;i<n;i++){const b=this.orbitBlades[i],a=this.elapsed*2.25+i/n*TAU,r=2.05;b.root.position.set(this.player.pos.x+Math.cos(a)*r,1.05,this.player.pos.z+Math.sin(a)*r);b.root.rotation.set(0,-a,Math.PI/2);}
    for(const e of this.enemies){if(e.dead||this.elapsed-e.lastOrbitHit<.38)continue;for(const b of this.orbitBlades){const dx=b.root.position.x-e.pos.x,dz=b.root.position.z-e.pos.z;if(dx*dx+dz*dz<(e.boss?2.2:.78)){e.lastOrbitHit=this.elapsed;this.damageEnemy(e,this.player.damage*.48);break;}}}
  }

  damageEnemy(e,amount){if(e.dead)return;e.hp-=amount;e.root.scale.multiplyScalar(1.035);setTimeout(()=>{if(e.root.parent)e.root.scale.multiplyScalar(1/1.035);},65);this.audio.hit();if(e.hp<=0)this.killEnemy(e);}

  killEnemy(e){
    if(e.dead)return;e.dead=true;this.kills++;this.runSoulsRaw+=e.def.souls*(e.elite?2:1);this.audio.kill();
    const clip=this.findClip(e.baseGltf,['death','die']);if(clip){e.action?.stop();e.action=e.mixer.clipAction(clip);e.action.setLoop(THREE.LoopOnce,1);e.action.clampWhenFinished=true;e.action.play();}
    this.spawnPickup(e.pos,e.def.xp*(e.elite?3:1));setTimeout(()=>{this.scene.remove(e.root);const i=this.enemies.indexOf(e);if(i>=0)this.enemies.splice(i,1);},e.boss?1200:650);
    if(e.boss){this.boss=null;this.victory();}
  }

  spawnPickup(pos,value){const root=this.prepareVisual(this.assets.gem,value>8?.7:value>2?.48:.32,{shadow:false});const p=pos.clone();p.y=.22;root.position.copy(p);this.scene.add(root);this.pickups.push({root,pos:p,value,t:Math.random()*TAU});}

  updatePickups(dt){
    for(let i=this.pickups.length-1;i>=0;i--){const o=this.pickups[i];o.t+=dt*2.3;o.root.rotation.y+=dt*2.4;o.root.position.y=.28+Math.sin(o.t)*.08;const flat=o.pos.clone();flat.y=0;const d=flat.distanceTo(this.player.pos);if(d<this.player.pickupRadius){const dir=this.player.pos.clone().sub(flat).normalize();o.pos.x+=dir.x*(7+(this.player.pickupRadius-d)*4)*dt;o.pos.z+=dir.z*(7+(this.player.pickupRadius-d)*4)*dt;o.root.position.x=o.pos.x;o.root.position.z=o.pos.z;}if(d<.7){this.gainXp(o.value);this.audio.pickup();this.scene.remove(o.root);this.pickups.splice(i,1);}}
  }

  gainXp(v){this.xp+=v;this.checkLevelUp();this.updateHud();}
  checkLevelUp(){if(this.state!=='playing'||this.xp<this.nextXp)return;this.xp-=this.nextXp;this.level++;this.nextXp=Math.floor(this.nextXp*1.25+3);this.showUpgrade();}

  showUpgrade(reroll=false){
    if(!reroll){this.state='upgrade';document.body.dataset.gameState='upgrade';this.bridge.stopGameplay();this.showMobileControls(false);$('#upgrade').classList.add('active');this.audio.level();this.vibrate([20,30,20]);if(this.level===2)this.toast(this.t('firstLevel'));}
    const pool=UPGRADE_POOL.filter(u=>(this.upgradeRanks[u.id]||0)<u.max),chosen=[];while(chosen.length<Math.min(3,pool.length))chosen.push(pool.splice(Math.floor(Math.random()*pool.length),1)[0]);
    const wrap=$('#upgrade-cards');wrap.innerHTML='';for(const u of chosen){const rank=(this.upgradeRanks[u.id]||0)+1,b=document.createElement('button');b.className='upgrade-card';b.innerHTML=`<i>${u.icon}</i><b>${u.name[this.lang]}</b><span>${u.desc[this.lang]}</span><small>${this.t('rank')} ${rank}/${u.max}</small>`;b.onclick=()=>{u.apply(this);this.upgradeRanks[u.id]=rank;$('#upgrade').classList.remove('active');this.state='playing';document.body.dataset.gameState='playing';this.bridge.startGameplay();this.showMobileControls(true);this.updateHud();setTimeout(()=>this.checkLevelUp(),60);};wrap.appendChild(b);}
  }

  async tryReroll(){const ok=await this.bridge.showRewarded();if(ok)this.showUpgrade(true);else this.toast(this.t('noAd'));}

  calcRunReward(victory=this.runWon){const metaBonus=1+(this.progress.meta.fortune||0)*.06;return Math.max(1,Math.floor((this.runSoulsRaw+this.level*2+Math.floor(this.elapsed/45)+(victory?90:0))*this.player.soulGain*metaBonus));}

  grantRewardTarget(){this.runRewardTarget=this.calcRunReward();const delta=Math.max(0,this.runRewardTarget-this.rewardGranted);if(delta>0){this.progress.souls+=delta;this.rewardGranted+=delta;}}

  gameOver(victory=false){
    if(['gameover','victory'].includes(this.state))return;this.runWon=victory;this.state=victory?'victory':'gameover';document.body.dataset.gameState=this.state;this.bridge.stopGameplay();$('#hud').classList.add('hidden');this.showMobileControls(false);
    this.grantRewardTarget();const best=Math.max(this.progress.bestTime||0,Math.floor(this.elapsed));this.progress.bestTime=best;this.progress.bestKills=Math.max(this.progress.bestKills||0,this.kills);if(victory)this.progress.wins++;this.saveProgress();
    $('#result-title').textContent=victory?this.t('victory'):this.t('fallen');$('#result-kicker').textContent=victory?this.t('runComplete'):this.t('runEnded');$('#result-time').textContent=this.format(this.elapsed);$('#result-kills').textContent=this.kills;$('#result-level').textContent=this.level;$('#result-souls').textContent=`+${this.runRewardTarget}`;
    const canRevive=!victory&&!this.reviveUsed&&!this.doubleUsedRun;$('#revive-btn').style.display=canRevive?'block':'none';$('#double-btn').style.display=this.doubleUsedRun?'none':'block';$('#gameover').classList.add('active');this.updateMenuStats();
  }

  async tryRevive(){
    if(this.reviveUsed||this.doubleUsedRun)return;const ok=await this.bridge.showRewarded();if(!ok){this.toast(this.t('noAd'));return;}this.reviveUsed=true;$('#gameover').classList.remove('active');this.player.hp=this.player.maxHp*.72;this.player.invuln=2.7;this.player.root.visible=true;
    for(let i=this.projectiles.length-1;i>=0;i--){if(this.projectiles[i].enemy){this.scene.remove(this.projectiles[i].root);this.projectiles.splice(i,1);}}
    for(const e of this.enemies){if(e.dead||e.boss)continue;const flat=e.pos.clone();flat.y=0;if(flat.distanceTo(this.player.pos)<5){const away=flat.sub(this.player.pos).normalize();e.pos.x=this.player.pos.x+away.x*6;e.pos.z=this.player.pos.z+away.z*6;}}
    this.state='playing';document.body.dataset.gameState='playing';this.bridge.startGameplay();this.showMobileControls(true);this.audio.resume();this.toast(this.t('revived'));this.updateHud();
  }

  async tryDoubleSouls(){
    if(this.doubleUsedRun)return;const ok=await this.bridge.showRewarded();if(!ok){this.toast(this.t('noAd'));return;}this.doubleUsedRun=true;this.reviveUsed=true;this.progress.souls+=this.runRewardTarget;this.rewardGranted+=this.runRewardTarget;this.saveProgress();$('#result-souls').textContent=`+${this.runRewardTarget*2}`;$('#double-btn').style.display='none';$('#revive-btn').style.display='none';this.updateMenuStats();this.audio.level();
  }

  async restartFromOver(){await this.bridge.showFullscreen();this.startRun();}
  victory(){this.audio.victory();this.vibrate([40,30,70,40,120]);this.gameOver(true);}

  updateHud(){
    if(!this.player)return;$('#time').textContent=this.format(this.elapsed);$('#level').textContent=this.level;$('#kills').textContent=this.kills;$('#hp-fill').style.width=`${clamp(this.player.hp/this.player.maxHp*100,0,100)}%`;$('#hp-text').textContent=`${Math.ceil(this.player.hp)} / ${Math.ceil(this.player.maxHp)}`;$('#xp-fill').style.width=`${clamp(this.xp/this.nextXp*100,0,100)}%`;
    if(this.boss&&!this.boss.dead){$('#boss-bar-wrap').classList.remove('hidden');$('#boss-fill').style.width=`${clamp(this.boss.hp/this.boss.maxHp*100,0,100)}%`;}else $('#boss-bar-wrap').classList.add('hidden');
    const cd=1-clamp(this.player.dashTimer/this.player.dashCooldown,0,1);$('#dash-btn').style.setProperty('--charge',`${Math.round(cd*100)}%`);
  }

  updateCamera(dt){const desired=this.player.pos.clone().add(new THREE.Vector3(0,10.4,11.4));this.camera.position.lerp(desired,1-Math.exp(-dt*5.2));const target=this.player.pos.clone().add(new THREE.Vector3(0,.8,0));if(this.cameraKick>0){this.cameraKick=Math.max(0,this.cameraKick-dt);this.camera.position.x+=(Math.random()-.5)*this.cameraKick;this.camera.position.y+=(Math.random()-.5)*this.cameraKick*.45;}this.camera.lookAt(target);}

  updateAtmosphere(){const bossFactor=this.boss?1-clamp(this.boss.hp/this.boss.maxHp,0,1):0;this.scene.fog.density=.0205+bossFactor*.006;this.rim.intensity=.85+bossFactor*.8;this.warm.intensity=1.1+bossFactor*.5;}

  animate(){
    requestAnimationFrame(()=>this.animate());const dt=Math.min(this.clock.getDelta(),.033);
    if(this.input.consumePause()){if(this.state==='playing')this.pauseGame();else if(this.state==='paused')this.resumeGame();}
    if(this.state==='playing'){
      this.elapsed+=dt*(DEBUG_FAST?12:1);this.player.mixer.update(dt);this.movePlayer(dt);this.spawnLogic(dt);this.updateEnemies(dt);this.autoAttack();this.spawnPlayerBolt();this.updateProjectiles(dt);this.updateOrbitBlades(dt);this.updatePickups(dt);this.updateCamera(dt);this.updateAtmosphere();this.updateHud();
    } else if(this.state==='menu'){
      this.menuHero?.mixer?.update(dt);const t=performance.now()*.00014;this.camera.position.set(Math.sin(t)*5.2,7.6,12.8+Math.cos(t)*2.1);this.camera.lookAt(0,1,1.8);
    } else if(this.state==='upgrade'||this.state==='paused') { this.player?.mixer?.update(0); }
    this.renderer.render(this.scene,this.camera);
  }

  resize(){const w=innerWidth,h=innerHeight;this.renderer.setSize(w,h,false);this.camera.aspect=w/h;this.camera.updateProjectionMatrix();}
  format(s){s=Math.max(0,Math.floor(s));return `${String(Math.floor(s/60)).padStart(2,'0')}:${String(s%60).padStart(2,'0')}`;}
  toast(text){const t=$('#toast');t.textContent=text;t.classList.add('show');clearTimeout(this.toastTimer);this.toastTimer=setTimeout(()=>t.classList.remove('show'),1700);}
  saveProgress(){this.progress.settings={...this.settings};this.bridge.saveProgress(this.progress);}
}

async function boot(){
  const bridge=new YandexBridge();await bridge.init();const game=new Game(bridge);game.localize(bridge.lang);window.__SHADOWVALE__=game;
  const progress=await bridge.loadProgress();try{await game.load(progress);}catch(err){console.error(err);$('#loading-text').textContent=game.t('loadError');$('#loading-fill').style.width='100%';$('#loading-fill').style.background='#a53e43';}
}
boot();
