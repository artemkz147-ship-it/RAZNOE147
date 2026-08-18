import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import cityManifest from '../game3d/scenery.json';
import factoryManifest from './factory.json';
import type { DropLevelSpec, DropSurface, SurfaceKind } from './DropTypes';

type FactoryManifest = {
  platform: string[];
  pole: string[];
  unit: string[];
  all: string[];
};

type SurfaceVisual = {
  spec: DropSurface;
  root: THREE.Object3D;
  basePosition: THREE.Vector3;
  marker: THREE.Mesh | null;
  origin: THREE.Vector3;
};

export class DropLevelManager {
  private scene: THREE.Scene;
  private loader = new GLTFLoader();
  private textureLoader = new THREE.TextureLoader();
  private cityAssets = cityManifest as string[];
  private factory = factoryManifest as FactoryManifest;
  private cache = new Map<string, Promise<THREE.Object3D>>();
  private surfaces: SurfaceVisual[] = [];
  private background: THREE.Object3D[] = [];
  private markerTexture: THREE.Texture | null = null;
  private activeTarget = 0;
  private time = 0;

  constructor(scene: THREE.Scene) {
    this.scene = scene;
  }

  async init() {
    try {
      this.markerTexture = await this.textureLoader.loadAsync('assets3d/ui/landing-target.svg');
      this.markerTexture.colorSpace = THREE.SRGBColorSpace;
    } catch (error) {
      console.warn('Landing target texture failed to load', error);
    }
  }

  async load(level: DropLevelSpec) {
    this.clear();
    this.time = 0;
    const specs = [level.start, ...level.targets];
    for (let index = 0; index < specs.length; index += 1) {
      const visual = await this.createSurface(level.id, specs[index], index);
      if (visual) this.surfaces.push(visual);
    }
    await this.createBackground(level);
    this.setActiveTarget(0);
  }

  update(time: number) {
    this.time = time;
    for (let i = 1; i < this.surfaces.length; i += 1) {
      const surface = this.surfaces[i];
      const offset = this.movingOffset(surface.spec, time);
      surface.root.position.copy(surface.basePosition).add(offset);
      if (surface.marker) {
        surface.marker.position.set(
          surface.origin.x + offset.x,
          surface.origin.y + 0.055,
          surface.origin.z + offset.z
        );
      }
    }
  }

  setActiveTarget(targetIndex: number) {
    this.activeTarget = targetIndex;
    for (let i = 0; i < this.surfaces.length; i += 1) {
      const marker = this.surfaces[i].marker;
      if (marker) marker.visible = i === targetIndex + 1;
    }
  }

  getStartPosition(target = new THREE.Vector3()) {
    const surface = this.surfaces[0];
    if (!surface) return target.set(0, 0, 0);
    return target.copy(surface.origin);
  }

  getTargetPosition(targetIndex: number, target = new THREE.Vector3()) {
    const surface = this.surfaces[targetIndex + 1];
    if (!surface) return target.set(0, 0, 0);
    return target.copy(surface.origin).add(this.movingOffset(surface.spec, this.time));
  }

  getTargetSpec(targetIndex: number) {
    return this.surfaces[targetIndex + 1]?.spec ?? null;
  }

  getStandingPosition(surfaceIndex: number, target = new THREE.Vector3()) {
    const surface = this.surfaces[surfaceIndex + 1];
    if (!surface) return this.getStartPosition(target);
    return target.copy(surface.origin).add(this.movingOffset(surface.spec, this.time));
  }

  clear() {
    for (const surface of this.surfaces) {
      this.scene.remove(surface.root);
      if (surface.marker) {
        this.scene.remove(surface.marker);
        surface.marker.geometry.dispose();
        if (surface.marker.material instanceof THREE.Material) surface.marker.material.dispose();
      }
    }
    for (const root of this.background) this.scene.remove(root);
    this.surfaces = [];
    this.background = [];
  }

  private async createSurface(levelId: number, spec: DropSurface, index: number): Promise<SurfaceVisual | null> {
    const asset = this.assetFor(levelId, spec.kind, index);
    if (!asset) return null;
    const root = await this.clone(asset);
    const size = this.measure(root);
    if (size.x <= 0.001 || size.y <= 0.001 || size.z <= 0.001) return null;

    const width = Math.max(0.7, spec.size[0]);
    const depth = Math.max(0.7, spec.size[1]);
    const desiredHeight = this.visualHeight(spec);
    root.scale.set(width / size.x, desiredHeight / size.y, depth / size.z);
    const scaled = new THREE.Box3().setFromObject(root);
    const center = scaled.getCenter(new THREE.Vector3());
    root.position.x += spec.p[0] - center.x;
    root.position.z += spec.p[2] - center.z;
    root.position.y += spec.p[1] - scaled.max.y;
    this.decorate(root);
    this.scene.add(root);

    const origin = new THREE.Vector3(...spec.p);
    const marker = this.createMarker(spec, origin);
    if (marker) this.scene.add(marker);
    return { spec, root, basePosition: root.position.clone(), marker, origin };
  }

