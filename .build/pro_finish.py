from pathlib import Path

mp=Path('src/main.js')
cp=Path('src/content.js')
bp=Path('tools/build_assets.sh')
vp=Path('tools/validate_build.py')
s=mp.read_text(encoding='utf-8')
c=cp.read_text(encoding='utf-8')
b=bp.read_text(encoding='utf-8')
v=vp.read_text(encoding='utf-8')

# A real bow is the held weapon for the archer; the arrow remains the projectile.
c=c.replace("fox_bow:['arrow',.46,'shoot']", "fox_bow:['bow',.34,'shoot']")
# Avoid a miniature full character being used as a handheld 'robot launcher'.
c=c.replace("blast_bot:['mini_robot',.34,'spell']", "blast_bot:['bomb',.17,'spell']")
if "bow:'./assets/bow.glb'" not in c:
    c=c.replace("arrow:'./assets/arrow.glb',", "arrow:'./assets/arrow.glb',bow:'./assets/bow.glb',")

if 'BOW="$(pick "$ROOT/medieval"' not in b:
    b=b.replace("ARROW=\"$(pick \"$ROOT/medieval\" 'Arrow' 'Bolt' 'Bow')\"", "ARROW=\"$(pick \"$ROOT/medieval\" 'Arrow' 'Bolt')\"\nBOW=\"$(pick \"$ROOT/medieval\" 'Bow' 'Longbow' 'Crossbow')\"")
if 'convert "$BOW" bow.glb static' not in b:
    b=b.replace('convert "$ARROW" arrow.glb static', 'convert "$ARROW" arrow.glb static\nconvert "$BOW" bow.glb static')
if "'bow.glb'" not in v:
    v=v.replace("'bomb.glb','arrow.glb','star.glb'", "'bomb.glb','arrow.glb','bow.glb','star.glb'")

# QA telemetry is updated at the exact combat events, not only when HUD happens to refresh.
old="this.fireWeapon(w,rank,target);this.playWeaponAttack(p,w);"
new="this.fireWeapon(w,rank,target);document.body.dataset.qaShots=String((Number(document.body.dataset.qaShots)||0)+1);this.playWeaponAttack(p,w);"
if old in s and 'dataset.qaShots' not in s:
    s=s.replace(old,new,1)

old="damageEnemy(e,amount,proj){if(e.dead)return;if(e.shield>0)"
new="damageEnemy(e,amount,proj){if(e.dead)return;document.body.dataset.qaHits=String((Number(document.body.dataset.qaHits)||0)+1);if(e.shield>0)"
if old in s and 'dataset.qaHits' not in s:
    s=s.replace(old,new,1)

old="if(reward){this.kills++;this.runCoins+="
new="if(reward){this.kills++;document.body.dataset.qaKills=String(this.kills);this.runCoins+="
if old in s:
    s=s.replace(old,new,1)

# Combat smoke enemies are very close, stationary and fragile, but only in debug-fast QA.
s=s.replace("e.root.position.set(6.5+q*1.4,0,-3+q*2.2);e.hp=6;e.maxHp=6;e.speed=.15;e.damage=1", "e.root.position.set(2.8+q*1.25,0,-1.3+q*1.3);e.hp=4;e.maxHp=4;e.speed=0;e.damage=1")

