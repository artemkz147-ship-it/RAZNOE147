import * as THREE from 'three';
import levelsJson from './drop_levels.json';
import { DropAudioSystem } from './DropAudioSystem';
import { DropAvatarSystem } from './DropAvatarSystem';
import { DropInput } from './DropInput';
import { DropLevelManager } from './DropLevelManager';
import type { DropLevelSpec, DropRunStats, LandingResult, TrickEvent, TrickKind } from './DropTypes';

type DropState = 'ready' | 'jump' | 'air' | 'land' | 'fail';

export type DropCallbacks = {
  onHud: (data: {
    level: DropLevelSpec;
    stage: number;
    stageCount: number;
    score: number;
    combo: number;
    dropLeft: number;
    state: string;
    target: string;
    chain: string;
  }) => void;
  onTrick: (event: TrickEvent, chain: string) => void;
  onLanding: (result: LandingResult) => void;
  onMiss: (falls: number) => void;
  onFinish: (data: { level: DropLevelSpec; stats: DropRunStats; reward: number }) => void;
  onReady: () => void;
};

const GRAVITY = 9.81;
const TWO_PI = Math.PI * 2;
const TRICKS: Record<TrickKind, { label: string; points: number }> = {
  front: { label: 'FRONTFLIP', points: 160 },
  back: { label: 'BACKFLIP', points: 190 },
  side: { label: 'SIDEFLIP', points: 220 },
  twist: { label: 'TWIST 360°', points: 145 }
};

export class DropGame3D {
  readonly levels = levelsJson as DropLevelSpec[];

  private host: HTMLElement;
  private callbacks: DropCallbacks;
  private renderer!: THREE.WebGLRenderer;
  private scene = new THREE.Scene();
  private camera = new THREE.PerspectiveCamera(61, 1, 0.05, 600);
  private input = new DropInput();
  private levelManager = new DropLevelManager(this.scene);
  private avatar = new DropAvatarSystem(this.scene);
  private audio = new DropAudioSystem();
  private currentLevel!: DropLevelSpec;
  private running = false;
  private paused = false;
  private state: DropState = 'ready';
  private simTime = 0;
  private lastFrame = performance.now();
  private playerPos = new THREE.Vector3();
  private velocity = new THREE.Vector3();
  private facingYaw = 0;
  private targetIndex = 0;
  private standingSurface = -1;
  private stateTimer = 0;
  private stageStartY = 0;
  private landingPrepUntil = -1;
  private score = 0;
  private combo = 1;
  private chain: string[] = [];
  private stageTrickPoints = 0;
  private stageTricks = 0;
  private stageUnique = new Set<TrickKind>();
  private runUnique = new Set<TrickKind>();
  private trickRotation = new THREE.Vector3();
  private trickTarget = new THREE.Vector3();
  private sideSign = 1;
  private finishPending = false;
  private stats: DropRunStats = this.freshStats();
  private tmpTarget = new THREE.Vector3();
  private tmpStanding = new THREE.Vector3();
  private tmpCamera = new THREE.Vector3();
  private tmpFocus = new THREE.Vector3();
  private tmpDirection = new THREE.Vector3();
  private tmpSide = new THREE.Vector3();

  constructor(host: HTMLElement, callbacks: DropCallbacks) {
    this.host = host;
    this.callbacks = callbacks;
  }

