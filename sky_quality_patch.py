from pathlib import Path
import re
root=Path('skyfrontiers3d') if Path('skyfrontiers3d').exists() else Path('.')
js_p=root/'game.js'; html_p=root/'index.html'; css_p=root/'style.css'
js=js_p.read_text(); html=html_p.read_text(); css=css_p.read_text()

# RGBE environment support.
if "RGBELoader" not in js:
    js=js.replace("import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';", "import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';\nimport { RGBELoader } from 'three/addons/loaders/RGBELoader.js';")

# Quality revision + larger world render distance.
js=js.replace("const SAVE_KEY = 'sky-frontiers-3d-save-v3';", "const SAVE_KEY = 'sky-frontiers-3d-save-v4-quality';")
js=js.replace("renderer.setPixelRatio(Math.min(devicePixelRatio,1.7)); renderer.setSize(innerWidth,innerHeight);", "renderer.setPixelRatio(Math.min(devicePixelRatio,1.85)); renderer.setSize(innerWidth,innerHeight);")
js=js.replace("renderer.toneMappingExposure=1.0;", "renderer.toneMappingExposure=1.08;")
js=js.replace("const scene=new THREE.Scene(); scene.background=new THREE.Color(0x7fc0e8); scene.fog=new THREE.FogExp2(0xa7d5ee,0.000055);", "const scene=new THREE.Scene(); scene.background=new THREE.Color(0x86bee1); scene.fog=new THREE.FogExp2(0xa3cae0,0.000043);")
js=js.replace("const camera=new THREE.PerspectiveCamera(66,innerWidth/innerHeight,.2,70000);", "const camera=new THREE.PerspectiveCamera(66,innerWidth/innerHeight,.2,85000);")
js=js.replace("const loader=new GLTFLoader();", "const loader=new GLTFLoader();\nconst rgbeLoader=new RGBELoader();\nconst textureLoader=new THREE.TextureLoader();\nconst maxAniso=Math.min(8,renderer.capabilities.getMaxAnisotropy());\nconst quality={textures:{},models:{},water:[],traffic:[]};")

# Expand state with traffic.
js=js.replace("freeBeacons:[], cameraShake:0, elapsed:0, downloadedModels:new Set(), downloadProgress:new Map(),", "freeBeacons:[], cameraShake:0, elapsed:0, downloadedModels:new Set(), downloadProgress:new Map(), airTraffic:[],")

# Boot preloads authored/local graphics pack before world creation.
old="""async function boot(){
  setBoot(8,'Создаю фиксированную карту 30 × 30 км...');
  createLighting(); createWorld(); createFreeBeacons();
  setBoot(42,'Готовлю аэродромы, деревни, горы и авианосец...');"""
new="""async function boot(){
  setBoot(5,'Загружаю локальный HD-набор окружения...');
  await preloadQualityAssets();
  setBoot(24,'Строю детализированную карту 30 × 30 км...');
  createLighting(); createWorld(); createFreeBeacons(); createAirTraffic();
  setBoot(48,'Готовлю аэродромы, деревни, горы и авианосец...');"""
if old not in js: raise SystemExit('boot marker missing')
js=js.replace(old,new)
js=js.replace("setBoot(64,'Загружаю выбранную 3D-модель...');", "setBoot(72,'Загружаю выбранную 3D-модель...');")

