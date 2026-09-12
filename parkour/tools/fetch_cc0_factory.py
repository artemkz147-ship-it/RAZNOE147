import json
import os
import shutil
import tempfile
import time
import urllib.request

import trimesh

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(ROOT, 'public', 'assets3d', 'factory')
MANIFEST = os.path.join(ROOT, 'src', 'drop', 'factory.json')

COMMIT = '697e73f478286d0c55d6caf3df4db421a625137c'
BASE = f'https://raw.githubusercontent.com/ruiguitos/horde-breaker/{COMMIT}/assets/models/kenney_factory_kit'
MODEL_GROUPS = {
    'pole': [
        'machine-connection-pipe.glb',
        'button-floor-round.glb',
    ],
    'unit': [
        'box-large.glb',
        'box-long.glb',
        'box-wide.glb',
    ],
    'platform': [
        'catwalk-stairs.glb',
        'conveyor-bars.glb',
        'conveyor-bars-sides.glb',
        'conveyor-long-sides.glb',
    ],
}

os.makedirs(OUT, exist_ok=True)
os.makedirs(os.path.dirname(MANIFEST), exist_ok=True)


def download(url: str, target: str, attempts=4):
    error = None
    for attempt in range(attempts):
        try:
            request = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 DropFlowBuild/2.2'})
            with urllib.request.urlopen(request, timeout=25) as response, open(target, 'wb') as output:
                shutil.copyfileobj(response, output)
            return
        except Exception as exc:
            error = exc
            if attempt + 1 < attempts:
                time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f'Failed after {attempts} attempts: {url}: {error}')


def write_manifest(data):
    with open(MANIFEST, 'w', encoding='utf-8') as file:
        json.dump(data, file, ensure_ascii=False, indent=2)


def convert(path: str, out_path: str):
    scene = trimesh.load(path, force='scene', process=False)
    scene.export(out_path, file_type='glb')


try:
    with tempfile.TemporaryDirectory(prefix='drop-factory-github-') as temp:
        textures = os.path.join(temp, 'Textures')
        os.makedirs(textures, exist_ok=True)
        print('Fetching pinned Kenney Factory CC0 models from GitHub...')
        download(f'{BASE}/Textures/colormap.png', os.path.join(textures, 'colormap.png'))

        for old in os.listdir(OUT):
            if old.startswith('kenney_factory_') and old.endswith('.glb'):
                os.remove(os.path.join(OUT, old))

        manifest = {'platform': [], 'pole': [], 'unit': [], 'all': []}
        index = 1
        for group, names in MODEL_GROUPS.items():
            for name in names:
                source = os.path.join(temp, name)
                download(f'{BASE}/{name}', source)
                file_name = f'kenney_factory_{index:02d}.glb'
                target = os.path.join(OUT, file_name)
                convert(source, target)
                rel = f'assets3d/factory/{file_name}'
                manifest[group].append(rel)
                manifest['all'].append(rel)
                print(f'  {file_name} [{group}] <- {name}')
                index += 1

        if len(manifest['all']) < 6:
            raise RuntimeError(f'Only {len(manifest["all"])} factory models prepared')
        write_manifest(manifest)
        with open(os.path.join(OUT, 'LICENSE_CC0.txt'), 'w', encoding='utf-8') as file:
            file.write(
                'Kenney Factory Kit 3.0\n'
                'Creator: Kenney\n'
                'License: Creative Commons Zero 1.0 (CC0)\n'
                'Source: https://kenney.nl/assets/factory-kit\n'
                f'Pinned build mirror commit: {COMMIT}\n'
            )
        print(f'Prepared {len(manifest["all"])} self-contained CC0 factory models.')
except Exception as error:
    print(f'ERROR: pinned CC0 factory fetch failed: {error}')
    write_manifest({'platform': [], 'pole': [], 'unit': [], 'all': []})
    raise
