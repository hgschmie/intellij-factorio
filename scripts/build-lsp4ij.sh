#!/bin/bash
# Historical entry point: prepare the pinned official plugin; no local source build is needed.
set -euo pipefail
PLUGIN_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORKSPACE="$(dirname "$PLUGIN_ROOT")"
mkdir -p "$PLUGIN_ROOT/build/logs" "$WORKSPACE/dev/tmp"
python3 - "$PLUGIN_ROOT" "$WORKSPACE" <<'PY'
from pathlib import Path
from zipfile import ZipFile
import hashlib, io, json, shutil, subprocess, sys, tempfile
import xml.etree.ElementTree as ET
plugin, workspace = map(Path, sys.argv[1:])
lock = plugin / 'lsp4ij.lock'
pin = json.loads(lock.read_text())
archive = workspace / 'upstream/lsp4ij/build/distributions' / ('lsp4ij-' + pin['version'] + '.zip')
archive.parent.mkdir(parents=True, exist_ok=True)
if not archive.exists() or hashlib.sha256(archive.read_bytes()).hexdigest() != pin['sha256']:
    with tempfile.TemporaryDirectory(dir=workspace / 'dev/tmp') as tmp:
        download = Path(tmp) / 'lsp4ij.zip'
        subprocess.run(['curl', '-fL', '--retry', '2', '--max-time', '120', pin['url'], '-o', str(download)], check=True)
        if hashlib.sha256(download.read_bytes()).hexdigest() != pin['sha256']:
            raise SystemExit('LSP4IJ archive checksum mismatch')
        shutil.move(download, archive)
plugins = workspace / 'dev/plugins'
plugins.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(dir=workspace / 'dev/tmp') as tmp:
    with ZipFile(archive) as z:
        descriptors = []
        for name in z.namelist():
            if name.endswith('.jar'):
                with ZipFile(io.BytesIO(z.read(name))) as jar:
                    if 'META-INF/plugin.xml' in jar.namelist():
                        descriptors.append(ET.fromstring(jar.read('META-INF/plugin.xml')))
        assert len(descriptors) == 1
        assert descriptors[0].findtext('id') == 'com.redhat.devtools.lsp4ij'
        assert descriptors[0].findtext('version') == pin['version']
    subprocess.run(['/usr/bin/unzip', '-q', str(archive), '-d', tmp], check=True)
    prepared = Path(tmp) / 'lsp4ij'
    (prepared / 'artifact.lock').write_text(lock.read_text())
    target = plugins / 'lsp4ij'
    if target.exists():
        shutil.rmtree(target)
    shutil.move(str(prepared), target)
print('Prepared official LSP4IJ:', target)
print('Verified installable ZIP:', archive)
PY