# Insert asset helpers before lighting.
marker="function createLighting(){"
idx=js.index(marker)
assets=r'''
function configureTexture(tex,{repeat=[1,1],srgb=false}={}){
  tex.wrapS=tex.wrapT=THREE.RepeatWrapping;tex.repeat.set(repeat[0],repeat[1]);tex.anisotropy=maxAniso;
  if(srgb)tex.colorSpace=THREE.SRGBColorSpace;return tex;
}
async function tex(url,opts){try{return configureTexture(await textureLoader.loadAsync(url),opts)}catch(e){console.warn('texture',url,e);return null}}
async function glb(url){try{const g=await loader.loadAsync(url);g.scene.traverse(o=>{if(o.isMesh){o.castShadow=true;o.receiveShadow=true;if(o.material?.map)o.material.map.anisotropy=maxAniso;}});return g.scene}catch(e){console.warn('model',url,e);return null}}
async function preloadQualityAssets(){
  const T='./quality/';
  const jobs=[
    ['grassD',T+'aerial_grass_rock_diff_1k.png',{repeat:[72,72],srgb:true}],['grassN',T+'aerial_grass_rock_nor_gl_1k.png',{repeat:[72,72]}],['grassR',T+'aerial_grass_rock_rough_1k.png',{repeat:[72,72]}],
    ['asphaltD',T+'aerial_asphalt_01_diff_1k.png',{repeat:[1,12],srgb:true}],['asphaltN',T+'aerial_asphalt_01_nor_gl_1k.png',{repeat:[1,12]}],['asphaltR',T+'aerial_asphalt_01_rough_1k.png',{repeat:[1,12]}],
    ['dirtD',T+'dirt_aerial_02_diff_1k.png',{repeat:[1,12],srgb:true}],['dirtN',T+'dirt_aerial_02_nor_gl_1k.png',{repeat:[1,12]}],['dirtR',T+'dirt_aerial_02_rough_1k.png',{repeat:[1,12]}],
    ['rockD',T+'rock_ground_02_diff_1k.png',{repeat:[24,24],srgb:true}],['rockN',T+'rock_ground_02_nor_gl_1k.png',{repeat:[24,24]}],['rockR',T+'rock_ground_02_rough_1k.png',{repeat:[24,24]}],
    ['waterN',T+'waternormals.jpg',{repeat:[10,10]}]
  ];
  const got=await Promise.all(jobs.map(async([k,u,o])=>[k,await tex(u,o)]));for(const [k,v] of got)quality.textures[k]=v;
  try{const hdr=await rgbeLoader.loadAsync(T+'sky.hdr');hdr.mapping=THREE.EquirectangularReflectionMapping;const pmrem=new THREE.PMREMGenerator(renderer);pmrem.compileEquirectangularShader();scene.environment=pmrem.fromEquirectangular(hdr).texture;hdr.dispose();pmrem.dispose();}catch(e){console.warn('HDR environment unavailable',e)}
  const modelJobs={small:'./env/b_small.glb',medium:'./env/b_medium.glb',large:'./env/b_large.glb',tree1:'./env/tree1.glb',tree2:'./env/tree2.glb',tree3:'./env/tree3.glb',bush:'./env/bush.glb',carrier:'./env/carrier_hd.glb',traffic:'./models/pa28.glb'};
  await Promise.all(Object.entries(modelJobs).map(async([k,u])=>quality.models[k]=await glb(u)));
}
function pbrMaterial(kind,baseColor,repeat=[1,1]){
  const clone=k=>{const src=quality.textures[kind+k];if(!src)return null;const t=src.clone();t.needsUpdate=true;t.wrapS=t.wrapT=THREE.RepeatWrapping;t.repeat.set(...repeat);t.anisotropy=maxAniso;return t},d=clone('D'),n=clone('N'),r=clone('R');
  return new THREE.MeshStandardMaterial({color:baseColor,map:d,normalMap:n,roughnessMap:r,roughness:.9,metalness:0,normalScale:new THREE.Vector2(1.15,1.15)});
}
function cloneAsset(key,height){const src=quality.models[key];if(!src)return null;const g=src.clone(true);const b=new THREE.Box3().setFromObject(g),sz=new THREE.Vector3();b.getSize(sz);const s=height/Math.max(.001,sz.y);g.scale.setScalar(s);const bb=new THREE.Box3().setFromObject(g);g.position.y-=bb.min.y;g.traverse(o=>{if(o.isMesh){o.castShadow=true;o.receiveShadow=true}});return g}
function placeAsset(key,x,y,z,height,rot=0,parent=worldGroup){const a=cloneAsset(key,height);if(!a)return null;a.position.x=x;a.position.y+=y;a.position.z=z;a.rotation.y=rot;parent.add(a);return a}
'''
js=js[:idx]+assets+js[idx:]

