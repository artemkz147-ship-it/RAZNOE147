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
      const [character, animationLibrary] = await Promise.all([
        this.loader.loadAsync(avatar.asset),
        locomotion.asset ? this.loader.loadAsync(locomotion.asset) : Promise.resolve(null)
      ]);

      this.model = character.scene;
      this.model.name = 'DropFlowHumanPerformer';
      this.model.rotation.y = Math.PI;
      this.model.traverse((child) => {
        if (!(child instanceof THREE.Mesh)) return;
        child.castShadow = true;
        child.receiveShadow = true;
        child.frustumCulled = false;
        const cloneMaterial = (material: THREE.Material) => {
          const copy = material.clone();
          if (copy instanceof THREE.MeshStandardMaterial) {
            copy.roughness = Math.max(0.46, copy.roughness);
            copy.metalness = Math.min(0.12, copy.metalness);
          }
          return copy;
        };
        child.material = Array.isArray(child.material)
          ? child.material.map(cloneMaterial)
          : cloneMaterial(child.material);
      });

      // The web-ready Universal Base Character is already authored in human-sized units.
      // Do NOT normalize a skinned mesh via Box3: its bind-pose bounds can produce giant characters.
      this.model.scale.setScalar(1);
      const bounds = new THREE.Box3().setFromObject(this.model);
      const center = bounds.getCenter(new THREE.Vector3());
      this.model.position.set(-center.x, -0.9 - bounds.min.y, -center.z);
      this.pivot.position.y = 0.9;
      this.pivot.add(this.model);

      this.clips = animationLibrary ? [...animationLibrary.animations] : [...character.animations];
      if (animationLibrary) {
        animationLibrary.scene.traverse((child) => {
          if (!(child instanceof THREE.Mesh)) return;
          child.geometry.dispose();
          const materials = Array.isArray(child.material) ? child.material : [child.material];
          for (const material of materials) material.dispose();
        });
      }

      this.mixer = new THREE.AnimationMixer(this.model);
      this.ready = this.clips.length > 0;
      if (!this.ready) {
        this.model.visible = false;
        console.warn('Drop Flow human character loaded, but animation library has no clips.');
        return;
      }
      this.setState('idle');
    } catch (error) {
      console.warn('Drop Flow human performer failed to load', error);
      if (this.model) this.model.visible = false;
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
    const oneShot = state === 'jump' || state === 'land' || state === 'fail';
    action.reset().setEffectiveWeight(1).setEffectiveTimeScale(1);
    action.setLoop(oneShot ? THREE.LoopOnce : THREE.LoopRepeat, oneShot ? 1 : Infinity);
    action.clampWhenFinished = oneShot;
    action.fadeIn(previous ? 0.08 : 0.01).play();
    if (previous) previous.fadeOut(0.08);
  }

  private pickClip(state: AvatarState) {
    const priorities: Record<AvatarState, string[]> = {
      idle: ['Idle_Loop', 'Idle_No_Loop'],
      jump: ['Jump_Start', 'Jump_Loop'],
      air: ['Jump_Loop', 'Fall_Loop', 'Jump_Start'],
      land: ['Jump_Land', 'ClimbUp_1m'],
      roll: ['Crouch_Fwd_Loop', 'Dodge_Roll', 'Jump_Land'],
      fail: ['Hit_Chest', 'Hit_Head', 'Death01']
    };
    for (const preferred of priorities[state]) {
      const exact = this.clips.find((clip) => clip.name.toLowerCase() === preferred.toLowerCase());
      if (exact) return exact;
    }
    const patterns: Record<AvatarState, RegExp[]> = {
      idle: [/^idle.*loop/i, /^idle/i],
      jump: [/jump.*start/i, /^jump/i],
      air: [/jump.*loop/i, /fall.*loop/i, /^fall/i],
      land: [/jump.*land/i, /land/i],
      roll: [/roll/i, /dodge/i, /crouch.*fwd/i],
      fail: [/hit.*chest/i, /hit.*head/i, /death/i]
    };
    for (const pattern of patterns[state]) {
      const clip = this.clips.find((item) => pattern.test(item.name) && !/_rm$/i.test(item.name));
      if (clip) return clip;
    }
    return this.clips.find((clip) => /idle/i.test(clip.name));
  }
}
