"""Exercise every asymmetric attribute in installed Factorio docs through real LSP."""
import json
import os
import re
import shutil
import subprocess
import time
from pathlib import Path

WORKSPACE = Path(__file__).resolve().parents[2]
ROOT = WORKSPACE / 'intellij-factorio/build/test-work/asymmetric-api'
fixture = ROOT / 'fixture'
fixture.mkdir(parents=True, exist_ok=True)
os.environ['FACTORIO_TEST_ROOT'] = str(ROOT)
from protocol import Peer, initialize, open_document, position

docs = Path(os.environ.get('FACTORIO_API_DOCS', '/Applications/factorio.app/Contents/doc-html'))
cli = WORKSPACE / 'upstream/vscode-factoriomod-debug/dist/fmtk-cli.js'
library = ROOT / 'api/factorio/library'
with (ROOT / 'generation.log').open('w') as log:
    subprocess.run([shutil.which('node'), str(cli), 'docs', str(ROOT / 'api'), '--target', 'emmylua',
                    '--docs', str(docs / 'runtime-api.json'), '--protos', str(docs / 'prototype-api.json')],
                   stdout=log, stderr=subprocess.STDOUT, check=True)
api = json.loads((docs / 'runtime-api.json').read_text())
attributes = [(cls['name'], attr) for cls in api['classes'] for attr in cls['attributes']
              if attr.get('read_type') is not None and attr.get('write_type') is not None
              and attr['read_type'] != attr['write_type']]
assert attributes, 'No asymmetric API attributes found'
lines, invalid_lines = [], []
for index, (cls, attr) in enumerate(attributes):
    stub = (library / 'runtime-api' / f'{cls}.lua').read_text()
    types = {}
    for direction in ['read', 'write']:
        match = re.search(r'^---@field \(' + direction + r'\) ' + re.escape(attr['name']) + r'(\?)? (.+)$', stub, re.M)
        assert match, (cls, attr['name'], direction)
        types[direction] = f'({match[2]})|nil' if match[1] else match[2]
    lines += [f'---@param obj {cls}', f'---@param input {types["write"]}', f'local function probe{index}(obj, input)',
              f'  ---@type {types["read"]}', f'  local before = obj.{attr["name"]}',
              f'  obj.{attr["name"]} = input', f'  ---@type {types["read"]}', f'  local after = obj.{attr["name"]}']
    invalid_lines.append(len(lines))
    lines += [f'  obj.{attr["name"]} = function() end', 'end', '']
lines += ['---@param burner LuaBurner', 'local function burner_probe(burner)',
          '  burner.currently_burning = "coal"', '  local burning = burner.currently_burning',
          '  if burning then', '    local name = burning.name', '  end', 'end', '']
text = '\n'.join(lines)
control = fixture / 'control.lua'
control.write_text(text)
(fixture / '.emmyrc.json').write_text(json.dumps({'runtime': {'version': 'Lua 5.2'},
    'workspace': {'library': [str(library)]}}, indent=2))
server = os.environ.get('EMMY_LS', str(WORKSPACE / 'upstream/Intellij-EmmyLua2/build/prepared/IntelliJ-EmmyLua2/server/darwin-arm64/emmylua_ls'))
peer = Peer([server, '--resources-path', str(WORKSPACE / 'dev/cache/emmy'), '--log-path', str(ROOT / 'evidence'), '--editor', 'intellij'], 'asymmetric-api', fixture)
results = {'version': api['application_version'], 'attributes': [f'{cls}.{attr["name"]}' for cls, attr in attributes]}
try:
    initialize(peer, fixture)
    peer.notify('initialized')
    open_document(peer, control, 'lua', text)
    params = {'textDocument': {'uri': control.as_uri()}}
    deadline = time.monotonic() + 45
    while True:
        report = peer.request('textDocument/diagnostic', params)
        diagnostics = report.get('items', [])
        mismatches = [d for d in diagnostics if d.get('code') == 'assign-type-mismatch']
        if len(mismatches) == len(attributes) or time.monotonic() >= deadline:
            break
        time.sleep(.2)
    results['assignment-diagnostics'] = mismatches
    assert sorted(d['range']['start']['line'] for d in mismatches) == invalid_lines, diagnostics
    results['hover'] = peer.request('textDocument/hover', dict(params, position=position(text, 'local burning = burner.currently_burning', -2)))
    hover = json.dumps(results['hover'])
    assert 'Read:' in hover and 'ItemIDAndQualityIDPair' in hover and 'Write:' in hover and ('ItemWithQualityID' in hover or ('LuaItemPrototype' in hover and 'string' in hover)), hover
    results['completion'] = peer.request('textDocument/completion', dict(params, position=position(text, 'local name = burning.'), context={'triggerKind': 1}))
    completion = results['completion']
    items = completion.get('items', []) if isinstance(completion, dict) else completion
    labels = [item['label'] for item in items]
    assert 'name' in labels and 'quality' in labels, labels
    results['checks'] = {'all-attribute-types': True, 'burner-hover': True, 'read-after-write-completion': True}
    peer.request('shutdown')
    peer.notify('exit')
finally:
    peer.close()
    (ROOT / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
print(f"Validated {len(attributes)} asymmetric attributes for Factorio {api['application_version']}; hover and read-after-write completion passed.")
