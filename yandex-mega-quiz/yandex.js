(() => {
  class YandexBridge {
    constructor(){this.ysdk=null;this.player=null;this.ready=false;this.local=true;this.listeners=[];this._paused=false;}
    async init(){
      try{
        if(window.YaGames?.init){
          this.ysdk=await window.YaGames.init();
          this.local=false;
          try{this.player=await this.ysdk.getPlayer();}catch(e){console.warn('Player init failed',e)}
          this.bindEvents();
        }
      }catch(e){console.warn('Yandex SDK unavailable, local mode enabled',e);this.local=true;}
      this.ready=true;return this;
    }
    bindEvents(){
      if(!this.ysdk?.on)return;
      this.ysdk.on('game_api_pause',()=>{this._paused=true;this.emit('pause');});
      this.ysdk.on('game_api_resume',()=>{this._paused=false;this.emit('resume');});
    }
    on(name,fn){this.listeners.push([name,fn]);}
    emit(name,payload){this.listeners.filter(x=>x[0]===name).forEach(x=>{try{x[1](payload)}catch(e){console.error(e)}})}
    gameReady(){try{this.ysdk?.features?.LoadingAPI?.ready?.();}catch(e){console.warn(e)}}
    gameplayStart(){try{this.ysdk?.features?.GameplayAPI?.start?.();}catch(e){console.warn(e)}}
    gameplayStop(){try{this.ysdk?.features?.GameplayAPI?.stop?.();}catch(e){console.warn(e)}}
    serverTime(){try{return this.ysdk?.serverTime?.() ?? Date.now();}catch{return Date.now()}}
    isAuthorized(){try{return !!this.player?.isAuthorized?.()}catch{return false}}
    async authorize(){
      if(this.local)return false;
      try{
        if(this.isAuthorized())return true;
        await this.ysdk.auth.openAuthDialog();
        this.player=await this.ysdk.getPlayer();
        return this.isAuthorized();
      }catch(e){console.warn('Auth failed',e);return false}
    }
    async loadSave(){
      if(!this.player)return null;
      try{const data=await this.player.getData(['quizSave']);return data?.quizSave||null}catch(e){console.warn('Cloud load failed',e);return null}
    }
    async save(data){
      if(!this.player)return false;
      try{await this.player.setData({quizSave:data},true);return true}catch(e){console.warn('Cloud save failed',e);return false}
    }
    async setScore(score){
      if(!this.ysdk?.leaderboards || !this.isAuthorized())return false;
      try{
        const available=await this.ysdk.isAvailableMethod?.('leaderboards.setScore');
        if(available===false)return false;
        await this.ysdk.leaderboards.setScore('global_score',Math.max(0,Math.floor(score)));
        return true;
      }catch(e){console.warn('Leaderboard score failed',e);return false}
    }
    async getLeaderboard(){
      if(!this.ysdk?.leaderboards)return null;
      try{
        const res=await this.ysdk.leaderboards.getEntries('global_score',{quantityTop:10,includeUser:this.isAuthorized(),quantityAround:2});
        return res;
      }catch(e){console.warn('Leaderboard read failed',e);return null}
    }
    async fullscreenAd(){
      if(!this.ysdk?.adv?.showFullscreenAdv)return false;
      return new Promise(resolve=>{
        this.gameplayStop();
        this.ysdk.adv.showFullscreenAdv({
          callbacks:{
            onOpen:()=>this.emit('adopen'),
            onClose:(shown)=>{this.emit('adclose',shown);resolve(!!shown)},
            onError:(e)=>{console.warn('Fullscreen ad',e);resolve(false)}
          }
        });
      });
    }
    async rewardedAd(){
      if(!this.ysdk?.adv?.showRewardedVideo)return false;
      return new Promise(resolve=>{
        let rewarded=false;this.gameplayStop();
        this.ysdk.adv.showRewardedVideo({callbacks:{
          onOpen:()=>this.emit('adopen'),
          onRewarded:()=>{rewarded=true},
          onClose:()=>{this.emit('adclose',rewarded);resolve(rewarded)},
          onError:(e)=>{console.warn('Rewarded ad',e);resolve(false)}
        }});
      });
    }
  }
  window.YandexBridge=new YandexBridge();
})();
