from pathlib import Path

p = Path('src/main.js')
s = p.read_text(encoding='utf-8')

if 'splashMul:1,slowBonus:0,homingBonus:0' not in s:
    old = """    const p={...actor,hero,speed:6.15*(1+(meta.speed||0)*.025),speedMul:1,maxHp:100+(meta.health||0)*10,hp:100+(meta.health||0)*10,damageMul:1+(meta.power||0)*.04,cooldownMul:1-(meta.cooldown||0)*.025,
      damageTaken:1-(meta.armor||0)*.025,areaMul:1+(meta.area||0)*.04,xpMul:1+(meta.xp||0)*.05,pickup:3.2*(1+(meta.pickup||0)*.08),crit:.05+(meta.crit||0)*.02,regen:(meta.regen||0)*.08,extraProjectiles:0,extraPierce:0,duration:1,
      luck:(meta.luck||0)*.04,dashCd:2.25,dashTimer:0,dashTime:0,invuln:0,slowMul:1,weaponRanks:{},passiveRanks:{},weaponTimers:{},weapons:[],freeRerolls:meta.reroll||0,rerolls:0};
    const b=hero.bonus||{};p.damageMul*=1+(b.damage||0);p.damageTaken*=1-(b.armor||b.damageReduction||0);p.maxHp*=1+(b.hp||0);p.hp=p.maxHp;p.speed*=1+(b.speed||0);p.cooldownMul*=1-(b.cooldown||0);p.xpMul*=1+(b.xp||0);p.pickup*=1+(b.pickup||0);p.crit+=b.crit||0;p.regen+=b.regen||0;p.extraProjectiles+=b.projectiles||0;p.extraPierce+=b.pierce||0;p.duration*=1+(b.duration||0);p.luck+=b.luck||0;
"""
    new = """    const p={...actor,hero,speed:6.15*(1+(meta.speed||0)*.025),speedMul:1,maxHp:100+(meta.health||0)*10,hp:100+(meta.health||0)*10,damageMul:1+(meta.power||0)*.04,cooldownMul:1-(meta.cooldown||0)*.025,
      damageTaken:1-(meta.armor||0)*.025,areaMul:1+(meta.area||0)*.04,xpMul:1+(meta.xp||0)*.05,pickup:3.2*(1+(meta.pickup||0)*.08),crit:.05+(meta.crit||0)*.02,regen:(meta.regen||0)*.08,extraProjectiles:0,extraPierce:0,duration:1,
      splashMul:1,slowBonus:0,homingBonus:0,projectileSpeedMul:1,dodge:0,knockbackMul:1,luck:(meta.luck||0)*.04,dashCd:2.25,dashTimer:0,dashTime:0,invuln:0,weaponRanks:{},passiveRanks:{},weaponTimers:{},weapons:[],freeRerolls:meta.reroll||0,rerolls:0};
    const b=hero.bonus||{};p.damageMul*=1+(b.damage||0);p.damageTaken*=1-(b.armor||b.damageReduction||0);p.maxHp*=1+(b.hp||0);p.hp=p.maxHp;p.speed*=1+(b.speed||0);p.cooldownMul*=1-(b.cooldown||0);p.xpMul*=1+(b.xp||0);p.pickup*=1+(b.pickup||0);p.crit+=b.crit||0;p.regen+=b.regen||0;p.extraProjectiles+=b.projectiles||0;p.extraPierce+=b.pierce||0;p.duration*=1+(b.duration||0);p.luck+=b.luck||0;p.splashMul*=1+(b.splash||0);p.slowBonus+=b.slow||0;p.homingBonus+=b.homing||0;p.projectileSpeedMul*=1+(b.projectileSpeed||0);p.dodge+=b.dodge||0;p.knockbackMul*=1+(b.knockback||0);
"""
    assert old in s, 'player bonus block not found'
    s = s.replace(old, new)

if 'let terrainMul=1' not in s:
    s = s.replace('p.mixer.update(dt);p.invuln=Math.max(0,p.invuln-dt);p.dashTimer=Math.max(0,p.dashTimer-dt);p.slowMul=1;', 'p.mixer.update(dt);p.invuln=Math.max(0,p.invuln-dt);p.dashTimer=Math.max(0,p.dashTimer-dt);')
    old = '    const map=this.currentMap(),v=this.input.vector();\n'
    new = "    const map=this.currentMap(),v=this.input.vector();let terrainMul=1;\n    for(const h of this.hazards){const coord=h.axis==='x'?p.root.position.x:p.root.position.z;if(Math.abs(coord-h.at)<h.width*.5)terrainMul*=h.slow;}\n"
    assert old in s, 'terrain insertion point not found'
    s = s.replace(old, new)
    s = s.replace('p.speed*p.speedMul*mul*p.slowMul*dt', 'p.speed*p.speedMul*mul*terrainMul*dt')
    s = s.replace("if(Math.abs(coord-h.at)<h.width*.5){p.slowMul*=h.slow;if(h.damage&&p.invuln<=0){this.hurtPlayer(h.damage*dt);}}", "if(Math.abs(coord-h.at)<h.width*.5&&h.damage&&p.invuln<=0)this.hurtPlayer(h.damage*dt);")

