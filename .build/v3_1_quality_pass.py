from pathlib import Path
import re

bp=Path('tools/build_assets.sh'); cp=Path('src/content.js'); mp=Path('src/main.js')
b=bp.read_text(encoding='utf-8'); c=cp.read_text(encoding='utf-8'); m=mp.read_text(encoding='utf-8')

def sub1(text, pattern, repl, label, flags=0):
    out,n=re.subn(pattern,repl,text,count=1,flags=flags)
    if n!=1: raise SystemExit(f'v3.1 patch failed: {label} ({n})')
    return out

# Replace the frying pan with an authored fantasy torch/scepter model.
b=b.replace('weapon_src[8]="$(pick_match "$ROOT/survival" \'Pan.fbx\' \'Pot.fbx\' \'Shovel\')"','weapon_src[8]="$(pick_match "$ROOT/dungeon" \'Torch.fbx\' \'Torch\' \'Woodfire\')"')

heroes={
'donut_knight':"{id:'donut_knight',name:'Золотая Рыцарка',asset:'hero01',weapon:'donut',cost:0,desc:'Стойкий стрелок: больше здоровья, брони и стабильности под давлением.',bonus:{armor:.12,hp:.08}},",
'dew_fairy':"{id:'dew_fairy',name:'Лесная Эльфийка',asset:'hero02',weapon:'firefly',cost:0,desc:'Мобильный следопыт: быстрее получает опыт и притягивает трофеи.',bonus:{xp:.16,pickup:.18}},",
'hamster_pirate':"{id:'hamster_pirate',name:'Капитан Бомба',asset:'hero03',weapon:'pirate_bomb',cost:0,desc:'Пират-бомбардир: мощные дальние взрывы и повышенная удача.',bonus:{luck:.20,splash:.16}},",
'candy_witch':"{id:'candy_witch',name:'Призматическая Ведьма',asset:'hero04',weapon:'rainbow_fan',cost:900,desc:'Маг залпового огня: широкая веерная атака и высокий базовый урон.',bonus:{damage:.12,projectiles:1}},",
'mushroom_druid':"{id:'mushroom_druid',name:'Зелёная Авантюристка',asset:'hero05',weapon:'flower_burst',cost:1250,desc:'Контролирует толпу дальними клинками ветра и замедляет наступление.',bonus:{area:.18,slow:.08}},",
'cat_mage':"{id:'cat_mage',name:'Янтарный Маг',asset:'hero06',weapon:'amber_comet',cost:1600,desc:'Точный магический стрелок: пробивает линии врагов и быстро перезаряжается.',bonus:{cooldown:.12,pierce:1}},",
'toy_robot':"{id:'toy_robot',name:'Лазурная Стражница',asset:'hero07',weapon:'blast_bot',cost:1950,desc:'Защитный класс с самонаводящимися метательными топорами.',bonus:{damageReduction:.10,homing:.18}},",
'baker_alchemist':"{id:'baker_alchemist',name:'Боевой Алхимик',asset:'hero08',weapon:'pancake',cost:2300,desc:'Огненный жезл выпускает пробивающие заряды, а герой постепенно лечится.',bonus:{duration:.20,regen:.18}},",
'fox_archer':"{id:'fox_archer',name:'Лазурный Стрелок',asset:'hero09',weapon:'fox_bow',cost:2650,desc:'Скоростной лучник с повышенным шансом критического попадания.',bonus:{speed:.12,crit:.08}},",
'cloud_princess':"{id:'cloud_princess',name:'Небесная Путница',asset:'hero10',weapon:'cloud_orb',cost:3000,desc:'Штормовой маг: быстрые самонаводящиеся заряды и повышенное уклонение.',bonus:{projectileSpeed:.16,dodge:.08}},",
'watermelon_captain':"{id:'watermelon_captain',name:'Штормовой Корсар',asset:'hero11',weapon:'watermelon',cost:3350,desc:'Тяжёлые взрывные стрелы разбрасывают плотные группы противников.',bonus:{knockback:.22,splash:.18}},",
'music_gnome':"{id:'music_gnome',name:'Северный Громовержец',asset:'hero12',weapon:'music_wave',cost:3700,desc:'Громовые импульсы расходятся кругом и пробивают окружившую толпу.',bonus:{area:.12,cooldown:.08}},",
'lightning_bee':"{id:'lightning_bee',name:'Ниндзя Искра',asset:'hero13',weapon:'smart_bee',cost:4050,desc:'Самый быстрый герой: зачарованные кинжалы сами доворачивают к целям.',bonus:{speed:.15,cooldown:.08}},",
'snow_penguin':"{id:'snow_penguin',name:'Полярный Разведчик',asset:'hero14',weapon:'snowball',cost:4400,desc:'Ледяные залпы замедляют врагов, а герой постепенно восстанавливается.',bonus:{regen:.35,slow:.14}},",
'pumpkin_jester':"{id:'pumpkin_jester',name:'Гоблинка-Охотница',asset:'hero15',weapon:'pumpkin',cost:4750,desc:'Метает парные боевые топоры и усиливает взрывные комбинации.',bonus:{luck:.12,splash:.12}},"}
for hid,line in heroes.items(): c=sub1(c,rf"^\s*\{{id:'{re.escape(hid)}'.*$","  "+line,f'hero {hid}',re.M)

