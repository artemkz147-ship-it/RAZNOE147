from pathlib import Path

p=Path('src/main.js')
s=p.read_text(encoding='utf-8')

s=s.replace("this.camera=new THREE.PerspectiveCamera(48,1,.1,220); this.camera.position.set(0,11.5,13.5);","this.camera=new THREE.PerspectiveCamera(46,1,.1,220); this.camera.position.set(0,9.4,11.2);")
s=s.replace("this.bridge.startGameplay();this.camera.position.set(0,11.4,13.2);this.updateHUD();","this.bridge.startGameplay();this.camera.position.set(0,this.lowPower?10.2:9.2,this.lowPower?12.0:10.6);this.updateHUD();")
s=s.replace("const pos=this.player.root.position,target=new THREE.Vector3(pos.x,pos.y+10.7,pos.z+12.6),look=new THREE.Vector3(pos.x,pos.y+1,pos.z-2.2);","const pos=this.player.root.position,target=new THREE.Vector3(pos.x,pos.y+(this.lowPower?9.6:8.6),pos.z+(this.lowPower?11.3:10.0)),look=new THREE.Vector3(pos.x,pos.y+1,pos.z-2.7);")

old="""    this.buildBoundary(map);
    if(map.hazard)this.buildHazard(map,map.hazard);
  }

  buildBoundary(map){"""
new="""    this.buildBoundary(map);
    if(map.hazard)this.buildHazard(map,map.hazard);
    this.populateMapDecor(map);
  }

  populateMapDecor(map){
    const themes={
      forest:['bush','flower','rock','tree'],park:['bush','flower','food_donut','food_cookie'],village:['food_pumpkin','bush','deadTree','rock'],snow:['snow','rock','tree','gem'],castle:['column','chest','torch','arch'],beach:['rock','food_watermelon','bush','tree'],moon:['rock','gem','star','rock'],clock:['column','gem','torch','arch'],canyon:['rock','deadTree','rock','gem'],cave:['gem','rock','gem','rock'],sky:['arch','column','gem','star'],desert:['rock','deadTree','food_cookie','rock'],swamp:['bush','tree','torch','rock'],fair:['food_donut','food_cookie','torch','flower'],crystal:['gem','rock','gem','star'],rooftop:['column','torch','chest','arch'],festival:['flower','food_donut','gem','torch']
    };
    const points=[[-.39,-.34],[-.28,-.31],[-.15,-.36],[.12,-.34],[.27,-.30],[.39,-.34],[-.43,-.16],[-.31,-.12],[-.18,-.17],[.18,-.15],[.31,-.12],[.43,-.17],[-.42,.02],[-.30,.08],[-.18,.12],[.19,.10],[.31,.07],[.43,.01],[-.40,.20],[-.28,.24],[-.13,.20],[.14,.23],[.28,.25],[.40,.20],[-.34,.35],[-.20,.34],[.20,.34],[.35,.35],[-.07,-.25],[.08,-.22],[-.08,.27],[.09,.29]];
    const list=themes[map.layout]||themes.forest,hx=map.width*.5,hz=map.height*.5;
    for(let i=0;i<points.length;i++){
      const [nx,nz]=points[i],x=nx*map.width,z=nz*map.height;if(Math.hypot(x,z)<9)continue;
      if(map.hazard){const c=map.hazard.axis==='x'?x:z;if(Math.abs(c-map.hazard.at)<map.hazard.width*.72)continue;}
      const key=list[(i+map.enemyTier)%list.length],large=['tree','deadTree','column','arch'].includes(key),solid=['rock','column','arch','tree','deadTree'].includes(key);
      const height=large?2.7+(i%4)*.32:.62+(i%5)*.11,root=this.cloneVisual(this.assets[key]||this.assets.bush,height,{tint:map.accent});
      root.position.set(x,0,z);root.rotation.y=((i*1.77+map.enemyTier*.61)%TAU);this.environment.add(root);
      if(solid)this.obstacles.push({x,z,radius:large?.72:.48});
    }
  }

  buildBoundary(map){"""
if old not in s: raise SystemExit('buildEnvironment insertion point missing')
s=s.replace(old,new)
p.write_text(s,encoding='utf-8')

# Public assets must remain relative so the same build works from Yandex's
# game root, a preview server, and nested static hosting.
c=Path('src/content.js')
t=c.read_text(encoding='utf-8').replace("'/assets/","'./assets/").replace('`/assets/','`./assets/')
c.write_text(t,encoding='utf-8')

# Use visual sources that make sense when seen in flight instead of fallback
# scenery. The tiny robot projectile uses the authored Robot character mesh;
# the cloud spell uses a faceted magical orb rather than a snow-tree fallback.
b=Path('tools/build_assets.sh')
bs=b.read_text(encoding='utf-8')
bs=bs.replace('CLOUD="$SNOW"','CLOUD="$GEM"')
bs=bs.replace('MINIROBOT="$(pick "$ROOT/guns" \'Grenade\' \'Mine\' \'Pistol\')"','MINIROBOT="$(pick "$ROOT/characters" \'Robot\' \'Mech\' \'Knight\')"')
b.write_text(bs,encoding='utf-8')
print('camera, map density, relative asset paths, and projectile visuals polished')
