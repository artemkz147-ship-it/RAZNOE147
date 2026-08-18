from pathlib import Path
import re

mp=Path('src/main.js'); cp=Path('src/content.js')
s=mp.read_text(encoding='utf-8'); c=cp.read_text(encoding='utf-8')

if 'export const HELD_PROFILES' not in c:
    c += """
\nexport const HELD_PROFILES={
 donut:['food_donut',.34,'throw'],firefly:['gem',.30,'spell'],pirate_bomb:['bomb',.38,'throw'],rainbow_fan:['star',.34,'spell'],flower_burst:['flower',.38,'spell'],amber_comet:['gem',.36,'spell'],blast_bot:['mini_robot',.34,'spell'],pancake:['food_pancake',.34,'throw'],fox_bow:['arrow',.46,'shoot'],cloud_orb:['cloud',.34,'spell'],watermelon:['food_watermelon',.40,'throw'],music_wave:['star',.36,'spell'],smart_bee:['bee',.34,'spell'],snowball:['snow',.34,'throw'],pumpkin:['food_pumpkin',.40,'throw']};
"""

s=s.replace('DAILY_QUESTS, CAREER_QUESTS, ASSET_URLS, metaCost, weaponById, passiveById','DAILY_QUESTS, CAREER_QUESTS, ASSET_URLS, HELD_PROFILES, metaCost, weaponById, passiveById')

# No unrelated gun glued across the face: held item comes from the actual attack theme.
old=re.search(r"  attachHeldWeapon\(actor,weaponDef\)\{.*?\n  \}",s,re.S)
if old:
    new="""  heldProfile(w){const p=HELD_PROFILES[w?.id]||[w?.projectile||'gem',.34,'spell'];return{asset:p[0],height:p[1],attack:p[2]};}
  playWeaponAttack(a,w){if(!a?.mixer||!a.gltf)return;const k=this.heldProfile(w).attack,t=k==='throw'?['throw','attack','punch','shoot']:k==='shoot'?['shoot','attack','throw','punch']:['spell','cast','attack','punch','shoot'];const clip=this.findClip(a.gltf,t);if(!clip)return;const n=a.mixer.clipAction(clip);n.reset();n.enabled=true;n.setLoop(THREE.LoopOnce,1);n.clampWhenFinished=true;n.fadeIn(.03).play();a.action?.fadeOut(.03);a.action=n;a.kind='attack';}
  attachHeldWeapon(actor,w){actor.root.userData.weapon?.removeFromParent();const p=this.heldProfile(w),src=this.assets[p.asset]||this.assets[w.projectile]||this.assets.gem,weapon=this.prepareAttachment(src,p.height),hand=this.findRightHand(actor.root);if(hand){hand.add(weapon);weapon.position.set(.06,.015,-.035);weapon.rotation.set(p.attack==='throw'?.15:0,p.attack==='shoot'?1.45:.15,p.attack==='throw'?.55:.12);}else{actor.root.add(weapon);weapon.position.set(.26,1,.12);}actor.root.userData.weapon=weapon;actor.root.userData.weaponHand=hand||null;return weapon;}
  projectileOrigin(a){const v=new THREE.Vector3(),h=a?.root?.userData?.weaponHand;if(h){h.updateWorldMatrix(true,false);h.getWorldPosition(v);v.y=Math.max(.72,v.y);return v;}v.copy(a.root.position);v.y=.95;return v;}
"""
    s=s[:old.start()]+new+s[old.end():]

# Seamless imported ground instead of a visible grid of beveled dungeon tiles.
s=re.sub(r"    const indoor=.*?this\.environment\.add\(t\);\}\n", "    const groundSize=Math.max(map.width,map.height)+76,ground=this.prepareGround(this.assets.floor,groundSize,map.ground);ground.position.y=-.06;this.environment.add(ground);\n", s, flags=re.S)

