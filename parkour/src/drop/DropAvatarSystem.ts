import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import avatarManifest from '../game3d/avatar.json';
import locomotionManifest from '../game3d/locomotion.json';
import tricksManifest from './tricks.json';

type Manifest = { asset: string | null; clips: string[] };
type AvatarState = 'idle' | 'walk' | 'run' | 'jump' | 'air' | 'land' | 'roll' | 'fail';
type TrickKind = 'front' | 'back' | 'side' | 'twist';

export class DropAvatarSystem {
  private scene: THREE.Scene;
  private loader = new GLTFLoader();
  private anchor = new THREE.Group();
  private pivot = new THREE.Group();
  private model: THREE.Object3D | null = null;
  private mixer: THREE.AnimationMixer | null = null;
  private clips: THREE.AnimationClip[] = [];
  private trickClips: THREE.AnimationClip[] = [];
  private actions = new Map<string, THREE.AnimationAction>();
  private currentAction: THREE.AnimationAction | null = null;
  private currentState: AvatarState | null = null;
  private activeTrickAction: THREE.AnimationAction | null = null;
  private activeTrickKind: TrickKind | null = null;
  private ready = false;

  constructor(scene: THREE.Scene) {
    this.scene = scene;
    this.anchor.name = 'DropPerformerAnchor';
    this.pivot.name = 'DropPerformerTrickPivot';
    this.anchor.add(this.pivot);
    this.scene.add(this.anchor);

    window.addEventListener('drop-avatar-trick', (rawEvent) => {
      const event = rawEvent as CustomEvent<{ kind?: TrickKind }>;
      const kind = event.detail?.kind;
      if (kind) this.playTrick(kind);
    });
  }

  async init() {
    const avatar = avatarManifest as Manifest;
    const locomotion = locomotionManifest as Manifest;
    const tricks = tricksManifest as Manifest;
    if (!avatar.asset) return;

    try {
      const [character, animationLibrary, trickLibrary] = await Promise.all([
        this.loader.loadAsync(avatar.asset),
        locomotion.asset ? this.loader.loadAsync(locomotion.asset) : Promise.resolve(null),
        tricks.asset ? this.loader.loadAsync(tricks.asset) : Promise.resolve(null)
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
            copy.roughness = Math.max(0.5, copy.roughness);
            copy.metalness = Math.min(0.08, copy.metalness);
          }
          return copy;
        };
        child.material = Array.isArray(child.material)
          ? child.material.map(cloneMaterial)
          : cloneMaterial(child.material);
      });

      this.model.scale.setScalar(1);
      const bounds = new THREE.Box3().setFromObject(this.model);
      const center = bounds.getCenter(new THREE.Vector3());
      this.model.position.set(-center.x, -0.9 - bounds.min.y, -center.z);
      this.pivot.position.y = 0.9;
      this.pivot.add(this.model);

      this.clips = animationLibrary ? [...animationLibrary.animations] : [...character.animations];
      this.trickClips = trickLibrary ? [...trickLibrary.animations] : [];
      this.disposeLibraryScene(animationLibrary?.scene ?? null);
      this.disposeLibraryScene(trickLibrary?.scene ?? null);

      this.mixer = new THREE.AnimationMixer(this.model);
      this.mixer.addEventListener('finished', (event) => {
        if (event.action !== this.activeTrickAction) return;
        this.activeTrickAction = null;
        this.activeTrickKind = null;
        this.currentAction = null;
        this.currentState = null;
      });
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

    // A real UAL2 trick clip already contains its own body rotation and limb pose.
    // Do not rotate the whole model a second time while that clip is active.
    if (this.activeTrickAction) this.pivot.rotation.set(0, 0, 0);
    else this.pivot.rotation.set(trickRotation.x, trickRotation.y, trickRotation.z, 'YXZ');

