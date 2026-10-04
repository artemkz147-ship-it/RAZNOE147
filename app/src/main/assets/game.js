const canvas=document.querySelector('#world');let ctx=canvas.getContext('2d');
let W=1280;const $=s=>document.querySelector(s),H=720,WORLD=5600,keys={},pressed={};
let paintedState='',hudCache={};
let state='menu',t=0,last=0,camera=0,shake=0,kills=0,soundOn=false,audio,checkpoint=150,toastTime=0;
const p={x:820,y:420,vx:0,vy:0,w:32,h:78,face:1,hp:100,ground:false,jumps:0,attack:0,attackCd:0,dash:0,dashCd:0,inv:0,step:0};
let enemies=[],shots=[],particles=[],orbs=[],combo=0;
const platforms=[{x:0,y:565,w:900},{x:1000,y:545,w:560},{x:1670,y:565,w:630},{x:2420,y:520,w:530},{x:3070,y:565,w:680},{x:3880,y:525,w:550},{x:4550,y:565,w:1100},{x:420,y:435,w:190,upper:true},{x:1190,y:405,w:200,upper:true},{x:1860,y:425,w:210,upper:true},{x:2630,y:390,w:185,upper:true},{x:3280,y:425,w:220,upper:true},{x:4050,y:400,w:210,upper:true},{x:4770,y:430,w:200,upper:true}];
const enemySpawns=[660,1300,1940,2180,2710,3430,4140,4820];
function rng(seed){return()=>{seed=(seed*1664525+1013904223)>>>0;return seed/4294967296}}
const rand=rng(471);const rain=Array.from({length:90},()=>({x:rand()*W,y:rand()*H,s:rand()*1.7+.5}));
function resize(){const d=Math.min(devicePixelRatio||1,2);canvas.width=innerWidth*d;canvas.height=innerHeight*d;paintedState='';}addEventListener('resize',resize);resize();
function rect(x,y,w,h,c){ctx.fillStyle=c;ctx.fillRect(x,y,w,h)}
function path(points,color,width=1){ctx.beginPath();points.forEach(([x,y],i)=>i?ctx.lineTo(x,y):ctx.moveTo(x,y));ctx.strokeStyle=color;ctx.lineWidth=width;ctx.lineCap='round';ctx.lineJoin='round';ctx.stroke()}
function poly(points,color){ctx.beginPath();points.forEach(([x,y],i)=>i?ctx.lineTo(x,y):ctx.moveTo(x,y));ctx.closePath();ctx.fillStyle=color;ctx.fill()}
function ellipse(x,y,rx,ry,color){ctx.beginPath();ctx.ellipse(x,y,rx,ry,0,0,Math.PI*2);ctx.fillStyle=color;ctx.fill()}
function glow(x,y,r,color){let g=ctx.createRadialGradient(x,y,0,x,y,r);g.addColorStop(0,color);g.addColorStop(1,'transparent');ctx.fillStyle=g;ctx.fillRect(x-r,y-r,r*2,r*2)}
function background(){let g=ctx.createLinearGradient(0,0,0,H);g.addColorStop(0,'#0e1d26');g.addColorStop(.48,'#26433f');g.addColorStop(1,'#111e20');ctx.fillStyle=g;ctx.fillRect(0,0,W,H);
const moonX=920-camera*.035;glow(moonX,169,220,'#cfe1a71a');ellipse(moonX,169,57,57,'#c6d2ab');ellipse(moonX-12,155,8,5,'#acba9555');ellipse(moonX+17,184,14,8,'#aaba9744');ellipse(moonX+23,145,7,10,'#aaba9755');
for(let layer=0;layer<3;layer++){const off=-(camera*(.07+layer*.11))%1900;ctx.drawImage(skylineTextures[layer],off,0);ctx.drawImage(skylineTextures[layer],off+1900,0);}
// fog banks, crane and telephone wires
for(let i=0;i<6;i++){let yy=300+i*52;let fog=ctx.createLinearGradient(0,yy,0,yy+75);fog.addColorStop(0,'transparent');fog.addColorStop(.5,'#70998a0b');fog.addColorStop(1,'transparent');ctx.fillStyle=fog;ctx.fillRect(0,yy,W,75)}
const cx=600-camera*.18;path([[cx,470],[cx,232],[cx+230,232],[cx+40,203],[cx,232],[cx+170,232],[cx+170,319]],'#101f2488',3);path([[cx-70,470],[cx+16,250],[cx+45,470]],'#13262788',3);
ctx.strokeStyle='#0c1c2180';ctx.lineWidth=2;for(let i=0;i<3;i++){ctx.beginPath();ctx.moveTo(-50,315+i*12);ctx.bezierCurveTo(480,420+i*12,720,360+i*12,1350,287+i*12);ctx.stroke()}
for(let i=0;i<10;i++){let bx=((i*183+t*(3+i%3))%1500)-100,by=160+Math.sin(i*2)*65;path([[bx-4,by],[bx,by+3],[bx+4,by]],'#0c2025',1.5)}
}
function drawSkyline(layer){const rr=rng(330+layer*333);let par=.07+layer*.11;const base=520+layer*35;let x=-200;for(let i=0;i<44;i++){let w=35+rr()*77,h=70+rr()*(195+layer*35),yy=base-h;rect(x,yy,w,h,['#253b3d','#1c3233','#182b2c'][layer]);if(rr()>.6){rect(x+w*.2,yy-15,w*.6,15,['#253b3d','#1c3233','#182b2c'][layer]);path([[x+w*.5,yy-15],[x+w*.5,yy-50]],'#203739',2)}for(let wy=yy+15;wy<base-10;wy+=19)for(let wx=x+9;wx<x+w-6;wx+=14){let lit=rr()>.8;rect(wx,wy,4,7,lit?(layer===2?'#b6a06c55':'#779d853b'):'#101f2533')}if(layer===2){path([[x,yy],[x+w,yy]],'#66827033',2);if(i%7===0){glow(x+20,yy+40,60,'#e2b37112');rect(x+14,yy+26,4,15,'#b39860aa')}}x+=w+rr()*20;}}
function terrain(){for(const a of platforms){const x=a.x-camera;if(x>W+100||x+a.w<-100)continue;if(a.upper){rect(x,a.y,a.w,14,'#283d37');rect(x,a.y,a.w,3,'#729076');rect(x+12,a.y+14,8,55,'#142621');rect(x+a.w-20,a.y+14,8,55,'#142621');path([[x+12,a.y+60],[x+a.w-20,a.y+14]],'#253d31',3);for(let i=15;i<a.w;i+=26)rect(x+i,a.y+5,14,2,'#455d49');continue}
let g=ctx.createLinearGradient(0,a.y,0,H);g.addColorStop(0,'#253830');g.addColorStop(.15,'#172622');g.addColorStop(1,'#0b1519');ctx.fillStyle=g;ctx.fillRect(x,a.y,a.w,H-a.y+200);rect(x,a.y,a.w,5,'#69866c');rect(x,a.y+5,a.w,8,'#33483a');rect(x,a.y+18,a.w,4,'#0b1d1b');
const rr=rng(a.x+63);for(let i=0;i<a.w;i+=42){let xx=x+i;path([[xx,a.y+35],[xx+12,a.y+50],[xx+5,a.y+78],[xx+23,a.y+100]],'#50634b35',1);rect(xx+6,a.y+43,20,2,'#80906713');}
for(let i=0;i<a.w;i+=17){let yy=a.y-rr()*8;path([[x+i,a.y],[x+i+2,yy-4],[x+i+5,a.y],[x+i+10,yy]],'#82936b77',1)}
if(a.x>0){rect(x+15,a.y+25,30,95,'#233a32');path([[x+15,a.y+28],[x+45,a.y+28],[x+45,a.y+115]],'#57684e44',3)}
// pipes and boarded windows
for(let i=70;i<a.w-30;i+=230){rect(x+i,a.y+57,100,100,'#0b171b');rect(x+i+5,a.y+63,90,90,'#132624');rect(x+i+49,a.y+60,3,95,'#405245');path([[x+i+5,a.y+83],[x+i+95,a.y+130]],'#4b554144',8);path([[x+i+4,a.y+145],[x+i+94,a.y+88]],'#414e3b66',6)}
}
// props along rooftops
for(const b of [{x:330,y:565,w:58,h:53},{x:785,y:565,w:75,h:70},{x:1490,y:545,w:40,h:45},{x:1780,y:565,w:52,h:48},{x:2900,y:520,w:35,h:60},{x:3580,y:565,w:65,h:44},{x:4400,y:525,w:45,h:50}]){let x=b.x-camera,y=b.y-b.h;if(x<-150||x>W+100)continue;rect(x,y,b.w,b.h,'#314237');rect(x,y,b.w,4,'#718263');rect(x+7,y+10,b.w-14,b.h-18,'#1c2b28');for(let i=13;i<b.h-6;i+=7)rect(x+10,y+i,b.w-20,2,'#4a5d4a');path([[x,y],[x+b.w,y],[x+b.w,b.y]],'#9ca67744',1)}
// quarantine fence, signage
for(let i=0;i<6;i++){let x=540+i*50-camera;path([[x,565],[x,484]],'#31473c',3);for(let j=0;j<4;j++){path([[x,493+j*16],[x+48,520+j*16]],'#65736533',1);path([[x,550-j*16],[x+48,522-j*16]],'#65736533',1)}}
let sign=692-camera;rect(sign,480,90,30,'#aea374');rect(sign+3,483,84,24,'#242e28');ctx.fillStyle='#c2b482';ctx.font='bold 9px monospace';ctx.fillText('QUARANTINE',sign+12,500);
// street lamp
for(let lx of [960,2370,3830]){let x=lx-camera;if(x<-200||x>W+200)continue;path([[x,730],[x,357],[x-15,343],[x-80,343]],'#182822',6);rect(x-93,341,28,5,'#d9c58d');glow(x-79,352,100,'#e8c38425');poly([[x-90,352],[x-170,620],[x+8,620],[x-68,352]],'#e8c38404')}
// exit beacon
let ex=5350-camera;glow(ex,467,150,'#b9ee6933');path([[ex-28,565],[ex-28,452],[ex+28,452],[ex+28,565]],'#91ba7066',4);rect(ex-20,457,40,106,'#a8e86713');for(let i=0;i<5;i++)rect(ex-21,466+i*20,42,2,'#d3ff9666');ctx.fillStyle='#c7ed9b';ctx.font='10px monospace';ctx.textAlign='center';ctx.fillText('ВЫХОД',ex,434);ctx.textAlign='left';
}
function character(a,human=false,scale=1){let x=a.x-camera,y=a.y;ctx.save();ctx.translate(x,y);ctx.scale(a.face*scale,scale);const moving=Math.abs(a.vx)>20,phase=a.step||t*3;const walk=moving?Math.sin(phase)*17:Math.sin(t*2)*1.5;let bob=moving?Math.abs(Math.cos(phase))*3:Math.sin(t*2.5)*1.3;let air=!a.ground;const attacking=a.attack>0, dash=a.dash>0;let lean=dash?.3:moving?.08:0;ctx.translate(0,bob);ctx.rotate(lean);
ellipse(0,2,23,5,'#00000040');if(!human)glow(0,-45,65,'#b1f87612');
// feet, segmented limbs
let leg1=air?-15:walk,leg2=air?18:-walk;path([[-5,-31],[-8+leg1*.35,-17],[leg1,0]],human?'#263938':'#344035',11);path([[6,-31],[7+leg2*.35,-15],[leg2+4,0]],human?'#3f4a42':'#4d5040',12);path([[leg1-5,0],[leg1+9,0]],'#0c1a1b',8);path([[leg2-3,0],[leg2+12,0]],'#14221e',9);
// back arm
path([[-6,-56],[-17-walk*.4,-40],[-13-walk*.5,-25]],human?'#485449':'#667450',8);ellipse(-13-walk*.5,-24,4,6,human?'#acaa86':'#a0b67a');
// coat/torn shirt
poly([[-13,-60],[9,-61],[16,-42],[13,-30],[6,-33],[1,-28],[-8,-34],[-13,-31],[-16,-47]],human?'#53604e':'#526953');poly([[-13,-57],[-6,-60],[-4,-36],[-12,-33]],human?'#3a4942':'#293f38');path([[2,-56],[3,-42],[10,-38]],'#9fa27b55',1.5);poly([[7,-44],[13,-44],[11,-37],[7,-38]],'#162823');if(human){rect(-10,-51,24,14,'#334439');rect(-3,-50,4,11,'#78816a');rect(-13,-35,29,4,'#182d2c')}
// neck/head
rect(-5,-68,10,9,human?'#939d7d':'#82985f');ctx.save();ctx.translate(0,-73);ctx.rotate(attacking?-.12:Math.sin(t*2)*.035);poly([[-10,-13],[6,-15],[13,-9],[12,5],[7,12],[-5,10],[-12,1]],human?'#a4ab8b':'#93ae6b');poly([[-10,-11],[-4,-13],[-3,8],[-8,5],[-12,0]],human?'#77896f':'#516c4b');if(human){poly([[-13,-12],[-8,-19],[9,-18],[15,-10],[15,-4],[-13,-4]],'#293d3d');path([[-10,-13],[10,-14]],'#657761',2);rect(9,-3,6,4,'#d8b981')}else{poly([[-10,-13],[-6,-19],[4,-17],[9,-13],[0,-13],[-4,-7]],'#203831');glow(7,-1,15,'#b7ff8944');rect(3,-3,8,3,'#d5ffac');rect(7,7,5,2,'#314c37');rect(8,7,2,2,'#e6e9b8');path([[-7,-3],[-4,2],[-8,5]],'#3d6141',1)}ctx.restore();
// front arm / attack / rifle
const swing=attacking?Math.sin((.3-a.attack)*12):0;
if(human){path([[7,-55],[22,-41],[30,-48]],'#6d7860',9);ellipse(30,-48,4,4,'#aaab87');rect(14,-50,32,5,'#101e23');rect(43,-49,13,3,'#263a3b');rect(21,-46,9,9,'#172428');path([[16,-48],[5,-43]],'#17262b',4)}else{let ax=attacking?35+swing*12:18+walk*.6,ay=attacking?-48+swing*14:-29;path([[8,-55],[22,-42],[ax,ay]],'#7b8e5b',9);ellipse(ax,ay,5,7,'#b0c488');for(let i=0;i<3;i++)path([[ax+3,ay-3+i*3],[ax+11,ay-6+i*4]],'#d6deb0',1.5);if(attacking){ctx.strokeStyle='#d5ffaa';ctx.shadowColor='#baff86';ctx.shadowBlur=15;ctx.lineWidth=2;for(let i=0;i<3;i++){ctx.beginPath();ctx.arc(7,-48,48+i*6,-.75+swing*.3,.9+swing*.3);ctx.stroke()}ctx.shadowBlur=0}}
ctx.restore();if(human&&a.hp<3){rect(x-16,y-105,32,3,'#313d32');rect(x-16,y-105,32*a.hp/3,3,'#d6ad73')}
}
function effects(){for(let o of orbs){let x=o.x-camera,y=o.y+Math.sin(t*3+o.x)*5;glow(x,y,28,'#b4f57430');ellipse(x,y,4,6,'#d0f99b');path([[x-9,y],[x+9,y]],'#d2fbaa66',1)}for(let b of shots){path([[b.x-camera,b.y],[b.x-camera-b.vx*.025,b.y]],'#ffd796',2);glow(b.x-camera,b.y,12,'#f4b26335')}for(let q of particles){ctx.globalAlpha=Math.min(1,q.life*2);if(q.type==='ring'){ctx.strokeStyle=q.c;ctx.lineWidth=2;ctx.beginPath();ctx.arc(q.x-camera,q.y,(1-q.life)*55,0,Math.PI*2);ctx.stroke()}else rect(q.x-camera,q.y,q.size,q.size,q.c);ctx.globalAlpha=1}
for(let r of rain){let x=(r.x-t*50*r.s)%W;if(x<0)x+=W;let y=(r.y+t*260*r.s)%H;path([[x,y],[x-4*r.s,y+16*r.s]],'#b2cec017',.7)}
// foreground weeds
for(let i=0;i<30;i++){let x=i*57-(camera*.75)%57,yy=H+8;path([[x,yy],[x+Math.sin(t+i)*3-4,yy-25-i%4*7]],'#0a181a',3)}
}
function render(){ctx.setTransform(canvas.width/W,0,0,canvas.height/H,0,0);ctx.save();if(shake>0)ctx.translate((Math.random()-.5)*shake,(Math.random()-.5)*shake*.7);background();for(let i=0;i<terrainTextures.length;i++){let x=i*1400-camera;if(x<W&&x+1400>0)ctx.drawImage(terrainTextures[i],x,0);}for(let e of enemies)if(e.hp>0&&e.x-camera>-100&&e.x-camera<W+100)character(e,true);if(state==='menu'){p.y=565;p.x=825;p.ground=true;p.vx=0;character(p,false,1.25);glow(830,500,180,'#b5e97a0d')}else{if(p.inv<=0||Math.floor(t*17)%2===0)character(p);if(p.dash>0)glow(p.x-camera,p.y-45,90,'#b7ff8d44')}effects();ctx.restore();if(state==='menu'){let g=ctx.createLinearGradient(0,0,W,0);g.addColorStop(0,'#09191bd9');g.addColorStop(.43,'#0b1a1d85');g.addColorStop(.72,'transparent');ctx.fillStyle=g;ctx.fillRect(0,0,W,H)} }
function burst(x,y,c,n=16){for(let i=0;i<n;i++){let ang=Math.random()*Math.PI*2,sp=40+Math.random()*200;particles.push({x,y,vx:Math.cos(ang)*sp,vy:Math.sin(ang)*sp,life:.3+Math.random()*.5,c,size:2+Math.random()*3})}}
function sfx(freq=130,dur=.1,type='sawtooth',vol=.03){if(!soundOn)return;if(!audio)audio=new(window.AudioContext||window.webkitAudioContext)();audio.resume();let osc=audio.createOscillator(),g=audio.createGain();osc.type=type;osc.frequency.setValueAtTime(freq,audio.currentTime);osc.frequency.exponentialRampToValueAtTime(freq*.35,audio.currentTime+dur);g.gain.setValueAtTime(vol,audio.currentTime);g.gain.exponentialRampToValueAtTime(.001,audio.currentTime+dur);osc.connect(g);g.connect(audio.destination);osc.start();osc.stop(audio.currentTime+dur)}
function toast(text){$('#toast').textContent=text;$('#toast').style.opacity=1;toastTime=2.8}
function updateHUD(){const hp=Math.ceil(p.hp),ready=p.dashCd<=0;if(hudCache.hp!==hp){$('#health').style.width=p.hp+'%';$('#hp').textContent=hp;hudCache.hp=hp}if(hudCache.kills!==kills){$('#kills').innerHTML=String(kills).padStart(2,'0')+'<span>/ 08</span>';hudCache.kills=kills}if(hudCache.ready!==ready){$('#dash').textContent=ready?'ГОТОВ':'ПЕРЕЗАРЯДКА';hudCache.ready=ready}}
function reset(){Object.assign(p,{x:150,y:565,vx:0,vy:0,hp:100,ground:true,jumps:0,face:1,attack:0,attackCd:0,dash:0,dashCd:0,inv:0});checkpoint=150;kills=0;camera=0;particles=[];shots=[];enemies=enemySpawns.map((x,i)=>({x,y:platforms.find(a=>!a.upper&&x>=a.x&&x<a.x+a.w).y,vx:0,vy:0,hp:3,w:30,h:85,face:-1,ground:true,step:i,shootCd:1.2+i*.2,hit:0,home:x}));orbs=platforms.filter(a=>a.upper).map(a=>({x:a.x+a.w/2,y:a.y-35}));state='play';$('#menu').classList.add('hidden');$('#hud').classList.remove('hidden');$('#objective').classList.remove('hidden');$('#overlay').classList.add('hidden');document.body.classList.add('playing');updateHUD();toast('ДВОЙНОЙ ПРЫЖОК · SPACE / SPACE');}
function overlay(title,text,action,mode){state=mode;$('#overlay-title').textContent=title;$('#overlay-text').textContent=text;$('#resume span').textContent=action;$('#overlay').classList.remove('hidden')}
function hurt(amount){if(p.inv>0||p.dash>0||state!=='play')return;p.hp=Math.max(0,p.hp-amount);p.inv=1.2;shake=8;burst(p.x,p.y-44,'#b8d782',12);sfx(70,.18);if(p.hp<=0)overlay('ТЬМА ЗОВЁТ','Ты пал, но мёртвые умеют возвращаться. Попробуй ещё раз.','ВОССТАТЬ','dead');updateHUD()}
function physics(a,dt){let oldY=a.y;a.vy+=1300*dt;a.x+=a.vx*dt;a.y+=a.vy*dt;a.ground=false;for(let b of platforms){if(a.vy>=0&&oldY<=b.y+2&&a.y>=b.y&&a.x+a.w/2>b.x&&a.x-a.w/2<b.x+b.w){a.y=b.y;a.vy=0;a.ground=true;if(a===p)a.jumps=0;break}}a.x=Math.max(18,Math.min(WORLD-20,a.x))}
function update(dt){shake=Math.max(0,shake-dt*30);if(toastTime>0){toastTime-=dt;if(toastTime<=0)$('#toast').style.opacity=0}for(let q of particles){q.life-=dt;q.x+=q.vx*dt;q.y+=q.vy*dt;q.vy+=200*dt}particles=particles.filter(q=>q.life>0);if(state!=='play')return;
p.attack=Math.max(0,p.attack-dt);p.attackCd=Math.max(0,p.attackCd-dt);p.dashCd=Math.max(0,p.dashCd-dt);p.inv=Math.max(0,p.inv-dt);p.dash=Math.max(0,p.dash-dt);
let dir=(keys.d||keys.ArrowRight?1:0)-(keys.a||keys.ArrowLeft?1:0);if(dir)p.face=dir;p.vx=p.dash>0?p.face*780:dir*260;p.step+=dt*(Math.abs(p.vx)>0?12:2);
if((pressed[' ']||pressed.w||pressed.ArrowUp)&&p.jumps<2){p.vy=p.jumps===0?-520:-465;p.jumps++;p.ground=false;burst(p.x,p.y,'#a5c687',8);sfx(p.jumps===1?250:370,.11,'triangle');}
if(pressed.k&&p.dashCd===0){p.dash=.19;p.dashCd=1.35;p.inv=Math.max(p.inv,.2);p.vy=-35;burst(p.x,p.y-40,'#c0f47a',12);sfx(180,.16,'triangle')}
if((pressed.j||pressed.f)&&p.attackCd===0){p.attack=.28;p.attackCd=.38;sfx(160,.09);for(let e of enemies){if(e.hp>0&&Math.abs(e.x-p.x)<94&&Math.abs(e.y-p.y)<78&&(e.x-p.x)*p.face>-18){e.hp--;e.x+=p.face*18;e.hit=.25;shake=4;burst(e.x,e.y-50,'#d5bf86',14);if(e.hp<=0){kills++;p.hp=Math.min(100,p.hp+12);burst(e.x,e.y-45,'#c3ed8b',30);particles.push({x:e.x,y:e.y-40,vx:0,vy:0,life:1,type:'ring',c:'#ccf5a0'});sfx(80,.22);toast(kills===8?'ПАТРУЛЬ УНИЧТОЖЕН · НАЙДИ МАЯК':'ПОГЛОЩЕНИЕ +12 ЖИЗНИ');}}}}
physics(p,dt);if(p.y>H+100){p.x=checkpoint;p.y=400;p.vy=0;hurt(20);camera=Math.max(0,p.x-400);toast('ОСТОРОЖНО: ПРОПАСТЬ')}
if(p.x>1665&&checkpoint<1675&&p.ground){checkpoint=1730;toast('КОНТРОЛЬНАЯ ТОЧКА')};if(p.x>3060&&checkpoint<3070&&p.ground){checkpoint=3120;toast('КОНТРОЛЬНАЯ ТОЧКА')}
for(let e of enemies){if(e.hp<=0)continue;e.shootCd-=dt;const dx=p.x-e.x;e.face=dx>=0?1:-1;let floor=platforms.find(b=>!b.upper&&e.home>=b.x&&e.home<b.x+b.w);e.vx=Math.abs(dx)<500&&Math.abs(dx)>145?e.face*48:Math.sin(t+e.home)*18;e.x=Math.max(floor.x+28,Math.min(floor.x+floor.w-28,e.x+e.vx*dt));e.step+=dt*5;if(Math.abs(dx)<520&&Math.abs(p.y-e.y)<130&&e.shootCd<=0){e.shootCd=1.7+Math.random()*.7;shots.push({x:e.x+e.face*50,y:e.y-48,vx:e.face*350,life:2.3});burst(e.x+e.face*55,e.y-48,'#ffce83',4);sfx(240,.04,'square',.012)}if(Math.abs(dx)<34&&Math.abs(p.y-e.y)<68)hurt(8)}
for(let b of shots){b.x+=b.vx*dt;b.life-=dt;if(Math.abs(b.x-p.x)<22&&b.y>p.y-p.h&&b.y<p.y){hurt(9);b.life=0}}shots=shots.filter(b=>b.life>0);
for(let i=orbs.length-1;i>=0;i--){let o=orbs[i];if(Math.abs(o.x-p.x)<27&&Math.abs(o.y-(p.y-40))<55){p.hp=Math.min(100,p.hp+22);burst(o.x,o.y,'#c7ff92',22);orbs.splice(i,1);sfx(600,.2,'sine');toast('ЭНЕРГИЯ +22 ЖИЗНИ')}}
camera+=(Math.max(0,Math.min(WORLD-W,p.x-W*.36))-camera)*Math.min(1,dt*5);if(p.x>5300&&p.ground){overlay('ТЫ ВЫЖИЛ.',`Карантин позади. Поглощено патрульных: ${kills} из 8. Ночь принадлежит тебе.`,'ИГРАТЬ СНОВА','win');sfx(470,.5,'sine')}updateHUD();}
function frame(now){let dt=Math.min((now-last)/1000||.016,.033);last=now;if(state==='play'||state==='menu'){t+=dt;update(dt);render();paintedState=state}else if(paintedState!==state){render();paintedState=state}for(let k in pressed)delete pressed[k];requestAnimationFrame(frame)}
addEventListener('keydown',e=>{let k=e.key.length===1?e.key.toLowerCase():e.key;const map={'ф':'a','в':'d','ц':'w','о':'j','л':'k'};k=map[k]||k;if([' ','ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(k))e.preventDefault();if(!keys[k])pressed[k]=true;keys[k]=true;if(k==='Escape')togglePause();if(k==='Enter'&&state==='menu')reset()});
addEventListener('keyup',e=>{let k=e.key.length===1?e.key.toLowerCase():e.key;const map={'ф':'a','в':'d','ц':'w','о':'j','л':'k'};keys[map[k]||k]=false});
function togglePause(){if(state==='play')overlay('ПАУЗА','Город подождёт. Наберись сил.','ПРОДОЛЖИТЬ','paused');else if(state==='paused'){state='play';$('#overlay').classList.add('hidden')}}
addEventListener('blur',()=>{for(let k in keys)keys[k]=false;if(state==='play')togglePause()});document.addEventListener('visibilitychange',()=>{if(document.hidden&&state==='play')togglePause()});
$('#play').onclick=reset;$('#pause').onclick=togglePause;$('#restart').onclick=reset;$('#resume').onclick=()=>state==='paused'?togglePause():reset();$('#sound').onclick=()=>{soundOn=!soundOn;$('#sound').style.color=soundOn?'#c0f47a':'#cfdbca';$('#sound').setAttribute('aria-label',soundOn?'Выключить звук':'Включить звук');$('#sound').title=soundOn?'Выключить звук':'Включить звук';if(soundOn)sfx(360,.15,'sine')};
for(let b of document.querySelectorAll('[data-key]')){b.addEventListener('pointerdown',e=>{e.preventDefault();b.setPointerCapture(e.pointerId);keys[b.dataset.key]=true;pressed[b.dataset.key]=true});for(let event of ['pointerup','pointercancel','lostpointercapture'])b.addEventListener(event,()=>keys[b.dataset.key]=false)}
// Rasterize static city layers once. Keep characters, light, rain, birds and camera live.
function makeTexture(width,height,draw){const texture=document.createElement('canvas');texture.width=width;texture.height=height;const original=ctx;ctx=texture.getContext('2d');draw();ctx=original;return texture;}
const skylineTextures=Array.from({length:3},(_,layer)=>makeTexture(1900,H,()=>drawSkyline(layer)));
const terrainTextures=Array.from({length:4},(_,i)=>makeTexture(1400,H,()=>{const oldW=W,oldCamera=camera;W=1400;camera=i*1400;terrain();W=oldW;camera=oldCamera;}));
window.__game={get state(){return state},p,reset,update,keys,pressed,get enemies(){return enemies},get platforms(){return platforms},get kills(){return kills}};
requestAnimationFrame(frame);
