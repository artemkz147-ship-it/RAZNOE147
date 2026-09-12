import sfxManifest from './sfx.json';
import type { ParkourEvent } from './PlayerController';

type SfxManifest = {
  footsteps: string[];
  landings: string[];
  breaks: string[];
  ambient: string[];
};

export class AudioSystem {
  private manifest = sfxManifest as SfxManifest;
  private stepClock = 0;
  private stepIndex = 0;
  private ambient: HTMLAudioElement | null = null;
  private enabled = true;

  setEnabled(value: boolean) {
    this.enabled = value;
    if (!value) this.stopAmbient();
  }

  startAmbient() {
    if (!this.enabled || !this.manifest.ambient.length || this.ambient) return;
    const source = this.manifest.ambient[0];
    const audio = new Audio(source);
    audio.loop = true;
    audio.volume = 0.16;
    audio.preload = 'auto';
    this.ambient = audio;
    void audio.play().catch(() => {
      // Browser autoplay can block ambient until the player interacts again.
    });
  }

  stopAmbient() {
    if (!this.ambient) return;
    this.ambient.pause();
    this.ambient.currentTime = 0;
    this.ambient = null;
  }

  update(dt: number, speed: number, grounded: boolean, motion: string) {
    if (!this.enabled || !grounded || speed < 1.6 || motion === 'EDGE CLIMB' || motion === 'VAULT') {
      this.stepClock = Math.min(this.stepClock, 0.12);
      return;
    }
    this.stepClock -= dt;
    if (this.stepClock > 0) return;
    const interval = Math.max(0.19, 0.48 - speed * 0.028);
    this.stepClock = motion === 'SLIDE' ? interval * 1.7 : interval;
    if (motion !== 'SLIDE') this.playFootstep(speed);
  }

  parkour(event: ParkourEvent) {
    if (!this.enabled) return;
    if (event.type === 'hard-land' || event.type === 'roll-land' || event.type === 'perfect-land') {
      this.play(this.manifest.landings, 0.34 + event.intensity * 0.22, 0.92 + event.intensity * 0.08);
      return;
    }
    if (event.type === 'vault' || event.type === 'mantle' || event.type === 'wall-kick') {
      this.play(this.manifest.footsteps, 0.15 + event.intensity * 0.08, 1.15 + event.intensity * 0.08);
    }
  }

  breakObject() {
    if (!this.enabled) return;
    this.play(this.manifest.breaks, 0.45, 0.92 + Math.random() * 0.16);
  }

  private playFootstep(speed: number) {
    const pool = this.manifest.footsteps;
    if (!pool.length) return;
    const source = pool[this.stepIndex % pool.length];
    this.stepIndex += 1;
    const volume = Math.min(0.34, 0.16 + speed * 0.018);
    const rate = 0.94 + Math.min(0.16, speed * 0.01) + (this.stepIndex % 3) * 0.018;
    this.playOne(source, volume, rate);
  }

  private play(pool: string[], volume: number, rate: number) {
    if (!pool.length) return;
    const index = Math.floor(Math.random() * pool.length);
    this.playOne(pool[index], volume, rate);
  }

  private playOne(source: string, volume: number, rate: number) {
    const audio = new Audio(source);
    audio.volume = Math.max(0, Math.min(1, volume));
    audio.playbackRate = Math.max(0.5, Math.min(2, rate));
    audio.preload = 'auto';
    void audio.play().catch(() => {});
  }
}