  private createMarker(spec: DropSurface, origin: THREE.Vector3) {
    if (!this.markerTexture) return null;
    const geometry = new THREE.PlaneGeometry(spec.radius * 2.05, spec.radius * 2.05);
    const material = new THREE.MeshBasicMaterial({
      map: this.markerTexture,
      transparent: true,
      depthWrite: false,
      side: THREE.DoubleSide,
      opacity: 0.92
    });
    const mesh = new THREE.Mesh(geometry, material);
    mesh.rotation.x = -Math.PI / 2;
    mesh.position.set(origin.x, origin.y + 0.055, origin.z);
    mesh.renderOrder = 4;
    mesh.visible = false;
    return mesh;
  }

  private async createBackground(level: DropLevelSpec) {
    if (!this.cityAssets.length) return;
    const end = level.targets[level.targets.length - 1]?.p ?? level.start.p;
    const centerX = (level.start.p[0] + end[0]) * 0.5;
    const centerZ = (level.start.p[2] + end[2]) * 0.5;
    const top = level.start.p[1];
    for (let index = 0; index < Math.min(14, this.cityAssets.length); index += 1) {
      const asset = this.cityAssets[(level.id * 5 + index * 3) % this.cityAssets.length];
      const root = await this.clone(asset);
      const size = this.measure(root);
      if (size.x <= 0.001 || size.y <= 0.001 || size.z <= 0.001) continue;
      const width = 7 + (index % 5) * 2.1;
      const scale = width / Math.max(size.x, size.z);
      root.scale.setScalar(scale);
      const box = new THREE.Box3().setFromObject(root);
      const center = box.getCenter(new THREE.Vector3());
      const side = index % 2 === 0 ? -1 : 1;
      const lane = Math.floor(index / 2);
      const x = centerX - 30 + lane * 9.5 + ((level.id + index) % 3) * 2.2;
      const z = centerZ + side * (16 + (index % 4) * 5.5);
      const roofY = Math.max(-4, top - 12 - (index % 6) * 4.2);
      root.position.x += x - center.x;
      root.position.z += z - center.z;
      root.position.y += roofY - box.max.y;
      root.rotation.y = side > 0 ? Math.PI : 0;
      this.decorate(root);
      this.scene.add(root);
      this.background.push(root);
    }
  }

  private assetFor(levelId: number, kind: SurfaceKind, index: number) {
    if (kind === 'roof' && this.cityAssets.length) {
      return this.cityAssets[(levelId * 2 + index * 5) % this.cityAssets.length];
    }
    const group = kind === 'pole'
      ? this.factory.pole
      : kind === 'beam' || kind === 'factory'
        ? this.factory.platform
        : this.factory.unit;
    const pool = group?.length ? group : this.factory.all;
    if (pool?.length) return pool[(levelId * 3 + index * 7) % pool.length];
    if (this.cityAssets.length) return this.cityAssets[(levelId + index) % this.cityAssets.length];
    return null;
  }

  private visualHeight(spec: DropSurface) {
    if (spec.kind === 'roof') return Math.max(7, spec.p[1] + 10);
    if (spec.kind === 'pole') return Math.max(5, Math.min(18, spec.p[1] + 8));
    if (spec.kind === 'beam') return Math.max(1.3, Math.min(4, spec.p[1] * 0.12 + 1.5));
    return Math.max(2.2, Math.min(7, spec.p[1] * 0.2 + 2.5));
  }

  private movingOffset(spec: DropSurface, time: number) {
    const offset = new THREE.Vector3();
    if (!spec.moving) return offset;
    const value = Math.sin(time * spec.moving.speed + (spec.moving.phase ?? 0)) * spec.moving.distance;
    if (spec.moving.axis === 'x') offset.x = value;
    else offset.z = value;
    return offset;
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
    return new THREE.Box3().setFromObject(root).getSize(new THREE.Vector3());
  }

  private decorate(root: THREE.Object3D) {
    root.traverse((child) => {
      if (!(child instanceof THREE.Mesh)) return;
      child.castShadow = true;
      child.receiveShadow = true;
      if (child.material instanceof THREE.MeshStandardMaterial) child.material.envMapIntensity = 0.55;
    });
  }
}
