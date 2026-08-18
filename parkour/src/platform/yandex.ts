type YaSdk = any;

declare global {
  interface Window {
    YaGames?: { init: () => Promise<YaSdk> };
  }
}

export type DailyProgress = {
  date: string;
  distance: number;
  tokens: number;
  tricks: number;
  claimed: boolean;
};

export type PlayerProfile = {
  bestDistance: number;
  coins: number;
  lifetimeDistance: number;
  runs: number;
  daily: DailyProgress;
};

const todayKey = () => {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
};

const freshDaily = (): DailyProgress => ({
  date: todayKey(),
  distance: 0,
  tokens: 0,
  tricks: 0,
  claimed: false
});

const freshProfile = (): PlayerProfile => ({
  bestDistance: 0,
  coins: 0,
  lifetimeDistance: 0,
  runs: 0,
  daily: freshDaily()
});

class YandexBridge {
  private ysdk: YaSdk | null = null;
  private player: any = null;
  private gameplayActive = false;

  async init() {
    if (!window.YaGames) return;
    try {
      this.ysdk = await window.YaGames.init();
      try { this.player = await this.ysdk.getPlayer({ scopes: false }); } catch { this.player = null; }
      this.bindPauseEvents();
    } catch (error) {
      console.warn('Yandex SDK init failed, local mode enabled', error);
    }
  }

  ready() {
    this.ysdk?.features?.LoadingAPI?.ready();
  }

  gameplayStart() {
    if (this.gameplayActive) return;
    this.gameplayActive = true;
    this.ysdk?.features?.GameplayAPI?.start();
  }

  gameplayStop() {
    if (!this.gameplayActive) return;
    this.gameplayActive = false;
    this.ysdk?.features?.GameplayAPI?.stop();
  }

  async loadProfile(): Promise<PlayerProfile> {
    const fallback = this.loadLocalProfile();
    try {
      if (this.player) {
        const data = await this.player.getData(['profileV2', 'bestDistance']);
        const cloud = data.profileV2 as Partial<PlayerProfile> | undefined;
        const merged = this.normalizeProfile({
          ...fallback,
          ...cloud,
          bestDistance: Math.max(Number(data.bestDistance || 0), Number(cloud?.bestDistance || 0), fallback.bestDistance)
        });
        this.saveLocalProfile(merged);
        return merged;
      }
    } catch {}
    return fallback;
  }

  async saveProfile(profile: PlayerProfile) {
    const normalized = this.normalizeProfile(profile);
    this.saveLocalProfile(normalized);
    try {
      await this.player?.setData({
        bestDistance: normalized.bestDistance,
        profileV2: normalized
      }, true);
    } catch {}
  }

  async loadBest(): Promise<number> {
    return (await this.loadProfile()).bestDistance;
  }

  async saveBest(value: number) {
    const profile = await this.loadProfile();
    profile.bestDistance = Math.max(profile.bestDistance, Math.max(0, Math.floor(value)));
    await this.saveProfile(profile);
  }

  showInterstitial(): Promise<void> {
    if (!this.ysdk?.adv) return Promise.resolve();
    this.gameplayStop();
    return new Promise((resolve) => {
      this.ysdk.adv.showFullscreenAdv({
        callbacks: {
          onClose: () => resolve(),
          onError: () => resolve(),
          onOffline: () => resolve()
        }
      });
    });
  }

  showRewarded(): Promise<boolean> {
    if (!this.ysdk?.adv) return Promise.resolve(false);
    this.gameplayStop();
    return new Promise((resolve) => {
      let rewarded = false;
      this.ysdk.adv.showRewardedVideo({
        callbacks: {
          onRewarded: () => { rewarded = true; },
          onClose: () => resolve(rewarded),
          onError: () => resolve(false)
        }
      });
    });
  }

  private loadLocalProfile(): PlayerProfile {
    try {
      const raw = localStorage.getItem('rooftopFlow.profileV2');
      if (raw) return this.normalizeProfile(JSON.parse(raw) as Partial<PlayerProfile>);
    } catch {}

    const legacyBest = Number(localStorage.getItem('rooftopFlow.bestDistance') || 0);
    return this.normalizeProfile({ bestDistance: legacyBest });
  }

  private saveLocalProfile(profile: PlayerProfile) {
    try {
      localStorage.setItem('rooftopFlow.profileV2', JSON.stringify(profile));
      localStorage.setItem('rooftopFlow.bestDistance', String(profile.bestDistance));
    } catch {}
  }

  private normalizeProfile(input: Partial<PlayerProfile>): PlayerProfile {
    const base = freshProfile();
    const dailyInput = input.daily;
    const daily = dailyInput?.date === todayKey()
      ? {
          date: todayKey(),
          distance: Math.max(0, Math.floor(Number(dailyInput.distance || 0))),
          tokens: Math.max(0, Math.floor(Number(dailyInput.tokens || 0))),
          tricks: Math.max(0, Math.floor(Number(dailyInput.tricks || 0))),
          claimed: Boolean(dailyInput.claimed)
        }
      : freshDaily();

    return {
      bestDistance: Math.max(0, Math.floor(Number(input.bestDistance || base.bestDistance))),
      coins: Math.max(0, Math.floor(Number(input.coins || base.coins))),
      lifetimeDistance: Math.max(0, Math.floor(Number(input.lifetimeDistance || base.lifetimeDistance))),
      runs: Math.max(0, Math.floor(Number(input.runs || base.runs))),
      daily
    };
  }

  private bindPauseEvents() {
    if (!this.ysdk?.on) return;
    this.ysdk.on('game_api_pause', () => window.dispatchEvent(new CustomEvent('platform-pause')));
    this.ysdk.on('game_api_resume', () => window.dispatchEvent(new CustomEvent('platform-resume')));
  }
}

export const yandex = new YandexBridge();
