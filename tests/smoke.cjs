const fs=require('fs'),vm=require('vm');
const listeners={window:{},document:{},canvas:{},pause:{},buttons:{}};
function grad(){return {addColorStop(){}}}
const ctx=new Proxy({
  createLinearGradient:grad,createRadialGradient:grad,
  save(){},restore(){},beginPath(){},closePath(){},fill(){},stroke(){},fillRect(){},strokeRect(){},clearRect(){},moveTo(){},lineTo(){},bezierCurveTo(){},arc(){},ellipse(){},translate(){},scale(){},rotate(){},roundRect(){},rect(){},fillText(){},setTransform(){},
}, {set(o,p,v){o[p]=v;return true},get(o,p){if(p in o)return o[p];return ()=>{}}});
function classList(){const s=new Set();return{add(x){s.add(x)},remove(x){s.delete(x)},toggle(x,v){if(v===undefined){s.has(x)?s.delete(x):s.add(x)}else v?s.add(x):s.delete(x)},contains(x){return s.has(x)}}}
function el(kind='el'){listeners[kind]??={};return{style:{},dataset:{},classList:classList(),children:[],addEventListener(t,f){(listeners[kind][t]??=[]).push(f)},getContext(){return ctx},canGoBack(){return false}}}
const canvas=el('canvas');canvas.width=1280;canvas.height=720;
const touchUI=el('touch'),pause=el('pause');
const keys=['left','up','right','down','RUN','BL','HP','LP','HK','LK','SPECIAL'];
const buttons=keys.map((k,i)=>{const b=el('b'+i);b.dataset.key=k;return b});
let raf=[];
global.window=global;global.innerWidth=1280;global.innerHeight=720;global.devicePixelRatio=1;
global.navigator={vibrate(){}};
global.localStorage={getItem(){return null},setItem(){}};
global.addEventListener=(t,f)=>(listeners.window[t]??=[]).push(f);
global.document={
  getElementById(id){return id==='game'?canvas:id==='touch-ui'?touchUI:id==='pause'?pause:null},
  querySelectorAll(sel){return sel==='.touch'?buttons:[]},
  addEventListener(t,f){(listeners.document[t]??=[]).push(f)},hidden:false,body:{children:[{}]}
};
global.requestAnimationFrame=(fn)=>{raf.push(fn);return raf.length};
const data=fs.readFileSync(process.cwd()+'/web/umk3-data.js','utf8');
const code=fs.readFileSync(process.cwd()+'/web/game.js','utf8');
vm.runInThisContext(data,{filename:'umk3-data.js'});vm.runInThisContext(code,{filename:'game.js'});
let ts=0;
function step(n=1){for(let i=0;i<n;i++){const q=raf;raf=[];ts+=16.6667;for(const fn of q)fn(ts)}}
function key(code,type='keydown'){const ev={code,preventDefault(){}};(listeners.window[type]||[]).forEach(fn=>fn(ev))}
function tapCanvas(){(listeners.canvas.pointerdown||[]).forEach(fn=>fn({preventDefault(){}}))}
step(3);if(!raf.length)throw new Error('RAF dead on title');
tapCanvas();step(3);
if(global.__UMK3_DEBUG__.game.state!=='select')throw new Error('Title -> select failed');
key('Enter');step(2);key('Enter','keyup');step(3);
if(global.__UMK3_DEBUG__.game.state!=='tower')throw new Error('Select -> tower failed');
key('Enter');step(2);key('Enter','keyup');step(5);
if(global.__UMK3_DEBUG__.game.state!=='fight')throw new Error('Tower -> fight failed');
step(170);
if(!touchUI.classList.contains('show'))throw new Error('Touch UI hidden in fight');
key('KeyJ');step(2);key('KeyJ','keyup');step(30);
key('KeyO');key('KeyD');step(5);key('KeyO','keyup');key('KeyD','keyup');step(10);
if(!raf.length)throw new Error('RAF stopped during fight');
const g=global.__UMK3_DEBUG__.game;
if(!g.player||!g.enemy)throw new Error('Fighters missing');
if(window.UMK3_DATA.allPlayable.length<25)throw new Error('Roster incomplete');
if(window.UMK3_DATA.stages.length<16)throw new Error('Stages incomplete');
console.log(`SMOKE_OK state=${g.state} roster=${window.UMK3_DATA.allPlayable.length} stages=${window.UMK3_DATA.stages.length}`);
