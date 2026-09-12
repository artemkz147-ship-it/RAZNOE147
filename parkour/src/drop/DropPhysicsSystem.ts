import RAPIER from '@dimforge/rapier3d-compat';
import * as THREE from 'three';
import type { DropLevelSpec, DropSurface } from './DropTypes';

const PLAYER_HALF_HEIGHT = 0.87;
const GROUND_Y = 0;

type PhysicalSurface = {
  index: number;
  spec: DropSurface;
  origin: THREE.Vector3;
  body: RAPIER.RigidBody;
  collider: RAPIER.Collider;
  height: number;
};

export class DropPhysicsSystem {
  private world: RAPIER.World | null = null;
  private playerBody: RAPIER.RigidBody | null = null;
  private playerCollider: RAPIER.Collider | null = null;
  private surfaces: PhysicalSurface[] = [];
  private time = 0;

  async init() {
    await RAPIER.init();
  }

  load(level: DropLevelSpec, origins: THREE.Vector3[]) {
    if (this.world) this.world.free();
    this.world = new RAPIER.World({ x: 0, y: -9.81, z: 0 });
    this.world.timestep = 1 / 60;
    this.surfaces = [];

    this.world.createCollider(
      RAPIER.ColliderDesc.cuboid(120, 0.35, 120)
        .setTranslation(0, GROUND_Y - 0.35, 0)
        .setFriction(0.9)
        .setRestitution(0)
    );

    const specs = [level.start, ...level.targets];
    specs.forEach((spec, listIndex) => {
      const origin = origins[listIndex]?.clone() ?? new THREE.Vector3(...spec.p);
      const index = listIndex - 1;
      const width = Math.max(0.7, spec.size[0]);
      const depth = Math.max(0.7, spec.size[1]);
      const moving = Boolean(spec.moving);
      const height = moving
        ? this.platformThickness(spec)
        : this.colliderHeight(spec, origin.y);
      const centerY = moving
        ? origin.y - height * 0.5
        : Math.max(GROUND_Y, origin.y - height) + height * 0.5;

      const bodyDesc = moving
        ? RAPIER.RigidBodyDesc.kinematicPositionBased()
        : RAPIER.RigidBodyDesc.fixed();
      bodyDesc
        .setTranslation(origin.x, centerY, origin.z)
        .setUserData({ surfaceIndex: index });
      const body = this.world!.createRigidBody(bodyDesc);
      const collider = this.world!.createCollider(
        RAPIER.ColliderDesc.cuboid(width * 0.5, height * 0.5, depth * 0.5)
          .setFriction(0.92)
          .setRestitution(0),
        body
      );
      this.surfaces.push({ index, spec, origin, body, collider, height });
    });

    const playerDesc = RAPIER.RigidBodyDesc.dynamic()
      .setTranslation(origins[0]?.x ?? 0, (origins[0]?.y ?? level.start.p[1]) + PLAYER_HALF_HEIGHT + 0.03, origins[0]?.z ?? 0)
      .setCanSleep(false)
      .setCcdEnabled(true)
      .setLinearDamping(0.1)
      .lockRotations();
    this.playerBody = this.world.createRigidBody(playerDesc);
    this.playerCollider = this.world.createCollider(
      RAPIER.ColliderDesc.capsule(0.55, 0.32)
        .setFriction(0.22)
        .setRestitution(0)
        .setDensity(1.05),
      this.playerBody
    );
  }

  step(dt: number, time: number) {
    if (!this.world || !this.playerBody) return;
    this.time = time;
    for (const surface of this.surfaces) {
      if (!surface.spec.moving) continue;
      const offset = this.movingOffset(surface.spec, time);
      surface.body.setNextKinematicTranslation({
        x: surface.origin.x + offset.x,
        y: surface.origin.y - surface.height * 0.5,
        z: surface.origin.z + offset.z
      });
    }
    this.world.timestep = Math.max(1 / 240, Math.min(1 / 30, dt));
    this.world.step();

    // Lightweight QA telemetry. It is not rendered and has no gameplay effect.
    const snapshot = this.debugSnapshot();
    document.documentElement.dataset.dropPhysics = JSON.stringify(snapshot);
  }

  teleportFoot(foot: THREE.Vector3) {
    if (!this.playerBody) return;
    this.playerBody.setTranslation({ x: foot.x, y: foot.y + PLAYER_HALF_HEIGHT, z: foot.z }, true);
    this.playerBody.setLinvel({ x: 0, y: 0, z: 0 }, true);
  }