  async init() {
    this.renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
    this.renderer.setPixelRatio(Math.min(devicePixelRatio, 1.65));
    this.renderer.setSize(innerWidth, innerHeight);
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.05;
    this.host.appendChild(this.renderer.domElement);

    this.createLighting();
    await Promise.all([this.levelManager.init(), this.avatar.init()]);
    addEventListener('resize', () => this.resize());
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
    this.state = 'ready';
    this.simTime = 0;
    this.targetIndex = 0;
    this.standingSurface = -1;
    this.stateTimer = 0;
    this.score = 0;
    this.combo = 1;
    this.chain = [];
    this.trickRotation.set(0, 0, 0);
    this.trickTarget.set(0, 0, 0);
    this.finishPending = false;
    this.stats = this.freshStats();
    this.resetStageTricks();
    this.applyTheme(level.theme);
    await this.levelManager.load(level);
    this.levelManager.getStartPosition(this.playerPos);
    this.playerPos.y += 0.03;
    this.stageStartY = this.playerPos.y;
    this.faceTarget();
    this.levelManager.setActiveTarget(0);
    this.avatar.setVisible(true);
    this.audio.startAmbient();
    this.running = true;
    this.lastFrame = performance.now();
    this.emitHud();
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
    const dt = Math.min(0.04, Math.max(0, (now - this.lastFrame) / 1000));
    this.lastFrame = now;
    this.input.update();

    if (this.running && !this.paused) {
      this.simTime += dt;
      this.levelManager.update(this.simTime);
      this.updateState(dt);
      this.updateTrickRotation(dt);
      this.updateAvatar(dt);
      this.emitHud();
    }

    this.updateCamera(dt);
    this.renderer.render(this.scene, this.camera);
  }

  private updateState(dt: number) {
    if (this.state === 'ready') {
      this.followStandingSurface();
      this.faceTarget();
      if (this.input.consumeJump()) this.launch();
      this.input.consumeTricks();
      this.input.consumeLand();
      return;
    }

    if (this.state === 'jump') {
      this.stateTimer -= dt;
      this.followStandingSurface();
      if (this.stateTimer <= 0) this.state = 'air';
      return;
    }

    if (this.state === 'air') {
      for (const trick of this.input.consumeTricks()) this.queueTrick(trick);
      if (this.input.consumeLand()) this.landingPrepUntil = this.simTime + 0.95;

      const airControl = 2.4;
      this.velocity.x += this.input.moveX * airControl * dt;
      this.velocity.z -= this.input.moveZ * airControl * dt;
      this.velocity.y -= GRAVITY * dt;
      const previousY = this.playerPos.y;
      this.playerPos.addScaledVector(this.velocity, dt);

      const target = this.levelManager.getTargetPosition(this.targetIndex, this.tmpTarget);
      const spec = this.levelManager.getTargetSpec(this.targetIndex);
      if (!spec) return;
      if (this.velocity.y < 0 && previousY >= target.y && this.playerPos.y <= target.y + 0.08) {
        const dx = this.playerPos.x - target.x;
        const dz = this.playerPos.z - target.z;
        const inside = Math.abs(dx) <= spec.size[0] * 0.5 && Math.abs(dz) <= spec.size[1] * 0.5;
        if (inside) this.land(target, Math.hypot(dx, dz));
        else this.miss();
      } else if (this.playerPos.y < target.y - 5.5) {
        this.miss();
      }
      return;
    }

    this.stateTimer -= dt;
    if (this.state === 'land') {
      this.followStandingSurface();
      if (this.stateTimer <= 0) {
        if (this.finishPending) {
          this.finishLevel();
        } else {
          this.state = 'ready';
          this.stageStartY = this.playerPos.y;
          this.trickRotation.set(0, 0, 0);
          this.trickTarget.set(0, 0, 0);
          this.resetStageTricks();
          this.faceTarget();
        }
      }
      return;
    }

    if (this.state === 'fail' && this.stateTimer <= 0) {
      this.state = 'ready';
      this.followStandingSurface();
      this.stageStartY = this.playerPos.y;
      this.trickRotation.set(0, 0, 0);
      this.trickTarget.set(0, 0, 0);
      this.resetStageTricks();
      this.faceTarget();
    }
  }

  private launch() {
    const target = this.levelManager.getTargetPosition(this.targetIndex, this.tmpTarget);
    const dx = target.x - this.playerPos.x;
    const dz = target.z - this.playerPos.z;
    const horizontal = Math.hypot(dx, dz);
    const drop = Math.max(1, this.playerPos.y - target.y);
    const duration = Math.max(1.0, Math.min(3.35, 0.82 + Math.sqrt(drop / 8.5) * 0.9 + horizontal * 0.025));
    const vy = (target.y - this.playerPos.y + 0.5 * GRAVITY * duration * duration) / duration;
    this.velocity.set(dx / duration, vy, dz / duration);
    this.stageStartY = this.playerPos.y;
    this.landingPrepUntil = -1;
    this.state = 'jump';
    this.stateTimer = 0.12;
    this.audio.jump();
  }

