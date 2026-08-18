import Phaser from 'phaser';
import { PlayerController } from './PlayerController';
import { WorldBuilder } from './WorldBuilder';

const asset = (path: string) => `assets/${path}`;

export class GameScene extends Phaser.Scene {
  private world!: WorldBuilder;
  private player!: PlayerController;
  private sky!: Phaser.GameObjects.Image;
  private cityFar!: Phaser.GameObjects.TileSprite;
  private cityNear!: Phaser.GameObjects.TileSprite;
  private jumpKeys: Phaser.Input.Keyboard.Key[] = [];
  private slideKeys: Phaser.Input.Keyboard.Key[] = [];
  private leftKey?: Phaser.Input.Keyboard.Key;
  private rightKey?: Phaser.Input.Keyboard.Key;
  private touchJump = false;
  private touchSlide = false;
  private previousJump = false;
  private runActive = false;
  private manualPaused = false;
  private externalPaused = false;
  private distance = 0;
  private flow = 1;
  private hudClock = 0;
  private runIndex = 0;
  private runTokens = 0;
  private runTricks = 0;
  private canRevive = true;
  private lastSafe = { x: 180, y: 500 };

  constructor() {
    super('Game');
  }

  preload() {
    this.load.image('player-body-front', asset('player/body-front.svg'));
    this.load.image('player-body-back', asset('player/body-back.svg'));
    this.load.image('player-head', asset('player/head.svg'));
    this.load.image('player-arm', asset('player/arm.svg'));
    this.load.image('player-hand', asset('player/hand.svg'));
    this.load.image('player-leg', asset('player/leg.svg'));

    this.load.image('sky-city', asset('world/sky-city.svg'));
    this.load.image('city-far', asset('world/city-far.svg'));
    this.load.image('city-near', asset('world/city-near.svg'));
    this.load.image('roof', asset('world/roof.svg'));
    this.load.image('facade', asset('world/facade.svg'));
    this.load.image('wall-face', asset('world/wall-face.svg'));
    this.load.image('crate', asset('world/crate.svg'));
    this.load.image('overhead', asset('world/overhead.svg'));
    this.load.image('vent', asset('world/vent.svg'));
    this.load.image('flow-token', asset('world/flow-token.svg'));
    this.load.image('sensor', asset('world/sensor.svg'));
  }

  create() {
    this.physics.world.setBounds(-1200, 0, 200000, 1400);
    this.createBackground();

    this.world = new WorldBuilder(this);
    this.world.reset();
    this.player = new PlayerController(
      this,
      180,
      500,
      (label, boost) => this.addTrick(label, boost, true),
      (reason) => this.crash(reason)
    );
    this.player.stop();

    this.physics.add.collider(this.player.host, this.world.platforms);
    this.physics.add.collider(this.player.host, this.world.hazards, () => this.crash('obstacle'));
    this.physics.add.overlap(this.player.host, this.world.vaultSensors, (_player, sensorObject) => {
      const sensor = sensorObject as Phaser.Physics.Arcade.Image;
      if (sensor.getData('used')) return;
      if (this.player.triggerVault()) {
        sensor.setData('used', true);
        if (sensor.body) (sensor.body as Phaser.Physics.Arcade.StaticBody).enable = false;
      }
    });
    this.physics.add.overlap(this.player.host, this.world.trickSensors, (_player, sensorObject) => {
      const sensor = sensorObject as Phaser.Physics.Arcade.Image;
      if (sensor.getData('used')) return;
      if (sensor.getData('kind') === 'slide' && this.player.isSliding()) {
        sensor.setData('used', true);
        this.addTrick('LOW PROFILE', 0.34, true);
      }
    });
    this.physics.add.overlap(this.player.host, this.world.tokens, (_player, tokenObject) => {
      const token = tokenObject as Phaser.Physics.Arcade.Image;
      if (!token.active) return;
      token.destroy();
      this.runTokens += 1;
      this.addTrick('FLOW +', 0.12, false);
      this.emitHud();
    });

    this.bindKeyboard();
    this.scale.on(Phaser.Scale.Events.RESIZE, (gameSize: Phaser.Structs.Size) => {
      this.layoutBackground(gameSize.width, gameSize.height);
    });

    this.physics.pause();
    window.dispatchEvent(new CustomEvent('parkour-ready'));
  }

