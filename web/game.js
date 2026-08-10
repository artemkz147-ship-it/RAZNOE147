(() => {
'use strict';

const canvas = document.getElementById('game');
const ctx = canvas.getContext('2d', { alpha: false, desynchronized: true });
const touchUI = document.getElementById('touch-ui');
const pauseBtn = document.getElementById('pause');
const W = 1280, H = 720, FLOOR = 590;
const TAU = Math.PI * 2;

const clamp = (v,a,b)=>Math.max(a,Math.min(b,v));
const lerp = (a,b,t)=>a+(b-a)*t;
const rand = (a,b)=>a+Math.random()*(b-a);
const choose = a=>a[(Math.random()*a.length)|0];
const ease = t=>1-Math.pow(1-t,3);

const roster = [
  ['scorpion','SCORPION','#d8a20e','#231a08','teleport'],
  ['subzero','SUB-ZERO','#2696d1','#061b2e','freeze'],
  ['reptile','REPTILE','#48a945','#09210b','acid'],
  ['smoke','SMOKE','#9aa1a6','#1b1d1f','smoke'],
  ['ermac','ERMAC','#b3232c','#260608','force'],
  ['rain','RAIN','#6f49c7','#160a2f','lightning'],
  ['noob','NOOB SAIBOT','#232329','#050506','shadow'],
  ['classic-subzero','CLASSIC SUB-ZERO','#4fa8d6','#101820','freeze'],
  ['kitana','KITANA','#2d62c7','#071630','fan'],
  ['jade','JADE','#2e9f66','#092019','staff'],
  ['mileena','MILEENA','#b53695','#2e0828','roll'],
  ['cyrax','CYRAX','#d0aa14','#282105','net'],
  ['sektor','SEKTOR','#b62823','#290705','rocket'],
  ['robot-smoke','ROBOT SMOKE','#7e878d','#15181a','rocket'],
  ['liu-kang','LIU KANG','#b12623','#21100c','fireball'],
  ['kung-lao','KUNG LAO','#293d67','#0d1015','hat'],
  ['sonya','SONYA','#5c9442','#12230b','rings'],
  ['jax','JAX','#8e5c42','#18100d','ground'],
  ['kano','KANO','#7c5a48','#19110d','knife'],
  ['nightwolf','NIGHTWOLF','#26736b','#08201d','arrow'],
  ['sindel','SINDEL','#704b8f','#17101d','scream'],
  ['kabal','KABAL','#665b48','#14120f','dash'],
  ['stryker','STRYKER','#5c6170','#101216','grenade'],
  ['sheeva','SHEEVA','#8d4d36','#24100a','stomp'],
  ['shang-tsung','SHANG TSUNG','#b99532','#211b08','skull'],
  ['human-smoke','HUMAN SMOKE','#858b91','#17191b','teleport'],
  ['motaro','MOTARO','#7b462b','#1a0e08','charge'],
  ['shao-kahn','SHAO KAHN','#6d3130','#170809','hammer']
].map((x,i)=>({id:x[0],name:x[1],primary:x[2],dark:x[3],special:x[4],boss:i>=26}));

const stages = [
  {id:'subway',name:'THE SUBWAY',palette:['#0d1114','#172228','#33515b'],kind:'subway'},
  {id:'rooftop',name:'THE ROOFTOP',palette:['#0d0d17','#25213b','#7a4054'],kind:'roof'},
  {id:'soul',name:'SOUL CHAMBER',palette:['#07120c','#123724','#4bd25b'],kind:'soul'},
  {id:'street',name:'THE STREET',palette:['#121016','#29202c','#67323d'],kind:'street'},
  {id:'bell',name:'BELL TOWER',palette:['#110d08','#342112','#8a5a24'],kind:'tower'},
  {id:'temple',name:'THE TEMPLE',palette:['#100b08','#2e1710','#71352a'],kind:'temple'},
  {id:'graveyard',name:'GRAVEYARD',palette:['#0b1014','#202b30','#586c68'],kind:'grave'},
  {id:'waterfront',name:'WATERFRONT',palette:['#06131c','#0e3040','#326a70'],kind:'water'},
  {id:'pit3',name:'THE PIT III',palette:['#100c0c','#2a1918','#6e2a25'],kind:'pit'},
  {id:'busorez',name:'SCISLAC BUSOREZ',palette:['#0c0d0e','#2c2f31','#80705a'],kind:'industry'}
];

const input = {left:false,right:false,up:false,down:false,punch:false,kick:false,block:false,special:false,run:false};
const pressed = Object.create(null);
const keyMap = {
  KeyA:'left',ArrowLeft:'left',KeyD:'right',ArrowRight:'right',KeyW:'up',ArrowUp:'up',KeyS:'down',ArrowDown:'down',
  KeyJ:'punch',KeyK:'kick',KeyL:'block',KeyI:'special',KeyU:'run',Enter:'confirm',Space:'confirm',Escape:'pause'
};
window.addEventListener('keydown',e=>{
  const k=keyMap[e.code]; if(!k)return; e.preventDefault();
  if(!input[k]) pressed[k]=true;
  input[k]=true;
});
window.addEventListener('keyup',e=>{ const k=keyMap[e.code]; if(!k)return; input[k]=false; });

for(const b of document.querySelectorAll('.touch')){
  const k=b.dataset.key;
  const down=e=>{e.preventDefault(); if(!input[k]) pressed[k]=true; input[k]=true; b.classList.add('pressed');};
  const up=e=>{e.preventDefault(); input[k]=false; b.classList.remove('pressed');};
  b.addEventListener('pointerdown',down); b.addEventListener('pointerup',up); b.addEventListener('pointercancel',up); b.addEventListener('pointerleave',up);
}

let audioCtx=null;
function sound(type='hit', strength=1){
  try{
    audioCtx ||= new (window.AudioContext||window.webkitAudioContext)();
    const t=audioCtx.currentTime;
    const o=audioCtx.createOscillator(), g=audioCtx.createGain();
    let f=100,d=.08,w='square';
    if(type==='menu'){f=220;d=.05;w='triangle';}
    if(type==='kick'){f=72;d=.1;w='sawtooth';}
    if(type==='special'){f=145;d=.2;w='sawtooth';}
    if(type==='block'){f=420;d=.05;w='square';}
    if(type==='ko'){f=60;d=.6;w='sawtooth';}
    o.type=w; o.frequency.setValueAtTime(f,t); o.frequency.exponentialRampToValueAtTime(Math.max(30,f*.45),t+d);
    g.gain.setValueAtTime(.0001,t); g.gain.exponentialRampToValueAtTime(.12*strength,t+.006); g.gain.exponentialRampToValueAtTime(.0001,t+d);
    o.connect(g); g.connect(audioCtx.destination); o.start(t); o.stop(t+d+.02);
  }catch(_){ }
}

function roundRect(x,y,w,h,r,fill,stroke){
  r=Math.min(r,w/2,h/2); ctx.beginPath(); ctx.roundRect(x,y,w,h,r); if(fill){ctx.fillStyle=fill;ctx.fill();} if(stroke){ctx.strokeStyle=stroke;ctx.stroke();}
}
function text(str,x,y,size,align='center',color='#fff',font='Impact'){
  ctx.save(); ctx.font=`${size}px ${font}`; ctx.textAlign=align; ctx.textBaseline='middle'; ctx.fillStyle=color; ctx.shadowColor='#000';ctx.shadowBlur=4;ctx.shadowOffsetY=2;ctx.fillText(str,x,y);ctx.restore();
}

const game = {
  state:'title', t:0, paused:false, select:0, opponentSelect:1, stageIndex:0,
  tower:[], towerStep:0, score:0, playerWins:0, enemyWins:0, round:1, timer:99,
  intro:2.2, outro:0, shake:0, flash:0, winner:null, message:'', particles:[], projectiles:[],
  player:null, enemy:null
};

class Fighter {
  constructor(data,x,face,isCPU=false){
    this.data=data; this.x=x; this.y=FLOOR; this.vx=0; this.vy=0; this.face=face; this.cpu=isCPU;
    this.health=100; this.state='idle'; this.stateT=0; this.attackT=0; this.attackDone=false; this.hitstun=0; this.blockstun=0;
    this.combo=0; this.comboT=0; this.air=false; this.crouch=false; this.blocking=false; this.dead=false; this.frozen=0;
    this.runMeter=100; this.specialCD=0; this.aiT=0; this.ai={}; this.roundWins=0; this.flash=0;
  }
  reset(x,face){
    Object.assign(this,{x,y:FLOOR,vx:0,vy:0,face,health:100,state:'idle',stateT:0,attackT:0,attackDone:false,hitstun:0,blockstun:0,combo:0,comboT:0,air:false,crouch:false,blocking:false,dead:false,frozen:0,runMeter:100,specialCD:0,aiT:0,flash:0});
  }
  get hurtbox(){ const h=this.crouch?105:176; return {x:this.x-34,y:this.y-h,w:68,h}; }
  update(dt, other){
    this.stateT += dt; this.flash=Math.max(0,this.flash-dt*7); this.specialCD=Math.max(0,this.specialCD-dt); this.comboT-=dt; if(this.comboT<=0)this.combo=0;
    if(this.dead) return this.physics(dt);
    if(this.frozen>0){ this.frozen-=dt; this.state='frozen'; return this.physics(dt); }
    if(this.hitstun>0){ this.hitstun-=dt; this.state='hit'; return this.physics(dt); }
    if(this.blockstun>0){this.blockstun-=dt;this.state='block';return this.physics(dt);}
    if(this.cpu) this.cpuThink(dt,other);
    const c=this.cpu?this.ai:input;
    this.blocking=!!c.block && !this.air; this.crouch=!!c.down && !this.air && this.state!=='attack';
    if(this.blocking){this.state='block';this.vx*=.72;}
    else if(this.state==='attack' || this.state==='special') this.attackUpdate(dt,other);
    else {
      if(c.up && !this.air){this.vy=-760;this.air=true;this.state='jump'; sound('menu',.4);}
      const dir=(c.left?-1:0)+(c.right?1:0);
      let speed=c.run && this.runMeter>0?390:250;
      if(c.run && dir){this.runMeter=Math.max(0,this.runMeter-dt*32);}
      else this.runMeter=Math.min(100,this.runMeter+dt*18);
      if(dir && !this.crouch){this.vx=dir*speed;this.state=this.air?'jump':'walk';}
      else {this.vx*=this.air?.985:.72;if(!this.air)this.state=this.crouch?'crouch':'idle';}
      if((c.punch && (this.cpu||pressed.punch)) || (this.cpu&&c.punch)){this.startAttack('punch'); if(!this.cpu)pressed.punch=false;}
      else if((c.kick && (this.cpu||pressed.kick)) || (this.cpu&&c.kick)){this.startAttack('kick'); if(!this.cpu)pressed.kick=false;}
      else if((c.special && (this.cpu||pressed.special)) || (this.cpu&&c.special)){ if(this.specialCD<=0)this.startAttack('special'); if(!this.cpu)pressed.special=false; }
    }
    this.physics(dt);
    const gap=other.x-this.x; if(Math.abs(gap)>8)this.face=Math.sign(gap);
  }
  physics(dt){
    this.vy+=this.air?1800*dt:0; this.x+=this.vx*dt; this.y+=this.vy*dt;
    if(this.y>=FLOOR){this.y=FLOOR;this.vy=0;if(this.air){this.air=false;if(this.state==='jump')this.state='idle';}}
    this.x=clamp(this.x,62,W-62);
  }
  startAttack(kind){
    this.attackDone=false; this.attackT=0; this.state=kind==='special'?'special':'attack'; this.attackKind=kind;
    if(kind==='special'){this.specialCD=1.15;sound('special',.8);} else sound(kind==='kick'?'kick':'hit',.35);
  }
  attackUpdate(dt,other){
    this.attackT+=dt;
    const kind=this.attackKind;
    const startup=kind==='punch'?.08:kind==='kick'?.12:.18;
    const active=kind==='punch'?.10:kind==='kick'?.12:.18;
    const total=kind==='punch'?.31:kind==='kick'?.4:.62;
    if(kind==='special' && this.attackT>=startup && !this.attackDone){this.attackDone=true;this.performSpecial(other);}
    if(kind!=='special' && this.attackT>=startup && this.attackT<startup+active && !this.attackDone){
      this.attackDone=true;
      const reach=kind==='kick'?105:82, damage=kind==='kick'?9:6.5;
      if(Math.abs(other.x-this.x)<reach && Math.abs(other.y-this.y)<95) hit(this,other,damage,kind==='kick'?280:170,kind);
    }
    if(this.attackT>=total){this.state=this.air?'jump':'idle';this.attackT=0;}
  }
  performSpecial(other){
    const s=this.data.special;
    const projectile=['freeze','acid','force','lightning','fan','net','rocket','fireball','hat','rings','knife','arrow','grenade','skull'].includes(s);
    if(projectile){
      const effect=s==='freeze'?'freeze':null;
      spawnProjectile(this, s, 9+(s==='rocket'?3:0), effect);
      return;
    }
    if(s==='teleport'||s==='shadow'){
      const old=this.x; this.x=clamp(other.x-other.face*92,70,W-70); this.face=Math.sign(other.x-this.x)||1;
      burst(old,this.y-85,this.data.primary,22); burst(this.x,this.y-85,this.data.primary,22);
      if(Math.abs(other.x-this.x)<115)hit(this,other,11,260,'special');
    } else if(s==='dash'||s==='roll'||s==='charge'){
      this.vx=this.face*(s==='dash'?900:650); if(Math.abs(other.x-this.x)<180) hit(this,other,12,390,'special');
    } else if(s==='ground'||s==='stomp'){
      game.shake=.42; burst(this.x,this.y-6,'#caa56a',32); if(Math.abs(other.x-this.x)<290)hit(this,other,13,220,'special');
    } else if(s==='scream'){
      burst(this.x+this.face*80,this.y-110,'#e5a5ff',38); if(Math.abs(other.x-this.x)<260)hit(this,other,12,160,'special');
    } else if(s==='smoke'){
      burst(this.x,this.y-90,'#aeb5ba',48); this.x=clamp(this.x+this.face*180,70,W-70); if(Math.abs(other.x-this.x)<120)hit(this,other,10,220,'special');
    } else if(s==='staff'||s==='hammer'){
      if(Math.abs(other.x-this.x)<145)hit(this,other,s==='hammer'?16:12,420,'special');
    } else {
      if(Math.abs(other.x-this.x)<120)hit(this,other,11,260,'special');
    }
  }
  cpuThink(dt,other){
    this.aiT-=dt;
    if(this.aiT>0)return;
    this.aiT=rand(.08,.22); const d=other.x-this.x, ad=Math.abs(d);
    const r=Math.random(); this.ai={};
    if(other.state==='attack' && ad<130 && r<.7){this.ai.block=true;return;}
    if(ad>260){ if(d<0)this.ai.left=true; else this.ai.right=true; if(r<.22)this.ai.run=true; if(r<.15)this.ai.special=true; }
    else if(ad<75){ if(r<.22){if(d<0)this.ai.right=true;else this.ai.left=true;} else if(r<.55)this.ai.punch=true; else if(r<.84)this.ai.kick=true; else this.ai.special=true; }
    else { if(r<.28){if(d<0)this.ai.left=true;else this.ai.right=true;} else if(r<.52)this.ai.kick=true; else if(r<.68)this.ai.special=true; else if(r<.78)this.ai.up=true; else this.ai.block=true; }
  }
}

function rectsOverlap(a,b){return a.x<b.x+b.w&&a.x+a.w>b.x&&a.y<b.y+b.h&&a.y+a.h>b.y;}
function hit(attacker,defender,damage,kb,kind){
  if(defender.dead)return;
  let blocked=defender.blocking && Math.sign(attacker.x-defender.x)===defender.face;
  if(blocked){damage*=.18;kb*=.22;defender.blockstun=.12;sound('block',.6);burst(defender.x+defender.face*28,defender.y-110,'#e9e1c6',10);}
  else {defender.hitstun=kind==='special'?.34:.2;sound(kind==='kick'?'kick':'hit',.8);burst(defender.x,defender.y-105,'#b31919',kind==='special'?26:14); game.shake=Math.max(game.shake,kind==='special'?.28:.12);game.flash=.05;}
  defender.health=clamp(defender.health-damage,0,100); defender.vx=attacker.face*kb; defender.flash=1;
  attacker.combo++;attacker.comboT=1.1;
  if(kind==='special' && attacker.data.special==='freeze' && !blocked) defender.frozen=1.15;
  if(defender.health<=0){defender.dead=true;defender.hitstun=1.4;defender.vy=-360;defender.air=true;sound('ko',1);game.outro=2.6;game.winner=attacker;game.message='K.O.';}
}

function spawnProjectile(owner,kind,damage,effect){
  const speed=kind==='rocket'?680:kind==='hat'?560:610;
  game.projectiles.push({owner,x:owner.x+owner.face*55,y:owner.y-(kind==='grenade'?95:125),vx:owner.face*speed,life:2,kind,damage,effect,r:kind==='rocket'?15:10,spin:0});
}
function burst(x,y,color,count=16){
  for(let i=0;i<count;i++)game.particles.push({x,y,vx:rand(-260,260),vy:rand(-340,80),life:rand(.22,.75),max:1,color,size:rand(2,8),g:rand(250,900)});
}
function updateFX(dt){
  for(const p of game.particles){p.life-=dt;p.vy+=p.g*dt;p.x+=p.vx*dt;p.y+=p.vy*dt;}
  game.particles=game.particles.filter(p=>p.life>0);
  for(const p of game.projectiles){p.life-=dt;p.x+=p.vx*dt;p.spin+=dt*8; const target=p.owner===game.player?game.enemy:game.player;if(target&&!target.dead&&rectsOverlap({x:p.x-p.r,y:p.y-p.r,w:p.r*2,h:p.r*2},target.hurtbox)){hit(p.owner,target,p.damage,260,'special');if(p.effect==='freeze')target.frozen=1.25;p.life=0;burst(p.x,p.y,projectileColor(p.kind),18);}}
  game.projectiles=game.projectiles.filter(p=>p.life>0&&p.x>-50&&p.x<W+50);
}
function projectileColor(kind){return ({freeze:'#70d9ff',acid:'#6cff55',force:'#ff5252',lightning:'#b895ff',fan:'#8cc4ff',net:'#e7cb5a',rocket:'#ff7b31',fireball:'#ff742b',hat:'#d8d9dc',rings:'#6cffbf',knife:'#ddd7c8',arrow:'#58ffb6',grenade:'#85a469',skull:'#75ff57'})[kind]||'#fff';}

function startFight(opponentIndex=null){
  const p=roster[game.select], e=roster[opponentIndex??game.opponentSelect];
  game.stageIndex=(game.towerStep||game.stageIndex)%stages.length;
  game.player=new Fighter(p,330,1,false); game.enemy=new Fighter(e,950,-1,true);
  game.playerWins=0;game.enemyWins=0;game.round=1;game.timer=99;game.intro=2.3;game.outro=0;game.winner=null;game.message='ROUND 1';game.projectiles=[];game.particles=[];game.state='fight';
  touchUI.classList.add('show');pauseBtn.classList.add('show');
}
function nextRound(){
  game.round++;game.timer=99;game.intro=1.8;game.outro=0;game.winner=null;game.message=`ROUND ${game.round}`;game.projectiles=[];game.particles=[];
  game.player.reset(330,1);game.enemy.reset(950,-1);
}
function finishMatch(){
  touchUI.classList.remove('show');pauseBtn.classList.remove('show');
  const won=game.playerWins>game.enemyWins;
  if(won){game.score+=1000+Math.floor(game.timer*10);game.towerStep++; if(game.towerStep<game.tower.length){game.state='tower';game.t=0;} else {game.state='ending';game.t=0;}}
  else {game.state='result';game.t=0;}
}
function buildTower(){
  const ids=roster.map((_,i)=>i).filter(i=>i!==game.select&&i<26); ids.sort(()=>Math.random()-.5);
  game.tower=ids.slice(0,7).concat([26,27]);game.towerStep=0;game.score=0;
}

function update(dt){
  game.t+=dt; if(game.paused)return;
  if(game.state==='title'){
    if(pressed.confirm||pressed.punch||pressed.special){pressed.confirm=pressed.punch=pressed.special=false;game.state='select';game.t=0;sound('menu');}
    return;
  }
  if(game.state==='select'){
    if(pressed.left){game.select=(game.select-1+roster.length)%roster.length;pressed.left=false;sound('menu',.4);}
    if(pressed.right){game.select=(game.select+1)%roster.length;pressed.right=false;sound('menu',.4);}
    if(pressed.up){game.select=(game.select-6+roster.length)%roster.length;pressed.up=false;sound('menu',.4);}
    if(pressed.down){game.select=(game.select+6)%roster.length;pressed.down=false;sound('menu',.4);}
    if(pressed.confirm||pressed.punch||pressed.special){pressed.confirm=pressed.punch=pressed.special=false;buildTower();game.state='tower';game.t=0;sound('special',.5);}
    return;
  }
  if(game.state==='tower'){
    if(game.t>.75 && (pressed.confirm||pressed.punch||pressed.special||game.t>2.5)){pressed.confirm=pressed.punch=pressed.special=false;startFight(game.tower[game.towerStep]);}
    return;
  }
  if(game.state==='fight'){
    if(pressed.pause){pressed.pause=false;game.paused=!game.paused;return;}
    if(game.intro>0){game.intro-=dt;if(game.intro<.9)game.message='FIGHT!';return;}
    if(game.outro>0){game.outro-=dt;updateFX(dt);game.player.update(dt,game.enemy);game.enemy.update(dt,game.player);if(game.outro<=0){if(game.winner===game.player)game.playerWins++;else game.enemyWins++;if(game.playerWins>=2||game.enemyWins>=2)finishMatch();else nextRound();}return;}
    game.timer=Math.max(0,game.timer-dt); if(game.timer<=0){game.winner=game.player.health>=game.enemy.health?game.player:game.enemy;game.message='TIME';game.outro=2.2;return;}
    game.player.update(dt,game.enemy);game.enemy.update(dt,game.player);
    const dx=game.enemy.x-game.player.x; if(Math.abs(dx)<76){const push=(76-Math.abs(dx))*.5;const s=Math.sign(dx)||1;game.player.x-=s*push;game.enemy.x+=s*push;}
    updateFX(dt); game.shake=Math.max(0,game.shake-dt);game.flash=Math.max(0,game.flash-dt);
    return;
  }
  if(game.state==='result'||game.state==='ending'){
    if(game.t>1 && (pressed.confirm||pressed.punch||pressed.special)){pressed.confirm=pressed.punch=pressed.special=false;game.state='select';game.t=0;}
  }
}

function stageBackground(stage,t){
  const [a,b,c]=stage.palette;
  const g=ctx.createLinearGradient(0,0,0,H);g.addColorStop(0,a);g.addColorStop(.55,b);g.addColorStop(1,'#09090a');ctx.fillStyle=g;ctx.fillRect(0,0,W,H);
  ctx.save();
  if(stage.kind==='subway'){
    ctx.fillStyle='#050607';ctx.fillRect(0,120,W,310); for(let i=0;i<9;i++){const x=i*170-(t*20)%170;ctx.fillStyle=i%2?c:'#1b2d31';ctx.fillRect(x,165,120,170);ctx.fillStyle='#071014';ctx.fillRect(x+12,180,96,105);}
    ctx.fillStyle='#182025';ctx.fillRect(0,430,W,160);ctx.strokeStyle='#61686a';ctx.lineWidth=6;for(let y=478;y<590;y+=42){ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(W,y);ctx.stroke();}
  } else if(stage.kind==='roof'){
    ctx.fillStyle='#080912';ctx.fillRect(0,350,W,240);for(let i=0;i<18;i++){const x=i*80;const h=randSeed(i)*210+70;ctx.fillStyle=i%3===0?'#161a28':'#0d101a';ctx.fillRect(x,350-h,64,h);}
    ctx.fillStyle=c;ctx.globalAlpha=.25;ctx.beginPath();ctx.arc(930,150,95,0,TAU);ctx.fill();ctx.globalAlpha=1;
  } else if(stage.kind==='soul'){
    ctx.fillStyle='#07130b';ctx.fillRect(0,250,W,340);for(let i=0;i<12;i++){let x=i*115+Math.sin(t+i)*8;ctx.strokeStyle=i%2?c:'#1e6a32';ctx.lineWidth=10;ctx.beginPath();ctx.moveTo(x,590);ctx.bezierCurveTo(x-30,470,x+40,380,x,245);ctx.stroke();}
    for(let i=0;i<10;i++){ctx.fillStyle=`rgba(90,255,115,${.08+.05*Math.sin(t*2+i)})`;ctx.beginPath();ctx.arc(80+i*125,210+Math.sin(t+i)*50,35,0,TAU);ctx.fill();}
  } else if(stage.kind==='street'){
    ctx.fillStyle='#09090c';ctx.fillRect(0,300,W,290);for(let i=0;i<7;i++){ctx.fillStyle=i%2?'#221920':'#151218';ctx.fillRect(i*210-50,160,180,330);ctx.fillStyle=c;ctx.globalAlpha=.28;ctx.fillRect(i*210,210,48,70);ctx.globalAlpha=1;}ctx.fillStyle='#19191c';ctx.fillRect(0,505,W,85);
  } else if(stage.kind==='tower'){
    ctx.fillStyle='#15100a';ctx.fillRect(0,140,W,450);for(let i=0;i<8;i++){ctx.fillStyle='#332211';ctx.fillRect(i*180,120,35,470);ctx.fillStyle=c;ctx.globalAlpha=.2;ctx.beginPath();ctx.arc(i*180+90,250,42,0,TAU);ctx.fill();ctx.globalAlpha=1;}
  } else if(stage.kind==='temple'){
    ctx.fillStyle='#130b09';ctx.fillRect(0,220,W,370);for(let i=0;i<8;i++){ctx.fillStyle='#3b1c15';ctx.fillRect(i*180,170,110,360);ctx.fillStyle='#130906';ctx.fillRect(i*180+18,220,74,140);}ctx.fillStyle=c;ctx.globalAlpha=.25;ctx.fillRect(0,90,W,55);ctx.globalAlpha=1;
  } else if(stage.kind==='grave'){
    ctx.fillStyle='#10181c';ctx.fillRect(0,330,W,260);for(let i=0;i<20;i++){const x=i*70;ctx.fillStyle=i%2?'#303e42':'#253136';ctx.fillRect(x,420-(i%3)*20,30,105+(i%3)*20);ctx.beginPath();ctx.arc(x+15,420-(i%3)*20,15,Math.PI,0);ctx.fill();}ctx.globalAlpha=.12;ctx.fillStyle='#c8ffff';ctx.fillRect(0,280,W,120);ctx.globalAlpha=1;
  } else if(stage.kind==='water'){
    ctx.fillStyle='#071723';ctx.fillRect(0,240,W,350);ctx.fillStyle=c;ctx.globalAlpha=.18;for(let i=0;i<8;i++)ctx.fillRect(0,440+i*18+Math.sin(t*2+i)*4,W,4);ctx.globalAlpha=1;for(let i=0;i<9;i++){ctx.fillStyle='#0d1d22';ctx.fillRect(i*160,210,105,240);ctx.fillStyle='#102d33';ctx.fillRect(i*160+20,180,64,40);}
  } else if(stage.kind==='pit'){
    ctx.fillStyle='#120909';ctx.fillRect(0,240,W,350);for(let i=0;i<14;i++){ctx.fillStyle=i%2?'#321715':'#261210';ctx.fillRect(i*100,180,52,410);}ctx.strokeStyle='#a14732';ctx.lineWidth=8;ctx.beginPath();ctx.moveTo(0,505);ctx.lineTo(W,505);ctx.stroke();
  } else {
    ctx.fillStyle='#151719';ctx.fillRect(0,250,W,340);for(let i=0;i<11;i++){ctx.fillStyle=i%2?'#3c3d3b':'#242628';ctx.fillRect(i*130,190,92,330);ctx.fillStyle=c;ctx.globalAlpha=.25;ctx.fillRect(i*130+18,225,55,70);ctx.globalAlpha=1;}ctx.strokeStyle='#6a6254';ctx.lineWidth=5;for(let i=0;i<8;i++){ctx.beginPath();ctx.moveTo(i*190,100);ctx.lineTo(i*190+80,420);ctx.stroke();}
  }
  const fg=ctx.createLinearGradient(0,FLOOR-20,0,H);fg.addColorStop(0,'#252526');fg.addColorStop(1,'#050506');ctx.fillStyle=fg;ctx.fillRect(0,FLOOR-10,W,H-FLOOR+10);
  ctx.strokeStyle='rgba(255,255,255,.08)';ctx.lineWidth=2;ctx.beginPath();ctx.moveTo(0,FLOOR);ctx.lineTo(W,FLOOR);ctx.stroke();ctx.restore();
}
function randSeed(i){const x=Math.sin(i*999.13)*43758.5453;return x-Math.floor(x);}

function drawFighter(f){
  const d=f.data;const x=f.x,y=f.y;const face=f.face;const bob=f.air?0:Math.sin(game.t*7+f.x*.01)*2;
  ctx.save();ctx.translate(x,y+bob);ctx.scale(face,1);
  if(f.flash>0){ctx.globalCompositeOperation='screen';ctx.globalAlpha=.55;}
  ctx.shadowColor='rgba(0,0,0,.8)';ctx.shadowBlur=16;ctx.shadowOffsetY=10;
  ctx.save();ctx.scale(face,1);ctx.globalAlpha=.4;ctx.fillStyle='#000';ctx.beginPath();ctx.ellipse(0,2,46,11,0,0,TAU);ctx.fill();ctx.restore();
  const crouch=f.crouch?34:0;
  ctx.translate(0,crouch);
  limb(-18,-62,-24,-8,18,d.dark); limb(16,-64,28,-8,18,d.dark);
  roundRect(-41,-16,38,16,6,'#0a0a0b');roundRect(8,-16,42,16,6,'#0a0a0b');
  ctx.fillStyle=d.dark;ctx.beginPath();ctx.moveTo(-35,-145);ctx.lineTo(34,-145);ctx.lineTo(42,-76);ctx.lineTo(0,-55);ctx.lineTo(-42,-76);ctx.closePath();ctx.fill();
  ctx.fillStyle=d.primary;ctx.beginPath();ctx.moveTo(-27,-137);ctx.lineTo(25,-137);ctx.lineTo(28,-85);ctx.lineTo(0,-70);ctx.lineTo(-28,-85);ctx.closePath();ctx.fill();
  ctx.fillStyle='rgba(255,255,255,.16)';ctx.fillRect(-22,-132,9,49);
  ctx.fillStyle='#121214';ctx.fillRect(-40,-78,80,13);ctx.fillStyle='#b39b54';ctx.fillRect(-9,-79,18,14);
  let ax=0,ay=0;
  if(f.state==='attack'&&f.attackKind==='punch'&&f.attackT<.22){ax=70;ay=-10;}
  if(f.state==='special'){ax=38;ay=-28;}
  limb(-32,-130,-55,-75,16,skinFor(d));
  limb(32,-130,55+ax,-88+ay,16,skinFor(d));
  circle(-58,-73,14,d.dark);circle(58+ax,-87+ay,14,d.dark);
  circle(0,-174,30,skinFor(d));
  ctx.fillStyle=d.dark;ctx.beginPath();ctx.arc(0,-176,31,Math.PI,TAU);ctx.lineTo(29,-157);ctx.lineTo(-29,-157);ctx.closePath();ctx.fill();
  if(['scorpion','subzero','reptile','smoke','ermac','rain','noob','classic-subzero','human-smoke'].includes(d.id)){
    ctx.fillStyle=d.primary;ctx.beginPath();ctx.moveTo(-22,-177);ctx.lineTo(22,-177);ctx.lineTo(16,-151);ctx.lineTo(-16,-151);ctx.closePath();ctx.fill();ctx.fillStyle='#050506';ctx.fillRect(-18,-175,36,7);
  } else if(['cyrax','sektor','robot-smoke'].includes(d.id)){
    ctx.fillStyle=d.primary;ctx.beginPath();ctx.arc(0,-173,31,0,TAU);ctx.fill();ctx.fillStyle='#111';ctx.fillRect(-22,-180,44,12);ctx.fillStyle='#e8f9ff';ctx.fillRect(7,-177,7,4);
  } else if(d.id==='shao-kahn'){
    ctx.fillStyle='#aaa39b';ctx.beginPath();ctx.moveTo(-34,-194);ctx.lineTo(-13,-185);ctx.lineTo(0,-205);ctx.lineTo(13,-185);ctx.lineTo(34,-194);ctx.lineTo(25,-153);ctx.lineTo(-25,-153);ctx.closePath();ctx.fill();ctx.fillStyle='#181719';ctx.fillRect(-21,-174,42,9);
  } else {
    ctx.fillStyle='#101011';ctx.fillRect(-18,-181,12,4);ctx.fillRect(7,-181,12,4);
  }
  if(f.frozen>0){ctx.globalAlpha=.38;ctx.fillStyle='#65d8ff';ctx.fillRect(-55,-215,110,215);ctx.globalAlpha=1;}
  if(f.blocking){ctx.strokeStyle='rgba(255,235,170,.55)';ctx.lineWidth=5;ctx.beginPath();ctx.arc(18,-112,58,-1.4,1.4);ctx.stroke();}
  ctx.restore();
}
function skinFor(d){return ['cyrax','sektor','robot-smoke','noob'].includes(d.id)?'#74777b':d.id==='shao-kahn'?'#b07c63':'#b98163';}
function limb(x1,y1,x2,y2,w,color){ctx.strokeStyle=color;ctx.lineWidth=w;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(x1,y1);ctx.lineTo(x2,y2);ctx.stroke();}
function circle(x,y,r,color){ctx.fillStyle=color;ctx.beginPath();ctx.arc(x,y,r,0,TAU);ctx.fill();}

function drawProjectile(p){
  ctx.save();ctx.translate(p.x,p.y);ctx.rotate(p.spin);ctx.shadowColor=projectileColor(p.kind);ctx.shadowBlur=22;ctx.fillStyle=projectileColor(p.kind);
  if(p.kind==='hat'){ctx.fillRect(-22,-4,44,8);ctx.fillStyle='#eee';ctx.fillRect(-10,-9,20,5);} else if(p.kind==='net'){ctx.strokeStyle=projectileColor(p.kind);ctx.lineWidth=3;for(let i=-2;i<=2;i++){ctx.beginPath();ctx.moveTo(-14,i*6);ctx.lineTo(14,i*6);ctx.stroke();ctx.beginPath();ctx.moveTo(i*6,-14);ctx.lineTo(i*6,14);ctx.stroke();}} else {ctx.beginPath();ctx.arc(0,0,p.r,0,TAU);ctx.fill();ctx.globalAlpha=.35;ctx.beginPath();ctx.arc(-p.vx*.045,0,p.r*1.4,0,TAU);ctx.fill();}
  ctx.restore();
}
function drawParticles(){for(const p of game.particles){ctx.globalAlpha=clamp(p.life*2,0,1);ctx.fillStyle=p.color;ctx.fillRect(p.x,p.y,p.size,p.size);}ctx.globalAlpha=1;}

function drawHUD(){
  const p=game.player,e=game.enemy; if(!p||!e)return;
  ctx.fillStyle='rgba(0,0,0,.46)';ctx.fillRect(0,0,W,94);
  const barY=31,barW=430,barH=24;
  healthBar(45,barY,barW,barH,p.health,false,p.data.primary);healthBar(W-45-barW,barY,barW,barH,e.health,true,e.data.primary);
  text(p.data.name,48,17,22,'left','#f4eee2');text(e.data.name,W-48,17,22,'right','#f4eee2');
  text(String(Math.ceil(game.timer)),W/2,44,42,'center','#f5e7b5');
  for(let i=0;i<2;i++){circle(55+i*25,70,7,i<game.playerWins?'#d29b31':'#332a20');circle(W-55-i*25,70,7,i<game.enemyWins?'#d29b31':'#332a20');}
  ctx.fillStyle='#151515';ctx.fillRect(48,66,180,7);ctx.fillStyle='#d4a72c';ctx.fillRect(48,66,180*(p.runMeter/100),7);
  if(p.combo>=2&&p.comboT>0){text(`${p.combo} HITS`,120,118,30,'center','#ffd46b');}
}
function healthBar(x,y,w,h,v,reverse,color){roundRect(x,y,w,h,4,'#171719','#777');const fill=(w-6)*clamp(v/100,0,1);ctx.fillStyle=color;if(reverse)ctx.fillRect(x+w-3-fill,y+3,fill,h-6);else ctx.fillRect(x+3,y+3,fill,h-6);ctx.fillStyle='rgba(255,255,255,.16)';ctx.fillRect(x+3,y+3,w-6,4);}

function drawTitle(){
  stageBackground(stages[4],game.t*.25);ctx.fillStyle='rgba(0,0,0,.52)';ctx.fillRect(0,0,W,H);
  const pulse=1+Math.sin(game.t*2)*.02;ctx.save();ctx.translate(W/2,245);ctx.scale(pulse,pulse);text('ULTIMATE',0,-65,58,'center','#ddd2bd');text('MORTAL KOMBAT 3',0,5,104,'center','#b91f1f');text('HD FAN REMAKE',0,82,42,'center','#e2c678');ctx.restore();
  text('PRIVATE / PERSONAL-USE BUILD',W/2,390,22,'center','#8f8f91','Arial');
  if((game.t*2|0)%2===0)text('PRESS ENTER / TAP ATTACK',W/2,510,32,'center','#fff0c7');
  text('Original clean-room engine • no ripped game assets included',W/2,660,18,'center','#8b8b8e','Arial');
}

function drawSelect(){
  ctx.fillStyle='#09090b';ctx.fillRect(0,0,W,H);const g=ctx.createRadialGradient(W/2,260,20,W/2,260,600);g.addColorStop(0,'#3c0f10');g.addColorStop(1,'#050506');ctx.fillStyle=g;ctx.fillRect(0,0,W,H);
  text('CHOOSE YOUR FIGHTER',W/2,52,42,'center','#e4d7be');
  const cols=7,cellW=112,cellH=105,startX=(W-cols*cellW)/2,startY=108;
  roster.forEach((d,i)=>{const col=i%cols,row=(i/cols)|0,x=startX+col*cellW,y=startY+row*cellH;const sel=i===game.select;roundRect(x+5,y+5,cellW-10,cellH-10,7,sel?'#d8b253':'#171719',sel?'#fff0af':'#3a3a3d');roundRect(x+11,y+11,cellW-22,cellH-35,4,d.dark);ctx.fillStyle=d.primary;ctx.fillRect(x+22,y+25,cellW-44,cellH-62);ctx.fillStyle='#0d0d0f';ctx.beginPath();ctx.arc(x+cellW/2,y+37,17,0,TAU);ctx.fill();text(d.name,x+cellW/2,y+cellH-12,11,'center',sel?'#180d05':'#ddd','Arial');});
  const d=roster[game.select];text(d.name,W/2,585,44,'center',d.primary);text('ENTER / PUNCH = ARCADE TOWER',W/2,642,22,'center','#d6c59f','Arial');
}

function drawTower(){
  ctx.fillStyle='#070708';ctx.fillRect(0,0,W,H);stageBackground(stages[(game.towerStep||0)%stages.length],game.t*.2);ctx.fillStyle='rgba(0,0,0,.62)';ctx.fillRect(0,0,W,H);
  text('ARCADE TOWER',W/2,58,46,'center','#e6d5b1');
  const step=game.towerStep;for(let i=0;i<game.tower.length;i++){const x=W/2+(i%2?135:-135),y=610-i*58;const idx=game.tower[i],active=i===step,done=i<step;roundRect(x-125,y-23,250,46,8,done?'#1b351f':active?'#7d1818':'#171719',active?'#f0c16e':'#47474a');text(roster[idx].name,x,y,18,'center',done?'#72b97a':active?'#ffe3a9':'#9a9a9d','Arial');}
  text(`${game.towerStep+1} / ${game.tower.length}`,W/2,662,22,'center','#bbb','Arial');
}

function drawFight(){
  const s=stages[game.stageIndex];ctx.save(); if(game.shake>0)ctx.translate(rand(-8,8)*game.shake*4,rand(-5,5)*game.shake*4);stageBackground(s,game.t);
  for(const p of game.projectiles)drawProjectile(p);drawFighter(game.player);drawFighter(game.enemy);drawParticles();ctx.restore();drawHUD();
  text(s.name,W/2,695,16,'center','rgba(255,255,255,.45)','Arial');
  if(game.intro>0){const scale=1+clamp(game.intro-1,0,1)*.25;ctx.save();ctx.translate(W/2,300);ctx.scale(scale,scale);text(game.message,0,0,game.message==='FIGHT!'?86:64,'center',game.message==='FIGHT!'?'#e83a27':'#f2d48b');ctx.restore();}
  if(game.outro>0)text(game.message,W/2,300,110,'center','#d51e19');
  if(game.paused){ctx.fillStyle='rgba(0,0,0,.68)';ctx.fillRect(0,0,W,H);text('PAUSED',W/2,H/2,70,'center','#fff0c4');}
  if(game.flash>0){ctx.fillStyle='rgba(255,255,255,.22)';ctx.fillRect(0,0,W,H);}
}

function drawResult(ending=false){
  stageBackground(stages[ending?2:8],game.t*.2);ctx.fillStyle='rgba(0,0,0,.68)';ctx.fillRect(0,0,W,H);
  text(ending?'TOWER CONQUERED':'DEFEAT',W/2,190,78,'center',ending?'#e1b84e':'#b31f1f');
  text(roster[game.select].name,W/2,300,46,'center',roster[game.select].primary);text(`SCORE ${game.score}`,W/2,370,32,'center','#eee','Arial');
  text(ending?'YOU DEFEATED THE OUTWORLD TOWER':'THE TOWER AWAITS ANOTHER CHALLENGER',W/2,445,22,'center','#c8bea8','Arial');
  if((game.t*2|0)%2===0)text('PRESS ENTER / ATTACK',W/2,555,28,'center','#fff0c2');
}

function render(){
  ctx.setTransform(1,0,0,1,0,0);ctx.clearRect(0,0,W,H);
  if(game.state==='title')drawTitle(); else if(game.state==='select')drawSelect(); else if(game.state==='tower')drawTower(); else if(game.state==='fight')drawFight(); else if(game.state==='result')drawResult(false); else if(game.state==='ending')drawResult(true);
}

function resize(){
  const cw=innerWidth,ch=innerHeight;canvas.style.width=cw+'px';canvas.style.height=ch+'px';
  if(canvas.width!==W||canvas.height!==H){canvas.width=W;canvas.height=H;}
}
window.addEventListener('resize',resize);resize();

pauseBtn.addEventListener('click',()=>{if(game.state==='fight'){game.paused=!game.paused;sound('menu',.3);}});
document.addEventListener('visibilitychange',()=>{if(document.hidden&&game.state==='fight')game.paused=true;});
canvas.addEventListener('pointerdown',()=>{if(game.state==='title'){pressed.confirm=true;}else if(game.state==='result'||game.state==='ending'){pressed.confirm=true;}else if(game.state==='tower'){pressed.confirm=true;}});

let last=performance.now(),acc=0;const STEP=1/60;
function frame(now){
  const raw=Math.min(.05,(now-last)/1000);last=now;acc+=raw;
  while(acc>=STEP){update(STEP);acc-=STEP;}
  render();for(const k in pressed){if(k!=='punch'&&k!=='kick'&&k!=='special')pressed[k]=false;}
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);

})();
