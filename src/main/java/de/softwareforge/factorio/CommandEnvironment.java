package de.softwareforge.factorio;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.util.EnvironmentUtil;
import java.nio.file.Path;
import java.util.*;

/** Toolkit commands and release previews share IntelliJ's shell environment. */
final class CommandEnvironment {
    private CommandEnvironment() {}
    // Run under Node, just like FMTK, so diagnostics sees its actual inherited PATH.
    static final String TOOL_PATHS = """
        const fs = require('fs'), path = require('path');
        const directories = (process.env.PATH || '').split(path.delimiter);
        const suffixes = process.platform === 'win32'
            ? ['', ...(process.env.PATHEXT || '.EXE;.COM;.BAT;.CMD').split(';')] : [''];
        for (const tool of ['git', 'gpg']) {
            let found;
            for (const directory of directories) {
                for (const suffix of suffixes) {
                    const file = path.resolve(directory, tool + suffix);
                    try {
                        fs.accessSync(file, fs.constants.X_OK);
                        if (fs.statSync(file).isFile()) { found = file; break; }
                    } catch {}
                }
                if (found) break;
            }
            console.log(tool + ': ' + (found || 'not found on Command PATH'));
        }
        """;
    static Map<String,String> environment(String pathOverride) {
        var env = new HashMap<>(EnvironmentUtil.getEnvironmentMap());
        if (!pathOverride.isBlank()) {
            env.keySet().removeIf(key -> key.equalsIgnoreCase("PATH"));
            env.put("PATH", pathOverride.trim());
        }
        return env;
    }
    static GeneralCommandLine command(List<String> command, Path cwd, Map<String,String> env) {
        return new GeneralCommandLine(command).withWorkDirectory(cwd.toFile())
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.NONE)
            .withEnvironment(env).withCharset(java.nio.charset.StandardCharsets.UTF_8);
    }
}
