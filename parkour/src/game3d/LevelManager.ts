import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import RAPIER from '@dimforge/rapier3d-compat';
import type { LevelSpec, MoverSpec } from './types';
import { DestructionSystem } from './DestructionSystem';

type Mover = {
  spec: MoverSpec;
  object: THREE.Object3D;
  body: RAPIER.RigidBody;
  origin: THREE.Vector3;
};

export class LevelManager {
  readonly destruction: DestructionSystem;

  private scene: THREE.Scene;
  private world: RAPIER.World;
  private loader = new GLTFLoader();
  private levelRoot: THREE.Object3D | null = null;
  private staticBodies: RAPIER.RigidBody[] = [];
  private movers: Mover[] = [];
  private checkpointIndex = -1;
  private current: LevelSpec | null = null;

  constructor(scene: THREE.Scene, world: RAPIER.World, onBreak: (reward: number) => void) {
    this.scene = scene;
    this.world = world;
    this.destruction = new DestructionSystem(scene, world, onBreak);
  }

  async init() {
    await this.destruction.preload();
  }

  async load(level: LevelSpec) {
    this.clear();
    this.current = level;
    this.checkpointIndex = -1;

    const gltf = await this.loader.loadAsync(level.asset.replace('assets/levels/', 'assets3d/levels/'));
    this.levelRoot = gltf.scene;
    this.levelRoot.traverse((object) => {
      if (object instanceof THREE.Mesh) {
        object.castShadow = true;
        object.receiveShadow = true;
        if (object.material instanceof THREE.MeshStandardMaterial) object.material.envMapIntensity = 0.55;
      }
    });
    this.scene.add(this.levelRoot);

    for (const spec of level.colliders) {
      const body = this.world.createRigidBody(RAPIER.RigidBodyDesc.fixed().setTranslation(...spec.p));
      this.world.createCollider(
        RAPIER.ColliderDesc.cuboid(spec.s[0] / 2, spec.s[1] / 2, spec.s[2] / 2).setFriction(0.7),
        body
      );
      this.staticBodies.push(body);
    }

    await Promise.all(level.breakables.map((spec) => this.destruction.add(spec)));
    await Promise.all(level.movers.map((spec) => this.addMover(spec)));
  }

  update(time: number) {
    for (const mover of this.movers) {
      const offset = Math.sin(time * mover.spec.speed) * mover.spec.distance;
      const target = mover.origin.clone();
      if (mover.spec.axis === 'x') target.x += offset;
      else if (mover.spec.axis === 'y') target.y += offset;
      else target.z += offset;
      mover.body.setNextKinematicTranslation({ x: target.x, y: target.y, z: target.z });
      const p = mover.body.translation();
      const q = mover.body.rotation();
      mover.object.position.set(p.x, p.y, p.z);
      mover.object.quaternion.set(q.x, q.y, q.z, q.w);
    }
    this.destruction.update(time);
  }

  updateCheckpoints(playerFoot: THREE.Vector3) {
    if (!this.current) return null;
    let activated: THREE.Vector3 | null = null;
    for (let i = this.checkpointIndex + 1; i < this.current.checkpoints.length; i += 1) {
      const point = new THREE.Vector3(...this.current.checkpoints[i]);
      if (playerFoot.distanceTo(point) < 3.0) {
        this.checkpointIndex = i;
        activated = point;
      }
    }
    return activated;
  }

  reachedFinish(playerFoot: THREE.Vector3) {
    if (!this.current) return false;
    const finish = new THREE.Vector3(...this.current.finish);
    return playerFoot.distanceTo(finish) < 2.7;
  }

  getCheckpointCount() {
    return this.current?.checkpoints.length ?? 0;
  }

  getCheckpointIndex() {
    return this.checkpointIndex;
  }

  clear() {
    if (this.levelRoot) {
      this.scene.remove(this.levelRoot);
      this.levelRoot = null;
    }
    this.destruction.clear();
    for (const mover of this.movers) {
      this.scene.remove(mover.object);
      this.world.removeRigidBody(mover.body);
    }
    for (const body of this.staticBodies) this.world.removeRigidBody(body);
    this.movers = [];
    this.staticBodies = [];
  }

  private async addMover(spec: MoverSpec) {
    const gltf = await this.loader.loadAsync(spec.asset.replace('assets/props/', 'assets3d/props/'));
    const object = gltf.scene;
    object.position.set(...spec.p);
    object.traverse((child) => {
      if (child instanceof THREE.Mesh) {
        child.castShadow = true;
        child.receiveShadow = true;
      }
    });
    this.scene.add(object);

    const body = this.world.createRigidBody(RAPIER.RigidBodyDesc.kinematicPositionBased().setTranslation(...spec.p));
    this.world.createCollider(
      RAPIER.ColliderDesc.cuboid(spec.collider[0] / 2, spec.collider[1] / 2, spec.collider[2] / 2).setFriction(0.75),
      body
    );
    this.movers.push({ spec, object, body, origin: new THREE.Vector3(...spec.p) });
  }
}
