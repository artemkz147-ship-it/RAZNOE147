import Phaser from 'phaser';

type WorldObject = Phaser.GameObjects.GameObject & { destroy(fromScene?: boolean): void };

type SegmentRecord = {
  start: number;
  end: number;
  objects: WorldObject[];
};

type PlatformRecord = {
  object: Phaser.Physics.Arcade.Image;
  start: number;
  end: number;
  top: number;
};

export class WorldBuilder {
  readonly platforms: Phaser.Physics.Arcade.StaticGroup;
  readonly hazards: Phaser.Physics.Arcade.StaticGroup;
  readonly vaultSensors: Phaser.Physics.Arcade.StaticGroup;
  readonly trickSensors: Phaser.Physics.Arcade.StaticGroup;
  readonly tokens: Phaser.Physics.Arcade.StaticGroup;

  private scene: Phaser.Scene;
  private segments: SegmentRecord[] = [];
  private platformRecords: PlatformRecord[] = [];
  private cursorX = 0;
  private entryTop = 560;
  private templateIndex = 0;

  constructor(scene: Phaser.Scene) {
    this.scene = scene;
    this.platforms = scene.physics.add.staticGroup();
    this.hazards = scene.physics.add.staticGroup();
    this.vaultSensors = scene.physics.add.staticGroup();
    this.trickSensors = scene.physics.add.staticGroup();
    this.tokens = scene.physics.add.staticGroup();
  }

  reset() {
    this.platforms.clear(true, true);
    this.hazards.clear(true, true);
    this.vaultSensors.clear(true, true);
    this.trickSensors.clear(true, true);
    this.tokens.clear(true, true);
    this.segments = [];
    this.platformRecords = [];
    this.cursorX = -360;
    this.entryTop = 560;
    this.templateIndex = 0;

    const objects: WorldObject[] = [];
    this.addRoof(objects, -360, 1560, 560);
    this.addVent(objects, 490, 560);
    this.addTokenLine(objects, 690, 510, 5, 58, 0);
    this.segments.push({ start: -360, end: 1200, objects });
    this.cursorX = 1200;
  }

  update(playerX: number) {
    while (this.cursorX < playerX + 4300) {
      this.spawnNextTemplate();
    }
    this.cleanup(playerX - 1300);
  }

  getSafeSpawn(x: number) {
    const candidates = this.platformRecords.filter((p) => (p.object as any).scene && p.start < x - 30);
    let platform = candidates
      .filter((p) => p.end > x + 20)
      .sort((a, b) => b.start - a.start)[0];

    if (!platform) {
      platform = candidates.sort((a, b) => b.end - a.end)[0];
    }

    if (!platform) return { x: 180, y: 512 };
    const safeX = Phaser.Math.Clamp(x - 110, platform.start + 90, platform.end - 90);
    return { x: safeX, y: platform.top - 49 };
  }

  private spawnNextTemplate() {
    const start = this.cursorX;
    const objects: WorldObject[] = [];
    const mode = this.templateIndex % 4;
    let exitTop = this.entryTop;

    if (mode === 0) {
      const t0 = this.entryTop;
      const t1 = Phaser.Math.Clamp(t0 - 20, 410, 575);
      const t2 = Phaser.Math.Clamp(t1 - 20, 400, 565);
      this.addRoof(objects, start, 620, t0);
      this.addCrate(objects, start + 430, t0);
      this.addRoof(objects, start + 745, 510, t1);
      this.addOverhead(objects, start + 1020, t1);
      this.addRoof(objects, start + 1370, 430, t2);
      this.addTokenArc(objects, start + 580, t0 - 90, 7, 58, 68);
      exitTop = t2;
    } else if (mode === 1) {
      const low = this.entryTop;
      const high = Phaser.Math.Clamp(low - 145, 390, 465);
      const exit = Phaser.Math.Clamp(high + 60, 420, 535);
      this.addRoof(objects, start, 500, low);
      this.addWallClimb(objects, start + 500, low, high, 86);
      this.addRoof(objects, start + 540, 720, high);
      this.addVent(objects, start + 890, high);
      this.addRoof(objects, start + 1390, 410, exit);
      this.addTokenLine(objects, start + 575, high - 72, 7, 78, 0);
      exitTop = exit;
    } else if (mode === 2) {
      const t0 = this.entryTop;
      const t1 = Phaser.Math.Clamp(t0 + 45, 450, 590);
      const t2 = Phaser.Math.Clamp(t1 - 85, 405, 550);
      this.addRoof(objects, start, 450, t0);
      this.addRoof(objects, start + 585, 390, t1);
      this.addCrate(objects, start + 785, t1);
      this.addRoof(objects, start + 1090, 710, t2);
      this.addOverhead(objects, start + 1370, t2);
      this.addTokenArc(objects, start + 395, t0 - 95, 6, 60, 72);
      exitTop = t2;
    } else {
      const t0 = this.entryTop;
      const t1 = Phaser.Math.Clamp(t0 - 80, 400, 535);
      const t2 = Phaser.Math.Clamp(t1 + 105, 470, 590);
      this.addRoof(objects, start, 680, t0);
      this.addOverhead(objects, start + 330, t0);
      this.addRoof(objects, start + 800, 420, t1);
      this.addCrate(objects, start + 1040, t1);
      this.addRoof(objects, start + 1360, 440, t2);
      this.addTokenLine(objects, start + 1420, t2 - 82, 6, 58, -10);
      exitTop = t2;
    }

    this.segments.push({ start, end: start + 1800, objects });
    this.cursorX += 1800;
    this.entryTop = exitTop;
    this.templateIndex += 1;
  }