# More natural lighting.
start=js.index('function createLighting(){')
end=js.index('\nfunction terrainHeight',start)
lighting=r'''function createLighting(){
  const hemi=new THREE.HemisphereLight(0xddefff,0x465035,1.25);scene.add(hemi);
  const sun=new THREE.DirectionalLight(0xfff1d4,4.1);sun.position.set(-9000,14500,-6500);sun.castShadow=true;sun.shadow.mapSize.set(3072,3072);sun.shadow.camera.left=-6200;sun.shadow.camera.right=6200;sun.shadow.camera.top=6200;sun.shadow.camera.bottom=-6200;sun.shadow.camera.near=200;sun.shadow.camera.far=33000;sun.shadow.bias=-0.00008;scene.add(sun);
  const fill=new THREE.DirectionalLight(0xb9d9ff,.7);fill.position.set(9000,6500,7000);scene.add(fill);
}
'''
js=js[:start]+lighting+js[end:]

# Replace complete world block through createClouds with quality version.
start=js.index('function createWorld(){')
end=js.index('\nconst pendingNativeDownloads',start)
world=r'''function createWorld(){
  const waterMat=new THREE.MeshPhysicalMaterial({color:0x246982,roughness:.18,metalness:.05,transmission:.04,transparent:true,opacity:.97,envMapIntensity:1.1,normalMap:quality.textures.waterN||null,normalScale:new THREE.Vector2(.55,.55)});
  const water=new THREE.Mesh(new THREE.PlaneGeometry(70000,70000,1,1),waterMat);water.rotation.x=-Math.PI/2;water.position.y=-20;water.receiveShadow=true;worldGroup.add(water);quality.water.push(water);
  const seg=220,geo=new THREE.PlaneGeometry(WORLD,WORLD,seg,seg);geo.rotateX(-Math.PI/2);const p=geo.attributes.position,colors=new Float32Array(p.count*3);const c=new THREE.Color();
  for(let i=0;i<p.count;i++){
    const x=p.getX(i),z=p.getZ(i),h=terrainHeight(x,z);p.setY(i,h);
    if(h>650)c.setRGB(.54,.57,.50);else if(h>330)c.setRGB(.68,.72,.60);else if(h<15)c.setRGB(.76,.72,.55);else c.setRGB(.78,.90,.72);
    colors[i*3]=c.r;colors[i*3+1]=c.g;colors[i*3+2]=c.b;
  }
  geo.setAttribute('color',new THREE.BufferAttribute(colors,3));geo.computeVertexNormals();if(geo.attributes.uv&&!geo.attributes.uv1)geo.setAttribute('uv1',geo.attributes.uv.clone());
  const landMat=pbrMaterial('grass',0xffffff,[72,72]);landMat.vertexColors=true;landMat.roughness=.96;
  const land=new THREE.Mesh(geo,landMat);land.receiveShadow=true;worldGroup.add(land);

  // High-altitude rocky caps make the mountains read as real terrain rather than a green blanket.
  for(const [x,z,sx,sz,rot] of [[3600,-2600,2700,1900,.2],[-5600,4800,1900,1500,-.35],[-2500,1200,2200,1400,.55]]){
    const rg=new THREE.PlaneGeometry(sx,sz,70,50);rg.rotateX(-Math.PI/2);const rp=rg.attributes.position;for(let i=0;i<rp.count;i++){const wx=rp.getX(i)+x,wz=rp.getZ(i)+z;rp.setXYZ(i,wx,terrainHeight(wx,wz)+2+Math.sin(i*.71)*2,wz)}rg.computeVertexNormals();
    const rm=pbrMaterial('rock',0xc2b8a7,[10,8]);const rock=new THREE.Mesh(rg,rm);rock.receiveShadow=true;worldGroup.add(rock);
  }

  createRunway(-6500,-2200,0,2100,72,'MAIN 09/27',true);
  createRunway(5200,5600,.35,1350,46,'RIDGE STRIP',false);
  createVillage(-3000,3200,52); createVillage(2700,-4700,70); createVillage(6300,1100,38); createVillage(-7200,5200,34);
  createCarrier(10400,-5200,-.38);
  createLake(-700,6200,900,600);createLake(5000,-1500,700,520);
  createTowers();createClouds();
}
function createRunway(x,z,rot,len,w,label,main=false){
  const y=terrainHeight(x,z)+3,g=new THREE.Group();g.position.set(x,y,z);g.rotation.y=rot;
  const mat=pbrMaterial('asphalt',0xffffff,[1.4,len/130]);mat.roughness=.91;
  const strip=new THREE.Mesh(new THREE.BoxGeometry(w,3,len),mat);strip.receiveShadow=true;g.add(strip);
  const lineMat=new THREE.MeshStandardMaterial({color:0xf3f0db,roughness:.75});
  for(let i=-9;i<=9;i++){const dash=new THREE.Mesh(new THREE.BoxGeometry(2.3,.12,52),lineMat);dash.position.set(0,1.58,i*98);g.add(dash)}
  for(const sx of [-1,1]){const edge=new THREE.Mesh(new THREE.BoxGeometry(1.3,.12,len-30),lineMat);edge.position.set(sx*(w/2-3),1.59,0);g.add(edge)}
  // Threshold bars and runway numbers.
  for(const end of [-1,1])for(let i=-3;i<=3;i++){const bar=new THREE.Mesh(new THREE.BoxGeometry(5,.13,34),lineMat);bar.position.set(i*8,1.6,end*(len/2-55));g.add(bar)}
  const apronMat=pbrMaterial('asphalt',0xdddddd,[3,4]);const apron=new THREE.Mesh(new THREE.BoxGeometry(main?310:150,2,main?330:190),apronMat);apron.position.set(w+(main?145:85),.3,-len*.28);apron.receiveShadow=true;g.add(apron);
  // Real GLB airport buildings rather than primitive house blocks.
  const bx=w+(main?110:65);for(let i=0;i<(main?6:3);i++){const key=i%3===0?'large':i%3===1?'medium':'small';const a=cloneAsset(key,main?26:18);if(a){a.position.set(bx+(i%2)*58,1.5,-len*.38+i*58);a.rotation.y=Math.PI/2;g.add(a)}}
  // PAPI/edge/approach lighting.
  const lampMat=new THREE.MeshBasicMaterial({color:0xe9f8ff});for(const sx of [-1,1])for(let i=-10;i<=10;i++){const lamp=new THREE.Mesh(new THREE.SphereGeometry(.65,7,5),lampMat);lamp.position.set(sx*(w/2+3),2.3,i*(len/22));g.add(lamp)}
  for(const end of [-1,1])for(let i=1;i<=8;i++){const lamp=new THREE.Mesh(new THREE.SphereGeometry(.72,7,5),new THREE.MeshBasicMaterial({color:i<3?0xff574f:0xffffff}));lamp.position.set(0,2.3,end*(len/2+i*40));g.add(lamp)}
  worldGroup.add(g);
}
function createVillage(cx,cz,count){
  const group=new THREE.Group();const roadMat=pbrMaterial('dirt',0xffffff,[1,8]);
  for(let r=-1;r<=1;r++){const road=new THREE.Mesh(new THREE.BoxGeometry(16,.45,1250),roadMat);road.position.set(cx+r*260,terrainHeight(cx+r*260,cz)+1,cz);road.rotation.y=r*.18;road.receiveShadow=true;group.add(road)}
  for(let i=0;i<count;i++){
    const ang=i*2.399963,rad=110+(i%13)*58,x=cx+Math.cos(ang)*rad,z=cz+Math.sin(ang)*rad,y=terrainHeight(x,z);
    const key=i%7<3?'small':i%7<6?'medium':'large',h=11+(i%5)*3.2;const a=placeAsset(key,x,y,z,h,(i%8)*.41,group);
    if(!a){const fallback=new THREE.Mesh(new THREE.BoxGeometry(14,h,18),new THREE.MeshStandardMaterial({color:0xb9aa90,roughness:.9}));fallback.position.set(x,y+h/2,z);group.add(fallback)}
    if(i%2===0){const tk=['tree1','tree2','tree3'][i%3];placeAsset(tk,x+18+Math.sin(i)*9,y,z-14,16+(i%5)*3,(i*.6)%6.28,group)}
    if(i%5===0)placeAsset('bush',x-12,y,z+16,4+(i%3),i,group);
  }worldGroup.add(group);
}
function createCarrier(x,z,rot){
  const src=quality.models.carrier;if(src){const g=src.clone(true);g.position.set(x,-5,z);g.rotation.y=rot;g.traverse(o=>{if(o.isMesh){o.castShadow=true;o.receiveShadow=true;if(o.material?.name==='deck'&&quality.textures.asphaltD){o.material=o.material.clone();o.material.map=quality.textures.asphaltD;o.material.normalMap=quality.textures.asphaltN;o.material.roughnessMap=quality.textures.asphaltR;o.material.map.repeat.set(2,16);o.material.normalMap?.repeat.set(2,16);o.material.roughnessMap?.repeat.set(2,16)}}});worldGroup.add(g);state.carrier=g;return}
  const g=new THREE.Group();g.position.set(x,-6,z);g.rotation.y=rot;const hull=new THREE.Mesh(new THREE.BoxGeometry(150,32,690),new THREE.MeshStandardMaterial({color:0x46515a,roughness:.75,metalness:.25}));g.add(hull);worldGroup.add(g);state.carrier=g;
}
function createLake(x,z,rx,rz){const y=terrainHeight(x,z)+2;const mat=new THREE.MeshPhysicalMaterial({color:0x2d7795,roughness:.12,metalness:.04,transparent:true,opacity:.94,normalMap:quality.textures.waterN||null,normalScale:new THREE.Vector2(.35,.35)});const lake=new THREE.Mesh(new THREE.CircleGeometry(1,80),mat);lake.scale.set(rx,rz,1);lake.rotation.x=-Math.PI/2;lake.position.set(x,y,z);worldGroup.add(lake);quality.water.push(lake)}
function createTowers(){for(const [x,z] of [[-6500,-1000],[5600,6100],[-3300,3300],[2800,-4800]]){const y=terrainHeight(x,z),g=new THREE.Group();const m=new THREE.Mesh(new THREE.CylinderGeometry(4,7,92,12),new THREE.MeshStandardMaterial({color:0xd6d9d7,metalness:.5,roughness:.45}));m.position.y=46;g.add(m);for(let i=0;i<6;i++){const arm=new THREE.Mesh(new THREE.BoxGeometry(30,1.2,1.2),new THREE.MeshStandardMaterial({color:0x646b70,metalness:.6,roughness:.4}));arm.position.y=55+i*6;arm.rotation.y=i*Math.PI/3;g.add(arm)}g.position.set(x,y,z);worldGroup.add(g)}}
function createClouds(){const mat=new THREE.MeshStandardMaterial({color:0xffffff,transparent:true,opacity:.48,roughness:1,depthWrite:false});for(let i=0;i<44;i++){const g=new THREE.Group();for(let j=0;j<6;j++){const m=new THREE.Mesh(new THREE.SphereGeometry(rand(100,230),12,8),mat);m.scale.set(1.2+rand(0,.7),.38+rand(0,.2),1+rand(0,.5));m.position.set(j*105+rand(-90,80),rand(-30,45),rand(-90,80));g.add(m)}g.position.set(rand(-16000,16000),rand(1800,4200),rand(-16000,16000));worldGroup.add(g)}}
function createAirTraffic(){const src=quality.models.traffic;if(!src)return;for(let i=0;i<9;i++){const g=src.clone(true);normalizeModel(g,8);g.traverse(o=>{if(o.isMesh){o.castShadow=true;o.receiveShadow=true}});trafficGroup.add(g);state.airTraffic.push({g,cx:rand(-6500,6500),cz:rand(-6500,6500),r:rand(1800,5600),alt:rand(650,1800),speed:rand(.035,.07),phase:rand(0,Math.PI*2),dir:i%2?1:-1})}}
'''
js=js[:start]+world+js[end:]

