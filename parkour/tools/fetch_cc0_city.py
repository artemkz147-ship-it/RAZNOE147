import json
import os
import shutil
import tempfile
import urllib.request
import zipfile

import trimesh

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(ROOT, 'public', 'assets3d', 'scenery')
MANIFEST = os.path.join(ROOT, 'src', 'game3d', 'scenery.json')
URL = 'https://opengameart.org/sites/default/files/kenney_city-kit-commercial_2.1.zip'

os.makedirs(OUT, exist_ok=True)


def score(path: str):
    name = os.path.basename(path).lower()
    good = ('building', 'skyscraper', 'shop', 'store', 'office', 'commercial', 'mall', 'tower', 'corner')
    bad = ('sign', 'awning', 'bench', 'trash', 'light', 'tree', 'road', 'instruction')
    value = sum(8 for word in good if word in name) - sum(7 for word in bad if word in name)
    if 'large' in name or 'high' in name: value += 3
    return value


def unique_candidates(root: str):
    found = []
    for base, _, files in os.walk(root):
        for file in files:
            ext = os.path.splitext(file)[1].lower()
            if ext in ('.glb', '.gltf', '.obj'):
                found.append(os.path.join(base, file))
    priority = {'.glb': 0, '.gltf': 1, '.obj': 2}
    found.sort(key=lambda path: (-score(path), priority.get(os.path.splitext(path)[1].lower(), 9), path.lower()))
    selected = []
    stems = set()
    for path in found:
        stem = os.path.splitext(os.path.basename(path))[0].lower()
        normalized = stem.replace('_', '').replace('-', '').replace(' ', '')
        if normalized in stems:
            continue
        stems.add(normalized)
        selected.append(path)
        if len(selected) >= 18:
            break
    return selected


def convert(path: str, out_path: str):
    if path.lower().endswith('.glb'):
        shutil.copyfile(path, out_path)
        return
    scene = trimesh.load(path, force='scene', process=False)
    scene.export(out_path)


def write_manifest(paths):
    with open(MANIFEST, 'w', encoding='utf-8') as file:
        json.dump(paths, file, ensure_ascii=False, indent=2)


try:
    with tempfile.TemporaryDirectory(prefix='vertical-cc0-') as temp:
        archive = os.path.join(temp, 'kenney-city.zip')
        request = urllib.request.Request(
            URL,
            headers={
                'User-Agent': 'Mozilla/5.0 VerticalParkourBuild/1.0',
                'Referer': 'https://opengameart.org/content/city-kit-commercial',
            },
        )
        print('Downloading Kenney City Kit Commercial CC0...')
        with urllib.request.urlopen(request, timeout=45) as response, open(archive, 'wb') as output:
            shutil.copyfileobj(response, output)
        extract_dir = os.path.join(temp, 'city')
        with zipfile.ZipFile(archive) as package:
            package.extractall(extract_dir)

        candidates = unique_candidates(extract_dir)
        if not candidates:
            raise RuntimeError('No compatible 3D files found in Kenney archive')

        for old in os.listdir(OUT):
            if old.startswith('kenney_city_') and old.endswith('.glb'):
                os.remove(os.path.join(OUT, old))

        manifest = []
        for index, source in enumerate(candidates, start=1):
            file_name = f'kenney_city_{index:02d}.glb'
            target = os.path.join(OUT, file_name)
            try:
                convert(source, target)
                manifest.append(f'assets3d/scenery/{file_name}')
                print(f'  {file_name} <- {os.path.basename(source)}')
            except Exception as error:
                print(f'  skipped {source}: {error}')

        if len(manifest) < 4:
            raise RuntimeError(f'Only {len(manifest)} city models converted')

        write_manifest(manifest)
        with open(os.path.join(OUT, 'LICENSE_CC0.txt'), 'w', encoding='utf-8') as license_file:
            license_file.write(
                'Kenney City Kit (Commercial) 2.1\n'
                'Creator: Kenney\n'
                'License: Creative Commons Zero 1.0 (CC0)\n'
                'Source: https://kenney.nl/assets/city-kit-commercial\n'
                'Redistribution source used by build: OpenGameArt.org\n'
            )
        print(f'Prepared {len(manifest)} CC0 city models for the game.')
except Exception as error:
    print(f'WARNING: CC0 city fetch failed: {error}')
    if not os.path.exists(MANIFEST):
        write_manifest([])
