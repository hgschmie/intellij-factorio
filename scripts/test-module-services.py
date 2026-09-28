"""Two externally located mod roots; uses only isolated workspace fixtures."""
import json, time, os
from pathlib import Path
from protocol import ROOT, Peer, initialize, open_document, position

work = ROOT / 'modules'
project = work / 'project'
a = work / 'external-one' / 'mod-a'
b = work / 'external-two' / 'mod-b'
for p in [project, a, b, ROOT/'evidence', ROOT/'tmp']:
    p.mkdir(parents=True, exist_ok=True)
for path,name,number in [(a,'mod-a',11),(b,'mod-b',22)]:
    (path/'info.json').write_text(json.dumps(dict(name=name,version='0.1.0',factorio_version='2.1')))
    (path/'helper.lua').write_text(f'return {{marker_{number} = {number}}}\n')
    (path/'control.lua').write_text('local helper = require("helper")\nlocal other = require("__mod-b__/helper")\nlocal test = helper.\nlocal cross = other.\nlocal api = game.\nlocal locale = {"'+name+'."}\n')
    (path/'locale/en').mkdir(parents=True,exist_ok=True)
    (path/'locale/en/probe.cfg').write_text(f'[{name}]\nready=Ready\n')
workspace = Path(__file__).resolve().parents[2]
api = workspace/'dev/fixtures/plugin/api/factorio/library'
config={'runtime':{'version':'Lua 5.2','requirePattern':['?.lua']},'workspace':{'library':[str(api),str(a.parent),str(b.parent)],'workspaceRoots':[str(a),str(b)],'moduleMap':[{'pattern':'^mod-a[.](.*)$','replace':'__mod-a__.$1'},{'pattern':'^mod-b[.](.*)$','replace':'__mod-b__.$1'}]}}
config=json.loads(os.environ['EMMY_TEST_CONFIG']) if 'EMMY_TEST_CONFIG' in os.environ else config
(project/'.emmyrc.json').write_text(json.dumps(config))
emmy = Peer([os.environ.get('EMMY_LS',str(workspace/'upstream/Intellij-EmmyLua2/build/prepared/IntelliJ-EmmyLua2/server/darwin-arm64/emmylua_ls'))], 'modules-emmy', project)
fmtk = Peer(['/opt/homebrew/bin/node',str(workspace/'intellij-factorio/build/toolkit/fmtk/fmtk-cli.js'),'lsp','--stdio'],'modules-fmtk',project)
results={}
try:
    initialize(emmy,project);emmy.notify('initialized')
    fmtk.request('initialize',{'processId':None,'rootUri':project.as_uri(),'workspaceFolders':[{'uri':p.as_uri(),'name':p.name} for p in [a,b]],'capabilities':{'workspace':{'workspaceFolders':True,'didChangeWatchedFiles':{'dynamicRegistration':True}},'textDocument':{'completion':{'completionItem':{'snippetSupport':True}}}}});fmtk.notify('initialized')
    for path in [a,b]:
        for peer in [emmy,fmtk]:open_document(peer,path/'control.lua','lua')
    time.sleep(3)
    for path in [a,b]:
        file=path/'control.lua';text=file.read_text()
        for name,peer,needle in [('helper',emmy,'require("helper'),('crossmod',emmy,'__mod-b__/helper')]:
            results[path.name+'-'+name]=peer.request('textDocument/definition',{'textDocument':{'uri':file.as_uri()},'position':position(text,needle,-2)})
        for name,peer,needle in [('completion',emmy,'local test = helper.'),('cross-completion',emmy,'local cross = other.'),('locale',fmtk,'local locale = {"'+path.name+'.')]:
            response=peer.request('textDocument/completion',{'textDocument':{'uri':file.as_uri()},'position':position(text,needle),'context':{'triggerKind':1}})
            results[path.name+'-'+name]=[i['label'] for i in (response.get('items',[]) if isinstance(response,dict) else response or [])]
    # Each external root must receive disk changes, including deletion.
    for path in [a,b]:
        locale=path/'locale/en/update.cfg'
        locale.write_text(f'[{path.name}]\nadded=Added\n')
        fmtk.notify('workspace/didChangeWatchedFiles',{'changes':[{'uri':locale.as_uri(),'type':1}]})
        time.sleep(.5)
        file=path/'control.lua'
        params={'textDocument':{'uri':file.as_uri()},'position':position(file.read_text(),'local locale = {"'+path.name+'.'),'context':{'triggerKind':1}}
        response=fmtk.request('textDocument/completion',params)
        results[path.name+'-locale-created']=path.name+'.added' in json.dumps(response)
        locale.unlink()
        fmtk.notify('workspace/didChangeWatchedFiles',{'changes':[{'uri':locale.as_uri(),'type':3}]})
        time.sleep(.5)
        response=fmtk.request('textDocument/completion',params)
        results[path.name+'-locale-deleted']=path.name+'.added' not in json.dumps(response)
    results['checks']={
        **{name: results[name] for name in results if name.endswith(('-locale-created','-locale-deleted'))},
        'a-local':'mod-a/helper.lua' in json.dumps(results['mod-a-helper']),
        'b-local':'mod-b/helper.lua' in json.dumps(results['mod-b-helper']),
        'crossmod':'mod-b/helper.lua' in json.dumps(results['mod-a-crossmod']),
        'cross-types':'marker_22' in results['mod-a-cross-completion'],
        'a-types':'marker_11' in results['mod-a-completion'],
        'b-types':'marker_22' in results['mod-b-completion'],
        'a-locale':'mod-a.ready' in results['mod-a-locale'],
        'b-locale':'mod-b.ready' in results['mod-b-locale'],
    }
finally:
    emmy.close();fmtk.close()
    (ROOT/'evidence/modules-results.json').write_text(json.dumps(results,indent=2))
print(json.dumps(results,indent=2))
assert all(results['checks'].values()), results['checks']