  private queueTrick(kind: TrickKind) {
    if (this.chain.length >= 8) return;
    const data = TRICKS[kind];
    if (kind === 'front') this.trickTarget.x += TWO_PI;
    if (kind === 'back') this.trickTarget.x -= TWO_PI;
    if (kind === 'side') {
      const steerSign = this.input.moveX < -0.15 ? -1 : this.input.moveX > 0.15 ? 1 : this.sideSign;
      this.sideSign = -steerSign;
      this.trickTarget.z += TWO_PI * steerSign;
    }
    if (kind === 'twist') {
      const steerSign = this.input.moveX < -0.15 ? -1 : 1;
      this.trickTarget.y += TWO_PI * steerSign;
    }
    this.chain.push(data.label);
    this.stageTrickPoints += data.points;
    this.stageTricks += 1;
    this.stageUnique.add(kind);
    this.runUnique.add(kind);
    this.stats.tricks += 1;
    this.audio.trick(this.stageTricks);
    this.callbacks.onTrick({ kind, label: data.label, points: data.points }, this.chain.join(' + '));
  }

  private updateTrickRotation(dt: number) {
    const prep = this.state === 'air' && this.simTime <= this.landingPrepUntil;
    const speed = prep ? 18.0 : 12.2;
    const maxStep = speed * dt;
    this.trickRotation.x = this.approach(this.trickRotation.x, this.trickTarget.x, maxStep);
    this.trickRotation.y = this.approach(this.trickRotation.y, this.trickTarget.y, maxStep);
    this.trickRotation.z = this.approach(this.trickRotation.z, this.trickTarget.z, maxStep);
  }

  private land(target: THREE.Vector3, distance: number) {
    const spec = this.levelManager.getTargetSpec(this.targetIndex);
    if (!spec) return;
    this.playerPos.copy(target);
    const impact = Math.abs(this.velocity.y);
    this.velocity.set(0, 0, 0);
    const precision = Math.max(0, Math.min(1, 1 - distance / Math.max(0.2, spec.radius)));
    const precisionPoints = distance <= spec.radius * 0.22 ? 500 : distance <= spec.radius * 0.55 ? 250 : distance <= spec.radius ? 100 : 60;
    const alignment = this.rotationAlignmentError();
    const prepared = this.simTime <= this.landingPrepUntil;
    let grade: LandingResult['grade'];
    let label: string;
    let gradeMultiplier: number;

    if (alignment < 0.14 && precision >= 0.78 && (impact < 12 || prepared)) {
      grade = 'perfect'; label = 'ИДЕАЛЬНАЯ ПОСАДКА'; gradeMultiplier = 1.5;
      this.stats.perfectLandings += 1;
    } else if (impact >= 13 && prepared && alignment < 0.55) {
      grade = 'roll'; label = 'ПЕРЕКАТ'; gradeMultiplier = 1.3;
      this.stats.cleanLandings += 1;
    } else if (alignment < 0.52 && precision >= 0.28) {
      grade = 'clean'; label = 'ЧИСТАЯ ПОСАДКА'; gradeMultiplier = 1.12;
      this.stats.cleanLandings += 1;
    } else {
      grade = 'rough'; label = 'ГРЯЗНАЯ ПОСАДКА'; gradeMultiplier = 0.58;
    }

    const dropHeight = Math.max(0, this.stageStartY - target.y);
    const heightPoints = Math.round(dropHeight * 18);
    const varietyBonus = Math.max(0, this.stageUnique.size - 1) * 85;
    const trickPoints = this.stageTrickPoints + varietyBonus;
    if (grade === 'rough') this.combo = 1;
    else this.combo = Math.min(10, this.combo + 1);
    this.stats.bestCombo = Math.max(this.stats.bestCombo, this.combo);
    const comboMultiplier = 1 + Math.max(0, this.combo - 1) * 0.14;
    const stageScore = Math.round((heightPoints + trickPoints + precisionPoints) * gradeMultiplier * comboMultiplier);
    this.score += stageScore;
    this.stats.score = this.score;
    this.stats.totalDrop += dropHeight;
    this.stats.uniqueTricks = this.runUnique.size;

    const result: LandingResult = {
      grade,
      label,
      stageScore,
      precision,
      precisionPoints,
      heightPoints,
      trickPoints,
      combo: this.combo
    };
    this.callbacks.onLanding(result);
    this.audio.landing(impact);
    this.state = 'land';
    this.stateTimer = grade === 'roll' ? 0.82 : 0.58;
    this.standingSurface = this.targetIndex;
    this.targetIndex += 1;
    this.finishPending = this.targetIndex >= this.currentLevel.targets.length;
    if (!this.finishPending) this.levelManager.setActiveTarget(this.targetIndex);
  }

