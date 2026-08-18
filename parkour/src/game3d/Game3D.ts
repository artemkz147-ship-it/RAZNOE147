import * as THREE from 'three';
import RAPIER from '@dimforge/rapier3d-compat';
import levelsJson from './levels.json';
import { AudioSystem } from './AudioSystem';
import { AvatarSystem } from './AvatarSystem';
import { Input } from './Input';
import { LevelManager } from './LevelManager';
import { PlayerController, type ParkourEvent } from './PlayerController';
import type { LevelSpec, RunStats } from './types';

export type GameCallbacks = {
  onHud: (data: {
    level: LevelSpec;
    time: number;
    speed: number;
    checkpoint: number;
    checkpointCount: number;
    wallRun: boolean;
    breaks: number;
    motion: string;
  }) => void;
  onCheckpoint: (index: number, total: number) => void;
  onFall: (falls: number) => void;
  onParkour: (event: ParkourEvent) => void;
  onFinish: (data: { level: LevelSpec; time: number; stats: RunStats; reward: number }) => void;
  onReady: () => void;
};

export class Game3D {
  readonly levels = levelsJson as LevelSpec[];

  private host: HTMLElement;
  private callbacks: GameCallbacks;
  private renderer!: THREE.WebGLRenderer;
  private scene = new THREE.Scene();
  private camera = new THREE.PerspectiveCamera(74, 1, 0.05, 500);
  private world!: RAPIER.World;
  private player!: PlayerController;
  private levelManager!: LevelManager;
  private avatar!: AvatarSystem;
  private audio = new AudioSystem();
  private input!: Input;
  private currentLevel!: LevelSpec;
  private running = false;
  private paused = false;
  private thirdPerson = true;
  private elapsed = 0;
  private simTime = 0;
  private accumulator = 0;
  private readonly fixedDt = 1 / 60;
  private lastFrame = performance.now();
  private hudClock = 0;
  private cameraKick = 0;
  private stats: RunStats = { falls: 0, breaks: 0, checkpoints: 0, parkourMoves: 0, perfectLandings: 0 };
  private tmpFoot = new THREE.Vector3();
  private tmpEye = new THREE.Vector3();
  private tmpDirection = new THREE.Vector3();
  private tmpCameraDesired = new THREE.Vector3();
  private tmpCameraTarget = new THREE.Vector3();

  constructor(host: HTMLElement, callbacks: GameCallbacks) {
    this.host = host;
    this.callbacks = callbacks;
  }

  async init() {
    await RAPIER.init();
    this.world = new RAPIER.World({ x: 0, y: -19.5, z: 0 });

    this.renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
    this.renderer.setPixelRatio(Math.min(devicePixelRatio, 1.75));
    this.renderer.setSize(innerWidth, innerHeight);
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.08;
    this.host.appendChild(this.renderer.domElement);

    this.input = new Input(this.renderer.domElement);
    this.createLighting();
    this.levelManager = new LevelManager(this.scene, this.world, () => {
      this.stats.breaks += 1;
      this.cameraKick = Math.min(1, this.cameraKick + 0.5);
      this.audio.breakObject();
    });
    await this.levelManager.init();

    const spawn = new THREE.Vector3(...this.levels[0].spawn);
    this.player = new PlayerController(
      this.world,
      spawn,
      (collider, impactSpeed, normal) => {
        if (this.levelManager.destruction.hit(collider, impactSpeed, normal)) this.cameraKick = 0.8;
      },
      (event) => this.handleParkourEvent(event)
    );

    this.avatar = new AvatarSystem(this.scene);
    await this.avatar.init();

    addEventListener('resize', () => this.resize());
    addEventListener('keydown', (event) => {
      if (event.code !== 'KeyV' || event.repeat) return;
      this.thirdPerson = !this.thirdPerson;
      this.avatar.setVisible(this.thirdPerson);
      window.dispatchEvent(new CustomEvent('parkour-camera', {
        detail: { mode: this.thirdPerson ? 'ТРЕТЬЕ ЛИЦО' : 'ПЕРВОЕ ЛИЦО' }
      }));
    });
    document.addEventListener('visibilitychange', () => this.setPaused(document.hidden));
    this.resize();
    this.renderer.setAnimationLoop(() => this.frame());
    this.callbacks.onReady();
  }

