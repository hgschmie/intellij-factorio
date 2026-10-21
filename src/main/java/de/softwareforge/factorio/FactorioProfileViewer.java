package de.softwareforge.factorio;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;

/** Optional adapter: core recording never links to common-profiler classes. */
public interface FactorioProfileViewer {
    ExtensionPointName<FactorioProfileViewer> EP = ExtensionPointName.create("de.softwareforge.factorio.profileViewer");
    void open(Project project, Path snapshot);
}