  private miss() {
    this.stats.falls += 1;
    this.score = Math.max(0, this.score - 120);
    this.stats.score = this.score;
    this.combo = 1;
    this.velocity.set(0, 0, 0);
    this.state = 'fail';
    this.stateTimer = 0.9;
    this.audio.fail();
    this.callbacks.onMiss(this.stats.falls);
  }

  private finishLevel() {
    this.running = false;
    this.audio.stopAmbient();
    const scoreRatio = this.score / Math.max(1, this.currentLevel.parScore);
    const reward = Math.max(25, Math.round(35 + scoreRatio * 55 + this.stats.perfectLandings * 10 - this.stats.falls * 4));
    this.callbacks.onFinish({ level: this.currentLevel, stats: { ...this.stats }, reward });
  }

  private followStandingSurface() {
    if (this.standingSurface < 0) this.levelManager.getStartPosition(this.tmpStanding);
    else this.levelManager.getStandingPosition(this.standingSurface, this.tmpStanding);
    this.playerPos.copy(this.tmpStanding);
    this.playerPos.y += 0.03;
  }

  private faceTarget() {
    if (!this.currentLevel || this.targetIndex >= this.currentLevel.targets.length) return;
    const target = this.levelManager.getTargetPosition(this.targetIndex, this.tmpTarget);
    const dx = target.x - this.playerPos.x;
    const dz = target.z - this.playerPos.z;
    if (Math.hypot(dx, dz) > 0.01) this.facingYaw = Math.atan2(dx, dz);
  }

  private updateAvatar(dt: number) {
    let avatarState: 'idle' | 'jump' | 'air' | 'land' | 'roll' | 'fail' = 'idle';
    if (this.state === 'jump') avatarState = 'jump';
    if (this.state === 'air') avatarState = 'air';
    if (this.state === 'fail') avatarState = 'fail';
    if (this.state === 'land') avatarState = this.simTime <= this.landingPrepUntil ? 'roll' : 'land';
    this.avatar.update(dt, this.playerPos, this.facingYaw, avatarState, this.trickRotation, true);
  }

  private updateCamera(dt: number) {
    if (!this.currentLevel) return;
    const target = this.targetIndex < this.currentLevel.targets.length
      ? this.levelManager.getTargetPosition(this.targetIndex, this.tmpTarget)
      : this.playerPos;
    const direction = this.tmpDirection.copy(target).sub(this.playerPos);
    direction.y = 0;
    if (direction.lengthSq() < 0.001) direction.set(Math.sin(this.facingYaw), 0, Math.cos(this.facingYaw));
    direction.normalize();
    const side = this.tmpSide.set(direction.z, 0, -direction.x);
    const air = this.state === 'air' || this.state === 'jump';
    const drop = Math.max(0, this.playerPos.y - target.y);
    const distanceBack = air ? 8.2 : 7.0;
    const height = air ? 6.3 + Math.min(3.2, drop * 0.07) : 8.1;
    const desired = this.tmpCamera.copy(this.playerPos)
      .addScaledVector(direction, -distanceBack)
      .addScaledVector(side, 2.4)
      .add(new THREE.Vector3(0, height, 0));
    this.camera.position.lerp(desired, 1 - Math.exp(-7.5 * dt));
    const focusWeight = air ? 0.26 : 0.42;
    const focus = this.tmpFocus.copy(this.playerPos).lerp(target, focusWeight).add(new THREE.Vector3(0, 0.7, 0));
    this.camera.lookAt(focus);
    const desiredFov = 59 + Math.min(12, drop * 0.22) + (air ? 3 : 0);
    this.camera.fov += (desiredFov - this.camera.fov) * Math.min(1, dt * 4.5);
    this.camera.updateProjectionMatrix();
  }

