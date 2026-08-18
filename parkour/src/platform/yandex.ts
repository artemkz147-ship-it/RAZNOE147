type YaSdk = any;

declare global {
  interface Window {
    YaGames?: { init: () => Promise<YaSdk> };
  }
}

export type Medal = 'bronze' | 'silver' | 'gold';

export type ParkourProgress = {
  unlockedLevel: number;
  bestScores: Record<string, number>;
  bestCombos: Record<string, number>;
  bestTimes: Record<string, number>;
  bestMedals: Record<string, Medal>;
  cleanLevels: number[];
  completedLevels: number[];
  tokens: number;
  totalFalls: number;
};

const MAX_LEVEL = 18;
const MEDALS = new Set<Medal>(['bronze', 'silver', 'gold']);

const freshProgress = (): ParkourProgress => ({
  unlockedLevel: 1,
  bestScores: {},
  bestCombos: {},
  bestTimes: {},
  bestMedals: {},
  cleanLevels: [],
  completedLevels: [],
  tokens: 0,
  totalFalls: 0
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

  async loadProgress(): Promise<ParkourProgress> {
    const local = this.loadLocal();
    try {
      if (this.player) {
        const data = await this.player.getData(['parkour3d']);
        const cloud = data.parkour3d as Partial<ParkourProgress> | undefined;
        const merged = this.normalize({ ...local, ...cloud });
        this.saveLocal(merged);
        return merged;
      }
    } catch {}
    return local;
  }

  async saveProgress(progress: ParkourProgress) {
    const normalized = this.normalize(progress);
    this.saveLocal(normalized);
    try { await this.player?.setData({ parkour3d: normalized }, true); } catch {}
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

  private loadLocal(): ParkourProgress {
    try {
      const raw = localStorage.getItem('vertical.parkour3d');
      if (raw) return this.normalize(JSON.parse(raw) as Partial<ParkourProgress>);
    } catch {}
    return freshProgress();
  }

  private saveLocal(progress: ParkourProgress) {
    try { localStorage.setItem('vertical.parkour3d', JSON.stringify(progress)); } catch {}
  }

  private normalize(input: Partial<ParkourProgress>): ParkourProgress {
    const base = freshProgress();
    const bestMedals: Record<string, Medal> = {};
    if (input.bestMedals && typeof input.bestMedals === 'object') {
      for (const [key, value] of Object.entries(input.bestMedals)) {
        if (MEDALS.has(value as Medal)) bestMedals[key] = value as Medal;
      }
    }
    const numberRecord = (value: unknown) => {
      const result: Record<string, number> = {};
      if (!value || typeof value !== 'object') return result;
      for (const [key, raw] of Object.entries(value as Record<string, unknown>)) {
        const number = Number(raw);
        if (Number.isFinite(number) && number >= 0) result[key] = number;
      }
      return result;
    };
    return {
      unlockedLevel: Math.max(1, Math.min(MAX_LEVEL, Math.floor(Number(input.unlockedLevel || base.unlockedLevel)))),
      bestScores: numberRecord(input.bestScores),
      bestCombos: numberRecord(input.bestCombos),
      bestTimes: numberRecord(input.bestTimes),
      bestMedals,
      cleanLevels: Array.isArray(input.cleanLevels)
        ? [...new Set(input.cleanLevels.map(Number).filter((value) => value >= 1 && value <= MAX_LEVEL))]
        : [],
      completedLevels: Array.isArray(input.completedLevels)
        ? [...new Set(input.completedLevels.map(Number).filter((value) => value >= 1 && value <= MAX_LEVEL))]
        : [],
      tokens: Math.max(0, Math.floor(Number(input.tokens || 0))),
      totalFalls: Math.max(0, Math.floor(Number(input.totalFalls || 0)))
    };
  }

  private bindPauseEvents() {
    if (!this.ysdk?.on) return;
    this.ysdk.on('game_api_pause', () => window.dispatchEvent(new CustomEvent('platform-pause')));
    this.ysdk.on('game_api_resume', () => window.dispatchEvent(new CustomEvent('platform-resume')));
  }
}

export const yandex = new YandexBridge();
