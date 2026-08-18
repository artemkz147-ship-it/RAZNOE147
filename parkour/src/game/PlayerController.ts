import Phaser from 'phaser';
import { PlayerRig, type RigState } from './PlayerRig';

type TrickHandler = (label: string, boost: number) => void;
type CrashHandler = (reason: string) => void;

export class PlayerController {
  readonly host: Phaser.GameObjects.Zone;
  readonly body: Phaser.Physics.Arcade.Body;
  readonly rig: PlayerRig;

  private onTrick: TrickHandler;
  private onCrash: CrashHandler;
  private speed = 430;
  private maxSpeed = 720;
  private correction = 0;
  private active = false;
  private jumpHeld = false;
  private slideHeld = false;
  private jumpBufferMs = 0;
  private coyoteMs = 0;
  private airMs = 0;
  private landMs = 0;
  private vaultMs = 0;
  private wallMs = 0;
  private wallJumpMs = 0;
  private wallCooldownMs = 0;
  private crashBlockMs = 0;
  private wasGrounded = false;
  private isSlidingNow = false;
  private wallTrickLatched = false;

  constructor(scene: Phaser.Scene, x: number, y: number, onTrick: TrickHandler, onCrash: CrashHandler) {
    this.host = scene.add.zone(x, y, 64, 96).setOrigin(0.5);
    scene.physics.add.existing(this.host);
    this.body = (this.host as any).body as Phaser.Physics.Arcade.Body;
    this.body.setSize(42, 96, true);
    this.body.setGravityY(1950);
    this.body.setMaxVelocity(900, 1700);
    this.body.setBounce(0);
    this.body.setDragX(0);
    this.rig = new PlayerRig(scene, x, y + 2);
    this.onTrick = onTrick;
    this.onCrash = onCrash;
  }

  reset(x: number, y: number, speed = 430) {
    this.active = true;
    this.speed = speed;
    this.correction = 0;
    this.jumpHeld = false;
    this.slideHeld = false;
    this.jumpBufferMs = 0;
    this.coyoteMs = 120;
    this.airMs = 0;
    this.landMs = 0;
    this.vaultMs = 0;
    this.wallMs = 0;
    this.wallJumpMs = 0;
    this.wallCooldownMs = 0;
    this.crashBlockMs = 0;
    this.wasGrounded = false;
    this.wallTrickLatched = false;
    this.isSlidingNow = false;
    this.host.setPosition(x, y);
    this.body.enable = true;
    this.body.setAllowGravity(true);
    this.body.setGravityY(1950);
    this.body.setSize(42, 96, true);
    this.body.setVelocity(this.speed, 0);
    this.rig.setVisible(true).setPosition(x, y + 2).setState('run');
  }

  stop() {
    this.active = false;
    this.body.setVelocity(0, 0);
  }

  revive(x: number, y: number) {
    const retainedSpeed = this.speed;
    this.reset(x, y, retainedSpeed);
    this.body.setVelocityY(-140);
    this.rig.setState('jump');
  }

  setCorrection(value: number) {
    this.correction = Phaser.Math.Clamp(value, -1, 1);
  }

  pressJump() {
    if (!this.active) return;
    this.jumpHeld = true;
    this.jumpBufferMs = 130;

    if (this.wallMs > 0) {
      this.wallJump();
    }
  }

  releaseJump() {
    this.jumpHeld = false;
    if (this.body.velocity.y < -280 && this.wallMs <= 0) {
      this.body.setVelocityY(-280);
    }
  }

  setSlide(held: boolean) {
    this.slideHeld = held;
  }

  triggerVault() {
    if (!this.active || this.vaultMs > 0 || this.wallMs > 0) return false;
    const grounded = this.isGrounded();
    if (!grounded || this.isSlidingNow) return false;

    this.vaultMs = 360;
    this.jumpBufferMs = 0;
    this.body.setVelocityY(-500);
    this.body.setVelocityX(this.speed * 1.08);
    this.rig.setState('vault');
    this.onTrick('VAULT', 0.38);
    return true;
  }

  isSliding() {
    return this.isSlidingNow;
  }

  isWallRunning() {
    return this.wallMs > 0;
  }

  getSpeed() {
    return this.speed;
  }

  getPosition() {
    return { x: this.host.x, y: this.host.y };
  }

