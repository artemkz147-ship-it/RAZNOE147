from pathlib import Path
import re

root=Path('skyfrontiers3d') if Path('skyfrontiers3d').exists() else Path('.')
js_p=root/'game.js'; css_p=root/'style.css'
js=js_p.read_text()
css=css_p.read_text() if css_p.exists() else ''

# New save namespace: old gyro/plane selection must not leak into this build.
js=re.sub(r"const SAVE_KEY = 'sky-frontiers-3d-save-v\d+[^']*';", "const SAVE_KEY = 'sky-frontiers-3d-save-v6-video-fix';", js, count=1)

# Gyro is available, but never enabled/calibrated behind the player's back.
js=re.sub(r"function defaultSave\(\)\{return \{[^\n]*\};\}",
          "function defaultSave(){return {credits:12000, level:1, completed:[], owned:['c172-fg','b777-fg','f16-fg'], selected:'c172-fg', paint:0, totalFlights:0, gyroEnabled:false, gyroSensitivity:0.72};}",
          js, count=1)

# Plane records carry explicit source-axis info. Inferring the nose from the longest dimension broke PA-28/ASK-21.
old="return {id,name,cls,url,price,license,source,maxSpeed,turn,climb,length:opts.length||32,yaw:opts.yaw||0,level:opts.level||1,desc:opts.desc||'', prototype:(opts.prototype??(license.includes('GPL')||license.includes('NC'))),bundled:!!opts.bundled,localFile:opts.localFile||null};"
new="return {id,name,cls,url,price,license,source,maxSpeed,turn,climb,length:opts.length||32,yaw:opts.yaw||0,forwardAxis:opts.forwardAxis||'z',axisSign:opts.axisSign??-1,modelPitch:opts.modelPitch||0,modelRoll:opts.modelRoll||0,cameraFactor:opts.cameraFactor||1,level:opts.level||1,desc:opts.desc||'', prototype:(opts.prototype??(license.includes('GPL')||license.includes('NC'))),bundled:!!opts.bundled,localFile:opts.localFile||null};"
if old not in js: raise SystemExit('plane record marker missing')
js=js.replace(old,new,1)

# Three actually detailed bundled starter aircraft. They are converted from flight-simulator source meshes during CI.
marker="const AIRCRAFT = ["
insert="""const AIRCRAFT = [
  plane('c172-fg','Cessna 172P','light','./models/c172_fg.glb',0,'GPL-2.0','FlightGear C172P',{length:8.28,level:1,desc:'Детализированная Cessna с текстурами · в APK',bundled:true,localFile:'c172_fg.glb',forwardAxis:'x',axisSign:1,cameraFactor:.82}),
  plane('b777-fg','Boeing 777-200ER','airliner','./models/b777_fg.glb',0,'GPL-2.0','FlightGear 777',{length:63.7,level:1,desc:'Полноразмерный дальнемагистральный лайнер · в APK',bundled:true,localFile:'b777_fg.glb',forwardAxis:'x',axisSign:1,cameraFactor:1.08}),
  plane('f16-fg','F-16D Fighting Falcon','military','./models/f16_fg.glb',0,'GPL-compatible','FlightGear F-16',{length:15.0,level:1,desc:'Детализированный истребитель · в APK',bundled:true,localFile:'f16_fg.glb',forwardAxis:'x',axisSign:1,cameraFactor:.9}),"""
if marker not in js: raise SystemExit('aircraft marker missing')
js=js.replace(marker,insert,1)

# Legacy small starter assets remain in the catalogue only as downloadable extras, not as the first impression.
for old_id, price in [('pa28-fr24',5000),('ask21-fr24',7000),('atr42-fr24',18000)]:
    pat=rf"(plane\('{re.escape(old_id)}'.*?,)0,('GPLv2 · glTF2 verified'.*?\{{)([^\n]*?)(\}}\),)"
    m=re.search(pat,js)
    if m:
        opts=m.group(3)
        opts=re.sub(r",?bundled:true",'',opts)
        opts=re.sub(r",?localFile:'[^']+'",'',opts)
        js=js[:m.start()]+m.group(1)+str(price)+","+m.group(2)+opts+m.group(4)+js[m.end():]

# Traffic/mission targets use the detailed C172 bundled model too.
js=js.replace("traffic:'./models/pa28.glb'", "traffic:'./models/c172_fg.glb'")

