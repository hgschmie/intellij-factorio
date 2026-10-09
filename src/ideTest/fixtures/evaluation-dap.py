"""DAP peer exercising frame-aware REPL requests, errors, late replies and variables."""
import json
from pathlib import Path
import sys
import threading
import time

root = Path(ROOT)
sequence = 0
lock = threading.Lock()


def send(message):
    global sequence
    with lock:
        sequence += 1
        message['seq'] = sequence
        payload = json.dumps(message).encode()
        sys.stdout.buffer.write(f'Content-Length: {len(payload)}\r\n\r\n'.encode() + payload)
        sys.stdout.buffer.flush()


def reply(request, body, success=True):
    message = {'type': 'response', 'request_seq': request['seq'], 'command': request['command'],
               'success': success, 'body': body}
    if not success:
        message['message'] = 'error'
    send(message)


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
    with (root / 'requests.jsonl').open('a') as log:
        log.write(json.dumps(request) + '\n')
    command = request['command']
    args = request.get('arguments', {})
    body = {}
    if command == 'initialize':
        body = {'supportsConfigurationDoneRequest': True, 'supportsTerminateRequest': True,
                'supportsEvaluateForHovers': True}
    elif command == 'launch':
        send({'type': 'event', 'event': 'initialized'})
    elif command == 'threads':
        body = {'threads': [{'id': 1, 'name': 'Factorio Lua'}]}
    elif command == 'stackTrace':
        body = {'stackFrames': [{'id': 7, 'name': 'paused', 'line': 1, 'column': 1,
                                'source': {'path': str(root / 'control.lua')}}], 'totalFrames': 1}
    elif command == 'scopes':
        body = {'scopes': []}
    elif command == 'evaluate':
        expression = args['expression']
        if expression == 'bad()':
            reply(request, {'error': {'id': 1, 'format': 'LuaTrain.get_rail_end: invalid argument 3'}}, False)
            continue
        body = {'result': '{inner = {...}}', 'type': 'table', 'variablesReference': 21, 'namedVariables': 1}
        if expression == 'slow()':
            def delayed(r=request, b=body):
                time.sleep(.8)
                reply(r, b)
            threading.Thread(target=delayed, daemon=True).start()
            continue
    elif command == 'variables':
        body = {'variables': [{'name': 'inner', 'value': '{value = 42}', 'type': 'table', 'variablesReference': 22}]}
        if args['variablesReference'] == 22:
            body = {'variables': [{'name': 'value', 'value': '42', 'type': 'number', 'variablesReference': 0}]}
    elif command == 'continue':
        body = {'allThreadsContinued': True}
    reply(request, body)
    if command == 'configurationDone':
        send({'type': 'event', 'event': 'stopped', 'body': {'reason': 'pause', 'threadId': 1, 'allThreadsStopped': True}})
        send({'type': 'event', 'event': 'output', 'body': {'category': 'stderr', 'output': 'real stderr survives\n'}})
    elif command == 'continue':
        send({'type': 'event', 'event': 'continued', 'body': {'threadId': 1, 'allThreadsContinued': True}})
    elif command == 'terminate':
        send({'type': 'event', 'event': 'terminated', 'body': {}})
    elif command == 'disconnect':
        sys.exit(0)
