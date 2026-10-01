const KEY='iz-gryazi-v-knyazi-ads-v1',MINUTE=60000;
export class GameAds {
 constructor({bridge=null,storage=null,now=Date.now,onGrant=()=>({ok:false}),onChange=()=>{},onMessage=()=>{}}={}){
  this.bridge=bridge;this.storage=storage;this.now=now;this.onGrant=onGrant;this.onChange=onChange;this.onMessage=onMessage;this.started=now();this.actions=0;this.request=null;this.seq=0;this.lastAttempt=-Infinity;
  let saved={};try{saved=JSON.parse(storage?.getItem(KEY)||'{}');}catch{}
  this.credits=Number.isInteger(saved.credits)&&saved.credits>=0?Math.min(10,saved.credits):0;
  this.lastAd=Number.isFinite(saved.lastAd)?saved.lastAd:-Infinity;this.quietUntil=Number.isFinite(saved.quietUntil)?saved.quietUntil:0;
  this.shows=Array.isArray(saved.shows)?saved.shows.filter(n=>Number.isFinite(n)&&n>now()-60*MINUTE):[];
 }
 get busy(){return !!this.request;}
 persist(){try{this.storage?.setItem(KEY,JSON.stringify({credits:this.credits,lastAd:Number.isFinite(this.lastAd)?this.lastAd:null,quietUntil:this.quietUntil,shows:this.shows}));}catch{}}
 notify(){this.onChange(this.request);}
 useCredit(context){const result=this.onGrant(context);if(result?.ok){this.credits--;this.persist();this.notify();return true;}return false;}
 requestMonth(context){
  if(this.busy)return false;
  if(this.credits>0)return this.useCredit(context);
  if(!this.bridge?.showRewarded){this.onMessage('Реклама для перемотки доступна в Android-версии.');return false;}
  const id='rewarded-'+this.now()+'-'+(++this.seq);this.request={id,type:'rewarded',status:'loading',context,rewarded:false};this.notify();
  try{this.bridge.showRewarded(id);}catch{this.handle({type:'rewarded',requestId:id,status:'failed'});}return true;
 }
 cancel(){const r=this.request;if(!r||r.status!=='loading')return false;this.request=null;try{this.bridge?.cancelAd?.(r.id);}catch{}this.notify();return true;}
 noteAction(){this.actions++;}
 tryInterstitial(safe){
  const now=this.now();this.shows=this.shows.filter(t=>t>now-60*MINUTE);
  if(!safe||this.busy||!this.bridge?.showInterstitial||this.actions<3||now-this.started<3*MINUTE||now-this.lastAd<3*MINUTE||now<this.quietUntil||this.shows.length>=4||now-this.lastAttempt<MINUTE)return false;
  this.lastAttempt=now;const id='interstitial-'+now+'-'+(++this.seq);this.request={id,type:'interstitial',status:'loading',rewarded:false};this.notify();
  try{this.bridge.showInterstitial(id);}catch{this.handle({type:'interstitial',requestId:id,status:'skipped'});}return true;
 }
 handle(event){
  const r=this.request;if(!r||event.requestId!==r.id||event.type!==r.type)return;
  if(event.status==='shown'){
   r.status='shown';this.lastAd=this.now();if(r.type==='rewarded')this.quietUntil=this.now()+5*MINUTE;
   else{this.shows.push(this.now());this.actions=0;}this.persist();this.notify();return;
  }
  if(event.status==='rewarded'&&r.type==='rewarded'&&!r.rewarded){r.rewarded=true;this.credits++;this.persist();return;}
  if(!['closed','failed','skipped'].includes(event.status))return;
  this.request=null;this.notify();
  if(r.type==='rewarded'){
   if(event.status==='closed'&&r.rewarded)this.useCredit(r.context);
   else this.onMessage(event.status==='closed'?'Просмотр не завершён. Перемотка не началась.':'Реклама сейчас недоступна. Попробуй позже.');
  }
 }
}
