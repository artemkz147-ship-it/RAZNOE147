(() => {
'use strict';

const FW=240,FH=320,COLS=8;
const cache=new Map();
const ready=new Map();
const poses=[
  {hip:[120,220],ch:[120,150],hd:[120,92],lh:[77,205],rh:[170,194],lf:[92,306],rf:[150,306]},
  {hip:[120,218],ch:[121,147],hd:[120,89],lh:[70,194],rh:[176,207],lf:[88,306],rf:[156,304]},
  {hip:[120,221],ch:[119,151],hd:[121,94],lh:[82,211],rh:[164,185],lf:[98,306],rf:[146,306]},
  {hip:[118,218],ch:[119,148],hd:[120,91],lh:[69,188],rh:[175,211],lf:[72,306],rf:[161,306]},
  {hip:[122,216],ch:[121,146],hd:[121,90],lh:[80,205],rh:[181,182],lf:[106,306],rf:[177,303]},
  {hip:[118,219],ch:[119,149],hd:[119,92],lh:[65,211],rh:[164,190],lf:[69,304],rf:[152,306]},
  {hip:[120,250],ch:[120,194],hd:[120,145],lh:[75,238],rh:[168,224],lf:[66,306],rf:[173,306]},
  {hip:[120,215],ch:[120,150],hd:[121,92],lh:[80,184],rh:[169,180],lf:[75,267],rf:[172,255]},
  {hip:[120,211],ch:[122,147],hd:[123,90],lh:[72,178],rh:[176,193],lf:[90,257],rf:[180,276]},
  {hip:[120,219],ch:[120,149],hd:[120,91],lh:[78,204],rh:[214,145],lf:[93,306],rf:[150,306]},
  {hip:[118,220],ch:[120,148],hd:[120,91],lh:[75,206],rh:[216,117],lf:[94,306],rf:[151,306]},
  {hip:[116,226],ch:[116,156],hd:[115,99],lh:[73,210],rh:[170,192],lf:[85,306],rf:[215,202]},
  {hip:[120,247],ch:[119,192],hd:[119,143],lh:[76,238],rh:[175,219],lf:[69,306],rf:[217,291]},
  {hip:[108,222],ch:[103,152],hd:[94,95],lh:[65,186],rh:[161,219],lf:[87,306],rf:[153,306]},
  {hip:[120,220],ch:[120,150],hd:[120,92],lh:[95,168],rh:[148,151],lf:[93,306],rf:[150,306]},
  {hip:[120,218],ch:[120,145],hd:[120,86],lh:[66,165],rh:[187,165],lf:[92,306],rf:[150,306]}
];

const esc=s=>String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/"/g,'&quot;');
const darker=(hex,amt=35)=>{
  const n=parseInt(hex.slice(1),16),c=x=>Math.max(0,Math.min(255,x));
  return '#'+((1<<24)+(c((n>>16)-amt)<<16)+(c(((n>>8)&255)-amt)<<8)+c((n&255)-amt)).toString(16).slice(1);
};
const lighter=(hex,amt=45)=>{
  const n=parseInt(hex.slice(1),16),c=x=>Math.max(0,Math.min(255,x));
  return '#'+((1<<24)+(c((n>>16)+amt)<<16)+(c(((n>>8)&255)+amt)<<8)+c((n&255)+amt)).toString(16).slice(1);
};

function limb(a,b,w,base,hi){
  return `<path d="M${a[0]} ${a[1]} L${b[0]} ${b[1]}" stroke="#050506" stroke-width="${w+8}" stroke-linecap="round"/><path d="M${a[0]} ${a[1]} L${b[0]} ${b[1]}" stroke="${base}" stroke-width="${w}" stroke-linecap="round"/><path d="M${a[0]-2} ${a[1]-3} L${b[0]-2} ${b[1]-3}" stroke="${hi}" stroke-opacity=".42" stroke-width="${Math.max(3,w*.18)}" stroke-linecap="round"/>`;
}
function boot(pt,dark){return `<path d="M${pt[0]-19} ${pt[1]-8}h37q8 0 9 7l-3 9h-46z" fill="${dark}" stroke="#030304" stroke-width="5"/><path d="M${pt[0]-12} ${pt[1]-5}h22" stroke="rgba(255,255,255,.18)" stroke-width="3"/>`}
function styleDetails(d,p,skin,metal){
  const st=d.style||'human',pri=d.primary,dark=d.dark;let s='';
  if(st==='ninja'||st==='femaleNinja'){
    s+=`<path d="M93 147h54l-9 47h-37z" fill="${darker(dark,0)}" opacity=".96"/><path d="M102 163h36l-6 42h-24z" fill="${lighter(pri,15)}"/><path d="M100 210h40l-6 18h-28z" fill="${dark}"/>`;
    if(st==='femaleNinja')s+=`<path d="M96 151q24-16 48 0l-7 24h-34z" fill="${pri}"/>`;
  }else if(st==='subzero'){
    s+=`<path d="M91 145h58l-5 70h-48z" fill="${lighter(pri,20)}" stroke="#0a1720" stroke-width="5"/><path d="M108 159h24v47h-24z" fill="#111820"/><path d="M95 148h50" stroke="#9be7ff" stroke-opacity=".75" stroke-width="3"/>`;
  }else if(st==='cyborg'){
    s+=`<rect x="94" y="145" width="52" height="70" rx="7" fill="${lighter(pri,12)}" stroke="#090a0b" stroke-width="6"/><path d="M100 158h40v19h-40z" fill="#191d20"/><circle cx="120" cy="168" r="6" fill="#ffe16a"/><path d="M99 190h42" stroke="${metal}" stroke-width="5"/>`;
  }else if(st==='jax'){
    s+=`<path d="M95 148h50v64H95z" fill="${skin}"/><path d="M97 169h46" stroke="#3a2520" stroke-width="7"/><path d="M86 151v69M154 151v69" stroke="${metal}" stroke-width="13"/><path d="M86 151v69M154 151v69" stroke="#e2e6e8" stroke-opacity=".45" stroke-width="3"/>`;
  }else if(st==='kano'){
    s+=`<path d="M94 147h52v68H94z" fill="#17191b"/><path d="M96 151l47 58" stroke="#a33a34" stroke-width="9"/>`;
  }else if(st==='nightwolf'){
    s+=`<path d="M94 147h52v68H94z" fill="#17201d"/><path d="M100 154h40v52h-40z" fill="none" stroke="${pri}" stroke-width="5"/><path d="M120 151v56" stroke="#d5c18c" stroke-width="3"/>`;
  }else if(st==='sindel'){
    s+=`<path d="M94 145h52v72H94z" fill="${pri}"/><path d="M109 145h22v72h-22z" fill="#151219"/><path d="M104 74q-49-28-48 64M136 74q49-28 48 64" stroke="#dedee5" stroke-width="14" fill="none" stroke-linecap="round"/>`;
  }else if(st==='stryker'){
    s+=`<path d="M92 145h56v72H92z" fill="#4b596b"/><path d="M94 177h52v20H94z" fill="#111820"/><path d="M103 145v72M137 145v72" stroke="#2d3440" stroke-width="5"/>`;
  }else if(st==='kunglao'){
    s+=`<path d="M95 145h50v70H95z" fill="#18191c"/><path d="M102 149l11 62M138 149l-11 62" stroke="#c9b27a" stroke-width="5"/><path d="M77 72h86" stroke="#111217" stroke-width="10"/><path d="M96 66h48v12H96z" fill="#171719"/>`;
  }else if(st==='kabal'){
    s+=`<path d="M92 145h56v72H92z" fill="#282729"/><path d="M91 167q29-30 58 0" stroke="#777d82" stroke-width="7" fill="none"/><path d="M93 99q-25 20-31 58M147 99q25 20 31 58" stroke="#6f7478" stroke-width="6" fill="none"/>`;
  }else if(st==='shang'){
    s+=`<path d="M91 145h58v72H91z" fill="#171719"/>`;
    for(let x=98;x<=138;x+=13)s+=`<rect x="${x}" y="150" width="7" height="61" fill="${pri}"/>`;
  }else if(st==='liukang'){
    s+=`<path d="M94 148h52v48H94z" fill="${skin}"/><path d="M93 194h54v23H93z" fill="#17121a"/><path d="M101 149h38" stroke="#7b241e" stroke-width="6"/>`;
  }else if(st==='shaokahn'){
    s+=`<path d="M84 140h72v82H84z" fill="#55282a" stroke="#090506" stroke-width="6"/><path d="M89 145h62v17H89z" fill="${metal}"/><path d="M95 171h50" stroke="#a75b4c" stroke-width="9"/>`;
  }else if(st==='sheeva'){
    s+=`<path d="M91 145h58v76H91z" fill="${pri}"/><path d="M102 150h36v66h-36z" fill="${dark}"/>`;
  }else{
    s+=`<path d="M94 146h52v70H94z" fill="${pri}"/><path d="M100 155h40v52h-40z" fill="${dark}" opacity=".55"/>`;
  }
  return s;
}
function headDetails(d,p,skin,metal){
  const x=p.hd[0],y=p.hd[1],st=d.style||'human',pri=d.primary,dark=d.dark;let s=`<ellipse cx="${x}" cy="${y}" rx="27" ry="32" fill="${d.robot?lighter(dark,30):skin}" stroke="#050506" stroke-width="6"/>`;
  if(st==='ninja'||st==='femaleNinja'){
    s+=`<path d="M${x-27} ${y-7}q27-28 54 0v-18q-27-21-54 0z" fill="${dark}"/><path d="M${x-25} ${y-5}h50l-8 22h-34z" fill="${pri}"/><path d="M${x-15} ${y-9}h11M${x+4} ${y-9}h11" stroke="#f1eee0" stroke-width="4"/>`;
  }else if(st==='subzero'){
    s+=`<path d="M${x-25} ${y-7}h50l-7 23h-36z" fill="#18232b"/><path d="M${x-14} ${y-10}h10M${x+4} ${y-10}h10" stroke="#dceaff" stroke-width="4"/><path d="M${x-21} ${y-25}q21-17 42 0" stroke="#2a211b" stroke-width="9" fill="none"/>`;
  }else if(st==='cyborg'){
    s+=`<rect x="${x-24}" y="${y-25}" width="48" height="47" rx="11" fill="${lighter(pri,8)}" stroke="#050506" stroke-width="5"/><path d="M${x-14} ${y-8}h10M${x+4} ${y-8}h10" stroke="#fff47b" stroke-width="4"/><path d="M${x-13} ${y+8}h26" stroke="#16191c" stroke-width="7"/>`;
  }else if(st==='kano'){
    s+=`<path d="M${x+1} ${y-23}q24 5 22 26q-7 18-22 18z" fill="#8e969b"/><circle cx="${x+10}" cy="${y-5}" r="6" fill="#ff2828"/>`;
  }else if(st==='kunglao'){
    s+=`<path d="M${x-42} ${y-28}h84" stroke="#111217" stroke-width="9"/><rect x="${x-21}" y="${y-39}" width="42" height="12" fill="#161719"/>`;
  }else if(st==='kabal'){
    s+=`<rect x="${x-23}" y="${y-18}" width="46" height="36" rx="12" fill="#292c2f" stroke="#050506" stroke-width="4"/><path d="M${x-13} ${y-5}h8M${x+5} ${y-5}h8" stroke="#e5d7b3" stroke-width="4"/>`;
  }else if(st==='stryker'){
    s+=`<path d="M${x-27} ${y-29}h54" stroke="#2a2e35" stroke-width="11"/><path d="M${x+3} ${y-35}h31" stroke="#2a2e35" stroke-width="7"/>`;
  }else if(st==='nightwolf'){
    s+=`<path d="M${x-27} ${y-24}h54" stroke="${pri}" stroke-width="8"/><path d="M${x+18} ${y-25}l31-31M${x+21} ${y-20}l37-19" stroke="#d8c19c" stroke-width="6"/>`;
  }else if(st==='shaokahn'){
    s+=`<path d="M${x-29} ${y-28}h58l5 33-18 19h-32L${x-34} ${y+5}z" fill="#b7b9bb" stroke="#050506" stroke-width="5"/><path d="M${x-22} ${y-25}l-24-24 32 13M${x+22} ${y-25}l24-24-32 13" fill="#777a7c"/><path d="M${x-14} ${y-4}h10M${x+4} ${y-4}h10" stroke="#ffd46c" stroke-width="4"/>`;
  }
  return s;
}
function motaroFrame(d,p){
  const pri=d.primary,dark=d.dark;
  return `<ellipse cx="108" cy="235" rx="82" ry="44" fill="${darker(pri,30)}" stroke="#050506" stroke-width="7"/>${limb([62,242],[51,307],25,dark,lighter(dark,50))}${limb([145,241],[170,307],27,dark,lighter(dark,50))}<path d="M95 204h55v-92H96z" fill="${pri}" stroke="#050506" stroke-width="7"/>${limb([102,132],[65,182],23,pri,lighter(pri,45))}${limb([145,132],[186,180],23,pri,lighter(pri,45))}<ellipse cx="122" cy="85" rx="29" ry="31" fill="#925b3d" stroke="#050506" stroke-width="6"/><rect x="105" y="145" width="36" height="52" rx="5" fill="#94979a" stroke="#050506" stroke-width="4"/><path d="M28 230q-58-35-43-84q7-22-25-40" stroke="#71402a" stroke-width="13" fill="none" stroke-linecap="round"/><path d="M-43 92l20 20-28 8z" fill="#c0c2c3"/>`;
}
function humanFrame(d,p,idx){
  if(d.style==='motaro')return motaroFrame(d,p);
  const female=d.style==='femaleNinja'||d.gender==='female',four=d.style==='sheeva',robot=!!d.robot||d.style==='cyborg';
  const skin=robot?'#666e73':(d.style==='nightwolf'?'#a76d4d':'#b77b59'),metal='#a6adb1';
  const dark=d.dark||'#18181a',pri=d.primary||'#777',leg=darker(dark,0),arm=robot?lighter(dark,28):skin,hi=robot?metal:lighter(skin,32);
  const hip=p.hip,ch=p.ch,hd=p.hd;
  let s=`<ellipse cx="120" cy="307" rx="58" ry="10" fill="rgba(0,0,0,.38)"/>`;
  s+=limb(hip,p.lf,female?25:29,leg,lighter(leg,36))+limb(hip,p.rf,female?25:30,leg,lighter(leg,32));
  s+=boot(p.lf,dark)+boot(p.rf,dark);
  s+=limb(ch,p.lh,female?19:23,arm,hi)+limb(ch,p.rh,female?19:23,arm,hi);
  if(four){s+=limb([ch[0]-20,ch[1]+15],[68,177],19,skin,lighter(skin,35))+limb([68,177],[48,220],18,skin,lighter(skin,35))+limb([ch[0]+20,ch[1]+15],[172,177],19,skin,lighter(skin,35))+limb([172,177],[193,220],18,skin,lighter(skin,35));}
  const shoulder=female?31:(d.style==='shaokahn'?42:37),waist=female?20:27;
  s+=`<path d="M${ch[0]-shoulder} ${ch[1]-8}L${ch[0]+shoulder} ${ch[1]-8}L${hip[0]+waist} ${hip[1]+6}L${hip[0]-waist} ${hip[1]+6}Z" fill="url(#g${idx})" stroke="#050506" stroke-width="6"/>`;
  s+=styleDetails(d,p,skin,metal)+headDetails(d,p,skin,metal);
  s+=`<circle cx="${p.lh[0]}" cy="${p.lh[1]}" r="11" fill="${robot?metal:dark}"/><circle cx="${p.rh[0]}" cy="${p.rh[1]}" r="11" fill="${robot?metal:dark}"/>`;
  return s;
}
function makeAtlas(d){
  const id=esc(d.id||'fighter'),pri=d.primary||'#777',dark=d.dark||'#222';
  let defs='';for(let i=0;i<16;i++)defs+=`<linearGradient id="g${i}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${lighter(pri,55)}"/><stop offset=".45" stop-color="${pri}"/><stop offset="1" stop-color="${darker(pri,55)}"/></linearGradient>`;
  let frames='';for(let i=0;i<16;i++){const x=(i%COLS)*FW,y=((i/COLS)|0)*FH;frames+=`<g transform="translate(${x} ${y})">${humanFrame(d,poses[i],i)}</g>`;}
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${FW*COLS}" height="${FH*2}" viewBox="0 0 ${FW*COLS} ${FH*2}"><title>${id} HD atlas</title><defs>${defs}</defs>${frames}</svg>`;
}
function ensure(d){
  if(!d||typeof Image==='undefined')return null;
  const id=d.id;if(cache.has(id))return cache.get(id);
  const img=new Image();ready.set(id,false);img.onload=()=>ready.set(id,true);img.onerror=()=>ready.set(id,false);
  img.src='data:image/svg+xml;charset=utf-8,'+encodeURIComponent(makeAtlas(d));cache.set(id,img);return img;
}
function frameIndex(f,t){
  if(!f)return 0;if(f.state==='dizzy'||f.state==='hit'||f.state==='frozen'||f.stun>0)return 13;if(f.blocking||f.state==='block')return 14;
  if(f.attack){
    if(f.attack.kind==='special')return 12;
    const id=f.attack.id;if(id==='LP'||id==='jumpPunch')return 9;if(id==='HP'||id==='uppercut'||id==='grab')return 10;if(id==='LK'||id==='sweep')return 11;if(id==='HK'||id==='jumpKick')return 12;
  }
  if(f.air)return f.vy<0?7:8;if(f.crouch)return 6;if(f.state==='run'||f.state==='walk'){const a=[3,4,5,4];return a[(t*(f.state==='run'?12:8)|0)%a.length]}
  if(f.finishPose>0||f.dead)return 15;const a=[0,1,2,1];return a[(t*5|0)%a.length];
}
function drawLocal(ctx,f,t,mini=false){
  const d=f&&f.activeData||f&&f.data;if(!d)return false;const img=ensure(d);if(!img||!ready.get(d.id))return false;
  const idx=frameIndex(f,t),sx=(idx%COLS)*FW,sy=((idx/COLS)|0)*FH;
  const dw=d.style==='motaro'?225:(mini?196:202),dh=d.style==='motaro'?300:(mini?261:270),dx=-dw*.5,dy=-dh+8;
  ctx.drawImage(img,sx,sy,FW,FH,dx,dy,dw,dh);return true;
}
function warm(list){if(!Array.isArray(list))return;for(const d of list)ensure(d)}
function stats(){return{cached:cache.size,ready:[...ready.values()].filter(Boolean).length,frameWidth:FW,frameHeight:FH,frames:16}}
window.UMK3_HD_ATLASES={FW,FH,ensure,drawLocal,warm,stats,makeAtlas};
})();
