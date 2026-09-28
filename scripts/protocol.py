"""Small stdio LSP/DAP probe; records full protocol traffic under evidence/."""
import json
import shutil
import os
import queue
import subprocess
import threading
import time
from pathlib import Path

WORKSPACE = Path(__file__).resolve().parents[2]
ROOT = Path(os.environ.get('FACTORIO_TEST_ROOT', str(WORKSPACE / 'intellij-factorio/build/test-work/protocol'))).resolve()
for directory in [ROOT / 'evidence', ROOT / 'tmp', ROOT / 'runtime/home', ROOT / 'runtime/data']:
    directory.mkdir(parents=True, exist_ok=True)
# Tests modify only a disposable copy, never the reusable fixture.
if not (ROOT / 'fixture').exists():
    source = WORKSPACE / 'dev/fixtures/protocol'
    shutil.copytree(source, ROOT / 'fixture', ignore=shutil.ignore_patterns('write-data', '.idea'))
    (ROOT / 'fixture/write-data').mkdir()
    for name in ['config.ini', '.emmyrc.json']:
        path = ROOT / 'fixture' / name
        path.write_text(path.read_text().replace(str(source), str(ROOT / 'fixture')))



class Peer:
    def __init__(self, command, name, cwd=None, dap=False):
        self.dap = dap
        self.sequence = 0
        self.events = []
        self.pending = {}
        self.incoming = queue.Queue()
        self.trace = (ROOT / 'evidence' / f'{name}.jsonl').open('w')
        self.stderr = (ROOT / 'evidence' / f'{name}.stderr').open('w')
        env = dict(os.environ, TMPDIR=str(ROOT / 'tmp'), HOME=str(ROOT / 'runtime/home'),
                   XDG_DATA_HOME=str(ROOT / 'runtime/data'), LOCALAPPDATA=str(ROOT / 'runtime/data'))
        self.process = subprocess.Popen(command, cwd=cwd or ROOT, env=env,
                                        stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                        stderr=self.stderr)
        threading.Thread(target=self.read, daemon=True).start()

    def read(self):
        try:
            while True:
                headers = {}
                while True:
                    line = self.process.stdout.readline()
                    if not line:
                        self.incoming.put(RuntimeError('Server exited'))
                        return
                    if line in (b'\r\n', b'\n'):
                        break
                    key, value = line.decode().split(':', 1)
                    headers[key.lower()] = value.strip()
                data = json.loads(self.process.stdout.read(int(headers['content-length'])))
                self.incoming.put(data)
        except Exception as error:
            self.incoming.put(error)

    def send(self, data):
        self.trace.write(json.dumps({'direction': 'send', 'message': data}) + '\n')
        self.trace.flush()
        payload = json.dumps(data).encode()
        self.process.stdin.write(f'Content-Length: {len(payload)}\r\n\r\n'.encode() + payload)
        self.process.stdin.flush()

    def notify(self, method, params=None):
        self.send({'jsonrpc': '2.0', 'method': method, 'params': params or {}})

    def start_request(self, method, params=None):
        self.sequence += 1
        number = self.sequence
        if self.dap:
            data = {'seq': number, 'type': 'request', 'command': method, 'arguments': params or {}}
        else:
            data = {'jsonrpc': '2.0', 'id': number, 'method': method, 'params': params or {}}
        self.send(data)
        return number

    def pump(self, timeout):
        data = self.incoming.get(timeout=timeout)
        if isinstance(data, Exception):
            raise data
        self.trace.write(json.dumps({'direction': 'receive', 'message': data}) + '\n')
        self.trace.flush()
        if (not self.dap) and 'method' in data and 'id' in data:
            result = [None for _ in data.get('params', {}).get('items', [])] if data['method'] == 'workspace/configuration' else None
            self.send({'jsonrpc': '2.0', 'id': data['id'], 'result': result})
        elif data.get('type') == 'response' or ('id' in data and 'method' not in data):
            self.pending[data.get('request_seq', data.get('id'))] = data
        else:
            self.events.append(data)
        return data

    def response(self, number, timeout=30):
        deadline = time.monotonic() + timeout
        while number not in self.pending:
            self.pump(max(.01, deadline - time.monotonic()))
        data = self.pending.pop(number)
        if 'error' in data or data.get('success') is False:
            raise RuntimeError(data)
        return data.get('body' if self.dap else 'result')

    def request(self, method, params=None, timeout=30):
        return self.response(self.start_request(method, params), timeout)

    def wait_event(self, name, timeout=30):
        deadline = time.monotonic() + timeout
        while True:
            for i, event in enumerate(self.events):
                if event.get('event', event.get('method')) == name:
                    return self.events.pop(i)
            self.pump(max(.01, deadline - time.monotonic()))

    def close(self):
        if self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait()
        self.trace.close()
        self.stderr.close()


def initialize(peer, folder):
    return peer.request('initialize', {
        'processId': os.getpid(), 'rootUri': folder.as_uri(),
        'workspaceFolders': [{'uri': folder.as_uri(), 'name': folder.name}],
        'capabilities': {'workspace': {'workspaceFolders': True, 'configuration': True,
                                     'didChangeWatchedFiles': {'dynamicRegistration': True}},
                         'textDocument': {'completion': {'completionItem': {'snippetSupport': True}},
                                          'hover': {'contentFormat': ['markdown', 'plaintext']}}},
    })


def open_document(peer, path, language, content=None):
    peer.notify('textDocument/didOpen', {'textDocument': {
        'uri': path.as_uri(), 'languageId': language, 'version': 1,
        'text': path.read_text() if content is None else content}})


def position(text, needle, delta=0):
    offset = text.index(needle) + len(needle) + delta
    before = text[:offset]
    return {'line': before.count('\n'), 'character': len(before.rsplit('\n', 1)[-1])}
