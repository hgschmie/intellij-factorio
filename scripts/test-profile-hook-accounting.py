"""Compare native profile accounting with explicit hook events on/off.

Run with FACTORIO_TEST_ROOT pointing at a dedicated disposable fixture directory.
Only that copy of fmtk-probe is changed; this never attaches to a user's game.
"""
import collections
import json
import os
import shutil
import time

if not os.environ.get('FACTORIO_TEST_ROOT'):
    raise SystemExit('Set FACTORIO_TEST_ROOT to a dedicated disposable directory')
from protocol import ROOT, Peer

fixture = ROOT / 'fixture'
control = fixture / 'mods/fmtk-probe/control.lua'
control.write_text('''local function measured_work()
    local sum = 0
    for i = 1, 40 do
        sum = sum + i -- COST
    end
    sum = sum + 0 -- PAUSE
    return sum
end

script.on_event(defines.events.on_tick, function()
    local ready = true -- READY
    storage.hook_probe_result = measured_work()
    local done = true -- DONE
end)
''')
lines = {marker: next(i for i, text in enumerate(control.read_text().splitlines(), 1)
                       if '-- ' + marker in text) for marker in ('READY', 'COST', 'PAUSE', 'DONE')}


def analyze(path):
    data = json.loads(path.read_text())
    nodes = {n['id']: n for n in data['nodes']}
    self_time = collections.defaultdict(float)
    previous = None
    elapsed = 0.0
    root = next(n['id'] for n in data['nodes'] if n['callFrame']['functionName'] == '(root)')
    for sample, delta in zip(data['samples'], data['timeDeltas'], strict=True):
        self_time[root if previous is None else previous] += delta
        previous = sample
        elapsed += delta
    duration = data['endTime'] - data['startTime']
    self_time[root if previous is None else previous] += duration - elapsed

    def category(node):
        frame = node['callFrame']
        name = frame['functionName']
        if name.startswith('(hook:'):
            return 'hook'
        return 'pause' if name == '(pause)' else 'root' if name == '(root)' else 'execution'

    totals = collections.defaultdict(float)
    for node_id, value in self_time.items():
        totals[category(nodes[node_id])] += value / 1000
    work_nodes = [n for n in nodes.values() if n['callFrame'].get('url', '').endswith('/fmtk-probe/control.lua')
                  and n['callFrame']['lineNumber'] == 0]
    assert len(work_nodes) == 1, work_nodes
    work = collections.defaultdict(float)
    queue = [work_nodes[0]['id']]
    while queue:
        node = nodes[queue.pop()]
        work[category(node)] += self_time[node['id']] / 1000
        queue.extend(node.get('children', []))
    return {'duration_ms': duration / 1000, 'samples': len(data['samples']),
            'hook_frames': sum(category(n) == 'hook' for n in nodes.values()),
            'pause_frames': sum(category(n) == 'pause' for n in nodes.values()),
            'all_ms': dict(totals), 'work_ms': dict(work), 'work_total_ms': sum(work.values())}


def wait_at(peer, marker):
    stopped = peer.wait_event('stopped', timeout=30)
    thread = stopped['body']['threadId']
    frames = peer.request('stackTrace', {'threadId': thread, 'startFrame': 0, 'levels': 1})['stackFrames']
    assert frames[0]['line'] == lines[marker], (marker, frames)
    return thread


peer = Peer(['/Applications/factorio.app/Contents/MacOS/factorio', '--dap'], 'hook-accounting', fixture, dap=True)
results = []
try:
    capabilities = peer.request('initialize', {'adapterID': 'factorio', 'linesStartAt1': True, 'pathFormat': 'path'})
    assert capabilities['supportsConditionalBreakpoints'], capabilities
    launch = peer.start_request('launch', {'factorioArgs': ['--config', str(fixture / 'config.ini'),
                '--mod-directory', str(fixture / 'mods'), '--load-game', str(fixture / 'probe.zip')], 'followSymlinks': True})
    peer.wait_event('initialized', timeout=60)
    peer.request('setBreakpoints', {'source': {'path': str(control)}, 'breakpoints': [{'line': lines['READY']}]})
    peer.request('setExceptionBreakpoints', {'filters': []})
    peer.request('configurationDone')
    peer.response(launch, timeout=90)
    thread = wait_at(peer, 'READY')
    # Reverse the second heavy pair to expose warmup/order effects.
    cases = [('cheap', False), ('cheap', True), ('heavy', False), ('heavy', True), ('heavy', True), ('heavy', False)]
    for index, (cost, hooks) in enumerate(cases):
        condition = 'false' if cost == 'cheap' else '(function() local s=0 for i=1,1000000 do s=s+i end return s==0 end)()'
        bps = [{'line': lines[m]} for m in ('READY', 'PAUSE', 'DONE')]
        bps.append({'line': lines['COST'], 'condition': condition})
        response = peer.request('setBreakpoints', {'source': {'path': str(control)}, 'breakpoints': bps})
        assert all(b['verified'] for b in response['breakpoints']), response
        before = time.monotonic()
        peer.request('startProfile', {'show_hook_events': hooks})
        peer.wait_event('profileRunning')
        peer.request('continue', {'threadId': thread})
        thread = wait_at(peer, 'PAUSE')
        pause_start = time.monotonic()
        time.sleep(.4)
        deliberate_pause_ms = (time.monotonic() - pause_start) * 1000
        peer.request('continue', {'threadId': thread})
        thread = wait_at(peer, 'DONE')
        peer.request('stopProfile')
        complete = peer.wait_event('profileComplete')
        observed_ms = (time.monotonic() - before) * 1000
        destination = ROOT / 'evidence' / f'{index}-{cost}-hooks-{str(hooks).lower()}.cpuprofile'
        shutil.copyfile(complete['body']['path'], destination)
        result = {'case': index, 'condition': cost, 'show_hook_events': hooks, 'client_elapsed_ms': observed_ms,
                  'deliberate_pause_ms': deliberate_pause_ms, 'snapshot': str(destination), **analyze(destination)}
        results.append(result)
        (ROOT / 'evidence/results.json').write_text(json.dumps(results, indent=2))
        print(json.dumps(result), flush=True)
        peer.request('continue', {'threadId': thread})
        thread = wait_at(peer, 'READY')
    peer.request('terminate')
    peer.wait_event('terminated', timeout=15)
    # The native adapter may not answer disconnect after terminated. All captures
    # are already saved; close only this probe's process in the finally block.
finally:
    peer.close()
