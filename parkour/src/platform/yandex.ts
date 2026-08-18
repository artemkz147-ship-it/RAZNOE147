type YaSdk = any;

declare global {
  interface Window {
    YaGames?: { init: () => Promise<YaSdk> };
  }
}

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

  async loadBest(): Promise<number> {
    try {
      if (this.player) {
        const data = await this.player.getData(['bestDistance']);
        return Number(data.bestDistance || 0);
      }
    } catch {}
    return Number(localStorage.getItem('rooftopFlow.bestDistance') || 0);
  }

  async saveBest(value: number) {
    const rounded = Math.max(0, Math.floor(value));
    localStorage.setItem('rooftopFlow.bestDistance', String(rounded));
    try { await this.player?.setData({ bestDistance: rounded }, true); } catch {}
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

  private bindPauseEvents() {
    if (!this.ysdk?.on) return;
    this.ysdk.on('game_api_pause', () => window.dispatchEvent(new CustomEvent('platform-pause')));
    this.ysdk.on('game_api_resume', () => window.dispatchEvent(new CustomEvent('platform-resume')));
  }
}

export const yandex = new YandexBridge();
