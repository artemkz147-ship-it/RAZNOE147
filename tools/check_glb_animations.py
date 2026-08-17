from pathlib import Path
import json
import struct

ROOT = Path('public/assets')


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
        if kind == 0x4E4F534A:  # JSON
            payload = json.loads(chunk.decode('utf-8').rstrip('\x00 '))
            break
    if payload is None:
        raise SystemExit(f'No JSON chunk in GLB: {path}')
    return [str(a.get('name', '')).lower() for a in payload.get('animations', [])]


checks = {
    'hero.glb': ['idle', 'run', 'attack', 'death'],
    'skeleton.glb': ['idle', 'run', 'attack', 'death'],
    'slime.glb': ['idle', 'attack', 'death'],
    'bat.glb': ['idle', 'attack', 'death'],
    'dragon.glb': ['idle', 'attack', 'death'],
}

for filename, required in checks.items():
    names = animation_names(ROOT / filename)
    print(f'{filename}: {len(names)} clips -> {names}')
    if len(names) < len(required):
        raise SystemExit(f'{filename} contains too few animation clips: {len(names)}')
    for token in required:
        if token == 'run' and filename != 'hero.glb':
            if any(('run' in n or 'walk' in n or 'fly' in n) for n in names):
                continue
        if not any(token in n for n in names):
            raise SystemExit(f'{filename} missing required animation token: {token}')

print('Animated GLB libraries validated.')
