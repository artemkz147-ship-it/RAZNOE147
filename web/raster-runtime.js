(() => {
'use strict';
const debug=window.__UMK3_DEBUG__, DATA=window.UMK3_DATA;
if(!debug||!DATA)return;
const W=1280,H=720,S=1.5,PW=1920,PH=1080,FW=360,FH=480,COLS=8,TAU=Math.PI*2;
const game=debug.game,app=document.getElementById('app'),touch=document.getElementById('touch-ui');
if(!app)return;
const old=document.getElementById('raster-game');if(old)old.remove();
const canvas=document.createElement('canvas');canvas.id='raster-game';canvas.width=PW;canvas.height=PH;canvas.setAttribute('aria-hidden','true');
Object.assign(canvas.style,{position:'absolute',inset:'0',width:'100%',height:'100%',objectFit:'contain',pointerEvents:'none',background:'#030304'});
app.insertBefore(canvas,touch||null);const ctx=canvas.getContext('2d',{alpha:false,desynchronized:true});
ctx.imageSmoothingEnabled=true;ctx.imageSmoothingQuality='high';
const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
const fighters=new Map(),portraits=new Map(),stages=new Map(),fReady=new Map(),pReady=new Map(),sReady=new Map();
let activeState='',activeFightKey='';

function img(map,ready,key,src){
  if(map.has(key))return map.get(key);
  if(typeof Image==='undefined')return null;
  const im=new Image();ready.set(key,false);
  im.onload=()=>ready.set(key,true);
  im.onerror=()=>ready.set(key,false);
  im.src=src;map.set(key,im);return im;
}
function fighterImage(d){return d?img(fighters,fReady,d.id,`assets/fighters/${encodeURIComponent(d.id)}/atlas.png`):null}
function portraitImage(d){return d?img(portraits,pReady,d.id,`assets/fighters/${encodeURIComponent(d.id)}/portrait.png`):null}
function stageImage(s){return s?img(stages,sReady,s.id,`assets/stages/${encodeURIComponent(s.id)}.png`):null}
function releaseMap(map,ready,keep=new Set()){
  for(const [key,im] of [...map]){
    if(keep.has(key))continue;
    try{im.onload=null;im.onerror=null;im.src=''}catch(_){}
    map.delete(key);ready.delete(key);
  }
}
function trimForState(state){
  if(state==='fight'){
    const ids=[game.player?.activeData?.id||game.player?.data?.id,game.enemy?.activeData?.id||game.enemy?.data?.id].filter(Boolean);
    const s=DATA.stages[game.stageIndex]||DATA.stages[0],fightKey=`${ids.join('|')}|${s?.id||''}`;
    if(state===activeState&&fightKey===activeFightKey)return;
    activeFightKey=fightKey;
    releaseMap(fighters,fReady,new Set(ids));releaseMap(stages,sReady,new Set(s?[s.id]:[]));releaseMap(portraits,pReady,new Set());
    return;
  }
  if(state===activeState)return;
  activeState=state;activeFightKey='';
  if(state==='select'){
    releaseMap(fighters,fReady,new Set());releaseMap(stages,sReady,new Set());
  }else{
    releaseMap(fighters,fReady,new Set());releaseMap(portraits,pReady,new Set());
    const index=state==='title'?12:state==='tower'?9:state==='ending'?15:6;
    const s=DATA.stages[index];releaseMap(stages,sReady,new Set(s?[s.id]:[]));
  }
}

function text(s,x,y,size,align='center',fill='#fff',font='Impact'){ctx.save();ctx.font=`${size}px ${font},Arial,sans-serif`;ctx.textAlign=align;ctx.textBaseline='middle';ctx.fillStyle=fill;ctx.shadowColor='rgba(0,0,0,.95)';ctx.shadowBlur=Math.max(4,size*.12);ctx.shadowOffsetY=3;ctx.fillText(String(s),x,y);ctx.restore()}
function rr(x,y,w,h,r,fill,stroke,lw=1){ctx.beginPath();if(ctx.roundRect)ctx.roundRect(x,y,w,h,r);else ctx.rect(x,y,w,h);if(fill){ctx.fillStyle=fill;ctx.fill()}if(stroke){ctx.strokeStyle=stroke;ctx.lineWidth=lw;ctx.stroke()}}
const A={idle:[0,4],walk:[4,6],crouch:[10,3],jump:[13,4],LP:[17,3],HP:[20,3],LK:[23,3],HK:[26,3],block:[29,3],hit:[32,3],special:[35,5],run:[40,4],win:[44,4]};
function seq(name,t,fps=8){const [b,n]=A[name];return b+((Math.max(0,t)*fps|0)%n)}
function attackSeq(name,f){const [b,n]=A[name]||A.special;const a=f.attack,def=a?.def||{},total=Math.max(.001,(def.startup||.08)+(def.active||.1)+(def.recovery||.2));return b+Math.min(n-1,Math.floor(clamp(f.attackT/total,0,.999)*n))}
function frameIndex(f,t){
  if(!f)return 0;if(f.dead)return A.hit[0]+2;if(f.finishPose>0)return seq('win',f.stateT||t,6);
  if(f.state==='dizzy'||f.state==='hit'||f.state==='frozen'||f.stun>0)return seq('hit',f.stateT||t,10);
  if(f.blocking||f.state==='block')return seq('block',f.stateT||t,7);
  if(f.attack){
    if(f.attack.kind==='special')return attackSeq('special',f);
    const id=f.attack.id;
    if(id==='LP'||id==='jumpPunch')return attackSeq('LP',f);
    if(id==='HP'||id==='uppercut'||id==='grab')return attackSeq('HP',f);
    if(id==='LK'||id==='sweep')return attackSeq('LK',f);
    if(id==='HK'||id==='jumpKick')return attackSeq('HK',f);
    return attackSeq('special',f);
  }
  if(f.air){const [b,n]=A.jump,p=clamp((f.vy+800)/1600,0,1);return b+Math.min(n-1,Math.floor(p*n))}
  if(f.crouch)return seq('crouch',f.stateT||t,5);
  if(f.state==='run')return seq('run',f.stateT||t,13);
  if(f.state==='walk')return seq('walk',f.stateT||t,9);
  return seq('idle',f.stateT||t,6);
}
function drawFighter(f,t){
  if(!f||f.invisible>50)return false;
  const d=f.activeData||f.data,im=fighterImage(d);if(!im||!fReady.get(d.id))return false;
  const idx=frameIndex(f,t),sx=(idx%COLS)*FW,sy=((idx/COLS)|0)*FH;
  const baseH=d.style==='motaro'?350:d.style==='shaokahn'?330:306,baseW=baseH*.75;
  ctx.save();ctx.globalAlpha=f.invisible>0?.28:1;ctx.translate(f.x,f.y);ctx.scale(f.face||1,1);
  ctx.fillStyle='rgba(0,0,0,.52)';ctx.beginPath();ctx.ellipse(0,5,d.style==='motaro'?100:62,15,0,0,TAU);ctx.fill();
  if(f.flash>0){ctx.shadowColor='#fff';ctx.shadowBlur=20}
  ctx.drawImage(im,sx,sy,FW,FH,-baseW/2,-baseH+9,baseW,baseH);ctx.restore();return true;
}
function drawStage(s){const im=stageImage(s);if(im&&sReady.get(s.id)){ctx.drawImage(im,0,0,W,H);return true}ctx.fillStyle=s&&s.sky?s.sky[0]:'#050506';ctx.fillRect(0,0,W,H);return false}
function projectileColor(t=''){if(t.includes('freeze'))return '#85ddff';if(t.includes('acid'))return '#64e444';if(t.includes('purple'))return '#c66cff';if(t.includes('soul'))return '#53f36f';if(t.includes('fire')||t.includes('skull')||t.includes('heat'))return '#ff7d25';if(t==='net')return '#f3cd32';if(t.includes('water'))return '#59c8ff';return '#f2f2f2'}
function drawHazards(){
  for(const h of game.hazards||[]){
    ctx.save();
    if(h.type==='clone'){
      ctx.globalAlpha=.48;ctx.fillStyle='#72dcff';ctx.shadowColor='#72dcff';ctx.shadowBlur=20;rr(h.x-28,420,56,174,12,'rgba(80,205,255,.26)','#b9f1ff',3);
    }else if(h.type==='iceShower'||h.type==='groundFreeze'){
      ctx.fillStyle='rgba(98,220,255,.55)';ctx.shadowColor='#88e6ff';ctx.shadowBlur=24;ctx.beginPath();ctx.ellipse(h.x,586,78,18,0,0,TAU);ctx.fill();
      for(let i=0;i<5;i++){ctx.beginPath();ctx.moveTo(h.x-42+i*21,586);ctx.lineTo(h.x-31+i*21,525-(i%2)*22);ctx.lineTo(h.x-17+i*21,586);ctx.closePath();ctx.fill()}
    }else{
      const c=h.type==='groundBlade'?'#d7d9db':h.type==='groundFire'?'#ff5b22':'#ffb52a';ctx.fillStyle=c;ctx.shadowColor=c;ctx.shadowBlur=22;
      const r=24+Math.sin((h.t||0)*14)*7;ctx.beginPath();ctx.arc(h.x,582,r,0,TAU);ctx.fill();
    }
    ctx.restore();
  }
}
function drawProjectiles(){
  for(const p of game.projectiles||[]){
    if((p.t||0)<0)continue;const c=projectileColor(p.type||'');
    ctx.save();ctx.translate(p.x,p.y);ctx.fillStyle=c;ctx.strokeStyle=c;ctx.shadowColor=c;ctx.shadowBlur=24;
    if(p.type==='spear'){
      ctx.lineWidth=4;ctx.beginPath();ctx.moveTo(-34,0);ctx.lineTo(24,0);ctx.stroke();ctx.beginPath();ctx.moveTo(28,0);ctx.lineTo(10,-9);ctx.lineTo(10,9);ctx.closePath();ctx.fill();
    }else if((p.type||'').includes('missile')){
      rr(-22,-8,44,16,5,'#a3a7aa','#202226',2);ctx.fillStyle='#ff7a20';ctx.fillRect(-33,-5,12,10);
    }else if(p.type==='fan'||(p.type||'').includes('boomerang')){
      ctx.rotate((p.t||0)*12);for(let i=0;i<4;i++){ctx.rotate(TAU/4);ctx.beginPath();ctx.moveTo(0,0);ctx.lineTo(34,-6);ctx.lineTo(28,8);ctx.closePath();ctx.fill()}
    }else if(['knife','sai','baton','hat'].includes(p.type)){
      ctx.rotate((p.t||0)*10);ctx.fillRect(-30,-4,60,8);
    }else{
      const r=p.r||12;ctx.beginPath();ctx.arc(0,0,r,0,TAU);ctx.fill();ctx.globalAlpha=.32;ctx.beginPath();ctx.arc(0,0,r*2.1,0,TAU);ctx.fill();
    }
    ctx.restore();
  }
}
function drawParticles(){
  for(const p of game.particles||[]){
    const life=Math.max(.001,p.life||1),a=clamp(1-(p.t||0)/life,0,1);if(a<=0)continue;
    ctx.save();ctx.globalAlpha=a;ctx.fillStyle=p.c||'#fff';ctx.shadowColor=p.c||'#fff';ctx.shadowBlur=(p.r||2)*1.7;
    ctx.beginPath();ctx.arc(p.x,p.y,Math.max(.6,(p.r||2)*a),0,TAU);ctx.fill();ctx.restore();
  }
}
function drawCombatFx(){drawProjectiles();drawParticles();if(game.flash>0){ctx.save();ctx.globalAlpha=clamp(game.flash,0,.72);ctx.fillStyle='#fff';ctx.fillRect(0,0,W,H);ctx.restore()}if(game.stageFlash>0){ctx.save();ctx.globalAlpha=clamp(game.stageFlash*.55,0,.4);ctx.fillStyle='#ff3b1f';ctx.fillRect(0,0,W,H);ctx.restore()}}
function healthBar(x,y,w,h,v,flip,color){rr(x,y,w,h,5,'#111216','#d4c59c',2);const q=clamp(v,0,1),ww=(w-8)*q;ctx.fillStyle=q<.25?'#c52828':'#c8b63a';if(flip)ctx.fillRect(x+w-4-ww,y+4,ww,h-8);else ctx.fillRect(x+4,y+4,ww,h-8);ctx.globalAlpha=.22;ctx.fillStyle=color;if(flip)ctx.fillRect(x+w-4-ww,y+4,ww,5);else ctx.fillRect(x+4,y+4,ww,5);ctx.globalAlpha=1}
function hud(){
  const p=game.player,e=game.enemy;if(!p||!e)return;const mh=f=>f.data.boss?(f.data.id==='shao-kahn'?135:120):100;
  text(p.data.name,58,35,25,'left','#f4ebd2');text(e.data.name,W-58,35,25,'right','#f4ebd2');healthBar(54,56,505,30,p.health/mh(p),false,p.data.primary);healthBar(W-559,56,505,30,e.health/mh(e),true,e.data.primary);text(Math.ceil(game.timer),W/2,58,43,'center','#f3e5be');
  ctx.fillStyle='rgba(0,0,0,.62)';ctx.fillRect(54,94,184,8);ctx.fillRect(W-238,94,184,8);ctx.fillStyle='#d8b63f';ctx.fillRect(54,94,184*clamp((p.runMeter||0)/100,0,1),8);const ew=184*clamp((e.runMeter||0)/100,0,1);ctx.fillRect(W-54-ew,94,ew,8);
  if(p.comboHits>1&&p.comboT>0){text(`${p.comboHits} HITS`,58,132,27,'left','#ffd55d');text(`${Math.round(p.comboDamage)}%`,58,160,18,'left','#fff','Arial')}
  if(e.comboHits>1&&e.comboT>0){text(`${e.comboHits} HITS`,W-58,132,27,'right','#ffd55d');text(`${Math.round(e.comboDamage)}%`,W-58,160,18,'right','#fff','Arial')}
  if(game.intro>0)text(game.intro>1.15?`ROUND ${game.round}`:'FIGHT!',W/2,H*.42,game.intro>1.15?58:88,'center',game.intro>1.15?'#e7ddc1':'#dc3027');
  if(game.finishT>0&&!game.finisher){const l=game.winner===p?e:p;text(l.data.gender==='female'?'FINISH HER!':'FINISH HIM!',W/2,H*.32,80,'center','#da2d24')}
  if(game.paused){ctx.fillStyle='rgba(0,0,0,.78)';ctx.fillRect(0,0,W,H);text('ПАУЗА',W/2,180,64,'center','#e5d6ae');text('Ⅱ — продолжить',W/2,285,24,'center','#f2cd67','Arial')}
}
function title(){drawStage(DATA.stages[12]);ctx.fillStyle='rgba(0,0,0,.42)';ctx.fillRect(0,0,W,H);text('ULTIMATE',W/2,388,48,'center','#e3d5ae');text('MORTAL KOMBAT 3',W/2,452,72,'center','#dc342b');text('PRERENDERED REMASTER',W/2,518,29,'center','#d8c59b');text('КОСНИСЬ ЭКРАНА',W/2,620,24,'center','#f4e8c9','Arial');text(`v${DATA.version} • OFFLINE`,W/2,674,15,'center','#9b9da2','Arial')}
function portraitCell(d,x,y,w,h){
  const im=portraitImage(d);
  if(im&&pReady.get(d.id)){ctx.save();ctx.beginPath();ctx.rect(x,y,w,h);ctx.clip();ctx.drawImage(im,x,y,w,h);ctx.restore();return true}
  const g=ctx.createLinearGradient(x,y,x+w,y+h);g.addColorStop(0,d.dark||'#18191d');g.addColorStop(1,d.primary||'#555');ctx.fillStyle=g;ctx.fillRect(x,y,w,h);ctx.globalAlpha=.42;ctx.fillStyle='#000';ctx.beginPath();ctx.arc(x+w/2,y+h*.37,Math.min(w,h)*.19,0,TAU);ctx.fill();rr(x+w*.27,y+h*.51,w*.46,h*.42,Math.min(w,h)*.08,'#000');ctx.globalAlpha=1;return false;
}
function select(){
  ctx.fillStyle='#070709';ctx.fillRect(0,0,W,H);text('CHOOSE YOUR FIGHTER',W/2,45,42,'center','#e0d2aa');const list=DATA.allPlayable,cols=7,cw=132,ch=122,sx=(W-cols*cw)/2,sy=88;
  for(let i=0;i<list.length;i++){
    const d=list[i],x=sx+(i%cols)*cw,y=sy+((i/cols)|0)*ch,sel=i===game.select;rr(x+5,y+5,cw-10,ch-10,7,sel?'#44291a':'#111216',sel?'#edbd52':'#41434a',sel?4:2);
    portraitCell(d,x+9,y+9,cw-18,ch-34);
    ctx.fillStyle='rgba(0,0,0,.62)';ctx.fillRect(x+8,y+84,cw-16,25);text(d.name,x+cw/2,y+97,12,'center',sel?'#ffe29a':'#eee','Arial');
  }
  const d=list[game.select]||list[0];text(d.name,W/2,620,37,'center',d.primary);text('ТАПНИ ПО БОЙЦУ',W/2,674,18,'center','#bdc0c4','Arial')
}
function tower(){drawStage(DATA.stages[9]);ctx.fillStyle='rgba(0,0,0,.68)';ctx.fillRect(0,0,W,H);text('KOMBAT TOWER',W/2,55,49,'center','#e6d8b2');const n=game.tower.length;for(let i=0;i<n;i++){const idx=n-1-i,d=game.tower[idx],y=116+i*50,a=idx===game.towerStep;rr(W/2-220,y-21,440,42,6,a?'#4d281a':'rgba(17,17,20,.88)',a?'#e7b34b':'#44464c',a?3:1);text(`${idx+1}. ${d.name}`,W/2,y,20,'center',a?'#ffdc88':'#d0d2d5','Arial')}text('КОСНИСЬ ЭКРАНА — БОЙ',W/2,686,17,'center','#ddd','Arial')}
function fight(){drawStage(DATA.stages[game.stageIndex]||DATA.stages[0]);drawHazards();drawFighter(game.player,game.clock);drawFighter(game.enemy,game.clock);drawCombatFx();hud();if(game.outro>0&&game.winner)text(`${game.winner.data.name} WINS`,W/2,225,51,'center','#e2d3ab')}
function simple(label,stage=6){drawStage(DATA.stages[stage]);ctx.fillStyle='rgba(0,0,0,.68)';ctx.fillRect(0,0,W,H);text(label,W/2,300,62,'center','#d32e27');text('КОСНИСЬ ЭКРАНА',W/2,400,24,'center','#ead9ae')}
let switched=false;
function render(){
  trimForState(game.state);
  ctx.setTransform(1,0,0,1,0,0);ctx.fillStyle='#030304';ctx.fillRect(0,0,PW,PH);
  const shake=game.state==='fight'&&game.shake>0?Math.min(8,game.shake*22):0,sx=shake?(Math.random()-.5)*shake:0,sy=shake?(Math.random()-.5)*shake:0;
  ctx.setTransform(S,0,0,S,sx*S,sy*S);
  if(game.state==='title')title();else if(game.state==='select')select();else if(game.state==='tower')tower();else if(game.state==='fight')fight();else if(game.state==='ending')simple('EARTHREALM IS FREE',15);else if(game.state==='gameover')simple('YOU HAVE BEEN DEFEATED',6);else{ctx.fillStyle='#050506';ctx.fillRect(0,0,W,H)}
  if(!switched){const base=document.getElementById('hd-game');if(base)base.style.opacity='0';const original=document.getElementById('game');if(original)original.style.opacity='0';switched=true}
  requestAnimationFrame(render);
}
stageImage(DATA.stages[12]);
requestAnimationFrame(render);
window.__UMK3_RASTER__={canvas,fighters,portraits,stages,fReady,pReady,sReady,drawFighter,drawStage,frameIndex,trimForState,cacheStats:()=>({fighters:fighters.size,portraits:portraits.size,stages:stages.size})};
})();
