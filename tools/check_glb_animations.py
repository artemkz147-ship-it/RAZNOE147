from pathlib import Path
import json, struct
ROOT=Path('public/assets')
FILES=[*(f'hero-{i:02}.glb' for i in range(1,16)),*(f'monster-{i:02}.glb' for i in range(1,13))]

def animation_names(path):
    data=path.read_bytes()
    if len(data)<20 or data[:4]!=b'glTF': raise SystemExit(f'Invalid GLB: {path}')
    total=struct.unpack_from('<I',data,8)[0];off=12;payload=None
    while off<total:
        length,kind=struct.unpack_from('<II',data,off);off+=8;chunk=data[off:off+length];off+=length
        if kind==0x4E4F534A: payload=json.loads(chunk.decode('utf-8').rstrip('\x00 '));break
    if payload is None: raise SystemExit(f'No JSON chunk: {path}')
    return [str(a.get('name','')).lower() for a in payload.get('animations',[])]

for name in FILES:
    names=animation_names(ROOT/name)
    print(name,len(names),names[:18])
    if len(names)<2: raise SystemExit(f'{name}: expected an authored animation library')
    locomotion=any(any(t in n for t in ('idle','run','walk','fly','flying','move')) for n in names)
    if not locomotion: raise SystemExit(f'{name}: missing locomotion/idle animation')
    if name.startswith('hero-'):
        action=any(any(t in n for t in ('attack','shoot','rifle','spell','throw','punch','hit')) for n in names)
        if not action: raise SystemExit(f'{name}: missing ranged/combat-capable action clip')
print('27 animated character/monster GLB libraries validated.')