# Keep held props compact and beside the palm; they must read as held items rather than body-sized meshes.
for a,b2 in {
 "donut:['food_donut',.34,'throw']":"donut:['food_donut',.16,'throw']",
 "firefly:['gem',.30,'spell']":"firefly:['gem',.14,'spell']",
 "pirate_bomb:['bomb',.38,'throw']":"pirate_bomb:['bomb',.18,'throw']",
 "rainbow_fan:['star',.34,'spell']":"rainbow_fan:['star',.16,'spell']",
 "flower_burst:['flower',.38,'spell']":"flower_burst:['flower',.17,'spell']",
 "amber_comet:['gem',.36,'spell']":"amber_comet:['gem',.15,'spell']",
 "pancake:['food_pancake',.34,'throw']":"pancake:['food_pancake',.17,'throw']",
 "cloud_orb:['cloud',.34,'spell']":"cloud_orb:['cloud',.16,'spell']",
 "watermelon:['food_watermelon',.40,'throw']":"watermelon:['food_watermelon',.18,'throw']",
 "music_wave:['star',.36,'spell']":"music_wave:['star',.16,'spell']",
 "smart_bee:['bee',.34,'spell']":"smart_bee:['bee',.16,'spell']",
 "snowball:['snow',.34,'throw']":"snowball:['snow',.15,'throw']",
 "pumpkin:['food_pumpkin',.40,'throw']":"pumpkin:['food_pumpkin',.16,'throw']",
}.items(): c=c.replace(a,b2)

s=s.replace("weapon.position.set(.06,.015,-.035);weapon.rotation.set(p.attack==='throw'?.15:0,p.attack==='shoot'?1.45:.15,p.attack==='throw'?.55:.12);", "weapon.position.set(p.attack==='shoot'?.025:.095,p.attack==='throw'?.02:0,p.attack==='throw'?.015:-.025);weapon.rotation.set(p.attack==='shoot'?Math.PI/2:p.attack==='throw'?.08:0,p.attack==='shoot'?0:.08,p.attack==='throw'?.38:.08);")
s=s.replace("weapon.position.set(p.attack==='shoot'?.03:.11,p.attack==='throw'?.02:0,p.attack==='throw'?.02:-.025);weapon.rotation.set(p.attack==='throw'?.10:0,p.attack==='shoot'?1.42:.08,p.attack==='throw'?.42:.10);", "weapon.position.set(p.attack==='shoot'?.025:.095,p.attack==='throw'?.02:0,p.attack==='throw'?.015:-.025);weapon.rotation.set(p.attack==='shoot'?Math.PI/2:p.attack==='throw'?.08:0,p.attack==='shoot'?0:.08,p.attack==='throw'?.38:.08);")

# The imported floor asset is thick. Scale X/Z to map size, but never scale its thickness with map size.
s=s.replace("model.scale.multiplyScalar(targetSize/Math.max(s.x,s.z,.001));model.updateMatrixWorld(true);", "const groundK=targetSize/Math.max(s.x,s.z,.001);model.scale.set(groundK,.18,groundK);model.updateMatrixWorld(true);",1)

# Repair an inner variable shadow in macro clusters and use the closer desktop combat camera.
s=s.replace("const h=this.cloneVisual(this.assets[a]||this.assets.rock,u*(.82+(i%5)*.06),{tint:null});h.position.set(x,0,z);h.rotation.y=q;this.environment.add(h)", "const deco=this.cloneVisual(this.assets[a]||this.assets.rock,u*(.82+(i%5)*.06),{tint:null});deco.position.set(x,0,z);deco.rotation.y=q;this.environment.add(deco)")
s=s.replace("target=new THREE.Vector3(pos.x,pos.y+(this.lowPower?9.5:7.7),pos.z+(this.lowPower?11.8:10.5))", "target=new THREE.Vector3(pos.x,pos.y+(this.lowPower?9.4:7),pos.z+(this.lowPower?11.8:9.3))")
s=s.replace("target=new THREE.Vector3(pos.x,pos.y+(this.lowPower?9.6:8.6),pos.z+(this.lowPower?11.3:10.0))", "target=new THREE.Vector3(pos.x,pos.y+(this.lowPower?9.4:7),pos.z+(this.lowPower?11.8:9.3))")

c=c.replace('Тыквенный Шут','Тыквенный Чародей')

mp.write_text(s,encoding='utf-8')
cp.write_text(c,encoding='utf-8')
bp.write_text(b,encoding='utf-8')
vp.write_text(v,encoding='utf-8')
print('final combat/weapon/terrain QA fixes applied')
