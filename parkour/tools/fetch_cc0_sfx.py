import json
import os
import re
import shutil
import tempfile
import urllib.request
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(ROOT, 'public', 'assets3d', 'audio')
MANIFEST = os.path.join(ROOT, 'src', 'game3d', 'sfx.json')
URL = 'https://opengameart.org/sites/default/files/sfx_100_v2.zip'
AUDIO_EXT = ('.ogg', '.wav', '.mp3')

os.makedirs(OUT, exist_ok=True)


def safe_name(name: str):
    stem, ext = os.path.splitext(os.path.basename(name))
    stem = re.sub(r'[^a-zA-Z0-9_-]+', '_', stem).strip('_').lower()
    return f'{stem[:64]}{ext.lower()}'


def classify(name: str):
    low = name.lower()
    if any(word in low for word in ('footstep', 'foot_step', 'steps', 'step_')):
        return 'footsteps'
    if any(word in low for word in ('glass', 'wood_hit', 'metal_hit', 'stone_hit', 'impact', 'break')):
        return 'breaks'
    if any(word in low for word in ('land', 'thud', 'body', 'hit_')):
        return 'landings'
    if any(word in low for word in ('ambient', 'street', 'highway', 'construction', 'air_flow', 'airflow', 'wind')):
        return 'ambient'
    return None


def write_manifest(data):
    with open(MANIFEST, 'w', encoding='utf-8') as file:
        json.dump(data, file, ensure_ascii=False, indent=2)


manifest = {'footsteps': [], 'landings': [], 'breaks': [], 'ambient': []}
try:
    with tempfile.TemporaryDirectory(prefix='vertical-sfx-') as temp:
        archive = os.path.join(temp, 'sfx.zip')
        request = urllib.request.Request(
            URL,
            headers={
                'User-Agent': 'Mozilla/5.0 VerticalParkourBuild/1.0',
                'Referer': 'https://opengameart.org/content/100-cc0-sfx-2',
            },
        )
        print('Downloading 100 CC0 SFX #2...')
        with urllib.request.urlopen(request, timeout=45) as response, open(archive, 'wb') as output:
            shutil.copyfileobj(response, output)

        with zipfile.ZipFile(archive) as package:
            candidates = []
            for name in package.namelist():
                if not name.lower().endswith(AUDIO_EXT):
                    continue
                category = classify(name)
                if category:
                    candidates.append((category, name))

            limits = {'footsteps': 8, 'landings': 4, 'breaks': 6, 'ambient': 3}
            used_names = set()
            for category, name in candidates:
                if len(manifest[category]) >= limits[category]:
                    continue
                target_name = safe_name(name)
                if target_name in used_names:
                    continue
                used_names.add(target_name)
                target = os.path.join(OUT, target_name)
                with package.open(name) as source, open(target, 'wb') as output:
                    shutil.copyfileobj(source, output)
                manifest[category].append(f'assets3d/audio/{target_name}')

        if not manifest['footsteps']:
            raise RuntimeError('No footstep files detected in CC0 pack')
        write_manifest(manifest)
        with open(os.path.join(OUT, 'LICENSE_CC0.txt'), 'w', encoding='utf-8') as file:
            file.write(
                '100 CC0 SFX #2\n'
                'Creator: rubberduck\n'
                'License: Creative Commons Zero 1.0 (CC0)\n'
                'Source: https://opengameart.org/content/100-cc0-sfx-2\n'
            )
        print('Prepared CC0 SFX: ' + ', '.join(f'{key}={len(value)}' for key, value in manifest.items()))
except Exception as error:
    print(f'WARNING: CC0 SFX fetch failed: {error}')
    write_manifest(manifest)
