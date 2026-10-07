"use strict";
/* KINOIMPERIYA 0.2 — offline portrait movie studio tycoon */
const cvs=document.querySelector("canvas"),ctx=cvs.getContext("2d",{alpha:false});
const W=480,H=854;ctx.imageSmoothingEnabled=false;
const C={ink:"#111628",navy:"#1b2440",slate:"#263c5b",line:"#30466a",white:"#fff5d7",pale:"#c7d9dd",muted:"#8fa9b9",gold:"#ffdc70",yellow:"#ffe26f",red:"#fa6378",pink:"#ff8fb8",mint:"#5ee6bc",teal:"#34bdaa",blue:"#65bffc",lav:"#ad91f2",floor:"#b78e79",shadow:"#10182a"};
const genres=["КОМЕДИЯ","БОЕВИК","ФАНТАСТИКА","УЖАСЫ"];
const genreEmoji=["☺","★","✦","☾"];
const genresColor=[C.gold,C.red,C.blue,C.lav];
const titles=[["Соседи с Луны","Папа в отпуске","Кофе для двоих","Свадьба века","Супер няня"],["Последний дубль","Точка удара","Погоня 1995","Секретный агент","Без тормозов"],["Звёздный рейс","Орбита 9","Космический гость","Новая планета","Сигнал издалека"],["Тихий этаж","Ночной гость","Старый дом","Кошмар в студии","Шёпот в темноте"]];
const budgets=[8000,16000,29000],budgetNames=["ИНДИ","СТУДИЯ","ХИТ"];
const recruits=[
{name:"СОНЯ",job:"АКТРИСА",skill:78,salary:180,cost:5400,look:2},
{name:"ТИМОФЕЙ",job:"ОПЕРАТОР",skill:67,salary:140,cost:4200,look:3},
{name:"КИРА",job:"ПРОДЮСЕР",skill:84,salary:220,cost:7300,look:4},
{name:"ЛЁВА",job:"АКТЁР",skill:60,salary:110,cost:3300,look:5},
{name:"ЕВА",job:"МОНТАЖЁР",skill:74,salary:170,cost:5100,look:6},
{name:"МИША",job:"ЗВУК",skill:69,salary:130,cost:3800,look:7}
];
const fresh=()=>({money:50000,fans:120,rep:21,day:1,staff:[
{name:"МАКС",job:"РЕЖИССЁР",skill:68,salary:180,look:0},
{name:"ЛИЗА",job:"СЦЕНАРИСТ",skill:62,salary:155,look:1}],
movies:[],film:null,studio:1,trend:2,press:0,debt:0,speed:1,music:true,tip:"Начни снимать первый фильм!"});
let g;try{g=JSON.parse(localStorage.getItem("kinov2"))||fresh()}catch(e){g=fresh()}
const defaults=fresh();for(const k of Object.keys(defaults))if(g[k]===undefined)g[k]=defaults[k];
let modal="",genre=0,level=1,notice="",toastAge=0,clock=0,stepClock=0;
let hits=[],particles=[],people=[],buttonAreas=[],lastPremiere=null,historyTab=0;
function persist(){try{localStorage.setItem("kinov2",JSON.stringify(g))}catch(e){}}
function cash(x){let v=Math.abs(Math.round(x)).toLocaleString("ru-RU");return (x<0?"−":"")+"$"+v}
const rand=(a,b)=>a+Math.random()*(b-a);
function fill(x,y,w,h,color){ctx.fillStyle=color;ctx.fillRect(Math.round(x),Math.round(y),Math.round(w),Math.round(h))}
function stroke(x,y,w,h,color=C.line,t=2){fill(x,y,w,t,color);fill(x,y+h-t,w,t,color);fill(x,y,t,h,color);fill(x+w-t,y,t,h,color)}
function txt(s,x,y,size=14,color=C.white,bold=false,max=440,align="left"){
 let value=String(s);ctx.font=(bold?"900 ":"600 ")+size+"px monospace";
 while(value.length>2&&ctx.measureText(value).width>max)value=value.slice(0,-2)+"…";
 ctx.textAlign=align;ctx.textBaseline="top";ctx.fillStyle=color;ctx.fillText(value,Math.round(x),Math.round(y));
 ctx.textAlign="left";
}
function card(x,y,w,h,color=C.navy,border=C.line){
 fill(x+3,y+4,w,h,C.shadow);fill(x,y,w,h,color);stroke(x,y,w,h,border,2);
 fill(x+4,y+4,w-8,2,"#ffffff14");
}
function shine(x,y,w,h,color="#ffffff14"){fill(x,y,w,h,color)}
function circle(x,y,r,col){ctx.beginPath();ctx.fillStyle=col;ctx.arc(x,y,r,0,Math.PI*2);ctx.fill()}
function button(x,y,w,h,label,id,opts={}){
 const enabled=opts.enabled!==false,selected=!!opts.selected;
 const bg=enabled?(selected?C.gold:opts.kind==="hot"?C.red:C.slate):"#2a344a";
 fill(x+2,y+4,w,h,C.shadow);fill(x,y,w,h,selected?C.gold:enabled?(opts.kind==="hot"?"#ffb9b8":C.muted):"#43536c");
 fill(x+3,y+3,w-6,h-6,bg);
 fill(x+6,y+4,w-12,3,selected?"#fff4bf":"#ffffff1a");
 txt(label,x+w/2,y+(h-(opts.sz||14))/2-1,opts.sz||14,enabled?(selected?C.ink:C.white):"#70859a",true,w-12,"center");
 if(enabled)buttonAreas.push({x,y,w,h,id});
}
function meter(x,y,w,percent,col=C.mint){fill(x,y,w,12,C.ink);fill(x+2,y+2,(w-4)*Math.max(0,Math.min(1,percent)),8,col)}
function star(x,y,r,color=C.gold){fill(x-2,y-r,4,2*r,color);fill(x-r,y-2,2*r,4,color);fill(x-4,y-4,8,8,color)}
function bubble(s,x,y){
 const width=Math.max(33,Math.min(95,s.length*9+13));x=Math.min(W-width-8,Math.max(8,x));
 fill(x+2,y+2,width,22,C.shadow);fill(x,y,width,22,C.white);stroke(x,y,width,22,C.ink,2);
 txt(s,x+7,y+4,12,C.ink,true,width-10);fill(x+11,y+22,7,5,C.white);
}
function badge(x,y,s,color=C.gold){const w=s.length*8+16;fill(x,y,w,23,color);txt(s,x+8,y+5,11,C.ink,true,w-10);return w}
function nowTip(msg){notice=msg;toastAge=4;g.tip=msg}
function banner(x,y,w,h,label,subtitle,color){
 card(x,y,w,h,C.navy,C.line);fill(x+3,y+3,5,h-6,color);
 txt(label,x+14,y+8,13,C.white,true,w-20);
 txt(subtitle,x+14,y+27,11,C.muted,false,w-20);
}
function wallGradient(){
 // hand-stippled neon city beyond studio windows
 fill(0,0,W,H,C.ink);
 for(let i=0;i<22;i++)fill(i*28,54+Math.sin(i*3)*5,12,3,i%2?C.navy:"#273754");
}
function pixelArt(scale=2){
 const a=document.createElement("canvas");a.width=432;a.height=342;
 const t=a.getContext("2d");t.imageSmoothingEnabled=false;
 const p=(x,y,w,h,color)=>{t.fillStyle=color;t.fillRect(x,y,w,h)};
 // background stage wall and sky windows
 p(0,0,432,342,"#30364f");
 p(0,0,432,29,"#526078");
 for(let i=0;i<25;i++)p(i*19,7,11,4,i%2?"#596b88":"#65718d");
 p(4,28,424,76,"#56677c");
 for(let i=0;i<3;i++){
  const x=20+i*145;
  p(x,34,107,56,"#1c2f56");p(x+4,38,99,48,"#34496f");
  for(let j=0;j<10;j++){let y=75-(j%3)*11;p(x+j*10+5,y,8,10,"#253657");if(j%2===0)p(x+j*10+7,y+2,3,3,C.gold)}
  p(x,34,107,4,"#afc0bf");p(x,85,107,5,"#b8d3cb");p(x+51,35,5,51,"#cddbd1");
 }
 p(0,99,432,10,"#1d293c");p(0,109,432,233,"#c8a085");
 // floor checkerboard 
 for(let yy=0;yy<9;yy++)for(let xx=0;xx<17;xx++){
  p(xx*26+(yy%2)*13,110+yy*28,25,27,(xx+yy)%2?"#bc967e":"#b08876");
  p(xx*26+(yy%2)*13,135+yy*28,25,2,"#9b766c");
 }
 // upper rooms wall partitions
 p(0,108,432,8,"#57485a");
 p(157,108,8,128,"#69556a");p(158,110,3,124,"#cfaa88");
 p(312,108,8,128,"#69556a");p(313,110,3,124,"#cfaa88");
 // desks stage top left writing
 p(24,148,111,9,"#f0bb77");p(28,157,7,42,"#735062");p(124,157,7,42,"#735062");
 p(40,128,71,19,"#344760");p(43,132,65,13,"#72c9d2");p(48,135,26,3,"#d2f5ef");
 p(70,150,24,4,"#7e4d60");p(24,178,31,26,"#e28b83");p(28,178,24,9,"#f9c37e");
 p(120,123,7,25,"#6d5c67");p(112,111,24,18,"#7ad5a8");p(116,108,16,5,"#d8ef8d");
 // film reel editing area upper center
 p(190,145,99,9,"#f4c078");p(196,155,8,37,"#755364");p(280,155,7,37,"#755364");
 p(205,122,61,27,"#252e4d");p(210,126,50,19,"#89c5e6");
 for(let i=0;i<4;i++)p(215+i*10,132,8,3,i%2?C.gold:C.mint);
 p(271,129,10,17,"#1c2841");
 p(218,172,39,25,"#5f6681");p(220,174,35,8,"#8796a5");
 p(279,113,28,11,"#f3ba7f");
 // right office producer 
 p(336,147,79,11,"#ffd18a");p(340,158,9,43,"#806277");p(400,158,9,43,"#806277");
 p(348,126,52,21,"#344661");p(353,130,42,12,"#f9ab78");
 p(355,133,29,4,"#f7e4a1");p(365,172,25,22,"#c86778");
 p(325,108,34,6,"#ffcc7d");p(328,115,29,26,"#d68d6e");p(331,118,23,20,"#7c4569");
 // stage shoot zone with curtain
 p(12,230,237,106,"#d66b77");p(18,235,225,91,"#f08087");
 for(let i=0;i<8;i++)p(23+i*27,237,11,85,i%2?"#ed6d84":"#f88b82");
 p(62,242,119,10,"#78436d");p(80,250,84,68,"#486d80");
 p(85,254,74,60,"#64b6af");p(111,265,32,45,"#f5cd88");
 p(93,279,11,31,"#e2aa74");p(153,279,9,31,"#e2aa74");
 p(102,283,51,8,"#ffe0a1");
 // studio cameras detailed silhouette
 p(26,263,29,23,"#253755");p(21,265,8,12,"#394767");p(52,267,14,11,"#1a304a");p(32,257,14,7,"#a9c2c5");
 p(41,286,4,25,"#1e2d48");p(28,309,30,5,"#273453");
 p(192,254,14,12,"#35465e");p(198,267,5,34,"#35465e");
 p(186,301,29,5,"#34435a");p(191,249,17,6,"#fff1b2");p(193,246,14,3,"#ffe08b");
 // clapperboard
 p(173,306,33,21,"#1a2d46");p(171,303,37,8,"#f7e6cb");
 for(let i=0;i<4;i++)p(176+i*9,303,5,8,"#212e46");
 p(176,317,26,3,C.white);
 // stage right props
 p(263,240,159,94,"#867a80");p(271,248,144,78,"#9e8790");
 for(let i=0;i<3;i++){let x=278+i*44;p(x,266,34,52,"#dbaa80");p(x,266,34,7,"#f5d29a");p(x+12,277,5,7,"#885c5f");}
 p(284,237,63,8,"#f8d08b");p(292,229,49,11,"#a87283");
 p(376,230,30,24,"#43536c");p(378,233,25,18,"#78d2cf");p(384,241,13,4,"#ffe5b5");
 p(371,257,35,10,"#a87473");p(373,267,5,23,"#5e4a60");p(397,267,5,23,"#5e4a60");
 // reel, plants, posters, overhead lamps 
 for(let i=0;i<5;i++){let x=16+i*98;p(x,97,17,6,"#3e354d");p(x+6,101,4,19,"#756179");p(x+2,116,13,4,C.yellow)}
 // poster upper horizontal ornaments 
 const labels=[[12,"СЦЕНАРНАЯ"],[171,"МОНТАЖ"],[330,"ОФИС"],[16,"ПАВИЛЬОН"],[263,"РЕКВИЗИТ"]];
 for(let i=0;i<labels.length;i++){let [x,s]=labels[i],y=i<3?113:213;p(x,y,115,18,"#172943");p(x,y,4,18,i<3?C.mint:C.gold);
  t.font="bold 10px monospace";t.fillStyle=C.white;t.fillText(s,x+8,y+4);}
 // glowing film bulbs dotted on beams
 for(let i=0;i<30;i++){let x=7+i*14;p(x,105,4,3,i%3===0?C.yellow:"#f8d2ad")}
 return a;
}
const stage=pixelArt();
function drawSpriteFrame(t,x,y,who,walk,direction=0){
 // 24x34 pixel-precise sprite, hand-authored layered 16-bit look; no primitive character rectangles at game scale
 const P=(a,b,w,h,color)=>{t.fillStyle=color;t.fillRect(x+a,y+b,w,h)};
 const palette=[
 ["#f0b77c","#503954","#ec5e78","#425879"],["#f6bf9d","#63477d","#6cd5bb","#344f6b"],
 ["#eac49e","#432f46","#ee99c4","#7b4c8f"],["#9c6c57","#262b44","#80b8ed","#2b4765"],
 ["#e4a884","#8d4d57","#fbd17a","#645270"],["#d5a17d","#1f3a4a","#e67f78","#396579"],
 ["#f7be9d","#e6a970","#96c8fa","#5f598f"],["#a96a5f","#362849","#ac9aff","#535b76"]];
 const s=palette[who%palette.length],step=walk%4,swing=[0,2,0,-2][step],face=direction;
 // drop shadow / legs
 P(3,30,19,2,"#302f4a");
 P(6,25,5,6,"#26304e");P(13,25+swing,5,6-swing,"#253251");
 P(5,29,7,3,"#1a2941");P(13,29+swing,7,3,"#1a2941");
 // torso jacket shadow / sleeve
 P(5,14,15,12,"#30364e");P(6,14,13,11,s[2]);P(8,16,9,2,"#ffe1aa");
 P(3,16,3,10,s[0]);P(20,16,3,10,s[0]);
 P(3,25,3,2,"#77516b");P(20,25,3,2,"#77516b");
 // neck and face
 P(10,12,6,4,s[0]);P(6,5,14,11,s[0]);P(6,11,3,5,"#bd847a");
 P(7,5,14,3,s[1]);P(5,7,3,7,s[1]);
 P(9,11,2,2,"#283452");P(16,11,2,2,"#283452");
 P(12,14,3,1,"#b66f76");
 P(7,8,2,2,"#ffecbf");
 P(7,18,3,3,"#ffffff55");
 // hairstyles and occupation hats
 if(who===0){P(5,1,16,5,s[1]);P(4,3,6,8,s[1]);P(16,5,6,4,s[1]);P(11,0,8,2,s[1]);}
 if(who===1||who===2||who===6){P(5,2,16,5,s[1]);P(4,7,4,11,s[1]);P(19,8,3,11,s[1]);}
 if(who===3||who===5){P(6,2,15,5,s[1]);P(5,4,5,7,s[1]);P(8,1,10,3,s[1]);}
 if(who===4){P(4,3,18,5,s[1]);P(6,0,13,3,s[1]);P(16,8,5,4,s[1]);}
 if(who===7){P(5,2,17,6,s[1]);P(7,0,11,3,s[1]);}
 // prop unique to role
 if(who===0){P(1,22,7,5,"#213049");P(2,23,5,2,C.white);P(3,21,4,2,C.red);}
 if(who===1){P(18,21,7,7,"#fff3cb");P(20,23,3,1,C.blue);}
 if(who===2||who===5){P(0,19,6,8,"#d6ae64");P(1,20,5,3,"#f3dd9c");}
 if(who===3){P(0,17,7,8,"#293449");P(2,19,4,3,C.blue);}
 if(who===4){P(21,20,3,8,C.gold);}
 if(who===6){P(20,19,5,7,C.mint);}
 if(who===7){P(1,17,3,12,C.gold);}
}
const spriteAtlas=document.createElement("canvas");spriteAtlas.width=8*4*26;spriteAtlas.height=34;
const sat=spriteAtlas.getContext("2d");sat.imageSmoothingEnabled=false;
for(let i=0;i<8;i++)for(let j=0;j<4;j++)drawSpriteFrame(sat,(i*4+j)*26,0,i,j);
let actors=[];
function syncActors(){
 actors=g.staff.map((p,i)=>actors[i]&&actors[i].name===p.name?actors[i]:{
 name:p.name,look:p.look||0,x:50+i*26,y:220+(i%3)*38,tx:50+i*26,ty:220+(i%3)*38,
 frame:0,phase:rand(0,5),task:"walk",goal:0,working:0});
}
syncActors();
const stations=[[72,154],[226,160],[356,146],[115,267],[304,266],[384,265],[225,240],[149,195]];
function updateActors(dt){
 const targets=g.film?[[95,263],[184,260],[108,253],[298,268],[143,275],[310,251],[215,279],[366,276]]:[[86,165],[226,173],[348,163],[128,268],[315,272],[380,268],[239,186],[170,210]];
 actors.forEach((a,i)=>{
  if(a.working<=0){const k=(i+Math.floor(clock/7))%targets.length;a.tx=targets[k][0]+rand(-12,13);a.ty=targets[k][1]+rand(-9,9);a.working=rand(1.9,4.5)}
  const dx=a.tx-a.x,dy=a.ty-a.y,dist=Math.hypot(dx,dy);
  if(dist>3){const vel=Math.min(dist,dt*(g.film?31:21));a.x+=dx/dist*vel;a.y+=dy/dist*vel;a.frame=Math.floor(clock*7+i)%4;a.task="walk"}
  else{a.frame=0;a.working-=dt;a.task="work"}
 });
}
function character(a){
 const scale=1.8,x=24+a.x* (432/432),y=176+a.y*(342/342);
 fill(x+7,y+50,30,6,"#3b3c4e66");
 ctx.drawImage(spriteAtlas,(a.look*4+a.frame)*26,0,26,34,x,y,26*scale,34*scale);
 const nameW=Math.max(43,a.name.length*7+12),nx=x+22-nameW/2;
 fill(nx,y-17,nameW,16,"#152946");fill(nx+2,y-15,nameW-4,12,C.mint);
 txt(a.name,nx+nameW/2,y-13,10,C.ink,true,nameW-4,"center");
 if(a.task==="work" && Math.sin(clock*4+a.phase)>0.5){
  fill(x+42,y+5,19,17,"#fff5d4");txt(g.film?["!","★","♫","OK"][a.look%4]:["✎","...","!","♫"][a.look%4],x+46,y+7,12,C.navy,true,16);
 }
}
function scene(){
 const x=24,y=173;
 fill(14,164,451,375,"#0c1930");fill(18,160,443,375,C.gold);
 fill(22,165,435,370,"#162843");
 // render pixel art at 432x342 scaled to 435x345
 ctx.drawImage(stage,x,y,432,342);
 // moving stage spot lights and camera animation
 if(g.film){
  const ox=143+Math.sin(clock*2.1)*12;
  ctx.save();ctx.globalAlpha=.14;
  ctx.beginPath();ctx.moveTo(ox,285);ctx.lineTo(ox-82,484);ctx.lineTo(ox+80,484);ctx.closePath();ctx.fillStyle="#fff3bd";ctx.fill();ctx.restore();
  for(let i=0;i<8;i++){
   let p=i*1.6+clock*.6,cx=42+i*50+Math.sin(p)*8,cy=410+Math.cos(p)*19;
   if(i%2===0)star(cx,cy,3,i%4===0?C.yellow:C.mint);
  }
 }
 actors.slice().sort((a,b)=>a.y-b.y).forEach(character);
 // stage status overlay
 fill(32,506,412,23,"#182f4df5");
 txt(g.film?"●  СЪЁМКА ИДЁТ  •  "+g.film.name:"●  ПАВИЛЬОН В ОЖИДАНИИ СЪЁМОК",37,510,12,g.film?C.red:C.mint,true,396);
 // top illuminated signage
 fill(25,174,423,27,C.ink);fill(29,178,415,19,"#213b56");
 txt("КИНОСТУДИЯ «ИСКРА»",36,180,13,C.gold,true);
 txt("ПАВИЛЬОН "+g.studio,432,182,11,C.mint,true,117,"right");
}
function upperUI(){
 wallGradient();fill(0,0,W,151,C.navy);
 // festive pixels
 for(let i=0;i<28;i++){fill(i*19,0,12,4,i%2?C.pink:C.gold)}
 txt("КИНОИМПЕРИЯ",15,18,30,C.gold,true);txt("STUDIO TYCOON",17,54,13,C.mint,true);
 button(392,20,72,39,g.music?"♫ ON":"♫ OFF","sound",{sz:13});
 fill(15,85,216,57,C.slate);stroke(15,85,216,57,C.line);
 txt("КАССА",24,90,12,C.pale);txt(cash(g.money),24,108,22,g.money>7000?C.mint:C.red,true,202);
 fill(240,85,224,57,C.slate);stroke(240,85,224,57,C.line);
 txt("ФАНАТЫ",248,91,10,C.pale);txt("РЕЙТИНГ",358,91,10,C.pale);
 txt("♥ "+g.fans,249,111,19,C.white,true,100);txt("★ "+g.rep,361,111,19,C.gold,true,97);
}
function progressCard(){
 card(15,548,450,118,"#243c59",C.line);
 if(g.film){
 txt("В ПРОИЗВОДСТВЕ",28,557,12,C.mint,true);
 txt(g.film.name.toUpperCase(),28,578,19,C.white,true,414);
 txt("ДЕНЬ "+g.film.progress+" / "+g.film.days,355,557,12,C.gold,true,94);
 meter(27,610,426,g.film.progress/g.film.days,C.gold);
 txt(genres[g.film.genre]+"  •  БЮДЖЕТ "+cash(g.film.budget),28,636,12,C.pale);
 }else{
 txt("ГОТОВЫ К НОВОМУ ФИЛЬМУ?",28,562,18,C.white,true,415);
 txt("Тренд сезона: "+genres[g.trend],28,591,15,C.gold,true);
 txt("Команда: "+g.staff.length+"/8     Год: 1995     День: "+g.day,28,626,12,C.pale);
 }
}
function home(){
 upperUI();scene();progressCard();
 button(15,682,450,68,g.film?"СЪЁМКИ ИДУТ...":"🎬  СОЗДАТЬ ФИЛЬМ","create",{enabled:!g.film,kind:"hot",sz:20});
 button(15,763,141,65,"КОМАНДА","team",{sz:15});
 button(169,763,141,65,"АРХИВ","archive",{sz:15});
 button(323,763,142,65,"+ 3 ДНЯ","skip",{sz:15});
 txt(notice||g.tip,18,834,11,C.muted,false,434);
 if(toastAge>0){fill(18,146,444,24,C.mint);txt(notice,27,150,12,C.ink,true,420);}
}
function overlay(){
 fill(0,0,W,H,"#0b1424ef");buttonAreas=[];
 card(16,100,448,653,C.navy,C.gold);
 fill(23,107,434,7,C.gold);
}
function modalTitle(s){txt(s,34,121,23,C.gold,true,390);button(415,119,35,34,"X","close",{sz:20})}
function createModal(){
 overlay();modalTitle("НОВЫЙ ФИЛЬМ");
 txt("01 / ВЫБЕРИ ЖАНР",34,174,14,C.mint,true);
 genres.forEach((name,i)=>{
  const x=33+(i%2)*210,y=199+Math.floor(i/2)*74;
  button(x,y,198,63,genreEmoji[i]+"  "+name,"genre:"+i,{selected:genre===i,sz:16});
 });
 txt("02 / БЮДЖЕТ ПРОЕКТА",34,362,14,C.mint,true);
 budgets.forEach((sum,i)=>{
  const x=33+i*141;
  button(x,389,132,88,budgetNames[i],"tier:"+i,{selected:level===i,sz:14});
  txt(cash(sum),x+66,453,13,level===i?C.ink:C.gold,true,118,"center");
 });
 txt("ВРЕМЯ СЪЁМОК: "+[7,12,17][level]+" ДНЕЙ",34,495,15,C.white);
 txt("ПОД СЪЁМКИ: "+cash(budgets[level]),34,522,15,C.white);
 txt("Тренд сезона: "+genres[g.trend],34,550,12,C.gold);
 fill(32,584,415,40,C.slate);
 txt("После старта: "+cash(g.money-budgets[level]),43,595,15,g.money>=budgets[level]?C.mint:C.red,true);
 button(32,647,416,72,"КАМЕРА! МОТОР!","start",{enabled:g.money>=budgets[level],kind:"hot",sz:20});
}
function miniPortrait(x,y,p){
 fill(x,y,57,65,"#3b4e6b");fill(x+4,y+4,49,57,"#94bed0");
 ctx.drawImage(spriteAtlas,(p.look*4)*26,0,26,34,x+5,y+4,46,59);
}
function teamModal(){
 overlay();modalTitle("МОЯ КОМАНДА");
 txt("РАБОТНИКИ  "+g.staff.length+"/8",34,160,13,C.mint,true);
 txt("ЗАРПЛАТЫ  "+cash(wages())+"/день",236,160,12,C.pale,true);
 const choices=recruits.filter(p=>!g.staff.some(a=>a.name===p.name));
 for(let i=0;i<4;i++){
  const p=choices[i],y=196+i*122;
  if(!p)continue;
  card(32,y,416,112,"#294260",C.line);
  miniPortrait(41,y+9,p);
  txt(p.name,111,y+10,16,C.white,true);
  txt(p.job,111,y+33,12,C.mint,true);
  txt("НАВЫК "+p.skill+"  •  "+cash(p.salary)+"/д.",111,y+57,11,C.pale);
  button(281,y+72,152,31,"НАНЯТЬ "+cash(p.cost),"hire:"+p.name,{enabled:g.money>=p.cost&&g.staff.length<8,sz:11});
 }
 txt("Хорошая команда = успешные премьеры.",32,712,12,C.gold);
}
function archives(){
 overlay();modalTitle("КИНОАРХИВ");
 badge(35,168,"ПРОКАТ",C.mint);
 txt("Выпущено: "+g.movies.length,35,206,18,C.white,true);
 txt("Студия: уровень "+g.studio,35,234,15,C.gold,true);
 if(g.movies.length===0)txt("Здесь появятся ваши фильмы.",35,299,15,C.pale);
 for(let i=0;i<Math.min(g.movies.length,4);i++){
  let m=g.movies[i],y=274+i*76;
  card(31,y,417,67,C.slate);
  txt("★ "+m.name,43,y+8,15,C.white,true,375);
  txt(genres[m.genre]+"  •  "+m.rating+"/100",43,y+32,12,C.pale);
  txt(cash(m.gross),420,y+40,15,C.mint,true,130,"right");
 }
 button(34,635,412,46,"УЛУЧШИТЬ ПАВИЛЬОН — "+cash(g.studio*12500),"upgrade",{enabled:g.studio<3&&g.money>=g.studio*12500,sz:13});
 if(!g.debt)button(34,689,412,42,"КРЕДИТ +$12 000","loan",{sz:14});
 else txt("ДОЛГ: "+cash(g.debt),40,698,15,C.red,true);
}
function premiere(){
 overlay();modalTitle("ПРЕМЬЕРА!");
 const f=lastPremiere;if(!f)return;
 fill(35,176,410,180,C.slate);
 for(let i=0;i<23;i++)star(50+i*17,184+(i%3)*6,2,i%2?C.gold:C.mint);
 txt("МИРОВАЯ ПРЕМЬЕРА",240,202,16,C.gold,true,380,"center");
 txt(f.name,240,238,22,C.white,true,388,"center");
 txt(genres[f.genre],240,279,14,C.mint,true,380,"center");
 txt("ОЦЕНКА КРИТИКОВ",35,376,15,C.pale,true);
 txt(f.rating+"/100",35,407,47,f.rating>=67?C.mint:C.red,true);
 meter(34,467,412,f.rating/100,f.rating>=67?C.mint:C.red);
 card(35,498,411,104,C.slate);
 txt("БЮДЖЕТ",47,511,13,C.pale);txt("СБОРЫ",248,511,13,C.pale);
 txt(cash(f.budget),47,539,19,C.white,true,175);txt(cash(f.gross),248,539,20,C.gold,true,183);
 txt("ПРИБЫЛЬ: "+cash(f.gross-f.budget),37,615,17,f.gross>=f.budget?C.mint:C.red,true);
 txt("ПОКЛОННИКИ: "+(f.fans>=0?"+":"")+f.fans,37,641,13,C.white);
 button(34,677,412,52,"ПРОДОЛЖИТЬ","close",{kind:"hot",sz:17});
}
function pauseModal(){
 overlay();modalTitle("НАСТРОЙКИ");
 txt("Музыка и эффекты",35,198,16,C.white);
 button(34,238,412,59,g.music?"ЗВУК: ВКЛ":"ЗВУК: ВЫКЛ","sound",{selected:g.music});
 txt("Прогресс сохраняется автоматически.",35,349,13,C.mint);
 txt("Киноимперия v0.2 • Pixel Studio",35,698,12,C.pale);
}
function wages(){return 65+g.staff.reduce((n,p)=>n+p.salary,0)}
function tickDay(){
 g.day++;g.money-=wages();
 if(g.day%22===0){g.trend=(g.trend+1)%4;nowTip("Новый тренд: "+genres[g.trend]);}
 if(g.film){
  g.film.progress++;
  if(g.film.progress>=g.film.days)completeFilm();
 }
 if(g.money<0 && !g.debt){g.debt=12000;g.money+=9000;nowTip("Экстренный кредит студии");}
 persist();
}
function completeFilm(){
 const f=g.film,skill=g.staff.reduce((a,p)=>a+p.skill,0)/g.staff.length;
 const rating=Math.min(99,Math.max(12,Math.round(21+skill*.56+g.studio*5+(f.genre===g.trend?9:0)+f.level*6+rand(-14,10))));
 const gross=Math.max(600,Math.round(f.budget*(.15+rating/46+g.fans/2600)*rand(.7,1.15)));
 const fans=rating>53?Math.round((rating-42)*(f.level+1)*1.8):-Math.round((53-rating)*.8);
 const m={...f,rating,gross,fans};
 lastPremiere=m;g.movies.unshift(m);g.movies=g.movies.slice(0,20);
 g.money+=gross;g.fans=Math.max(0,g.fans+fans);g.rep=Math.max(1,Math.min(100,g.rep+Math.round((rating-55)/14)));
 if(g.debt){let pay=Math.min(g.debt,Math.floor(gross*.15));g.debt-=pay;g.money-=pay;}
 g.film=null;modal="premiere";playSfx(rating>55?"win":"fail");burst(240,352,C.gold,50);nowTip("Премьера: "+m.name);
}
function startMovie(){
 const cost=budgets[level];if(g.film||g.money<cost)return;
 const name=titles[genre][g.movies.length%5];
 g.money-=cost;g.film={genre,level,name,budget:cost,days:[7,12,17][level],progress:0};
 modal="";burst(240,385,C.yellow,28);playSfx("start");nowTip("Съёмки начались: "+name);persist();
}
function burst(x,y,color,n){
 for(let i=0;i<n;i++)particles.push({x,y,vx:rand(-150,150),vy:rand(-180,-10),life:rand(.4,1),color});
}
function updateParticles(dt){for(let i=particles.length-1;i>=0;i--){let p=particles[i];p.vy+=240*dt;p.x+=p.vx*dt;p.y+=p.vy*dt;p.life-=dt;if(p.life<=0)particles.splice(i,1)}}
function drawParticles(){particles.forEach(p=>fill(p.x,p.y,Math.max(2,p.life*6),Math.max(2,p.life*6),p.color))}
let ac=null,musicNext=0,musicBeat=0,master=null;
function audioInit(){
 if(!g.music)return;
 try{if(!ac){ac=new (window.AudioContext||window.webkitAudioContext)();master=ac.createGain();master.gain.value=.18;master.connect(ac.destination);musicNext=ac.currentTime+.03;}if(ac.state==="suspended")ac.resume();}catch(e){}
}
function tone(freq,start,len,type="square",volume=.15,slide=0){
 if(!ac||!g.music)return;
 const o=ac.createOscillator(),v=ac.createGain();o.type=type;o.frequency.setValueAtTime(Math.max(30,freq),start);
 if(slide)o.frequency.exponentialRampToValueAtTime(Math.max(30,freq+slide),start+len);
 v.gain.setValueAtTime(.0001,start);v.gain.linearRampToValueAtTime(volume,start+.013);
 v.gain.exponentialRampToValueAtTime(.0001,start+len);
 o.connect(v);v.connect(master);o.start(start);o.stop(start+len+.008);
}
const melody=[0,4,7,11,7,4,2,4,0,4,9,7,4,2,0,-3];
function musicPump(){
 if(!ac||!g.music||ac.state!=="running")return;
 while(musicNext<ac.currentTime+.20){
  const step=musicBeat%16,n=melody[step];
  const freq=261.63*Math.pow(2,n/12);
  tone(freq,musicNext,.11,"triangle",.10);
  if(step%4===0)tone(130.81*Math.pow(2,(step/4)%3/12),musicNext,.36,"sine",.07);
  if(step%2===0)tone(70,musicNext,.045,"triangle",.055,-15);
  musicNext+=.225;musicBeat++;
 }
}
function playSfx(what){
 if(!g.music)return;audioInit();if(!ac)return;
 let now=ac.currentTime+.004;
 if(what==="click"){tone(490,now,.035,"square",.08,160);}
 if(what==="hire"){[440,554,659].forEach((f,i)=>tone(f,now+i*.06,.10,"triangle",.12));}
 if(what==="start"){[260,320,400,520].forEach((f,i)=>tone(f,now+i*.09,.12,"square",.10));}
 if(what==="win"){[523,659,784,1047].forEach((f,i)=>tone(f,now+i*.11,.26,"triangle",.15));}
 if(what==="fail"){[330,240,175].forEach((f,i)=>tone(f,now+i*.18,.21,"sawtooth",.07));}
 if(what==="cash"){tone(760,now,.08,"triangle",.11);tone(1020,now+.06,.12,"triangle",.09);}
}
function click(id){
 if(id==="close"){modal="";persist();return}
 if(id==="sound"){g.music=!g.music;if(g.music)audioInit();else if(ac)ac.suspend();persist();return}
 if(id==="create"&&!g.film){modal="create";genre=0;level=1;return}
 if(id==="team"){modal="team";return}
 if(id==="archive"){modal="archive";return}
 if(id==="settings"){modal="settings";return}
 if(id==="skip"&&modal===""){for(let i=0;i<3;i++){tickDay();if(modal==="premiere")break;}return}
 if(id.startsWith("genre:")){genre=Number(id.split(":")[1]);return}
 if(id.startsWith("tier:")){level=Number(id.split(":")[1]);return}
 if(id==="start"){startMovie();return}
 if(id.startsWith("hire:")){
  const p=recruits.find(x=>x.name===id.slice(5));
  if(p&&g.money>=p.cost&&g.staff.length<8&&!g.staff.some(x=>x.name===p.name)){
   g.money-=p.cost;g.staff.push({...p});syncActors();burst(250,380,C.mint,20);playSfx("hire");nowTip(p.name+" в команде!");persist();
  }
 }
 if(id==="upgrade"&&g.studio<3&&g.money>=g.studio*12500){
  g.money-=g.studio*12500;g.studio++;playSfx("cash");nowTip("Павильон улучшен!");persist();
 }
 if(id==="loan"&&!g.debt){g.money+=12000;g.debt=15000;playSfx("cash");nowTip("Кредит: $12 000");persist();}
}
function animate(time){
 const dt=Math.min(.048,(time-stepClock)/1000||.016);stepClock=time;clock+=dt;
 if(toastAge>0){toastAge=Math.max(0,toastAge-dt);if(toastAge===0)notice=""}
 if(modal!=="premiere")updateActors(dt);
 updateParticles(dt);musicPump();
 buttonAreas=[];
 home();drawParticles();
 if(modal){if(modal==="create")createModal();else if(modal==="team")teamModal();else if(modal==="archive")archives();else if(modal==="premiere")premiere();else if(modal==="settings")pauseModal();}
 requestAnimationFrame(animate);
}
cvs.addEventListener("pointerdown",e=>{
 e.preventDefault();audioInit();
 const r=cvs.getBoundingClientRect(),x=(e.clientX-r.left)*W/r.width,y=(e.clientY-r.top)*H/r.height;
 for(let i=buttonAreas.length-1;i>=0;i--){const b=buttonAreas[i];if(x>=b.x&&x<=b.x+b.w&&y>=b.y&&y<=b.y+b.h){
  playSfx("click");click(b.id);break;}}
},{passive:false});
window.addEventListener("keydown",e=>{if(e.key==="Escape"||e.key==="Backspace"){modal="";e.preventDefault()}});
document.addEventListener("visibilitychange",()=>{if(document.hidden)persist()});
setInterval(()=>{if(g.film&&modal!=="premiere")tickDay()},3000);
requestAnimationFrame(animate);
