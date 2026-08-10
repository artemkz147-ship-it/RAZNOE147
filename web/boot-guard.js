(() => {
  'use strict';
  const errors=[];
  function show(message){
    errors.push(String(message));
    let el=document.getElementById('boot-error');
    if(!el){
      el=document.createElement('pre');el.id='boot-error';
      Object.assign(el.style,{position:'fixed',left:'12px',right:'12px',bottom:'12px',zIndex:'99999',maxHeight:'40vh',overflow:'auto',padding:'12px',margin:'0',background:'rgba(90,0,0,.92)',color:'#fff',font:'14px monospace',whiteSpace:'pre-wrap',pointerEvents:'none'});
      document.addEventListener('DOMContentLoaded',()=>document.body.appendChild(el),{once:true});
    }
    el.textContent='UMK3 boot error\n'+errors.slice(-6).join('\n');
  }
  window.addEventListener('error',e=>show(`${e.message} @ ${e.filename||'?'}:${e.lineno||0}`));
  window.addEventListener('unhandledrejection',e=>show(`Promise: ${e.reason||'unknown'}`));
  window.__UMK3_BOOT_GUARD__={errors,show};
})();
