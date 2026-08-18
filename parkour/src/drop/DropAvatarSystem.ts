import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import avatarManifest from '../game3d/avatar.json';
import locomotionManifest from '../game3d/locomotion.json';

type Manifest = { asset: string | null; clips: string[] };
type AvatarState = 'idle' | 'jump' | 'air' | 'land' | 'roll' | 'fail';

export class DropAvatarSystem {
  private scene: THREE.Scene;
  private loader = new GLTFLoader();
  private anchor = new THREE.Group();
  private pivot = new THREE.Group();
  private model: THREE.Object3D | null = null;
  private mixer: THREE.AnimationMixer | null = null;
  private clips: THREE.AnimationClip[] = [];
  private actions = new Map<string, THREE.AnimationAction>();
  private currentAction: THREE.AnimationAction | null = null;
  private currentState: AvatarState | null = null;
  private ready = false;

  constructor(scene: THREE.Scene) {
    this.scene = scene;
    this.anchor.name = 'DropPerformerAnchor';
    this.pivot.name = 'DropPerformerTrickPivot';
    this.anchor.add(this.pivot);
    this.scene.add(this.anchor);
  }

  async init() {
    const avatar = avatarManifest as Manifest;
    const locomotion = locomotionManifest as Manifest;
    if (!avatar.asset) return;

    try {
      const gltf = await this.loader.loadAsync(avatar.asset);
      this.model = gltf.scene;
      this.model.name = 'DropParkourPerformer';
      this.model.traverse((child) => {
        if (child instanceof THREE.Mesh) {
          child.castShadow = true;
          child.receiveShadow = true;
          child.frustumCulled = false;
        }
      });

      const initial = new THREE.Box3().setFromObject(this.model);
      const size = initial.getSize(new THREE.Vector3());
      if (size.y > 0.01) this.model.scale.setScalar(1.76 / size.y);
      const scaled = new THREE.Box3().setFromObject(this.model);
      const center = scaled.getCenter(new THREE.Vector3());
      const minY = scaled.min.y;
      this.model.position.set(-center.x, -0.9 - minY, -center.z);
      this.pivot.position.y = 0.9;
      this.pivot.add(this.model);

      this.clips = [...gltf.animations];
      if (locomotion.asset) {
        try {
          const lib = await this.loader.loadAsync(locomotion.asset);
          const names = new Set(this.clips.map((clip) => clip.name));
          for (const clip of lib.animations) if (!names.has(clip.name)) this.clips.push(clip);
        } catch (error) {
          console.warn('Drop locomotion library failed', error);
        }
      }

      this.mixer = new THREE.AnimationMixer(this.model);
      this.ready = true;
      this.setState('idle');
    } catch (error) {
      console.warn('Drop performer failed to load', error);
    }
  }

  update(
    dt: number,
    foot: THREE.Vector3,
    yaw: number,
    state: AvatarState,
    trickRotation: THREE.Vector3,
    visible = true
  ) {
    this.anchor.visible = visible;
    this.anchor.position.copy(foot);
    this.anchor.rotation.set(0, yaw, 0);
    this.pivot.rotation.set(trickRotation.x, trickRotation.y, trickRotation.z, 'YXZ');
    if (!this.ready || !this.mixer) return;
    if (state !== this.currentState) this.setState(state);
    this.mixer.update(dt);
  }

  setVisible(value: boolean) {
    this.anchor.visible = value;
  }

  private setState(state: AvatarState) {
    this.currentState = state;
    if (!this.mixer) return;
    const clip = this.pickClip(state);
    if (!clip) return;
    let action = this.actions.get(clip.name);
    if (!action) {
      action = this.mixer.clipAction(clip);
      this.actions.set(clip.name, action);
    }
    if (action === this.currentAction) return;

    const previous = this.currentAction;
    this.currentAction = action;
    const oneShot = state === 'jump' || state === 'land' || state === 'roll' || state === 'fail';
    action.reset().setEffectiveWeight(1).setEffectiveTimeScale(1);
    action.setLoop(oneShot ? THREE.LoopOnce : THREE.LoopRepeat, oneShot ? 1 : Infinity);
    action.clampWhenFinished = oneShot;
    action.fadeIn(previous ? 0.09 : 0.01).play();
    if (previous) previous.fadeOut(0.09);
  }

  private pickClip(state: AvatarState) {
    const patterns: Record<AvatarState, RegExp[]> = {
      idle: [/^idle_loop$/i, /^idle_no_loop$/i, /idle.*loop/i, /^idle/i, /tpose/i],
      jump: [/^jump_start$/i, /jump.*start/i, /jump/i],
      air: [/^jump_loop$/i, /fall.*loop/i, /jump.*loop/i, /^fall/i, /^jump/i],
      land: [/^jump_land$/i, /jump.*land/i, /land/i, /climbup_1m/i],
      roll: [/roll/i, /dodge/i, /crouch.*fwd/i, /climbup_1m/i],
      fail: [/hit_knockback/i, /hit.*chest/i, /hit.*head/i, /death/i]
    };
    for (const pattern of patterns[state]) {
      const clean = this.clips.find((clip) => pattern.test(clip.name) && !/_rm$/i.test(clip.name));
      if (clean) return clean;
      const any = this.clips.find((clip) => pattern.test(clip.name));
      if (any) return any;
    }
    return this.clips[0];
  }
}
