import * as THREE from 'three';
import RAPIER from '@dimforge/rapier3d-compat';
import './style.css';

type RaceState = 'menu' | 'countdown' | 'race' | 'paused' | 'finish';
type Quality = 'auto' | 'high' | 'low';

type AIState = {
  mesh: THREE.Group;
  distance: number;
  speed: number;
  lane: number;
  finished: boolean;
};

const $ = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;
const clamp = THREE.MathUtils.clamp;
const mod = (n: number, m: number) => ((n % m) + m) % m;

const ui = {
  boot: $('boot'), bootStatus: $('bootStatus'), menu: $('menu'), start: $('startBtn') as HTMLButtonElement,
  quality: $('qualityBtn') as HTMLButtonElement, hud: $('hud'), position: $('position'), lap: $('lap'),
  raceTime: $('raceTime'), speed: $('speed'), nitroText: $('nitroText'), nitroBar: $('nitroBar') as HTMLElement,
  countdown: $('countdown'), touch: $('touchControls'), pause: $('pauseBtn') as HTMLButtonElement,
  pauseScreen: $('pauseScreen'), resume: $('resumeBtn') as HTMLButtonElement, restart: $('restartBtn') as HTMLButtonElement,
  menuBtn: $('menuBtn') as HTMLButtonElement, finish: $('finishScreen'), finishTitle: $('finishTitle'),
  finishTime: $('finishTime'), again: $('againBtn') as HTMLButtonElement, finishMenu: $('finishMenuBtn') as HTMLButtonElement,
};

class NeonApexGame {
  renderer!: THREE.WebGLRenderer;
  scene = new THREE.Scene();
  camera = new THREE.PerspectiveCamera(66, innerWidth / innerHeight, 0.08, 550);
  clock = new THREE.Clock();
  world!: RAPIER.World;
  playerBody!: RAPIER.RigidBody;
  playerMesh!: THREE.Group;
  track!: THREE.CatmullRomCurve3;
  trackLength = 1;
  trackSamples: THREE.Vector3[] = [];
  trackTangents: THREE.Vector3[] = [];
  roadWidth = 15;
  ai: AIState[] = [];
  keys = new Set<string>();
  touch = { left: false, right: false, gas: false, brake: false, nitro: false };
  state: RaceState = 'menu';
  quality: Quality = 'auto';
  countdown = 0;
  raceSeconds = 0;
  lap = 1;
  lastTrackIndex = 0;
  nitro = 100;
  cameraPos = new THREE.Vector3();
  cameraLook = new THREE.Vector3();
  nearestIndex = 0;
  playerProgress = 0;
  bestPosition = 6;
  frame = 0;
  skid = 0;
  sun!: THREE.DirectionalLight;

  async init() {
    ui.bootStatus.textContent = 'Загрузка 3D и физики…';
    await RAPIER.init();
    this.world = new RAPIER.World({ x: 0, y: -9.81, z: 0 });
    this.setupRenderer();
    this.buildWorld();
    this.buildTrack();
    this.buildScenery();
    this.buildPhysics();
    this.spawnPlayer();
    this.spawnAI();
    this.bindControls();
    this.setQuality('auto');
    this.resetRace(false);
    this.renderer.setAnimationLoop(() => this.loop());
    ui.bootStatus.textContent = 'Готово';
    setTimeout(() => {
      ui.boot.classList.remove('active');
      ui.boot.style.display = 'none';
      ui.menu.classList.add('active');
    }, 350);
  }

  setupRenderer() {
    this.renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
    this.renderer.setSize(innerWidth, innerHeight);
    this.renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.08;
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    $('game').appendChild(this.renderer.domElement);
    addEventListener('resize', () => this.resize());
    this.renderer.domElement.addEventListener('webglcontextlost', e => e.preventDefault());
  }