    if (!this.ready || !this.mixer) return;
    if (!this.activeTrickAction && state !== this.currentState) this.setState(state);
    if (state !== 'air' && state !== 'jump' && this.activeTrickAction) {
      this.activeTrickAction.fadeOut(0.08);
      this.activeTrickAction = null;
      this.activeTrickKind = null;
      this.currentAction = null;
      this.currentState = null;
      this.setState(state);
    }
    this.mixer.update(dt);
  }

  setVisible(value: boolean) {
    this.anchor.visible = value;
  }

  private playTrick(kind: TrickKind) {
    if (!this.mixer || !this.ready || !this.trickClips.length) return;
    const clip = this.pickTrickClip(kind);
    if (!clip) return;

    let action = this.actions.get(`trick:${clip.name}`);
    if (!action) {
      action = this.mixer.clipAction(clip);
      this.actions.set(`trick:${clip.name}`, action);
    }
    const previous = this.currentAction;
    this.activeTrickAction = action;
    this.activeTrickKind = kind;
    this.currentAction = action;
    this.currentState = 'air';
    action.reset().setEffectiveWeight(1).setEffectiveTimeScale(this.trickTimeScale(clip));
    action.setLoop(THREE.LoopOnce, 1);
    action.clampWhenFinished = true;
    action.fadeIn(previous ? 0.07 : 0.01).play();
    if (previous && previous !== action) previous.fadeOut(0.07);
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
    action.reset().setEffectiveWeight(1).setEffectiveTimeScale(state === 'run' ? 1.08 : 1);
    action.setLoop(oneShot ? THREE.LoopOnce : THREE.LoopRepeat, oneShot ? 1 : Infinity);
    action.clampWhenFinished = oneShot;
    action.fadeIn(previous ? 0.11 : 0.01).play();
    if (previous) previous.fadeOut(0.11);
  }

  private pickTrickClip(kind: TrickKind) {
    const patterns: Record<TrickKind, RegExp[]> = {
      front: [
        /front.*flip/i,
        /flip.*front/i,
        /front.*somersault/i,
        /somersault.*front/i,
        /parkour.*flip/i,
        /dive.*roll/i
      ],
      back: [
        /back.*flip/i,
        /flip.*back/i,
        /back.*somersault/i,
        /somersault.*back/i
      ],
      side: [
        /side.*flip/i,
        /flip.*side/i,
        /aerial/i,
        /cartwheel/i
      ],
      twist: [
        /twist/i,
        /jump.*spin/i,
        /spin.*jump/i,
        /360/i
      ]
    };
    for (const pattern of patterns[kind]) {
      const clip = this.trickClips.find((item) => pattern.test(item.name) && !/_rm$/i.test(item.name));
      if (clip) return clip;
    }
    return null;
  }

  private trickTimeScale(clip: THREE.AnimationClip) {
    if (clip.duration <= 0.01) return 1;
    // Keep the expressive pose but fit long authored moves into a typical rooftop
    // flight window instead of freezing the player halfway through a clip.
    return THREE.MathUtils.clamp(clip.duration / 0.9, 0.9, 1.9);
  }

  private pickClip(state: AvatarState) {
    const priorities: Record<AvatarState, string[]> = {
      idle: ['Idle_Loop'],
      walk: ['Walk_Loop', 'Walk_Formal_Loop', 'Jog_Fwd_Loop'],
      run: ['Jog_Fwd_Loop', 'Sprint_Loop', 'Walk_Loop'],
      jump: ['Jump_Start', 'Jump_Loop'],
      air: ['Jump_Loop', 'Jump_Start'],
      land: ['Jump_Land'],
      roll: ['Roll', 'Crouch_Fwd_Loop', 'Jump_Land'],
      fail: ['Hit_Chest', 'Hit_Head', 'Death01']
    };
    for (const preferred of priorities[state]) {
      const exact = this.clips.find((clip) => clip.name.toLowerCase() === preferred.toLowerCase());
      if (exact) return exact;
    }
    const patterns: Record<AvatarState, RegExp[]> = {
      idle: [/^idle.*loop/i, /^idle/i],
      walk: [/^walk.*loop/i, /jog.*fwd/i],
      run: [/sprint.*loop/i, /jog.*fwd/i, /^walk.*loop/i],
      jump: [/jump.*start/i, /^jump/i],
      air: [/jump.*loop/i, /^jump/i],
      land: [/jump.*land/i, /land/i],
      roll: [/^roll$/i, /roll/i, /crouch.*fwd/i],
      fail: [/hit.*chest/i, /hit.*head/i, /death/i]
    };
    for (const pattern of patterns[state]) {
      const clip = this.clips.find((item) => pattern.test(item.name) && !/_rm$/i.test(item.name));
      if (clip) return clip;
    }
    return this.clips.find((clip) => /idle/i.test(clip.name));
  }

  private disposeLibraryScene(root: THREE.Object3D | null) {
    root?.traverse((child) => {
      if (!(child instanceof THREE.Mesh)) return;
      child.geometry.dispose();
      const materials = Array.isArray(child.material) ? child.material : [child.material];
      for (const material of materials) material.dispose();
    });
  }
}
