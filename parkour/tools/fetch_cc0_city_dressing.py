import json
import os
import shutil
import tempfile
import time
import urllib.request

import trimesh

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(ROOT, 'public', 'assets3d', 'dressing')
MANIFEST = os.path.join(ROOT, 'src', 'drop', 'dressing.json')
COMMIT = '697e73f478286d0c55d6caf3df4db421a625137c'
BASE = f'https://raw.githubusercontent.com/ruiguitos/horde-breaker/{COMMIT}/assets/models/kaykit_city_bits'

GROUPS = {
    'roads': ['road_straight', 'road_corner', 'road_junction', 'road_tsplit'],
    'street': ['streetlight', 'dumpster', 'firehydrant'],
    'rooftop': ['watertower'],
}

os.makedirs(OUT, exist_ok=True)


def download(url: str, target: str, attempts=4):
    error = None
    for attempt in range(attempts):
        try:
            request = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 DropFlowBuild/2.3'})
            with urllib.request.urlopen(request, timeout=25) as response, open(target, 'wb') as output:
                shutil.copyfileobj(response, output)
            return
        except Exception as exc:
            error = exc
            if attempt + 1 < attempts:
                time.sleep(1.2 * (attempt + 1))
    raise RuntimeError(f'Failed after {attempts} attempts: {url}: {error}')


def convert(source: str, target: str):
    scene = trimesh.load(source, force='scene', process=False)
    scene.export(target, file_type='glb')


with tempfile.TemporaryDirectory(prefix='drop-citybits-') as temp:
    print('Fetching pinned KayKit City Builder Bits (CC0)...')
    download(f'{BASE}/citybits_texture.png', os.path.join(temp, 'citybits_texture.png'))
    manifest = {key: [] for key in GROUPS}

    for old in os.listdir(OUT):
        if old.startswith('kaykit_') and old.endswith('.glb'):
            os.remove(os.path.join(OUT, old))

    for group, names in GROUPS.items():
        for stem in names:
            gltf = os.path.join(temp, f'{stem}.gltf')
            binary = os.path.join(temp, f'{stem}.bin')
            download(f'{BASE}/{stem}.gltf', gltf)
            download(f'{BASE}/{stem}.bin', binary)
            filename = f'kaykit_{stem}.glb'
            target = os.path.join(OUT, filename)
            convert(gltf, target)
            manifest[group].append(f'assets3d/dressing/{filename}')
            print(f'  {filename}')

    with open(MANIFEST, 'w', encoding='utf-8') as file:
        json.dump(manifest, file, ensure_ascii=False, indent=2)
        file.write('\n')

    with open(os.path.join(OUT, 'LICENSE_CC0.txt'), 'w', encoding='utf-8') as file:
        file.write(
            'KayKit : City Builder Bits (1.0)\n'
            'Creator: Kay Lousberg\n'
            'License: Creative Commons Zero 1.0 (CC0)\n'
            'Source mirror pinned at commit: ' + COMMIT + '\n'
        )

    print('Prepared KayKit street/rooftop dressing GLBs.')