# Real GLB target aircraft instead of primitive capsule/box target.
old="function createTargetAircraft(){const g=new THREE.Group();const mat=new THREE.MeshStandardMaterial({color:0xff6b55,metalness:.4,roughness:.35});const body=new THREE.Mesh(new THREE.CapsuleGeometry(8,54,8,12),mat);body.rotation.x=Math.PI/2;const wing=new THREE.Mesh(new THREE.BoxGeometry(74,3,16),mat);g.add(body,wing);g.position.set(-2000,900,1500);trafficGroup.add(g);return g;}"
new="function createTargetAircraft(){const src=quality.models.traffic;if(src){const g=src.clone(true);normalizeModel(g,14);g.position.set(-2000,900,1500);g.traverse(o=>{if(o.isMesh){o.castShadow=true;o.receiveShadow=true}});trafficGroup.add(g);return g}const g=new THREE.Group();const mat=new THREE.MeshStandardMaterial({color:0xff6b55,metalness:.4,roughness:.35});const body=new THREE.Mesh(new THREE.CapsuleGeometry(8,54,8,12),mat);body.rotation.x=Math.PI/2;g.add(body);g.position.set(-2000,900,1500);trafficGroup.add(g);return g;}"
if old in js: js=js.replace(old,new)

# Animate traffic + water normal maps.
old="function animateWorld(dt){for(const b of state.freeBeacons){b.rotation.y+=dt*.2;b.children[1].rotation.z+=dt*.25;}if(state.missionData?.targets){for(const t of state.missionData.targets)t.rotation.z+=dt*.45;}}"
new="""function animateWorld(dt){
  for(const b of state.freeBeacons){b.rotation.y+=dt*.2;b.children[1].rotation.z+=dt*.25;}
  if(state.missionData?.targets){for(const t of state.missionData.targets)t.rotation.z+=dt*.45;}
  if(quality.textures.waterN){quality.textures.waterN.offset.x=(quality.textures.waterN.offset.x+dt*.006)%1;quality.textures.waterN.offset.y=(quality.textures.waterN.offset.y+dt*.004)%1;}
  for(const t of state.airTraffic){t.phase+=dt*t.speed*t.dir;const x=t.cx+Math.cos(t.phase)*t.r,z=t.cz+Math.sin(t.phase)*t.r,y=terrainHeight(x,z)+t.alt+Math.sin(t.phase*2)*90;t.g.position.set(x,y,z);t.g.rotation.y=-t.phase+(t.dir>0?0:Math.PI);t.g.rotation.z=.08*t.dir;}
}"""
if old not in js: raise SystemExit('animateWorld marker missing')
js=js.replace(old,new)