# Pass source metadata to model normalization.
js=js.replace("normalizeModel(model,p.length); model.rotation.y+=p.yaw;", "normalizeModel(model,p.length,p); model.rotation.y+=p.yaw;")
js=js.replace("normalizeModel(g,14);", "normalizeModel(g,14,{forwardAxis:'x',axisSign:1});")
js=js.replace("normalizeModel(g,8);", "normalizeModel(g,8,{forwardAxis:'x',axisSign:1});")

# Correct scale/orientation. Source axis is explicit; wingspan is never mistaken for fuselage length.
pat=r"function normalizeModel\(model,targetLength\)\{.*?\n\}"
m=re.search(pat,js,flags=re.S)
if not m: raise SystemExit('normalizeModel missing')
normalize=r'''function normalizeModel(model,targetLength,p={}){
  model.rotation.set(Number(p.modelPitch||0),0,Number(p.modelRoll||0));
  if((p.forwardAxis||'z')==='x') model.rotation.y = (p.axisSign??1)>0 ? Math.PI/2 : -Math.PI/2;
  else if((p.forwardAxis||'z')==='z') model.rotation.y = (p.axisSign??-1)>0 ? Math.PI : 0;
  model.updateMatrixWorld(true);
  let box=new THREE.Box3().setFromObject(model),size=box.getSize(new THREE.Vector3());
  const sourceLength=Math.max(.001,size.z);const s=targetLength/sourceLength;model.scale.multiplyScalar(s);
  model.updateMatrixWorld(true);box=new THREE.Box3().setFromObject(model);const center=box.getCenter(new THREE.Vector3());
  model.position.x-=center.x;model.position.z-=center.z;model.position.y-=box.min.y;
  model.traverse(o=>{if(o.isMesh){o.castShadow=true;o.receiveShadow=true;if(o.material){o.material=o.material.clone();o.material.envMapIntensity=1.05;o.material.roughness=Math.min(.82,o.material.roughness??.72);}}});
}'''
js=js[:m.start()]+normalize+js[m.end():]

# Flatten a broad airport plateau. The old runway floated through a sloping terrain mesh and the camera could start underneath it.
pat=r"function terrainHeight\(x,z\)\{.*?\n\}"
m=re.search(pat,js,flags=re.S)
if not m: raise SystemExit('terrainHeight missing')
terrain=r'''function terrainHeight(x,z){
  const coast=Math.max(0,1-Math.pow(Math.hypot(x*.72,z)/14500,3.6));
  const ridge=980*Math.exp(-((x+3200)**2)/(2*3200**2))*Math.exp(-((z-1800)**2)/(2*6500**2));
  const peaks=1200*Math.exp(-((x-3800)**2+(z+2600)**2)/(2*2700**2))+820*Math.exp(-((x+6000)**2+(z-5000)**2)/(2*2200**2));
  const rolling=95*(Math.sin(x/980)+Math.sin(z/1270)+Math.sin((x+z)/1700))*coast;
  let h=Math.max(-18,(70+ridgesafe(ridge)+peaks+rolling)*coast-15);
  const flatten=(cx,cz,rx,rz,target)=>{const d=Math.max(Math.abs((x-cx)/rx),Math.abs((z-cz)/rz));if(d<1){const t=THREE.MathUtils.smoothstep(d,.68,1);h=THREE.MathUtils.lerp(target,h,t);}};
  flatten(-6500,-2200,1450,2350,42);
  flatten(5200,5600,850,1550,155);
  return h;
}'''
js=js[:m.start()]+terrain+js[m.end():]

# Safe runway start: stationary, level, above the runway surface, gyro neutral.
pat=r"function resetAtRunway\(\)\{.*?\n\}"
m=re.search(pat,js,flags=re.S)
if not m: raise SystemExit('resetAtRunway missing')
reset=r'''function resetAtRunway(){
  state.speed=0;state.throttle=0;state.pitch=0;state.roll=0;state.yaw=Math.PI;state.gear=true;els.warning.textContent='';
  state.touchPitch=0;state.touchRoll=0;
  if(state.gyro){state.gyro.calibrated=false;state.gyro.basePitch=state.gyro.rawPitch;state.gyro.baseRoll=state.gyro.rawRoll;}
  if(!state.planeVisual)return;
  const x=-6500,z=-2850,y=terrainHeight(x,z)+6.2;
  state.planeVisual.position.set(x,y,z);state.planeVisual.rotation.order='YXZ';state.planeVisual.rotation.set(0,Math.PI,0);
  const len=state.planeAsset?.length||9;camera.position.set(x,y+Math.max(5,len*.42),z-Math.max(14,len*1.45));camera.lookAt(x,y+2,z+8);
}'''
js=js[:m.start()]+reset+js[m.end():]