  private emitHud() {
    if (!this.currentLevel) return;
    const target = this.targetIndex < this.currentLevel.targets.length
      ? this.levelManager.getTargetPosition(this.targetIndex, this.tmpTarget)
      : this.playerPos;
    const dropLeft = Math.max(0, this.playerPos.y - target.y);
    const stateLabel = this.state === 'ready'
      ? 'ГОТОВ К ПРЫЖКУ'
      : this.state === 'jump'
        ? 'ТОЛЧОК'
        : this.state === 'air'
          ? (this.simTime <= this.landingPrepUntil ? 'ГОТОВ К ПОСАДКЕ' : 'В ПОЛЁТЕ')
          : this.state === 'fail'
            ? 'МИМО'
            : 'ПОСАДКА';
    this.callbacks.onHud({
      level: this.currentLevel,
      stage: Math.min(this.targetIndex + 1, this.currentLevel.targets.length),
      stageCount: this.currentLevel.targets.length,
      score: this.score,
      combo: this.combo,
      dropLeft,
      state: stateLabel,
      target: this.currentLevel.targets[this.targetIndex]?.label ?? 'ФИНИШ',
      chain: this.chain.slice(-4).join(' + ')
    });
  }

  private resetStageTricks() {
    this.chain = [];
    this.stageTrickPoints = 0;
    this.stageTricks = 0;
    this.stageUnique.clear();
    this.landingPrepUntil = -1;
  }

  private rotationAlignmentError() {
    const wrap = (value: number) => Math.atan2(Math.sin(value), Math.cos(value));
    return Math.hypot(wrap(this.trickRotation.x), wrap(this.trickRotation.y), wrap(this.trickRotation.z));
  }

  private approach(current: number, target: number, step: number) {
    if (current < target) return Math.min(target, current + step);
    if (current > target) return Math.max(target, current - step);
    return current;
  }

  private freshStats(): DropRunStats {
    return { score: 0, falls: 0, tricks: 0, uniqueTricks: 0, perfectLandings: 0, cleanLandings: 0, bestCombo: 1, totalDrop: 0 };
  }

  private createLighting() {
    const hemi = new THREE.HemisphereLight(0xb7d7ff, 0x161b25, 1.7);
    this.scene.add(hemi);
    const sun = new THREE.DirectionalLight(0xffe1bd, 3.3);
    sun.position.set(-28, 48, 22);
    sun.castShadow = true;
    sun.shadow.mapSize.set(2048, 2048);
    sun.shadow.camera.near = 1;
    sun.shadow.camera.far = 180;
    sun.shadow.camera.left = -70;
    sun.shadow.camera.right = 70;
    sun.shadow.camera.top = 70;
    sun.shadow.camera.bottom = -70;
    this.scene.add(sun);
  }

  private applyTheme(theme: DropLevelSpec['theme']) {
    const palette: Record<DropLevelSpec['theme'], { bg: number; fog: number; density: number }> = {
      sunset: { bg: 0xb78b8d, fog: 0xb78b8d, density: 0.006 },
      city: { bg: 0x8fa8c2, fog: 0x8fa8c2, density: 0.0065 },
      industrial: { bg: 0x7d8996, fog: 0x7d8996, density: 0.007 },
      night: { bg: 0x263247, fog: 0x263247, density: 0.0085 },
      final: { bg: 0x161e30, fog: 0x161e30, density: 0.009 }
    };
    const colors = palette[theme];
    this.scene.background = new THREE.Color(colors.bg);
    this.scene.fog = new THREE.FogExp2(colors.fog, colors.density);
  }

  private resize() {
    const width = Math.max(1, innerWidth);
    const height = Math.max(1, innerHeight);
    this.renderer.setSize(width, height, false);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
  }
}
