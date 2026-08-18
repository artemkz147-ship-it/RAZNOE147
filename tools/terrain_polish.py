from pathlib import Path

# 1) Expand the imported CC0 nature vocabulary used by the runtime.
bp=Path('tools/build_assets.sh')
bs=bp.read_text(encoding='utf-8')
anchor="""FLOWER="$(pick "$ROOT/nature" 'Flower' 'Plant' 'Bush')"
SNOW="$(pick "$ROOT/nature" 'Snow' 'Rock' 'Flower')"
GEM="$(pick "$ROOT/rpg" 'Gems' 'Gem' 'Crystal')"""
replacement="""FLOWER="$(pick "$ROOT/nature" 'Flowers.fbx' 'Flower' 'Plant')"
GRASS="$(pick "$ROOT/nature" 'Grass.fbx' 'Grass_2' 'Plant')"
GRASS_SHORT="$(pick "$ROOT/nature" 'Grass_Short' 'Grass_2' 'Grass')"
SNOW_ROCK="$(pick "$ROOT/nature" 'Rock_Snow_1' 'Rock_Snow' 'Snow')"
PINE_SNOW="$(pick "$ROOT/nature" 'PineTree_Snow_1' 'PineTree_Snow' 'PineTree')"
BIRCH="$(pick "$ROOT/nature" 'BirchTree_1' 'BirchTree')"
BIRCH_AUTUMN="$(pick "$ROOT/nature" 'BirchTree_Autumn_1' 'Autumn')"
WILLOW="$(pick "$ROOT/nature" 'Willow_1' 'Willow')"
CACTUS="$(pick "$ROOT/nature" 'Cactus_1' 'Cactus')"
PALM="$(pick "$ROOT/nature" 'PalmTree_1' 'PalmTree')"
MOSS_ROCK="$(pick "$ROOT/nature" 'Rock_Moss_1' 'Rock_Moss' 'Rock')"
SNOW="$SNOW_ROCK"
GEM="$(pick "$ROOT/rpg" 'Gems' 'Gem' 'Crystal')"""
if anchor in bs:
    bs=bs.replace(anchor,replacement)

conv_anchor="""convert "$FLOWER" flower.glb static
convert "$SNOW" snow.glb static
convert "$GEM" gem.glb static"""
conv_replacement="""convert "$FLOWER" flower.glb static
convert "$GRASS" grass.glb static
convert "$GRASS_SHORT" grass-short.glb static
convert "$SNOW_ROCK" snow-rock.glb static
convert "$PINE_SNOW" pine-snow.glb static
convert "$BIRCH" birch.glb static
convert "$BIRCH_AUTUMN" birch-autumn.glb static
convert "$WILLOW" willow.glb static
convert "$CACTUS" cactus.glb static
convert "$PALM" palm.glb static
convert "$MOSS_ROCK" moss-rock.glb static
convert "$SNOW" snow.glb static
convert "$GEM" gem.glb static"""
if conv_anchor in bs:
    bs=bs.replace(conv_anchor,conv_replacement)
bp.write_text(bs,encoding='utf-8')

# 2) Register the extra imported assets.
cp=Path('src/content.js')
c=cp.read_text(encoding='utf-8')
asset_anchor="""const a={floor:'./assets/floor.glb',tree:'./assets/tree.glb',deadTree:'./assets/dead-tree.glb',bush:'./assets/bush.glb',rock:'./assets/rock.glb',gem:'./assets/gem.glb'"""
asset_replacement="""const a={floor:'./assets/floor.glb',tree:'./assets/tree.glb',deadTree:'./assets/dead-tree.glb',bush:'./assets/bush.glb',rock:'./assets/rock.glb',grass:'./assets/grass.glb',grassShort:'./assets/grass-short.glb',snowRock:'./assets/snow-rock.glb',pineSnow:'./assets/pine-snow.glb',birch:'./assets/birch.glb',birchAutumn:'./assets/birch-autumn.glb',willow:'./assets/willow.glb',cactus:'./assets/cactus.glb',palm:'./assets/palm.glb',mossRock:'./assets/moss-rock.glb',gem:'./assets/gem.glb'"""
if asset_anchor in c:
    c=c.replace(asset_anchor,asset_replacement)
