import type { TrickKind } from './DropTypes';

export class DropInput {
  moveX = 0;
  moveZ = 0;

  private keys = new Set<string>();
  private jumpPressed = false;
  private landPressed = false;
  private tricks: TrickKind[] = [];
  private touchPointer: number | null = null;
  private touchOrigin = { x: 0, y: 0 };
  private touchX = 0;
  private touchZ = 0;

  constructor() {
    this.bindKeyboard();
    this.bindTouch();
  }

  update() {
    const keyX = (this.keys.has('KeyD') ? 1 : 0) - (this.keys.has('KeyA') ? 1 : 0);
    const keyZ = (this.keys.has('KeyW') ? 1 : 0) - (this.keys.has('KeyS') ? 1 : 0);
    this.moveX = Math.abs(keyX) > Math.abs(this.touchX) ? keyX : this.touchX;
    this.moveZ = Math.abs(keyZ) > Math.abs(this.touchZ) ? keyZ : this.touchZ;
  }

  consumeJump() {
    const value = this.jumpPressed;
    this.jumpPressed = false;
    return value;
  }

  consumeLand() {
    const value = this.landPressed;
    this.landPressed = false;
    return value;
  }

  consumeTricks() {
    const value = this.tricks.splice(0, this.tricks.length);
    return value;
  }

  private queueTrick(kind: TrickKind) {
    if (this.tricks.length < 6) this.tricks.push(kind);
  }

  private bindKeyboard() {
    window.addEventListener('keydown', (event) => {
      if (event.repeat) return;
      if (event.code === 'Space') this.jumpPressed = true;
      if (event.code === 'KeyC' || event.code === 'ControlLeft' || event.code === 'ControlRight') this.landPressed = true;
      if (event.code === 'Digit1' || event.code === 'KeyQ') this.queueTrick('front');
      if (event.code === 'Digit2' || event.code === 'KeyE') this.queueTrick('back');
      if (event.code === 'Digit3' || event.code === 'KeyZ') this.queueTrick('side');
      if (event.code === 'Digit4' || event.code === 'KeyX') this.queueTrick('twist');
      this.keys.add(event.code);
      if (['Space', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(event.code)) event.preventDefault();
    });
    window.addEventListener('keyup', (event) => this.keys.delete(event.code));
    window.addEventListener('blur', () => {
      this.keys.clear();
      this.touchX = 0;
      this.touchZ = 0;
    });
  }

  private bindTouch() {
    const surface = document.querySelector<HTMLElement>('#touchSteer');
    const jump = document.querySelector<HTMLButtonElement>('#touchJump');
    const land = document.querySelector<HTMLButtonElement>('#touchLand');
    const front = document.querySelector<HTMLButtonElement>('#trickFront');
    const back = document.querySelector<HTMLButtonElement>('#trickBack');
    const side = document.querySelector<HTMLButtonElement>('#trickSide');
    const twist = document.querySelector<HTMLButtonElement>('#trickTwist');

    jump?.addEventListener('pointerdown', (event) => { event.preventDefault(); this.jumpPressed = true; });
    land?.addEventListener('pointerdown', (event) => { event.preventDefault(); this.landPressed = true; });
    front?.addEventListener('pointerdown', (event) => { event.preventDefault(); this.queueTrick('front'); });
    back?.addEventListener('pointerdown', (event) => { event.preventDefault(); this.queueTrick('back'); });
    side?.addEventListener('pointerdown', (event) => { event.preventDefault(); this.queueTrick('side'); });
    twist?.addEventListener('pointerdown', (event) => { event.preventDefault(); this.queueTrick('twist'); });

    if (!surface) return;
    surface.addEventListener('pointerdown', (event) => {
      if (this.touchPointer !== null) return;
      this.touchPointer = event.pointerId;
      this.touchOrigin = { x: event.clientX, y: event.clientY };
      surface.setPointerCapture(event.pointerId);
    });
    surface.addEventListener('pointermove', (event) => {
      if (event.pointerId !== this.touchPointer) return;
      this.touchX = Math.max(-1, Math.min(1, (event.clientX - this.touchOrigin.x) / 55));
      this.touchZ = Math.max(-1, Math.min(1, -(event.clientY - this.touchOrigin.y) / 55));
    });
    const release = (event: PointerEvent) => {
      if (event.pointerId !== this.touchPointer) return;
      this.touchPointer = null;
      this.touchX = 0;
      this.touchZ = 0;
    };
    surface.addEventListener('pointerup', release);
    surface.addEventListener('pointercancel', release);
  }
}
