import * as THREE from 'three';
import levelsJson from './drop_levels.json';
import { DropAudioSystem } from './DropAudioSystem';
import { DropAvatarSystem } from './DropAvatarSystem';
import { DropInput } from './DropInput';
import { DropLevelManager } from './DropLevelManager';
import { DropPhysicsSystem } from './DropPhysicsSystem';
import type { DropLevelSpec, DropRunStats, DropSurface, LandingResult, TrickEvent, TrickKind } from './DropTypes';

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
  private camera = new THREE.PerspectiveCamera(56, 1, 0.05, 600);
  private input = new DropInput();
  private levelManager = new DropLevelManager(this.scene);
  private physics = new DropPhysicsSystem();
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
  private preStepVelocity = new THREE.Vector3();
  private pendingLaunchVelocity = new THREE.Vector3();
  private facingYaw = 0;
  private cameraYaw = 0.12;
  private cameraPitch = 0.43;
  private cameraZoom = 1;
  private cameraImpactKick = 0;
  private targetIndex = 0;
  private standingSurface = -1;
  private stateTimer = 0;
  private stageAirTime = 0;
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
  private footstepTimer = 0;
  private stats: DropRunStats = this.freshStats();
  private tmpTarget = new THREE.Vector3();
  private tmpStanding = new THREE.Vector3();
  private tmpCamera = new THREE.Vector3();
  private tmpFocus = new THREE.Vector3();
  private tmpDirection = new THREE.Vector3();
  private tmpOrbit = new THREE.Vector3();
  private tmpMidpoint = new THREE.Vector3();
  private tmpLift = new THREE.Vector3();
  private tmpMoveForward = new THREE.Vector3();
  private tmpMoveRight = new THREE.Vector3();
  private tmpMove = new THREE.Vector3();
  private tmpDesired = new THREE.Vector3();
  private readonly upAxis = new THREE.Vector3(0, 1, 0);

  constructor(host: HTMLElement, callbacks: DropCallbacks) {
    this.host = host;
    this.callbacks = callbacks;
  }

  async init() {
    this.renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
    this.renderer.setPixelRatio(Math.min(devicePixelRatio, 1.55));
    this.renderer.setSize(innerWidth, innerHeight);
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.0;
    this.host.appendChild(this.renderer.domElement);
    this.input.attachCameraSurface(this.renderer.domElement);

    this.createLighting();
    await Promise.all([this.levelManager.init(), this.avatar.init(), this.physics.init()]);
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
    this.cameraYaw = 0.12;
    this.cameraPitch = 0.43;
    this.cameraZoom = 1;
    this.cameraImpactKick = 0;
    this.stateTimer = 0;
    this.stageAirTime = 0;
    this.score = 0;
    this.combo = 1;
    this.chain = [];
    this.trickRotation.set(0, 0, 0);
    this.trickTarget.set(0, 0, 0);
    this.pendingLaunchVelocity.set(0, 0, 0);
    this.finishPending = false;
    this.footstepTimer = 0;
    this.stats = this.freshStats();
    this.resetStageTricks();
    this.applyTheme(level.theme);

    await this.levelManager.load(level);
    const origins = [this.levelManager.getSurfaceBasePosition(-1, new THREE.Vector3()).clone()];
    for (let i = 0; i < level.targets.length; i += 1) {
      origins.push(this.levelManager.getSurfaceBasePosition(i, new THREE.Vector3()).clone());
    }
    this.physics.load(level, origins);

    this.levelManager.getStartPosition(this.playerPos);
    this.playerPos.y += 0.035;
    this.physics.teleportFoot(this.playerPos);
    this.physics.getFootPosition(this.playerPos);
    this.stageStartY = this.playerPos.y;
    this.faceTarget();
    this.levelManager.setActiveTarget(0);
    this.avatar.setVisible(true);
    this.running = true;
    this.lastFrame = performance.now();
    this.snapCameraToRoute();
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
    const dt = Math.min(1 / 30, Math.max(0, (now - this.lastFrame) / 1000));
    this.lastFrame = now;
    this.input.update();
    this.updateCameraInput();

    if (this.running && !this.paused) {
      this.simTime += dt;
      this.levelManager.update(this.simTime);
      this.preparePhysics(dt);
      this.physics.getVelocity(this.preStepVelocity);
      this.physics.step(dt, this.simTime);
      this.physics.getFootPosition(this.playerPos);
      this.physics.getVelocity(this.velocity);
      this.resolvePhysics(dt);
      this.updateTrickRotation(dt);
      this.updateAvatar(dt);
      this.emitHud();
    }

    this.cameraImpactKick *= Math.exp(-dt * 7.5);
    this.updateCamera(dt);
    this.renderer.render(this.scene, this.camera);
  }

  private preparePhysics(dt: number) {
    if (this.state === 'ready') {
      this.updateGroundMovement(dt);
      if (this.input.consumeJump()) this.beginTakeoff();
      this.input.consumeTricks();
      this.input.consumeLand();
      return;
    }

    if (this.state === 'jump') {
      this.physics.setHorizontalVelocity(0, 0);
      this.input.consumeTricks();
      this.input.consumeLand();
      return;
    }

    if (this.state === 'air') {
      for (const trick of this.input.consumeTricks()) this.queueTrick(trick);
      if (this.input.consumeLand()) this.landingPrepUntil = this.simTime + 0.9;
      this.updateAirControl(dt);
      return;
    }

    if (this.state === 'land') {
      this.physics.setHorizontalVelocity(0, 0);
      return;
    }

    this.input.consumeTricks();
    this.input.consumeLand();
  }

  private resolvePhysics(dt: number) {
    if (this.state === 'ready') {
      const expected = this.levelManager.getStandingPosition(this.standingSurface, this.tmpStanding);
      const grounded = this.physics.isGroundedOn(this.standingSurface, 0.2);
      if (!grounded && this.velocity.y < -0.8 && this.playerPos.y < expected.y - 0.18) {
        this.respawnStanding(false);
      }
      return;
    }

    if (this.state === 'jump') {
      this.stateTimer -= dt;
      if (this.stateTimer <= 0) {
        this.physics.setVelocity(
          this.pendingLaunchVelocity.x,
          this.pendingLaunchVelocity.y,
          this.pendingLaunchVelocity.z
        );
        this.audio.jump();
        this.state = 'air';
        this.stageAirTime = 0;
      }
      return;
    }

    if (this.state === 'air') {
      this.stageAirTime += dt;
      const target = this.levelManager.getTargetPosition(this.targetIndex, this.tmpTarget);
      const spec = this.levelManager.getTargetSpec(this.targetIndex);
      if (!spec) return;

      const groundedSurface = this.physics.groundedSurface(0.2);
      if (groundedSurface === this.targetIndex && this.preStepVelocity.y <= 0.2) {
        const dx = this.playerPos.x - target.x;
        const dz = this.playerPos.z - target.z;
        this.land(target, Math.hypot(dx, dz), Math.abs(this.preStepVelocity.y));
        return;
      }

      // Rapier may report the launch roof for a few solver frames after takeoff.
      // That is expected contact separation, not a wrong landing. Only a different
      // surface can fail the jump immediately. Returning to the launch surface later
      // still counts as a miss.
      if (groundedSurface !== null && groundedSurface !== this.standingSurface && this.stageAirTime > 0.32) {
        this.miss();
        return;
      }
      if (
        groundedSurface === this.standingSurface
        && this.stageAirTime > 0.78
        && this.preStepVelocity.y < -0.25
      ) {
        this.miss();
        return;
      }

      if (this.playerPos.y < target.y - 4.2 || this.playerPos.y < -1.5) {
        this.miss();
      }
      return;
    }

    this.stateTimer -= dt;
    if (this.state === 'land') {
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
      this.respawnStanding(false);
      this.stageStartY = this.playerPos.y;
      this.trickRotation.set(0, 0, 0);
      this.trickTarget.set(0, 0, 0);
      this.resetStageTricks();
      this.faceTarget();
    }
  }

  private updateCameraInput() {
    const look = this.input.consumeLook();
    if (look.x !== 0 || look.y !== 0) {
      this.cameraYaw -= look.x * 0.006;
      this.cameraPitch = THREE.MathUtils.clamp(this.cameraPitch - look.y * 0.0045, 0.12, 1.0);
    }
    const zoom = this.input.consumeZoom();
    if (zoom !== 0) this.cameraZoom = THREE.MathUtils.clamp(this.cameraZoom + zoom * 0.0009, 0.66, 1.5);
  }

  private updateGroundMovement(dt: number) {
    const spec = this.currentStandingSpec();
    if (!spec || !this.physics.isGroundedOn(this.standingSurface, 0.2)) {
      this.physics.setHorizontalVelocity(0, 0);
      return;
    }

    const inputLength = Math.hypot(this.input.moveX, this.input.moveZ);
    if (inputLength < 0.05) {
      const current = this.physics.getVelocity(this.tmpMove);
      const damping = Math.exp(-dt * 13);
      this.physics.setHorizontalVelocity(current.x * damping, current.z * damping);
      this.footstepTimer = 0;
      this.faceTarget();
      return;
    }

    const forward = this.tmpMoveForward.copy(this.playerPos).sub(this.camera.position);
    forward.y = 0;
    if (forward.lengthSq() < 0.0001) forward.set(Math.sin(this.facingYaw), 0, Math.cos(this.facingYaw));
    else forward.normalize();
    const right = this.tmpMoveRight.set(forward.z, 0, -forward.x);
    const move = this.tmpMove.set(0, 0, 0)
      .addScaledVector(forward, this.input.moveZ)
      .addScaledVector(right, this.input.moveX);
    if (move.lengthSq() > 1) move.normalize();

    const center = this.levelManager.getStandingPosition(this.standingSurface, this.tmpStanding);
    const speed = 2.35;
    const desired = this.tmpDesired.copy(this.playerPos).addScaledVector(move, speed * dt);
    const margin = 0.43;
    const maxX = Math.max(0.08, spec.size[0] * 0.5 - margin);
    const maxZ = Math.max(0.08, spec.size[1] * 0.5 - margin);
    desired.x = THREE.MathUtils.clamp(desired.x, center.x - maxX, center.x + maxX);
    desired.z = THREE.MathUtils.clamp(desired.z, center.z - maxZ, center.z + maxZ);

    const vx = (desired.x - this.playerPos.x) / Math.max(0.001, dt);
    const vz = (desired.z - this.playerPos.z) / Math.max(0.001, dt);
    this.physics.setHorizontalVelocity(vx, vz);
    if (Math.hypot(vx, vz) > 0.08) this.facingYaw = Math.atan2(vx, vz);

    this.footstepTimer -= dt;
    if (this.footstepTimer <= 0) {
      this.audio.footstep(Math.min(1, Math.hypot(vx, vz) / speed));
      this.footstepTimer = 0.44;
    }
  }

  private updateAirControl(dt: number) {
    const inputLength = Math.hypot(this.input.moveX, this.input.moveZ);
    if (inputLength < 0.02) return;
    const forward = this.tmpMoveForward.copy(this.playerPos).sub(this.camera.position);
    forward.y = 0;
    if (forward.lengthSq() < 0.0001) forward.set(Math.sin(this.facingYaw), 0, Math.cos(this.facingYaw));
    else forward.normalize();
    const right = this.tmpMoveRight.set(forward.z, 0, -forward.x);
    const move = this.tmpMove.set(0, 0, 0)
      .addScaledVector(forward, this.input.moveZ)
      .addScaledVector(right, this.input.moveX);
    if (move.lengthSq() > 1) move.normalize();

    const v = this.physics.getVelocity(this.velocity);
    const accel = 2.25;
    let vx = v.x + move.x * accel * dt;
    let vz = v.z + move.z * accel * dt;
    const horizontal = Math.hypot(vx, vz);
    const maxHorizontal = 8.0;
    if (horizontal > maxHorizontal) {
      vx = vx / horizontal * maxHorizontal;
      vz = vz / horizontal * maxHorizontal;
    }
    this.physics.setVelocity(vx, v.y, vz);
  }

  private beginTakeoff() {
    const target = this.levelManager.getTargetPosition(this.targetIndex, this.tmpTarget);
    const dx = target.x - this.playerPos.x;
    const dz = target.z - this.playerPos.z;
    const horizontal = Math.hypot(dx, dz);
    const direction = this.tmpDirection.set(dx, 0, dz);
    if (direction.lengthSq() < 0.0001) direction.set(Math.sin(this.facingYaw), 0, Math.cos(this.facingYaw));
    else direction.normalize();

    const drop = Math.max(0.5, this.playerPos.y - target.y);
    const jumpUp = 2.15 + Math.min(0.45, drop * 0.025);
    const flightTime = (jumpUp + Math.sqrt(jumpUp * jumpUp + 2 * GRAVITY * drop)) / GRAVITY;
    const desiredSpeed = THREE.MathUtils.clamp(horizontal / Math.max(0.65, flightTime) * 0.94, 2.3, 7.2);
    const current = this.physics.getVelocity(this.velocity);
    this.pendingLaunchVelocity.set(
      direction.x * desiredSpeed + current.x * 0.12,
      jumpUp,
      direction.z * desiredSpeed + current.z * 0.12
    );

    this.physics.setVelocity(0, 0, 0);
    this.facingYaw = Math.atan2(direction.x, direction.z);
    this.stageStartY = this.playerPos.y;
    this.stageAirTime = 0;
    this.landingPrepUntil = -1;
    this.state = 'jump';
    this.stateTimer = 0.13;
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
    this.callbacks.onTrick({ kind, label: data.label, points: data.points }, this.chain.join(' + '));
  }

  private updateTrickRotation(dt: number) {
    const prep = this.state === 'air' && this.simTime <= this.landingPrepUntil;
    const speed = prep ? 17.5 : 10.8;
    const maxStep = speed * dt;
    this.trickRotation.x = this.approach(this.trickRotation.x, this.trickTarget.x, maxStep);
    this.trickRotation.y = this.approach(this.trickRotation.y, this.trickTarget.y, maxStep);
    this.trickRotation.z = this.approach(this.trickRotation.z, this.trickTarget.z, maxStep);
  }

  private land(target: THREE.Vector3, distance: number, impact: number) {
    const spec = this.levelManager.getTargetSpec(this.targetIndex);
    if (!spec) return;

    this.physics.getFootPosition(this.playerPos);
    this.physics.setVelocity(0, 0, 0);

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
    } else if (impact >= 11.5 && prepared && alignment < 0.55) {
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
    this.cameraImpactKick = Math.min(0.16, 0.035 + impact * 0.006);
    this.state = 'land';
    this.stateTimer = grade === 'roll' ? 0.72 : 0.48;
    this.standingSurface = this.targetIndex;
    this.targetIndex += 1;
    this.finishPending = this.targetIndex >= this.currentLevel.targets.length;
    if (!this.finishPending) this.levelManager.setActiveTarget(this.targetIndex);
  }

  private miss() {
    if (this.state === 'fail') return;
    this.stats.falls += 1;
    this.score = Math.max(0, this.score - 120);
    this.stats.score = this.score;
    this.combo = 1;
    this.state = 'fail';
    this.stateTimer = 0.62;
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

  private respawnStanding(playSound = false) {
    if (this.standingSurface < 0) this.levelManager.getStartPosition(this.tmpStanding);
    else this.levelManager.getStandingPosition(this.standingSurface, this.tmpStanding);
    this.tmpStanding.y += 0.035;
    this.physics.teleportFoot(this.tmpStanding);
    this.physics.getFootPosition(this.playerPos);
    this.velocity.set(0, 0, 0);
    if (playSound) this.audio.landing(2);
  }

  private currentStandingSpec(): DropSurface | null {
    return this.standingSurface < 0
      ? this.currentLevel?.start ?? null
      : this.currentLevel?.targets[this.standingSurface] ?? null;
  }

  private faceTarget() {
    if (!this.currentLevel || this.targetIndex >= this.currentLevel.targets.length) return;
    const target = this.levelManager.getTargetPosition(this.targetIndex, this.tmpTarget);
    const dx = target.x - this.playerPos.x;
    const dz = target.z - this.playerPos.z;
    if (Math.hypot(dx, dz) > 0.01) this.facingYaw = Math.atan2(dx, dz);
  }

  private updateAvatar(dt: number) {
    let avatarState: 'idle' | 'walk' | 'run' | 'jump' | 'air' | 'land' | 'roll' | 'fail' = 'idle';
    const horizontalSpeed = Math.hypot(this.velocity.x, this.velocity.z);
    if (this.state === 'ready' && horizontalSpeed > 0.18) avatarState = horizontalSpeed > 3.7 ? 'run' : 'walk';
    if (this.state === 'jump') avatarState = 'jump';
    if (this.state === 'air') avatarState = 'air';
    if (this.state === 'fail') avatarState = 'fail';
    if (this.state === 'land') avatarState = this.simTime <= this.landingPrepUntil ? 'roll' : 'land';
    this.avatar.update(dt, this.playerPos, this.facingYaw, avatarState, this.trickRotation, true);
  }

  private routeCameraPose() {
    const target = this.targetIndex < this.currentLevel.targets.length
      ? this.levelManager.getTargetPosition(this.targetIndex, this.tmpTarget)
      : this.playerPos;
    const direction = this.tmpDirection.copy(target).sub(this.playerPos);
    const drop = Math.max(0, this.playerPos.y - target.y);
    direction.y = 0;
    const horizontal = direction.length();
    if (horizontal < 0.001) direction.set(Math.sin(this.facingYaw), 0, Math.cos(this.facingYaw));
    else direction.multiplyScalar(1 / horizontal);
    const orbit = this.tmpOrbit.copy(direction).applyAxisAngle(this.upAxis, this.cameraYaw);
    const air = this.state === 'air' || this.state === 'jump';

    const playerWeight = air ? 0.58 : 0.52;
    const midpoint = this.tmpMidpoint.copy(target).lerp(this.playerPos, playerWeight);
    const baseRadius = air ? 7.4 + Math.min(3, horizontal * 0.1) : 8.6 + Math.min(3.2, horizontal * 0.12);
    const radius = baseRadius * this.cameraZoom;
    const horizontalRadius = radius * Math.cos(this.cameraPitch);
    const verticalRadius = radius * Math.sin(this.cameraPitch);
    const desired = this.tmpCamera.copy(midpoint)
      .addScaledVector(orbit, -horizontalRadius)
      .add(this.tmpLift.set(0, verticalRadius + (air ? 0.9 : 1.25) + Math.min(1.2, drop * 0.035) + this.cameraImpactKick * 2.5, 0));
    const focus = this.tmpFocus.copy(target).lerp(this.playerPos, air ? 0.6 : 0.56).add(this.tmpLift.set(0, 0.62 - this.cameraImpactKick, 0));
    return { desired, focus, drop, air };
  }

  private snapCameraToRoute() {
    if (!this.currentLevel) return;
    const { desired, focus, drop } = this.routeCameraPose();
    this.camera.position.copy(desired);
    this.camera.lookAt(focus);
    this.camera.fov = 52 + Math.min(5, drop * 0.1);
    this.camera.updateProjectionMatrix();
  }

  private updateCamera(dt: number) {
    if (!this.currentLevel) return;
    const { desired, focus, drop, air } = this.routeCameraPose();
    this.camera.position.lerp(desired, 1 - Math.exp(-(air ? 6.2 : 9.2) * dt));
    this.camera.lookAt(focus);
    const desiredFov = (air ? 56 : 52) + Math.min(6, drop * 0.11);
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
        ? 'ПОДГОТОВКА'
        : this.state === 'air'
          ? (this.simTime <= this.landingPrepUntil ? 'ГОТОВ К ПОСАДКЕ' : 'В ПОЛЁТЕ')
          : this.state === 'fail'
            ? 'ПРОМАХ'
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
    this.stageAirTime = 0;
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
    const hemi = new THREE.HemisphereLight(0xc8dcf5, 0x202632, 1.15);
    this.scene.add(hemi);
    const sun = new THREE.DirectionalLight(0xffdfb0, 2.25);
    sun.position.set(-26, 46, 18);
    sun.castShadow = true;
    sun.shadow.mapSize.set(2048, 2048);
    sun.shadow.camera.near = 1;
    sun.shadow.camera.far = 180;
    sun.shadow.camera.left = -70;
    sun.shadow.camera.right = 70;
    sun.shadow.camera.top = 70;
    sun.shadow.camera.bottom = -70;
    sun.shadow.bias = -0.00012;
    this.scene.add(sun);
  }

  private applyTheme(theme: DropLevelSpec['theme']) {
    const palette: Record<DropLevelSpec['theme'], { bg: number; fog: number; density: number }> = {
      sunset: { bg: 0xa8b2be, fog: 0xc2b3a8, density: 0.0035 },
      city: { bg: 0x94abc1, fog: 0x9fb1c1, density: 0.0042 },
      industrial: { bg: 0x8695a5, fog: 0x8f9aa6, density: 0.0048 },
      night: { bg: 0x263247, fog: 0x2c394d, density: 0.0062 },
      final: { bg: 0x161e30, fog: 0x1d2739, density: 0.0068 }
    };
    const colors = palette[theme];
    if (!this.levelManager.hasSky()) this.scene.background = new THREE.Color(colors.bg);
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