weapons={
'donut':"{id:'donut',name:'Солнечный Посох',evolution:'Корона Солнца',model:'weapon01',projectile:'star',type:'fan',damage:17,cooldown:.68,speed:23,count:3,spread:.11,pierce:1,range:31,scale:.20,passive:'area',fx:0xffcf6b},",
'firefly':"{id:'firefly',name:'Флакон Зарниц',evolution:'Рой Зарниц',model:'weapon02',projectile:'gem',type:'chain',damage:15,cooldown:.64,speed:24,count:1,chain:4,chainRadius:6.4,range:31,scale:.20,passive:'cooldown',fx:0x8effc8},",
'pirate_bomb':"{id:'pirate_bomb',name:'Рунный Молот',evolution:'Пороховой Шторм',model:'weapon03',projectile:'bomb',type:'bomb',damage:32,cooldown:1.18,speed:15,count:1,splash:4.2,range:29,scale:.28,passive:'area',fx:0xff7b48},",
'rainbow_fan':"{id:'rainbow_fan',name:'Призматический Гримуар',evolution:'Семицветная Буря',model:'weapon04',projectile:'star',type:'fan',damage:12,cooldown:.70,speed:22,count:7,spread:.15,pierce:1,range:28,scale:.17,passive:'projectiles',fx:0xff78dc},",
'flower_burst':"{id:'flower_burst',name:'Клинок Ветра',evolution:'Шторм Клинков',model:'weapon05',projectile:'arrow',type:'fan',damage:14,cooldown:.70,speed:25,count:5,spread:.19,slow:.18,range:29,scale:.24,passive:'area',fx:0x98f2ff},",
'amber_comet':"{id:'amber_comet',name:'Янтарный Фокус',evolution:'Янтарная Сверхновая',model:'weapon06',projectile:'gem',type:'shot',damage:28,cooldown:.78,speed:30,count:1,pierce:4,range:35,scale:.24,passive:'power',fx:0xffb54a},",
'blast_bot':"{id:'blast_bot',name:'Грозовой Топор',evolution:'Штормовой Арсенал',model:'weapon07',projectile:'weapon07',type:'homing',damage:23,cooldown:.98,speed:17,count:2,homing:7,splash:2.7,range:32,scale:.22,passive:'cooldown',fx:0x73d9ff},",
'pancake':"{id:'pancake',name:'Огненный Жезл',evolution:'Пылающая Комета',model:'weapon08',projectile:'fire',type:'shot',damage:21,cooldown:.82,speed:24,count:2,pierce:3,range:31,scale:.23,passive:'duration',fx:0xff9b4c},",
'fox_bow':"{id:'fox_bow',name:'Лисий Лук',evolution:'Ливень Лисьих Стрел',model:'weapon09',projectile:'arrow',type:'shot',damage:22,cooldown:.62,speed:32,count:1,pierce:2,crit:.14,range:37,scale:.27,passive:'crit',fx:0xffa24c},",
'cloud_orb':"{id:'cloud_orb',name:'Штормовой Посох',evolution:'Грозовой Спутник',model:'weapon10',projectile:'gem',type:'homing',damage:17,cooldown:.58,speed:20,count:3,homing:9,pierce:1,range:31,scale:.20,passive:'speed',fx:0xb9e9ff},",
'watermelon':"{id:'watermelon',name:'Золотой Лук',evolution:'Небесная Баллиста',model:'weapon11',projectile:'arrow',type:'bomb',damage:38,cooldown:1.18,speed:23,count:1,splash:4.8,knockback:2.8,range:34,scale:.31,passive:'area',fx:0xffdc72},",
'music_wave':"{id:'music_wave',name:'Молот Грома',evolution:'Громовой Венец',model:'weapon12',projectile:'star',type:'radial',damage:13,cooldown:1.02,speed:21,count:10,pierce:2,range:24,scale:.17,passive:'projectiles',fx:0x8e8cff},",
'smart_bee':"{id:'smart_bee',name:'Кинжалы Роя',evolution:'Королевский Рой',model:'weapon13',projectile:'weapon13',type:'homing',damage:15,cooldown:.42,speed:22,count:3,homing:11,range:30,scale:.18,passive:'cooldown',fx:0xffe45d},",
'snowball':"{id:'snowball',name:'Морозный Посох',evolution:'Полярная Буря',model:'weapon14',projectile:'gem',type:'fan',damage:15,cooldown:.70,speed:20,count:4,spread:.15,slow:.34,range:28,scale:.19,passive:'duration',fx:0x9feaff},",
'pumpkin':"{id:'pumpkin',name:'Двойной Топор',evolution:'Вихрь Берсерка',model:'weapon15',projectile:'weapon15',type:'boomerang',damage:27,cooldown:.86,speed:21,count:2,pierce:5,range:25,scale:.20,passive:'area',fx:0xff873f},"}
for wid,line in weapons.items(): c=sub1(c,rf"^\s*\{{id:'{re.escape(wid)}'.*$","  "+line,f'weapon {wid}',re.M)

