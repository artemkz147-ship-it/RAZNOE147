const fs=require('fs'),vm=require('vm');
const listeners={window:{},document:{},canvas:{},pause:{},buttons:{}};
function grad(){return {addColorStop(){}}}
const ctx=new Proxy({
  createLinearGradient:grad,createRadialGradient:grad,
  save(){},restore(){},beginPath(){},closePath(){},fill(){},stroke(){},fillRect(){},strokeRect(){},clearRect(){},moveTo(){},lineTo(){},bezierCurveTo(){},arc(){},ellipse(){},translate(){},scale(){},rotate(){},roundRect(){},rect(){},fillText(){},setTransform(){},drawImage(){},
}, {set(o,p,v){o[p]=v;return true},get(o,p){if(p in o)return o[p];return ()=>{}}});
function classList(){const s=new Set();return{add(x){s.add(x)},remove(x){s.delete(x)},toggle(x,v){if(v===undefined){s.has(x)?s.delete(x):s.add(x)}else v?s.add(x):s.delete(x)},contains(x){return s.has(x)}}}
function el(kind='el'){listeners[kind]??={};return{style:{},dataset:{},classList:classList(),children:[],nextSibling:null,setAttribute(){},insertBefore(n){this.children.push(n)},appendChild(n){this.children.push(n)},addEventListener(t,f){(listeners[kind][t]??=[]).push(f)},getContext(){return ctx},canGoBack(){return false},remove(){}}}
const canvas=el('canvas');canvas.width=1280;canvas.height=720;
const app=el('app'),touchUI=el('touch'),pause=el('pause');
const keys=['left','up','right','down','RUN','BL','HP','LP','HK','LK','SPECIAL'];
const buttons=keys.map((k,i)=>{const b=el('b'+i);b.dataset.key=k;return b});
let raf=[];
global.window=global;global.innerWidth=1280;global.innerHeight=720;global.devicePixelRatio=1;
global.navigator={vibrate(){}};global.localStorage={getItem(){return null},setItem(){}};
global.Image=class{constructor(){this.onload=null;this.onerror=null;this.complete=false;this.naturalWidth=1920}set src(v){this._src=v;this.complete=true;if(this.onload)this.onload()}get src(){return this._src}};
global.addEventListener=(t,f)=>(listeners.window[t]??=[]).push(f);
global.document={
  getElementById(id){return id==='game'?canvas:id==='app'?app:id==='touch-ui'?touchUI:id==='pause'?pause:null},
  createElement(tag){const e=el('created-'+tag);if(tag==='canvas'){e.width=1280;e.height=720}return e},
  querySelectorAll(sel){return sel==='.touch'?buttons:[]},
  addEventListener(t,f){(listeners.document[t]??=[]).push(f)},hidden:false,body:{children:[{}],appendChild(){}}
};
global.requestAnimationFrame=(fn)=>{raf.push(fn);return raf.length};
for(const file of ['boot-guard.js','umk3-data.js','version.js','hd-atlases.js','game.js','hd-renderer.js','raster-runtime.js']){
  const code=fs.readFileSync(process.cwd()+'/web/'+file,'utf8');vm.runInThisContext(code,{filename:file});
}
if(global.UMK3_BUILD?.version!=='0.7.0'||global.UMK3_DATA.version!=='0.7.0')throw new Error('0.7.0 build metadata missing');
if(global.UMK3_BUILD?.renderer!=='prerendered-48f-hd-v4-layered-stages')throw new Error('Real-art renderer metadata missing');
if(!global.__UMK3_RASTER__)throw new Error('Raster runtime missing');
let ts=0;
function step(n=1){for(let i=0;i<n;i++){const q=raf;raf=[];ts+=16.6667;for(const fn of q)fn(ts)}}
function key(code,type='keydown'){const ev={code,preventDefault(){}};(listeners.window[type]||[]).forEach(fn=>fn(ev))}
function tapCanvas(){(listeners.canvas.pointerdown||[]).forEach(fn=>fn({preventDefault(){}}))}
step(3);if(!raf.length)throw new Error('RAF dead on title');
if(!global.__UMK3_HD_RENDERER__)throw new Error('HD renderer missing');
if(!global.UMK3_HD_ATLASES)throw new Error('HD atlas runtime missing');
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
if(!raf.length)throw new Error('RAF stopped during raster fight');
const g=global.__UMK3_DEBUG__.game;
if(!g.player||!g.enemy)throw new Error('Fighters missing');
if(window.UMK3_DATA.allPlayable.length<25)throw new Error('Roster incomplete');
if(window.UMK3_DATA.stages.length<16)throw new Error('Stages incomplete');
if(global.__UMK3_RASTER__.fReady.size<25||global.__UMK3_RASTER__.sReady.size<16)throw new Error('Raster caches incomplete');
console.log(`SMOKE_REALART_OK version=${window.UMK3_DATA.version} state=${g.state} roster=${window.UMK3_DATA.allPlayable.length} stages=${window.UMK3_DATA.stages.length} fighterCache=${global.__UMK3_RASTER__.fReady.size}`);