# Chase camera scales with the actual aircraft. Small aircraft are no longer a tiny stick 30m away.
pat=r"function updateCamera\(dt\)\{.*?\n\}"
m=re.search(pat,js,flags=re.S)
if not m: raise SystemExit('updateCamera missing')
camera=r'''function updateCamera(dt){
  if(!state.planeVisual)return;const q=state.planeVisual.quaternion,pos=state.planeVisual.position,p=state.planeAsset||AIRCRAFT[0];
  const len=Math.max(6,p.length||10),cf=p.cameraFactor||1;let local,lookLocal;
  if(state.cameraMode===0){const back=THREE.MathUtils.clamp(len*1.55*cf,12,125),up=THREE.MathUtils.clamp(len*.42,4.2,30);local=new THREE.Vector3(0,up,back);lookLocal=new THREE.Vector3(0,Math.max(1.5,len*.08),-len*.7);}
  else if(state.cameraMode===1){local=new THREE.Vector3(0,Math.max(1.4,len*.10),Math.max(.8,len*.08));lookLocal=new THREE.Vector3(0,Math.max(1.2,len*.07),-len*1.2);}
  else {local=new THREE.Vector3(len*.8,Math.max(8,len*.32),Math.max(22,len*1.8));lookLocal=new THREE.Vector3(0,len*.08,-len*.8);}
  local.applyQuaternion(q);lookLocal.applyQuaternion(q);const desired=pos.clone().add(local);
  const safeGround=terrainHeight(desired.x,desired.z)+2.5;if(desired.y<safeGround)desired.y=safeGround;
  camera.position.lerp(desired,1-Math.exp(-dt*(state.cameraMode===1?12:6.2)));camera.lookAt(pos.clone().add(lookLocal));
}'''
js=js[:m.start()]+camera+js[m.end():]

# More stable mobile controls: gyro has lower gain and a dead zone.
js=re.sub(r"function gyroPitchInput\(\)\{.*?\}",
          "function gyroPitchInput(){const g=state.gyro;if(!g.enabled||!g.available||!g.calibrated)return 0;let v=-(g.rawPitch-g.basePitch)*.78*g.sensitivity;if(Math.abs(v)<.045)v=0;return clamp(v,-.72,.72);}", js, count=1)
js=re.sub(r"function gyroRollInput\(\)\{.*?\}",
          "function gyroRollInput(){const g=state.gyro;if(!g.enabled||!g.available||!g.calibrated)return 0;let v=(g.rawRoll-g.baseRoll)*.92*g.sensitivity;if(Math.abs(v)<.045)v=0;return clamp(v,-.82,.82);}", js, count=1)

js=js.replace("toast(gt.checked?'Гироскоп включён':'Гироскоп выключен');", "if(gt.checked){state.gyro.calibrated=false;toast('Гироскоп включён · держите телефон удобно и нажмите CAL');}else toast('Гироскоп выключен');")

css += r'''

/* Video QA fix: compact landscape phone HUD and controls */
@media (orientation:landscape) and (max-height:620px){
  .hud-left{left:12px;top:10px;max-width:42vw}.brand{font-size:8px}.objective{font-size:18px;margin-top:2px}.subobjective{font-size:9px;margin-top:2px;max-width:40vw}
  .hud-right{right:10px;top:8px;gap:4px}.instrument{min-width:70px;padding:6px 8px;border-radius:10px}.instrument small{font-size:6px}.instrument b{font-size:16px}.instrument span{font-size:7px}
  #minimap{right:12px;bottom:55px;width:116px;height:116px}.quick-actions{left:12px;bottom:9px;transform:none}.quick-actions button{padding:7px 9px;font-size:8px;border-radius:9px}
  .stick{left:20px;bottom:50px;width:104px;height:104px}.stick i{width:42px;height:42px}.touch-right{right:18px;bottom:10px;grid-template-columns:48px 48px;gap:6px}.touch-right button{height:46px;font-size:18px}
  .gyro-quick{right:126px;bottom:14px;width:54px;height:42px;font-size:9px}.warning{font-size:18px;top:22%}.hint{display:none}
}
'''

for required in ["c172_fg.glb","b777_fg.glb","f16_fg.glb","forwardAxis","safeGround","gyroEnabled:false"]:
    if required not in js and required not in css: raise SystemExit('missing QA marker '+required)

js_p.write_text(js)
if css_p.exists(): css_p.write_text(css)
print('Applied video QA / detailed-aircraft patch')
