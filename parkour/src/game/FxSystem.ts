import Phaser from 'phaser';

export class FxSystem {
  private scene: Phaser.Scene;
  private trailClock = 0;
  private trailIndex = 0;

  constructor(scene: Phaser.Scene) {
    this.scene = scene;
  }

  update(delta: number, x: number, y: number, speed: number) {
    if (speed < 500) return;
    this.trailClock += delta;
    if (this.trailClock < Math.max(55, 125 - (speed - 500) * 0.2)) return;
    this.trailClock = 0;

    const offsets = [-32, -5, 26, 43];
    const streak = this.scene.add.image(x - 74, y + offsets[this.trailIndex % offsets.length], 'fx-speed');
    this.trailIndex += 1;
    streak.setDepth(24).setAlpha(0.22 + Math.min(0.26, (speed - 500) / 600)).setScale(0.62 + Math.min(0.34, speed / 1600));
    this.scene.tweens.add({
      targets: streak,
      x: x - 230,
      alpha: 0,
      scaleX: streak.scaleX * 1.28,
      duration: 210,
      ease: 'Quad.easeOut',
      onComplete: () => streak.destroy()
    });
  }

  trickBurst(x: number, y: number, label: string) {
    const isLanding = label === 'CLEAN LAND' || label === 'VAULT' || label === 'LOW PROFILE';
    if (isLanding) this.dust(x - 12, y + 43);
    this.sparks(x + 18, y - 4, label.includes('WALL') ? 6 : 4);

    const camera = this.scene.cameras.main;
    camera.zoomTo(1.014, 55, 'Sine.easeOut');
    this.scene.time.delayedCall(65, () => camera.zoomTo(1, 155, 'Sine.easeInOut'));
  }

  tokenBurst(x: number, y: number) {
    this.sparks(x, y, 3);
    const pulse = this.scene.add.image(x, y, 'flow-token').setDepth(25).setAlpha(0.75).setScale(0.65);
    this.scene.tweens.add({
      targets: pulse,
      alpha: 0,
      scale: 1.35,
      duration: 180,
      ease: 'Cubic.easeOut',
      onComplete: () => pulse.destroy()
    });
  }

  private dust(x: number, y: number) {
    const puff = this.scene.add.image(x, y, 'fx-dust').setDepth(23).setAlpha(0.48).setScale(0.62).setOrigin(0.5, 0.72);
    this.scene.tweens.add({
      targets: puff,
      x: x - 46,
      y: y - 7,
      alpha: 0,
      scaleX: 1.08,
      scaleY: 0.88,
      duration: 330,
      ease: 'Quad.easeOut',
      onComplete: () => puff.destroy()
    });
  }

  private sparks(x: number, y: number, count: number) {
    const angles = [-2.75, -2.25, -1.75, -1.25, -0.75, -0.25];
    for (let i = 0; i < count; i += 1) {
      const angle = angles[i % angles.length];
      const spark = this.scene.add.image(x, y, 'fx-spark').setDepth(26).setScale(0.45 + i * 0.035).setRotation(angle).setAlpha(0.9);
      const distance = 34 + i * 9;
      this.scene.tweens.add({
        targets: spark,
        x: x + Math.cos(angle) * distance,
        y: y + Math.sin(angle) * distance,
        alpha: 0,
        scaleX: spark.scaleX * 0.45,
        duration: 170 + i * 16,
        ease: 'Quad.easeOut',
        onComplete: () => spark.destroy()
      });
    }
  }
}
