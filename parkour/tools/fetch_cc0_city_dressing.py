import json
import os
import shutil
import tempfile
import time
import urllib.request

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
            request = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 DropFlowBuild/2.4'})
            with urllib.request.urlopen(request, timeout=25) as response, open(target, 'wb') as output:
                shutil.copyfileobj(response, output)
            return
        except Exception as exc:
            error = exc
            if attempt + 1 < attempts:
                time.sleep(1.2 * (attempt + 1))
    raise RuntimeError(f'Failed after {attempts} attempts: {url}: {error}')


with tempfile.TemporaryDirectory(prefix='drop-citybits-') as temp:
    print('Fetching original textured KayKit City Builder Bits street assets (CC0)...')
    texture_name = 'citybits_texture.png'
    download(f'{BASE}/{texture_name}', os.path.join(OUT, texture_name))
    manifest = {key: [] for key in GROUPS}

    for old in os.listdir(OUT):
        if old.startswith('kaykit_') and old.endswith(('.glb', '.gltf', '.bin')):
            os.remove(os.path.join(OUT, old))

    for group, names in GROUPS.items():
        for stem in names:
            source_gltf = os.path.join(temp, f'{stem}.gltf')
            source_bin = os.path.join(temp, f'{stem}.bin')
            download(f'{BASE}/{stem}.gltf', source_gltf)
            download(f'{BASE}/{stem}.bin', source_bin)

            with open(source_gltf, 'r', encoding='utf-8') as file:
                doc = json.load(file)

            prefix = f'kaykit_{stem}'
            bin_name = f'{prefix}.bin'
            for buffer in doc.get('buffers', []):
                if buffer.get('uri') == f'{stem}.bin':
                    buffer['uri'] = bin_name
            for image in doc.get('images', []):
                if image.get('uri'):
                    image['uri'] = texture_name

            gltf_name = f'{prefix}.gltf'
            with open(os.path.join(OUT, gltf_name), 'w', encoding='utf-8') as file:
                json.dump(doc, file, ensure_ascii=False, separators=(',', ':'))
            shutil.copy2(source_bin, os.path.join(OUT, bin_name))
            manifest[group].append(f'assets3d/dressing/{gltf_name}')
            print(f'  {gltf_name} + {bin_name}')

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

    print('Prepared original-textured KayKit street/rooftop assets.')
