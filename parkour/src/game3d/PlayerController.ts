import * as THREE from 'three';
import RAPIER from '@dimforge/rapier3d-compat';
import type { Input } from './Input';

type HitHandler = (collider: RAPIER.Collider, impactSpeed: number, normal: THREE.Vector3) => void;

export type ParkourEvent = {
  type: 'vault' | 'mantle' | 'wall-kick' | 'perfect-land' | 'roll-land' | 'hard-land';
  label: string;
  intensity: number;
};

type TraversalKind = 'none' | 'vault' | 'mantle';

type LedgeProbe = {
  top: THREE.Vector3;
  wallNormal: THREE.Vector3;
  height: number;
};

export class PlayerController {
  readonly body: RAPIER.RigidBody;
  readonly collider: RAPIER.Collider;

  private world: RAPIER.World;
  private controller: RAPIER.KinematicCharacterController;
  private verticalVelocity = 0;
  private grounded = false;
  private coyote = 0;
  private jumpBuffer = 0;
  private wallRun = 0;
  private wallRunBudget = 0.95;
  private wallCooldown = 0;
  private wallNormal = new THREE.Vector3();
  private wallKickVelocity = new THREE.Vector3();
  private speed = 0;
  private bobTime = 0;
  private onHit: HitHandler;
  private onParkour: (event: ParkourEvent) => void;
  private respawnPoint = new THREE.Vector3();
  private sliding = false;
  private traversal: TraversalKind = 'none';
  private traversalElapsed = 0;
  private traversalDuration = 0;
  private traversalStart = new THREE.Vector3();
  private traversalTarget = new THREE.Vector3();
  private traversalCooldown = 0;

  constructor(
    world: RAPIER.World,
    footSpawn: THREE.Vector3,
    onHit: HitHandler,
    onParkour: (event: ParkourEvent) => void = () => {}
  ) {
    this.world = world;
    this.onHit = onHit;
    this.onParkour = onParkour;
    this.body = world.createRigidBody(
      RAPIER.RigidBodyDesc.kinematicPositionBased().setTranslation(footSpawn.x, footSpawn.y + 1, footSpawn.z)
    );
    this.collider = world.createCollider(
      RAPIER.ColliderDesc.capsule(0.65, 0.35).setFriction(0.0),
      this.body
    );
    this.controller = world.createCharacterController(0.035);
    this.controller.setUp({ x: 0, y: 1, z: 0 });
    this.controller.enableAutostep(0.48, 0.22, true);
    this.controller.enableSnapToGround(0.28);
    this.controller.setMaxSlopeClimbAngle(50 * Math.PI / 180);
    this.controller.setMinSlopeSlideAngle(64 * Math.PI / 180);
    this.controller.setApplyImpulsesToDynamicBodies(true);
    this.respawnPoint.copy(footSpawn);
  }

  setRespawn(footPosition: THREE.Vector3) {
    this.respawnPoint.copy(footPosition);
  }

  respawn() {
    this.teleport(this.respawnPoint);
  }

  teleport(footPosition: THREE.Vector3) {
    this.exitSlide(true);
    this.traversal = 'none';
    this.body.setTranslation({ x: footPosition.x, y: footPosition.y + 1, z: footPosition.z }, true);
    this.body.setNextKinematicTranslation({ x: footPosition.x, y: footPosition.y + 1, z: footPosition.z });
    this.verticalVelocity = 0;
    this.wallRun = 0;
    this.wallRunBudget = 0.95;
    this.wallCooldown = 0;
    this.wallKickVelocity.set(0, 0, 0);
  }

