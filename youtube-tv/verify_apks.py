#!/usr/bin/env python3
"""Require exactly one ARM APK per ABI, consistent native libs, and real signatures."""
import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('source', type=Path)
parser.add_argument('output', type=Path)
parser.add_argument('--apksigner', required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
libraries = []
for abi, label in [('armeabi-v7a', 'arm32'), ('arm64-v8a', 'arm64')]:
    matches = list(args.source.glob('**/*_' + abi + '.apk'))
    if len(matches) != 1:
        raise SystemExit(f'Expected one {abi} APK, found {len(matches)}')
    apk = matches[0]
    with zipfile.ZipFile(apk) as z:
        if z.testzip(): raise SystemExit('Corrupt APK')
        native = [name for name in z.namelist() if name.startswith('lib/') and name.endswith('.so')]
        assert native, 'No native libraries'
        assert {name.split('/')[1] for name in native} == {abi}, 'Wrong ABI'
        libraries.append({name.split('/')[-1] for name in native})
        assert 'classes.dex' in z.namelist() and 'AndroidManifest.xml' in z.namelist()
    subprocess.run([args.apksigner, 'verify', '--verbose', str(apk)], check=True)
    target = args.output / f'TihuluTube-0.1.0-{label}-preview.apk'
    shutil.copy2(apk, target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    target.with_suffix('.apk.sha256').write_text(f'{digest}  {target.name}\n')
    print(f'{target.name}: {target.stat().st_size / 1048576:.1f} MiB; native ABI: {abi}')
assert libraries[0] == libraries[1], 'Native library mismatch between ARM32 and ARM64'
