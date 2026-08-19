import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import avatarManifest from './avatar.json';
import locomotionManifest from './locomotion.json';

type AnimationManifest = { asset: string | null; clips: string[] };

type MotionKey = 'idle' | 'walk' | 'run' | 'air' | 'vault' | 'mantle' | 'wall' | 'slide';

export class AvatarSystem {
  private scene: THREE.Scene;
  private loader = new GLTFLoader();
  private root: THREE.Object3D | null = null;
  private mixer: THREE.AnimationMixer | null = null;
  private clips: THREE.AnimationClip[] = [];
  private actions = new Map<string, THREE.AnimationAction>();
  private currentAction: THREE.AnimationAction | null = null;
  private currentMotion: MotionKey | null = null;
  private footOffset = 0;
  private ready = false;

  constructor(scene: THREE.Scene) {
    this.scene = scene;
  }

  async init() {
    const avatar = avatarManifest as AnimationManifest;
    const locomotion = locomotionManifest as AnimationManifest;
    if (!avatar.asset) return;

    try {
      const gltf = await this.loader.loadAsync(avatar.asset);
      this.root = gltf.scene;
      this.root.name = 'ParkourPerformer';
      this.root.traverse((child) => {
        if (child instanceof THREE.Mesh) {
          child.castShadow = true;
          child.receiveShadow = true;
          child.frustumCulled = false;
        }
      });

      const initialBox = new THREE.Box3().setFromObject(this.root);
      const initialSize = initialBox.getSize(new THREE.Vector3());
      if (initialSize.y > 0.01) {
        const scale = 1.76 / initialSize.y;
        this.root.scale.setScalar(scale);
      }
      const scaledBox = new THREE.Box3().setFromObject(this.root);
      this.footOffset = -scaledBox.min.y;
      this.scene.add(this.root);

      this.clips = [...gltf.animations];
      if (locomotion.asset) {
        try {
          const locomotionGltf = await this.loader.loadAsync(locomotion.asset);
          const existing = new Set(this.clips.map((clip) => clip.name));
          for (const clip of locomotionGltf.animations) {
            if (!existing.has(clip.name)) this.clips.push(clip);
          }
        } catch (error) {
          console.warn('Locomotion animation library failed to load', error);
        }
      }

      this.mixer = new THREE.AnimationMixer(this.root);
      this.ready = true;
      this.setMotion('idle', 0);
    } catch (error) {
      console.warn('Animated avatar failed to load', error);
      this.root = null;
      this.mixer = null;
    }
  }

  update(dt: number, foot: THREE.Vector3, yaw: number, motionState: string, speed: number, visible: boolean) {
    if (!this.ready || !this.root || !this.mixer) return;
    this.root.visible = visible;
    this.root.position.set(foot.x, foot.y + this.footOffset, foot.z);
    this.root.rotation.set(0, yaw + Math.PI, 0);

    const motion = this.toMotion(motionState, speed);
    if (motion !== this.currentMotion) this.setMotion(motion, speed);
    if (this.currentAction) {
      const scale = motion === 'run' ? Math.max(0.86, Math.min(1.35, speed / 6.4)) : 1;
      this.currentAction.timeScale = scale;
    }
    this.mixer.update(dt);
  }

  setVisible(value: boolean) {
    if (this.root) this.root.visible = value;
  }

  private toMotion(state: string, speed: number): MotionKey {
    if (state === 'VAULT') return 'vault';
    if (state === 'EDGE CLIMB') return 'mantle';
    if (state === 'WALL RUN') return 'wall';
    if (state === 'SLIDE') return 'slide';
    if (state === 'AIR') return 'air';
    if (speed > 5.8) return 'run';
    if (speed > 0.8) return 'walk';
    return 'idle';
  }

  private setMotion(motion: MotionKey, speed: number) {
    this.currentMotion = motion;
    if (!this.mixer) return;
    const clip = this.pickClip(motion, speed);
    if (!clip) return;

    let action = this.actions.get(clip.name);
    if (!action) {
      action = this.mixer.clipAction(clip);
      this.actions.set(clip.name, action);
    }
    if (action === this.currentAction) return;

    const previous = this.currentAction;
    this.currentAction = action;
    action.reset().setEffectiveWeight(1).setEffectiveTimeScale(1);
    const oneShot = motion === 'vault' || motion === 'mantle' || motion === 'slide';
    action.setLoop(oneShot ? THREE.LoopOnce : THREE.LoopRepeat, oneShot ? 1 : Infinity);
    action.clampWhenFinished = oneShot;
    action.fadeIn(previous ? 0.12 : 0.02).play();
    if (previous) previous.fadeOut(0.12);
  }

  private pickClip(motion: MotionKey, speed: number) {
    const groups: Record<MotionKey, RegExp[]> = {
      idle: [/^idle_no_loop$/i, /idle.*loop/i, /^idle/i, /tpose/i],
      walk: [/walk.*forward/i, /walk/i, /jog/i, /run/i],
      run: [speed > 7 ? /sprint/i : /run.*forward/i, /sprint/i, /jog/i, /run/i],
      air: [/fall.*loop/i, /^fall/i, /jump.*loop/i, /^jump/i, /air/i],
      vault: [/vault/i, /parkour.*over/i, /jump.*obstacle/i, /climbup_1m/i, /climb/i],
      mantle: [/climbup_1m/i, /climb.*up/i, /ledge/i, /mantle/i, /climb/i],
      wall: [/wall.*run/i, /wallrun/i, /wall/i, /climb.*loop/i, /run/i],
      slide: [/slide/i, /roll/i, /dodge/i, /crouch/i, /climbup_1m/i]
    };

    for (const pattern of groups[motion]) {
      const nonRootMotion = this.clips.find((clip) => pattern.test(clip.name) && !/_rm$/i.test(clip.name));
      if (nonRootMotion) return nonRootMotion;
      const any = this.clips.find((clip) => pattern.test(clip.name));
      if (any) return any;
    }
    return this.clips.find((clip) => /idle/i.test(clip.name)) ?? this.clips[0];
  }
}