  prepare(dt: number, input: Input) {
    this.traversalCooldown = Math.max(0, this.traversalCooldown - dt);
    this.wallCooldown = Math.max(0, this.wallCooldown - dt);

    if (this.traversal !== 'none') {
      this.updateTraversal(dt);
      return;
    }

    const jumpPressed = input.consumeJumpPress();
    if (jumpPressed) this.jumpBuffer = 0.14;
    this.jumpBuffer = Math.max(0, this.jumpBuffer - dt);
    this.coyote = this.grounded ? 0.13 : Math.max(0, this.coyote - dt);
    this.wallRun = Math.max(0, this.wallRun - dt);

    const forward = new THREE.Vector3(-Math.sin(input.yaw), 0, -Math.cos(input.yaw));
    const right = new THREE.Vector3(Math.cos(input.yaw), 0, -Math.sin(input.yaw));
    const wish = new THREE.Vector3();
    wish.addScaledVector(forward, input.moveZ).addScaledVector(right, input.moveX);
    if (wish.lengthSq() > 1) wish.normalize();

    const targetSpeed = input.sprint ? 8.4 : 5.2;
    const control = this.grounded ? 13 : 4.6;
    this.speed += (targetSpeed * Math.min(1, wish.length()) - this.speed) * Math.min(1, control * dt);

    if (this.grounded && input.crouch && this.speed > 4.6) this.enterSlide();
    else if ((!input.crouch || !this.grounded) && this.sliding) this.exitSlide(false);

    if (jumpPressed && this.grounded && input.moveZ > 0.18 && this.speed > 3.2 && this.tryVault(forward)) return;
    if (!this.grounded && input.jump && input.moveZ > 0.12 && this.verticalVelocity <= 2.3 && this.traversalCooldown <= 0 && this.tryMantle(forward)) return;

    if (jumpPressed && this.wallRun > 0 && this.wallRunBudget > 0) {
      this.verticalVelocity = 7.8;
      this.jumpBuffer = 0;
      this.wallKickVelocity.copy(this.wallNormal).multiplyScalar(5.6);
      this.wallRun = 0;
      this.wallRunBudget = 0;
      this.wallCooldown = 0.38;
      this.onParkour({ type: 'wall-kick', label: 'WALL KICK', intensity: 0.9 });
    } else if (this.jumpBuffer > 0 && (this.grounded || this.coyote > 0)) {
      this.exitSlide(true);
      this.verticalVelocity = 7.25;
      this.jumpBuffer = 0;
      this.coyote = 0;
      this.grounded = false;
    }

    if (this.wallRun > 0 && this.wallRunBudget > 0 && input.moveZ > 0.15) {
      this.wallRunBudget = Math.max(0, this.wallRunBudget - dt);
      const tangent = new THREE.Vector3().crossVectors(new THREE.Vector3(0, 1, 0), this.wallNormal).normalize();
      if (tangent.dot(forward) < 0) tangent.negate();
      wish.lerp(tangent, 0.76).normalize();
      this.speed = Math.max(this.speed, 6.45);
      this.verticalVelocity = Math.max(this.verticalVelocity, -1.05);
      if (this.wallRunBudget <= 0) {
        this.wallRun = 0;
        this.wallCooldown = 0.5;
      }
    }

    const gravity = this.wallRun > 0 && input.moveZ > 0.15 ? 5.4 : 19.5;
    this.verticalVelocity -= gravity * dt;
    this.verticalVelocity = Math.max(this.verticalVelocity, -18);

    if (this.sliding && this.grounded) this.speed = Math.min(10.3, this.speed + 3.8 * dt);

    const kickDecay = Math.exp(-5.2 * dt);
    const desired = {
      x: (wish.x * this.speed + this.wallKickVelocity.x) * dt,
      y: this.verticalVelocity * dt,
      z: (wish.z * this.speed + this.wallKickVelocity.z) * dt
    };
    this.wallKickVelocity.multiplyScalar(kickDecay);

    const downwardSpeedBeforeMove = Math.max(0, -this.verticalVelocity);
    this.controller.computeColliderMovement(this.collider, desired);
    const corrected = this.controller.computedMovement();
    const current = this.body.translation();
    this.body.setNextKinematicTranslation({
      x: current.x + corrected.x,
      y: current.y + corrected.y,
      z: current.z + corrected.z
    });

    const wasGrounded = this.grounded;
    this.grounded = this.controller.computedGrounded();
    if (!wasGrounded && this.grounded) this.handleLanding(downwardSpeedBeforeMove, input);
    if (this.grounded && this.verticalVelocity < 0) this.verticalVelocity = 0;
    if (this.grounded) {
      this.wallRunBudget = 0.95;
      this.wallRun = 0;
    }

    let horizontalWall: THREE.Vector3 | null = null;
    for (let i = 0; i < this.controller.numComputedCollisions(); i += 1) {
      const hit = this.controller.computedCollision(i);
      if (!hit) continue;
      const normal = new THREE.Vector3(hit.normal1.x, hit.normal1.y, hit.normal1.z);
      if (Math.abs(normal.y) < 0.35) horizontalWall = normal;
      const impact = normal.y > 0.55 ? Math.hypot(this.speed, downwardSpeedBeforeMove) : Math.hypot(this.speed, this.verticalVelocity);
      this.onHit(hit.collider, impact, normal);
    }

    if (!this.grounded && horizontalWall && input.moveZ > 0.22 && this.speed > 4.7 && this.wallCooldown <= 0 && this.wallRunBudget > 0) {
      this.wallNormal.copy(horizontalWall).normalize();
      this.wallRun = Math.max(this.wallRun, 0.15);
    }

    if (!this.grounded && horizontalWall && input.jump && this.verticalVelocity < 2.2 && this.wallRunBudget > 0) {
      this.verticalVelocity = Math.max(this.verticalVelocity, 3.25);
    }

    if (this.grounded && this.speed > 0.5) this.bobTime += dt * (6.4 + this.speed * 0.55);
  }