  getFootPosition(target: THREE.Vector3) {
    if (!this.playerBody) return target.set(0, 0, 0);
    const p = this.playerBody.translation();
    return target.set(p.x, p.y - PLAYER_HALF_HEIGHT, p.z);
  }

  getVelocity(target: THREE.Vector3) {
    if (!this.playerBody) return target.set(0, 0, 0);
    const v = this.playerBody.linvel();
    return target.set(v.x, v.y, v.z);
  }

  setVelocity(x: number, y: number, z: number) {
    if (!this.playerBody) return;
    const current = this.playerBody.linvel();
    const groundedTakeoff = y > 1.0
      && Math.abs(current.y) < 0.35
      && this.groundedSurface(0.22) !== null;
    const push = groundedTakeoff ? 1.13 : 1;
    this.playerBody.setLinvel({ x: x * push, y, z: z * push }, true);
  }

  setHorizontalVelocity(x: number, z: number) {
    if (!this.playerBody) return;
    const v = this.playerBody.linvel();
    this.playerBody.setLinvel({ x, y: v.y, z }, true);
  }

  getSurfaceTop(surfaceIndex: number, target: THREE.Vector3) {
    const surface = this.surfaces.find((item) => item.index === surfaceIndex);
    if (!surface) return target.set(0, 0, 0);
    const offset = this.movingOffset(surface.spec, this.time);
    return target.set(surface.origin.x + offset.x, surface.origin.y, surface.origin.z + offset.z);
  }

  groundedSurface(tolerance = 0.16) {
    if (!this.playerBody || !this.playerCollider) return null;
    const velocity = this.playerBody.linvel();
    if (velocity.y > 0.8) return null;

    let bestContact: { index: number; distance: number } | null = null;
    const prediction = Math.max(0.055, tolerance);
    for (const surface of this.surfaces) {
      const contact = this.playerCollider.contactCollider(surface.collider, prediction);
      if (!contact) continue;
      if (contact.normal2.y < 0.42) continue;
      if (contact.distance > prediction) continue;
      if (!bestContact || contact.distance < bestContact.distance) {
        bestContact = { index: surface.index, distance: contact.distance };
      }
    }
    if (bestContact) return bestContact.index;

    const foot = this.getFootPosition(new THREE.Vector3());
    let bestFallback: { index: number; error: number } | null = null;
    for (const surface of this.surfaces) {
      const top = this.getSurfaceTop(surface.index, new THREE.Vector3());
      const halfX = surface.spec.size[0] * 0.5 + 0.05;
      const halfZ = surface.spec.size[1] * 0.5 + 0.05;
      if (Math.abs(foot.x - top.x) > halfX || Math.abs(foot.z - top.z) > halfZ) continue;
      const error = Math.abs(foot.y - top.y);
      if (error > Math.max(0.24, tolerance)) continue;
      if (!bestFallback || error < bestFallback.error) bestFallback = { index: surface.index, error };
    }
    return bestFallback?.index ?? null;
  }

  isGroundedOn(surfaceIndex: number, tolerance = 0.16) {
    return this.groundedSurface(tolerance) === surfaceIndex;
  }

  debugSnapshot() {
    const foot = this.getFootPosition(new THREE.Vector3());
    const velocity = this.getVelocity(new THREE.Vector3());
    return {
      foot: { x: foot.x, y: foot.y, z: foot.z },
      velocity: { x: velocity.x, y: velocity.y, z: velocity.z },
      groundedSurface: this.groundedSurface(0.24)
    };
  }

  private colliderHeight(spec: DropSurface, topY: number) {
    if (spec.kind === 'beam') return 0.7;
    if (spec.kind === 'factory' || spec.kind === 'unit') return Math.max(1.5, topY - GROUND_Y);
    if (spec.kind === 'pole') return Math.max(1.2, topY - GROUND_Y);
    return Math.max(1.8, topY - GROUND_Y);
  }

  private platformThickness(spec: DropSurface) {
    if (spec.kind === 'beam') return 0.55;
    if (spec.kind === 'pole') return 0.8;
    return 0.9;
  }

  private movingOffset(spec: DropSurface, time: number) {
    const offset = new THREE.Vector3();
    if (!spec.moving) return offset;
    const value = Math.sin(time * spec.moving.speed + (spec.moving.phase ?? 0)) * spec.moving.distance;
    if (spec.moving.axis === 'x') offset.x = value;
    else offset.z = value;
    return offset;
  }
}
