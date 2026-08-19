import json
import os
import shutil
import tempfile
import time
import urllib.request

import numpy as np
import trimesh

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(ROOT, 'public', 'assets3d', 'scenery')
MANIFEST = os.path.join(ROOT, 'src', 'game3d', 'scenery.json')
DECK_MANIFEST = os.path.join(ROOT, 'src', 'drop', 'city_decks.json')

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
            request = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 DropFlowBuild/2.5'})
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


def write_deck_manifest(items):
    with open(DECK_MANIFEST, 'w', encoding='utf-8') as file:
        json.dump(items, file, ensure_ascii=False, indent=2)
        file.write('\n')


def measure_deck_ratio(gltf_path: str):
    """Find the dominant upper horizontal surface of a building.

    KayKit buildings often have parapets/chimneys above the surface the player
    visually stands on. Using the model AABB max as the gameplay roof height makes
    the character appear to float. We measure the largest upward-facing horizontal
    face cluster in the upper half of the authored mesh and align Rapier Y to it.
    """
    scene = trimesh.load(gltf_path, force='scene', process=False)
    mesh = scene.to_geometry()
    if mesh.faces is None or len(mesh.faces) == 0:
        return 0.78

    min_y = float(mesh.bounds[0][1])
    max_y = float(mesh.bounds[1][1])
    height = max_y - min_y
    if height <= 1e-5:
        return 0.78

    centers = np.asarray(mesh.triangles_center)
    normals = np.asarray(mesh.face_normals)
    areas = np.asarray(mesh.area_faces)
    relative_y = (centers[:, 1] - min_y) / height
    mask = (normals[:, 1] > 0.82) & (relative_y > 0.48)
    if not np.any(mask):
        return 0.78

    tolerance = max(height * 0.012, 0.004)
    buckets = {}
    for y, area, rel in zip(centers[mask, 1], areas[mask], relative_y[mask]):
        key = int(round((float(y) - min_y) / tolerance))
        bucket = buckets.setdefault(key, {'area': 0.0, 'weighted_y': 0.0, 'rel': float(rel)})
        bucket['area'] += float(area)
        bucket['weighted_y'] += float(y) * float(area)
        bucket['rel'] = max(bucket['rel'], float(rel))

    candidates = []
    for bucket in buckets.values():
        if bucket['area'] <= 1e-8:
            continue
        y = bucket['weighted_y'] / bucket['area']
        rel = (y - min_y) / height
        # Surface area is the main signal; a small height bonus breaks ties in
        # favour of an actual roof rather than a wide balcony one storey below.
        score = bucket['area'] * (0.82 + 0.18 * rel)
        candidates.append((score, bucket['area'], rel, y))

    if not candidates:
        return 0.78
    candidates.sort(reverse=True)
    _, area, ratio, y = candidates[0]
    ratio = float(np.clip(ratio, 0.50, 0.985))
    print(f'    detected walkable deck y={y:.3f}, ratio={ratio:.4f}, horizontal area={area:.3f}')
    return ratio


try:
    with tempfile.TemporaryDirectory(prefix='drop-kaykit-city-') as temp:
        print('Fetching original textured KayKit City Builder Bits CC0 buildings...')
        texture_name = 'citybits_texture.png'
        download(f'{BASE}/{texture_name}', os.path.join(OUT, texture_name))

        for old in os.listdir(OUT):
            if old.startswith(('kenney_city_', 'kaykit_city_')) and old.endswith(('.glb', '.gltf', '.bin')):
                os.remove(os.path.join(OUT, old))

        manifest = []
        deck_manifest = []
        for index, stem in enumerate(MODEL_STEMS, start=1):
            source_gltf = os.path.join(temp, f'{stem}.gltf')
            source_bin = os.path.join(temp, f'{stem}.bin')
            source_texture = os.path.join(temp, texture_name)
            download(f'{BASE}/{stem}.gltf', source_gltf)
            download(f'{BASE}/{stem}.bin', source_bin)
            shutil.copy2(os.path.join(OUT, texture_name), source_texture)

            deck_ratio = measure_deck_ratio(source_gltf)

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

            asset = f'assets3d/scenery/{gltf_name}'
            manifest.append(asset)
            deck_manifest.append({'asset': asset, 'deckRatio': round(deck_ratio, 6)})
            print(f'  {gltf_name} + {bin_name} <- {stem}; deckRatio={deck_ratio:.4f}')

        if len(manifest) < 6:
            raise RuntimeError(f'Only {len(manifest)} KayKit city models prepared')
        write_manifest(manifest)
        write_deck_manifest(deck_manifest)
        with open(os.path.join(OUT, 'LICENSE_CC0.txt'), 'w', encoding='utf-8') as file:
            file.write(
                'KayKit : City Builder Bits (1.0)\n'
                'Creator: Kay Lousberg\n'
                'License: Creative Commons Zero 1.0 (CC0)\n'
                'Free for personal, educational and commercial projects.\n'
                f'Pinned build mirror commit: {COMMIT}\n'
            )
        print(f'Prepared {len(manifest)} textured KayKit buildings with measured roof decks.')
except Exception as error:
    print(f'ERROR: pinned KayKit city fetch failed: {error}')
    write_manifest([])
    write_deck_manifest([])
    raise