  getFootPosition(target = new THREE.Vector3()) {
    const p = this.body.translation();
    return target.set(p.x, p.y - 1, p.z);
  }

  getEyePosition(target = new THREE.Vector3()) {
    const p = this.body.translation();
    const slideDrop = this.sliding ? 0.58 : 0;
    const traversalDrop = this.traversal === 'mantle' ? 0.12 : 0;
    const bob = this.grounded && this.speed > 0.7 && !this.sliding
      ? Math.sin(this.bobTime) * Math.min(0.045, this.speed * 0.006)
      : 0;
    return target.set(p.x, p.y + 0.67 - slideDrop - traversalDrop + bob, p.z);
  }

  getCameraRoll(input: Input) {
    const strafe = -input.moveX * 0.018;
    const wall = this.wallRun > 0 ? Math.sign(this.wallNormal.x + this.wallNormal.z) * 0.075 : 0;
    const slide = this.sliding ? -input.moveX * 0.028 : 0;
    return strafe + wall + slide;
  }

  getSpeed() {
    return this.speed;
  }

  isGrounded() {
    return this.grounded;
  }

  isWallRunning() {
    return this.wallRun > 0;
  }

  isSliding() {
    return this.sliding;
  }

  getMotionState() {
    if (this.traversal === 'vault') return 'VAULT';
    if (this.traversal === 'mantle') return 'EDGE CLIMB';
    if (this.wallRun > 0) return 'WALL RUN';
    if (this.sliding) return 'SLIDE';
    return this.grounded ? 'FLOW' : 'AIR';
  }

  private handleLanding(downwardSpeed: number, input: Input) {
    if (downwardSpeed >= 11.5) {
      if (input.crouch && this.speed > 3.5) {
        this.speed *= 0.78;
        this.onParkour({ type: 'roll-land', label: 'ROLL LANDING', intensity: 0.75 });
      } else {
        this.speed *= 0.3;
        this.onParkour({ type: 'hard-land', label: 'HARD LANDING', intensity: 1 });
      }
      return;
    }
    if (downwardSpeed >= 7.2) {
      if (input.crouch && this.speed > 3.2) {
        this.speed *= 0.93;
        this.onParkour({ type: 'roll-land', label: 'ROLL LANDING', intensity: 0.55 });
      } else {
        this.speed *= 0.7;
      }
      return;
    }
    if (downwardSpeed >= 4.2 && this.speed > 4.4) {
      this.speed = Math.min(10.2, this.speed + 0.55);
      this.onParkour({ type: 'perfect-land', label: 'PERFECT LAND', intensity: 0.4 });
    }
  }

  private enterSlide() {
    if (this.sliding) return;
    this.sliding = true;
    this.collider.setShape(new RAPIER.Capsule(0.22, 0.35));
    this.collider.setTranslationWrtParent({ x: 0, y: -0.43, z: 0 });
  }

  private exitSlide(force: boolean) {
    if (!this.sliding) return;
    if (!force && !this.canStand()) return;
    this.sliding = false;
    this.collider.setShape(new RAPIER.Capsule(0.65, 0.35));
    this.collider.setTranslationWrtParent({ x: 0, y: 0, z: 0 });
  }

  private canStand() {
    const p = this.body.translation();
    const ray = new RAPIER.Ray({ x: p.x, y: p.y + 0.1, z: p.z }, { x: 0, y: 1, z: 0 });
    return !this.world.castRay(ray, 0.75, true, undefined, undefined, this.collider);
  }

