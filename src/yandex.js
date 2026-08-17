export class YandexBridge {
  constructor() {
    this.ysdk = null;
    this.player = null;
    this.lang = (navigator.language || 'en').slice(0, 2).toLowerCase();
    this.pausedByPlatform = false;
    this.onPause = null;
    this.onResume = null;
  }

  async init() {
    if (!window.YaGames) return null;
    try {
      this.ysdk = await window.YaGames.init();
      this.lang = this.ysdk?.environment?.i18n?.lang || this.lang;
      try { this.player = await this.ysdk.getPlayer({ scopes: false }); } catch (_) {}
      this.ysdk.on?.('game_api_pause', () => {
        this.pausedByPlatform = true;
        this.stopGameplay();
        this.onPause?.();
      });
      this.ysdk.on?.('game_api_resume', () => {
        this.pausedByPlatform = false;
        this.onResume?.();
      });
      return this.ysdk;
    } catch (err) {
      console.warn('Yandex SDK init failed, local mode enabled', err);
      return null;
    }
  }

  ready() {
    try { this.ysdk?.features?.LoadingAPI?.ready(); } catch (err) { console.warn(err); }
  }
  startGameplay() {
    try { this.ysdk?.features?.GameplayAPI?.start(); } catch (_) {}
  }
  stopGameplay() {
    try { this.ysdk?.features?.GameplayAPI?.stop(); } catch (_) {}
  }

  async showFullscreen() {
    if (!this.ysdk?.adv?.showFullscreenAdv) return false;
    this.stopGameplay();
    return new Promise(resolve => {
      this.ysdk.adv.showFullscreenAdv({ callbacks: {
        onOpen: () => this.onPause?.(),
        onClose: wasShown => { this.onResume?.(); resolve(Boolean(wasShown)); },
        onError: () => { this.onResume?.(); resolve(false); }
      }});
    });
  }

  async showRewarded() {
    if (!this.ysdk?.adv?.showRewardedVideo) return true;
    this.stopGameplay();
    let rewarded = false;
    return new Promise(resolve => {
      this.ysdk.adv.showRewardedVideo({ callbacks: {
        onOpen: () => this.onPause?.(),
        onRewarded: () => { rewarded = true; },
        onClose: () => { this.onResume?.(); resolve(rewarded); },
        onError: () => { this.onResume?.(); resolve(false); }
      }});
    });
  }

  async loadProgress() {
    const local = this.loadLocal();
    if (!this.player) return local;
    try {
      const data = await this.player.getData(['shadowvale']);
      return { ...local, ...(data?.shadowvale || {}) };
    } catch (_) { return local; }
  }

  async saveProgress(progress) {
    this.saveLocal(progress);
    if (!this.player) return;
    try { await this.player.setData({ shadowvale: progress }, true); } catch (_) {}
  }

  loadLocal() {
    try { return JSON.parse(localStorage.getItem('shadowvale-progress') || '{}'); } catch (_) { return {}; }
  }
  saveLocal(progress) {
    try { localStorage.setItem('shadowvale-progress', JSON.stringify(progress)); } catch (_) {}
  }
}
