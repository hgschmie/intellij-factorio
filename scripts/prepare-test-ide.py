#!/usr/bin/env python3
"""Prepare the existing spike's copied fixture and isolated profile; run with the test IDE closed."""
from pathlib import Path
import shutil,zipfile,json,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[2];spike=root/'spike';profile=spike/'plugin-ide';fixture=spike/'plugin-fixture'
for d in ['config/options','system','plugins','log','home','tmp']:(profile/d).mkdir(parents=True,exist_ok=True)
if not fixture.exists():
    shutil.copytree(spike/'fixture',fixture,ignore=shutil.ignore_patterns('.idea','.run','write-data'))
    (fixture/'write-data').mkdir()
    for name in ['config.ini','.emmyrc.json']:
        p=fixture/name;p.write_text(p.read_text().replace(str(spike/'fixture'),str(fixture)))
for source,name in [(spike/'ide/plugins/lsp4ij','lsp4ij'),(spike/'emmylua2-module-build/verified/IntelliJ-EmmyLua2','IntelliJ-EmmyLua2')]:
    if name=='IntelliJ-EmmyLua2':
        assert (source/'server/darwin-arm64/analyzer.lock').read_text().strip()==(root/'intellij-factorio/emmy-analyzer.lock').read_text().strip(), 'Prepare the pinned EmmyLua development build'
    target=profile/'plugins'/name
    if target.exists():shutil.rmtree(target)
    shutil.copytree(source,target)
with zipfile.ZipFile(root/'intellij-factorio/build/distributions/intellij-factorio-0.1.0-dev.zip') as z:z.extractall(profile/'plugins')
(profile/'idea.properties').write_text('\n'.join(f'idea.{k}.path={profile/v}' for k,v in [('config','config'),('system','system'),('plugins','plugins'),('log','log')])+'\nidea.initially.ask.config=false\n')
(profile/'idea.vmoptions').write_text(f'-Xms256m\n-Xmx2048m\n-Djava.io.tmpdir={profile}/tmp\n-Duser.home={profile}/home\n')
# Reuse already-recorded preferences and local license, without logging their contents.
for relative in ['config/idea.key','config/options/ide.general.xml','home/Library/Application Support/JetBrains/consentOptions/accepted']:
    source=spike/'ide'/relative;target=profile/relative
    if source.exists():target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
(profile/'config/options/trusted-paths.xml').write_text(f'<application><component name="Trusted.Paths"><option name="TRUSTED_PROJECT_PATHS"><map><entry key="{fixture}" value="true"/></map></option></component></application>')
(fixture/'.idea/runConfigurations').mkdir(parents=True,exist_ok=True)
workspace=fixture/'.idea/workspace.xml'
if not workspace.exists():workspace.write_text(f'<project version="4"><component name="FactorioToolkit"><option name="enabled" value="true"/><option name="activeMod" value="{fixture}/mods/fmtk-probe"/><option name="dependencies" value="{fixture}/mods/LogisticTrainNetwork"/></component></project>')
x=ET.Element('component',name='ProjectRunConfigurationManager');c=ET.SubElement(x,'configuration',default='false',name='Factorio Plugin Probe',type='SoftwareforgeFactorio',factoryName='Factorio')
launch={'request':'launch','factorioArgs':['--config',str(fixture/'config.ini'),'--mod-directory',str(fixture/'mods'),'--load-game',str(fixture/'probe.zip')],'followSymlinks':True,'hookDebugConsole':True}
for key,value in {'serverId':'softwareforge.factorio.debug','serverName':'Factorio','command':'/Applications/factorio.app/Contents/MacOS/factorio','commandLine':'/Applications/factorio.app/Contents/MacOS/factorio','workingDirectory':str(fixture),'launchConfiguration':json.dumps(launch)}.items():ET.SubElement(c,'option',name=key,value=value)
ET.SubElement(c,'method',v='2');ET.ElementTree(x).write(fixture/'.idea/runConfigurations/Factorio.xml',encoding='unicode')
print('Prepared isolated IDE:',profile)