  private tryVault(forward: THREE.Vector3) {
    const probe = this.probeLedge(forward, 1.75);
    if (!probe || probe.height < 0.42 || probe.height > 1.18) return false;
    const target = probe.top.clone().addScaledVector(forward, 1.05);
    target.y += 0.08;
    this.startTraversal('vault', target, 0.28);
    this.onParkour({ type: 'vault', label: 'VAULT', intensity: 0.45 });
    return true;
  }

  private tryMantle(forward: THREE.Vector3) {
    const probe = this.probeLedge(forward, 2.55);
    if (!probe || probe.height < 1.02 || probe.height > 2.05) return false;
    const target = probe.top.clone().addScaledVector(forward, 0.72);
    target.y += 0.08;
    this.startTraversal('mantle', target, 0.42);
    this.onParkour({ type: 'mantle', label: 'EDGE GRAB', intensity: 0.7 });
    return true;
  }

  private probeLedge(forward: THREE.Vector3, topProbeHeight: number): LedgeProbe | null {
    const p = this.body.translation();
    const footY = p.y - 1;
    const dir = forward.clone().normalize();
    const frontOrigin = { x: p.x, y: footY + 0.92, z: p.z };
    const frontRay = new RAPIER.Ray(frontOrigin, { x: dir.x, y: 0, z: dir.z });
    const frontHit = this.world.castRayAndGetNormal(frontRay, 0.9, true, undefined, undefined, this.collider);
    if (!frontHit || Math.abs(frontHit.normal.y) > 0.45) return null;

    const wallPoint = new THREE.Vector3(
      frontOrigin.x + dir.x * frontHit.timeOfImpact,
      frontOrigin.y,
      frontOrigin.z + dir.z * frontHit.timeOfImpact
    );
    const downOrigin = {
      x: wallPoint.x + dir.x * 0.34,
      y: footY + topProbeHeight,
      z: wallPoint.z + dir.z * 0.34
    };
    const downRay = new RAPIER.Ray(downOrigin, { x: 0, y: -1, z: 0 });
    const downHit = this.world.castRayAndGetNormal(downRay, topProbeHeight + 0.25, true, undefined, undefined, this.collider);
    if (!downHit || downHit.normal.y < 0.62) return null;

    const topY = downOrigin.y - downHit.timeOfImpact;
    const height = topY - footY;
    const top = new THREE.Vector3(downOrigin.x, topY, downOrigin.z);

    const clearanceRay = new RAPIER.Ray(
      { x: top.x, y: top.y + 0.18, z: top.z },
      { x: 0, y: 1, z: 0 }
    );
    const blocked = this.world.castRay(clearanceRay, 1.62, true, undefined, undefined, this.collider);
    if (blocked) return null;

    return {
      top,
      wallNormal: new THREE.Vector3(frontHit.normal.x, frontHit.normal.y, frontHit.normal.z),
      height
    };
  }

  private startTraversal(kind: Exclude<TraversalKind, 'none'>, targetFoot: THREE.Vector3, duration: number) {
    const p = this.body.translation();
    this.traversal = kind;
    this.traversalElapsed = 0;
    this.traversalDuration = duration;
    this.traversalStart.set(p.x, p.y, p.z);
    this.traversalTarget.copy(targetFoot).add(new THREE.Vector3(0, 1, 0));
    this.verticalVelocity = 0;
    this.wallRun = 0;
    this.wallCooldown = 0.25;
    this.traversalCooldown = duration + 0.2;
    this.exitSlide(true);
  }

  private updateTraversal(dt: number) {
    this.traversalElapsed += dt;
    const raw = Math.min(1, this.traversalElapsed / this.traversalDuration);
    const t = raw * raw * (3 - 2 * raw);
    const next = this.traversalStart.clone().lerp(this.traversalTarget, t);
    const arc = Math.sin(raw * Math.PI);
    next.y += arc * (this.traversal === 'vault' ? 0.48 : 0.24);
    this.body.setNextKinematicTranslation({ x: next.x, y: next.y, z: next.z });

    if (raw >= 1) {
      this.body.setTranslation({ x: this.traversalTarget.x, y: this.traversalTarget.y, z: this.traversalTarget.z }, true);
      this.body.setNextKinematicTranslation({ x: this.traversalTarget.x, y: this.traversalTarget.y, z: this.traversalTarget.z });
      if (this.traversal === 'mantle') this.speed = Math.max(3.8, this.speed * 0.82);
      this.traversal = 'none';
      this.grounded = false;
      this.verticalVelocity = -0.4;
    }
  }
}
