package de.softwareforge.factorio;

import com.google.gson.JsonObject;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Builds read-only confirmations for the selected Mod Portal operation. */
public final class ReleaseSummary {
    private ReleaseSummary() {}

    /** Presents the operation's scope and the button that authorizes it. */
    public record Preview(String title, String acceptText, String message) {}

    /**
     * Describes the exact CLI operation before credentials are requested or the command runs.
     * Only a full publish inspects release configuration and Git state.
     * @param mod selected mod directory
     * @param arguments CLI action and any selected ZIP argument
     * @param configPath optional FMTK configuration file
     * @param commandPath executable search path for the publish Git preview
     * @return action-specific confirmation text and labels
     */
    public static Preview create(Path mod,List<String> arguments,String configPath,String commandPath) throws Exception {
        String action=arguments.getFirst();
        JsonObject info=PathsAndMods.read(mod.resolve("info.json"));
        String identity="Mod: "+info.get("name").getAsString()+"\nDirectory: "+mod+"\nPortal: https://mods.factorio.com";
        if(action.equals("details")) {
            JsonObject options=info.has("package") && info.get("package").isJsonObject()
                ? info.getAsJsonObject("package") : new JsonObject();
            String readme=option(options,"readme","readme.md");
            String faq=option(options,"faq","faq.md");
            String images="Local Markdown images may be uploaded according to the package's Markdown settings.";
            if(options.has("gallery") && !options.get("gallery").isJsonNull()) {
                boolean prune=!options.has("prune_gallery") || !options.get("prune_gallery").isJsonPrimitive()
                    || !options.get("prune_gallery").getAsJsonPrimitive().isBoolean()
                    || options.get("prune_gallery").getAsBoolean();
                images+="\nGallery patterns: "+options.get("gallery")
                    +"\nThe portal gallery will be reordered"+(prune?" and images outside the synchronized set removed.":"; existing images will be retained.");
            }
            return new Preview("Synchronize Mod Portal Details","Synchronize Details",identity
                +"\n\nUpdate the title, homepage and summary from info.json."
                +"\nDescription source (if present): "+readme+"\nFAQ source (if present): "+faq
                +"\n"+images
                +"\n\nNo release ZIP is uploaded. The local version and Git repository are unchanged.");
        }
        if(action.equals("upload")) {
            if(arguments.size()!=2)throw new IllegalArgumentException("Select the release ZIP to upload");
            return new Preview("Upload Mod ZIP","Upload ZIP",identity+"\n\nRelease ZIP: "+arguments.get(1)
                +"\nUpload this existing archive as a mod release."
                +"\nThe local version and Git repository are unchanged. Portal details are not synchronized.");
        }
        if(!action.equals("publish"))throw new IllegalArgumentException("No portal confirmation for "+action);
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
        return new Preview("Publish Mod","Publish", "Action: "+action+"\nMod: "+info.get("name").getAsString()+"\nVersion: "+info.get("version").getAsString()+"\nDirectory: "+mod+
            "\nPortal: https://mods.factorio.com\nGit branch: "+branch+"\nPush destination: "+remote+" "+url+"\nRelease tag: "+tag+
            "\nWorking tree: "+(status.isBlank()?"clean or no Git repository":"has changes; publish will refuse")+
            "\nCommit author: "+(config.has("package.autoCommitAuthor")?config.get("package.autoCommitAuthor").getAsString():"compilatron <compilatron@justarandomgeek.com>")+
            "\nCommitter and signing: Git configuration"+
            "\nPackage options and hooks:\n"+info.get("package")+
            "\nPublish performs configured hooks, commits/tags, portal upload/details, version increment and Git push unless disabled by package options.\nCancellation does not undo completed steps.");
    }
    private static String option(JsonObject options,String name,String fallback) {
        return options.has(name) && !options.get(name).isJsonNull()?options.get(name).getAsString():fallback;
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
