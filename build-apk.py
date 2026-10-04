#!/usr/bin/env python3
"""Build a debug APK with JDK 17 and Android SDK installed. Downloads Gradle 8.9."""
from pathlib import Path
import hashlib, os, shutil, subprocess, sys, urllib.request, zipfile
ROOT = Path(__file__).resolve().parent
VERSION = '8.9'

def main():
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    local = ROOT / 'local.properties'
    if not sdk and not local.is_file():
        raise RuntimeError('Install Android SDK Platform 35 / Build Tools 34.0.0 and set ANDROID_HOME (or add sdk.dir to local.properties). See README.md.')
    tool = ROOT / '.build-tools' / ('gradle-' + VERSION)
    binary = tool / 'bin' / ('gradle.bat' if os.name == 'nt' else 'gradle')
    if not binary.is_file():
        tool.parent.mkdir(parents=True, exist_ok=True)
        url = 'https://services.gradle.org/distributions/gradle-' + VERSION + '-bin.zip'
        archive = tool.parent / 'gradle.zip'
        print('Downloading Gradle ' + VERSION + '…', flush=True)
        expected = urllib.request.urlopen(url + '.sha256', timeout=60).read().decode().strip().split()[0]
        urllib.request.urlretrieve(url, archive)
        digest = hashlib.sha256()
        with archive.open('rb') as stream:
            for block in iter(lambda: stream.read(1024*1024), b''): digest.update(block)
        if digest.hexdigest() != expected:
            archive.unlink()
            raise RuntimeError('Gradle checksum mismatch')
        with zipfile.ZipFile(archive) as bundle:
            for item in bundle.infolist():
                target = (tool.parent / item.filename).resolve()
                if not target.is_relative_to(tool.parent.resolve()):
                    raise RuntimeError('Unsafe archive path')
            bundle.extractall(tool.parent)
        archive.unlink()
        if os.name != 'nt': binary.chmod(0o755)
    print('Building DEADLIGHT…', flush=True)
    subprocess.run([str(binary), '--no-daemon', ':app:assembleDebug'], cwd=ROOT, check=True)
    source = ROOT / 'app' / 'build' / 'outputs' / 'apk' / 'debug' / 'app-debug.apk'
    target = ROOT / 'DEADLIGHT.apk'
    shutil.copy2(source, target)
    print('APK ready: ' + str(target))

if __name__ == '__main__':
    try: main()
    except (RuntimeError, OSError, subprocess.CalledProcessError) as exc:
        print('Build failed: ' + str(exc), file=sys.stderr)
        sys.exit(1)
