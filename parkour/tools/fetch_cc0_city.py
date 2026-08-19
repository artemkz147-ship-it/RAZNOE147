import json
import os
import shutil
import tempfile
import time
import urllib.request

import trimesh

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(ROOT, 'public', 'assets3d', 'scenery')
MANIFEST = os.path.join(ROOT, 'src', 'game3d', 'scenery.json')

# Pinned mirror of KayKit City Builder Bits. These buildings use the same
# citybits_texture.png as the street/road dressing, so the whole city shares one
# coherent authored art style instead of the old washed-out white Kenney mix.
COMMIT = '697e73f478286d0c55d6caf3df4db421a625137c'
BASE = f'https://raw.githubusercontent.com/ruiguitos/horde-breaker/{COMMIT}/assets/models/kaykit_city_bits'
MODEL_STEMS = [
    'building_A',
    'building_B',
    'building_C',
    'building_D',
    'building_E',
    'building_F',
    'building_G',
    'building_H',
]

os.makedirs(OUT, exist_ok=True)


def download(url: str, target: str, attempts=4):
    error = None
    for attempt in range(attempts):
        try:
            request = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 DropFlowBuild/2.4'})
            with urllib.request.urlopen(request, timeout=25) as response, open(target, 'wb') as output:
                shutil.copyfileobj(response, output)
            return
        except Exception as exc:
            error = exc
            if attempt + 1 < attempts:
                time.sleep(1.2 * (attempt + 1))
    raise RuntimeError(f'Failed after {attempts} attempts: {url}: {error}')


def write_manifest(paths):
    with open(MANIFEST, 'w', encoding='utf-8') as file:
        json.dump(paths, file, ensure_ascii=False, indent=2)
        file.write('\n')


def convert(path: str, out_path: str):
    scene = trimesh.load(path, force='scene', process=False)
    scene.export(out_path, file_type='glb')


try:
    with tempfile.TemporaryDirectory(prefix='drop-kaykit-city-') as temp:
        print('Fetching pinned KayKit City Builder Bits CC0 buildings...')
        download(f'{BASE}/citybits_texture.png', os.path.join(temp, 'citybits_texture.png'))

        for old in os.listdir(OUT):
            if (old.startswith('kenney_city_') or old.startswith('kaykit_city_')) and old.endswith('.glb'):
                os.remove(os.path.join(OUT, old))

        manifest = []
        for index, stem in enumerate(MODEL_STEMS, start=1):
            gltf = os.path.join(temp, f'{stem}.gltf')
            binary = os.path.join(temp, f'{stem}.bin')
            download(f'{BASE}/{stem}.gltf', gltf)
            download(f'{BASE}/{stem}.bin', binary)
            file_name = f'kaykit_city_{index:02d}.glb'
            target = os.path.join(OUT, file_name)
            convert(gltf, target)
            manifest.append(f'assets3d/scenery/{file_name}')
            print(f'  {file_name} <- {stem}.gltf')

        if len(manifest) < 6:
            raise RuntimeError(f'Only {len(manifest)} KayKit city models prepared')
        write_manifest(manifest)
        with open(os.path.join(OUT, 'LICENSE_CC0.txt'), 'w', encoding='utf-8') as file:
            file.write(
                'KayKit : City Builder Bits (1.0)\n'
                'Creator: Kay Lousberg\n'
                'License: Creative Commons Zero 1.0 (CC0)\n'
                'Free for personal, educational and commercial projects.\n'
                f'Pinned build mirror commit: {COMMIT}\n'
            )
        print(f'Prepared {len(manifest)} textured self-contained KayKit CC0 city buildings.')
except Exception as error:
    print(f'ERROR: pinned KayKit city fetch failed: {error}')
    write_manifest([])
    raise
