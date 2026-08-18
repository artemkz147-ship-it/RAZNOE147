import json
import os
import shutil
import struct
import tempfile
import urllib.request
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(ROOT, 'public', 'assets3d', 'character')
MANIFEST = os.path.join(ROOT, 'src', 'game3d', 'locomotion.json')
URL = 'https://opengameart.org/sites/default/files/universal_animation_librarystandard.zip'

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
    with tempfile.TemporaryDirectory(prefix='vertical-locomotion-') as temp:
        archive = os.path.join(temp, 'locomotion.zip')
        request = urllib.request.Request(
            URL,
            headers={
                'User-Agent': 'Mozilla/5.0 VerticalParkourBuild/1.0',
                'Referer': 'https://opengameart.org/content/universal-animation-library',
            },
        )
        print('Downloading Quaternius Universal Animation Library CC0...')
        with urllib.request.urlopen(request, timeout=60) as response, open(archive, 'wb') as output:
            shutil.copyfileobj(response, output)

        candidates = []
        with zipfile.ZipFile(archive) as package:
            for name in package.namelist():
                if not name.lower().endswith('.glb'):
                    continue
                data = package.read(name)
                doc = glb_json(data)
                if not doc:
                    continue
                animations = doc.get('animations', [])
                clips = [str(item.get('name') or f'clip_{i}') for i, item in enumerate(animations)]
                meshes = len(doc.get('meshes', []))
                skins = len(doc.get('skins', []))
                score = len(clips) * 50 + skins * 25 + meshes * 10
                candidates.append((score, len(data), name, data, clips, meshes, skins))

        if not candidates:
            raise RuntimeError('No GLB files found in locomotion archive')
        candidates.sort(key=lambda item: (item[0], item[1]), reverse=True)
        _, size, name, data, clips, meshes, skins = candidates[0]
        target = os.path.join(OUT, 'parkour_locomotion.glb')
        with open(target, 'wb') as file:
            file.write(data)
        write_manifest('assets3d/character/parkour_locomotion.glb', clips)
        print(f'Prepared CC0 locomotion library: {os.path.basename(name)}; {meshes} meshes, {skins} skins, {len(clips)} clips, {size / 1024 / 1024:.1f} MB')
        print('Locomotion sample: ' + ', '.join(clips[:24]))
except Exception as error:
    print(f'WARNING: CC0 locomotion fetch failed: {error}')
    write_manifest()
