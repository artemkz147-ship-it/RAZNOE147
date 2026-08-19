import json
import os
import shutil
import struct
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(ROOT, 'public', 'assets3d', 'character')
MANIFEST = os.path.join(ROOT, 'src', 'game3d', 'locomotion.json')
URL = (
    'https://raw.githubusercontent.com/Seyamalam/blood-league-kickoff/'
    'aa02a4e6d8337a0604d2da131bcbbeb1f01badf0/'
    'public/assets/vendor/quaternius/universal-animation-library.glb'
)

os.makedirs(OUT, exist_ok=True)


def glb_json(data: bytes):
    if len(data) < 20 or data[:4] != b'glTF':
        return None
    _, version, total = struct.unpack_from('<4sII', data, 0)
    if version != 2 or total > len(data):
        return None
    offset = 12
    while offset + 8 <= total:
        length, chunk_type = struct.unpack_from('<II', data, offset)
        offset += 8
        chunk = data[offset:offset + length]
        offset += length
        if chunk_type == 0x4E4F534A:
            return json.loads(chunk.decode('utf-8').rstrip('\x00 '))
    return None


def write_manifest(asset=None, clips=None):
    with open(MANIFEST, 'w', encoding='utf-8') as file:
        json.dump({'asset': asset, 'clips': clips or []}, file, ensure_ascii=False, indent=2)


try:
    request = urllib.request.Request(URL, headers={'User-Agent': 'Mozilla/5.0 DropFlowBuild/2.1'})
    print('Downloading web-ready CC0 Universal Animation Library...')
    with urllib.request.urlopen(request, timeout=60) as response:
        data = response.read()

    doc = glb_json(data)
    if not doc:
        raise RuntimeError('Downloaded animation library is not a valid GLB')
    clips = [str(item.get('name') or f'clip_{i}') for i, item in enumerate(doc.get('animations', []))]
    if not clips:
        raise RuntimeError('Animation library contains no clips')

    target = os.path.join(OUT, 'parkour_locomotion.glb')
    with open(target, 'wb') as file:
        file.write(data)
    write_manifest('assets3d/character/parkour_locomotion.glb', clips)
    print(f'Prepared web-ready CC0 animation library: {len(clips)} clips, {len(data) / 1024 / 1024:.1f} MB')
    print('Animation sample: ' + ', '.join(clips[:24]))
except Exception as error:
    print(f'WARNING: CC0 locomotion fetch failed: {error}')
    write_manifest()