  update(_time: number, delta: number) {
    if (!this.player) return;

    const camera = this.cameras.main;
    this.cityFar.tilePositionX = camera.scrollX * 0.1;
    this.cityNear.tilePositionX = camera.scrollX * 0.23;

    this.world.tokens.getChildren().forEach((child) => {
      const token = child as Phaser.Physics.Arcade.Image;
      if (token.active) token.rotation += delta * 0.0024;
    });

    if (!this.runActive || this.isPaused()) {
      this.player.update(delta, this.distance);
      return;
    }

    const jumpDown = this.jumpKeys.some((key) => key.isDown) || this.touchJump;
    const slideDown = this.slideKeys.some((key) => key.isDown) || this.touchSlide;
    if (jumpDown && !this.previousJump) this.player.pressJump();
    if (!jumpDown && this.previousJump) this.player.releaseJump();
    this.previousJump = jumpDown;
    this.player.setSlide(slideDown);

    const correction = (this.rightKey?.isDown ? 1 : 0) - (this.leftKey?.isDown ? 1 : 0);
    this.player.setCorrection(correction);

    const positionBefore = this.player.getPosition();
    this.distance = Math.max(this.distance, (positionBefore.x - 180) / 10);
    this.flow = Math.max(1, this.flow - delta * 0.000055);
    this.player.update(delta, this.distance);

    const position = this.player.getPosition();
    this.world.update(position.x);
    if (this.player.body.blocked.down || this.player.body.touching.down) {
      this.lastSafe = this.world.getSafeSpawn(position.x);
    }

    if (position.y > 900 || position.x < camera.scrollX - 360) {
      this.crash('fall');
      return;
    }

    this.hudClock += delta;
    if (this.hudClock >= 70) {
      this.hudClock = 0;
      this.emitHud();
    }
  }

  startRun() {
    this.physics.resume();
    this.world.reset();
    this.distance = 0;
    this.flow = 1;
    this.hudClock = 0;
    this.runTokens = 0;
    this.runTricks = 0;
    this.runIndex += 1;
    this.canRevive = true;
    this.manualPaused = false;
    this.externalPaused = false;
    this.previousJump = false;
    this.touchJump = false;
    this.touchSlide = false;
    this.lastSafe = { x: 180, y: 500 };
    this.player.reset(180, 500, 430);
    this.cameras.main.stopFollow();
    this.cameras.main.setScroll(0, 0);
    this.cameras.main.startFollow(this.player.host, true, 0.1, 0.085, -300, 38);
    this.cameras.main.fadeIn(180, 10, 12, 25);
    this.runActive = true;
    this.emitHud();
    window.dispatchEvent(new CustomEvent('parkour-started', { detail: { runIndex: this.runIndex } }));
  }

  revive() {
    if (this.runActive || !this.canRevive) return false;
    this.canRevive = false;
    this.manualPaused = false;
    this.externalPaused = false;
    this.physics.resume();
    const safe = this.world.getSafeSpawn(this.lastSafe.x);
    this.player.revive(safe.x, safe.y - 8);
    this.cameras.main.startFollow(this.player.host, true, 0.1, 0.085, -300, 38);
    this.cameras.main.fadeIn(220, 12, 14, 28);
    this.runActive = true;
    window.dispatchEvent(new CustomEvent('parkour-revived'));
    return true;
  }

  togglePause() {
    if (!this.runActive) return;
    this.manualPaused = !this.manualPaused;
    this.applyPauseState();
  }

