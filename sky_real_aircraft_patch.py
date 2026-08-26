from pathlib import Path
import re

root=Path('skyfrontiers3d') if Path('skyfrontiers3d').exists() else Path('.')
js_p=root/'game.js'
js=js_p.read_text()

# New save namespace so an old invalid/procedural selection cannot survive upgrade.
js=js.replace("const SAVE_KEY = 'sky-frontiers-3d-save-v4-quality';", "const SAVE_KEY = 'sky-frontiers-3d-save-v5-real-aircraft';")

# Every legacy FR24 aircraft now points to our verified glTF 2 conversion branch.
old="const FR24='https://raw.githubusercontent.com/Flightradar24/fr24-3d-models/master/models/';"
new="const FR24='https://raw.githubusercontent.com/artemkz147-ship-it/RAZNOE147/sky-models-v2/models/';"
if old not in js:
    raise SystemExit('FR24 URL marker missing')
js=js.replace(old,new)

# The CC0 Quaternius airplane is intentionally stylized; remove it from a realism-focused fleet.
js=re.sub(r"\n\s*plane\('small-quat'.*?\),", "", js)

# Do not ever substitute primitive geometry for a player aircraft.
old_boot="try{await selectPlane(chosen,false);}catch(e){console.warn(e);await selectPlane(AIRCRAFT[0],false).catch(()=>spawnEmergencyPlaceholder(AIRCRAFT[0]));}"
new_boot="""let loadedReal=false;
  for(const p of [chosen,...AIRCRAFT.filter(x=>x.bundled&&x.id!==chosen.id)]){
    try{await selectPlane(p,false);loadedReal=true;break}catch(e){console.error('Real aircraft failed',p.id,e)}
  }
  if(!loadedReal){setBoot(100,'Ошибка: настоящие 3D-модели самолётов не загрузились');throw new Error('No real aircraft model could be loaded')}"""
if old_boot not in js:
    raise SystemExit('boot fallback marker missing')
js=js.replace(old_boot,new_boot)

# Delete emergency box-and-wing aircraft fallback entirely.
js=re.sub(r"\nfunction spawnEmergencyPlaceholder\(p\)\{.*?\n\}\n", "\n", js, flags=re.S)

# Target aircraft must also be a real GLB mesh; never a capsule/primitive.
pat=r"function createTargetAircraft\(\)\{.*?\n\}"
m=re.search(pat,js,flags=re.S)
if not m:
    raise SystemExit('target aircraft function missing')
real_target="""function createTargetAircraft(){
  const src=quality.models.traffic;
  if(!src)throw new Error('Real target aircraft GLB is unavailable');
  const g=src.clone(true);normalizeModel(g,14);g.position.set(-2000,900,1500);
  g.traverse(o=>{if(o.isMesh){o.castShadow=true;o.receiveShadow=true}});trafficGroup.add(g);return g;
}"""
js=js[:m.start()]+real_target+js[m.end():]

# Carrier must be the authored GLB too; no emergency box carrier.
old_carrier="""function createCarrier(x,z,rot){
  const src=quality.models.carrier;if(src){const g=src.clone(true);g.position.set(x,-5,z);g.rotation.y=rot;g.traverse(o=>{if(o.isMesh){o.castShadow=true;o.receiveShadow=true;if(o.material?.name==='deck'&&quality.textures.asphaltD){o.material=o.material.clone();o.material.map=quality.textures.asphaltD;o.material.normalMap=quality.textures.asphaltN;o.material.roughnessMap=quality.textures.asphaltR;o.material.map.repeat.set(2,16);o.material.normalMap?.repeat.set(2,16);o.material.roughnessMap?.repeat.set(2,16)}}});worldGroup.add(g);state.carrier=g;return}
  const g=new THREE.Group();g.position.set(x,-6,z);g.rotation.y=rot;const hull=new THREE.Mesh(new THREE.BoxGeometry(150,32,690),new THREE.MeshStandardMaterial({color:0x46515a,roughness:.75,metalness:.25}));g.add(hull);worldGroup.add(g);state.carrier=g;
}"""
if old_carrier in js:
    new_carrier="""function createCarrier(x,z,rot){
  const src=quality.models.carrier;if(!src){console.error('Authored carrier GLB missing');return}
  const g=src.clone(true);g.position.set(x,-5,z);g.rotation.y=rot;g.traverse(o=>{if(o.isMesh){o.castShadow=true;o.receiveShadow=true;if(o.material?.name==='deck'&&quality.textures.asphaltD){o.material=o.material.clone();o.material.map=quality.textures.asphaltD;o.material.normalMap=quality.textures.asphaltN;o.material.roughnessMap=quality.textures.asphaltR;o.material.map.repeat.set(2,16);o.material.normalMap?.repeat.set(2,16);o.material.roughnessMap?.repeat.set(2,16)}}});worldGroup.add(g);state.carrier=g;
}"""
    js=js.replace(old_carrier,new_carrier)

# Make the converted source explicit in the hangar.
js=js.replace("'GPLv2 prototype','FR24'", "'GPLv2 · glTF2 verified','FR24 converted'")

# Hard guard: no 3D primitive aircraft fallback may survive the patch.
for forbidden in ['spawnEmergencyPlaceholder','CapsuleGeometry(8,54','аварийный контур']:
    if forbidden in js:
        raise SystemExit('Forbidden aircraft fallback remains: '+forbidden)

js_p.write_text(js)

# Remove the primitive parked jets that were baked into the carrier model.
carrier=Path('make_carrier.py')
if carrier.exists():
    s=carrier.read_text()
    marker='def add_jet(x,z,heading,idx,scale=1.0):'
    end_marker='for i,z in enumerate([-250,-145,-45,75,185,270]):'
    if marker in s and end_marker in s:
        start=s.index(marker)
        # include the line immediately before the helper when it is the parked-jet comment
        prev=s.rfind('\n',0,start)
        if prev>=0 and 'jet' in s[prev:start].lower(): start=prev+1
        end=s.index(end_marker,start)
        s=s[:start]+"# No primitive parked aircraft. Deck aircraft are shown only from real GLB assets.\n"+s[end:]
        carrier.write_text(s)

print('Applied real-aircraft-only patch')
