#!/usr/bin/env python3
import hashlib
import json
import pathlib
import subprocess
import urllib.parse
import urllib.request

UA = 'DinoEncyclopedia3D/2.0 (+https://github.com/artemkz147-ship-it/RAZNOE147)'
API = 'https://api.polyhaven.com/files/{}'
ROOT = pathlib.Path('assets/environments/hell_creek')
MODELS = ['fern_02', 'dead_tree_trunk', 'tree_stump_01', 'rock_moss_set_01', 'shrub_03']


def get_json(url: str):
    req = urllib.request.Request(url, headers={'User-Agent': UA, 'Accept': 'application/json'})
    with urllib.request.urlopen(req, timeout=90) as response:
        return json.load(response)


def download(url: str, dest: pathlib.Path, md5: str | None = None):
    dest.parent.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(url, headers={'User-Agent': UA})
    with urllib.request.urlopen(req, timeout=180) as response, open(dest, 'wb') as output:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            output.write(chunk)
    if md5:
        digest = hashlib.md5(dest.read_bytes()).hexdigest()
        if digest.lower() != md5.lower():
            raise RuntimeError(f'MD5 mismatch for {dest}: {digest} != {md5}')
    print(f'downloaded {dest} ({dest.stat().st_size} bytes)')


def walk_records(obj, path=()):
    if isinstance(obj, dict):
        if isinstance(obj.get('url'), str):
            yield path, obj
        for key, value in obj.items():
            if key not in {'url', 'md5', 'size'}:
                yield from walk_records(value, path + (str(key),))
    elif isinstance(obj, list):
        for i, value in enumerate(obj):
            yield from walk_records(value, path + (str(i),))


def choose_record(data, *, suffix=None, contains=(), preferred_tokens=()):
    candidates = []
    for path, record in walk_records(data):
        url = record['url']
        low = url.lower()
        if suffix and not low.split('?')[0].endswith(suffix.lower()):
            continue
        if any(token.lower() not in low for token in contains):
            continue
        score = 0
        searchable = '/'.join(path).lower() + ' ' + low
        for i, token in enumerate(preferred_tokens):
            if token.lower() in searchable:
                score += (len(preferred_tokens) - i) * 100
        score -= int(record.get('size', 0)) // 1_000_000
        candidates.append((score, path, record))
    if not candidates:
        raise RuntimeError(f'No matching Poly Haven file suffix={suffix} contains={contains}')
    candidates.sort(key=lambda item: item[0], reverse=True)
    return candidates[0][1], candidates[0][2]


def download_model(slug: str):
    data = get_json(API.format(slug))
    _, main = choose_record(data, suffix='.gltf', preferred_tokens=('1k', '2k', 'gltf'))
    temp = ROOT / '_download' / slug
    temp.mkdir(parents=True, exist_ok=True)
    main_url = main['url']
    main_name = pathlib.Path(urllib.request.url2pathname(urllib.parse.urlparse(main_url).path)).name
    main_dest = temp / main_name
    download(main_url, main_dest, main.get('md5'))

    includes = main.get('include')
    if includes:
        for _, record in walk_records(includes):
            url = record['url']
            basename = pathlib.Path(urllib.request.url2pathname(urllib.parse.urlparse(url).path)).name
            download(url, temp / basename, record.get('md5'))
    else:
        for path, record in walk_records(data):
            url = record['url']
            low = url.lower()
            if url == main_url:
                continue
            if not any(low.split('?')[0].endswith(ext) for ext in ('.bin', '.jpg', '.jpeg', '.png', '.webp')):
                continue
            searchable = '/'.join(path).lower() + ' ' + low
            if '1k' not in searchable:
                continue
            basename = pathlib.Path(urllib.request.url2pathname(urllib.parse.urlparse(url).path)).name
            dest = temp / basename
            if not dest.exists():
                download(url, dest, record.get('md5'))

    out_dir = ROOT / 'models'
    out_dir.mkdir(parents=True, exist_ok=True)
    output = out_dir / f'{slug}.glb'
    subprocess.run([
        'npx', '--yes', 'gltfpack@1.2.0', '-i', str(main_dest), '-o', str(output),
        '-si', '0.52', '-kn', '-km', '-ke'
    ], check=True)
    if output.stat().st_size < 50_000:
        raise RuntimeError(f'Converted model is suspiciously small: {output}')
    print(f'packed {slug} -> {output} ({output.stat().st_size} bytes)')


def download_texture():
    data = get_json(API.format('mud_forest'))
    mapping = {
        'mud_forest_diff_1k.jpg': ('.jpg', ('diff',), ('1k', 'jpg')),
        'mud_forest_nor_gl_1k.jpg': ('.jpg', ('nor_gl',), ('1k', 'jpg')),
        'mud_forest_rough_1k.jpg': ('.jpg', ('rough',), ('1k', 'jpg')),
    }
    tex_dir = ROOT / 'textures'
    for name, (suffix, contains, preferred) in mapping.items():
        try:
            _, record = choose_record(data, suffix=suffix, contains=contains, preferred_tokens=preferred)
        except RuntimeError:
            _, record = choose_record(data, contains=contains, preferred_tokens=('1k',))
        download(record['url'], tex_dir / name, record.get('md5'))


def download_hdri():
    data = get_json(API.format('xanderklinge'))
    _, record = choose_record(data, suffix='.hdr', preferred_tokens=('2k', '1k', 'hdr'))
    download(record['url'], ROOT / 'xanderklinge_2k.hdr', record.get('md5'))


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    for slug in MODELS:
        download_model(slug)
    download_texture()
    download_hdri()
    print('POLYHAVEN_ASSETS_OK')


if __name__ == '__main__':
    main()
