"""Paused adapter that continues using stdio after acknowledging shutdown."""
import json
import os
from pathlib import Path
import select
import signal
import sys
import threading
import time

root = Path(ROOT)
mode = (root / 'mode').read_text()
lock = threading.Lock()
sequence = 0


def send(message):
    global sequence
    with lock:
        sequence += 1
        message['seq'] = sequence
        payload = json.dumps(message).encode()
        sys.stdout.buffer.write(f'Content-Length: {len(payload)}\r\n\r\n'.encode() + payload)
        sys.stdout.buffer.flush()


def output(text):
    send({'type': 'event', 'event': 'output', 'body': {'category': 'stdout', 'output': text + '\n'}})


def exit_cleanly(check_stdin=False):
    time.sleep(.2)
    if check_stdin and select.select([sys.stdin], [], [], 0)[0] and not os.read(0, 1):
        (root / 'premature-eof').touch()
        os._exit(97)
    output('final shutdown output')
    (root / 'clean-exit').touch()
    os._exit(0)


def sigterm(signum, frame):
    (root / 'sigterm').touch()
    if mode == 'ignore-disconnect':
        threading.Thread(target=exit_cleanly, daemon=True).start()
    # Otherwise finish the in-progress protocol cleanup, or intentionally hang.


signal.signal(signal.SIGTERM, sigterm)


def end_session():
    time.sleep(.2)
    output('output after terminate response')
    send({'type': 'event', 'event': 'terminated', 'body': {}})


while True:
    headers = {}
    while True:
        line = sys.stdin.buffer.readline()
        if not line:
            (root / 'premature-eof').touch()
            os._exit(98)
        if line in (b'\n', b'\r\n'):
            break
        key, value = line.decode().split(':', 1)
        headers[key.lower()] = value.strip()
    request = json.loads(sys.stdin.buffer.read(int(headers['content-length'])))
    with (root / 'requests.jsonl').open('a') as log:
        log.write(json.dumps(request) + '\n')
    command = request['command']
    body = {}
    success = True
    if command == 'initialize':
        body = {'supportsConfigurationDoneRequest': True, 'supportsTerminateRequest': mode != 'no-terminate'}
    elif command == 'launch':
        send({'type': 'event', 'event': 'initialized'})
    elif command == 'threads':
        body = {'threads': [{'id': 1, 'name': 'Factorio Lua'}]}
    elif command == 'stackTrace':
        body = {'stackFrames': [{'id': 1, 'name': 'paused', 'line': 1, 'column': 1,
                                'source': {'path': str(root / 'control.lua')}}], 'totalFrames': 1}
    elif command == 'scopes':
        body = {'scopes': []}
    elif command == 'setBreakpoints':
        body = {'breakpoints': [dict(verified=True, line=b['line']) for b in request['arguments']['breakpoints']]}
    elif command == 'terminate':
        if mode in ('ignore-terminate', 'ignore-signal'):
            continue
        if mode == 'exit-on-terminate':
            exit_cleanly()
        success = mode != 'terminate-error'
    elif command == 'disconnect' and mode in ('ignore-disconnect', 'ignore-signal'):
        continue
    send({'type': 'response', 'request_seq': request['seq'], 'command': command, 'success': success, 'body': body})
    if command == 'configurationDone':
        send({'type': 'event', 'event': 'stopped', 'body': {'reason': 'pause', 'threadId': 1, 'allThreadsStopped': True}})
    elif command == 'terminate' and success:
        threading.Thread(target=end_session, daemon=True).start()
    elif command == 'disconnect':
        exit_cleanly(check_stdin=True)
