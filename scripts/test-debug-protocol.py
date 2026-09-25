import json
from protocol import ROOT, Peer

fixture = ROOT / 'fixture'
results = []
try:
    for iteration in range(3):
        peer = Peer(['/Applications/factorio.app/Contents/MacOS/factorio', '--dap'], f'dap-{iteration}', fixture, dap=True)
        run = {'iteration': iteration}
        results.append(run)
        try:
            run['capabilities'] = peer.request('initialize', {'clientID': 'fmtk-spike', 'adapterID': 'factorio', 'linesStartAt1': True, 'columnsStartAt1': True, 'pathFormat': 'path', 'supportsVariableType': True, 'supportsVariablePaging': True})
            launch = peer.start_request('launch', {'factorioArgs': ['--config', str(fixture / 'config.ini'), '--mod-directory', str(fixture / 'mods'), '--load-game', str(fixture / 'probe.zip')], 'followSymlinks': True, 'hookDebugConsole': True})
            peer.wait_event('initialized', timeout=60)
            control = fixture / 'mods/fmtk-probe/control.lua'
            line = next(i for i, text in enumerate(control.read_text().splitlines(), 1) if '-- BREAKPOINT' in text)
            run['breakpoints'] = peer.request('setBreakpoints', {'source': {'name': 'control.lua', 'path': str(control)}, 'breakpoints': [{'line': line}]})
            peer.request('setExceptionBreakpoints', {'filters': []})
            peer.request('configurationDone')
            run['launch'] = peer.response(launch, timeout=90)
            run['stopped'] = peer.wait_event('stopped', timeout=90)
            thread = run['stopped']['body']['threadId']
            stack = peer.request('stackTrace', {'threadId': thread})
            run['stack'] = stack
            frame = stack['stackFrames'][0]['id']
            run['scopes'] = peer.request('scopes', {'frameId': frame})
            run['variables'] = [peer.request('variables', {'variablesReference': scope['variablesReference']}) for scope in run['scopes']['scopes'] if scope['name'].lower().startswith('local')]
            locals_ = run['variables'][0]['variables']
            run['expanded'] = {variable['name']: peer.request('variables', {'variablesReference': variable['variablesReference']}) for variable in locals_ if variable['name'].endswith((' nested', ' surface'))}
            run['evaluate'] = {expression: peer.request('evaluate', {'expression': expression, 'frameId': frame, 'context': 'watch'}) for expression in ['count + 1', 'nested.inner.value', 'surface.name', 'game.tick']}
            for step in ['stepIn', 'stepOut', 'next']:
                peer.request(step, {'threadId': thread})
                peer.wait_event('stopped')
                run[step] = peer.request('stackTrace', {'threadId': thread})
            dependency = fixture / 'mods/LogisticTrainNetwork/script/metrics.lua'
            dependency_line = next(i for i, text in enumerate(dependency.read_text().splitlines(), 1) if 'local value = (rawget' in text)
            peer.request('setBreakpoints', {'source': {'path': str(control)}, 'breakpoints': []})
            peer.request('setBreakpoints', {'source': {'path': str(dependency)}, 'breakpoints': [{'line': dependency_line}]})
            peer.request('continue', {'threadId': thread})
            dependency_stop = peer.wait_event('stopped')
            run['dependency-stack'] = peer.request('stackTrace', {'threadId': dependency_stop['body']['threadId']})
            assert run['evaluate']['count + 1']['result'] == '42'
            assert run['stepIn']['stackFrames'][0]['source']['path'].endswith('/helper.lua')
            assert run['dependency-stack']['stackFrames'][0]['source']['path'] == str(dependency)
            run['disconnect'] = peer.request('disconnect', {'terminateDebuggee': True})
            run['exitCode'] = peer.process.wait(timeout=15)
        finally:
            peer.close()
finally:
    (ROOT / 'evidence/dap-results.json').write_text(json.dumps(results, indent=2) + '\n')
    print(json.dumps(results, indent=2))