  buildWorld() {
    this.scene.background = new THREE.Color(0x030711);
    this.scene.fog = new THREE.FogExp2(0x050a15, 0.0075);
    this.scene.add(new THREE.HemisphereLight(0x6285b8, 0x090b12, 1.55));
    this.sun = new THREE.DirectionalLight(0xd8e9ff, 2.3);
    this.sun.position.set(-45, 80, 30);
    this.sun.castShadow = true;
    this.sun.shadow.mapSize.set(1024, 1024);
    this.sun.shadow.camera.left = -120; this.sun.shadow.camera.right = 120;
    this.sun.shadow.camera.top = 120; this.sun.shadow.camera.bottom = -120;
    this.scene.add(this.sun);

    const moon = new THREE.Mesh(
      new THREE.SphereGeometry(7, 24, 16),
      new THREE.MeshBasicMaterial({ color: 0xa9c9ff })
    );
    moon.position.set(-115, 78, -150);
    this.scene.add(moon);

    const ground = new THREE.Mesh(
      new THREE.PlaneGeometry(360, 360),
      new THREE.MeshStandardMaterial({ color: 0x071016, roughness: 0.96, metalness: 0.02 })
    );
    ground.rotation.x = -Math.PI / 2;
    ground.position.y = -0.04;
    ground.receiveShadow = true;
    this.scene.add(ground);
  }

