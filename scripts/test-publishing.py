#!/usr/bin/env python3
"""Run the bundled CLI against disposable repos and a loopback mod portal."""
import http.server, threading, tempfile, pathlib, json, os, subprocess, sys, zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
WORKSPACE=ROOT.parent
CLI=pathlib.Path(sys.argv[1]).resolve() if len(sys.argv)>1 else WORKSPACE/'vscode-factoriomod-debug/dist/fmtk-cli.js'
requests=[]
class Portal(http.server.BaseHTTPRequestHandler):
    def log_message(self,*args): pass
    def do_GET(self):
        self.send_response(200); self.send_header('Content-Type','application/json'); self.end_headers()
        self.wfile.write(json.dumps({'name':'softwareforge-test','images':[], 'description':'', 'faq':''}).encode())
    def do_POST(self):
        body=self.rfile.read(int(self.headers.get('Content-Length',0)))
        requests.append((self.path,body))
        if self.headers.get('Authorization')!='Bearer fake-test-key':
            self.send_response(401); result={'error':'InvalidApiKey','message':'Test key rejected'}
        elif self.path.endswith('/init_upload'):
            self.send_response(200); result={'upload_url':f'http://127.0.0.1:{self.server.server_port}/upload'}
        else:
            self.send_response(200); result={'success':True}
        self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(json.dumps(result).encode())
portal=http.server.ThreadingHTTPServer(('127.0.0.1',0),Portal)
threading.Thread(target=portal.serve_forever,daemon=True).start()
base=WORKSPACE/'spike/plugin-publishing-tests';base.mkdir(exist_ok=True)
test=pathlib.Path(tempfile.mkdtemp(prefix='run-',dir=base))
env=os.environ.copy();env.update(FACTORIO_UPLOAD_API_KEY='fake-test-key',FACTORIO_TEST_PORTAL=f'http://127.0.0.1:{portal.server_port}',TMPDIR=str(test),FMTK_CONFIG=str(test/'config.json'))
(test/'config.json').write_text('{}')
def call(args,cwd,ok=True,custom=None):
    p=subprocess.run(args,cwd=cwd,env=custom or env,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=45)
    with (test/'commands.log').open('a') as f: f.write(f'{args}\nexit={p.returncode}\n{p.stdout}\n')
    assert (p.returncode==0)==ok,p.stdout
    return p.stdout
def git(cwd,*args):return call(['git',*args],cwd)
def cli(cwd,*args,ok=True,custom=None): return call(['node','--import',str(ROOT/'scripts/portal-interceptor.mjs'),str(CLI),*args],cwd,ok,custom)
mod=test/'mod';mod.mkdir()
info={'name':'softwareforge-test','version':'1.0.0','title':'Test','author':'Test','factorio_version':'2.0','package':{'git_publish_branch':'main','ignore':['.git/**'],'scripts':{'compile':'node -e "require(\'fs\').writeFileSync(\'compiled.lua\',\'return 1\')"'}}}
(mod/'info.json').write_text(json.dumps(info,indent=2));(mod/'control.lua').write_text('local x=1\n');(mod/'changelog.txt').write_text('---------------------------------------------------------------------------------------------------\nVersion: 1.0.0\n  Features:\n    - Test release\n')
(mod/'readme.md').write_text('# Test mod\n\nTest description.\n')
cli(mod,'package');archive=mod/'softwareforge-test_1.0.0.zip'
with zipfile.ZipFile(archive) as z:
    assert 'softwareforge-test_1.0.0/compiled.lua' in z.namelist()
    assert all('/.git/' not in name for name in z.namelist())
archive.unlink();(mod/'compiled.lua').unlink()
# Avoid generated untracked artifacts during the full publish workflow.
info['package']['scripts']={};(mod/'info.json').write_text(json.dumps(info,indent=2))
git(mod,'init','-b','main');git(mod,'config','user.name','Publishing Test');git(mod,'config','user.email','test@example.invalid');git(mod,'config','commit.gpgsign','false');git(mod,'config','tag.gpgsign','false');git(mod,'add','.');git(mod,'commit','-m','fixture')
remote=test/'remote.git';git(test,'init','--bare',str(remote));git(mod,'remote','add','origin',str(remote));git(mod,'push','-u','origin','main')
(mod/'dirty').write_text('dirty');cli(mod,'publish',ok=False);(mod/'dirty').unlink();assert not requests
# Wrong branch is rejected before portal traffic.
git(mod,'switch','-c','wrong');cli(mod,'publish',ok=False);git(mod,'switch','main');assert not requests
cli(mod,'publish')
assert json.loads((mod/'info.json').read_text())['version']=='1.0.1'
assert 'Date:' in (mod/'changelog.txt').read_text()
assert git(mod,'tag','--list').strip()=='1.0.0'
assert git(mod,'rev-parse','HEAD').strip()==git(test,'--git-dir='+str(remote),'rev-parse','main').strip()
assert [r[0] for r in requests]==['/api/v2/mods/releases/init_upload','/upload']
cli(mod,'details');assert any('edit_details' in path for path,_ in requests)
cli(mod,'package');archive=mod/'softwareforge-test_1.0.1.zip'
bad=env.copy();bad['FACTORIO_UPLOAD_API_KEY']='invalid';cli(mod,'upload',str(archive),ok=False,custom=bad)
cli(mod,'upload',str(archive));cli(mod,'datestamp');cli(mod,'version');assert json.loads((mod/'info.json').read_text())['version']=='1.0.2'
# Hook failure must fail the operation.
info=json.loads((mod/'info.json').read_text());info['package']['scripts']={'compile':'node -e "process.exit(7)"'};(mod/'info.json').write_text(json.dumps(info));cli(mod,'package',ok=False)
# Connection failure must fail without silently retrying a release.
closed=env.copy();closed['FACTORIO_TEST_PORTAL']='http://127.0.0.1:1'
cli(mod,'upload',str(archive),ok=False,custom=closed)
# A cancellation can leave completed local work; terminate the CLI process group.
import signal,time
info['package']['scripts']={'wait':'node -e "setTimeout(()=>{},60000)"'}
(mod/'info.json').write_text(json.dumps(info))
p=subprocess.Popen(['node',str(CLI),'run','wait'],cwd=mod,env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,start_new_session=True)
time.sleep(.5);os.killpg(p.pid,signal.SIGTERM);p.wait(timeout=5)
assert p.returncode != 0
(test/'results.json').write_text(json.dumps({'status':'pass','requests':[path for path,_ in requests],'cli':str(CLI)},indent=2))
print('Publishing/package checks passed:',test)
portal.shutdown()