if 'const shotSpeed=(w.speed||18)*this.player.projectileSpeedMul;' not in s:
    old = "    this.projectiles.push({root,w,vel:dir.clone().multiplyScalar(w.speed||18),life:(w.range||24)/(w.speed||18)*this.player.duration,damage:opts.damage,pierce:opts.pierce||0,hit:new Set(),homing:opts.homing||0,chain:opts.chain||0,boomerang:opts.boomerang,age:0,mine:opts.mine,slow:w.slow||0,splash:(w.splash||0)*(opts.area||1),knockback:w.knockback||0,crit:(w.crit||0)+this.player.crit});"
    new = "    const shotSpeed=(w.speed||18)*this.player.projectileSpeedMul;\n    this.projectiles.push({root,w,vel:dir.clone().multiplyScalar(shotSpeed),life:(w.range||24)/shotSpeed*this.player.duration,damage:opts.damage,pierce:opts.pierce||0,hit:new Set(),homing:(opts.homing||0)+this.player.homingBonus,chain:opts.chain||0,boomerang:opts.boomerang,age:0,mine:opts.mine,slow:Math.min(.8,(w.slow||0)+this.player.slowBonus),splash:(w.splash||0)*(opts.area||1)*this.player.splashMul,knockback:(w.knockback||0)*this.player.knockbackMul,crit:(w.crit||0)+this.player.crit});"
    assert old in s, 'projectile block not found'
    s = s.replace(old, new)

if 'const expired=p.life<=0;let remove=expired;' not in s:
    s = s.replace('let remove=p.life<=0;', 'const expired=p.life<=0;let remove=expired;')
    s = s.replace('if(remove){p.root.removeFromParent();this.projectiles.splice(i,1);}', 'if(remove){if(p.mine&&expired&&p.splash>0)this.splashDamage(p.root.position,p.splash,p.damage*.62,null);p.root.removeFromParent();this.projectiles.splice(i,1);}')

if "shouldSplit=reward&&!e.elite&&e.def.behavior==='split'" not in s:
    old = "  killEnemy(e,reward=true){if(e.dead)return;e.dead=true;this.playActor(e,'death',false,.03);if(reward){this.kills++;this.runCoins+=e.elite?16:1+Math.floor(e.def.xp/2);this.addXp(e.def.xp*(e.elite?7:1));this.spawnPickup(e.root.position,e.elite?'coin':'xp',e.elite?8:1);this.audio.kill();this.progress.daily.kills++;this.progress.career.kills++;if(e.elite)setTimeout(()=>this.openChest(),280);}setTimeout(()=>{e.root.removeFromParent();const i=this.enemies.indexOf(e);if(i>=0)this.enemies.splice(i,1);},240);}"
    new = """  killEnemy(e,reward=true){
    if(e.dead)return;const deathPos=e.root.position.clone(),shouldSplit=reward&&!e.elite&&e.def.behavior==='split';e.dead=true;this.playActor(e,'death',false,.03);
    if(reward){this.kills++;this.runCoins+=e.elite?16:1+Math.floor(e.def.xp/2);const xpValue=e.def.xp*(e.elite?7:1);this.spawnPickup(deathPos,'xp',xpValue);if(e.elite)this.spawnPickup(deathPos,'coin',8);this.audio.kill();this.progress.daily.kills++;this.progress.career.kills++;if(e.elite)setTimeout(()=>this.openChest(),280);}
    if(shouldSplit){for(let n=0;n<2;n++){const child=this.spawnEnemy(false,e.def);child.root.position.copy(deathPos).add(new THREE.Vector3(n?1:-1,0,(Math.random()-.5)*1.4));child.root.scale.multiplyScalar(.62);child.radius*=.62;child.hp=Math.max(12,e.maxHp*.26);child.maxHp=child.hp;child.damage*=.72;child.speed*=1.18;child.def={...child.def,behavior:'chase',xp:Math.max(1,Math.floor(e.def.xp*.55))};}}
    setTimeout(()=>{e.root.removeFromParent();const i=this.enemies.indexOf(e);if(i>=0)this.enemies.splice(i,1);},240);
  }"""
    assert old in s, 'killEnemy block not found'
    s = s.replace(old, new)

if "this.runToast('УКЛОНЕНИЕ')" not in s:
    s = s.replace('hurtPlayer(amount){const p=this.player;if(!p||p.invuln>0)return;p.hp-=amount*p.damageTaken;', "hurtPlayer(amount){const p=this.player;if(!p||p.invuln>0)return;if(p.dodge>0&&Math.random()<p.dodge){this.runToast('УКЛОНЕНИЕ');return;}p.hp-=amount*p.damageTaken;")

p.write_text(s, encoding='utf-8')

# Normalize the historical OpenGameArt download names to the exact files
# currently linked from the CC0 pack pages.
bp = Path('tools/build_assets.sh')
bs = bp.read_text(encoding='utf-8')
bs = bs.replace('ultimate_nature_pack_by_quaternius.zip', 'ultimate_nature_pack_by_quaternius_1.zip')
bs = bs.replace('RPG%20Pack.zip', 'ultimate_rpg_items_pack_by_quaternius_0.zip')
bs = bs.replace('ultimate_rpg_items_pack_by_quaternius.zip', 'ultimate_rpg_items_pack_by_quaternius_0.zip')
bp.write_text(bs, encoding='utf-8')

print('post-materialize gameplay and asset fixes applied')
