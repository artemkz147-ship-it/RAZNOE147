import sfxManifest from '../game3d/sfx.json';

type Manifest = { footsteps: string[]; landings: string[]; breaks: string[]; ambient: string[] };

export class DropAudioSystem {
  private manifest = sfxManifest as Manifest;
  private ambient: HTMLAudioElement | null = null;

  startAmbient() {
    if (this.ambient || !this.manifest.ambient.length) return;
    const audio = new Audio(this.manifest.ambient[0]);
    audio.loop = true;
    audio.volume = 0.14;
    audio.preload = 'auto';
    this.ambient = audio;
    void audio.play().catch(() => {});
  }

  stopAmbient() {
    if (!this.ambient) return;
    this.ambient.pause();
    this.ambient.currentTime = 0;
    this.ambient = null;
  }

  jump() {
    this.play(this.manifest.footsteps, 0.2, 1.08);
  }

  trick(index: number) {
    this.play(this.manifest.footsteps, 0.1, 1.18 + (index % 4) * 0.06);
  }

  landing(intensity: number) {
    this.play(this.manifest.landings, 0.32 + Math.min(0.28, intensity * 0.02), 0.94);
  }

  fail() {
    this.play(this.manifest.breaks.length ? this.manifest.breaks : this.manifest.landings, 0.34, 0.82);
  }

  private play(pool: string[], volume: number, rate: number) {
    if (!pool.length) return;
    const source = pool[Math.floor(Math.random() * pool.length)];
    const audio = new Audio(source);
    audio.volume = Math.max(0, Math.min(1, volume));
    audio.playbackRate = Math.max(0.5, Math.min(2, rate));
    audio.preload = 'auto';
    void audio.play().catch(() => {});
  }
}
