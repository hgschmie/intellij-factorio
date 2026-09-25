import json
import time
from protocol import ROOT, Peer, initialize, open_document, position

fixture = ROOT / 'fixture'
probe = fixture / 'mods/fmtk-probe'
results = {}


def completion(peer, path, text, needle):
    return peer.request('textDocument/completion', {
        'textDocument': {'uri': path.as_uri()}, 'position': position(text, needle),
        'context': {'triggerKind': 1}})


def labels(result):
    return [item['label'] for item in (result.get('items', []) if isinstance(result, dict) else result or [])]


fmtk = Peer(['/opt/homebrew/bin/node', str(ROOT.parent.parent / 'intellij-factorio/build/toolkit/fmtk/fmtk-cli.js'), 'lsp', '--stdio'], 'fmtk-lsp', fixture)
emmy = Peer([str(ROOT.parent / 'plugin-ide/plugins/IntelliJ-EmmyLua2/server/darwin-arm64/emmylua_ls'), '--resources-path', str(ROOT.parent / 'cache/emmy'), '--log-path', str(ROOT / 'evidence'), '--editor', 'intellij'], 'emmy-lsp', fixture)
try:
    for peer in [fmtk, emmy]:
        results['fmtk-init' if peer is fmtk else 'emmy-init'] = initialize(peer, fixture)
        peer.notify('initialized')
    control = probe / 'control.lua'
    original = control.read_text()
    sample = original + '\nlocal api_probe = game.\nlocal imported_probe = Metrics.\nlocal locale_probe = {"fmtk-probe."}\n'
    for peer in [fmtk, emmy]:
        open_document(peer, control, 'lua', sample)
    time.sleep(3)
    results['runtime-completion'] = labels(completion(emmy, control, sample, 'local api_probe = game.'))
    results['import-completion'] = labels(completion(emmy, control, sample, 'local imported_probe = Metrics.'))
    results['locale-completion'] = labels(completion(fmtk, control, sample, 'local locale_probe = {"fmtk-probe.'))
    for key, needle, offset in [('api-hover', 'game.surfaces', -2), ('event-hover', 'event.tick', -2), ('import-definition', '__LogisticTrainNetwork__/script/metrics', -3)]:
        results[key] = emmy.request('textDocument/definition' if key.endswith('definition') else 'textDocument/hover', {'textDocument': {'uri': control.as_uri()}, 'position': position(sample, needle, offset)})
    results['locale-definition'] = fmtk.request('textDocument/definition', {'textDocument': {'uri': control.as_uri()}, 'position': position(sample, 'fmtk-probe.ready', -2)})
    results['local-definition'] = emmy.request('textDocument/definition', {'textDocument': {'uri': control.as_uri()}, 'position': position(sample, 'require("helper', -2)})
    results['event-completion'] = labels(completion(emmy, control, sample, 'if event.'))
    results['signature'] = emmy.request('textDocument/signatureHelp', {'textDocument': {'uri': control.as_uri()}, 'position': position(sample, 'helper.add(')})
    data = probe / 'data.lua'
    data_sample = data.read_text() + '\nlocal proto_probe = prototype.\n'
    open_document(emmy, data, 'lua', data_sample)
    time.sleep(.5)
    results['prototype-completion'] = labels(completion(emmy, data, data_sample, 'local proto_probe = prototype.'))
    dependency = fixture / 'mods/LogisticTrainNetwork/script/metrics.lua'
    dependency_original = dependency.read_text()
    open_document(emmy, dependency, 'lua', dependency_original)
    # Model editing an already-open file. EmmyLua 0.24 dispatches didOpen
    # asynchronously and does not reliably diagnose library files. Immediate
    # didOpen/didChange is separately documented as an upstream ordering race.
    time.sleep(1)
    emmy.notify('textDocument/didChange', {'textDocument': {'uri': dependency.as_uri(), 'version': 2}, 'contentChanges': [{'text': dependency_original.replace('return Metrics\n', 'Metrics.spike_export = 42\nreturn Metrics\n')}]})
    deadline = time.monotonic() + 10
    while True:
        results['dependency-edit'] = labels(completion(emmy, control, sample, 'local imported_probe = Metrics.'))
        if 'spike_export' in results['dependency-edit'] or time.monotonic() >= deadline:
            break
        time.sleep(.2)
    emmy.notify('textDocument/didClose', {'textDocument': {'uri': dependency.as_uri()}})
    locale = probe / 'locale/en/probe.cfg'
    baseline = locale.read_text()
    open_document(fmtk, locale, 'factorio-locale')
    fmtk.notify('textDocument/didChange', {'textDocument': {'uri': locale.as_uri(), 'version': 2}, 'contentChanges': [{'text': baseline + 'unsaved=Unsaved key\n'}]})
    results['locale-unsaved'] = labels(completion(fmtk, control, sample, 'local locale_probe = {"fmtk-probe.'))
    fmtk.notify('textDocument/didClose', {'textDocument': {'uri': locale.as_uri()}})
    update = probe / 'locale/en/update.cfg'
    try:
        for name, kind, content in [('create', 1, '[fmtk-probe]\ncreated=Created\n'), ('change', 2, '[fmtk-probe]\nchanged=Changed\n'), ('delete', 3, None)]:
            if content is None:
                update.unlink()
            else:
                update.write_text(content)
            fmtk.notify('workspace/didChangeWatchedFiles', {'changes': [{'uri': update.as_uri(), 'type': kind}]})
            time.sleep(.2)
            results['locale-' + name] = labels(completion(fmtk, control, sample, 'local locale_probe = {"fmtk-probe.'))
    finally:
        update.unlink(missing_ok=True)
    results['locale-after-close'] = labels(completion(fmtk, control, sample, 'local locale_probe = {"fmtk-probe.'))
    open_document(fmtk, locale, 'factorio-locale')
    results['locale-after-reopen'] = labels(completion(fmtk, control, sample, 'local locale_probe = {"fmtk-probe.'))
    changelog = probe / 'changelog.txt'
    open_document(fmtk, changelog, 'factorio-changelog')
    results['changelog-symbols'] = fmtk.request('textDocument/documentSymbol', {'textDocument': {'uri': changelog.as_uri()}})
    results['checks'] = {
        'runtime': 'surfaces' in results['runtime-completion'],
        'prototype': 'stack_size' in results['prototype-completion'],
        'event': 'tick' in results['event-completion'],
        'dependency-edit': 'spike_export' in results['dependency-edit'],
        'local-import': 'helper.lua' in json.dumps(results['local-definition']),
        'cross-mod-import': 'LogisticTrainNetwork/script/metrics.lua' in json.dumps(results['import-definition']),
        'locale-reopen-clears-unsaved': 'fmtk-probe.unsaved' not in results['locale-after-reopen'],
        'locale-close-clears-unsaved': 'fmtk-probe.unsaved' not in results['locale-after-close'],
        'changelog': bool(results['changelog-symbols']),
    }
    results['watch-registration'] = [json.loads(line)['message'] for line in (ROOT / 'evidence/fmtk-lsp.jsonl').read_text().splitlines() if json.loads(line)['message'].get('method') == 'client/registerCapability']
    results['checks']['watch-registration'] = 'workspace/didChangeWatchedFiles' in json.dumps(results['watch-registration'])
    for peer in [fmtk, emmy]:
        peer.request('shutdown')
        peer.notify('exit')
finally:
    fmtk.close()
    emmy.close()
    (ROOT / 'evidence/lsp-results.json').write_text(json.dumps(results, indent=2) + '\n')
    print(json.dumps(results, indent=2))
assert all(results['checks'].values()), results['checks']
