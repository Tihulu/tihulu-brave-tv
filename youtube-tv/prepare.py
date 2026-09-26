#!/usr/bin/env python3
"""Apply this app's reviewed patch and overlays to the pinned, clean source."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess

HERE = Path(__file__).resolve().parent
LOCK = json.loads((HERE / 'upstream.json').read_text())

def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True).strip()

def prepare(root):
    if git(root, 'rev-parse', 'HEAD') != LOCK['commit']:
        raise SystemExit('Wrong upstream commit. Check youtube-tv/upstream.json.')
    if git(root, 'status', '--porcelain', '--untracked-files=no'):
        raise SystemExit('Source has local modifications. Use a fresh checkout; refusing to overwrite work.')
    for path, expected in LOCK['submodules'].items():
        if git(root / path, 'rev-parse', 'HEAD') != expected:
            raise SystemExit('Unexpected submodule revision: ' + path)
    subprocess.run(['git', '-C', str(root), 'apply', '--check', str(HERE / 'patches/smarttube.patch')], check=True)
    subprocess.run(['git', '-C', str(root), 'apply', str(HERE / 'patches/smarttube.patch')], check=True)
    shutil.copytree(HERE / 'overlays', root, dirs_exist_ok=True)
    print('Prepared Tihulu Tube ' + LOCK['app_version'])

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    prepare(parser.parse_args().source.resolve())
