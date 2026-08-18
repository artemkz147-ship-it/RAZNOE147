import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { RGBELoader } from 'three/addons/loaders/RGBELoader.js';
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
  marker: THREE.Mesh<THREE.PlaneGeometry, THREE.MeshBasicMaterial> | null;
  beacon: THREE.Sprite | null;
  origin: THREE.Vector3;
};

const ROUTE_TONES = [0x58687a, 0x6b6f78, 0x75685e, 0x4f6970, 0x6a5f73, 0x596f61];
const CITY_TONES = [0x8b98a5, 0x9d8d80, 0x7f918c, 0x8a8498, 0x748592, 0xa0927e, 0x7d8794];

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
  private skyTexture: THREE.Texture | null = null;
  private activeTarget = 0;
  private time = 0;

  constructor(scene: THREE.Scene) {
    this.scene = scene;
  }

  async init() {
    const marker = this.textureLoader.loadAsync('assets3d/ui/landing-target.svg')
      .then((texture) => {
        texture.colorSpace = THREE.SRGBColorSpace;
        this.markerTexture = texture;
      })
      .catch((error) => console.warn('Landing target texture failed to load', error));

    const sky = new RGBELoader().loadAsync('assets3d/environment/rooftop_sunset_1k.hdr')
      .then((texture) => {
        texture.mapping = THREE.EquirectangularReflectionMapping;
        this.skyTexture = texture;
        this.scene.background = texture;
        this.scene.environment = texture;
      })
      .catch((error) => console.warn('Rooftop HDR environment failed to load', error));

    await Promise.all([marker, sky]);
  }

  hasSky() {
    return this.skyTexture !== null;
  }

  async load(level: DropLevelSpec) {
    this.clear();
    this.time = 0;
    const specs = [level.start, ...level.targets];
    const visuals = await Promise.all(
      specs.map((spec, index) => this.createSurface(level.id, spec, index))
    );
    this.surfaces = visuals.filter((visual): visual is SurfaceVisual => visual !== null);
    await this.createBackground(level);
    if (this.skyTexture) {
      this.scene.background = this.skyTexture;
      this.scene.environment = this.skyTexture;
    }
    this.setActiveTarget(0);
  }

  update(time: number) {
    this.time = time;
    for (let i = 0; i < this.surfaces.length; i += 1) {
      const surface = this.surfaces[i];
      const offset = i === 0 ? new THREE.Vector3() : this.movingOffset(surface.spec, time);
      surface.root.position.copy(surface.basePosition).add(offset);
      if (surface.marker) {
        surface.marker.position.set(
          surface.origin.x + offset.x,
          surface.origin.y + 0.065,
          surface.origin.z + offset.z
        );
      }
      if (surface.beacon) {
        surface.beacon.position.set(
          surface.origin.x + offset.x,
          surface.origin.y + 2.25,
          surface.origin.z + offset.z
        );
      }
    }

    const active = this.surfaces[this.activeTarget + 1];
    if (active) {
      const pulse = 1 + Math.sin(time * 4.8) * 0.08;
      active.marker?.scale.setScalar(pulse);
      active.beacon?.scale.set(1.15 * pulse, 1.15 * pulse, 1);
      if (active.marker) active.marker.material.opacity = 0.9 + Math.sin(time * 4.8) * 0.08;
      if (active.beacon?.material instanceof THREE.SpriteMaterial) {
        active.beacon.material.opacity = 0.7 + Math.sin(time * 4.8 + 0.8) * 0.18;
      }
    }
  }

  setActiveTarget(targetIndex: number) {
    this.activeTarget = targetIndex;
    for (let i = 0; i < this.surfaces.length; i += 1) {
      const visible = i === targetIndex + 1;
      if (this.surfaces[i].marker) this.surfaces[i].marker!.visible = visible;
      if (this.surfaces[i].beacon) this.surfaces[i].beacon!.visible = visible;
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
        surface.marker.material.dispose();
      }
      if (surface.beacon) {
        this.scene.remove(surface.beacon);
        if (surface.beacon.material instanceof THREE.Material) surface.beacon.material.dispose();
      }
      this.disposeMaterials(surface.root);
    }
    for (const root of this.background) {
      this.scene.remove(root);
      this.disposeMaterials(root);
    }
    this.surfaces = [];
    this.background = [];
  }

  private async createSurface(levelId: number, spec: DropSurface, index: number): Promise<SurfaceVisual | null> {
    const asset = this.assetFor(levelId, spec.kind, index);
    if (!asset) return null;
    const root = await this.clone(asset);
    const size = this.measure(root);
    if (size.x <= 0.001 || size.y <= 0.001 || size.z <= 0.001) return null;

    const position = this.authoredPosition(levelId, index, spec);
    const width = Math.max(0.7, spec.size[0]);
    const depth = Math.max(0.7, spec.size[1]);
    const desiredHeight = this.visualHeight(spec);
    root.scale.set(width / size.x, desiredHeight / size.y, depth / size.z);
    const scaled = new THREE.Box3().setFromObject(root);
    const center = scaled.getCenter(new THREE.Vector3());
    root.position.x += position.x - center.x;
    root.position.z += position.z - center.z;
    root.position.y += position.y - scaled.max.y;
    this.decorate(root, levelId * 5 + index, true);
    this.scene.add(root);

    const origin = position;
    const { marker, beacon } = this.createMarker(spec, origin);
    if (marker) this.scene.add(marker);
    if (beacon) this.scene.add(beacon);
    return { spec, root, basePosition: root.position.clone(), marker, beacon, origin };
  }

  private authoredPosition(levelId: number, index: number, spec: DropSurface) {
    const position = new THREE.Vector3(...spec.p);
    // The first tutorial targets must be visibly separate buildings, not hidden
    // inside the start roof footprint. Later authored routes already have enough gap.
    if (index === 1 && levelId === 1) position.set(9, spec.p[1], -6);
    if (index === 1 && levelId === 2) position.set(9, spec.p[1], -5);
    return position;
  }

  private createMarker(spec: DropSurface, origin: THREE.Vector3) {
    if (!this.markerTexture) return { marker: null, beacon: null };
    const geometry = new THREE.PlaneGeometry(spec.radius * 2.15, spec.radius * 2.15);
    const material = new THREE.MeshBasicMaterial({
      map: this.markerTexture,
      transparent: true,
      depthWrite: false,
      depthTest: false,
      side: THREE.DoubleSide,
      opacity: 0.94,
      toneMapped: false
    });
    const marker = new THREE.Mesh(geometry, material);
    marker.rotation.x = -Math.PI / 2;
    marker.position.set(origin.x, origin.y + 0.065, origin.z);
    marker.renderOrder = 40;
    marker.visible = false;

    const beaconMaterial = new THREE.SpriteMaterial({
      map: this.markerTexture,
      transparent: true,
      depthWrite: false,
      depthTest: false,
      opacity: 0.82,
      toneMapped: false
    });
    const beacon = new THREE.Sprite(beaconMaterial);
    beacon.position.set(origin.x, origin.y + 2.25, origin.z);
    beacon.scale.set(1.15, 1.15, 1);
    beacon.renderOrder = 41;
    beacon.visible = false;
    return { marker, beacon };
  }

  private async createBackground(level: DropLevelSpec) {
    if (!this.cityAssets.length) return;
    const end = level.targets[level.targets.length - 1]?.p ?? level.start.p;
    const centerX = (level.start.p[0] + end[0]) * 0.5;
    const centerZ = (level.start.p[2] + end[2]) * 0.5;
    const top = level.start.p[1];
    const count = Math.min(12, this.cityAssets.length);
    const roots = await Promise.all(
      Array.from({ length: count }, async (_, index) => {
        const asset = this.cityAssets[(level.id * 5 + index * 3) % this.cityAssets.length];
        const root = await this.clone(asset);
        const size = this.measure(root);
        if (size.x <= 0.001 || size.y <= 0.001 || size.z <= 0.001) return null;
        const width = 7 + (index % 5) * 2.5;
        const scale = width / Math.max(size.x, size.z);
        root.scale.setScalar(scale);
        const box = new THREE.Box3().setFromObject(root);
        const center = box.getCenter(new THREE.Vector3());
        const side = index % 2 === 0 ? -1 : 1;
        const lane = Math.floor(index / 2);
        const x = centerX - 30 + lane * 11 + ((level.id + index) % 3) * 2.4;
        const z = centerZ + side * (18 + (index % 4) * 6.2);
        const roofY = Math.max(-8, top - 15 - (index % 6) * 5.1);
        root.position.x += x - center.x;
        root.position.z += z - center.z;
        root.position.y += roofY - box.max.y;
        root.rotation.y = side > 0 ? Math.PI : 0;
        this.decorate(root, level.id * 11 + index, false);
        this.scene.add(root);
        return root;
      })
    );
    this.background = roots.filter((root): root is THREE.Object3D => root !== null);
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
    if (spec.kind === 'roof') return Math.max(8, spec.p[1] + 11);
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

  private decorate(root: THREE.Object3D, paletteIndex: number, route: boolean) {
    const palette = route ? ROUTE_TONES : CITY_TONES;
    const tint = new THREE.Color(palette[Math.abs(paletteIndex) % palette.length]);
    root.traverse((child) => {
      if (!(child instanceof THREE.Mesh)) return;
      child.castShadow = route;
      child.receiveShadow = true;
      const cloneMaterial = (material: THREE.Material) => {
        const copy = material.clone();
        if (copy instanceof THREE.MeshStandardMaterial) {
          copy.color.multiply(tint);
          copy.roughness = route ? 0.7 : 0.78;
          copy.metalness = Math.min(copy.metalness, route ? 0.08 : 0.04);
          copy.envMapIntensity = route ? 0.72 : 0.45;
        }
        return copy;
      };
      child.material = Array.isArray(child.material)
        ? child.material.map(cloneMaterial)
        : cloneMaterial(child.material);
    });
  }

  private disposeMaterials(root: THREE.Object3D) {
    root.traverse((child) => {
      if (!(child instanceof THREE.Mesh)) return;
      const materials = Array.isArray(child.material) ? child.material : [child.material];
      for (const material of materials) material.dispose();
    });
  }
}