  buildTrack() {
    const pts = [
      [-88, 0, 2], [-72, 0, -58], [-25, 0, -92], [32, 0, -94], [82, 0, -67],
      [104, 0, -18], [92, 0, 40], [55, 0, 82], [2, 0, 96], [-50, 0, 78], [-92, 0, 42]
    ].map(p => new THREE.Vector3(p[0], p[1], p[2]));
    this.track = new THREE.CatmullRomCurve3(pts, true, 'catmullrom', 0.22);
    this.trackLength = this.track.getLength();
    const N = 320;
    this.trackSamples = Array.from({ length: N }, (_, i) => this.track.getPointAt(i / N));
    this.trackTangents = Array.from({ length: N }, (_, i) => this.track.getTangentAt(i / N).normalize());

    const verts: number[] = [];
    const uvs: number[] = [];
    const indices: number[] = [];
    for (let i = 0; i <= N; i++) {
      const idx = i % N;
      const p = this.trackSamples[idx];
      const t = this.trackTangents[idx];
      const normal = new THREE.Vector3(-t.z, 0, t.x);
      const left = p.clone().addScaledVector(normal, this.roadWidth * 0.5);
      const right = p.clone().addScaledVector(normal, -this.roadWidth * 0.5);
      verts.push(left.x, 0.055, left.z, right.x, 0.055, right.z);
      uvs.push(0, i / 10, 1, i / 10);
      if (i < N) {
        const a = i * 2, b = a + 1, c = a + 2, d = a + 3;
        indices.push(a, b, c, b, d, c);
      }
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.Float32BufferAttribute(verts, 3));
    geo.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2));
    geo.setIndex(indices); geo.computeVertexNormals();
    const road = new THREE.Mesh(geo, new THREE.MeshStandardMaterial({ color: 0x151a20, roughness: 0.73, metalness: 0.16 }));
    road.receiveShadow = true;
    this.scene.add(road);

    const markerGeo = new THREE.BoxGeometry(0.18, 0.035, 3.2);
    const markerMat = new THREE.MeshStandardMaterial({ color: 0xddeaff, emissive: 0x4f6d88, emissiveIntensity: 0.45 });
    const markers = new THREE.InstancedMesh(markerGeo, markerMat, Math.floor(N / 4));
    const m = new THREE.Matrix4(); const q = new THREE.Quaternion(); const s = new THREE.Vector3(1, 1, 1);
    let mi = 0;
    for (let i = 0; i < N; i += 4) {
      const p = this.trackSamples[i], t = this.trackTangents[i];
      const yaw = Math.atan2(-t.x, -t.z);
      q.setFromAxisAngle(new THREE.Vector3(0, 1, 0), yaw);
      m.compose(new THREE.Vector3(p.x, 0.09, p.z), q, s);
      markers.setMatrixAt(mi++, m);
    }
    this.scene.add(markers);

    const railGeo = new THREE.BoxGeometry(5.8, 0.85, 0.26);
    const railMat = new THREE.MeshStandardMaterial({ color: 0x263447, metalness: 0.78, roughness: 0.34 });
    const railCount = Math.floor(N / 5) * 2;
    const rails = new THREE.InstancedMesh(railGeo, railMat, railCount);
    let ri = 0;
    for (let i = 0; i < N; i += 5) {
      const p = this.trackSamples[i], t = this.trackTangents[i];
      const n = new THREE.Vector3(-t.z, 0, t.x);
      const yaw = Math.atan2(-t.x, -t.z);
      q.setFromAxisAngle(new THREE.Vector3(0, 1, 0), yaw);
      for (const side of [-1, 1]) {
        const rp = p.clone().addScaledVector(n, side * (this.roadWidth * 0.5 + 0.75));
        m.compose(new THREE.Vector3(rp.x, 0.48, rp.z), q, s);
        rails.setMatrixAt(ri++, m);
      }
    }
    rails.castShadow = true; rails.receiveShadow = true;
    this.scene.add(rails);

    const start = this.trackSamples[0], tangent = this.trackTangents[0];
    const yaw = Math.atan2(-tangent.x, -tangent.z);
    const startLine = new THREE.Mesh(new THREE.BoxGeometry(this.roadWidth - 1.2, 0.025, 1.8), new THREE.MeshStandardMaterial({ color: 0xeaf7ff, emissive: 0x4bc8ff, emissiveIntensity: 0.25 }));
    startLine.position.set(start.x, 0.085, start.z);
    startLine.rotation.y = yaw;
    this.scene.add(startLine);
  }

  buildScenery() {
    const count = 155;
    const geo = new THREE.BoxGeometry(1, 1, 1);
    const mat = new THREE.MeshStandardMaterial({ color: 0x121928, roughness: 0.7, metalness: 0.25, emissive: 0x071421, emissiveIntensity: 0.75 });
    const buildings = new THREE.InstancedMesh(geo, mat, count);
    const dummy = new THREE.Object3D();
    const c = new THREE.Color();
    for (let i = 0; i < count; i++) {
      const t = (i / count + Math.sin(i * 91.7) * 0.03 + 1) % 1;
      const p = this.track.getPointAt(t); const tan = this.track.getTangentAt(t).normalize(); const n = new THREE.Vector3(-tan.z, 0, tan.x);
      const side = i % 2 ? 1 : -1;
      const dist = 25 + ((i * 37) % 42);
      const w = 5 + ((i * 11) % 10); const d = 5 + ((i * 17) % 10); const h = 9 + ((i * 29) % 46);
      dummy.position.copy(p).addScaledVector(n, side * dist);
      dummy.position.y = h * 0.5 - 0.05;
      dummy.rotation.y = Math.sin(i * 2.11) * 1.2;
      dummy.scale.set(w, h, d); dummy.updateMatrix();
      buildings.setMatrixAt(i, dummy.matrix);
      c.setHSL(0.55 + (i % 9) * 0.008, 0.22, 0.10 + (i % 4) * 0.018);
      buildings.setColorAt(i, c);
    }
    buildings.castShadow = false; buildings.receiveShadow = true;
    this.scene.add(buildings);

    const glowGeo = new THREE.BoxGeometry(0.28, 4.8, 0.28);
    const glowMat = new THREE.MeshBasicMaterial({ color: 0x43dbff });
    const poles = new THREE.InstancedMesh(glowGeo, glowMat, 56);
    for (let i = 0; i < 56; i++) {
      const t = i / 56; const p = this.track.getPointAt(t); const tan = this.track.getTangentAt(t).normalize(); const n = new THREE.Vector3(-tan.z, 0, tan.x);
      const pp = p.clone().addScaledVector(n, (i % 2 ? 1 : -1) * (this.roadWidth * .5 + 3));
      dummy.position.set(pp.x, 2.4, pp.z); dummy.scale.set(1, 1, 1); dummy.rotation.set(0, 0, 0); dummy.updateMatrix(); poles.setMatrixAt(i, dummy.matrix);
    }
    this.scene.add(poles);

    for (let i = 0; i < 20; i++) {
      const sign = new THREE.Mesh(new THREE.BoxGeometry(4.8, 1.2, 0.12), new THREE.MeshBasicMaterial({ color: i % 2 ? 0xff357c : 0x39ddff }));
      const t = (i * 0.071 + 0.035) % 1; const p = this.track.getPointAt(t); const tan = this.track.getTangentAt(t).normalize(); const n = new THREE.Vector3(-tan.z, 0, tan.x);
      sign.position.copy(p).addScaledVector(n, (i % 2 ? 1 : -1) * (this.roadWidth * .5 + 6)); sign.position.y = 4.5 + (i % 3);
      sign.rotation.y = Math.atan2(-tan.x, -tan.z);
      this.scene.add(sign);
    }
  }

  buildPhysics() {
    const groundBody = this.world.createRigidBody(RAPIER.RigidBodyDesc.fixed().setTranslation(0, -0.3, 0));
    this.world.createCollider(RAPIER.ColliderDesc.cuboid(180, 0.25, 180).setFriction(1.0), groundBody);
    for (let i = 0; i < this.trackSamples.length; i += 5) {
      const p = this.trackSamples[i], t = this.trackTangents[i];
      const n = new THREE.Vector3(-t.z, 0, t.x); const yaw = Math.atan2(-t.x, -t.z);
      const rot = { x: 0, y: Math.sin(yaw / 2), z: 0, w: Math.cos(yaw / 2) };
      for (const side of [-1, 1]) {
        const bp = p.clone().addScaledVector(n, side * (this.roadWidth * 0.5 + 0.75));
        const rb = this.world.createRigidBody(RAPIER.RigidBodyDesc.fixed().setTranslation(bp.x, 0.62, bp.z).setRotation(rot));
        this.world.createCollider(RAPIER.ColliderDesc.cuboid(2.9, 0.7, 0.2).setFriction(0.45).setRestitution(0.08), rb);
      }
    }
  }

  makeCar(color: number, accent: number) {
    const g = new THREE.Group();
    const paint = new THREE.MeshStandardMaterial({ color, metalness: 0.82, roughness: 0.24 });
    const dark = new THREE.MeshStandardMaterial({ color: 0x07090d, metalness: 0.7, roughness: 0.22 });
    const glass = new THREE.MeshStandardMaterial({ color: 0x11263a, metalness: 0.42, roughness: 0.08, transparent: true, opacity: 0.86 });
    const neon = new THREE.MeshBasicMaterial({ color: accent });
    const body = new THREE.Mesh(new THREE.BoxGeometry(2.12, 0.48, 4.2), paint); body.position.y = 0.64; body.castShadow = true; g.add(body);
    const hood = new THREE.Mesh(new THREE.BoxGeometry(1.86, 0.25, 1.38), paint); hood.position.set(0, 0.93, -1.22); hood.rotation.x = -0.07; g.add(hood);
    const cabin = new THREE.Mesh(new THREE.BoxGeometry(1.62, 0.68, 1.72), glass); cabin.position.set(0, 1.16, 0.18); cabin.rotation.x = -0.03; g.add(cabin);
    const splitter = new THREE.Mesh(new THREE.BoxGeometry(2.28, 0.12, 0.35), dark); splitter.position.set(0, 0.42, -2.06); g.add(splitter);
    const spoiler = new THREE.Mesh(new THREE.BoxGeometry(2.12, 0.12, 0.38), dark); spoiler.position.set(0, 1.22, 1.75); g.add(spoiler);
    const wingL = new THREE.Mesh(new THREE.BoxGeometry(.12, .48, .12), dark); wingL.position.set(-.72, 1.02, 1.7); g.add(wingL);
    const wingR = wingL.clone(); wingR.position.x = .72; g.add(wingR);
    const wheelGeo = new THREE.CylinderGeometry(0.43, 0.43, 0.32, 16); const wheelMat = new THREE.MeshStandardMaterial({ color: 0x050608, roughness: .82 });
    for (const x of [-1.04, 1.04]) for (const z of [-1.34, 1.32]) { const w = new THREE.Mesh(wheelGeo, wheelMat); w.rotation.z = Math.PI / 2; w.position.set(x, .48, z); w.castShadow = true; g.add(w); }
    for (const x of [-.68, .68]) { const h = new THREE.Mesh(new THREE.BoxGeometry(.4, .14, .08), neon); h.position.set(x, .73, -2.13); g.add(h); }
    const rear = new THREE.Mesh(new THREE.BoxGeometry(1.45, .1, .08), new THREE.MeshBasicMaterial({ color: 0xff204f })); rear.position.set(0, .68, 2.13); g.add(rear);
    const under = new THREE.Mesh(new THREE.BoxGeometry(1.75, .025, 3.35), neon); under.position.y = .25; g.add(under);
    return g;
  }

  spawnPlayer() {
    this.playerMesh = this.makeCar(0x1b9cff, 0x65f5ff);
    this.scene.add(this.playerMesh);
    const p = this.trackSamples[0], t = this.trackTangents[0]; const yaw = Math.atan2(-t.x, -t.z);
    const desc = RAPIER.RigidBodyDesc.dynamic().setTranslation(p.x, 0.8, p.z).setRotation({ x: 0, y: Math.sin(yaw / 2), z: 0, w: Math.cos(yaw / 2) }).setLinearDamping(0.18).setAngularDamping(4.0).setCcdEnabled(true).setCanSleep(false);
    this.playerBody = this.world.createRigidBody(desc);
    this.world.createCollider(RAPIER.ColliderDesc.cuboid(1.0, 0.42, 2.0).setDensity(120).setFriction(0.75).setRestitution(0.05), this.playerBody);
  }

  spawnAI() {
    const colors = [0xff3a77, 0xffb22e, 0x8b5cff, 0x63e26f, 0xf0f3ff];
    this.ai = colors.map((color, i) => {
      const mesh = this.makeCar(color, i % 2 ? 0xffb448 : 0xff3fd2); mesh.scale.setScalar(.97); this.scene.add(mesh);
      return { mesh, distance: -(i + 1) * 7.5, speed: 36.5 + i * 1.45 + (i % 2) * 1.2, lane: ((i % 3) - 1) * 2.35, finished: false };
    });
  }

  bindControls() {
    addEventListener('keydown', e => { this.keys.add(e.code); if (['ArrowUp','ArrowDown','ArrowLeft','ArrowRight','Space'].includes(e.code)) e.preventDefault(); if (e.code === 'Escape' && this.state === 'race') this.pause(); });
    addEventListener('keyup', e => this.keys.delete(e.code));
    document.querySelectorAll<HTMLButtonElement>('[data-key]').forEach(btn => {
      const key = btn.dataset.key as keyof typeof this.touch;
      const on = (e: Event) => { e.preventDefault(); this.touch[key] = true; btn.classList.add('pressed'); };
      const off = (e: Event) => { e.preventDefault(); this.touch[key] = false; btn.classList.remove('pressed'); };
      btn.addEventListener('pointerdown', on); btn.addEventListener('pointerup', off); btn.addEventListener('pointercancel', off); btn.addEventListener('pointerleave', off);
    });
    ui.start.onclick = () => this.startRace(); ui.again.onclick = () => this.startRace(); ui.restart.onclick = () => this.startRace();
    ui.pause.onclick = () => this.pause(); ui.resume.onclick = () => this.resume(); ui.menuBtn.onclick = () => this.toMenu(); ui.finishMenu.onclick = () => this.toMenu();
    ui.quality.onclick = () => { const next: Quality = this.quality === 'auto' ? 'high' : this.quality === 'high' ? 'low' : 'auto'; this.setQuality(next); };
    document.addEventListener('visibilitychange', () => { if (document.hidden && this.state === 'race') this.pause(); });
  }

  setQuality(q: Quality) {
    this.quality = q;
    const low = q === 'low' || (q === 'auto' && (devicePixelRatio > 2.5 || Math.min(innerWidth, innerHeight) < 500));
    this.renderer.setPixelRatio(Math.min(devicePixelRatio, low ? 1.25 : 2));
    this.renderer.shadowMap.enabled = !low;
    this.sun.castShadow = !low;
    ui.quality.textContent = `КАЧЕСТВО: ${q === 'auto' ? 'АВТО' : q === 'high' ? 'ВЫСОКОЕ' : 'ЭКОНОМ'}`;
  }

  resetRace(showHUD = true) {
    const p = this.trackSamples[0], t = this.trackTangents[0]; const yaw = Math.atan2(-t.x, -t.z);
    this.playerBody.setTranslation({ x: p.x, y: .82, z: p.z }, true);
    this.playerBody.setRotation({ x: 0, y: Math.sin(yaw / 2), z: 0, w: Math.cos(yaw / 2) }, true);
    this.playerBody.setLinvel({ x: 0, y: 0, z: 0 }, true); this.playerBody.setAngvel({ x: 0, y: 0, z: 0 }, true);
    this.ai.forEach((a, i) => { a.distance = -(i + 1) * 7.5; a.finished = false; });
    this.raceSeconds = 0; this.lap = 1; this.lastTrackIndex = 0; this.nearestIndex = 0; this.playerProgress = 0; this.nitro = 100; this.bestPosition = 6;
    ui.lap.textContent = '1'; ui.position.textContent = '1'; ui.raceTime.textContent = '00:00.000'; ui.nitroText.textContent = '100%'; ui.nitroBar.style.width = '100%';
    if (showHUD) { ui.hud.classList.remove('hidden'); if (matchMedia('(pointer:coarse)').matches) ui.touch.classList.remove('hidden'); }
    this.syncPlayerMesh(); this.updateAI(0);
  }

  startRace() {
    ui.menu.classList.remove('active'); ui.pauseScreen.classList.remove('active'); ui.finish.classList.remove('active');
    this.resetRace(true); this.state = 'countdown'; this.countdown = 3.85; ui.countdown.classList.remove('hidden'); ui.countdown.textContent = '3';
    this.clock.getDelta();
  }

  pause() { if (this.state !== 'race') return; this.state = 'paused'; ui.pauseScreen.classList.add('active'); ui.touch.classList.add('hidden'); }
  resume() { if (this.state !== 'paused') return; this.state = 'race'; ui.pauseScreen.classList.remove('active'); if (matchMedia('(pointer:coarse)').matches) ui.touch.classList.remove('hidden'); this.clock.getDelta(); }
  toMenu() { this.state = 'menu'; ui.pauseScreen.classList.remove('active'); ui.finish.classList.remove('active'); ui.hud.classList.add('hidden'); ui.touch.classList.add('hidden'); ui.countdown.classList.add('hidden'); ui.menu.classList.add('active'); }

  input() {
    return {
      gas: this.touch.gas || this.keys.has('ArrowUp') || this.keys.has('KeyW'),
      brake: this.touch.brake || this.keys.has('ArrowDown') || this.keys.has('KeyS'),
      left: this.touch.left || this.keys.has('ArrowLeft') || this.keys.has('KeyA'),
      right: this.touch.right || this.keys.has('ArrowRight') || this.keys.has('KeyD'),
      nitro: this.touch.nitro || this.keys.has('ShiftLeft') || this.keys.has('ShiftRight') || this.keys.has('Space')
    };
  }

  updatePlayer(dt: number) {
    const input = this.input(); const q0 = this.playerBody.rotation(); const q = new THREE.Quaternion(q0.x, q0.y, q0.z, q0.w);
    const forward = new THREE.Vector3(0, 0, -1).applyQuaternion(q).setY(0).normalize(); const right = new THREE.Vector3(1, 0, 0).applyQuaternion(q).setY(0).normalize();
    const rv = this.playerBody.linvel(); const vel = new THREE.Vector3(rv.x, rv.y, rv.z); let longitudinal = vel.dot(forward); let lateral = vel.dot(right);
    const steer = (input.left ? 1 : 0) - (input.right ? 1 : 0);
    const nitroOn = input.nitro && input.gas && this.nitro > .5 && longitudinal > 5;
    const top = nitroOn ? 68 : 51;
    if (input.gas) longitudinal += (longitudinal < 18 ? 25 : 16) * dt;
    else longitudinal *= Math.pow(.982, dt * 60);
    if (input.brake) {
      if (longitudinal > 2) longitudinal -= 35 * dt; else longitudinal -= 13 * dt;
    }
    longitudinal = clamp(longitudinal + (nitroOn ? 24 * dt : 0), -12, top);
    this.nitro = clamp(this.nitro + (nitroOn ? -28 * dt : 8.5 * dt), 0, 100);
    const drift = input.brake && Math.abs(longitudinal) > 11;
    const grip = drift ? Math.pow(.982, dt * 60) : Math.pow(.84, dt * 60);
    lateral *= grip; this.skid = THREE.MathUtils.lerp(this.skid, drift ? 1 : 0, 1 - Math.pow(.06, dt));
    const speedFactor = clamp(Math.abs(longitudinal) / 32, .12, 1.15);
    const yawRate = steer * (0.45 + 1.55 * speedFactor) * (longitudinal >= 0 ? 1 : -1) * (drift ? 1.22 : 1);
    this.playerBody.setAngvel({ x: 0, y: yawRate, z: 0 }, true);
    const newVel = forward.multiplyScalar(longitudinal).add(right.multiplyScalar(lateral)); newVel.y = rv.y;
    this.playerBody.setLinvel({ x: newVel.x, y: newVel.y, z: newVel.z }, true);

    const rotNow = this.playerBody.rotation(); const e = new THREE.Euler().setFromQuaternion(new THREE.Quaternion(rotNow.x, rotNow.y, rotNow.z, rotNow.w), 'YXZ');
    const yaw = e.y; this.playerBody.setRotation({ x: 0, y: Math.sin(yaw / 2), z: 0, w: Math.cos(yaw / 2) }, true);

    const p = this.playerBody.translation(); if (p.y < -2) this.respawnAtNearest();
    this.updateProgress(longitudinal);
    const kmh = Math.max(0, longitudinal * 3.6); ui.speed.textContent = `${Math.round(kmh)}`; ui.nitroText.textContent = `${Math.round(this.nitro)}%`; ui.nitroBar.style.width = `${this.nitro}%`;
  }

  updateProgress(forwardSpeed: number) {
    const p = this.playerBody.translation(); let best = Infinity; let idx = this.nearestIndex;
    for (let i = 0; i < this.trackSamples.length; i++) { const s = this.trackSamples[i]; const dx = s.x - p.x, dz = s.z - p.z, d = dx * dx + dz * dz; if (d < best) { best = d; idx = i; } }
    if (this.lastTrackIndex > this.trackSamples.length * .82 && idx < this.trackSamples.length * .18 && forwardSpeed > 5) {
      this.lap++; if (this.lap > 3) { this.finishRace(); return; } ui.lap.textContent = `${this.lap}`;
    }
    if (this.lastTrackIndex < this.trackSamples.length * .18 && idx > this.trackSamples.length * .82 && forwardSpeed < -4 && this.lap > 1) { this.lap--; ui.lap.textContent = `${this.lap}`; }
    this.nearestIndex = idx; this.lastTrackIndex = idx; this.playerProgress = (this.lap - 1) + idx / this.trackSamples.length;
  }

  respawnAtNearest() {
    const i = this.nearestIndex; const p = this.trackSamples[i], t = this.trackTangents[i]; const yaw = Math.atan2(-t.x, -t.z);
    this.playerBody.setTranslation({ x: p.x, y: .9, z: p.z }, true); this.playerBody.setRotation({ x: 0, y: Math.sin(yaw / 2), z: 0, w: Math.cos(yaw / 2) }, true); this.playerBody.setLinvel({ x: 0, y: 0, z: 0 }, true); this.playerBody.setAngvel({ x: 0, y: 0, z: 0 }, true);
  }

  updateAI(dt: number) {
    for (let i = 0; i < this.ai.length; i++) {
      const a = this.ai[i]; if (this.state === 'race') {
        const wobble = Math.sin(this.raceSeconds * .47 + i * 2.2) * 1.1;
        const playerCatch = clamp((this.playerProgress * this.trackLength - a.distance) * .018, -2.5, 2.5);
        a.distance += (a.speed + wobble + playerCatch) * dt;
      }
      const t = mod(a.distance / this.trackLength, 1); const p = this.track.getPointAt(t); const tan = this.track.getTangentAt(t).normalize(); const n = new THREE.Vector3(-tan.z, 0, tan.x);
      a.mesh.position.copy(p).addScaledVector(n, a.lane); a.mesh.position.y = .08;
      a.mesh.rotation.y = Math.atan2(-tan.x, -tan.z);
    }
  }

  updatePosition() {
    const scores = [{ player: true, progress: this.playerProgress }, ...this.ai.map(a => ({ player: false, progress: a.distance / this.trackLength }))].sort((a,b) => b.progress - a.progress);
    const pos = scores.findIndex(s => s.player) + 1; this.bestPosition = pos; ui.position.textContent = `${pos}`;
  }

  syncPlayerMesh() {
    const p = this.playerBody.translation(), q = this.playerBody.rotation(); this.playerMesh.position.set(p.x, p.y - .43, p.z); this.playerMesh.quaternion.set(q.x, q.y, q.z, q.w);
    const bodyLean = clamp(this.playerBody.angvel().y * -.055, -.09, .09); this.playerMesh.rotation.z = bodyLean;
  }

  updateCamera(dt: number) {
    const p = this.playerMesh.position; const q = this.playerMesh.quaternion; const forward = new THREE.Vector3(0, 0, -1).applyQuaternion(q).setY(0).normalize();
    const speed = Math.min(1, parseFloat(ui.speed.textContent || '0') / 220); const desired = p.clone().addScaledVector(forward, -7.4 - speed * 2.0).add(new THREE.Vector3(0, 3.9 + speed * .9, 0));
    const look = p.clone().addScaledVector(forward, 7 + speed * 5).add(new THREE.Vector3(0, 1.0, 0));
    const a = 1 - Math.pow(.0015, dt); this.cameraPos.lerp(desired, a); this.cameraLook.lerp(look, a); this.camera.position.copy(this.cameraPos); this.camera.lookAt(this.cameraLook);
    this.camera.fov = THREE.MathUtils.lerp(this.camera.fov, 66 + speed * 9, 1 - Math.pow(.01, dt)); this.camera.updateProjectionMatrix();
  }

  formatTime(sec: number) { const m = Math.floor(sec / 60); const s = Math.floor(sec % 60); const ms = Math.floor((sec % 1) * 1000); return `${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}.${String(ms).padStart(3,'0')}`; }

  finishRace() {
    if (this.state === 'finish') return; this.updatePosition(); this.state = 'finish'; ui.touch.classList.add('hidden'); ui.hud.classList.add('hidden'); ui.finishTitle.textContent = `${this.bestPosition} МЕСТО`; ui.finishTime.textContent = this.formatTime(this.raceSeconds); ui.finish.classList.add('active');
  }

  updateCountdown(dt: number) {
    this.countdown -= dt; const n = Math.ceil(this.countdown); ui.countdown.textContent = n > 0 ? `${Math.min(3, n)}` : 'GO!';
    if (this.countdown <= -.55) { this.state = 'race'; ui.countdown.classList.add('hidden'); this.clock.getDelta(); }
  }

  loop() {
    let dt = Math.min(this.clock.getDelta(), 1 / 20); this.frame++;
    if (this.state === 'countdown') this.updateCountdown(dt);
    if (this.state === 'race') {
      this.raceSeconds += dt; ui.raceTime.textContent = this.formatTime(this.raceSeconds);
      this.updatePlayer(dt); this.world.timestep = dt; this.world.step(); this.updateAI(dt); this.updatePosition();
    } else if (this.state !== 'paused') this.updateAI(0);
    this.syncPlayerMesh(); this.updateCamera(dt || 1/60); this.renderer.render(this.scene, this.camera);
  }

  resize() { this.camera.aspect = innerWidth / innerHeight; this.camera.updateProjectionMatrix(); this.renderer.setSize(innerWidth, innerHeight); this.setQuality(this.quality); }
}

const game = new NeonApexGame();
game.init().catch(err => {
  console.error(err);
  ui.bootStatus.textContent = `Ошибка запуска: ${err instanceof Error ? err.message : String(err)}`;
});
