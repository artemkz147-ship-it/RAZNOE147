import os
import shutil
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(ROOT, 'public', 'assets3d', 'environment')
os.makedirs(OUT, exist_ok=True)

# Venice Sunset HDRI by Greg Zaal / Poly Haven, CC0.
# Three.js redistributes the 1K HDR in its examples; pin to r158 for a stable build URL.
URL = (
    'https://raw.githubusercontent.com/mrdoob/three.js/r158/'
    'examples/textures/equirectangular/venice_sunset_1k.hdr'
)
TARGET = os.path.join(OUT, 'rooftop_sunset_1k.hdr')

try:
    request = urllib.request.Request(URL, headers={'User-Agent': 'Mozilla/5.0 DropFlowBuild/2.1'})
    print('Downloading CC0 rooftop HDR environment...')
    with urllib.request.urlopen(request, timeout=60) as response, open(TARGET, 'wb') as output:
        shutil.copyfileobj(response, output)
    size = os.path.getsize(TARGET)
    if size < 500_000:
        raise RuntimeError(f'HDR unexpectedly small: {size} bytes')
    with open(os.path.join(OUT, 'LICENSE_CC0.txt'), 'w', encoding='utf-8') as file:
        file.write(
            'Venice Sunset HDRI\n'
            'Creator: Greg Zaal\n'
            'License: Creative Commons Zero 1.0 (CC0)\n'
            'Source: https://polyhaven.com/a/venice_sunset\n'
            '1K redistribution used by build: Three.js examples\n'
        )
    print(f'Prepared rooftop HDR environment: {size / 1024 / 1024:.1f} MB')
except Exception as error:
    print(f'WARNING: rooftop HDR environment fetch failed: {error}')
    try:
        os.remove(TARGET)
    except FileNotFoundError:
        pass
