(() => {
'use strict';
const coarse=matchMedia?.('(pointer:coarse)')?.matches || /Android|Mobile/i.test(navigator.userAgent||'');
const root=document.documentElement;
const pad=document.getElementById('move-pad');
const knob=document.getElementById('move-knob');
const gear=document.getElementById('controls-gear');
const panel=document.getElementById('controls-panel');
if(!coarse){ if(gear)gear.hidden=true; return; }

const CODE={left:'ArrowLeft',right:'ArrowRight',up:'ArrowUp',down:'ArrowDown'};
const held=new Set();
let padPointer=null;
const dispatch=(code,down)=>{
  window.dispatchEvent(new KeyboardEvent(down?'keydown':'keyup',{code,bubbles:true,cancelable:true}));
};
const setDir=(next)=>{
  for(const k of ['left','right','up','down']){
    const want=next.has(k),has=held.has(k);
    if(want&&!has){held.add(k);dispatch(CODE[k],true)}
    if(!want&&has){held.delete(k);dispatch(CODE[k],false)}
  }
};
const reset=()=>{setDir(new Set());if(knob)knob.style.transform='translate(-50%,-50%)'};
const updatePad=e=>{
  if(!pad)return;
  const r=pad.getBoundingClientRect();
  let dx=e.clientX-(r.left+r.width/2),dy=e.clientY-(r.top+r.height/2);
  const len=Math.hypot(dx,dy),max=r.width*.31;
  if(len>max){dx=dx/len*max;dy=dy/len*max}
  if(knob)knob.style.transform=`translate(calc(-50% + ${dx}px),calc(-50% + ${dy}px))`;
  const dead=r.width*.10;const d=new Set();
  if(dx>dead)d.add('right');else if(dx<-dead)d.add('left');
  if(dy>dead)d.add('down');else if(dy<-dead)d.add('up');
  setDir(d);
};
if(pad){
  pad.addEventListener('pointerdown',e=>{e.preventDefault();padPointer=e.pointerId;pad.setPointerCapture?.(e.pointerId);updatePad(e);navigator.vibrate?.(8)},{passive:false});
  pad.addEventListener('pointermove',e=>{if(e.pointerId===padPointer){e.preventDefault();updatePad(e)}},{passive:false});
  const end=e=>{if(e.pointerId===padPointer){padPointer=null;reset()}};
  pad.addEventListener('pointerup',end);pad.addEventListener('pointercancel',end);pad.addEventListener('lostpointercapture',()=>{padPointer=null;reset()});
}

// Make action buttons resilient to multi-touch cancellation / finger sliding.
for(const b of document.querySelectorAll('.touch')){
  b.addEventListener('pointerdown',e=>{b.setPointerCapture?.(e.pointerId);navigator.vibrate?.(6)},{passive:true});
  b.addEventListener('contextmenu',e=>e.preventDefault());
}
window.addEventListener('blur',reset);document.addEventListener('visibilitychange',()=>{if(document.hidden)reset()});

const KEY='umk3hd.mobile.controls.v1';
let cfg={scale:1,opacity:.82,buttons:1};
try{cfg={...cfg,...JSON.parse(localStorage.getItem(KEY)||'{}')}}catch(_){}
const apply=()=>{
  root.style.setProperty('--control-scale',String(cfg.scale));
  root.style.setProperty('--control-opacity',String(cfg.opacity));
  root.style.setProperty('--button-scale',String(cfg.buttons));
};
const save=()=>{try{localStorage.setItem(KEY,JSON.stringify(cfg))}catch(_){}apply()};
apply();

if(gear&&panel){
  gear.hidden=false;
  gear.addEventListener('pointerdown',e=>{e.preventDefault();panel.classList.toggle('open');navigator.vibrate?.(10)});
  const bind=(id,key,min,max)=>{
    const el=document.getElementById(id);if(!el)return;el.value=cfg[key];
    el.addEventListener('input',()=>{cfg[key]=Math.max(min,Math.min(max,Number(el.value)));save()});
  };
  bind('control-size','scale',.72,1.28);bind('control-opacity','opacity',.38,1);bind('button-size','buttons',.78,1.24);
  document.getElementById('controls-reset')?.addEventListener('pointerdown',()=>{cfg={scale:1,opacity:.82,buttons:1};save();for(const id of ['control-size','control-opacity','button-size']){const e=document.getElementById(id);if(e)e.value=id==='control-opacity'?.82:1}});
}
})();
