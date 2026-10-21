"""Capture a native Factorio profile using only disposable protocol fixtures."""
import argparse
import json
import shutil
import time
from pathlib import Path
from protocol import ROOT, Peer

fixture = ROOT / 'fixture'
parser = argparse.ArgumentParser()
parser.add_argument('--on-launch', action='store_true', help='Start recording as soon as the launch handshake finishes')
parser.add_argument('--while-running', action='store_true', help='Start recording after resuming the loaded game')
parser.add_argument('--show-hook-events', action='store_true', help='Include recorder hook timing events')
parser.add_argument('--quit-before-stop', choices=['terminated', 'quitting'],
                    help='Reproduce native profile loss: quit, then try saving at this notification')
args = parser.parse_args()
name = 'profile-quit-' + args.quit_before_stop if args.quit_before_stop else 'profile-running' if args.while_running else 'profile-launch' if args.on_launch else 'profile'
if args.show_hook_events:
    name += '-hooks'
profile_options = {'show_hook_events': args.show_hook_events}
peer = Peer(['/Applications/factorio.app/Contents/MacOS/factorio', '--dap'], name, fixture, dap=True)
try:
    peer.request('initialize', {'adapterID': 'factorio', 'linesStartAt1': True, 'pathFormat': 'path'})
    launch = peer.start_request('launch', {'factorioArgs': ['--config', str(fixture / 'config.ini'), '--mod-directory', str(fixture / 'mods'), '--load-game', str(fixture / 'probe.zip')], 'followSymlinks': True})
    peer.wait_event('initialized', timeout=60)
    control = fixture / 'mods/fmtk-probe/control.lua'
    line = next(i for i, text in enumerate(control.read_text().splitlines(), 1) if '-- BREAKPOINT' in text)
    peer.request('setBreakpoints', {'source': {'path': str(control)}, 'breakpoints': [{'line': line}]})
    peer.request('setExceptionBreakpoints', {'filters': []})
    peer.request('configurationDone')
    peer.response(launch, timeout=90)
    if args.on_launch:
        peer.request('startProfile', profile_options)
        peer.wait_event('profileRunning')
    stopped = peer.wait_event('stopped', timeout=90)
    if not args.on_launch and not args.while_running:
        peer.request('startProfile', profile_options)
        peer.wait_event('profileRunning')
    peer.request('setBreakpoints', {'source': {'path': str(control)}, 'breakpoints': []})
    peer.request('continue', {'threadId': stopped['body']['threadId']})
    time.sleep(.2)
    if args.while_running:
        peer.request('startProfile', profile_options)
        peer.wait_event('profileRunning')
        time.sleep(.2)
    if args.quit_before_stop:
        peer.request('terminate')
        if args.quit_before_stop == 'terminated':
            peer.wait_event('terminated', timeout=15)
        else:
            while 'Quitting: user-quit.' not in peer.wait_event('output', timeout=15).get('body', {}).get('output', ''):
                pass
        # Intentionally keep the transport open: neither disconnect nor IDE process
        # cleanup can explain a lost profile in this native-only reproduction.
        print('Received ' + args.quit_before_stop + '; requesting profile save', flush=True)
    peer.request('stopProfile')
    completed = peer.wait_event('profileComplete')
    path = Path(completed['body']['path'])
    destination = ROOT / 'evidence' / path.name
    shutil.copyfile(path, destination)
    print(json.dumps({'event': completed, 'snapshot': str(destination), 'bytes': destination.stat().st_size}))
    if not args.quit_before_stop:
        peer.request('terminate')
        peer.wait_event('terminated', timeout=15)
    # The native adapter may close stdout immediately after terminated.
    try:
        peer.request('disconnect')
    except (RuntimeError, BrokenPipeError):
        pass
    peer.process.wait(timeout=15)
finally:
    peer.close()
