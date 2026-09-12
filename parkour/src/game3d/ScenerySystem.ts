import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import sceneryManifest from './scenery.json';
import type { ColliderSpec, LevelSpec } from './types';

export class ScenerySystem {
  private scene: THREE.Scene;
  private loader = new GLTFLoader();
  private roots: THREE.Object3D[] = [];
  private cache = new Map<string, Promise<THREE.Object3D>>();
  private assets = sceneryManifest as string[];

  constructor(scene: THREE.Scene) {
    this.scene = scene;
  }

  async load(level: LevelSpec) {
    this.clear();
    if (!this.assets.length) return;

    const roofs = level.colliders.filter((item) => item.s[0] >= 6 && item.s[2] >= 6).slice(0, 7);
    const routeBuildings = roofs.map((roof, index) => this.placeUnderRoof(level.id, roof, index));
    const backgroundBuildings = Array.from({ length: Math.min(12, this.assets.length) }, (_, index) =>
      this.placeBackground(level, index)
    );
    await Promise.all([...routeBuildings, ...backgroundBuildings]);
  }

  clear() {
    for (const root of this.roots) this.scene.remove(root);
    this.roots = [];
  }

  private async placeUnderRoof(levelId: number, roof: ColliderSpec, index: number) {
    const asset = this.assets[(levelId * 3 + index * 5) % this.assets.length];
    const root = await this.clone(asset);
    const size = this.measure(root);
    if (size.x <= 0 || size.y <= 0 || size.z <= 0) return;

    const widthScale = Math.max(roof.s[0] / size.x, roof.s[2] / size.z) * 1.04;
    const targetHeight = 10 + levelId * 0.85 + (index % 3) * 3.2;
    const heightScale = targetHeight / size.y;
    const scale = Math.min(widthScale * 1.75, Math.max(widthScale, heightScale));
    root.scale.setScalar(scale);

    const box = new THREE.Box3().setFromObject(root);
    const center = box.getCenter(new THREE.Vector3());
    const roofBottom = roof.p[1] - roof.s[1] / 2 - 0.04;
    root.position.x += roof.p[0] - center.x;
    root.position.z += roof.p[2] - center.z;
    root.position.y += roofBottom - box.max.y;
    this.decorate(root);
    this.scene.add(root);
    this.roots.push(root);
  }

  private async placeBackground(level: LevelSpec, index: number) {
    const asset = this.assets[(level.id * 7 + index * 2) % this.assets.length];
    const root = await this.clone(asset);
    const size = this.measure(root);
    if (size.x <= 0 || size.y <= 0 || size.z <= 0) return;

    const side = index % 2 === 0 ? -1 : 1;
    const targetWidth = 7 + (index % 4) * 2.4;
    const scale = targetWidth / Math.max(size.x, size.z);
    root.scale.setScalar(scale);

    const box = new THREE.Box3().setFromObject(root);
    const center = box.getCenter(new THREE.Vector3());
    const pathLength = Math.max(level.finish[0], 45);
    const x = -13 + ((index * 13 + level.id * 4) % Math.floor(pathLength + 28));
    const z = side * (17 + (index % 5) * 4.3);
    const roofLine = -1 + ((index + level.id) % 5) * 2.1;
    root.position.x += x - center.x;
    root.position.z += z - center.z;
    root.position.y += roofLine - box.max.y;
    root.rotation.y = side < 0 ? Math.PI : 0;
    this.decorate(root);
    this.scene.add(root);
    this.roots.push(root);
  }

  private clone(path: string) {
    let promise = this.cache.get(path);
    if (!promise) {
      promise = this.loader.loadAsync(path).then((gltf) => gltf.scene);
      this.cache.set(path, promise);
    }
    return promise.then((template) => template.clone(true));
  }

  private measure(root: THREE.Object3D) {
    const box = new THREE.Box3().setFromObject(root);
    return box.getSize(new THREE.Vector3());
  }

  private decorate(root: THREE.Object3D) {
    root.traverse((child) => {
      if (!(child instanceof THREE.Mesh)) return;
      child.castShadow = true;
      child.receiveShadow = true;
      if (child.material instanceof THREE.MeshStandardMaterial) {
        child.material.envMapIntensity = 0.5;
      }
    });
  }
}
