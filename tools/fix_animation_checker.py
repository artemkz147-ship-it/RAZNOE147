from pathlib import Path

checker = r'''from pathlib import Path
import json, struct
ROOT=Path('public/assets')
FILES=[*(f'hero-{i:02}.glb' for i in range(1,16)),*(f'monster-{i:02}.glb' for i in range(1,13))]

def animation_names(path):
    data=path.read_bytes()
    if len(data)<20 or data[:4]!=b'glTF': raise SystemExit(f'Invalid GLB: {path}')
    total=struct.unpack_from('<I',data,8)[0];off=12;payload=None
    while off<total:
        length,kind=struct.unpack_from('<II',data,off);off+=8;chunk=data[off:off+length];off+=length
        if kind==0x4E4F534A:
            payload=json.loads(chunk.decode('utf-8').rstrip('\x00 '));break
    if payload is None: raise SystemExit(f'No JSON chunk: {path}')
    return [str(a.get('name','')).lower() for a in payload.get('animations',[])]

warnings=[]
for name in FILES:
    names=animation_names(ROOT/name)
    print(name,len(names),names[:18])
    if not names: raise SystemExit(f'{name}: no authored animation clip')
    if name.startswith('hero-'):
        if len(names)<4: raise SystemExit(f'{name}: incomplete hero animation library')
        idle=any('idle' in n for n in names)
        locomotion=any(any(t in n for t in ('run','walk','move')) for n in names)
        action=any(any(t in n for t in ('attack','shoot','rifle','spell','throw','punch','hit')) for n in names)
        defeat=any(any(t in n for t in ('death','defeat','die')) for n in names)
        if not (idle and locomotion and action and defeat):
            raise SystemExit(f'{name}: hero needs idle + locomotion + ranged/combat + defeat clips')
    elif len(names)<2:
        warnings.append(f'{name}: one authored clip; gameplay controller supplies locomotion')
for warning in warnings: print('WARNING:',warning)
print('27 animated character/monster GLB libraries validated.')
'''
Path('tools/check_glb_animations.py').write_text(checker, encoding='utf-8')
print('animation checker patched')
