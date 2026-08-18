import * as THREE from 'three';
import RAPIER from '@dimforge/rapier3d-compat';
import type { Input } from './Input';

type HitHandler = (collider: RAPIER.Collider, impactSpeed: number, normal: THREE.Vector3) => void;

export class PlayerController {
  readonly body: RAPIER.RigidBody;
  readonly collider: RAPIER.Collider;

  private controller: RAPIER.KinematicCharacterController;
  private verticalVelocity = 0;
  private grounded = false;
  private coyote = 0;
  private jumpBuffer = 0;
  private wallRun = 0;
  private wallNormal = new THREE.Vector3();
  private speed = 0;
  private bobTime = 0;
  private onHit: HitHandler;
  private respawnPoint = new THREE.Vector3();

  constructor(world: RAPIER.World, footSpawn: THREE.Vector3, onHit: HitHandler) {
    this.onHit = onHit;
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
    this.body.setTranslation({ x: footPosition.x, y: footPosition.y + 1, z: footPosition.z }, true);
    this.body.setNextKinematicTranslation({ x: footPosition.x, y: footPosition.y + 1, z: footPosition.z });
    this.verticalVelocity = 0;
    this.wallRun = 0;
  }

  prepare(dt: number, input: Input) {
    if (input.consumeJumpPress()) this.jumpBuffer = 0.14;
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

    if (this.jumpBuffer > 0 && (this.grounded || this.coyote > 0 || this.wallRun > 0)) {
      this.verticalVelocity = this.wallRun > 0 ? 7.7 : 7.2;
      this.jumpBuffer = 0;
      this.coyote = 0;
      if (this.wallRun > 0) {
        wish.addScaledVector(this.wallNormal, 1.35).normalize();
        this.wallRun = 0;
      }
      this.grounded = false;
    }

    const gravity = this.wallRun > 0 && input.moveZ > 0.15 ? 5.8 : 19.5;
    this.verticalVelocity -= gravity * dt;
    this.verticalVelocity = Math.max(this.verticalVelocity, -18);

    if (this.wallRun > 0 && input.moveZ > 0.15) {
      const tangent = new THREE.Vector3().crossVectors(new THREE.Vector3(0, 1, 0), this.wallNormal).normalize();
      if (tangent.dot(forward) < 0) tangent.negate();
      wish.lerp(tangent, 0.72).normalize();
      this.speed = Math.max(this.speed, 6.4);
      this.verticalVelocity = Math.max(this.verticalVelocity, -1.2);
    }

    if (input.crouch && this.grounded && this.speed > 5.8) {
      this.speed = Math.min(10.2, this.speed + 4.2 * dt);
    }

    const desired = {
      x: wish.x * this.speed * dt,
      y: this.verticalVelocity * dt,
      z: wish.z * this.speed * dt
    };

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
    if (this.grounded && this.verticalVelocity < 0) this.verticalVelocity = 0;
    if (!wasGrounded && this.grounded) this.speed *= 0.97;

    let horizontalWall: THREE.Vector3 | null = null;
    for (let i = 0; i < this.controller.numComputedCollisions(); i += 1) {
      const hit = this.controller.computedCollision(i);
      const normal = new THREE.Vector3(hit.normal1.x, hit.normal1.y, hit.normal1.z);
      if (Math.abs(normal.y) < 0.35) horizontalWall = normal;
      this.onHit(hit.collider, Math.hypot(this.speed, this.verticalVelocity), normal);
    }

    if (!this.grounded && horizontalWall && input.moveZ > 0.22 && this.speed > 4.7) {
      this.wallNormal.copy(horizontalWall).normalize();
      this.wallRun = Math.max(this.wallRun, 0.38);
    }

    if (!this.grounded && horizontalWall && input.jump && this.verticalVelocity < 2.2) {
      this.verticalVelocity = Math.max(this.verticalVelocity, 3.4);
    }

    if (this.grounded && this.speed > 0.5) this.bobTime += dt * (6.4 + this.speed * 0.55);
  }

  getFootPosition(target = new THREE.Vector3()) {
    const p = this.body.translation();
    return target.set(p.x, p.y - 1, p.z);
  }

  getEyePosition(target = new THREE.Vector3()) {
    const p = this.body.translation();
    const crouch = this.grounded && this.speed > 5.8 ? 0.08 : 0;
    const bob = this.grounded && this.speed > 0.7 ? Math.sin(this.bobTime) * Math.min(0.045, this.speed * 0.006) : 0;
    return target.set(p.x, p.y + 0.67 - crouch + bob, p.z);
  }

  getCameraRoll(input: Input) {
    const strafe = -input.moveX * 0.018;
    const wall = this.wallRun > 0 ? Math.sign(this.wallNormal.x + this.wallNormal.z) * 0.07 : 0;
    return strafe + wall;
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
}