maps={
"['sunny_meadow','Солнечный луг','Тёплая зелёная поляна','forest','#72b35a','#d7f3b6','#92c972','tree']":"['sunny_meadow','Солнечный луг','Большая светлая долина с рощами, лагерями и каменными тропами','forest','#6fb35b','#d9f1bd','#8ecb75','tree']",
"['magic_forest','Волшебный лес','Сказочный лес со светящимися кристаллами','forest','#315b4d','#6fc9a4','#4b8468','tree']":"['magic_forest','Изумрудная чаща','Глубокий лес с кристальными полянами и древними стоянками','forest','#315b4d','#74cba7','#4f8d70','tree']",
"['candy_park','Карамельный парк','Яркий парк с десертными декорациями','park','#ef9dc4','#fff0bd','#f5b4cf','food_cookie']":"['candy_park','Сад цветения','Яркий парк с цветочными аллеями, арками и павильонами','park','#d88aaa','#fff0c9','#f1abc4','birch']",
"['pumpkin_village','Тыквенная деревня','Тёплая осенняя деревушка','village','#ad6942','#ffd394','#c8874b','deadTree']":"['pumpkin_village','Янтарная деревня','Осенняя деревня с домами, рынком и старыми дорогами','village','#a96645','#ffd49b','#c78753','deadTree']",
"['snow_festival','Снежный праздник','Светлая зимняя долина','snow','#dce8ef','#f8fbff','#b8d5e6','rock']":"['snow_festival','Снежная долина','Светлая зимняя долина с хвойными рощами и лагерями','snow','#dce8ef','#f8fbff','#b8d5e6','rock']",
"['toy_castle','Игрушечный замок','Цветной замковый двор','castle','#7da0c6','#f5e4aa','#9fbde0','column']":"['toy_castle','Лазурная цитадель','Солнечный каменный двор с арками, башнями и укреплениями','castle','#789cc4','#f5e4aa','#9fbde0','column']",
"['bubble_beach','Пузырьковый пляж','Песчаная бухта с морской границей','beach','#d9bd79','#c7f5ff','#e9d590','rock']":"['bubble_beach','Сапфировый берег','Песчаная бухта с пальмами, лагерями и водной границей','beach','#d9bd79','#c7f5ff','#e9d590','rock']",
"['cheese_moon','Сырная луна','Кратеры, камни и звёздный горизонт','moon','#cdbf78','#1d2442','#b5aa6c','rock']":"['cheese_moon','Лунное плато','Кратеры, кристаллы и холодный звёздный горизонт','moon','#a69b78','#1d2442','#b5aa8c','rock']",
"['cookie_desert','Печеньковая пустыня','Тёплая пустыня и сладкие руины','desert','#c79662','#f6cf8f','#d9aa74','food_cookie']":"['cookie_desert','Золотые дюны','Тёплая пустыня с оазисами, руинами и скальными проходами','desert','#c79662','#f6cf8f','#d9aa74','rock']",
"['music_fair','Музыкальная ярмарка','Праздничная площадь и сцена','fair','#bd7d72','#ffe1a2','#d29588','arch']":"['music_fair','Солнечная ярмарка','Праздничная площадь с арками, лавками и огнями','fair','#bd7d72','#ffe1a2','#d29588','arch']",
"['festival_finale','Финал фестиваля','Самая яркая праздничная карта','festival','#d2788d','#ffe6a9','#e294a6','arch']":"['festival_finale','Лучезарный фестиваль','Большая праздничная столица с павильонами и сияющими площадями','festival','#cf7489','#ffe6a9','#e294a6','arch']"}
for old,new in maps.items():
    if old not in c: raise SystemExit('v3.1 map identity patch failed: '+old[:35])
    c=c.replace(old,new,1)

