import hashlib
import os
import shutil
import time
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(ROOT, 'public', 'assets3d', 'environment')
os.makedirs(OUT, exist_ok=True)

# Joburg Central Sunset by Dimitrios Savva / Greg Zaal, Poly Haven, CC0.
# Rooftop/city skyline context fits the vertical parkour scene much better than a waterfront HDRI.
URL = 'https://dl.polyhaven.org/file/ph-assets/HDRIs/hdr/1k/sunset_jhbcentral_1k.hdr'
EXPECTED_MD5 = '97335a81ba615beb6f6ae0da707ecd75'
TARGET = os.path.join(OUT, 'rooftop_sunset_1k.hdr')


def download(attempts=4):
    error = None
    for attempt in range(attempts):
        try:
            request = urllib.request.Request(URL, headers={'User-Agent': 'DropFlowYandex/2.2'})
            with urllib.request.urlopen(request, timeout=35) as response, open(TARGET, 'wb') as output:
                shutil.copyfileobj(response, output)
            return
        except Exception as exc:
            error = exc
            if attempt + 1 < attempts:
                time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f'HDR download failed after {attempts} attempts: {error}')


try:
    print('Downloading CC0 Joburg rooftop skyline HDR environment...')
    download()
    size = os.path.getsize(TARGET)
    digest = hashlib.md5(open(TARGET, 'rb').read()).hexdigest()
    if size < 1_000_000:
        raise RuntimeError(f'HDR unexpectedly small: {size} bytes')
    if digest != EXPECTED_MD5:
        raise RuntimeError(f'HDR checksum mismatch: {digest}')
    with open(os.path.join(OUT, 'LICENSE_CC0.txt'), 'w', encoding='utf-8') as file:
        file.write(
            'Joburg Central Sunset HDRI\n'
            'Creators: Dimitrios Savva (Photography), Greg Zaal (Processing)\n'
            'License: Creative Commons Zero 1.0 (CC0)\n'
            'Source: https://polyhaven.com/a/sunset_jhbcentral\n'
            'Resolution used: 1K HDR\n'
        )
    print(f'Prepared rooftop city HDR environment: {size / 1024 / 1024:.1f} MB')
except Exception as error:
    print(f'ERROR: rooftop HDR environment fetch failed: {error}')
    try:
        os.remove(TARGET)
    except FileNotFoundError:
        pass
    raise
