from pathlib import Path
import re
p=Path('skyfrontiers3d/game.js')
s=p.read_text()

# Terrain helper cleanup from V4.
s=s.replace('ridgesafe(ridge)','ridge')
s=re.sub(r'\n?function ridgesafe\(v\)\{return v;\}\n?','\n',s)
if 'ridgesafe' in s:
    raise SystemExit('unexpected ridgesafe remains')

# FlightGear AC models point nose toward -X. Map that explicitly to the game's -Z forward axis.
s=s.replace("axisSign:1", "axisSign:-1")

# FlightGear source units are already metres. Do not shrink detailed models because of detached animation/light nodes.
s=s.replace("cameraFactor:o.cameraFactor||1,level:o.level||1", "cameraFactor:o.cameraFactor||1,nativeScale:!!o.nativeScale,level:o.level||1")
s=s.replace("axisSign:-1,cameraFactor", "axisSign:-1,nativeScale:true,cameraFactor")
s=s.replace("{forwardAxis:'x',axisSign:-1}", "{forwardAxis:'x',axisSign:-1,nativeScale:true}")
s=s.replace("const sourceLength=Math.max(.001,size.z);const s=targetLength/sourceLength;model.scale.multiplyScalar(s);", "const sourceLength=Math.max(.001,size.z);const s=p.nativeScale?1:targetLength/sourceLength;model.scale.multiplyScalar(s);")

# Put the wheels onto the runway top instead of hovering above it.
s=s.replace("const x=-6500,z=-2850,y=terrainHeight(x,z)+6.2;", "const x=-6500,z=-2850,y=terrainHeight(x,z)+4.6;")

for required in ["axisSign:-1","nativeScale:true","p.nativeScale?1:targetLength/sourceLength","terrainHeight(x,z)+4.6"]:
    if required not in s:
        raise SystemExit('missing V6 marker '+required)

p.write_text(s)
print('v6 orientation/scale/runway hotfix applied')
