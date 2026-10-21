package de.softwareforge.factorio;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.tang.intellij.lua.psi.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Resolves Factorio's source names without requiring a running DAP connection. */
public final class FactorioProfileSources {
    private static final Pattern MOD = Pattern.compile("^__([^/]+)__/(.+)$");
    public static VirtualFile resolve(Project project, String source) {
        if (project.isDisposed()) return null;
        String location = source.startsWith("@") ? source.substring(1) : source;
        if (location.isBlank() || location.equals("=[C]")) return null;
        try {
            Path path = location.startsWith("file:") ? Path.of(java.net.URI.create(location)) : Path.of(location);
            if (path.isAbsolute()) return LocalFileSystem.getInstance().findFileByNioFile(path.normalize());
        } catch (IllegalArgumentException ignored) { return null; }
        var match = MOD.matcher(location);
        if (!match.matches()) return null;
        String name = match.group(1), relative = match.group(2);
        if (Arrays.asList(relative.split("/")).contains("..")) return null;
        var registry = FactorioModules.get(project);
        for (var mod : registry.mods()) if (mod.name().equals(name)) return find(mod.root(), relative);
        var settings = FactorioSettings.get(project);
        Path executable = Path.of(settings.factorio).toAbsolutePath();
        Path docs = settings.apiDocs.isBlank() ? executable.getParent().getParent().resolve("doc-html") : Path.of(settings.apiDocs);
        var data = find(docs.getParent().resolve("data").resolve(name), relative);
        if (data != null) return data;
        var dependencies = new LinkedHashSet<Path>();
        dependencies.addAll(PathsAndMods.lines(settings.dependencies));
        // Module overrides read IntelliJ's project model. Resolve dependency files
        // afterwards so disk/archive reads do not hold the application read lock.
        ReadAction.run(() -> {
            if (!project.isDisposed()) for (var mod : registry.mods())
                dependencies.addAll(PathsAndMods.lines(registry.dependencies(mod)));
        });
        for (Path dependency : dependencies) {
            if (!dependency.isAbsolute() && project.getBasePath() != null) dependency = Path.of(project.getBasePath()).resolve(dependency);
            var vf = LocalFileSystem.getInstance().findFileByNioFile(dependency.normalize());
            if (vf == null) continue;
            if (vf.isDirectory()) {
                try { if (PathsAndMods.read(dependency.resolve("info.json")).get("name").getAsString().equals(name)) return vf.findFileByRelativePath(relative); }
                catch (Exception ignored) { /* Not a readable mod directory. */ }
            } else if ("zip".equals(vf.getExtension())) {
                var root = JarFileSystem.getInstance().getJarRootForLocalFile(vf);
                if (root != null) for (var folder : root.getChildren()) {
                    var info = folder.findChild("info.json");
                    if (info == null) continue;
                    try {
                        var json = com.google.gson.JsonParser.parseString(new String(info.contentsToByteArray(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                        if (json.get("name").getAsString().equals(name)) return folder.findFileByRelativePath(relative);
                    } catch (Exception ignored) { /* Not a readable mod archive. */ }
                }
            }
        }
        return null;
    }
    private static VirtualFile find(Path root, String relative) {
        return LocalFileSystem.getInstance().findFileByNioFile(root.resolve(relative).normalize());
    }
    /** Capture presentation strings once per import; renderers must not resolve PSI or files. */
    public static Map<FactorioProfile.Frame, String> displayNames(Project project, FactorioProfile profile, Runnable checkCancelled) {
        var sources = new HashMap<String, Map<Integer, String>>();
        var names = new HashMap<FactorioProfile.Frame, String>();
        for (var node : profile.nodes()) {
            checkCancelled.run();
            var frame = node.frame();
            if (!(frame.name().isBlank() || frame.name().equals("(unknown)") || frame.name().equals("(anonymous)"))
                    || frame.nativeCall() || frame.source().isBlank() || frame.line() < 0 || names.containsKey(frame)) continue;
            var declarations = sources.computeIfAbsent(frame.source(), source -> functionNames(project, source, checkCancelled));
            var name = declarations.get(frame.line());
            if (name == null || name.isBlank()) {
                var source = frame.source().replace('\\', '/');
                name = source.substring(source.lastIndexOf('/') + 1) + ":" + (frame.line() + 1);
            }
            names.put(frame, name);
        }
        return Map.copyOf(names);
    }
    private static Map<Integer, String> functionNames(Project project, String source, Runnable checkCancelled) {
        var file = resolve(project, source);
        if (file == null) return Map.of();
        return ReadAction.compute(() -> {
            if (project.isDisposed() || !file.isValid()) return Map.of();
            var psi = PsiManager.getInstance(project).findFile(file);
            if (!(psi instanceof LuaPsiFile)) return Map.of();
            var documents = PsiDocumentManager.getInstance(project);
            var document = documents.getDocument(psi);
            if (document == null || !documents.isCommitted(document)) return Map.of();
            var names = new HashMap<Integer, String>();
            for (var body : PsiTreeUtil.findChildrenOfType(psi, LuaFuncBody.class)) {
                checkCancelled.run();
                var declaration = body.getParent();
                var keyword = declaration.getNode().findChildByType(LuaTypes.FUNCTION);
                if (keyword == null) continue;
                // Comments can belong to the declaration's PSI. Match the function
                // keyword, not the start of its comments or an enclosing function.
                int line = document.getLineNumber(keyword.getStartOffset());
                PsiElement nameElement = switch (declaration) {
                    case LuaLocalFuncDef local -> local.getId();
                    case LuaFuncDef function -> function.getId();
                    case LuaGlobalFuncDef global -> global.getId();
                    case LuaClassMethodDef method -> method.getClassMethodName();
                    default -> null;
                };
                String name = nameElement == null ? "" : nameElement.getText().replaceAll("\\s+", "");
                // A recorded line cannot disambiguate two functions on that line,
                // including anonymous closures next to a named declaration.
                names.merge(line, name, (previous, next) -> "");
            }
            return Map.copyOf(names);
        });
    }
    public static NavigatablePsiElement[] navigatables(Project project, FactorioProfile.Frame frame) {
        return navigatables(project, frame, frame.name());
    }
    public static NavigatablePsiElement[] navigatables(Project project, FactorioProfile.Frame frame, String displayName) {
        var file = resolve(project, frame.source());
        if (file == null || frame.line() < 0) return new NavigatablePsiElement[0];
        // The common profiler calls calcNavigatables on a background worker
        // without a read action. Keep PSI lookup protected at this boundary.
        return ReadAction.compute(() -> {
            if (project.isDisposed() || !file.isValid()) return new NavigatablePsiElement[0];
            var psi = PsiManager.getInstance(project).findFile(file);
            if (psi == null) return new NavigatablePsiElement[0];
            return new NavigatablePsiElement[]{new FakePsiElement() {
                @Override public PsiElement getParent() { return psi; }
                @Override public String getName() { return displayName; }
                @Override public int getTextOffset() {
                    // This callback can run after the lookup's read action ends.
                    return ReadAction.compute(() -> {
                        if (project.isDisposed() || !psi.isValid()) return 0;
                        var document = PsiDocumentManager.getInstance(project).getDocument(psi);
                        return document == null || document.getLineCount() == 0 ? 0
                                : document.getLineStartOffset(Math.min(frame.line(), document.getLineCount() - 1));
                    });
                }
                @Override public boolean canNavigate() { return !project.isDisposed() && file.isValid(); }
                @Override public boolean canNavigateToSource() { return canNavigate(); }
                @Override public void navigate(boolean requestFocus) { new OpenFileDescriptor(project, file, frame.line(), 0).navigate(requestFocus); }
            }};
        });
    }
}
