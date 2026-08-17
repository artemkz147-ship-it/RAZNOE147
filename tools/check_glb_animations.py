from pathlib import Path
import json
import struct

ROOT = Path('public/assets')
ANIMATED = ['hero.glb', 'skeleton.glb', 'slime.glb', 'bat.glb', 'dragon.glb']


def animation_names(path: Path):
    data = path.read_bytes()
    if len(data) < 20 or data[:4] != b'glTF':
        raise SystemExit(f'Invalid GLB: {path}')
    total = struct.unpack_from('<I', data, 8)[0]
    off = 12
    payload = None
    while off < total:
        length, kind = struct.unpack_from('<II', data, off)
        off += 8
        chunk = data[off:off + length]
        off += length
        if kind == 0x4E4F534A:
            payload = json.loads(chunk.decode('utf-8').rstrip('\x00 '))
            break
    if payload is None:
        raise SystemExit(f'No JSON chunk in GLB: {path}')
    return [str(a.get('name', '')).lower() for a in payload.get('animations', [])]


all_names = {name: animation_names(ROOT / name) for name in ANIMATED}
for filename, names in all_names.items():
    print(f'{filename}: {len(names)} clips -> {names}')
    if len(names) < 4:
        raise SystemExit(f'{filename} contains too few authored animation clips: {len(names)}')
    if not any('death' in n or 'die' in n for n in names):
        raise SystemExit(f'{filename} is missing a death animation')
    if not any('attack' in n or 'hit' in n for n in names):
        raise SystemExit(f'{filename} is missing a combat animation')
    if not any(any(token in n for token in ('idle','run','walk','fly','flying')) for n in names):
        raise SystemExit(f'{filename} is missing an idle/locomotion animation')

hero = all_names['hero.glb']
for token in ('idle','run','attack','death'):
    if not any(token in n for n in hero):
        raise SystemExit(f'hero.glb missing required animation token: {token}')

print('Animated GLB libraries validated.')