  private addRoof(objects: WorldObject[], start: number, width: number, top: number) {
    const centerX = start + width / 2;
    const roof = this.platforms.create(centerX, top + 36, 'roof') as Phaser.Physics.Arcade.Image;
    roof.setDisplaySize(width, 72).setDepth(12);
    roof.refreshBody();
    roof.setData('surface', 'roof');
    objects.push(roof as unknown as WorldObject);

    const facade = this.scene.add.tileSprite(centerX, top + 70, width, 430, 'facade');
    facade.setDepth(4).setOrigin(0.5, 0);
    objects.push(facade as unknown as WorldObject);

    this.platformRecords.push({ object: roof, start, end: start + width, top });
  }

  private addWallClimb(objects: WorldObject[], x: number, lowTop: number, highTop: number, width: number) {
    const height = Math.max(100, lowTop - highTop + 55);
    const wall = this.platforms.create(x + width / 2, highTop + height / 2, 'wall-face') as Phaser.Physics.Arcade.Image;
    wall.setDisplaySize(width, height).setDepth(11);
    wall.refreshBody();
    wall.setData('surface', 'wall');
    objects.push(wall as unknown as WorldObject);
  }

  private addCrate(objects: WorldObject[], x: number, platformTop: number) {
    const crate = this.hazards.create(x, platformTop - 34, 'crate') as Phaser.Physics.Arcade.Image;
    crate.setDisplaySize(68, 68).setDepth(18);
    crate.refreshBody();
    crate.setData('hazard', 'crate');
    objects.push(crate as unknown as WorldObject);

    const sensor = this.vaultSensors.create(x - 86, platformTop - 46, 'sensor') as Phaser.Physics.Arcade.Image;
    sensor.setVisible(false).setDisplaySize(100, 86);
    sensor.refreshBody();
    sensor.setData('used', false);
    objects.push(sensor as unknown as WorldObject);
  }

  private addOverhead(objects: WorldObject[], x: number, platformTop: number) {
    const sign = this.hazards.create(x, platformTop - 104, 'overhead') as Phaser.Physics.Arcade.Image;
    sign.setDisplaySize(178, 58).setDepth(18);
    sign.refreshBody();
    sign.setData('hazard', 'overhead');
    objects.push(sign as unknown as WorldObject);

    const sensor = this.trickSensors.create(x + 112, platformTop - 44, 'sensor') as Phaser.Physics.Arcade.Image;
    sensor.setVisible(false).setDisplaySize(76, 90);
    sensor.refreshBody();
    sensor.setData('kind', 'slide');
    sensor.setData('used', false);
    objects.push(sensor as unknown as WorldObject);
  }

  private addVent(objects: WorldObject[], x: number, platformTop: number) {
    const vent = this.scene.add.image(x, platformTop - 24, 'vent').setDisplaySize(86, 48).setDepth(15);
    objects.push(vent as unknown as WorldObject);
  }

  private addTokenLine(objects: WorldObject[], x: number, y: number, count: number, spacing: number, slope: number) {
    for (let i = 0; i < count; i += 1) {
      const token = this.tokens.create(x + i * spacing, y + i * slope, 'flow-token') as Phaser.Physics.Arcade.Image;
      token.setDisplaySize(34, 34).setDepth(22);
      token.refreshBody();
      objects.push(token as unknown as WorldObject);
    }
  }

  private addTokenArc(objects: WorldObject[], x: number, y: number, count: number, spacing: number, height: number) {
    for (let i = 0; i < count; i += 1) {
      const p = count <= 1 ? 0 : i / (count - 1);
      const arcY = y - Math.sin(p * Math.PI) * height;
      const token = this.tokens.create(x + i * spacing, arcY, 'flow-token') as Phaser.Physics.Arcade.Image;
      token.setDisplaySize(34, 34).setDepth(22);
      token.refreshBody();
      objects.push(token as unknown as WorldObject);
    }
  }

  private cleanup(cutoffX: number) {
    const expired = this.segments.filter((segment) => segment.end < cutoffX);
    if (expired.length === 0) return;

    expired.forEach((segment) => {
      segment.objects.forEach((object) => {
        if ((object as any).scene) object.destroy();
      });
    });
    const expiredSet = new Set(expired);
    this.segments = this.segments.filter((segment) => !expiredSet.has(segment));
    this.platformRecords = this.platformRecords.filter((platform) => (platform.object as any).scene);
  }
}