  update(delta: number, distanceMeters: number) {
    this.rig.setPosition(this.host.x, this.host.y + 2);
    if (!this.active) {
      this.rig.update(delta, 0.8);
      return;
    }

    const dt = Math.min(delta, 50);
    this.jumpBufferMs = Math.max(0, this.jumpBufferMs - dt);
    this.landMs = Math.max(0, this.landMs - dt);
    this.vaultMs = Math.max(0, this.vaultMs - dt);
    this.wallMs = Math.max(0, this.wallMs - dt);
    this.wallJumpMs = Math.max(0, this.wallJumpMs - dt);
    this.wallCooldownMs = Math.max(0, this.wallCooldownMs - dt);
    this.crashBlockMs = Math.max(0, this.crashBlockMs - dt);

    this.speed = Math.min(this.maxSpeed, 430 + distanceMeters * 0.11);
    const grounded = this.isGrounded();

    if (grounded) {
      this.coyoteMs = 115;
      if (!this.wasGrounded && this.airMs > 420 && this.vaultMs <= 0) {
        this.landMs = 170;
        if (this.airMs > 760) this.onTrick('CLEAN LAND', 0.22);
      }
      this.airMs = 0;
      this.wallTrickLatched = false;
    } else {
      this.coyoteMs = Math.max(0, this.coyoteMs - dt);
      this.airMs += dt;
    }

    if (this.jumpBufferMs > 0 && (grounded || this.coyoteMs > 0) && this.vaultMs <= 0) {
      this.exitSlide();
      this.jumpBufferMs = 0;
      this.coyoteMs = 0;
      this.body.setVelocityY(-780);
      this.landMs = 0;
      this.rig.setState('jump');
    }

    if (!grounded && this.body.blocked.right && this.wallCooldownMs <= 0 && this.wallJumpMs <= 0) {
      this.startWallRun();
    }

    if (this.wallMs > 0) {
      this.body.setGravityY(540);
      const wallRise = this.jumpHeld ? -315 : -245;
      if (this.body.velocity.y > wallRise) this.body.setVelocityY(wallRise);
      this.body.setVelocityX(Math.max(80, this.speed * 0.28));
      this.rig.setState('wall');
    } else {
      this.body.setGravityY(1950);
      if (this.wallJumpMs <= 0) {
        const correctionBoost = this.correction * (grounded ? 54 : 82);
        this.body.setVelocityX(this.speed + correctionBoost);
      }
    }

    if (grounded && this.body.blocked.right && this.wallMs <= 0) {
      if (this.crashBlockMs <= 0) this.crashBlockMs = 170;
      else if (this.crashBlockMs <= 40) this.onCrash('wall');
    } else {
      this.crashBlockMs = 0;
    }

    if (grounded && this.slideHeld && this.vaultMs <= 0 && this.landMs <= 0) {
      this.enterSlide();
    } else if (!this.slideHeld || !grounded) {
      this.exitSlide();
    }

    const state = this.pickRigState(grounded);
    this.rig.setState(state);
    this.rig.update(delta, Phaser.Math.Clamp(this.speed / 620, 0.65, 1.25));
    this.wasGrounded = grounded;
  }

  private pickRigState(grounded: boolean): RigState {
    if (this.wallJumpMs > 0) return 'wallJump';
    if (this.wallMs > 0) return 'wall';
    if (this.vaultMs > 0) return 'vault';
    if (this.isSlidingNow) return 'slide';
    if (this.landMs > 0) return 'land';
    if (!grounded) return this.body.velocity.y < 70 ? 'jump' : 'fall';
    return 'run';
  }

  private startWallRun() {
    this.wallMs = 760;
    this.wallCooldownMs = 120;
    this.exitSlide();
    if (!this.wallTrickLatched) {
      this.wallTrickLatched = true;
      this.onTrick('WALL RUN', 0.44);
    }
  }

  private wallJump() {
    this.wallMs = 0;
    this.wallJumpMs = 260;
    this.wallCooldownMs = 420;
    this.body.setGravityY(1950);
    this.body.setVelocity(-230, -820);
    this.host.x -= 8;
    this.rig.setState('wallJump');
    this.onTrick('WALL KICK', 0.5);
  }

  private enterSlide() {
    if (this.isSlidingNow) return;
    this.isSlidingNow = true;
    this.body.setSize(60, 38, true);
  }

  private exitSlide() {
    if (!this.isSlidingNow) return;
    if (this.isGrounded()) this.host.y -= 29;
    this.isSlidingNow = false;
    this.body.setSize(42, 96, true);
  }

  private isGrounded() {
    return this.body.blocked.down || this.body.touching.down;
  }
}