s=c.index('const layouts = {'); e=c.index('export const MAPS',s); q=c[s:e]
for old,new in [('food_cookie','flower'),('food_donut','gem'),('food_pumpkin','rock'),('food_watermelon','bush')]: q=q.replace(old,new)
c=c[:s]+q+c[e:]
c=c.replace("width:148+(i%3)*10,height:118+(i%4)*9,unlockWins:i===0?0:i,enemyTier:i,bossIndex:i,","width:188+(i%3)*14,height:154+(i%4)*12,unlockWins:i===0?0:i,enemyTier:i,bossIndex:i,")
c=sub1(c,r"export const BOSSES = \[\n.*?\n\]\.map\(\(name,i\)=>","""export const BOSSES = [
  'Хранитель Рощи','Древний Каменный Страж','Королева Призм','Янтарный Воевода','Ледяной Исполин','Страж Цитадели','Повелитель Прилива','Лунный Зверь','Архонт Механизмов','Дракон Каньона','Хозяин Кристальных Пещер','Грозовой Архонт','Песчаный Колосс','Владыка Болотных Огней','Маэстро Бури','Кристальный Мамонт','Страж Звёздных Крыш','Король Лучезарного Фестиваля'
].map((name,i)=>""",'boss roster',re.S)

m=m.replace('this.renderer.toneMappingExposure=1.16;','this.renderer.toneMappingExposure=1.24;')
m=m.replace("this.scene.background=new THREE.Color(map.sky);this.scene.fog=new THREE.FogExp2(map.sky,this.lowPower?.0085:.0068);","this.scene.background=new THREE.Color(map.sky);this.scene.fog=new THREE.FogExp2(map.sky,this.lowPower?.0075:.0056);")
old="const groundSize=Math.max(map.width,map.height)+76,ground=this.prepareGround(this.assets.floor,groundSize,map.ground);ground.position.y=-.06;this.environment.add(ground);"
if old not in m: raise SystemExit('v3.1 ground call patch failed')
m=m.replace(old,'this.buildGroundField(map);',1)

