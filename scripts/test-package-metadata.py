"""Check the bundled CLI excludes IDE metadata and honors package options."""
import json
import shutil
import subprocess
import zipfile
from pathlib import Path

workspace = Path(__file__).resolve().parents[2]
root = workspace / 'spike/package-metadata-test'
if root.exists():
    shutil.rmtree(root)
mod = root / 'mod'
extra = root / 'extra'
mod.mkdir(parents=True)
extra.mkdir()
for name in ['control.lua', 'module.iml', '.emmyrc.json', '.idea/modules.xml',
             'nested/nested.iml', 'scripts/helper.lua', 'private.txt']:
    path = mod / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('-- fixture\n')
(extra / 'explicit.iml').write_text('explicitly requested extra')
(mod / 'info.json').write_text(json.dumps(dict(
    name='metadata-probe', version='0.1.0', title='Metadata probe', author='Test',
    factorio_version='2.1', package=dict(ignore=['private.txt'], extra=[dict(root=str(extra))]))))
subprocess.run(['node', str(workspace / 'vscode-factoriomod-debug/dist/fmtk-cli.js'),
                'package'], cwd=mod, check=True)
with zipfile.ZipFile(mod / 'metadata-probe_0.1.0.zip') as archive:
    names = sorted(n.split('/', 1)[1] for n in archive.namelist() if not n.endswith('/'))
assert names == ['control.lua', 'explicit.iml', 'info.json', 'scripts/helper.lua'], names
(root / 'results.json').write_text(json.dumps(dict(passed=True, entries=names), indent=2))
print('Package contents passed:', names)
