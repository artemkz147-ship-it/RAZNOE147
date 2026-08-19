import json
import os
import shutil
import tempfile
import time
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(ROOT, 'public', 'assets3d', 'scenery')
MANIFEST = os.path.join(ROOT, 'src', 'game3d', 'scenery.json')

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


try:
    with tempfile.TemporaryDirectory(prefix='drop-kaykit-city-') as temp:
        print('Fetching original textured KayKit City Builder Bits CC0 buildings...')
        texture_name = 'citybits_texture.png'
        download(f'{BASE}/{texture_name}', os.path.join(OUT, texture_name))

        for old in os.listdir(OUT):
            if old.startswith(('kenney_city_', 'kaykit_city_')) and old.endswith(('.glb', '.gltf', '.bin')):
                os.remove(os.path.join(OUT, old))

        manifest = []
        for index, stem in enumerate(MODEL_STEMS, start=1):
            source_gltf = os.path.join(temp, f'{stem}.gltf')
            source_bin = os.path.join(temp, f'{stem}.bin')
            download(f'{BASE}/{stem}.gltf', source_gltf)
            download(f'{BASE}/{stem}.bin', source_bin)

            with open(source_gltf, 'r', encoding='utf-8') as file:
                doc = json.load(file)

            prefix = f'kaykit_city_{index:02d}'
            bin_name = f'{prefix}.bin'
            for buffer in doc.get('buffers', []):
                if buffer.get('uri') == f'{stem}.bin':
                    buffer['uri'] = bin_name
            for image in doc.get('images', []):
                if image.get('uri'):
                    image['uri'] = texture_name

            gltf_name = f'{prefix}.gltf'
            gltf_target = os.path.join(OUT, gltf_name)
            with open(gltf_target, 'w', encoding='utf-8') as file:
                json.dump(doc, file, ensure_ascii=False, separators=(',', ':'))
            shutil.copy2(source_bin, os.path.join(OUT, bin_name))

            manifest.append(f'assets3d/scenery/{gltf_name}')
            print(f'  {gltf_name} + {bin_name} <- {stem}')

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
        print(f'Prepared {len(manifest)} original-textured local KayKit CC0 city buildings.')
except Exception as error:
    print(f'ERROR: pinned KayKit city fetch failed: {error}')
    write_manifest([])
    raise
