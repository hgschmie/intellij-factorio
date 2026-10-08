#!/bin/bash
# Prepare a checksum-pinned official plugin or build the pinned local source.
set -euo pipefail
PLUGIN_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORKSPACE="$(dirname "$PLUGIN_ROOT")"
mkdir -p "$PLUGIN_ROOT/build/logs" "$WORKSPACE/dev/tmp"
python3 - "$PLUGIN_ROOT" "$WORKSPACE" <<'PY'
from pathlib import Path
from zipfile import ZipFile
import hashlib, io, json, os, shutil, subprocess, sys, tempfile
import xml.etree.ElementTree as ET
plugin, workspace = map(Path, sys.argv[1:])
lock = plugin / 'lsp4ij.lock'
pin = json.loads(lock.read_text())
archive = workspace / 'upstream/lsp4ij/build/distributions' / ('lsp4ij-' + pin['version'] + '.zip')
archive.parent.mkdir(parents=True, exist_ok=True)
if not archive.exists() or hashlib.sha256(archive.read_bytes()).hexdigest() != pin['sha256']:
    if 'sourceRevision' in pin:
        source = workspace / 'upstream/lsp4ij/build/checkouts/patched'
        revision = subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
        if revision != pin['sourceRevision']:
            raise SystemExit('LSP4IJ work branch differs from lsp4ij.lock')
        subprocess.run(['git', '-C', str(source), 'diff', '--quiet', 'HEAD', '--'], check=True)
        idea = Path(os.environ.get('FMTK_IDEA_PATH', Path.home() / 'Applications/IntelliJ IDEA.app'))
        env = dict(os.environ, GRADLE_USER_HOME=str(workspace / 'dev/cache/gradle'),
                   JAVA_HOME=os.environ.get('FMTK_JAVA_HOME', '/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home'),
                   JAVA_TOOL_OPTIONS=f'-Duser.home={workspace}/dev/build-home -Djava.io.tmpdir={workspace}/dev/tmp',
                   TMPDIR=str(workspace / 'dev/tmp'))
        logs = source / 'build/logs'
        logs.mkdir(parents=True, exist_ok=True)
        with (logs / 'prepare-pinned-plugin.log').open('w') as log:
            subprocess.run(['bash', 'gradlew', '--no-daemon', '--no-configuration-cache',
                            f'-PideaPath={idea}', '-PbuildJavaVersion=25',
                            f'-Dorg.gradle.java.installations.paths={idea}/Contents/jbr/Contents/Home',
                            'buildPlugin'], cwd=source, env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
        shutil.copy2(source / 'build/distributions' / archive.name, archive)
    else:
        with tempfile.TemporaryDirectory(dir=workspace / 'dev/tmp') as tmp:
            download = Path(tmp) / 'lsp4ij.zip'
            subprocess.run(['curl', '-fL', '--retry', '2', '--max-time', '120', pin['url'], '-o', str(download)], check=True)
            shutil.move(download, archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != pin['sha256']:
        raise SystemExit('LSP4IJ archive checksum mismatch')
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
print('Prepared pinned LSP4IJ:', target)
print('Verified installable ZIP:', archive)
PY
