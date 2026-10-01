"""Controllable adapter for the real IntelliJ session/restart lifecycle test."""
import json
import os
from pathlib import Path
import sys
import threading
import time

root = Path(ROOT)  # supplied by the test's executable bootstrap
counter = root / 'starts'
cycle = int(counter.read_text()) + 1 if counter.exists() else 1
counter.write_text(str(cycle))
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


def terminate_when_requested():
    trigger = root / f'terminate-{cycle}.json'
    while not trigger.exists():
        time.sleep(.02)
    body = json.loads(trigger.read_text())
    send({'type': 'event', 'event': 'terminated', 'body': body})
    # Exercise duplicate notifications too; they must not cancel the first restart.
    if body.get('restart'):
        send({'type': 'event', 'event': 'terminated', 'body': body})
    if (root / f'exit-immediately-{cycle}').exists():
        os._exit(0)


threading.Thread(target=terminate_when_requested, daemon=True).start()
while True:
    headers = {}
    while True:
        line = sys.stdin.buffer.readline()
        if not line:
            sys.exit(0)
        if line in (b'\n', b'\r\n'):
            break
        key, value = line.decode().split(':', 1)
        headers[key.lower()] = value.strip()
    request = json.loads(sys.stdin.buffer.read(int(headers['content-length'])))
    with (root / f'requests-{cycle}.jsonl').open('a') as log:
        log.write(json.dumps(request) + '\n')
    command = request['command']
    body = {}
    if command == 'initialize':
        body = {'supportsConfigurationDoneRequest': True, 'supportsTerminateRequest': True}
    elif command == 'launch':
        send({'type': 'event', 'event': 'initialized'})
    elif command == 'threads':
        body = {'threads': []}
    elif command == 'setBreakpoints':
        body = {'breakpoints': [dict(verified=True, line=b['line']) for b in request['arguments']['breakpoints']]}
    send({'type': 'response', 'request_seq': request['seq'], 'command': command, 'success': True, 'body': body})
    if command == 'disconnect':
        if (root / 'hold-exit').exists():
            while not (root / 'release-exit').exists():
                time.sleep(.02)
        os._exit(0)
