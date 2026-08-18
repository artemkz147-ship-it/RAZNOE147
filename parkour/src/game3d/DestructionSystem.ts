import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import RAPIER from '@dimforge/rapier3d-compat';
import type { BreakableSpec } from './types';

type Breakable = {
  spec: BreakableSpec;
  root: THREE.Object3D;
  collider: RAPIER.Collider;
  body: RAPIER.RigidBody;
  broken: boolean;
};

type Fragment = {
  object: THREE.Object3D;
  body: RAPIER.RigidBody;
  born: number;
};

export class DestructionSystem {
  private scene: THREE.Scene;
  private world: RAPIER.World;
  private loader = new GLTFLoader();
  private breakables = new Map<number, Breakable>();
  private fragments: Fragment[] = [];
  private fragmentTemplates = new Map<string, THREE.Object3D>();
  private ownedBodies: RAPIER.RigidBody[] = [];
  private onBreak: (reward: number) => void;
  private clock = 0;

  constructor(scene: THREE.Scene, world: RAPIER.World, onBreak: (reward: number) => void) {
    this.scene = scene;
    this.world = world;
    this.onBreak = onBreak;
  }

  async preload() {
    const assets = [
      ['wood', 'assets3d/props/wood_fragment.glb'],
      ['glass', 'assets3d/props/glass_fragment.glb'],
      ['metal', 'assets3d/props/metal_fragment.glb']
    ] as const;
    await Promise.all(assets.map(async ([key, path]) => {
      const gltf = await this.loader.loadAsync(path);
      this.fragmentTemplates.set(key, gltf.scene);
    }));
  }

  async add(spec: BreakableSpec) {
    const gltf = await this.loader.loadAsync(spec.asset.replace('assets/props/', 'assets3d/props/'));
    const root = gltf.scene;
    root.position.set(...spec.p);
    if (spec.r) root.rotation.set(...spec.r);
    root.traverse((object) => {
      if (object instanceof THREE.Mesh) {
        object.castShadow = true;
        object.receiveShadow = true;
      }
    });
    this.scene.add(root);

    const size = this.colliderSizeFor(spec.asset);
    const bodyDesc = RAPIER.RigidBodyDesc.fixed().setTranslation(...spec.p);
    if (spec.r) {
      const q = new THREE.Quaternion().setFromEuler(new THREE.Euler(...spec.r));
      bodyDesc.setRotation({ x: q.x, y: q.y, z: q.z, w: q.w });
    }
    const body = this.world.createRigidBody(bodyDesc);
    const collider = this.world.createCollider(
      RAPIER.ColliderDesc.cuboid(size.x / 2, size.y / 2, size.z / 2).setFriction(0.72),
      body
    );
    const entry: Breakable = { spec, root, collider, body, broken: false };
    this.breakables.set(collider.handle, entry);
    this.ownedBodies.push(body);
  }

  hit(collider: RAPIER.Collider, impactSpeed: number, normal: THREE.Vector3) {
    const entry = this.breakables.get(collider.handle);
    if (!entry || entry.broken || impactSpeed < entry.spec.threshold) return false;
    this.break(entry, normal, impactSpeed);
    return true;
  }

  update(time: number) {
    this.clock = time;
    for (const fragment of this.fragments) {
      const p = fragment.body.translation();
      const q = fragment.body.rotation();
      fragment.object.position.set(p.x, p.y, p.z);
      fragment.object.quaternion.set(q.x, q.y, q.z, q.w);
    }

    const expired = this.fragments.filter((fragment) => time - fragment.born > 6.5);
    if (expired.length) {
      const expiredSet = new Set(expired);
      this.fragments = this.fragments.filter((fragment) => !expiredSet.has(fragment));
      for (const fragment of expired) {
        this.scene.remove(fragment.object);
        this.world.removeRigidBody(fragment.body);
      }
    }
  }

  clear() {
    for (const entry of this.breakables.values()) this.scene.remove(entry.root);
    for (const fragment of this.fragments) {
      this.scene.remove(fragment.object);
      this.world.removeRigidBody(fragment.body);
    }
    for (const body of this.ownedBodies) this.world.removeRigidBody(body);
    this.breakables.clear();
    this.fragments = [];
    this.ownedBodies = [];
    this.clock = 0;
  }

  private break(entry: Breakable, normal: THREE.Vector3, impactSpeed: number) {
    entry.broken = true;
    this.scene.remove(entry.root);
    this.breakables.delete(entry.collider.handle);
    this.world.removeRigidBody(entry.body);
    this.ownedBodies = this.ownedBodies.filter((body) => body.handle !== entry.body.handle);

    const kind = entry.spec.asset.includes('glass')
      ? 'glass'
      : entry.spec.asset.includes('barrier') || entry.spec.asset.includes('crate')
        ? 'wood'
        : 'metal';
    const template = this.fragmentTemplates.get(kind);
    if (!template) return;

    const fragmentCount = entry.spec.asset.includes('fragile_roof') ? 11 : 7;
    for (let i = 0; i < fragmentCount; i += 1) {
      const object = template.clone(true);
      object.position.set(entry.spec.p[0], entry.spec.p[1] + (i % 3) * 0.12, entry.spec.p[2]);
      object.scale.setScalar(0.72 + (i % 3) * 0.14);
      object.traverse((child) => {
        if (child instanceof THREE.Mesh) {
          child.castShadow = true;
          child.receiveShadow = true;
        }
      });
      this.scene.add(object);

      const body = this.world.createRigidBody(
        RAPIER.RigidBodyDesc.dynamic()
          .setTranslation(object.position.x, object.position.y, object.position.z)
          .setLinearDamping(0.24)
          .setAngularDamping(0.18)
      );
      this.world.createCollider(RAPIER.ColliderDesc.cuboid(0.28, 0.09, 0.18).setDensity(0.5), body);
      body.setLinvel({
        x: -normal.x * impactSpeed * 0.45 + (i - fragmentCount / 2) * 0.28,
        y: 1.6 + (i % 4) * 0.65,
        z: -normal.z * impactSpeed * 0.45 + ((i * 3) % 5 - 2) * 0.35
      }, true);
      body.setAngvel({ x: i * 0.8, y: 1.7 + i * 0.35, z: -0.7 * i }, true);
      this.fragments.push({ object, body, born: this.clock });
    }
    this.onBreak(entry.spec.reward);
  }

  private colliderSizeFor(asset: string) {
    if (asset.includes('fragile_roof')) return new THREE.Vector3(3.2, 0.22, 3.2);
    if (asset.includes('glass')) return new THREE.Vector3(2.5, 1.8, 0.18);
    if (asset.includes('barrier')) return new THREE.Vector3(2.8, 1.5, 0.5);
    if (asset.includes('crate')) return new THREE.Vector3(1.4, 1.4, 1.4);
    return new THREE.Vector3(1.6, 1.6, 1.6);
  }
}
