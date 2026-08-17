export class YandexBridge {
  constructor() {
    this.ysdk = null;
    this.player = null;
    this.lang = (navigator.language || 'en').slice(0,2).toLowerCase();
    this.deviceType = 'desktop';
    this.pausedByPlatform = false;
    this.onPause = null;
    this.onResume = null;
    this.onAdOpen = null;
    this.onAdClose = null;
    this.reviewChecked = false;
  }

  async init() {
    if (!window.YaGames) return null;
    try {
      this.ysdk = await window.YaGames.init();
      this.lang = this.ysdk?.environment?.i18n?.lang || this.lang;
      this.deviceType = this.ysdk?.deviceInfo?.type || this.deviceType;
      try { this.player = await this.ysdk.getPlayer({ scopes:false }); } catch (_) {}
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

  ready(){ try{this.ysdk?.features?.LoadingAPI?.ready();}catch(err){console.warn(err);} }
  startGameplay(){ try{this.ysdk?.features?.GameplayAPI?.start();}catch(_){} }
  stopGameplay(){ try{this.ysdk?.features?.GameplayAPI?.stop();}catch(_){} }

  async showFullscreen() {
    if (!this.ysdk?.adv?.showFullscreenAdv) return false;
    this.stopGameplay(); this.onAdOpen?.();
    return new Promise(resolve => {
      let closed=false;
      const finish=v=>{if(closed)return;closed=true;this.onAdClose?.();resolve(Boolean(v));};
      this.ysdk.adv.showFullscreenAdv({ callbacks:{
        onOpen:()=>{},
        onClose:wasShown=>finish(wasShown),
        onError:()=>finish(false)
      }});
    });
  }

  async showRewarded() {
    if (!this.ysdk?.adv?.showRewardedVideo) return true;
    this.stopGameplay(); this.onAdOpen?.(); let rewarded=false;
    return new Promise(resolve => {
      let closed=false;
      const finish=()=>{if(closed)return;closed=true;this.onAdClose?.();resolve(rewarded);};
      this.ysdk.adv.showRewardedVideo({ callbacks:{
        onOpen:()=>{}, onRewarded:()=>{rewarded=true;}, onClose:finish, onError:finish
      }});
    });
  }

  async requestFullscreen() {
    try {
      const fs=this.ysdk?.screen?.fullscreen;
      if(fs){const req=fs.request;if(typeof req==='function')return await req.call(fs);if(req&&typeof req.then==='function')return await req;}
      if(document.documentElement.requestFullscreen)return await document.documentElement.requestFullscreen();
    } catch(err){console.warn('Fullscreen request failed',err);}
  }

  async canReview() {
    if(!this.ysdk?.feedback?.canReview)return false;
    try{const r=await this.ysdk.feedback.canReview();this.reviewChecked=Boolean(r?.value);return Boolean(r?.value);}catch(_){return false;}
  }
  async requestReview() {
    if(!this.ysdk?.feedback?.requestReview)return false;
    try{if(!this.reviewChecked&&!(await this.canReview()))return false;const r=await this.ysdk.feedback.requestReview();this.reviewChecked=false;return Boolean(r?.feedbackSent ?? r?.sentFeedback);}catch(_){return false;}
  }

  async loadProgress() {
    const local=this.loadLocal(); if(!this.player)return local;
    try{const data=await this.player.getData(['shadowvale']);return {...local,...(data?.shadowvale||{})};}catch(_){return local;}
  }

  async saveProgress(progress) {
    this.saveLocal(progress); if(!this.player)return;
    try{await this.player.setData({shadowvale:progress},true);}catch(_){}
  }

  loadLocal(){try{return JSON.parse(localStorage.getItem('shadowvale-progress')||'{}');}catch(_){return {};}}
  saveLocal(progress){try{localStorage.setItem('shadowvale-progress',JSON.stringify(progress));}catch(_){}}
}
