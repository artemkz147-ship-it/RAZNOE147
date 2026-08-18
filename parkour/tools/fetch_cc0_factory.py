import json
import os
import shutil
import tempfile
import urllib.request
import zipfile

import trimesh

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(ROOT, 'public', 'assets3d', 'factory')
MANIFEST = os.path.join(ROOT, 'src', 'drop', 'factory.json')
URL = 'https://opengameart.org/sites/default/files/kenney_factory-kit_3.0.zip'

os.makedirs(OUT, exist_ok=True)
os.makedirs(os.path.dirname(MANIFEST), exist_ok=True)

GROUP_WORDS = {
    'platform': ('platform', 'walkway', 'conveyor', 'belt', 'floor', 'bridge', 'stairs'),
    'pole': ('pipe', 'support', 'pillar', 'column', 'chimney', 'tower', 'tank', 'silo'),
    'unit': ('machine', 'crate', 'container', 'barrel', 'vent', 'box', 'generator', 'cabinet'),
}


def score(path: str):
    name = os.path.basename(path).lower()
    wanted = sum(6 for words in GROUP_WORDS.values() for word in words if word in name)
    bad = sum(5 for word in ('character', 'icon', 'preview', 'instruction') if word in name)
    return wanted - bad


def convert(path: str, out_path: str):
    if path.lower().endswith('.glb'):
        shutil.copyfile(path, out_path)
        return
    scene = trimesh.load(path, force='scene', process=False)
    scene.export(out_path)


def write_manifest(data):
    with open(MANIFEST, 'w', encoding='utf-8') as file:
        json.dump(data, file, ensure_ascii=False, indent=2)


try:
    with tempfile.TemporaryDirectory(prefix='drop-factory-') as temp:
        archive = os.path.join(temp, 'factory.zip')
        request = urllib.request.Request(
            URL,
            headers={
                'User-Agent': 'Mozilla/5.0 DropParkourBuild/1.0',
                'Referer': 'https://opengameart.org/content/factory-kit',
            },
        )
        print('Downloading Kenney Factory Kit CC0...')
        with urllib.request.urlopen(request, timeout=45) as response, open(archive, 'wb') as output:
            shutil.copyfileobj(response, output)
        extract_dir = os.path.join(temp, 'factory')
        with zipfile.ZipFile(archive) as package:
            package.extractall(extract_dir)

        candidates = []
        seen = set()
        for base, _, files in os.walk(extract_dir):
            for file in files:
                path = os.path.join(base, file)
                ext = os.path.splitext(file)[1].lower()
                if ext not in ('.glb', '.gltf', '.obj'):
                    continue
                stem = os.path.splitext(file)[0].lower().replace('_', '').replace('-', '').replace(' ', '')
                if stem in seen:
                    continue
                seen.add(stem)
                candidates.append(path)
        candidates.sort(key=lambda path: (-score(path), path.lower()))
        candidates = candidates[:32]
        if not candidates:
            raise RuntimeError('No compatible factory models found')

        for old in os.listdir(OUT):
            if old.startswith('kenney_factory_') and old.endswith('.glb'):
                os.remove(os.path.join(OUT, old))

        manifest = {'platform': [], 'pole': [], 'unit': [], 'all': []}
        for index, source in enumerate(candidates, start=1):
            name = os.path.basename(source).lower()
            file_name = f'kenney_factory_{index:02d}.glb'
            target = os.path.join(OUT, file_name)
            try:
                convert(source, target)
            except Exception as error:
                print(f'  skipped {source}: {error}')
                continue
            rel = f'assets3d/factory/{file_name}'
            manifest['all'].append(rel)
            assigned = False
            for group, words in GROUP_WORDS.items():
                if any(word in name for word in words):
                    manifest[group].append(rel)
                    assigned = True
            if not assigned:
                manifest['unit'].append(rel)
            print(f'  {file_name} <- {os.path.basename(source)}')

        for group in ('platform', 'pole', 'unit'):
            if not manifest[group]:
                manifest[group] = manifest['all'][: min(8, len(manifest['all']))]
        if len(manifest['all']) < 6:
            raise RuntimeError(f'Only {len(manifest["all"])} factory models converted')

        write_manifest(manifest)
        with open(os.path.join(OUT, 'LICENSE_CC0.txt'), 'w', encoding='utf-8') as file:
            file.write(
                'Kenney Factory Kit 3.0\n'
                'Creator: Kenney\n'
                'License: Creative Commons Zero 1.0 (CC0)\n'
                'Source: https://kenney.nl/assets/factory-kit\n'
                'Redistribution source used by build: OpenGameArt.org\n'
            )
        print(f'Prepared {len(manifest["all"])} CC0 factory models for descending parkour.')
except Exception as error:
    print(f'WARNING: CC0 factory fetch failed: {error}')
    if not os.path.exists(MANIFEST):
        write_manifest({'platform': [], 'pole': [], 'unit': [], 'all': []})