  async startLevel(levelId: number) {
    const level = this.levels.find((item) => item.id === levelId) ?? this.levels[0];
    this.currentLevel = level;
    this.running = false;
    this.paused = false;
    this.elapsed = 0;
    this.simTime = 0;
    this.accumulator = 0;
    this.stats = { falls: 0, breaks: 0, checkpoints: 0, parkourMoves: 0, perfectLandings: 0 };
    this.applyTheme(level.theme);
    await this.levelManager.load(level);
    const spawn = new THREE.Vector3(...level.spawn);
    this.player.teleport(spawn);
    this.player.setRespawn(spawn);
    this.input.yaw = -Math.PI / 2;
    this.input.pitch = -0.08;
    this.avatar.setVisible(this.thirdPerson);
    this.audio.startAmbient();
    this.running = true;
    this.lastFrame = performance.now();
    this.emitHud(true);
  }

  restartLevel() {
    if (!this.currentLevel) return;
    void this.startLevel(this.currentLevel.id);
  }

  setPaused(value: boolean) {
    this.paused = value;
    if (value) this.audio.stopAmbient();
    else if (this.running) this.audio.startAmbient();
  }

  isRunning() {
    return this.running && !this.paused;
  }

  private frame() {
    const now = performance.now();
    const dt = Math.min(0.05, Math.max(0, (now - this.lastFrame) / 1000));
    this.lastFrame = now;
    this.input.update();

    if (this.running && !this.paused) {
      this.elapsed += dt;
      this.accumulator += dt;
      while (this.accumulator >= this.fixedDt) {
        this.simTime += this.fixedDt;
        this.player.prepare(this.fixedDt, this.input);
        this.levelManager.update(this.simTime);
        this.world.step();
        this.accumulator -= this.fixedDt;
      }
      this.postSimulation(dt);
      const foot = this.player.getFootPosition(this.tmpFoot);
      const motion = this.player.getMotionState();
      this.avatar.update(dt, foot, this.input.yaw, motion, this.player.getSpeed(), this.thirdPerson);
      this.audio.update(dt, this.player.getSpeed(), this.player.isGrounded(), motion);
    }

    this.updateCamera(dt);
    this.renderer.render(this.scene, this.camera);
  }

  private postSimulation(dt: number) {
    const foot = this.player.getFootPosition(this.tmpFoot);
    const checkpoint = this.levelManager.updateCheckpoints(foot);
    if (checkpoint) {
      this.player.setRespawn(checkpoint.clone().add(new THREE.Vector3(0, 0.4, 0)));
      this.stats.checkpoints += 1;
      this.callbacks.onCheckpoint(this.levelManager.getCheckpointIndex() + 1, this.levelManager.getCheckpointCount());
    }

    if (foot.y < -20) {
      this.stats.falls += 1;
      this.player.respawn();
      this.cameraKick = 1;
      this.callbacks.onFall(this.stats.falls);
    }

    if (this.levelManager.reachedFinish(foot)) {
      this.running = false;
      this.audio.stopAmbient();
      const reward = Math.max(
        20,
        120
          - this.stats.falls * 10
          + this.stats.breaks * 8
          + this.stats.parkourMoves * 3
          + this.stats.perfectLandings * 6
      );
      this.callbacks.onFinish({ level: this.currentLevel, time: this.elapsed, stats: { ...this.stats }, reward });
    }

    this.hudClock += dt;
    if (this.hudClock > 0.08) {
      this.hudClock = 0;
      this.emitHud(false);
    }
  }