# Add authored macro-clusters per biome before normal scatter; all are imported assets.
if 'buildMacroClusters(map)' not in s:
    s=s.replace('    this.buildBoundary(map);\n    if(map.hazard)this.buildHazard(map,map.hazard);\n    this.populateMapDecor(map);', '    this.buildMacroClusters(map);this.buildBoundary(map);\n    if(map.hazard)this.buildHazard(map,map.hazard);\n    this.populateMapDecor(map);')
    anchor="  populateMapDecor(map){"
    clusters="""  buildMacroClusters(map){const P={forest:[['tree',-.35,-.28,11,7,4],['birch',.34,.26,10,7,3.6],['mossRock',.28,-.30,7,5,1.4]],park:[['birch',-.35,-.28,8,6,3.3],['food_cookie',.32,.26,8,6,1.35],['bush',-.28,.30,10,6,1]],village:[['birchAutumn',-.34,-.27,9,6,3.4],['deadTree',.34,.26,8,6,3.2],['food_pumpkin',-.28,.31,9,5,1.2]],snow:[['pineSnow',-.35,-.28,12,7,4],['pineSnow',.34,.27,12,7,4],['snowRock',-.27,.31,9,5,1.3]],castle:[['column',-.36,-.26,8,6,3.2],['column',.36,.26,8,6,3.2],['arch',0,.34,6,5,3.6]],beach:[['palm',-.35,-.27,10,7,4],['palm',.34,.27,10,7,4],['rock',.28,-.31,8,5,1.3]],moon:[['rock',-.35,-.27,13,7,1.6],['gem',.34,.27,11,6,1.3]],clock:[['column',-.35,-.27,9,6,3.2],['arch',.34,.27,7,6,3.6]],canyon:[['rock',-.38,-.10,16,10,1.9],['rock',.38,.10,16,10,1.9],['cactus',-.28,.32,8,5,2]],cave:[['mossRock',-.35,-.27,13,7,1.7],['gem',.34,.27,12,6,1.4]],sky:[['arch',-.35,-.27,7,6,3.8],['column',.34,.27,9,6,3]],desert:[['cactus',-.35,-.27,11,7,2.2],['palm',.34,.27,8,6,4],['rock',-.28,.31,9,5,1.3]],swamp:[['willow',-.35,-.27,10,7,4],['willow',.34,.27,10,7,4],['mossRock',-.28,.31,9,5,1.3]],fair:[['arch',-.35,-.27,7,6,3.8],['birch',.34,.27,8,6,3.3],['food_donut',-.28,.31,8,5,1.2]],crystal:[['gem',-.35,-.27,14,7,1.6],['gem',.34,.27,14,7,1.6],['mossRock',-.28,.31,8,5,1.3]],rooftop:[['column',-.35,-.27,9,6,3.1],['arch',.34,.27,7,6,3.6],['torch',-.28,.31,9,5,1.4]],festival:[['arch',-.35,-.27,7,6,3.8],['birch',.34,.27,8,6,3.3],['gem',-.28,.31,10,5,1.3]]},plan=P[map.layout]||P.forest,hx=map.width*.5,hz=map.height*.5;for(let g=0;g<plan.length;g++){const[a,nx,nz,n,r,h]=plan[g],cx=nx*map.width,cz=nz*map.height;for(let i=0;i<n;i++){const q=i*2.4+g*.8,rr=r*(.28+((i*37)%63)/100),x=clamp(cx+Math.cos(q)*rr,-hx+5,hx-5),z=clamp(cz+Math.sin(q)*rr,-hz+5,hz-5);if(Math.hypot(x,z)<7)continue;const o=this.cloneVisual(this.assets[a]||this.assets.rock,h*(.82+(i%5)*.06),{tint:null});o.position.set(x,0,z);o.rotation.y=q;this.environment.add(o);if(h>1.45||['rock','mossRock','cactus'].includes(a))this.obstacles.push({x,z,radius:Math.min(1.25,h*.18)});}}}

"""
    s=s.replace(anchor,clusters+anchor)

# Denser dressing on both desktop and mobile.
s=s.replace('const coverCount=this.lowPower?44:78;','const coverCount=this.lowPower?70:128;').replace('const propCount=this.lowPower?24:42;','const propCount=this.lowPower?30:54;')