# Better visual feedback in boot/card text.
html=html.replace('Три стартовых самолёта находятся внутри APK. Остальные скачиваются один раз во внутреннюю память приложения и после этого доступны офлайн.', 'Три стартовых самолёта и HD-набор карты находятся внутри APK. Остальные самолёты скачиваются один раз во внутреннюю память приложения и после этого доступны офлайн.')
# Add tiny quality badge if title exists.
html=html.replace('<span class="logo-sub">3D FLIGHT</span>', '<span class="logo-sub">3D FLIGHT · HD ENVIRONMENT</span>')

css += """
/* Quality build */
.logo-sub{letter-spacing:.17em}.card.plane-card .storage{border-top:1px solid rgba(255,255,255,.06);padding-top:7px}.boot-quality{font-size:10px;opacity:.7}
"""

lic=root/'ASSET-LICENSES.md'
if lic.exists():
    ls=lic.read_text()
    tag='## HD environment pack'
    if tag not in ls:
        ls += '''\n\n## HD environment pack\n- Poly Haven PBR terrain/asphalt/dirt/rock textures and HDRI: CC0 1.0.\n- Quaternius Downtown City MegaKit / Stylized Nature MegaKit environment GLB assets (via skyline-run): CC0 1.0.\n- Three.js example `waternormals.jpg`: MIT repository asset.\n- `carrier_hd.glb`: original static model authored for Sky Frontiers 3D in this build.\n'''
        lic.write_text(ls)
js_p.write_text(js);html_p.write_text(html);css_p.write_text(css)
print('quality patch applied',len(js),len(html),len(css))
