export class Input {
  yaw = 0;
  pitch = 0;
  moveX = 0;
  moveZ = 0;
  jump = false;
  sprint = false;
  crouch = false;
  interact = false;

  private keys = new Set<string>();
  private jumpPressed = false;
  private crouchPressed = false;
  private interactPressed = false;
  private sensitivity = 0.0022;
  private canvas: HTMLCanvasElement;
  private touchMovePointer: number | null = null;
  private touchLookPointer: number | null = null;
  private touchMoveOrigin = { x: 0, y: 0 };
  private touchLookLast = { x: 0, y: 0 };
  private touchMoveX = 0;
  private touchMoveZ = 0;
  private touchJump = false;
  private touchSprint = false;
  private touchCrouch = false;

  constructor(canvas: HTMLCanvasElement) {
    this.canvas = canvas;
    this.bindKeyboard();
    this.bindMouse();
    this.bindTouch();
  }

  update() {
    const keyX = (this.keys.has('KeyD') ? 1 : 0) - (this.keys.has('KeyA') ? 1 : 0);
    const keyZ = (this.keys.has('KeyW') ? 1 : 0) - (this.keys.has('KeyS') ? 1 : 0);
    this.moveX = Math.abs(keyX) > Math.abs(this.touchMoveX) ? keyX : this.touchMoveX;
    this.moveZ = Math.abs(keyZ) > Math.abs(this.touchMoveZ) ? keyZ : this.touchMoveZ;
    this.sprint = this.touchSprint || this.keys.has('ShiftLeft') || this.keys.has('ShiftRight');
    this.crouch = this.touchCrouch || this.keys.has('ControlLeft') || this.keys.has('ControlRight') || this.keys.has('KeyC');
    this.jump = this.touchJump || this.keys.has('Space');
    this.interact = this.keys.has('KeyE');
  }

  consumeJumpPress() {
    const value = this.jumpPressed;
    this.jumpPressed = false;
    return value;
  }

  consumeCrouchPress() {
    const value = this.crouchPressed;
    this.crouchPressed = false;
    return value;
  }

  consumeInteractPress() {
    const value = this.interactPressed;
    this.interactPressed = false;
    return value;
  }

  private bindKeyboard() {
    window.addEventListener('keydown', (event) => {
      if (event.code === 'Space' && !this.keys.has('Space')) this.jumpPressed = true;
      if ((event.code === 'ControlLeft' || event.code === 'ControlRight' || event.code === 'KeyC') && !this.keys.has(event.code)) this.crouchPressed = true;
      if (event.code === 'KeyE' && !this.keys.has('KeyE')) this.interactPressed = true;
      this.keys.add(event.code);
      if (['Space', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(event.code)) event.preventDefault();
    });
    window.addEventListener('keyup', (event) => this.keys.delete(event.code));
    window.addEventListener('blur', () => this.keys.clear());
  }

  private bindMouse() {
    this.canvas.addEventListener('click', () => {
      if (matchMedia('(pointer:fine)').matches && document.pointerLockElement !== this.canvas) {
        void this.canvas.requestPointerLock();
      }
    });
    window.addEventListener('mousemove', (event) => {
      if (document.pointerLockElement !== this.canvas) return;
      this.yaw -= event.movementX * this.sensitivity;
      this.pitch -= event.movementY * this.sensitivity;
      this.pitch = Math.max(-1.42, Math.min(1.32, this.pitch));
    });
  }

  private bindTouch() {
    const jumpButton = document.querySelector<HTMLButtonElement>('#touchJump');
    const crouchButton = document.querySelector<HTMLButtonElement>('#touchCrouch');
    const sprintButton = document.querySelector<HTMLButtonElement>('#touchSprint');
    const surface = document.querySelector<HTMLElement>('#touchSurface');

    const hold = (button: HTMLButtonElement | null, down: () => void, up: () => void) => {
      if (!button) return;
      button.addEventListener('pointerdown', (event) => { event.preventDefault(); button.setPointerCapture(event.pointerId); down(); });
      button.addEventListener('pointerup', (event) => { event.preventDefault(); up(); });
      button.addEventListener('pointercancel', up);
    };

    hold(jumpButton, () => { this.touchJump = true; this.jumpPressed = true; }, () => { this.touchJump = false; });
    hold(crouchButton, () => { this.touchCrouch = true; this.crouchPressed = true; }, () => { this.touchCrouch = false; });
    hold(sprintButton, () => { this.touchSprint = true; }, () => { this.touchSprint = false; });

    if (!surface) return;
    surface.addEventListener('pointerdown', (event) => {
      if (event.clientX < innerWidth * 0.48 && this.touchMovePointer === null) {
        this.touchMovePointer = event.pointerId;
        this.touchMoveOrigin = { x: event.clientX, y: event.clientY };
      } else if (this.touchLookPointer === null) {
        this.touchLookPointer = event.pointerId;
        this.touchLookLast = { x: event.clientX, y: event.clientY };
      }
      surface.setPointerCapture(event.pointerId);
    });
    surface.addEventListener('pointermove', (event) => {
      if (event.pointerId === this.touchMovePointer) {
        const dx = event.clientX - this.touchMoveOrigin.x;
        const dy = event.clientY - this.touchMoveOrigin.y;
        this.touchMoveX = Math.max(-1, Math.min(1, dx / 58));
        this.touchMoveZ = Math.max(-1, Math.min(1, -dy / 58));
      }
      if (event.pointerId === this.touchLookPointer) {
        const dx = event.clientX - this.touchLookLast.x;
        const dy = event.clientY - this.touchLookLast.y;
        this.touchLookLast = { x: event.clientX, y: event.clientY };
        this.yaw -= dx * 0.0043;
        this.pitch -= dy * 0.0043;
        this.pitch = Math.max(-1.42, Math.min(1.32, this.pitch));
      }
    });
    const release = (event: PointerEvent) => {
      if (event.pointerId === this.touchMovePointer) {
        this.touchMovePointer = null;
        this.touchMoveX = 0;
        this.touchMoveZ = 0;
      }
      if (event.pointerId === this.touchLookPointer) this.touchLookPointer = null;
    };
    surface.addEventListener('pointerup', release);
    surface.addEventListener('pointercancel', release);
  }
}
