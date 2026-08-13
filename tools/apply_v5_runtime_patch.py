#!/usr/bin/env python3
from pathlib import Path

p=Path(__file__).resolve().parents[1]/'web'/'raster-runtime.js'
s=p.read_text(encoding='utf-8')

old="const fighters=new Map(),portraits=new Map(),stages=new Map(),fReady=new Map(),pReady=new Map(),sReady=new Map();"
new="const fighters=new Map(),portraits=new Map(),stages=new Map(),uiImages=new Map(),fReady=new Map(),pReady=new Map(),sReady=new Map(),uReady=new Map();"
assert old in s
s=s.replace(old,new,1)

old="function stageImage(s){return s?img(stages,sReady,s.id,`assets/stages/${encodeURIComponent(s.id)}.png`):null}"
new=old+"\nfunction uiImage(key,src){return img(uiImages,uReady,key,src)}"
assert old in s
s=s.replace(old,new,1)

# Fix fight-state bookkeeping so cache trimming is not needlessly repeated every frame.
old="    activeFightKey=fightKey;"
new="    activeState=state;activeFightKey=fightKey;"
assert old in s
s=s.replace(old,new,1)

start=s.index('function title(){')
end=s.index('function portraitCell',start)
title="""function title(){
  const im=uiImage('title','assets/ui/title-bg.jpg');
  if(im&&uReady.get('title'))ctx.drawImage(im,0,0,W,H);else drawStage(DATA.stages[12]);
  const g=ctx.createLinearGradient(0,250,0,680);g.addColorStop(0,'rgba(0,0,0,.18)');g.addColorStop(.55,'rgba(0,0,0,.52)');g.addColorStop(1,'rgba(0,0,0,.82)');ctx.fillStyle=g;ctx.fillRect(0,0,W,H);
  text('ULTIMATE',W/2,330,42,'center','#e8d9b0');text('MORTAL KOMBAT 3',W/2,397,70,'center','#e0372e');text('PRERENDERED REMASTER',W/2,462,27,'center','#d8c59b');
  rr(W/2-190,548,380,52,10,'rgba(10,10,12,.72)','rgba(226,181,77,.65)',2);text('КОСНИСЬ ЭКРАНА',W/2,574,22,'center','#f4e8c9','Arial');text(`v${DATA.version} • OFFLINE`,W/2,652,14,'center','#b0b3b8','Arial')
}
"""
s=s[:start]+title+s[end:]

start=s.index('function portraitCell')
end=s.index('function select()',start)
portrait="""function portraitCell(d,x,y,w,h){
  const atlas=uiImage('portraits','assets/ui/portrait-atlas.jpg');
  const all=DATA.fighters||DATA.allFighters||DATA.allPlayable||[];
  let idx=all.indexOf(d);if(idx<0)idx=(DATA.allPlayable||[]).indexOf(d);
  if(atlas&&uReady.get('portraits')&&idx>=0){
    const cell=160,cols=7,sx=(idx%cols)*cell,sy=((idx/cols)|0)*cell;
    ctx.save();ctx.beginPath();ctx.rect(x,y,w,h);ctx.clip();ctx.drawImage(atlas,sx,sy,cell,cell,x,y,w,h);ctx.restore();return true;
  }
  const g=ctx.createLinearGradient(x,y,x+w,y+h);g.addColorStop(0,d.dark||'#18191d');g.addColorStop(1,d.primary||'#555');ctx.fillStyle=g;ctx.fillRect(x,y,w,h);return false;
}
"""
s=s[:start]+portrait+s[end:]

old="stageImage(DATA.stages[12]);\nrequestAnimationFrame(render);"
new="uiImage('title','assets/ui/title-bg.jpg');uiImage('portraits','assets/ui/portrait-atlas.jpg');stageImage(DATA.stages[12]);\nrequestAnimationFrame(render);"
assert old in s
s=s.replace(old,new,1)

old="window.__UMK3_RASTER__={canvas,fighters,portraits,stages,fReady,pReady,sReady,drawFighter,drawStage,frameIndex,trimForState,cacheStats:()=>({fighters:fighters.size,portraits:portraits.size,stages:stages.size})};"
new="window.__UMK3_RASTER__={canvas,fighters,portraits,stages,uiImages,fReady,pReady,sReady,uReady,drawFighter,drawStage,frameIndex,trimForState,cacheStats:()=>({fighters:fighters.size,portraits:portraits.size,stages:stages.size,ui:uiImages.size})};"
assert old in s
s=s.replace(old,new,1)

p.write_text(s,encoding='utf-8')
print('RUNTIME_V5_PATCH_OK',p)