  setExternalPaused(paused: boolean) {
    this.externalPaused = paused;
    this.applyPauseState();
  }

  setTouchAction(action: 'jump' | 'slide', held: boolean) {
    if (action === 'jump') this.touchJump = held;
    else this.touchSlide = held;
  }

  isRunning() {
    return this.runActive;
  }

  isManuallyPaused() {
    return this.manualPaused;
  }

  private createBackground() {
    this.sky = this.add.image(0, 0, 'sky-city').setOrigin(0).setScrollFactor(0).setDepth(-30);
    this.cityFar = this.add.tileSprite(0, 0, 1600, 520, 'city-far').setOrigin(0, 1).setScrollFactor(0).setDepth(-18);
    this.cityNear = this.add.tileSprite(0, 0, 1600, 420, 'city-near').setOrigin(0, 1).setScrollFactor(0).setDepth(-10);
    this.layoutBackground(this.scale.width, this.scale.height);
  }

  private layoutBackground(width: number, height: number) {
    this.sky.setDisplaySize(width, height);
    this.cityFar.setPosition(0, height).setSize(width, Math.max(320, height * 0.68));
    this.cityNear.setPosition(0, height).setSize(width, Math.max(260, height * 0.52));
  }

  private bindKeyboard() {
    if (!this.input.keyboard) return;
    this.jumpKeys = [
      this.input.keyboard.addKey(Phaser.Input.Keyboard.KeyCodes.SPACE),
      this.input.keyboard.addKey(Phaser.Input.Keyboard.KeyCodes.UP),
      this.input.keyboard.addKey(Phaser.Input.Keyboard.KeyCodes.W)
    ];
    this.slideKeys = [
      this.input.keyboard.addKey(Phaser.Input.Keyboard.KeyCodes.DOWN),
      this.input.keyboard.addKey(Phaser.Input.Keyboard.KeyCodes.S)
    ];
    this.leftKey = this.input.keyboard.addKey(Phaser.Input.Keyboard.KeyCodes.A);
    this.rightKey = this.input.keyboard.addKey(Phaser.Input.Keyboard.KeyCodes.D);
  }

  private addTrick(label: string, boost: number, countAsTrick: boolean) {
    if (!this.runActive) return;
    this.flow = Phaser.Math.Clamp(this.flow + boost, 1, 5);
    if (countAsTrick) this.runTricks += 1;
    window.dispatchEvent(new CustomEvent('parkour-trick', {
      detail: { label, flow: this.flow, tricks: this.runTricks, tokens: this.runTokens }
    }));
  }

  private crash(reason: string) {
    if (!this.runActive) return;
    this.runActive = false;
    this.player.stop();
    this.physics.pause();
    this.cameras.main.shake(180, 0.0075);
    this.cameras.main.flash(80, 255, 80, 70, false);
    window.dispatchEvent(new CustomEvent('parkour-gameover', {
      detail: {
        reason,
        distance: Math.max(0, Math.floor(this.distance)),
        flow: this.flow,
        tokens: this.runTokens,
        tricks: this.runTricks,
        runIndex: this.runIndex,
        canRevive: this.canRevive
      }
    }));
  }

  private emitHud() {
    window.dispatchEvent(new CustomEvent('parkour-hud', {
      detail: {
        distance: Math.max(0, Math.floor(this.distance)),
        flow: this.flow,
        speed: this.player?.getSpeed?.() ?? 0,
        tokens: this.runTokens
      }
    }));
  }

  private isPaused() {
    return this.manualPaused || this.externalPaused;
  }

  private applyPauseState() {
    const paused = this.isPaused();
    if (this.runActive) {
      if (paused) this.physics.pause();
      else this.physics.resume();
    }
    window.dispatchEvent(new CustomEvent('parkour-pause-state', { detail: { paused, manual: this.manualPaused } }));
  }
}
