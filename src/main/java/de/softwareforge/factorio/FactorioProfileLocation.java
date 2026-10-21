package de.softwareforge.factorio;

import java.io.IOException;
import java.nio.file.*;
import java.util.regex.Pattern;

/** Remember the actual snapshot directory, with discovery for a project's first import. */
final class FactorioProfileLocation {
    static Path find(String remembered, Path parent, String name) {
        try {
            if (remembered != null && !remembered.isBlank()) {
                Path directory = Path.of(remembered);
                if (Files.isDirectory(directory)) return directory;
            }
        } catch (InvalidPathException ignored) { /* Fall back if stored state is no longer usable. */ }
        // Factorio prereleases can use separate data roots, e.g. factorio_21.
        // Prefer the most recent recording when several installations exist.
        var names = Pattern.compile(Pattern.quote(name) + "(?:_[0-9]+)?", Pattern.CASE_INSENSITIVE);
        Path newest = null;
        long newestTime = Long.MIN_VALUE;
        try (var roots = Files.newDirectoryStream(parent)) {
            for (Path root : roots) {
                if (!names.matcher(root.getFileName().toString()).matches()) continue;
                Path directory = root.resolve("script-output");
                if (!Files.isDirectory(directory)) continue;
                if (newest == null) newest = directory;
                try (var profiles = Files.newDirectoryStream(directory, "*.cpuprofile")) {
                    for (Path profile : profiles) {
                        try {
                            if (!Files.isRegularFile(profile)) continue;
                            long modified = Files.getLastModifiedTime(profile).toMillis();
                            if (modified > newestTime) { newest = directory; newestTime = modified; }
                        } catch (IOException ignored) { /* A recording may have been moved. */ }
                    }
                } catch (IOException ignored) { /* Try another installation. */ }
            }
        } catch (IOException ignored) { /* Use the file chooser's normal default. */ }
        return newest;
    }
}
