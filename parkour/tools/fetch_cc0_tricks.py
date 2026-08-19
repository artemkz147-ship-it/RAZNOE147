import json
import os
import shutil
import struct
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(ROOT, 'public', 'assets3d', 'character')
MANIFEST = os.path.join(ROOT, 'src', 'drop', 'tricks.json')

# Pinned public mirror of Quaternius Universal Animation Library 2 [Standard].
# UAL2 is CC0 and contains the parkour-focused animation set. We package it
# locally so there are no runtime network dependencies.
COMMIT = 'bb2ce469f0ab2ccfc487faef55550ba07fae2483'
URL = (
    'https://raw.githubusercontent.com/Barbatos6669/elderforge/'
    f'{COMMIT}/assets/animations/universal_animation_library_2/UAL2_Standard.glb'
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
        file.write('\n')


try:
    request = urllib.request.Request(URL, headers={'User-Agent': 'Mozilla/5.0 DropFlowBuild/2.4'})
    print('Downloading pinned CC0 Universal Animation Library 2 parkour set...')
    with urllib.request.urlopen(request, timeout=90) as response:
        data = response.read()

    doc = glb_json(data)
    if not doc:
        raise RuntimeError('Downloaded UAL2 parkour library is not a valid GLB')
    clips = [str(item.get('name') or f'clip_{i}') for i, item in enumerate(doc.get('animations', []))]
    if len(clips) < 10:
        raise RuntimeError(f'UAL2 parkour library contains too few clips: {len(clips)}')

    target = os.path.join(OUT, 'parkour_tricks.glb')
    with open(target, 'wb') as file:
        file.write(data)
    write_manifest('assets3d/character/parkour_tricks.glb', clips)
    with open(os.path.join(OUT, 'LICENSE_UAL2_CC0.txt'), 'w', encoding='utf-8') as file:
        file.write(
            'Universal Animation Library 2 [Standard]\n'
            'Creator: Quaternius\n'
            'License: Creative Commons Zero 1.0 (CC0)\n'
            'Source: https://quaternius.com/\n'
            f'Pinned public mirror commit: {COMMIT}\n'
        )
    print(f'Prepared UAL2 parkour library: {len(clips)} clips, {len(data) / 1024 / 1024:.1f} MB')
    print('UAL2 clips: ' + ' | '.join(clips))
except Exception as error:
    print(f'WARNING: CC0 UAL2 trick fetch failed: {error}')
    write_manifest()