marker='  buildV3SetPieces(map){'
methods="""  buildGroundField(map){
    const tile=this.lowPower?22:15,hx=map.width*.5+18,hz=map.height*.5+18;
    const base=this.prepareGround(this.assets.floor,tile,map.ground);let row=0;
    for(let z=-hz;z<=hz;z+=tile*.94,row++){let col=0;for(let x=-hx;x<=hx;x+=tile*.94,col++){
      const r=base.clone(true);r.position.set(x+(row%2?tile*.18:0),-.075,z);r.rotation.y=((row+col)%2)*Math.PI*.5;
      const sc=.98+((row*17+col*11)%7)*.008;r.scale.set(sc,1,sc);this.environment.add(r);
    }}
  }

  buildNearFieldLandmarks(map){
    const L={
      forest:[['tent',-15,-12,2.2,.25],['campfire',-11,-9,1.05,0],['wagon',17,13,2.4,-.55],['well',14,-14,1.9,0],['birch',-21,8,4.1,.2],['tree',21,-5,4.4,-.2]],
      park:[['stall',-16,-12,2.5,.2],['bench',-11,-8,1.25,.9],['well',15,-13,1.9,0],['bench',11,-9,1.25,-.9],['birch',-20,12,3.8,.2],['birch',20,11,3.8,-.2]],
      village:[['house',-22,-13,4.8,.18],['stall',-14,12,2.5,.25],['well',14,-12,2.0,0],['wagon',19,13,2.4,-.5],['barrel',10,10,1.0,0]],
      snow:[['tent',-16,-12,2.2,.2],['campfire',-12,-9,1.05,0],['house',20,13,4.6,-.2],['pineSnow',-22,9,4.2,.1],['pineSnow',22,-7,4.1,-.2]],
      castle:[['arch',0,-20,4.5,0],['column',-16,-12,3.5,0],['column',16,-12,3.5,0],['chest',-12,11,1.2,.2],['torch',12,11,1.5,0]],
      beach:[['tent',-16,-11,2.1,.2],['campfire',-12,-8,1.0,0],['raft',18,13,2.6,-.4],['palm',-21,10,4.5,.1],['palm',21,-8,4.4,-.1]],
      moon:[['gem',-14,-12,1.7,.2],['gem',15,-10,1.5,-.2],['rock',-21,9,2.1,.2],['rock',21,10,2.2,-.2],['campfire',0,18,1.0,0]],
      clock:[['arch',0,-20,4.2,0],['column',-16,-10,3.5,0],['column',16,-10,3.5,0],['well',0,16,1.9,0],['bench',-11,12,1.2,.8],['bench',11,12,1.2,-.8]],
      canyon:[['tent',-15,-12,2.1,.2],['campfire',-11,-9,1.0,0],['wagon',18,14,2.4,-.5],['cactus',-21,7,2.3,.1],['rock',21,-6,2.3,-.1],['sign',14,-14,1.4,.2]],
      cave:[['gem',-14,-11,1.8,.2],['gem',14,-11,1.8,-.2],['mossRock',-20,10,2.0,.1],['mossRock',20,10,2.0,-.1],['chest',0,17,1.25,0]],
      sky:[['arch',0,-20,4.4,0],['column',-16,-11,3.5,0],['column',16,-11,3.5,0],['bench',-11,13,1.2,.8],['bench',11,13,1.2,-.8],['gem',0,17,1.5,0]],
      desert:[['tent',-15,-12,2.1,.2],['campfire',-11,-9,1.0,0],['well',15,-13,1.9,0],['palm',21,10,4.3,-.1],['cactus',-21,8,2.3,.1],['wagon',17,14,2.4,-.5]],
      swamp:[['tent',-16,-12,2.0,.2],['campfire',-12,-9,1.0,0],['torch',-9,12,1.6,0],['torch',9,12,1.6,0],['willow',-22,8,4.3,.1],['willow',22,-7,4.2,-.1]],
      fair:[['stall',-16,-12,2.5,.2],['stall',16,-12,2.5,-.2],['bench',-11,11,1.2,.8],['bench',11,11,1.2,-.8],['arch',0,18,4.2,0],['torch',0,-18,1.6,0]],
      crystal:[['gem',-15,-11,2.0,.2],['gem',15,-11,2.0,-.2],['mossRock',-21,10,2.0,.1],['mossRock',21,10,2.0,-.1],['chest',0,17,1.2,0]],
      rooftop:[['arch',0,-20,4.2,0],['column',-16,-11,3.4,0],['column',16,-11,3.4,0],['torch',-10,13,1.6,0],['torch',10,13,1.6,0],['crate2',0,17,1.2,0]],
      festival:[['stall',-16,-12,2.5,.2],['stall',16,-12,2.5,-.2],['arch',0,19,4.4,0],['well',0,-18,1.9,0],['bench',-11,11,1.2,.8],['bench',11,11,1.2,-.8]]};
    const list=L[map.layout]||L.forest;
    for(const [asset,x,z,height,rot] of list){if(map.hazard){const coord=map.hazard.axis==='x'?x:z;if(Math.abs(coord-map.hazard.at)<map.hazard.width*.75)continue;}
      const root=this.cloneVisual(this.assets[asset]||this.assets.rock,height,{tint:null});root.position.set(x,0,z);root.rotation.y=rot||0;this.environment.add(root);
      if(!['campfire','torch','barrel','bench','sign','gem'].includes(asset))this.obstacles.push({x,z,radius:asset==='house'?1.8:asset==='wagon'?1.05:asset==='arch'?1.0:.7});}
  }

"""
if marker not in m: raise SystemExit('v3.1 set-piece insert marker missing')
m=m.replace(marker,methods+marker,1)
m=m.replace('this.buildMacroClusters(map);this.buildV3SetPieces(map);this.buildBoundary(map);','this.buildMacroClusters(map);this.buildV3SetPieces(map);this.buildNearFieldLandmarks(map);this.buildBoundary(map);',1)
for old,new in [('food_donut','star'),('food_cookie','flower'),('food_pumpkin','rock'),('food_watermelon','bush'),('food_pancake','gem')]: m=m.replace(old,new)
m=m.replace('const coverCount=this.lowPower?118:238;','const coverCount=this.lowPower?150:340;').replace('const propCount=this.lowPower?46:86;','const propCount=this.lowPower?58:112;')
m=m.replace("const k=this.heldProfile(w).attack,t=k==='throw'?['throw','attack','punch','shoot']:k==='shoot'?['shoot','rifle','attack','throw','punch']:['spell','cast','attack','punch','shoot'];","const k=this.heldProfile(w).attack,t=k==='throw'?['shoot','throw','attack','punch']:k==='shoot'?['shoot','rifle','attack','throw','punch']:['shoot','spell','cast','attack','throw','punch'];")
m=m.replace("const pos=this.player.root.position,target=new THREE.Vector3(pos.x,pos.y+(this.lowPower?9.7:7.4),pos.z+(this.lowPower?12.2:9.8)),look=new THREE.Vector3(pos.x,pos.y+1.15,pos.z-2.9)","const pos=this.player.root.position,target=new THREE.Vector3(pos.x,pos.y+(this.lowPower?8.8:6.55),pos.z+(this.lowPower?10.8:8.35)),look=new THREE.Vector3(pos.x,pos.y+1.15,pos.z-2.15)")
m=m.replace('this.camera.position.set(0,this.lowPower?10.2:9.2,this.lowPower?12.0:10.6);','this.camera.position.set(0,this.lowPower?9.0:6.8,this.lowPower?10.9:8.7);')
oldspawn="""const root=this.cloneVisual(this.assets[def.asset],def.scale*(elite?1.45:1),{tint:elite?0xffd76a:null});const hx=map.width/2-6,hz=map.height/2-6;let x,z;
    const side=Math.floor(Math.random()*4);if(side===0){x=-hx;z=(Math.random()*2-1)*hz;}else if(side===1){x=hx;z=(Math.random()*2-1)*hz;}else if(side===2){z=-hz;x=(Math.random()*2-1)*hx;}else{z=hz;x=(Math.random()*2-1)*hx;}"""
newspawn="""const root=this.cloneVisual(this.assets[def.asset],def.scale*(elite?1.45:1),{tint:elite?0xffd76a:null});const hx=map.width/2-7,hz=map.height/2-7;let x,z;
    const center=this.player?.root?.position||new THREE.Vector3(),ang=Math.random()*TAU,dist=(this.lowPower?17:19)+Math.random()*9;
    x=clamp(center.x+Math.cos(ang)*dist,-hx,hx);z=clamp(center.z+Math.sin(ang)*dist,-hz,hz);"""
if oldspawn not in m: raise SystemExit('v3.1 enemy spawn patch failed')
m=m.replace(oldspawn,newspawn,1)
c=sub1(c,r"^\s*pancake:\[.*$"," pancake:['weapon08',.56,'shoot',[.05,-.14,-.03],[0,.10,.05],'low'],",'held profile weapon08',re.M)

bp.write_text(b,encoding='utf-8'); cp.write_text(c,encoding='utf-8'); mp.write_text(m,encoding='utf-8')
print('v3.1 quality pass applied: professional maps, no food combat, readable weapons, denser near-field art')
