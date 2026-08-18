import Phaser from 'phaser';

export type RigState = 'run' | 'jump' | 'fall' | 'slide' | 'wall' | 'wallJump' | 'vault' | 'land';

type Limb = Phaser.GameObjects.Image;

export class PlayerRig extends Phaser.GameObjects.Container {
  private art: Phaser.GameObjects.Container;
  private bodyBack: Limb;
  private bodyFront: Limb;
  private head: Limb;
  private armBack: Phaser.GameObjects.Container;
  private armFront: Phaser.GameObjects.Container;
  private legBack: Phaser.GameObjects.Container;
  private legFront: Phaser.GameObjects.Container;
  private state: RigState = 'run';
  private stateTime = 0;

  constructor(scene: Phaser.Scene, x: number, y: number) {
    super(scene, x, y);
    scene.add.existing(this);

    this.art = scene.add.container(0, 0);
    this.legBack = this.makeLeg(-8, 13, 0.72);
    this.armBack = this.makeArm(-10, -10, 0.7);
    this.bodyBack = scene.add.image(0, -5, 'player-body-back').setOrigin(0.5).setScale(1.0).setAlpha(0.9);
    this.bodyFront = scene.add.image(0, -5, 'player-body-front').setOrigin(0.5).setScale(1.0);
    this.head = scene.add.image(1, -38, 'player-head').setOrigin(0.5, 0.72).setScale(0.78);
    this.legFront = this.makeLeg(8, 13, 1);
    this.armFront = this.makeArm(10, -10, 1);

    this.art.add([
      this.legBack,
      this.armBack,
      this.bodyBack,
      this.bodyFront,
      this.head,
      this.legFront,
      this.armFront
    ]);
    this.add(this.art);
    this.setDepth(30);
    this.resetPose();
  }

  setState(next: RigState) {
    if (this.state === next) return;
    this.state = next;
    this.stateTime = 0;
  }

  getState() {
    return this.state;
  }

  update(delta: number, speedRatio = 1) {
    this.stateTime += delta / 1000;
    const t = this.stateTime;
    this.resetPose();

    if (this.state === 'run') {
      const cycle = t * (9.5 + speedRatio * 3.5);
      const swing = Math.sin(cycle) * 0.92;
      const bounce = Math.abs(Math.sin(cycle)) * 2.2;
      this.legFront.rotation = swing;
      this.legBack.rotation = -swing;
      this.armFront.rotation = -swing * 0.72;
      this.armBack.rotation = swing * 0.72;
      this.bodyFront.y += bounce * 0.25;
      this.bodyBack.y += bounce * 0.25;
      this.head.y += bounce * 0.35;
      this.art.rotation = Math.sin(cycle * 0.5) * 0.022;
      this.art.x = Math.sin(cycle) * 0.7;
    } else if (this.state === 'jump') {
      const p = Phaser.Math.Clamp(t / 0.28, 0, 1);
      this.art.rotation = Phaser.Math.Linear(-0.16, 0.08, p);
      this.armFront.rotation = Phaser.Math.Linear(-1.55, -0.55, p);
      this.armBack.rotation = Phaser.Math.Linear(-1.2, -0.35, p);
      this.legFront.rotation = Phaser.Math.Linear(0.78, 0.2, p);
      this.legBack.rotation = Phaser.Math.Linear(-0.78, -0.25, p);
      this.head.rotation = -0.05;
    } else if (this.state === 'fall') {
      const drift = Math.sin(t * 5) * 0.08;
      this.art.rotation = 0.1 + drift;
      this.armFront.rotation = -0.58 + drift;
      this.armBack.rotation = -1.05 - drift;
      this.legFront.rotation = 0.34;
      this.legBack.rotation = -0.32;
    } else if (this.state === 'slide') {
      this.art.rotation = -0.18;
      this.art.y = 18;
      this.art.x = 8;
      this.art.scaleY = 0.92;
      this.head.rotation = 0.2;
      this.armFront.rotation = 1.18;
      this.armBack.rotation = 0.82;
      this.legFront.rotation = 1.32;
      this.legBack.rotation = 1.72;
    } else if (this.state === 'wall') {
      const stride = Math.sin(t * 12) * 0.58;
      this.art.rotation = -0.27;
      this.art.x = 5;
      this.armFront.rotation = -1.12 + stride * 0.18;
      this.armBack.rotation = -0.45 - stride * 0.2;
      this.legFront.rotation = 0.82 + stride;
      this.legBack.rotation = 0.2 - stride;
      this.head.rotation = 0.08;
    } else if (this.state === 'wallJump') {
      const p = Phaser.Math.Clamp(t / 0.28, 0, 1);
      this.art.rotation = Phaser.Math.Linear(-0.55, 0.12, p);
      this.armFront.rotation = -1.4;
      this.armBack.rotation = -0.9;
      this.legFront.rotation = 0.9;
      this.legBack.rotation = -0.82;
    } else if (this.state === 'vault') {
      const p = Phaser.Math.Clamp(t / 0.34, 0, 1);
      const arc = Math.sin(p * Math.PI);
      this.art.rotation = -0.2 + arc * 0.34;
      this.art.y = -arc * 6;
      this.armFront.rotation = -1.22 + p * 0.7;
      this.armBack.rotation = -0.82 + p * 0.55;
      this.legFront.rotation = 1.05 - p * 0.25;
      this.legBack.rotation = -0.72 + p * 0.35;
    } else if (this.state === 'land') {
      const p = Phaser.Math.Clamp(t / 0.18, 0, 1);
      const crouch = Math.sin(p * Math.PI) * 9;
      this.art.y = crouch;
      this.art.rotation = -0.1 * Math.sin(p * Math.PI);
      this.armFront.rotation = 0.55 * Math.sin(p * Math.PI);
      this.armBack.rotation = -0.38 * Math.sin(p * Math.PI);
      this.legFront.rotation = 0.48 * Math.sin(p * Math.PI);
      this.legBack.rotation = -0.48 * Math.sin(p * Math.PI);
    }
  }

  private resetPose() {
    this.art.setPosition(0, 0);
    this.art.setScale(1, 1);
    this.art.rotation = 0;
    this.bodyBack.setPosition(0, -5).setRotation(0);
    this.bodyFront.setPosition(0, -5).setRotation(0);
    this.head.setPosition(1, -38).setRotation(0);
    this.armBack.setPosition(-10, -10).setRotation(0);
    this.armFront.setPosition(10, -10).setRotation(0);
    this.legBack.setPosition(-8, 13).setRotation(0);
    this.legFront.setPosition(8, 13).setRotation(0);
  }

  private makeArm(x: number, y: number, alpha: number) {
    const root = this.scene.add.container(x, y);
    const upper = this.scene.add.image(0, 0, 'player-arm').setOrigin(0.5, 0.08).setScale(0.92).setAlpha(alpha);
    const hand = this.scene.add.image(0, 28, 'player-hand').setOrigin(0.5, 0.24).setScale(0.82).setAlpha(alpha);
    root.add([upper, hand]);
    return root;
  }

  private makeLeg(x: number, y: number, alpha: number) {
    const root = this.scene.add.container(x, y);
    const leg = this.scene.add.image(0, 0, 'player-leg').setOrigin(0.5, 0.08).setScale(0.92).setAlpha(alpha);
    root.add(leg);
    return root;
  }
}