  private updateCamera(dt: number) {
    if (!this.player) return;
    const eye = this.player.getEyePosition(this.tmpEye);
    this.cameraKick = Math.max(0, this.cameraKick - dt * 3.5);
    const kickPitch = Math.sin(performance.now() * 0.034) * this.cameraKick * 0.012;
    const pitch = this.input.pitch + kickPitch;
    const roll = this.player.getCameraRoll(this.input) * (this.thirdPerson ? 0.45 : 1);

    if (this.thirdPerson) {
      const foot = this.player.getFootPosition(this.tmpFoot);
      const target = this.tmpCameraTarget.set(foot.x, foot.y + 1.18, foot.z);
      const horizontalForward = this.tmpDirection.set(-Math.sin(this.input.yaw), 0, -Math.cos(this.input.yaw)).normalize();
      const desired = this.tmpCameraDesired
        .copy(target)
        .addScaledVector(horizontalForward, -4.45)
        .add(new THREE.Vector3(0, 1.25 + Math.max(0, -pitch) * 0.7, 0));

      const rayDirection = desired.clone().sub(target);
      const maxDistance = rayDirection.length();
      if (maxDistance > 0.01) {
        rayDirection.normalize();
        const ray = new RAPIER.Ray(
          { x: target.x, y: target.y, z: target.z },
          { x: rayDirection.x, y: rayDirection.y, z: rayDirection.z }
        );
        const hit = this.world.castRay(ray, maxDistance, true, undefined, undefined, this.player.collider);
        if (hit) {
          const safeDistance = Math.max(0.55, hit.timeOfImpact - 0.22);
          desired.copy(target).addScaledVector(rayDirection, safeDistance);
        }
      }
      this.camera.position.lerp(desired, 1 - Math.exp(-10 * dt));
      this.camera.rotation.set(pitch * 0.68, this.input.yaw, roll, 'YXZ');
    } else {
      this.camera.position.lerp(eye, 1 - Math.exp(-20 * dt));
      this.camera.rotation.set(pitch, this.input.yaw, roll, 'YXZ');
    }

    const desiredFov = (this.thirdPerson ? 68 : 72)
      + Math.min(10, Math.max(0, this.player.getSpeed() - 4.5) * 1.6)
      + (this.player.isWallRunning() ? 3 : 0)
      + (this.player.isSliding() ? 2 : 0);
    this.camera.fov += (desiredFov - this.camera.fov) * Math.min(1, dt * 5.5);
    this.camera.updateProjectionMatrix();
  }

  private emitHud(force: boolean) {
    if (!this.currentLevel || (!force && !this.running)) return;
    this.callbacks.onHud({
      level: this.currentLevel,
      time: this.elapsed,
      speed: this.player.getSpeed(),
      checkpoint: this.levelManager.getCheckpointIndex() + 1,
      checkpointCount: this.levelManager.getCheckpointCount(),
      wallRun: this.player.isWallRunning(),
      breaks: this.stats.breaks,
      motion: this.player.getMotionState()
    });
  }

  private handleParkourEvent(event: ParkourEvent) {
    if (event.type !== 'hard-land') this.stats.parkourMoves += 1;
    if (event.type === 'perfect-land') this.stats.perfectLandings += 1;
    this.cameraKick = Math.min(1, this.cameraKick + event.intensity * 0.42);
    this.audio.parkour(event);
    this.callbacks.onParkour(event);
  }

  private createLighting() {
    const hemi = new THREE.HemisphereLight(0xa9c8ff, 0x1a1420, 1.6);
    this.scene.add(hemi);
    const sun = new THREE.DirectionalLight(0xffe4c4, 3.1);
    sun.position.set(-18, 28, 14);
    sun.castShadow = true;
    sun.shadow.mapSize.set(2048, 2048);
    sun.shadow.camera.near = 1;
    sun.shadow.camera.far = 110;
    sun.shadow.camera.left = -48;
    sun.shadow.camera.right = 48;
    sun.shadow.camera.top = 48;
    sun.shadow.camera.bottom = -48;
    this.scene.add(sun);
  }

  private applyTheme(theme: string) {
    const palette: Record<string, { bg: number; fog: number }> = {
      roof: { bg: 0x9fb6d0, fog: 0x9fb6d0 },
      brick: { bg: 0xb59b91, fog: 0xb59b91 },
      construction: { bg: 0x91a8bb, fog: 0x91a8bb },
      oldtown: { bg: 0xa98579, fog: 0xa98579 },
      highrise: { bg: 0x70839a, fog: 0x70839a },
      crane: { bg: 0x8294a8, fog: 0x8294a8 },
      precision: { bg: 0x596a80, fog: 0x596a80 },
      final: { bg: 0x414c64, fog: 0x414c64 }
    };
    const colors = palette[theme] ?? palette.roof;
    this.scene.background = new THREE.Color(colors.bg);
    this.scene.fog = new THREE.FogExp2(colors.fog, theme === 'final' ? 0.012 : 0.009);
  }

  private resize() {
    const width = Math.max(1, innerWidth);
    const height = Math.max(1, innerHeight);
    this.renderer.setSize(width, height, false);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
  }
}
