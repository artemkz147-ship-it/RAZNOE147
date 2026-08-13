(() => {
'use strict';

const DATA = window.UMK3_DATA;
if (!DATA) throw new Error('UMK3_DATA missing');
const canvas = document.getElementById('game');
const ctx = canvas.getContext('2d', {alpha:false, desynchronized:true});
const touchUI = document.getElementById('touch-ui');
const pauseBtn = document.getElementById('pause');
const W=1280,H=720,FLOOR=594,TAU=Math.PI*2,FPS=60;
const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
const lerp=(a,b,t)=>a+(b-a)*t;
const rand=(a,b)=>a+Math.random()*(b-a);
const choose=a=>a[(Math.random()*a.length)|0];
const easeOut=t=>1-Math.pow(1-clamp(t,0,1),3);
const nowSec=()=>performance.now()/1000;

const SETTINGS_KEY='umk3hd.settings.v2';
const defaultSettings={difficulty:2,assistSpecial:false,blood:true,shake:true,haptics:true,unlockAll:true,volume:.8};
let settings=defaultSettings;
try{settings={...defaultSettings,...JSON.parse(localStorage.getItem(SETTINGS_KEY)||'{}')}}catch(_){}
const saveSettings=()=>{try{localStorage.setItem(SETTINGS_KEY,JSON.stringify(settings))}catch(_){}};

function rr(x,y,w,h,r,fill,stroke,line=1){
  ctx.beginPath(); if(ctx.roundRect)ctx.roundRect(x,y,w,h,r); else ctx.rect(x,y,w,h);
  if(fill){ctx.fillStyle=fill;ctx.fill()} if(stroke){ctx.lineWidth=line;ctx.strokeStyle=stroke;ctx.stroke()}
}
function txt(s,x,y,size,align='center',fill='#fff',font='Impact'){
  ctx.save();ctx.font=`${size}px ${font},Arial,sans-serif`;ctx.textAlign=align;ctx.textBaseline='middle';
  ctx.fillStyle=fill;ctx.shadowColor='rgba(0,0,0,.85)';ctx.shadowBlur=Math.max(2,size*.12);ctx.shadowOffsetY=2;
  ctx.fillText(String(s),x,y);ctx.restore();
}
function line(x1,y1,x2,y2,w,c,alpha=1){
  ctx.save();ctx.globalAlpha=alpha;ctx.strokeStyle=c;ctx.lineWidth=w;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(x1,y1);ctx.lineTo(x2,y2);ctx.stroke();ctx.restore();
}
function poly(points,fill,stroke=null,lw=1){
  ctx.beginPath();points.forEach((p,i)=>i?ctx.lineTo(p[0],p[1]):ctx.moveTo(p[0],p[1]));ctx.closePath();
  if(fill){ctx.fillStyle=fill;ctx.fill()} if(stroke){ctx.lineWidth=lw;ctx.strokeStyle=stroke;ctx.stroke()}
}
function shade(hex,amt){
  const n=parseInt(hex.slice(1),16),r=clamp((n>>16)+amt,0,255),g=clamp(((n>>8)&255)+amt,0,255),b=clamp((n&255)+amt,0,255);
  return '#'+((1<<24)+(r<<16)+(g<<8)+b).toString(16).slice(1);
}
function vibrate(ms=18){if(settings.haptics&&navigator.vibrate)try{navigator.vibrate(ms)}catch(_){}}

// ---------- input ----------
const KEY={
 KeyA:'left',ArrowLeft:'left',KeyD:'right',ArrowRight:'right',KeyW:'up',ArrowUp:'up',KeyS:'down',ArrowDown:'down',
 KeyJ:'LP',KeyU:'HP',KeyK:'LK',KeyI:'HK',KeyL:'BL',KeyO:'RUN',KeyP:'SPECIAL',
 Enter:'confirm',Space:'confirm',Escape:'pause',F1:'help'
};
const input={left:false,right:false,up:false,down:false,LP:false,HP:false,LK:false,HK:false,BL:false,RUN:false,SPECIAL:false,confirm:false,pause:false};
const pressed=Object.create(null);
const inputEvents=[];
function pushInput(k){
  inputEvents.push({k,t:game.clock});
  while(inputEvents.length>40)inputEvents.shift();
}
function setKey(k,v){
  if(v&&!input[k]){pressed[k]=true;pushInput(k)}
  input[k]=v;
}
window.addEventListener('keydown',e=>{
  const k=KEY[e.code];if(!k)return;e.preventDefault();setKey(k,true);
});
window.addEventListener('keyup',e=>{
  const k=KEY[e.code];if(!k)return;e.preventDefault();input[k]=false;
});
for(const b of document.querySelectorAll('.touch')){
  const k=b.dataset.key;
  const dn=e=>{e.preventDefault();setKey(k,true);b.classList.add('pressed')};
  const up=e=>{e.preventDefault();input[k]=false;b.classList.remove('pressed')};
  b.addEventListener('pointerdown',dn);b.addEventListener('pointerup',up);b.addEventListener('pointercancel',up);b.addEventListener('pointerleave',up);
}
canvas.addEventListener('pointerdown',e=>{e.preventDefault(); if(game.state==='title'){pressed.confirm=true;pushInput('confirm')}});
if(pauseBtn)pauseBtn.addEventListener('pointerdown',e=>{e.preventDefault();pressed.pause=true});

function dirToken(raw,face){
  if(raw==='up')return 'U'; if(raw==='down')return 'D';
  if(raw==='left')return face<0?'F':'B';
  if(raw==='right')return face>0?'F':'B';
  return raw;
}
function eventToken(ev,face){
  return dirToken(ev.k,face);
}
function chordSatisfied(parts,face,at){
  for(const p of parts){
    if(['F','B','U','D'].includes(p)){
      const raw=p==='U'?'up':p==='D'?'down':(p==='F'?(face>0?'right':'left'):(face>0?'left':'right'));
      const recent=inputEvents.some(e=>e.k===raw&&at-e.t<.18);
      if(!input[raw]&&!recent)return false;
    }else{
      const recent=inputEvents.some(e=>e.k===p&&at-e.t<.18);
      if(!input[p]&&!recent)return false;
    }
  }
  return true;
}
function commandMatches(cmd, fighter, maxAge=1.15){
  if(!cmd||!cmd.length)return false;
  const evs=inputEvents.filter(e=>game.clock-e.t<=maxAge);
  let j=evs.length-1;
  for(let i=cmd.length-1;i>=0;i--){
    const want=cmd[i];
    if(want.includes('+')){
      const parts=want.split('+');
      let found=false;
      for(;j>=0;j--){
        if(chordSatisfied(parts,fighter.face,evs[j].t)){found=true;j--;break}
      }
      if(!found)return false;
      continue;
    }
    let found=false;
    for(;j>=0;j--){
      let got=eventToken(evs[j],fighter.face);
      if(want==='DF'||want==='DB'){
        const h=want[1];
        if(got===h){
          const prev=evs[j-1]&&eventToken(evs[j-1],fighter.face);
          if(prev==='D'&&evs[j].t-evs[j-1].t<.25){found=true;j-=2;break}
        }
        if(got==='D'){
          const prev=evs[j-1]&&eventToken(evs[j-1],fighter.face);
          if(prev===h&&evs[j].t-evs[j-1].t<.25){found=true;j-=2;break}
        }
      }
      if(got===want){found=true;j--;break}
    }
    if(!found)return false;
  }
  return true;
}
function clearPressed(){
  for(const k of Object.keys(pressed))delete pressed[k];
}

// ---------- audio ----------
let ac=null,master=null;
function audio(){
  try{
    ac ||= new (window.AudioContext||window.webkitAudioContext)();
    if(!master){master=ac.createGain();master.gain.value=settings.volume;master.connect(ac.destination)}
    if(ac.state==='suspended')ac.resume();
    return ac;
  }catch(_){return null}
}
function tone(type='hit',power=1){
  const a=audio();if(!a)return; const t=a.currentTime;
  const o=a.createOscillator(),g=a.createGain(),f=a.createBiquadFilter();
  const cfg={
    menu:[260,.055,'triangle'],hit:[95,.075,'square'],heavy:[62,.12,'sawtooth'],block:[420,.045,'square'],
    special:[155,.18,'sawtooth'],freeze:[680,.22,'sine'],ko:[52,.65,'sawtooth'],fatal:[38,.95,'square'],jump:[210,.05,'triangle']
  }[type]||[110,.08,'square'];
  o.type=cfg[2];o.frequency.setValueAtTime(cfg[0],t);o.frequency.exponentialRampToValueAtTime(Math.max(25,cfg[0]*.42),t+cfg[1]);
  f.type='lowpass';f.frequency.value=1800;
  g.gain.setValueAtTime(.0001,t);g.gain.exponentialRampToValueAtTime(.13*power,t+.005);g.gain.exponentialRampToValueAtTime(.0001,t+cfg[1]);
  o.connect(f);f.connect(g);g.connect(master);o.start(t);o.stop(t+cfg[1]+.03);
}
function noise(duration=.08,power=.09){
  const a=audio();if(!a)return;const len=Math.max(1,a.sampleRate*duration|0),buf=a.createBuffer(1,len,a.sampleRate),d=buf.getChannelData(0);
  for(let i=0;i<len;i++)d[i]=(Math.random()*2-1)*(1-i/len);
  const s=a.createBufferSource(),g=a.createGain();s.buffer=buf;g.gain.value=power;s.connect(g);g.connect(master);s.start();
}

// ---------- game state ----------
const game={
 state:'title',clock:0,stateT:0,paused:false,help:false,select:0,selectFlash:0,tower:[],towerStep:0,stageIndex:0,
 round:1,p1wins:0,p2wins:0,timer:99,intro:0,outro:0,finishT:0,winner:null,finisher:null,
 player:null,enemy:null,particles:[],projectiles:[],hazards:[],afterimages:[],shake:0,flash:0,stageFlash:0,
 cameraX:0,cameraY:0,menuNote:'',endingT:0,score:0,streak:0
};
function setState(s){game.state=s;game.stateT=0;clearPressed();inputEvents.length=0}
function showControls(on){
  if(touchUI)touchUI.classList.toggle('show',!!on);
  if(pauseBtn)pauseBtn.classList.toggle('show',!!on);
}
function corePlayable(){
  return settings.unlockAll?DATA.allPlayable:DATA.coreRoster.filter(f=>!f.secret);
}
function buildTower(playerId){
  const pool=DATA.coreRoster.filter(f=>f.id!==playerId&&!f.secret);
  const shuffled=[...pool].sort(()=>Math.random()-.5);
  return shuffled.slice(0,7).concat(DATA.bosses);
}
function startFight(){
  const pData=corePlayable()[game.select]||DATA.coreRoster[0];
  const oData=game.tower[game.towerStep]||choose(DATA.coreRoster.filter(f=>f.id!==pData.id));
  game.stageIndex=(game.towerStep<game.tower.length-2?(game.towerStep*3+game.select)%DATA.stages.length:(oData.id==='motaro'?13:15));
  game.player=new Fighter(pData,330,1,false);
  game.enemy=new Fighter(oData,950,-1,true);
  game.round=1;game.p1wins=0;game.p2wins=0;game.timer=99;game.intro=2.5;game.finishT=0;game.winner=null;game.finisher=null;
  game.projectiles.length=0;game.particles.length=0;game.hazards.length=0;game.afterimages.length=0;
  setState('fight');showControls(true);tone('menu',.6);
}
function resetRound(){
  game.player.reset(330,1);game.enemy.reset(950,-1);game.timer=99;game.intro=2.2;game.finishT=0;game.winner=null;game.finisher=null;
  game.projectiles.length=0;game.hazards.length=0;inputEvents.length=0;
}
function roundWon(winner){
  if(game.winner)return;
  game.winner=winner;
  winner===game.player?game.p1wins++:game.p2wins++;
  tone('ko',.9);game.shake=.25;
  if(game.p1wins>=2||game.p2wins>=2){
    game.finishT=6.5;
    const loser=winner===game.player?game.enemy:game.player;
    loser.state='dizzy';loser.stun=99;loser.health=0;
  }else game.outro=2.1;
}
function finishMatch(){
  showControls(false);
  if(game.p1wins>game.p2wins){
    game.score+=10000+Math.max(0,game.timer|0)*100;game.streak++;
    game.towerStep++;
    if(game.towerStep>=game.tower.length){game.endingT=0;setState('ending')}
    else {setState('tower');game.stateT=-.5}
  }else{
    game.streak=0;setState('gameover');
  }
}

// ---------- fighter ----------
const BASIC={
 LP:{name:'Low Punch',startup:.055,active:.07,recovery:.17,damage:4.4,reach:74,knock:105,hitstun:.16},
 HP:{name:'High Punch',startup:.075,active:.085,recovery:.22,damage:6.2,reach:84,knock:145,hitstun:.2},
 LK:{name:'Low Kick',startup:.08,active:.09,recovery:.21,damage:5.5,reach:92,knock:145,hitstun:.19},
 HK:{name:'High Kick',startup:.11,active:.105,recovery:.28,damage:8.2,reach:112,knock:245,hitstun:.26},
 uppercut:{name:'Uppercut',startup:.13,active:.10,recovery:.42,damage:13.5,reach:88,knock:280,vy:-620,hitstun:.45},
 sweep:{name:'Sweep',startup:.12,active:.11,recovery:.38,damage:9.5,reach:118,knock:330,hitstun:.35,low:true},
 jumpPunch:{name:'Jump Punch',startup:.05,active:.17,recovery:.12,damage:7,reach:85,knock:130,hitstun:.2},
 jumpKick:{name:'Jump Kick',startup:.06,active:.18,recovery:.14,damage:9,reach:104,knock:250,hitstun:.28}
};
class Fighter{
  constructor(data,x,face,cpu=false){
    this.data=data;this.cpu=cpu;this.roundWins=0;this.reset(x,face);
  }
  reset(x,face){
    Object.assign(this,{x,y:FLOOR,vx:0,vy:0,face,health:100,state:'idle',stateT:0,air:false,crouch:false,blocking:false,
      attack:null,attackT:0,attackDone:false,queue:null,stun:0,blockstun:0,frozen:0,dead:false,invisible:0,reflect:0,
      runMeter:100,comboHits:0,comboDamage:0,comboT:0,chain:[],chainT:0,specialCD:0,aiT:0,ai:{},flash:0,morph:null,
      grabT:0,finishPose:0,armor:0});
    if(this.data.boss)this.health=this.data.id==='shao-kahn'?135:120;
  }
  get activeData(){return this.morph||this.data}
  get height(){if(this.data.style==='motaro')return 198;if(this.data.style==='shaokahn')return 214;return this.crouch?112:184}
  get hurtbox(){return{x:this.x-(this.data.style==='motaro'?58:37),y:this.y-this.height,w:this.data.style==='motaro'?116:74,h:this.height}}
  update(dt,other){
    this.stateT+=dt;this.flash=Math.max(0,this.flash-dt*8);this.specialCD=Math.max(0,this.specialCD-dt);this.invisible=Math.max(0,this.invisible-dt);
    this.reflect=Math.max(0,this.reflect-dt);this.comboT-=dt;if(this.comboT<=0){this.comboHits=0;this.comboDamage=0}
    this.chainT-=dt;if(this.chainT<=0)this.chain.length=0;
    if(this.dead)return this.physics(dt);
    if(this.frozen>0){this.frozen-=dt;this.state='frozen';return this.physics(dt)}
    if(this.stun>0){this.stun-=dt;if(this.state!=='dizzy')this.state='hit';return this.physics(dt)}
    if(this.blockstun>0){this.blockstun-=dt;this.state='block';return this.physics(dt)}
    if(game.intro>0||game.finishT>0&&this!==game.winner){this.vx*=.8;return this.physics(dt)}
    if(this.cpu)this.cpuThink(dt,other);
    const c=this.cpu?this.ai:input;
    this.blocking=!!c.BL&&!this.air&&!this.attack;
    this.crouch=!!c.down&&!this.air&&!this.attack;
    if(this.attack)this.attackUpdate(dt,other);
    else{
      if(c.up&&!this.air){this.vy=-760;this.air=true;this.state='jump';tone('jump',.25)}
      const dir=(c.left?-1:0)+(c.right?1:0);
      let speed=c.RUN&&this.runMeter>0?405:245;
      if(c.RUN&&dir){this.runMeter=Math.max(0,this.runMeter-dt*35)} else this.runMeter=Math.min(100,this.runMeter+dt*20);
      if(this.blocking){this.state='block';this.vx*=.64}
      else if(dir&&!this.crouch){this.vx=dir*speed;this.state=this.air?'jump':(c.RUN?'run':'walk')}
      else{this.vx*=this.air?.992:.68;if(!this.air)this.state=this.crouch?'crouch':'idle'}
      if(!this.cpu)this.handlePlayerButtons(other); else this.handleCPUButtons(other);
    }
    this.physics(dt);
    if(Math.abs(other.x-this.x)>6&&!this.attack)this.face=Math.sign(other.x-this.x)||this.face;
  }
  handlePlayerButtons(other){
    if(game.finishT>0&&game.winner===this){
      for(const f of this.activeData.fatalities||[]){
        if(commandMatches(f.cmd,this,1.7)){executeFatality(this,other,f);inputEvents.length=0;return}
      }
      if(DATA.stages[game.stageIndex].fatal&&commandMatches(['F','F','U','U','LP'],this,1.7)){
        executeStageFatality(this,other,DATA.stages[game.stageIndex]);inputEvents.length=0;return;
      }
    }
    const moves=[...(this.activeData.moves||[])].sort((a,b)=>b.cmd.length-a.cmd.length);
    for(const m of moves){
      const last=m.cmd[m.cmd.length-1];
      const trigger=last.split('+').some(k=>pressed[k]||(['F','B','D','U','DF','DB'].includes(k)&&Object.keys(pressed).some(x=>['left','right','up','down'].includes(x))));
      if(trigger&&commandMatches(m.cmd,this)){this.startSpecial(m,other);inputEvents.length=0;return}
    }
    if(settings.assistSpecial&&pressed.SPECIAL){
      const m=this.activeData.moves.find(m=>m.damage>0||m.type==='projectile')||this.activeData.moves[0];if(m)this.startSpecial(m,other);pressed.SPECIAL=false;return;
    }
    if(pressed.HP){
      if(this.crouch)this.startBasic('uppercut',other);else if(this.air)this.startBasic('jumpPunch',other);else this.startBasic('HP',other);pressed.HP=false;
    }else if(pressed.LP){
      if(!this.air&&Math.abs(other.x-this.x)<58&&!other.air)this.startGrab(other);else if(this.air)this.startBasic('jumpPunch',other);else this.startBasic('LP',other);pressed.LP=false;
    }else if(pressed.HK){
      if(this.crouch)this.startBasic('sweep',other);else if(this.air)this.startBasic('jumpKick',other);else this.startBasic('HK',other);pressed.HK=false;
    }else if(pressed.LK){
      if(this.air)this.startBasic('jumpKick',other);else this.startBasic('LK',other);pressed.LK=false;
    }
  }
  handleCPUButtons(other){
    const a=this.ai;
    if(a.special){a.special=false;const list=this.activeData.moves.filter(m=>m.type!=='invisible'&&m.type!=='flight');if(list.length)this.startSpecial(choose(list),other);return}
    if(a.HP){a.HP=false;this.startBasic(this.crouch?'uppercut':'HP',other);return}
    if(a.LP){a.LP=false;this.startBasic('LP',other);return}
    if(a.HK){a.HK=false;this.startBasic(this.crouch?'sweep':'HK',other);return}
    if(a.LK){a.LK=false;this.startBasic('LK',other);return}
  }
  startBasic(kind,other){
    if(this.attack||this.stun>0)return;
    const def={...BASIC[kind]};this.attack={kind:'basic',id:kind,def};this.attackT=0;this.attackDone=false;this.state='attack';
    const tok=(this.crouch?'D+':'')+(kind==='uppercut'?'HP':kind==='sweep'?'HK':kind.replace('jump','').replace('Punch','HP').replace('Kick','HK'));
    this.chain.push(tok);this.chainT=.9;if(this.chain.length>8)this.chain.shift();
    this.checkCombo();
  }
  startGrab(other){
    this.attack={kind:'grab',id:'throw',def:{startup:.07,active:.05,recovery:.46,damage:11,reach:62,knock:380,hitstun:.45}};this.attackT=0;this.attackDone=false;this.state='grab';
  }
  checkCombo(){
    for(const c of this.activeData.combos||[]){
      const seq=c.seq.filter(x=>x!=='SPECIAL'); if(seq.length>this.chain.length)continue;
      const tail=this.chain.slice(-seq.length);
      if(seq.every((x,i)=>x===tail[i])){this.comboT=1.1;this.runMeter=Math.min(100,this.runMeter+8);burst(this.x,this.y-150,'#ffd86a',6,1.4)}
    }
  }
  startSpecial(move,other){
    if(this.specialCD>0||this.attack||this.stun>0)return;
    this.attack={kind:'special',id:move.type,move,def:{startup:.11,active:.16,recovery:.38,damage:move.damage||0,reach:120,knock:280,hitstun:.3}};
    this.attackT=0;this.attackDone=false;this.state='special';this.specialCD=.16;tone(move.type.includes('freeze')?'freeze':'special',.7);
    if(move.type==='invisible'){this.invisible=this.invisible>0?0:4;this.attackDone=true}
    if(move.type==='reflect'){this.reflect=move.duration||2.2;this.attackDone=true}
    if(move.type==='flight'){if(!this.air){this.air=true;this.vy=-420}this.attackDone=true}
    if(move.type==='morph'){const p=DATA.coreRoster.filter(f=>!f.boss&&f.id!==this.data.id);this.morph=choose(p);this.attackDone=true;burst(this.x,this.y-100,'#55ef77',30,1.7)}
  }
  attackUpdate(dt,other){
    this.attackT+=dt;const a=this.attack,d=a.def;
    if(!this.attackDone&&this.attackT>=d.startup){
      this.attackDone=true;
      if(a.kind==='basic'||a.kind==='grab'){this.tryHit(other,d,a.id)}
      else this.performSpecial(a.move,other);
    }
    if(a.kind==='basic'&&this.attackT>d.startup+d.active*.5){
      const any=pressed.HP||pressed.LP||pressed.HK||pressed.LK;
      if(any&&this.attackT>d.startup+.04){this.attack=null;this.state=this.air?'jump':'idle';this.handlePlayerButtons(other);return}
    }
    const total=d.startup+d.active+d.recovery;
    if(this.attackT>=total){this.attack=null;this.attackT=0;this.state=this.air?'jump':'idle'}
  }
  tryHit(other,d,id){
    const yOk=Math.abs((this.y-this.height*.48)-(other.y-other.height*.48))<120;
    if(Math.abs(other.x-this.x)<=d.reach&&yOk){
      hit(this,other,d.damage,d.knock||150,d.vy||0,d.hitstun||.2,id,!!d.low)
    }
  }
  performSpecial(m,other){
    const type=m.type;
    if(type==='projectile'||type==='projectileBurst'){
      const count=type==='projectileBurst'?(m.count||2):1;
      for(let i=0;i<count;i++)spawnProjectile(this,m.projectile||'energy',m.damage||8,{delay:i*.13,tracking:m.tracking,freeze:m.freeze,stun:m.stun});
    }else if(type==='spear'){
      spawnProjectile(this,'spear',m.damage||5,{speed:650,spear:true,stun:m.stun||1.1});
    }else if(type==='dash'||type==='roll'||type==='slide'){
      this.vx=this.face*(m.speed||760);this.state=type==='slide'?'slide':'special';
      if(Math.abs(other.x-this.x)<180)hit(this,other,m.damage||11,type==='slide'?260:360,0,.34,type,type==='slide');
    }else if(type==='teleport'||type==='teleportKick'){
      const old=this.x;this.x=clamp(other.x-other.face*88,72,W-72);this.face=Math.sign(other.x-this.x)||1;
      burst(old,this.y-100,this.activeData.primary,22,1.6);burst(this.x,this.y-100,this.activeData.primary,22,1.6);
      if(type==='teleportKick'||m.damage)if(Math.abs(other.x-this.x)<125)hit(this,other,m.damage||11,310,-160,.36,type);
    }else if(type==='teleportExplosion'){
      const old=this.x;this.x=clamp(other.x-other.face*120,72,W-72);burst(old,this.y-90,'#ffb128',28,2);burst(this.x,this.y-90,'#ff7b18',32,2);
      if(Math.abs(other.x-this.x)<160)hit(this,other,m.damage||12,350,-220,.42,type);
    }else if(type==='uppercutSpecial'||type==='diveKick'||type==='multiKick'||type==='lowStrike'||type==='hammer'){
      if(type==='diveKick')this.vx=this.face*630;
      if(Math.abs(other.x-this.x)<145)hit(this,other,m.damage||12,type==='uppercutSpecial'?300:260,type==='uppercutSpecial'?-610:0,.38,type);
      if(type==='multiKick'&&Math.abs(other.x-this.x)<145)for(let i=0;i<4;i++)setTimeout(()=>burst(other.x,other.y-100,'#ffd36a',4,1),i*45);
    }else if(type==='grab'||type==='grabMulti'||type==='teleportGrab'){
      if(type==='teleportGrab')this.x=clamp(other.x-this.face*45,70,W-70);
      if(Math.abs(other.x-this.x)<95)hit(this,other,m.damage||14,420,-220,.55,type);
    }else if(type==='ground'||type==='groundFire'||type==='groundBlade'||type==='bomb'||type==='groundFreeze'){
      const ox=type==='bomb'?this.x+this.face*(m.offset||180):other.x;
      game.hazards.push({type,x:ox,y:FLOOR,t:0,life:type==='groundFreeze'?1.4:.8,owner:this,damage:m.damage||10});
      game.shake=Math.max(game.shake,.22);
    }else if(type==='stompTeleport'){
      this.y=FLOOR-330;this.x=clamp(other.x,80,W-80);this.vy=780;this.air=true;
      setTimeout(()=>{if(Math.abs(other.x-this.x)<120)hit(this,other,m.damage||13,230,0,.45,type)},170);
    }else if(type==='lift'||type==='telekinesis'||type==='stunWave'||type==='spinStun'){
      if(Math.abs(other.x-this.x)<(m.range||300)){other.stun=m.stun||1.25;other.vy=type==='lift'||type==='telekinesis'?-500:0;other.air=other.vy<0;burst(other.x,other.y-90,this.activeData.primary,25,1.8)}
    }else if(type==='iceShower'){
      game.hazards.push({type:'iceShower',x:other.x+(m.offset||0),y:FLOOR,t:0,life:.8,owner:this,damage:0});
    }else if(type==='clone'){
      game.hazards.push({type:'clone',x:this.x-this.face*35,y:FLOOR,t:0,life:3,owner:this,damage:0});
    }else if(type==='lightning'){
      other.stun=.45;hit(this,other,m.damage||11,120,0,.45,type);burst(other.x,other.y-110,'#d9f6ff',42,2.2);
    }else if(type==='spin'){
      this.state='special';if(Math.abs(other.x-this.x)<160)hit(this,other,m.damage||14,300,-200,.42,type);
    }
  }
  physics(dt){
    if(this.air)this.vy+=1820*dt;
    this.x+=this.vx*dt;this.y+=this.vy*dt;
    if(this.y>=FLOOR){this.y=FLOOR;this.vy=0;if(this.air){this.air=false;if(!this.attack&&this.state==='jump')this.state='idle'}}
    this.x=clamp(this.x,55,W-55);
  }
  cpuThink(dt,other){
    this.aiT-=dt;if(this.aiT>0)return;
    const lvl=settings.difficulty,dist=Math.abs(other.x-this.x),r=Math.random();
    this.ai={left:false,right:false,up:false,down:false,LP:false,HP:false,LK:false,HK:false,BL:false,RUN:false,special:false};
    this.aiT=rand(.07,.18)-lvl*.012;
    if(game.finishT>0)return;
    const toward=other.x>this.x?'right':'left',away=toward==='right'?'left':'right';
    if(other.attack&&dist<150&&r<.32+lvl*.1){this.ai.BL=true;return}
    if(dist>360){this.ai[toward]=true;this.ai.RUN=r<.55;if(r<.18+lvl*.05)this.ai.special=true;else if(r>.9)this.ai.up=true;return}
    if(dist>170){this.ai[toward]=true;if(r<.24)this.ai.special=true;else if(r>.82)this.ai.up=true;return}
    if(r<.16)this.ai.BL=true;
    else if(r<.34)this.ai.HP=true;
    else if(r<.52)this.ai.HK=true;
    else if(r<.67)this.ai.LP=true;
    else if(r<.80)this.ai.LK=true;
    else if(r<.93)this.ai.special=true;
    else this.ai[away]=true;
  }
}

function hit(attacker,defender,damage,knock=160,vy=0,stun=.22,kind='hit',low=false){
  if(defender.dead||game.finishT>0)return false;
  const facingAttack=(attacker.x-defender.x)*defender.face>0;
  const blocked=defender.blocking&&facingAttack&&(!low||defender.crouch);
  if(blocked){
    damage*=.14;defender.blockstun=Math.max(defender.blockstun,stun*.7);defender.vx=attacker.face*knock*.28;tone('block',.55);burst(defender.x,defender.y-110,'#d8e6ff',8,1.2);
  }else{
    defender.health=Math.max(0,defender.health-damage);defender.stun=Math.max(defender.stun,stun);defender.vx=attacker.face*knock;if(vy){defender.vy=vy;defender.air=true}
    defender.flash=1;attacker.comboHits++;attacker.comboDamage+=damage;attacker.comboT=.9;game.shake=Math.max(game.shake,settings.shake?(damage>12?.22:.11):0);
    tone(damage>10?'heavy':'hit',clamp(damage/12,.4,1));noise(.045,.04);vibrate(damage>10?28:14);
    burst(defender.x-attacker.face*15,defender.y-rand(80,145),settings.blood?'#b90f17':'#f0d25f',settings.blood?12:7,damage>10?2:1.2);
  }
  if(defender.health<=0){defender.health=0;roundWon(attacker)}
  return true;
}

// ---------- effects ----------
function burst(x,y,color,count=12,power=1){
  for(let i=0;i<count;i++)game.particles.push({x,y,vx:rand(-180,180)*power,vy:rand(-280,40)*power,g:rand(600,1100),life:rand(.28,.9),t:0,r:rand(2,7)*power,c:color,trail:Math.random()<.35});
}
function spawnProjectile(owner,type,damage,opt={}){
  const speed=opt.speed||(type==='bullet'?1050:type==='forceSlow'?390:type==='forceFast'?800:650);
  const p={owner,type,damage,x:owner.x+owner.face*65,y:owner.y-(type.includes('Low')||type==='fireballLow'?62:118),vx:owner.face*speed,vy:0,t:-opt.delay||0,
    life:2.2,r:type==='missile'||type==='heatMissile'?15:12,tracking:opt.tracking,freeze:opt.freeze,stun:opt.stun,spear:opt.spear,returning:type==='boomerangReturn'};
  if(type==='grenadeHigh'){p.vy=-430;p.vx=owner.face*520} if(type==='grenadeLow'){p.vy=-250;p.vx=owner.face*430}
  game.projectiles.push(p);
}
function updateEffects(dt){
  for(let i=game.particles.length-1;i>=0;i--){const p=game.particles[i];p.t+=dt;p.vy+=p.g*dt;p.x+=p.vx*dt;p.y+=p.vy*dt;p.vx*=.985;if(p.t>=p.life)game.particles.splice(i,1)}
  for(let i=game.projectiles.length-1;i>=0;i--){
    const p=game.projectiles[i];p.t+=dt;if(p.t<0)continue;
    const target=p.owner===game.player?game.enemy:game.player;
    if(p.tracking)p.vy+=clamp((target.y-110-p.y)*2,-260,260)*dt;
    if(p.type.startsWith('grenade'))p.vy+=820*dt;
    p.x+=p.vx*dt;p.y+=p.vy*dt;
    if(target.reflect>0&&Math.abs(target.x-p.x)<70){p.owner=target;p.vx=-p.vx;p.damage*=1.15;burst(p.x,p.y,'#d7f7ff',12,1.2);continue}
    if(Math.abs(target.x-p.x)<55&&Math.abs((target.y-105)-p.y)<90){
      if(p.spear){hit(p.owner,target,p.damage,40,0,.18,'spear');target.stun=p.stun||1.1;target.vx=0;setTimeout(()=>{if(target.stun>0)target.x=lerp(target.x,p.owner.x+p.owner.face*95,.8)},80)}
      else if(p.freeze||p.type==='freeze'){target.frozen=p.freeze||1.8;target.vx=0;tone('freeze',.8);burst(target.x,target.y-105,'#7edbff',26,1.3)}
      else if(p.type==='net'){target.stun=p.stun||1.5;target.vx=0;burst(target.x,target.y-105,'#ffd23b',22,1.1)}
      else hit(p.owner,target,p.damage,230,p.type.includes('grenade')?-180:0,.28,p.type);
      burst(p.x,p.y,projectileColor(p.type),18,1.4);game.projectiles.splice(i,1);continue;
    }
    if(p.returning&&p.t>.85){p.vx=Math.sign(p.owner.x-p.x)*Math.abs(p.vx)}
    if(p.t>p.life||p.x<-100||p.x>W+100||p.y>H+100){game.projectiles.splice(i,1)}
  }
  for(let i=game.hazards.length-1;i>=0;i--){
    const h=game.hazards[i];h.t+=dt;const target=h.owner===game.player?game.enemy:game.player;
    if(h.type==='clone'&&Math.abs(target.x-h.x)<55&&target!==h.owner){target.frozen=1.8;burst(target.x,target.y-100,'#8edfff',24,1.4);h.life=0}
    if((h.type==='groundFreeze'||h.type==='iceShower')&&h.t>.15&&h.t<.55&&Math.abs(target.x-h.x)<90){target.frozen=1.55;h.life=0;burst(target.x,target.y-80,'#83dfff',24,1.4)}
    if(['ground','groundFire','groundBlade','bomb'].includes(h.type)&&h.t>.12&&h.t<.28&&Math.abs(target.x-h.x)<100){hit(h.owner,target,h.damage||10,250,-180,.36,h.type);h.life=0}
    if(h.t>=h.life)game.hazards.splice(i,1);
  }
}
function projectileColor(t){
  if(t.includes('freeze'))return '#85ddff';if(t.includes('acid'))return '#64e444';if(t.includes('purple'))return '#c66cff';if(t.includes('soul'))return '#53f36f';
  if(t.includes('fire')||t.includes('skull')||t.includes('heat'))return '#ff7d25';if(t==='net')return '#f3cd32';if(t.includes('water'))return '#59c8ff';
  return '#f2f2f2';
}

// ---------- finishers ----------
function executeFatality(winner,loser,move){
  if(game.finisher)return;game.finisher={type:'fatality',move,t:0,winner,loser};game.finishT=0;loser.stun=99;winner.attack=null;tone('fatal',1);game.flash=.5;game.shake=.5;vibrate(65);
}
function executeStageFatality(winner,loser,stage){
  if(game.finisher)return;game.finisher={type:'stage',stage,t:0,winner,loser};game.finishT=0;loser.stun=99;tone('fatal',1);game.shake=.55;vibrate(70);
}
function updateFinisher(dt){
  const f=game.finisher;if(!f)return;
  f.t+=dt;const l=f.loser,w=f.winner;
  if(f.type==='stage'){
    if(f.t<.5){l.vx=w.face*280}
    else if(f.t<1.5){l.y+=220*dt;l.x+=w.face*110*dt}
    if(f.t>.65&&f.t<.8)burst(l.x,l.y-60,'#b51218',60,2.4);
    if(f.t>2.8){l.dead=true;game.outro=1.5;game.finisher=null}
    return;
  }
  const kind=f.move.fatal||'burst';
  if(f.t>.35&&f.t<.39){game.flash=.8;game.shake=.5}
  if(f.t>.55&&f.t<.6){
    const c=settings.blood?'#b10e17':'#e2c958';
    if(['burn','laser','lightning','beam','electric','scream'].includes(kind))burst(l.x,l.y-110,kind==='lightning'?'#dff8ff':'#ff8d3c',55,2);
    else burst(l.x,l.y-110,c,75,2.6);
  }
  if(kind==='freezeBreak'||kind==='freezeShatter'){if(f.t<1.1)l.frozen=2;if(f.t>1.15){l.invisible=99}}
  if(kind==='burn'&&f.t>.6){game.stageFlash=.4}
  if(kind==='decap'||kind==='slice'){if(f.t>.72)l.heightOverride=115}
  if(kind==='soul'&&f.t>.65)game.particles.push({x:l.x,y:l.y-120,vx:0,vy:-90,g:-20,life:1.8,t:0,r:18,c:'#56ff79'});
  if(f.t>3.0){l.dead=true;l.invisible=99;game.outro=1.3;game.finisher=null}
}

// ---------- stage art ----------
function drawStage(stage,t){
  const g=ctx.createLinearGradient(0,0,0,H);g.addColorStop(0,stage.sky[0]);g.addColorStop(.72,stage.sky[1]);g.addColorStop(1,'#050505');ctx.fillStyle=g;ctx.fillRect(0,0,W,H);
  ctx.save();ctx.globalAlpha=.12;for(let i=0;i<7;i++){ctx.fillStyle=stage.accent;ctx.beginPath();ctx.ellipse((i*211+t*9)%1500-100,180+Math.sin(t*.4+i)*70,180,45,0,0,TAU);ctx.fill()}ctx.restore();
  switch(stage.kind){
    case'subway':stageSubway(stage,t);break;case'street':stageStreet(stage,t);break;case'roof':stageRoof(stage,t);break;case'bank':stageBank(stage,t);break;
    case'soul':stageSoul(stage,t);break;case'tower':stageTower(stage,t);break;case'temple':stageTemple(stage,t);break;case'grave':stageGrave(stage,t);break;
    case'water':stageWater(stage,t);break;case'portal':stagePortal(stage,t);break;case'desert':stageDesert(stage,t);break;case'cave':stageCave(stage,t);break;
    case'hell':stageHell(stage,t);break;case'balcony':stageBalcony(stage,t);break;case'noob':stageNoob(stage,t);break;case'pit':stagePit(stage,t);break;
  }
  const fg=ctx.createLinearGradient(0,FLOOR-5,0,H);fg.addColorStop(0,shade(stage.sky[1],18));fg.addColorStop(.18,'#161719');fg.addColorStop(1,'#030304');ctx.fillStyle=fg;ctx.fillRect(0,FLOOR,W,H-FLOOR);
  ctx.fillStyle='rgba(255,255,255,.08)';ctx.fillRect(0,FLOOR,W,2);
  for(let x=0;x<W;x+=90){line(x,FLOOR,x+35,H,1,'rgba(255,255,255,.045)')}
}
function stageSubway(s,t){
  ctx.fillStyle='#121b20';ctx.fillRect(0,285,W,250);
  for(let x=-20;x<W;x+=170){ctx.fillStyle='#22343b';ctx.fillRect(x,305,140,170);ctx.fillStyle='#0a0d0f';ctx.fillRect(x+12,320,116,118);ctx.fillStyle='rgba(80,180,190,.18)';ctx.fillRect(x+15,323,110,4)}
  ctx.fillStyle='#0a0a0b';ctx.fillRect(0,495,W,38);for(let x=0;x<W;x+=46){ctx.fillStyle='#4c4438';ctx.fillRect(x,510,28,6)}
  const tx=(t*180)%1700-300;ctx.fillStyle='#6e1d1d';ctx.fillRect(tx,335,360,120);for(let i=0;i<4;i++){ctx.fillStyle='#e1c070';ctx.fillRect(tx+25+i*82,355,58,45)}
}
function stageStreet(s,t){
  for(let i=0;i<8;i++){const x=i*175-40,h=170+(i%3)*65;ctx.fillStyle=i%2?'#17171d':'#202027';ctx.fillRect(x,430-h,150,h);for(let yy=280;yy<420;yy+=30)for(let xx=x+14;xx<x+135;xx+=32){ctx.fillStyle=Math.random()<.02?'#b64935':'rgba(219,176,83,.10)';ctx.fillRect(xx,yy,12,9)}}
  ctx.fillStyle='rgba(150,180,220,.12)';for(let i=0;i<26;i++)ctx.fillRect((i*91+t*70)%W,rand(80,520),2,16);
}
function stageRoof(s,t){stageStreet(s,t);ctx.fillStyle='#19171d';ctx.fillRect(0,455,W,95);for(let x=70;x<W;x+=230){ctx.fillStyle='#403b45';ctx.fillRect(x,390,65,75);ctx.fillStyle='#101014';ctx.fillRect(x+10,400,45,50)}}
function stageBank(s,t){ctx.fillStyle='#252628';ctx.fillRect(0,280,W,250);for(let x=50;x<W;x+=210){ctx.fillStyle='#b59b68';ctx.fillRect(x,300,26,210);ctx.fillRect(x-18,300,62,14);ctx.fillRect(x-18,495,62,14)}ctx.fillStyle='#111';ctx.fillRect(410,330,460,165);ctx.strokeStyle='#8d7b55';ctx.lineWidth=8;ctx.strokeRect(420,340,440,145)}
function stageSoul(s,t){for(let x=40;x<W;x+=185){ctx.fillStyle='#183824';ctx.fillRect(x,250,100,280);ctx.strokeStyle='#4ed76a';ctx.lineWidth=3;ctx.strokeRect(x+14,275,72,210);ctx.save();ctx.globalAlpha=.35+.2*Math.sin(t*2+x);ctx.fillStyle='#54ee75';ctx.beginPath();ctx.ellipse(x+50,385,25,75,0,0,TAU);ctx.fill();ctx.restore()}for(let i=0;i<18;i++){const y=420-((t*40+i*43)%300);ctx.fillStyle='rgba(91,255,118,.18)';ctx.beginPath();ctx.arc((i*83)%W,y,4+(i%4),0,TAU);ctx.fill()}}
function stageTower(s,t){for(let x=60;x<W;x+=170){ctx.fillStyle='#392719';ctx.fillRect(x,230,34,300);ctx.fillStyle='#a77a3c';ctx.fillRect(x-15,230,64,18)}ctx.fillStyle='#6b4b26';ctx.beginPath();ctx.arc(640,205,128,0,TAU);ctx.fill();ctx.fillStyle='#1d160e';ctx.beginPath();ctx.arc(640,205,92,0,TAU);ctx.fill();line(640,205,640+Math.sin(t)*70,205-Math.cos(t)*70,7,'#d3ad67')}
function stageTemple(s,t){for(let x=0;x<W;x+=128){ctx.fillStyle=x%256?'#2b1611':'#361b14';ctx.fillRect(x,245,128,290);ctx.fillStyle='#8a3d27';ctx.fillRect(x,245,6,290)}for(let x=90;x<W;x+=220){ctx.fillStyle='#e4442c';ctx.beginPath();ctx.arc(x,325,22,0,TAU);ctx.fill();ctx.fillStyle='#ffb130';ctx.beginPath();ctx.arc(x,325,9+Math.sin(t*8+x)*3,0,TAU);ctx.fill()}}
function stageGrave(s,t){for(let i=0;i<13;i++){const x=i*102+25,y=470+(i%3)*18;ctx.fillStyle='#384349';ctx.fillRect(x,y-70,42,70);ctx.beginPath();ctx.arc(x+21,y-70,21,Math.PI,0);ctx.fill()}for(let i=0;i<7;i++){ctx.strokeStyle='#182025';ctx.lineWidth=8;ctx.beginPath();ctx.moveTo(i*210+40,480);ctx.lineTo(i*210+100,280);ctx.lineTo(i*210+160,470);ctx.stroke()}}
function stageWater(s,t){ctx.fillStyle='#123d4a';ctx.fillRect(0,430,W,100);ctx.save();ctx.globalAlpha=.28;for(let y=440;y<525;y+=15)line(0,y,W,y,2,'#6cd4dc',.25+.15*Math.sin(t*2+y));ctx.restore();ctx.fillStyle='#11151b';poly([[830,420],[910,220],[980,420]],'#1a1922');ctx.fillStyle='#8e392e';ctx.fillRect(900,260,12,160)}
function stagePortal(s,t){ctx.fillStyle='#1b2138';ctx.fillRect(0,380,W,155);ctx.save();ctx.translate(640,315);ctx.rotate(t*.22);for(let i=7;i>0;i--){ctx.strokeStyle=`rgba(85,145,255,${.08+i*.045})`;ctx.lineWidth=10;ctx.beginPath();ctx.arc(0,0,30+i*22,0,TAU);ctx.stroke()}ctx.restore();for(let x=80;x<W;x+=160){ctx.fillStyle='#323849';ctx.fillRect(x,360,28,170);ctx.fillStyle='#6f7692';ctx.fillRect(x-12,360,52,10)}}
function stageDesert(s,t){ctx.fillStyle='#9f5b36';poly([[0,430],[180,285],[360,430]],'#6d3e28');poly([[260,430],[560,230],[820,430]],'#7b482b');poly([[700,430],[1050,260],[1280,430]],'#693a27');ctx.fillStyle='#c98a4a';ctx.fillRect(0,430,W,105);ctx.fillStyle='#5b554a';ctx.fillRect(930,390,38,70);ctx.fillStyle='#c5a024';ctx.fillRect(938,360,22,38)}
function stageCave(s,t){ctx.fillStyle='#2d0d0b';for(let x=0;x<W;x+=90){const h=80+(x%180);poly([[x,0],[x+50,h],[x+90,0]],'#26100e');poly([[x,530],[x+50,460-h*.2],[x+90,530]],'#3b1410')}for(let i=0;i<9;i++){ctx.fillStyle=`rgba(255,70,25,${.12+.08*Math.sin(t*3+i)})`;ctx.beginPath();ctx.arc(i*155,440,35,0,TAU);ctx.fill()}}
function stageHell(s,t){stageCave(s,t);ctx.fillStyle='#7d1f0b';ctx.fillRect(0,500,W,35);for(let x=0;x<W;x+=80){const h=15+25*Math.abs(Math.sin(t*4+x));poly([[x,500],[x+35,500-h],[x+70,500]],'#ff6a14')}}
function stageBalcony(s,t){ctx.fillStyle='#22202b';ctx.fillRect(0,250,W,280);for(let x=55;x<W;x+=190){ctx.fillStyle='#4a425b';ctx.fillRect(x,280,45,250);ctx.fillStyle='#b39bd8';ctx.fillRect(x-16,280,77,16)}ctx.fillStyle='#15131a';ctx.fillRect(0,460,W,70)}
function stageNoob(s,t){stageBalcony(s,t);ctx.fillStyle='rgba(0,0,0,.72)';ctx.fillRect(0,0,W,530)}
function stagePit(s,t){ctx.fillStyle='#281716';ctx.fillRect(0,300,W,230);for(let x=40;x<W;x+=140){ctx.fillStyle='#5a2925';ctx.fillRect(x,300,20,230);ctx.fillStyle='#8c3930';ctx.fillRect(x-18,300,56,10)}for(let x=0;x<W;x+=84){ctx.save();ctx.translate(x+30,555);ctx.rotate(t*3+x);ctx.fillStyle='#8c8c8e';for(let i=0;i<5;i++){ctx.rotate(TAU/5);poly([[0,-8],[55,0],[0,8]],'#8c8c8e')}ctx.restore()}}

// ---------- fighter art ----------
function drawFighter(f){
  if(!f||f.invisible>50)return;
  const alpha=f.invisible>0?.25:1;ctx.save();ctx.globalAlpha=alpha;ctx.translate(f.x,f.y);ctx.scale(f.face,1);
  if(f.flash>0){ctx.shadowColor='#fff';ctx.shadowBlur=26}
  const d=f.activeData,pose=getPose(f);
  drawShadow(f);
  if(d.style==='motaro')drawMotaro(f,d,pose);
  else if(d.style==='sheeva')drawHumanoid(f,d,pose,true);
  else drawHumanoid(f,d,pose,false);
  ctx.restore();
}
function drawShadow(f){ctx.save();ctx.scale(f.face,1);ctx.fillStyle='rgba(0,0,0,.46)';ctx.beginPath();ctx.ellipse(0,3,f.data.style==='motaro'?82:48,13,0,0,TAU);ctx.fill();ctx.restore()}
function getPose(f){
  const t=f.stateT;
  let hip=[0,-82],chest=[0,-142],head=[0,-181],la=[-34,-132],ra=[34,-132],lh=[-48,-80],rh=[48,-80],lf=[-25,-4],rf=[25,-4];
  if(f.state==='walk'||f.state==='run'){const s=Math.sin(t*(f.state==='run'?14:9));lh=[-48+s*18,-85];rh=[48-s*18,-85];lf=[-24+s*22,-4];rf=[24-s*22,-4];chest=[0,-142+Math.abs(s)*3]}
  if(f.air){const s=Math.sin(t*8);hip=[0,-94];chest=[0,-150];lf=[-42,-28+s*10];rf=[44,-36-s*10];lh=[-45,-105];rh=[48,-112]}
  if(f.crouch){hip=[0,-55];chest=[0,-100];head=[0,-136];lf=[-45,-4];rf=[43,-4];lh=[-42,-68];rh=[40,-72]}
  if(f.blocking){la=[-30,-142];ra=[18,-155];lh=[-15,-120];rh=[10,-124]}
  if(f.state==='hit'){chest=[-10,-139];head=[-17,-178];lh=[-55,-105];rh=[45,-88]}
  if(f.state==='frozen'){lh=[-45,-110];rh=[44,-110]}
  if(f.state==='dizzy'){const s=Math.sin(t*9)*10;head=[s,-181];lh=[-50,-96];rh=[48,-100]}
  if(f.attack){
    const p=clamp(f.attackT/(f.attack.def.startup+f.attack.def.active),0,1),q=Math.sin(p*Math.PI);
    if(['HP','LP','jumpPunch'].includes(f.attack.id)||f.attack.kind==='grab'){rh=[55+q*62,-130-q*10];ra=[30+q*26,-140]}
    if(['HK','LK','jumpKick'].includes(f.attack.id)){rf=[38+q*92,-32-q*65];}
    if(f.attack.id==='uppercut'){rh=[38+q*35,-90-q*100];ra=[20+q*28,-125-q*40]}
    if(f.attack.id==='sweep'){rf=[52+q*95,-8];hip=[0,-55];chest=[0,-105];head=[0,-142]}
    if(f.attack.kind==='special'){rh=[45+q*35,-135];lh=[-45-q*20,-120]}
  }
  return{hip,chest,head,la,ra,lh,rh,lf,rf};
}
function limb(a,b,w,c,hi){
  line(a[0],a[1],b[0],b[1],w+5,'rgba(0,0,0,.72)');line(a[0],a[1],b[0],b[1],w,c);line(a[0]-2,a[1]-2,b[0]-2,b[1]-2,Math.max(2,w*.18),hi,.5);
}
function drawHumanoid(f,d,p,fourArms){
  const primary=d.primary,dark=d.dark,skin=d.robot?'#5b6267':(d.style==='nightwolf'?'#a96e4e':'#b77b5a'),metal='#929aa0';
  const armor=d.robot||['jax','kano','kabal','stryker','shaokahn'].includes(d.style);
  const leg=shade(dark,12), hi=shade(primary,55);
  limb(p.hip,p.lf,28,leg,shade(leg,35));limb(p.hip,p.rf,30,leg,shade(leg,32));
  limb(p.chest,p.la,22,armor?shade(dark,30):skin,armor?metal:shade(skin,35));limb(p.la,p.lh,20,armor?shade(dark,20):skin,armor?metal:shade(skin,35));
  poly([[p.chest[0]-38,p.chest[1]-12],[p.chest[0]+38,p.chest[1]-12],[p.hip[0]+28,p.hip[1]+8],[p.hip[0]-28,p.hip[1]+8]],dark,'rgba(0,0,0,.85)',6);
  const torsoG=ctx.createLinearGradient(-35,-160,35,-75);torsoG.addColorStop(0,hi);torsoG.addColorStop(.45,primary);torsoG.addColorStop(1,shade(primary,-45));
  ctx.fillStyle=torsoG;poly([[-31,p.chest[1]-5],[31,p.chest[1]-5],[24,p.hip[1]],[ -24,p.hip[1]]],torsoG);
  drawCostumeDetails(f,d,p,skin,metal);
  limb(p.chest,p.ra,23,armor?shade(dark,34):skin,armor?metal:shade(skin,38));limb(p.ra,p.rh,21,armor?shade(dark,25):skin,armor?metal:shade(skin,38));
  if(fourArms){limb([p.chest[0]-24,p.chest[1]+15],[-58,-112],20,skin,shade(skin,40));limb([-58,-112],[-73,-72],18,skin,shade(skin,40));limb([p.chest[0]+24,p.chest[1]+15],[58,-112],20,skin,shade(skin,40));limb([58,-112],[73,-72],18,skin,shade(skin,40))}
  ctx.fillStyle=d.robot?shade(dark,32):skin;ctx.strokeStyle='#080808';ctx.lineWidth=5;ctx.beginPath();ctx.ellipse(p.head[0],p.head[1],25,29,0,0,TAU);ctx.fill();ctx.stroke();
  drawHead(f,d,p,skin,metal);
  for(const pt of [p.lh,p.rh]){ctx.fillStyle=d.robot?metal:shade(dark,22);ctx.beginPath();ctx.arc(pt[0],pt[1],11,0,TAU);ctx.fill()}
  for(const pt of [p.lf,p.rf]){rr(pt[0]-16,pt[1]-10,32,13,5,shade(dark,-5),'#050505',3)}
  if(f.state==='frozen'){ctx.fillStyle='rgba(90,205,255,.34)';ctx.strokeStyle='#a8edff';ctx.lineWidth=3;rr(-58,-220,116,220,18,'rgba(55,180,240,.20)','#a8edff',3)}
  if(f.invisible>0){ctx.globalAlpha*=.45}
}
function drawCostumeDetails(f,d,p,skin,metal){
  switch(d.style){
    case'ninja':case'femaleNinja':
      poly([[-27,-164],[27,-164],[18,-130],[-18,-130]],shade(d.dark,10),'#050505',3);
      ctx.fillStyle=shade(d.primary,25);ctx.fillRect(-19,-126,38,56);ctx.fillStyle=shade(d.dark,25);ctx.fillRect(-33,-116,12,39);ctx.fillRect(21,-116,12,39);
      if(d.style==='femaleNinja'){ctx.fillStyle=shade(d.dark,18);ctx.fillRect(-26,-86,52,12)}
      break;
    case'subzero':
      ctx.fillStyle=shade(d.primary,30);ctx.fillRect(-30,-148,60,60);ctx.fillStyle='#12161a';ctx.fillRect(-18,-126,36,46);ctx.strokeStyle='#8bdcff';ctx.lineWidth=2;ctx.strokeRect(-26,-144,52,52);break;
    case'cyborg':
      ctx.fillStyle=shade(d.primary,35);rr(-28,-150,56,58,8,shade(d.primary,20),'#050505',4);ctx.fillStyle='#171a1c';rr(-16,-135,32,20,4,'#171a1c');ctx.fillStyle='#ffd04b';ctx.fillRect(-4,-131,8,12);break;
    case'jax':
      ctx.fillStyle=skin;ctx.fillRect(-30,-151,60,54);for(const x of [-29,20]){ctx.fillStyle=metal;ctx.fillRect(x,-146,12,70);ctx.fillStyle='#d8dde0';ctx.fillRect(x+2,-140,3,58)}break;
    case'kano':
      ctx.fillStyle='#17181a';ctx.fillRect(-30,-151,60,62);ctx.strokeStyle='#aa3a35';ctx.lineWidth=7;ctx.beginPath();ctx.moveTo(-27,-150);ctx.lineTo(25,-90);ctx.stroke();break;
    case'nightwolf':
      ctx.fillStyle='#111';ctx.fillRect(-28,-151,56,60);ctx.strokeStyle=d.primary;ctx.lineWidth=5;ctx.strokeRect(-23,-146,46,52);break;
    case'sindel':
      ctx.fillStyle=d.primary;ctx.fillRect(-30,-150,60,63);ctx.fillStyle='#151219';ctx.fillRect(-11,-150,22,63);break;
    case'stryker':
      ctx.fillStyle='#4b596c';ctx.fillRect(-31,-151,62,61);ctx.fillStyle='#11161e';ctx.fillRect(-29,-117,58,19);break;
    case'kunglao':
      ctx.fillStyle='#18191c';ctx.fillRect(-27,-151,54,63);ctx.strokeStyle='#c9b27a';ctx.lineWidth=4;ctx.beginPath();ctx.moveTo(-18,-150);ctx.lineTo(-6,-88);ctx.moveTo(18,-150);ctx.lineTo(6,-88);ctx.stroke();break;
    case'kabal':
      ctx.fillStyle='#252426';ctx.fillRect(-31,-151,62,61);ctx.strokeStyle='#70747a';ctx.lineWidth=5;ctx.beginPath();ctx.arc(0,-120,42,0,Math.PI);ctx.stroke();break;
    case'shang':
      ctx.fillStyle='#171719';ctx.fillRect(-32,-151,64,62);for(let x=-24;x<25;x+=16){ctx.fillStyle=d.primary;ctx.fillRect(x,-148,8,58)}break;
    case'liukang':
      ctx.fillStyle=skin;ctx.fillRect(-29,-151,58,54);ctx.fillStyle='#161219';ctx.fillRect(-27,-100,54,18);break;
    case'shaokahn':
      ctx.fillStyle='#55282a';ctx.fillRect(-36,-160,72,70);ctx.fillStyle=metal;ctx.fillRect(-33,-154,66,14);break;
  }
}
function drawHead(f,d,p,skin,metal){
  const x=p.head[0],y=p.head[1];
  if(d.style==='ninja'||d.style==='femaleNinja'||d.style==='subzero'){
    ctx.fillStyle=d.dark;ctx.beginPath();ctx.arc(x,y-4,26,Math.PI,TAU);ctx.fill();ctx.fillStyle=shade(d.primary,-8);poly([[x-24,y-4],[x+24,y-4],[x+17,y+17],[x-17,y+17]],shade(d.primary,-8));
    ctx.fillStyle='#e6e6df';ctx.fillRect(x-13,y-8,9,3);ctx.fillRect(x+4,y-8,9,3);
  }else if(d.style==='cyborg'){
    ctx.fillStyle=shade(d.primary,5);rr(x-24,y-26,48,45,12,shade(d.primary,5),'#050505',4);ctx.fillStyle='#fff081';ctx.fillRect(x-14,y-9,10,4);ctx.fillRect(x+4,y-9,10,4);ctx.fillStyle='#111';ctx.fillRect(x-13,y+4,26,7);
  }else if(d.style==='kano'){
    ctx.fillStyle='#8b9297';ctx.beginPath();ctx.arc(x+10,y-3,16,-1.3,1.5);ctx.lineTo(x,y+12);ctx.fill();ctx.fillStyle='#ff2c2c';ctx.beginPath();ctx.arc(x+10,y-5,4,0,TAU);ctx.fill();
  }else if(d.style==='kunglao'){
    ctx.fillStyle='#171719';ctx.fillRect(x-39,y-29,78,7);ctx.fillRect(x-20,y-36,40,11);
  }else if(d.style==='kabal'){
    ctx.fillStyle='#25282b';rr(x-23,y-19,46,36,12,'#25282b','#050505',3);ctx.fillStyle='#e9d8b0';ctx.fillRect(x-13,y-6,8,4);ctx.fillRect(x+5,y-6,8,4);line(x-18,y+5,x-33,y+17,4,'#6f7478');line(x+18,y+5,x+34,y+18,4,'#6f7478');
  }else if(d.style==='sindel'){
    ctx.strokeStyle='#e1e0e4';ctx.lineWidth=13;ctx.beginPath();ctx.moveTo(x-12,y-25);ctx.bezierCurveTo(x-65,y-50,x-60,y+25,x-40,y+48);ctx.moveTo(x+12,y-25);ctx.bezierCurveTo(x+65,y-50,x+60,y+25,x+40,y+48);ctx.stroke();
  }else if(d.style==='stryker'){
    ctx.fillStyle='#292d34';ctx.fillRect(x-25,y-31,50,10);ctx.fillRect(x+4,y-36,30,6);
  }else if(d.style==='nightwolf'){
    ctx.fillStyle=d.primary;ctx.fillRect(x-26,y-24,52,7);line(x+18,y-25,x+45,y-52,7,'#d7c29c');line(x+22,y-20,x+55,y-38,6,'#b65c35');
  }else if(d.style==='shaokahn'){
    ctx.fillStyle='#b5b6b8';poly([[x-26,y-28],[x+26,y-28],[x+30,y+5],[x+15,y+21],[x-15,y+21],[x-30,y+5]],'#b5b6b8','#050505',4);poly([[x-24,y-25],[x-43,y-48],[x-15,y-36]],'#77797c');poly([[x+24,y-25],[x+43,y-48],[x+15,y-36]],'#77797c');ctx.fillStyle='#ffcf62';ctx.fillRect(x-14,y-5,10,4);ctx.fillRect(x+4,y-5,10,4);
  }else{
    ctx.fillStyle='#111';ctx.fillRect(x-16,y-29,32,7);
  }
}
function drawMotaro(f,d,p){
  ctx.fillStyle='#4e2718';ctx.strokeStyle='#070403';ctx.lineWidth=6;ctx.beginPath();ctx.ellipse(-28,-72,80,42,-.1,0,TAU);ctx.fill();ctx.stroke();
  limb([-55,-55],[-70,-5],22,'#5e321f','#9a6546');limb([20,-52],[38,-5],23,'#5e321f','#9a6546');
  ctx.fillStyle=d.primary;poly([[-22,-174],[32,-174],[34,-88],[-24,-88]],d.primary,'#050505',6);
  limb([-18,-156],[-55,-118],22,d.primary,shade(d.primary,40));limb([28,-156],[68,-112],22,d.primary,shade(d.primary,40));
  ctx.fillStyle='#8b5436';ctx.beginPath();ctx.arc(5,-202,28,0,TAU);ctx.fill();ctx.strokeStyle='#050505';ctx.lineWidth=5;ctx.stroke();
  ctx.fillStyle='#a0a1a2';rr(-12,-145,36,46,6,'#858789','#050505',3);
  ctx.strokeStyle='#6a3824';ctx.lineWidth=12;ctx.beginPath();ctx.moveTo(-95,-78);ctx.bezierCurveTo(-150,-110,-130,-160,-175,-175);ctx.stroke();ctx.fillStyle='#b6b6b6';poly([[-177,-189],[-162,-170],[-184,-164]],'#b6b6b6');
}

// ---------- projectiles + particles art ----------
function drawEffects(){
  for(const h of game.hazards){
    if(h.type==='clone'){ctx.save();ctx.globalAlpha=.5;ctx.fillStyle='#6fd8ff';rr(h.x-28,FLOOR-174,56,174,12,'rgba(80,205,255,.22)','#a8eeff',3);ctx.restore()}
    else if(h.type==='iceShower'||h.type==='groundFreeze'){ctx.fillStyle='rgba(80,210,255,.42)';ctx.beginPath();ctx.ellipse(h.x,FLOOR-8,70,14,0,0,TAU);ctx.fill()}
    else{ctx.fillStyle=h.type==='groundBlade'?'#b7b9bb':h.type==='groundFire'?'#ff5b22':'#ffb52a';ctx.beginPath();ctx.arc(h.x,FLOOR-12,25+Math.sin(h.t*14)*8,0,TAU);ctx.fill()}
  }
  for(const p of game.projectiles){
    if(p.t<0)continue;ctx.save();ctx.translate(p.x,p.y);ctx.fillStyle=projectileColor(p.type);ctx.shadowColor=ctx.fillStyle;ctx.shadowBlur=18;
    if(p.type==='spear'){line(-25,0,25,0,4,'#d8d4c8');poly([[24,0],[10,-8],[10,8]],'#b8b7b3')}
    else if(p.type.includes('missile')){rr(-20,-7,40,14,5,'#999da0','#181818',2);ctx.fillStyle='#ff7a20';ctx.fillRect(-29,-4,10,8)}
    else if(p.type==='fan'||p.type.includes('boomerang')){ctx.rotate(p.t*12);for(let i=0;i<4;i++){ctx.rotate(TAU/4);poly([[0,0],[32,-6],[28,7]],ctx.fillStyle)}}
    else if(p.type==='knife'||p.type==='sai'||p.type==='baton'||p.type==='hat'){ctx.rotate(p.t*10);ctx.fillRect(-28,-4,56,8)}
    else{ctx.beginPath();ctx.arc(0,0,p.r||12,0,TAU);ctx.fill();ctx.globalAlpha=.35;ctx.beginPath();ctx.arc(0,0,(p.r||12)*1.9,0,TAU);ctx.fill()}
    ctx.restore();
  }
  for(const p of game.particles){
    const a=1-p.t/p.life;ctx.save();ctx.globalAlpha=clamp(a,0,1);ctx.fillStyle=p.c;ctx.beginPath();ctx.arc(p.x,p.y,p.r*a+.5,0,TAU);ctx.fill();ctx.restore();
  }
}

// ---------- UI ----------
function drawHUD(){
  const p=game.player,e=game.enemy;if(!p||!e)return;
  txt(p.data.name,70,36,25,'left','#f6f0de');txt(e.data.name,W-70,36,25,'right','#f6f0de');
  healthBar(55,57,500,28,p.health/(p.data.boss?(p.data.id==='shao-kahn'?135:120):100),false,p.data.primary);
  healthBar(W-555,57,500,28,e.health/(e.data.boss?(e.data.id==='shao-kahn'?135:120):100),true,e.data.primary);
  for(let i=0;i<2;i++){ctx.fillStyle=i<game.p1wins?'#d93027':'#3b3b3d';ctx.beginPath();ctx.arc(72+i*24,98,7,0,TAU);ctx.fill();ctx.fillStyle=i<game.p2wins?'#d93027':'#3b3b3d';ctx.beginPath();ctx.arc(W-72-i*24,98,7,0,TAU);ctx.fill()}
  txt(Math.ceil(game.timer),W/2,56,42,'center','#f0e5c6');
  ctx.fillStyle='rgba(0,0,0,.65)';ctx.fillRect(55,94,180,8);ctx.fillRect(W-235,94,180,8);ctx.fillStyle='#d5b23c';ctx.fillRect(55,94,180*(p.runMeter/100),8);ctx.fillRect(W-55-180*(e.runMeter/100),94,180*(e.runMeter/100),8);
  if(p.comboHits>1&&p.comboT>0){txt(`${p.comboHits} HITS`,70,135,27,'left','#ffd55d');txt(`${p.comboDamage.toFixed(0)}%`,70,164,19,'left','#fff')}
  if(e.comboHits>1&&e.comboT>0){txt(`${e.comboHits} HITS`,W-70,135,27,'right','#ffd55d');txt(`${e.comboDamage.toFixed(0)}%`,W-70,164,19,'right','#fff')}
  if(game.intro>0){
    const a=clamp(1-Math.abs(game.intro-1.2),0,1);ctx.save();ctx.globalAlpha=clamp(a*1.6,0,1);
    txt(game.intro>1.15?`ROUND ${game.round}`:'FIGHT!',W/2,H*.42,game.intro>1.15?58:88,'center',game.intro>1.15?'#e8dfc6':'#d63028');ctx.restore();
  }
  if(game.finishT>0&&!game.finisher){
    const loser=game.winner===game.player?game.enemy:game.player;
    txt(loser.data.gender==='female'?'FINISH HER!':'FINISH HIM!',W/2,H*.32,78,'center','#d52f27');
    if(game.winner===game.player)txt('Выполни Fatality или: F F U U LP — Stage Fatal',W/2,H*.39,19,'center','#f2ead9');
  }
  if(game.paused)drawPause();
}
function healthBar(x,y,w,h,v,flip,color){
  rr(x,y,w,h,4,'#171719','#d1c4a1',2);const q=clamp(v,0,1),ww=(w-8)*q;ctx.fillStyle=q<.25?'#b32424':'#c6b845';
  if(flip)ctx.fillRect(x+w-4-ww,y+4,ww,h-8);else ctx.fillRect(x+4,y+4,ww,h-8);
  ctx.save();ctx.globalAlpha=.28;ctx.fillStyle=color;if(flip)ctx.fillRect(x+w-4-ww,y+4,ww,6);else ctx.fillRect(x+4,y+4,ww,6);ctx.restore();
}
function drawPause(){
  ctx.fillStyle='rgba(0,0,0,.78)';ctx.fillRect(0,0,W,H);txt('ПАУЗА',W/2,150,62,'center','#e6d8b4');
  txt('Управление',W/2,230,28,'center','#fff');txt('J — LP   U — HP   K — LK   I — HK   L — BLOCK   O — RUN',W/2,276,22,'center','#d8d8d8','Arial');
  txt('WASD / стрелки — движение. Команды считаются относительно соперника.',W/2,315,19,'center','#bfc3c6','Arial');
  txt('ESC / Ⅱ — продолжить',W/2,390,22,'center','#f2cf67');
}
function drawTitle(){
  drawStage(DATA.stages[12],game.clock*.4);
  ctx.fillStyle='rgba(0,0,0,.42)';ctx.fillRect(0,0,W,H);
  ctx.save();ctx.translate(W/2,265);ctx.strokeStyle='#a9863f';ctx.lineWidth=13;ctx.beginPath();ctx.arc(0,0,120,0,TAU);ctx.stroke();ctx.strokeStyle='#d1ad58';ctx.lineWidth=5;ctx.beginPath();ctx.arc(0,0,99,0,TAU);ctx.stroke();
  ctx.fillStyle='#be2d25';ctx.beginPath();ctx.moveTo(-55,55);ctx.bezierCurveTo(-120,-25,-45,-105,40,-72);ctx.bezierCurveTo(94,-50,85,12,36,38);ctx.bezierCurveTo(75,42,82,86,22,96);ctx.lineTo(42,44);ctx.bezierCurveTo(5,28,-12,6,-15,-20);ctx.bezierCurveTo(-45,0,-52,27,-55,55);ctx.fill();ctx.restore();
  txt('ULTIMATE',W/2,410,48,'center','#e3d7b2');txt('MORTAL KOMBAT 3',W/2,466,70,'center','#d6362d');txt('HD FAN REMAKE',W/2,524,30,'center','#d6c9a6');
  txt('НАЖМИ ENTER / КОСНИСЬ ЭКРАНА',W/2,620,22,'center','#f2e6c8');txt(`v${DATA.version} • OFFLINE`,W/2,672,15,'center','#909399','Arial');
}
function drawSelect(){
  const list=corePlayable();ctx.fillStyle='#070709';ctx.fillRect(0,0,W,H);txt('CHOOSE YOUR FIGHTER',W/2,50,42,'center','#d9cda9');
  const cols=7,cellW=132,cellH=122,startX=(W-cols*cellW)/2,startY=92;
  for(let i=0;i<list.length;i++){
    const f=list[i],col=i%cols,row=(i/cols)|0,x=startX+col*cellW,y=startY+row*cellH;
    rr(x+5,y+5,cellW-10,cellH-10,6,i===game.select?'#3a2517':'#111216',i===game.select?'#e5b64f':'#3b3d43',i===game.select?4:2);
    ctx.save();ctx.translate(x+cellW/2,y+88);ctx.scale(.36,.36);const fake={x:0,y:0,face:1,data:f,activeData:f,state:'idle',stateT:game.clock,air:false,crouch:false,blocking:false,attack:null,flash:0,invisible:0};drawMiniFighter(fake);ctx.restore();
    txt(f.name,x+cellW/2,y+104,13,'center',i===game.select?'#ffe19b':'#dedede','Arial');
    if(f.secret)txt('SECRET',x+cellW-12,y+14,9,'right','#d2aa55','Arial');
  }
  const sel=list[game.select];txt(sel.name,W/2,620,36,'center',sel.primary);txt('ENTER — выбрать • ←↑↓→ — персонаж',W/2,670,18,'center','#b9bbbf','Arial');
}
function drawMiniFighter(f){ctx.save();ctx.translate(f.x,f.y);const p={hip:[0,-82],chest:[0,-142],head:[0,-181],la:[-34,-132],ra:[34,-132],lh:[-48,-80],rh:[48,-80],lf:[-25,-4],rf:[25,-4]};drawHumanoid(f,f.activeData,p,f.activeData.style==='sheeva');ctx.restore()}
function drawTower(){
  const p=corePlayable()[game.select];drawStage(DATA.stages[9],game.clock*.25);ctx.fillStyle='rgba(0,0,0,.62)';ctx.fillRect(0,0,W,H);txt('KOMBAT TOWER',W/2,55,48,'center','#e3d7b3');
  const levels=game.tower.length;for(let i=0;i<levels;i++){const idx=levels-1-i,f=game.tower[idx],y=115+i*50,active=idx===game.towerStep;
    rr(W/2-210,y-20,420,40,5,active?'#4a251a':'rgba(18,18,21,.85)',active?'#e2b24d':'#44464b',active?3:1);
    txt(`${idx+1}. ${f.name}`,W/2,y,20,'center',active?'#ffd98b':'#d0d1d3','Arial');
  }
  txt(`${p.name} — ${game.towerStep}/${levels-1}`,W/2,650,22,'center',p.primary);txt('ENTER — следующий бой',W/2,685,17,'center','#ddd','Arial');
}
function drawGameOver(){
  drawStage(DATA.stages[6],game.clock*.2);ctx.fillStyle='rgba(0,0,0,.68)';ctx.fillRect(0,0,W,H);txt('YOU HAVE BEEN DEFEATED',W/2,280,62,'center','#c92c27');txt('ENTER — REVENGE',W/2,390,28,'center','#ead9ae');txt('ESC — TITLE',W/2,435,18,'center','#aaa','Arial');
}
function drawEnding(){
  drawStage(DATA.stages[15],game.clock*.2);ctx.fillStyle='rgba(0,0,0,.58)';ctx.fillRect(0,0,W,H);
  const f=corePlayable()[game.select];txt(`${f.name} WINS`,W/2,130,66,'center',f.primary);txt('SHAO KAHN HAS BEEN DEFEATED',W/2,210,30,'center','#e7d8b3');
  txt('EARTHREALM IS FREE',W/2,255,26,'center','#fff');txt(`SCORE ${game.score.toLocaleString('en-US')}`,W/2,340,30,'center','#f2cf67');
  txt('ENTER — НОВАЯ БАШНЯ',W/2,610,22,'center','#eee');
}

// ---------- loop ----------
function update(dt){
  game.clock+=dt;game.stateT+=dt;game.shake=Math.max(0,game.shake-dt);game.flash=Math.max(0,game.flash-dt*1.8);game.stageFlash=Math.max(0,game.stageFlash-dt*1.4);
  if(pressed.pause&&game.state==='fight'){game.paused=!game.paused;pressed.pause=false;tone('menu',.4)}
  if(game.paused){clearPressed();return}
  if(game.state==='title'){
    showControls(false);
    if(pressed.confirm){setState('select');tone('menu',.8)}
  }else if(game.state==='select'){
    const list=corePlayable(),cols=7;
    if(pressed.left){game.select=(game.select-1+list.length)%list.length;tone('menu',.35)}
    if(pressed.right){game.select=(game.select+1)%list.length;tone('menu',.35)}
    if(pressed.up){game.select=(game.select-cols+list.length)%list.length;tone('menu',.35)}
    if(pressed.down){game.select=(game.select+cols)%list.length;tone('menu',.35)}
    if(pressed.confirm){game.tower=buildTower(list[game.select].id);game.towerStep=0;setState('tower');tone('menu',.8)}
  }else if(game.state==='tower'){
    if(pressed.confirm||game.stateT>1.3)startFight();
  }else if(game.state==='fight'){
    if(game.intro>0){game.intro-=dt}
    else if(!game.winner&&!game.finisher){
      game.timer=Math.max(0,game.timer-dt);game.player.update(dt,game.enemy);game.enemy.update(dt,game.player);
      separateFighters(game.player,game.enemy);
      if(game.timer<=0)roundWon(game.player.health>=game.enemy.health?game.player:game.enemy);
    }else{
      if(game.finishT>0&&!game.finisher){
        game.finishT-=dt;game.player.update(dt,game.enemy);game.enemy.update(dt,game.player);
        if(game.finishT<=0){game.outro=1.8}
      }
      if(game.finisher)updateFinisher(dt);
    }
    updateEffects(dt);
    if(game.outro>0){game.outro-=dt;if(game.outro<=0){
      if(game.p1wins>=2||game.p2wins>=2)finishMatch();else{game.round++;resetRound()}
    }}
  }else if(game.state==='gameover'){
    if(pressed.confirm){game.towerStep=Math.max(0,game.towerStep);setState('tower')}
    if(pressed.pause){setState('title')}
  }else if(game.state==='ending'){
    game.endingT+=dt;if(pressed.confirm){game.tower=buildTower(corePlayable()[game.select].id);game.towerStep=0;setState('tower')}
  }
  clearPressed();
}
function separateFighters(a,b){
  const min=a.data.style==='motaro'||b.data.style==='motaro'?115:78,dx=b.x-a.x;
  if(Math.abs(dx)<min){const push=(min-Math.abs(dx))*.5,sg=Math.sign(dx)||1;if(!a.air)a.x-=push*sg;if(!b.air)b.x+=push*sg;a.x=clamp(a.x,55,W-55);b.x=clamp(b.x,55,W-55)}
}
function render(){
  ctx.setTransform(1,0,0,1,0,0);ctx.clearRect(0,0,W,H);
  if(game.state==='title')drawTitle();
  else if(game.state==='select')drawSelect();
  else if(game.state==='tower')drawTower();
  else if(game.state==='gameover')drawGameOver();
  else if(game.state==='ending')drawEnding();
  else if(game.state==='fight'){
    const s=DATA.stages[game.stageIndex];const sx=game.shake>0?rand(-game.shake*18,game.shake*18):0,sy=game.shake>0?rand(-game.shake*9,game.shake*9):0;
    ctx.save();ctx.translate(sx,sy);drawStage(s,game.clock);drawEffects();drawFighter(game.player);drawFighter(game.enemy);ctx.restore();drawHUD();
    if(game.finisher){const f=game.finisher;txt(f.type==='stage'?'STAGE FATALITY':f.move.name.toUpperCase(),W/2,160,42,'center','#d42d27')}
    if(game.outro>0&&game.winner)txt(`${game.winner.data.name} WINS`,W/2,225,50,'center','#e0d2ae');
  }
  if(game.flash>0){ctx.save();ctx.globalAlpha=clamp(game.flash,0,.8);ctx.fillStyle='#fff';ctx.fillRect(0,0,W,H);ctx.restore()}
  if(game.stageFlash>0){ctx.save();ctx.globalAlpha=game.stageFlash*.45;ctx.fillStyle='#ff4a24';ctx.fillRect(0,0,W,H);ctx.restore()}
}
let last=performance.now(),acc=0;
function frame(ts){
  let dt=Math.min(.05,(ts-last)/1000||0);last=ts;acc+=dt;
  while(acc>=1/FPS){update(1/FPS);acc-=1/FPS}
  render();requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
window.__UMK3_DEBUG__={game,DATA,startFight,setState,buildTower,commandMatches};
})();
