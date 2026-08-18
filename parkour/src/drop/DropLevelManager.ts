import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { RGBELoader } from 'three/addons/loaders/RGBELoader.js';
import cityManifest from '../game3d/scenery.json';
import factoryManifest from './factory.json';
import dressingManifest from './dressing.json';
import type { DropLevelSpec, DropSurface, SurfaceKind } from './DropTypes';

type FactoryManifest = {
  platform: string[];
  pole: string[];
  unit: string[];
  all: string[];
};

type DressingManifest = {
  roads: string[];
  street: string[];
  rooftop: string[];
};

type SurfaceVisual = {
  spec: DropSurface;
  root: THREE.Object3D;
  basePosition: THREE.Vector3;
  marker: THREE.Mesh<THREE.PlaneGeometry, THREE.MeshBasicMaterial> | null;
  beacon: THREE.Sprite | null;
  origin: THREE.Vector3;
};

const STREET_Y = 0;
const ROUTE_PALETTE = [0x9b775f, 0x6d7f8e, 0x8e806f, 0x7e6862, 0x687b80, 0x897465];
const CITY_PALETTE = [0x7b858d, 0x927866, 0x7c8990, 0x9a8874, 0x727483, 0x887d73];

export class DropLevelManager {
  private scene: THREE.Scene;
  private loader = new GLTFLoader();
  private textureLoader = new THREE.TextureLoader();
  private cityAssets = cityManifest as string[];
  private factory = factoryManifest as FactoryManifest;
  private dressing = dressingManifest as DressingManifest;
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
          surface.origin.y + 0.035,
          surface.origin.z + offset.z
        );
      }
      if (surface.beacon) {
        surface.beacon.position.set(
          surface.origin.x + offset.x,
          surface.origin.y + 2.0,
          surface.origin.z + offset.z
        );
      }
    }

    const active = this.surfaces[this.activeTarget + 1];
    if (active) {
      const pulse = 1 + Math.sin(time * 4.8) * 0.08;
      active.marker?.scale.setScalar(pulse);
      active.beacon?.scale.set(1.1 * pulse, 1.1 * pulse, 1);
      if (active.marker) active.marker.material.opacity = 0.88 + Math.sin(time * 4.8) * 0.08;
      if (active.beacon?.material instanceof THREE.SpriteMaterial) {
        active.beacon.material.opacity = 0.66 + Math.sin(time * 4.8 + 0.8) * 0.17;
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

  getSurfaceBasePosition(surfaceIndex: number, target = new THREE.Vector3()) {
    const surface = this.surfaces[surfaceIndex + 1];
    if (!surface) return this.getStartPosition(target);
    return target.copy(surface.origin);
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
    const roofCap = this.roofCap(spec);
    const desiredHeight = this.visualHeight(spec, position.y) + roofCap;
    root.scale.set(width / size.x, desiredHeight / size.y, depth / size.z);
    const scaled = new THREE.Box3().setFromObject(root);
    const center = scaled.getCenter(new THREE.Vector3());
    root.position.x += position.x - center.x;
    root.position.z += position.z - center.z;
    // Route Y is the walkable deck, not the tallest parapet/antenna vertex.
    root.position.y += position.y + roofCap - scaled.max.y;
    this.prepareMaterials(root, true, levelId * 13 + index * 3);
    this.scene.add(root);

    const origin = position;
    const { marker, beacon } = this.createMarker(spec, origin);
    if (marker) this.scene.add(marker);
    if (beacon) this.scene.add(beacon);
    return { spec, root, basePosition: root.position.clone(), marker, beacon, origin };
  }

  private authoredPosition(levelId: number, index: number, spec: DropSurface) {
    const position = new THREE.Vector3(...spec.p);
    // Tutorial buildings must be visibly separate, while still reachable with a
    // strong parkour jump and four metres of vertical drop.
    if (index === 1 && levelId === 1) position.set(8.5, spec.p[1], -4.0);
    if (index === 1 && levelId === 2) position.set(9.0, spec.p[1], -4.5);
    return position;
  }

  private createMarker(spec: DropSurface, origin: THREE.Vector3) {
    if (!this.markerTexture) return { marker: null, beacon: null };
    const geometry = new THREE.PlaneGeometry(spec.radius * 2.05, spec.radius * 2.05);
    const material = new THREE.MeshBasicMaterial({
      map: this.markerTexture,
      transparent: true,
      depthWrite: false,
      depthTest: false,
      side: THREE.DoubleSide,
      opacity: 0.92,
      toneMapped: false
    });
    const marker = new THREE.Mesh(geometry, material);
    marker.rotation.x = -Math.PI / 2;
    marker.position.set(origin.x, origin.y + 0.035, origin.z);
    marker.renderOrder = 40;
    marker.visible = false;

    const beaconMaterial = new THREE.SpriteMaterial({
      map: this.markerTexture,
      transparent: true,
      depthWrite: false,
      depthTest: false,
      opacity: 0.76,
      toneMapped: false
    });
    const beacon = new THREE.Sprite(beaconMaterial);
    beacon.position.set(origin.x, origin.y + 2.0, origin.z);
    beacon.scale.set(1.1, 1.1, 1);
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
    const count = 18;
    const roots = await Promise.all(
      Array.from({ length: count }, async (_, index) => {
        const asset = this.cityAssets[(level.id * 5 + index * 3) % this.cityAssets.length];
        const root = await this.clone(asset);
        const size = this.measure(root);
        if (size.x <= 0.001 || size.y <= 0.001 || size.z <= 0.001) return null;

        const width = 7.5 + ((index * 3 + level.id) % 4) * 2.0;
        const roofY = Math.max(7, Math.min(top + 15, 11 + ((index * 7 + level.id * 5) % Math.max(13, Math.round(top + 8)))));
        root.scale.set(width / size.x, Math.max(5, roofY - STREET_Y) / size.y, width / size.z);
        const box = new THREE.Box3().setFromObject(root);
        const center = box.getCenter(new THREE.Vector3());
        const angle = (index / count) * Math.PI * 2 + level.id * 0.17;
        const radius = 42 + (index % 4) * 7.0;
        const x = centerX + Math.cos(angle) * radius;
        const z = centerZ + Math.sin(angle) * radius;
        root.position.x += x - center.x;
        root.position.z += z - center.z;
        root.position.y += STREET_Y - box.min.y;
        root.rotation.y = -angle + Math.PI * 0.5;
        this.prepareMaterials(root, false, level.id * 19 + index * 5);
        this.scene.add(root);
        return root;
      })
    );

    const street = await this.createStreetDressing(centerX, centerZ, level.id);
    this.background = [
      ...roots.filter((root): root is THREE.Object3D => root !== null),
      ...street
    ];
  }

  private async createStreetDressing(centerX: number, centerZ: number, levelId: number) {
    const roots: THREE.Object3D[] = [];
    if (!this.dressing.roads.length) return roots;

    const tileSize = 11;
    const tasks: Promise<void>[] = [];
    for (let gx = -4; gx <= 4; gx += 1) {
      for (let gz = -4; gz <= 4; gz += 1) {
        tasks.push((async () => {
          const selector = Math.abs(gx * 3 + gz * 5 + levelId) % this.dressing.roads.length;
          const asset = this.dressing.roads[selector];
          const road = await this.clone(asset);
          const size = this.measure(road);
          if (size.x <= 0.001 || size.z <= 0.001) return;
          const scale = tileSize / Math.max(size.x, size.z);
          road.scale.setScalar(scale);
          const box = new THREE.Box3().setFromObject(road);
          const center = box.getCenter(new THREE.Vector3());
          const x = centerX + gx * tileSize;
          const z = centerZ + gz * tileSize;
          road.position.x += x - center.x;
          road.position.z += z - center.z;
          road.position.y += STREET_Y + 0.015 - box.min.y;
          if ((gx + gz) % 2 !== 0) road.rotation.y = Math.PI * 0.5;
          road.traverse((child) => {
            if (!(child instanceof THREE.Mesh)) return;
            child.receiveShadow = true;
            child.castShadow = false;
          });
          this.scene.add(road);
          roots.push(road);
        })());
      }
    }

    if (this.dressing.street.length) {
      for (let i = 0; i < 12; i += 1) {
        tasks.push((async () => {
          const asset = this.dressing.street[i % this.dressing.street.length];
          const prop = await this.clone(asset);
          const size = this.measure(prop);
          if (size.y <= 0.001) return;
          const targetHeight = asset.includes('streetlight') ? 4.2 : asset.includes('dumpster') ? 1.25 : 0.85;
          const scale = targetHeight / size.y;
          prop.scale.setScalar(scale);
          const box = new THREE.Box3().setFromObject(prop);
          const center = box.getCenter(new THREE.Vector3());
          const side = i % 2 === 0 ? -1 : 1;
          const x = centerX - 38 + Math.floor(i / 2) * 15;
          const z = centerZ + side * (7.2 + (i % 3) * 11);
          prop.position.x += x - center.x;
          prop.position.z += z - center.z;
          prop.position.y += STREET_Y + 0.04 - box.min.y;
          prop.rotation.y = (i % 4) * Math.PI * 0.5;
          prop.traverse((child) => {
            if (!(child instanceof THREE.Mesh)) return;
            child.castShadow = true;
            child.receiveShadow = true;
          });
          this.scene.add(prop);
          roots.push(prop);
        })());
      }
    }

    await Promise.all(tasks);
    return roots;
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

  private roofCap(spec: DropSurface) {
    if (spec.moving || spec.kind !== 'roof') return 0;
    return 0.78;
  }

  private visualHeight(spec: DropSurface, topY: number) {
    if (spec.moving) return spec.kind === 'beam' ? 0.65 : 0.9;
    if (spec.kind === 'beam') return 0.7;
    return Math.max(spec.kind === 'pole' ? 1.2 : 1.8, topY - STREET_Y);
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

  private prepareMaterials(root: THREE.Object3D, route: boolean, paletteIndex: number) {
    const palette = route ? ROUTE_PALETTE : CITY_PALETTE;
    const tint = new THREE.Color(palette[Math.abs(paletteIndex) % palette.length]);
    root.traverse((child) => {
      if (!(child instanceof THREE.Mesh)) return;
      child.castShadow = route;
      child.receiveShadow = true;
      const cloneMaterial = (material: THREE.Material) => {
        const copy = material.clone();
        if (copy instanceof THREE.MeshStandardMaterial) {
          // Keep authored maps, but give otherwise white low-poly buildings a restrained
          // city palette instead of the washed-out all-white DF5/DF6 prototype look.
          copy.color.lerp(tint, route ? 0.58 : 0.38);
          copy.roughness = Math.max(copy.roughness, route ? 0.58 : 0.66);
          copy.metalness = Math.min(copy.metalness, route ? 0.07 : 0.035);
          copy.envMapIntensity = route ? 0.72 : 0.52;
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