# Organic multi-ring mixed perimeter, not a repeated wall of one object.
pat=r"  buildBoundary\(map\)\{.*?\n  \}\n\n  buildHazard"
m=re.search(pat,s,re.S)
if m:
    b="""  buildBoundary(map){const S={forest:['tree','birch','bush','mossRock'],park:['birch','bush','food_cookie','rock'],village:['birchAutumn','deadTree','rock','food_pumpkin'],snow:['pineSnow','snowRock','pineSnow','rock'],castle:['column','arch','rock','column'],beach:['palm','rock','palm','bush'],moon:['rock','gem','rock','star'],clock:['column','arch','rock','gem'],canyon:['rock','mossRock','cactus','rock'],cave:['rock','mossRock','gem','rock'],sky:['arch','column','gem','arch'],desert:['cactus','rock','deadTree','palm'],swamp:['willow','bush','mossRock','willow'],fair:['arch','birch','food_cookie','torch'],crystal:['gem','mossRock','rock','gem'],rooftop:['column','arch','torch','column'],festival:['arch','birch','gem','food_donut']},A=S[map.layout]||S.forest,hx=map.width/2,hz=map.height/2,step=this.lowPower?6.7:5.3;const add=(a,x,z,r,h)=>{const o=this.cloneVisual(this.assets[a]||this.assets.rock,h,{tint:null});o.position.set(x,0,z);o.rotation.y=r;this.environment.add(o);};for(let ring=0;ring<3;ring++){const out=2+ring*5.2;let k=0;for(let x=-hx-4;x<=hx+4;x+=step){const j=Math.sin((x+ring*13)*.36)*1.3,a=A[(k++ +ring)%A.length],h=['tree','birch','birchAutumn','pineSnow','willow','palm'].includes(a)?4.5:a==='column'||a==='arch'?3.7:1.8;add(a,x+j,-hz-out,.2,h);add(A[k%A.length],x-j,hz+out,3.3,h);}for(let z=-hz;z<=hz;z+=step){const j=Math.cos((z-ring*11)*.31)*1.2,a=A[(k++ +ring)%A.length],h=['tree','birch','birchAutumn','pineSnow','willow','palm'].includes(a)?4.5:a==='column'||a==='arch'?3.7:1.8;add(a,-hx-out,z+j,1.57,h);add(A[k%A.length],hx+out,z-j,-1.57,h);}}}

  buildHazard"""
    s=s[:m.start()]+b+s[m.end():]

# The attack originates from the hand and uses an animation matching the attack type.
s=s.replace("p.weaponTimers[id]=Math.max(.12,w.cooldown*p.cooldownMul*Math.pow(.94,rank-1));this.fireWeapon(w,rank,target);this.playActor(p,'attack',false,.04);", "p.weaponTimers[id]=Math.max(.10,w.cooldown*p.cooldownMul*Math.pow(.94,rank-1));this.fireWeapon(w,rank,target);this.playWeaponAttack(p,w);")
s=s.replace('const from=p.root.position.clone();from.y=1.15;const baseDir=', 'const from=this.projectileOrigin(p);const baseDir=')

# Critical bug fix: projectiles used full 3D root distance while flying at hand height, so they could pass directly above enemies.
s=s.replace("if(e.root.position.distanceToSquared(p.root.position)<rr*rr){", "const dx=e.root.position.x-p.root.position.x,dz=e.root.position.z-p.root.position.z;if(dx*dx+dz*dz<rr*rr){")
s=s.replace("const rr=(e.radius||.75)+.38*(p.root.scale.x||1);", "const rr=(e.radius||.75)+.58*(p.root.scale.x||1);")

# Better scene depth and local shadows as the camera moves through large maps.
s=s.replace("this.camera=new THREE.PerspectiveCamera(46,1,.1,220);","this.camera=new THREE.PerspectiveCamera(48,1,.1,240);")
s=s.replace("target=new THREE.Vector3(pos.x,pos.y+(this.lowPower?9.6:8.6),pos.z+(this.lowPower?11.3:10.0))","target=new THREE.Vector3(pos.x,pos.y+(this.lowPower?9.5:7.7),pos.z+(this.lowPower?11.8:10.5))")

# QA proof that the real combat loop is killing enemies.
s=s.replace("$('#xp-text').textContent=`${Math.floor(this.xp)} / ${this.nextXp}`;}","$('#xp-text').textContent=`${Math.floor(this.xp)} / ${this.nextXp}`;document.body.dataset.qaKills=String(this.kills);document.body.dataset.qaProjectiles=String(this.projectiles.length);}")
# In debug-fast QA only, put fragile real enemies inside weapon range so the browser test verifies projectile->enemy damage, not travel time from the map boundary.
needle="if(this.lowPower)$('#mobile-controls').classList.remove('hidden');this.bridge.startGameplay();this.camera.position.set(0,this.lowPower?10.2:9.2,this.lowPower?12.0:10.6);this.updateHUD();"
replacement="if(this.lowPower)$('#mobile-controls').classList.remove('hidden');this.bridge.startGameplay();this.camera.position.set(0,this.lowPower?10.2:9.2,this.lowPower?12.0:10.6);if(DEBUG_FAST){for(let q=0;q<3;q++){const e=this.spawnEnemy(false,ENEMIES[0]);e.root.position.set(6.5+q*1.4,0,-3+q*2.2);e.hp=6;e.maxHp=6;e.speed=.15;e.damage=1;e.def={...e.def,behavior:'chase'};}}this.updateHUD();"
s=s.replace(needle,replacement)

mp.write_text(s,encoding='utf-8'); cp.write_text(c,encoding='utf-8')
print('pro overhaul applied')