cp.write_text(c,encoding='utf-8')

# 3) Make outdoor terrain read like terrain rather than a visible tiled board,
# add deterministic dense biome dressing, and expose QA map/hero selectors.
mp=Path('src/main.js')
m=mp.read_text(encoding='utf-8')
if "const DEBUG_MAP = qs.get('map');" not in m:
    m=m.replace("const DEBUG_FAST = qs.get('debug-fast') === '1';", "const DEBUG_FAST = qs.get('debug-fast') === '1';\nconst DEBUG_MAP = qs.get('map');\nconst DEBUG_HERO = qs.get('hero');")

load_anchor="""    this.selectedHero=HEROES.some(h=>h.id===this.progress.selectedHero)?this.progress.selectedHero:HEROES[0].id;
    this.selectedMap=MAPS.some(m=>m.id===this.progress.selectedMap)?this.progress.selectedMap:MAPS[0].id;
    this.selectedMode=MODES.some(m=>m.id===this.progress.selectedMode)?this.progress.selectedMode:MODES[0].id;"""
load_replacement="""    this.selectedHero=HEROES.some(h=>h.id===this.progress.selectedHero)?this.progress.selectedHero:HEROES[0].id;
    this.selectedMap=MAPS.some(m=>m.id===this.progress.selectedMap)?this.progress.selectedMap:MAPS[0].id;
    this.selectedMode=MODES.some(m=>m.id===this.progress.selectedMode)?this.progress.selectedMode:MODES[0].id;
    if(AUTO_START&&DEBUG_MAP&&MAPS.some(x=>x.id===DEBUG_MAP))this.selectedMap=DEBUG_MAP;
    if(AUTO_START&&DEBUG_HERO&&HEROES.some(x=>x.id===DEBUG_HERO))this.selectedHero=DEBUG_HERO;"""
if load_anchor in m:
    m=m.replace(load_anchor,load_replacement)

old_floor="""    const tileSize=8,cols=Math.ceil((map.width+32)/tileSize),rows=Math.ceil((map.height+32)/tileSize),base=this.prepareGround(this.assets.floor,tileSize,map.ground);
    for(let ix=-Math.ceil(cols/2);ix<=Math.ceil(cols/2);ix++)for(let iz=-Math.ceil(rows/2);iz<=Math.ceil(rows/2);iz++){const t=base.clone(true);t.position.set(ix*tileSize,0,iz*tileSize);t.rotation.y=((ix+iz)&1)?Math.PI/2:0;this.environment.add(t);}"""
new_floor="""    const indoor=['castle','clock','sky','rooftop'].includes(map.layout),tileSize=indoor?12:22,cols=Math.ceil((map.width+36)/tileSize),rows=Math.ceil((map.height+36)/tileSize),base=this.prepareGround(this.assets.floor,tileSize,map.ground);
    for(let ix=-Math.ceil(cols/2);ix<=Math.ceil(cols/2);ix++)for(let iz=-Math.ceil(rows/2);iz<=Math.ceil(rows/2);iz++){const t=base.clone(true);t.position.set(ix*tileSize,-.035,iz*tileSize);t.rotation.y=((ix*5+iz*3)&3)*Math.PI/2;t.scale.setScalar(1.035);this.environment.add(t);}"""
if old_floor in m:
    m=m.replace(old_floor,new_floor)

start=m.find('  populateMapDecor(map){')
end=m.find('\n  buildBoundary(map){',start)
if start<0 or end<0:
    raise SystemExit('populateMapDecor method not found')
