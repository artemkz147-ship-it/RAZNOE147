import Phaser from 'phaser';

export type RigState = 'run' | 'jump' | 'fall' | 'slide' | 'wall' | 'land';

type Limb = Phaser.GameObjects.Image;

export class PlayerRig extends Phaser.GameObjects.Container {
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

    this.legBack = this.makeLeg(-8, 13, 0.84);
    this.armBack = this.makeArm(-10, -10, 0.82);
    this.bodyBack = scene.add.image(0, -5, 'player-body-back').setOrigin(0.5, 0.5).setScale(2.4);
    this.bodyFront = scene.add.image(0, -5, 'player-body-front').setOrigin(0.5, 0.5).setScale(2.4);
    this.head = scene.add.image(1, -32, 'player-head').setOrigin(0.5, 0.72).setScale(2.35);
    this.legFront = this.makeLeg(8, 13, 1);
    this.armFront = this.makeArm(10, -10, 1);

    this.add([this.legBack, this.armBack, this.bodyBack, this.bodyFront, this.head, this.legFront, this.armFront]);
    this.setDepth(20);
  }

  setState(next: RigState) {
    if (this.state === next) return;
    this.state = next;
    this.stateTime = 0;
  }

  update(delta: number, speedRatio = 1) {
    this.stateTime += delta / 1000;
    const t = this.stateTime;

    this.setScale(1, 1);
    this.rotation = 0;
    this.y = this.y;
    this.head.rotation = 0;

    if (this.state === 'run') {
      const cycle = t * (10 + speedRatio * 3);
      const swing = Math.sin(cycle) * 0.92;
      const bounce = Math.abs(Math.sin(cycle)) * 2.2;
      this.legFront.rotation = swing;
      this.legBack.rotation = -swing;
      this.armFront.rotation = -swing * 0.72;
      this.armBack.rotation = swing * 0.72;
      this.bodyFront.y = -5 + bounce * 0.25;
      this.bodyBack.y = -5 + bounce * 0.25;
      this.head.y = -32 + bounce * 0.35;
      this.rotation = Math.sin(cycle * 0.5) * 0.025;
    } else if (this.state === 'jump') {
      const p = Phaser.Math.Clamp(t / 0.28, 0, 1);
      this.rotation = Phaser.Math.Linear(-0.12, 0.08, p);
      this.armFront.rotation = Phaser.Math.Linear(-1.55, -0.55, p);
      this.armBack.rotation = Phaser.Math.Linear(-1.2, -0.35, p);
      this.legFront.rotation = Phaser.Math.Linear(0.72, 0.2, p);
      this.legBack.rotation = Phaser.Math.Linear(-0.7, -0.25, p);
    } else if (this.state === 'fall') {
      const drift = Math.sin(t * 5) * 0.08;
      this.rotation = 0.12 + drift;
      this.armFront.rotation = -0.58 + drift;
      this.armBack.rotation = -1.05 - drift;
      this.legFront.rotation = 0.34;
      this.legBack.rotation = -0.32;
    } else if (this.state === 'slide') {
      this.rotation = -0.2;
      this.y += 8;
      this.head.rotation = 0.18;
      this.armFront.rotation = 1.18;
      this.armBack.rotation = 0.82;
      this.legFront.rotation = 1.32;
      this.legBack.rotation = 1.76;
    } else if (this.state === 'wall') {
      const stride = Math.sin(t * 11) * 0.55;
      this.rotation = -0.3;
      this.armFront.rotation = -1.12 + stride * 0.2;
      this.armBack.rotation = -0.45 - stride * 0.2;
      this.legFront.rotation = 0.82 + stride;
      this.legBack.rotation = 0.2 - stride;
    } else if (this.state === 'land') {
      const p = Phaser.Math.Clamp(t / 0.16, 0, 1);
      const crouch = Math.sin(p * Math.PI) * 8;
      this.y += crouch;
      this.rotation = -0.08 * Math.sin(p * Math.PI);
      this.armFront.rotation = 0.48 * Math.sin(p * Math.PI);
      this.armBack.rotation = -0.34 * Math.sin(p * Math.PI);
      this.legFront.rotation = 0.4 * Math.sin(p * Math.PI);
      this.legBack.rotation = -0.4 * Math.sin(p * Math.PI);
    }
  }

  private makeArm(x: number, y: number, alpha: number) {
    const root = this.scene.add.container(x, y);
    const upper = this.scene.add.image(0, 0, 'player-arm').setOrigin(0.5, 0.05).setScale(2.2).setAlpha(alpha);
    const hand = this.scene.add.image(0, 22, 'player-hand').setOrigin(0.5, 0.2).setScale(2.15).setAlpha(alpha);
    root.add([upper, hand]);
    return root;
  }

  private makeLeg(x: number, y: number, alpha: number) {
    const root = this.scene.add.container(x, y);
    const leg = this.scene.add.image(0, 0, 'player-leg').setOrigin(0.5, 0.05).setScale(2.25).setAlpha(alpha);
    root.add(leg);
    return root;
  }
}
