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

# Small pinned GitHub mirror of the original Kenney CC0 assets. Avoid downloading
# the whole OpenGameArt archive on every CI run.
COMMIT = '697e73f478286d0c55d6caf3df4db421a625137c'
BASE = f'https://raw.githubusercontent.com/ruiguitos/horde-breaker/{COMMIT}/assets/models/kenney_city_commercial'
MODELS = [
    'building-skyscraper-a.glb',
    'building-skyscraper-b.glb',
    'building-skyscraper-c.glb',
    'building-skyscraper-d.glb',
    'building-skyscraper-e.glb',
    'building-a.glb',
    'building-b.glb',
    'building-c.glb',
]

os.makedirs(OUT, exist_ok=True)


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


def write_manifest(paths):
    with open(MANIFEST, 'w', encoding='utf-8') as file:
        json.dump(paths, file, ensure_ascii=False, indent=2)


def convert(path: str, out_path: str):
    # Source GLBs use a shared sibling texture. Load them beside that texture and
    # export a single self-contained GLB for the Yandex package.
    scene = trimesh.load(path, force='scene', process=False)
    scene.export(out_path, file_type='glb')


try:
    with tempfile.TemporaryDirectory(prefix='drop-city-github-') as temp:
        textures = os.path.join(temp, 'Textures')
        os.makedirs(textures, exist_ok=True)
        print('Fetching pinned Kenney City CC0 models from GitHub...')
        download(f'{BASE}/Textures/colormap.png', os.path.join(textures, 'colormap.png'))

        for old in os.listdir(OUT):
            if old.startswith('kenney_city_') and old.endswith('.glb'):
                os.remove(os.path.join(OUT, old))

        manifest = []
        for index, name in enumerate(MODELS, start=1):
            source = os.path.join(temp, name)
            download(f'{BASE}/{name}', source)
            file_name = f'kenney_city_{index:02d}.glb'
            target = os.path.join(OUT, file_name)
            convert(source, target)
            manifest.append(f'assets3d/scenery/{file_name}')
            print(f'  {file_name} <- {name}')

        if len(manifest) < 4:
            raise RuntimeError(f'Only {len(manifest)} city models prepared')
        write_manifest(manifest)
        with open(os.path.join(OUT, 'LICENSE_CC0.txt'), 'w', encoding='utf-8') as file:
            file.write(
                'Kenney City Kit (Commercial) 2.1\n'
                'Creator: Kenney\n'
                'License: Creative Commons Zero 1.0 (CC0)\n'
                'Source: https://kenney.nl/assets/city-kit-commercial\n'
                f'Pinned build mirror commit: {COMMIT}\n'
            )
        print(f'Prepared {len(manifest)} self-contained CC0 city models.')
except Exception as error:
    print(f'ERROR: pinned CC0 city fetch failed: {error}')
    write_manifest([])
    raise
