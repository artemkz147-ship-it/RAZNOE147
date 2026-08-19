import json
import os
import shutil

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
PUBLIC = os.path.join(ROOT, 'public')
SOURCE = os.path.join(PUBLIC, 'assets3d')
VERSION_DIR = 'assets3d_df6_20260818'
BUILD_ID = 'DF6-20260818-1903'
TARGET = os.path.join(PUBLIC, VERSION_DIR)

MANIFESTS = [
    os.path.join(ROOT, 'src', 'game3d', 'avatar.json'),
    os.path.join(ROOT, 'src', 'game3d', 'locomotion.json'),
    os.path.join(ROOT, 'src', 'game3d', 'scenery.json'),
    os.path.join(ROOT, 'src', 'game3d', 'sfx.json'),
    os.path.join(ROOT, 'src', 'drop', 'factory.json'),
    os.path.join(ROOT, 'src', 'drop', 'dressing.json'),
    os.path.join(ROOT, 'src', 'drop', 'tricks.json'),
]

TEXT_FILES = []
for base, _, files in os.walk(os.path.join(ROOT, 'src')):
    for name in files:
        if os.path.splitext(name)[1].lower() in ('.ts', '.tsx', '.js', '.mjs', '.css'):
            TEXT_FILES.append(os.path.join(base, name))
TEXT_FILES.extend([
    os.path.join(ROOT, 'index.html'),
])


def rewrite(value):
    if isinstance(value, str):
        return value.replace('assets3d/', f'{VERSION_DIR}/')
    if isinstance(value, list):
        return [rewrite(item) for item in value]
    if isinstance(value, dict):
        return {key: rewrite(item) for key, item in value.items()}
    return value


if not os.path.isdir(SOURCE):
    raise RuntimeError(f'Missing generated asset directory: {SOURCE}')

if os.path.exists(TARGET):
    shutil.rmtree(TARGET)
shutil.move(SOURCE, TARGET)

for path in MANIFESTS:
    if not os.path.exists(path):
        continue
    with open(path, 'r', encoding='utf-8') as file:
        data = json.load(file)
    with open(path, 'w', encoding='utf-8') as file:
        json.dump(rewrite(data), file, ensure_ascii=False, indent=2)
        file.write('\n')

for path in TEXT_FILES:
    if not os.path.exists(path):
        continue
    with open(path, 'r', encoding='utf-8') as file:
        text = file.read()
    updated = text.replace('assets3d/', f'{VERSION_DIR}/')
    if path.endswith('index.html') and 'id="buildStamp"' not in updated:
        stamp = (
            f'    <div id="buildStamp" data-build="{BUILD_ID}" '
            'style="position:fixed;left:8px;bottom:6px;z-index:9999;pointer-events:none;'
            'font:700 8px/1.1 Arial,sans-serif;letter-spacing:.12em;color:rgba(255,255,255,.55);'
            'text-shadow:0 1px 3px rgba(0,0,0,.8)">BUILD DF6</div>\n'
        )
        updated = updated.replace('  </body>', stamp + '  </body>')
    if updated != text:
        with open(path, 'w', encoding='utf-8') as file:
            file.write(updated)

with open(os.path.join(PUBLIC, 'build-id.txt'), 'w', encoding='utf-8') as file:
    file.write(f'DROP FLOW BUILD {BUILD_ID}\n')

print(f'Cache-busted game assets: assets3d/ -> {VERSION_DIR}/')
print(f'Build stamp: {BUILD_ID}')
