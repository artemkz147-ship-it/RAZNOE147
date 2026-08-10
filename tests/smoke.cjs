const fs=require('fs'), vm=require('vm');
const listeners={window:{},document:{},canvas:{},pause:{}};
function grad(){return {addColorStop(){}}}
const ctx=new Proxy({
  createLinearGradient:grad, createRadialGradient:grad,
  save(){},restore(){},beginPath(){},closePath(){},fill(){},stroke(){},fillRect(){},strokeRect(){},clearRect(){},moveTo(){},lineTo(){},bezierCurveTo(){},arc(){},ellipse(){},translate(){},scale(){},rotate(){},roundRect(){},fillText(){},setTransform(){},
}, {set(o,p,v){o[p]=v;return true;},get(o,p){if(p in o)return o[p];return ()=>{};}});
function classList(){const s=new Set();return {add(x){s.add(x)},remove(x){s.delete(x)},contains(x){return s.has(x)},toString(){return [...s].join(' ')}}}
function el(kind='el'){return {style:{},dataset:{},classList:classList(),children:[],addEventListener(type,fn){(listeners[kind][type]??=[]).push(fn)},getContext(){return ctx},canGoBack(){return false}}}
const canvas=el('canvas'); canvas.width=1280;canvas.height=720;
const touchUI=el('touch'); listeners.touch={};
const pause=el('pause');
const keys=['left','up','right','down','run','punch','kick','block','special'];
const buttons=keys.map(k=>{const b=el('btn');b.dataset.key=k;listeners.btn??={};return b});
let raf=[];
global.window=global;
global.innerWidth=1280;global.innerHeight=720;global.devicePixelRatio=1;
global.addEventListener=(t,f)=>(listeners.window[t]??=[]).push(f);
global.document={
  getElementById(id){return id==='game'?canvas:id==='touch-ui'?touchUI:id==='pause'?pause:null},
  querySelectorAll(sel){return sel==='.touch'?buttons:[]},
  addEventListener(t,f){(listeners.document[t]??=[]).push(f)},
  hidden:false, body:{children:[{}]}
};
global.requestAnimationFrame=(fn)=>{raf.push(fn);return raf.length};
const code=fs.readFileSync(process.cwd()+'/web/game.js','utf8');
vm.runInThisContext(code,{filename:'game.js'});
let now=0;
function step(n=1){for(let i=0;i<n;i++){const q=raf;raf=[];now+=16.6667;for(const fn of q)fn(now)}}
function key(code,type='keydown'){const ev={code,preventDefault(){}};(listeners.window[type]||[]).forEach(fn=>fn(ev))}
function tapCanvas(){(listeners.canvas.pointerdown||[]).forEach(fn=>fn({preventDefault(){}}))}
step(2);
if(!raf.length) throw new Error('RAF loop not alive after title');
tapCanvas();step(2);
key('Enter','keydown');step(1);key('Enter','keyup');step(3);
step(60);
key('Enter','keydown');step(1);key('Enter','keyup');step(150);
if(!touchUI.classList.contains('show')) throw new Error('Fight did not expose touch UI');
key('KeyJ','keydown');step(1);key('KeyJ','keyup');step(20);
key('KeyI','keydown');step(1);key('KeyI','keyup');step(20);
if(!raf.length) throw new Error('RAF loop stopped after combat input');
console.log('SMOKE_OK title->select->tower->fight; touch UI active; punch/special input path alive');
