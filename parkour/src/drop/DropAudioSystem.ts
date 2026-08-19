import sfxManifest from '../game3d/sfx.json';

type Manifest = { footsteps: string[]; landings: string[]; breaks: string[]; ambient: string[] };

export class DropAudioSystem {
  private manifest = sfxManifest as Manifest;

  // DF6 deliberately has no always-on ambient loop. The previous loop was repetitive
  // and made every rooftop feel like the same noisy room. We only play sounds tied to
  // physical events: steps, takeoff, impact and failure.
  startAmbient() {}
  stopAmbient() {}

  footstep(strength = 1) {
    this.play(this.manifest.footsteps, 0.07 + Math.min(0.06, strength * 0.03), 0.94 + Math.random() * 0.08);
  }

  jump() {
    this.play(this.manifest.footsteps, 0.09, 1.04);
  }

  trick(_index: number) {
    // Air tricks are silent. Reusing footsteps for every flip was the source of the
    // constant unpleasant clicking in DF5.
  }

  landing(intensity: number) {
    this.play(this.manifest.landings, 0.16 + Math.min(0.18, intensity * 0.012), 0.91 + Math.random() * 0.06);
  }

  fail() {
    this.play(this.manifest.landings, 0.16, 0.84);
  }

  private play(pool: string[], volume: number, rate: number) {
    if (!pool.length) return;
    const source = pool[Math.floor(Math.random() * pool.length)];
    const audio = new Audio(source);
    audio.volume = Math.max(0, Math.min(0.42, volume));
    audio.playbackRate = Math.max(0.72, Math.min(1.28, rate));
    audio.preload = 'auto';
    void audio.play().catch(() => {});
  }
}