new_method=r'''  populateMapDecor(map){
    const themes={
      forest:{cover:['grass','grassShort','flower'],props:['birch','tree','mossRock','bush']},
      park:{cover:['grassShort','flower','grass'],props:['birch','food_donut','food_cookie','bush']},
      village:{cover:['grassShort','flower','grass'],props:['birchAutumn','deadTree','food_pumpkin','rock']},
      snow:{cover:['snowRock','grassShort','snowRock'],props:['pineSnow','snowRock','gem','pineSnow']},
      castle:{cover:['rock','chest','torch'],props:['column','arch','chest','torch']},
      beach:{cover:['grassShort','rock','flower'],props:['palm','food_watermelon','rock','palm']},
      moon:{cover:['rock','gem','rock'],props:['rock','gem','star','rock']},
      clock:{cover:['gem','torch','rock'],props:['column','arch','chest','gem']},
      canyon:{cover:['rock','cactus','rock'],props:['cactus','deadTree','rock','mossRock']},
      cave:{cover:['gem','rock','gem'],props:['mossRock','gem','rock','gem']},
      sky:{cover:['gem','star','flower'],props:['arch','column','gem','arch']},
      desert:{cover:['cactus','rock','grassShort'],props:['cactus','palm','deadTree','rock']},
      swamp:{cover:['grass','grassShort','mossRock'],props:['willow','bush','mossRock','willow']},
      fair:{cover:['flower','grassShort','food_cookie'],props:['arch','food_donut','torch','birch']},
      crystal:{cover:['gem','rock','gem'],props:['gem','mossRock','star','gem']},
      rooftop:{cover:['torch','chest','rock'],props:['column','arch','torch','chest']},
      festival:{cover:['flower','grassShort','food_donut'],props:['arch','birch','gem','torch']}
    };
    const theme=themes[map.layout]||themes.forest,hx=map.width*.5,hz=map.height*.5;
    // Ground cover is dense but non-colliding and deterministic: it masks the
    // modular base surface while leaving readable lanes for combat.
    if(!['castle','clock','sky','rooftop'].includes(map.layout)){
      const coverCount=this.lowPower?44:78;
      for(let i=0;i<coverCount;i++){
        const a=i*2.3999632297+map.enemyTier*.47,rad=.13+((i*37)%100)/100*.73;
        let x=Math.cos(a)*hx*rad*.93,z=Math.sin(a)*hz*rad*.93;
        x+=Math.sin(i*5.17+map.enemyTier)*2.8;z+=Math.cos(i*4.31-map.enemyTier)*2.4;
        if(Math.hypot(x,z)<7.5||Math.abs(x)>hx-4||Math.abs(z)>hz-4)continue;
        if(map.hazard){const coord=map.hazard.axis==='x'?x:z;if(Math.abs(coord-map.hazard.at)<map.hazard.width*.72)continue;}
        const key=theme.cover[i%theme.cover.length],h=key.includes('grass')||key==='flower'?.34+(i%4)*.08:.48+(i%5)*.09;
        const root=this.cloneVisual(this.assets[key]||this.assets.bush,h,{shadow:false,tint:map.accent});root.position.set(x,.01,z);root.rotation.y=(i*.91)%TAU;root.scale.multiplyScalar(.78+((i*13)%29)/100);this.environment.add(root);
      }
    }
    const propCount=this.lowPower?24:42;
    for(let i=0;i<propCount;i++){
      const ring=.25+((i*29)%70)/100*.65,a=i*2.117+map.enemyTier*.71;
      let x=Math.cos(a)*hx*ring*.91,z=Math.sin(a)*hz*ring*.91;
      x+=Math.sin(i*1.91)*3.2;z+=Math.cos(i*2.23)*2.7;
      if(Math.hypot(x,z)<9||Math.abs(x)>hx-5||Math.abs(z)>hz-5)continue;
      if(map.hazard){const coord=map.hazard.axis==='x'?x:z;if(Math.abs(coord-map.hazard.at)<map.hazard.width*.8)continue;}
      const key=theme.props[(i+map.enemyTier)%theme.props.length],large=['tree','deadTree','pineSnow','birch','birchAutumn','willow','palm','column','arch'].includes(key);
      const height=large?2.7+(i%5)*.31:.68+(i%5)*.12,root=this.cloneVisual(this.assets[key]||this.assets.rock,height,{tint:map.accent});
      root.position.set(x,0,z);root.rotation.y=(i*1.43+map.enemyTier*.33)%TAU;this.environment.add(root);
      if(large||['rock','mossRock','cactus'].includes(key))this.obstacles.push({x,z,radius:large?.7:.48});
    }
  }
'''
m=m[:start]+new_method+m[end:]
mp.write_text(m,encoding='utf-8')
print('biome terrain polish and QA selectors applied')
