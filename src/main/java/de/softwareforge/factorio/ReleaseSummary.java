package de.softwareforge.factorio;

import com.google.gson.JsonObject;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Read-only release preview; publish semantics remain owned by the CLI. */
public final class ReleaseSummary {
    private ReleaseSummary() {}
    public static String create(Path mod,String action,String configPath,String commandPath) throws Exception {
        JsonObject info=PathsAndMods.read(mod.resolve("info.json"));
        String branch=git(mod,commandPath,"branch","--show-current");
        String remote=git(mod,commandPath,"config","branch."+branch+".pushRemote");
        if(remote.isBlank())remote=git(mod,commandPath,"config","remote.pushDefault");
        if(remote.isBlank())remote=git(mod,commandPath,"config","branch."+branch+".remote");
        String url=remote.isBlank()?"No push remote":redactUrl(git(mod,commandPath,"remote","get-url",remote));
        String status=git(mod,commandPath,"status","--porcelain");
        JsonObject config=new JsonObject();
        Path configFile=configPath.isBlank()?Path.of(System.getProperty("user.home"),".fmtk/config.json"):Path.of(configPath);
        if(Files.isRegularFile(configFile))config=PathsAndMods.read(configFile);
        String tag=config.has("package.tagName")?config.get("package.tagName").getAsString():"$VERSION";
        tag=tag.replace("$VERSION",info.get("version").getAsString()).replace("$MODNAME",info.get("name").getAsString());
        return "Action: "+action+"\nMod: "+info.get("name").getAsString()+"\nVersion: "+info.get("version").getAsString()+"\nDirectory: "+mod+
            "\nPortal: https://mods.factorio.com\nGit branch: "+branch+"\nPush destination: "+remote+" "+url+"\nRelease tag: "+tag+
            "\nWorking tree: "+(status.isBlank()?"clean or no Git repository":"has changes; publish will refuse")+
            "\nCommit author: "+(config.has("package.autoCommitAuthor")?config.get("package.autoCommitAuthor").getAsString():"compilatron <compilatron@justarandomgeek.com>")+
            "\nCommitter and signing: Git configuration"+
            "\nPackage options and hooks:\n"+info.get("package")+
            "\nPublish performs configured hooks, commits/tags, portal upload/details, version increment and Git push unless disabled by package options.\nCancellation does not undo completed steps.";
    }
    public static String redactUrl(String url) { return url.replaceAll("(https?://)[^/@]+@","$1[redacted]@").replaceAll("([?&](?:token|key|access_token)=)[^&]+","$1[redacted]"); }
    private static String git(Path cwd,String commandPath,String... args) throws Exception {
        var command=new ArrayList<String>();command.add("git");command.addAll(List.of(args));
        Process p=CommandEnvironment.command(command,cwd,CommandEnvironment.environment(commandPath)).createProcess();
        try {
            if(!p.waitFor(5,TimeUnit.SECONDS))throw new IllegalStateException("Git preflight timed out");
            return p.exitValue()==0?new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8).trim():"";
        }finally { if(p.isAlive())p.destroyForcibly(); }
    }
}
