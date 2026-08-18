import json
import os
import shutil
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(ROOT, 'public', 'assets3d', 'character')
MANIFEST = os.path.join(ROOT, 'src', 'game3d', 'avatar.json')

# Web-ready derivative of Quaternius Universal Base Characters (CC0).
# The source repository records exact provenance and keeps the derived GLB under CC0.
URL = (
    'https://raw.githubusercontent.com/Seyamalam/blood-league-kickoff/'
    'aa02a4e6d8337a0604d2da131bcbbeb1f01badf0/'
    'public/assets/vendor/quaternius/night-striker.glb'
)

os.makedirs(OUT, exist_ok=True)


def write_manifest(asset=None):
    with open(MANIFEST, 'w', encoding='utf-8') as file:
        json.dump({'asset': asset, 'clips': []}, file, ensure_ascii=False, indent=2)


try:
    target = os.path.join(OUT, 'parkour_performer.glb')
    request = urllib.request.Request(
        URL,
        headers={'User-Agent': 'Mozilla/5.0 DropFlowBuild/2.1'},
    )
    print('Downloading CC0 Universal Base Character web GLB...')
    with urllib.request.urlopen(request, timeout=60) as response, open(target, 'wb') as output:
        shutil.copyfileobj(response, output)

    size = os.path.getsize(target)
    if size < 100_000:
        raise RuntimeError(f'Character GLB unexpectedly small: {size} bytes')

    write_manifest('assets3d/character/parkour_performer.glb')
    with open(os.path.join(OUT, 'LICENSE_CC0.txt'), 'w', encoding='utf-8') as file:
        file.write(
            'Universal Base Characters\n'
            'Creator: Quaternius\n'
            'License: Creative Commons Zero 1.0 (CC0)\n'
            'Source: https://quaternius.com/packs/universalbasecharacters.html\n'
            'Web GLB derivative: https://github.com/Seyamalam/blood-league-kickoff\n'
            'Derivative name: night-striker.glb (Superhero_Male_FullBody)\n'
        )
    print(f'Prepared CC0 human parkour character: {size / 1024 / 1024:.1f} MB')
except Exception as error:
    print(f'WARNING: CC0 human character fetch failed: {error}')
    write_manifest()
