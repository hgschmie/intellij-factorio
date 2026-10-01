import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import groovy.json.JsonSlurper
import java.util.zip.ZipFile
plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jetbrains.intellij.platform.grammarkit") version "2.18.1"
}
group = "de.softwareforge.factorio"
version = "0.4.2-dev"
repositories { mavenCentral(); intellijPlatform { defaultRepositories() } }
// Defaults follow the workspace layout; every external location can be overridden with -P.
val workspaceDir = projectDir.parentFile
val userHome = providers.environmentVariable("HOME").orElse(System.getProperty("user.home"))
val defaultIdea = listOf(file("${userHome.get()}/Applications/IntelliJ IDEA.app"), file("/Applications/IntelliJ IDEA.app"))
    .firstOrNull { it.isDirectory } ?: file("${userHome.get()}/Applications/IntelliJ IDEA.app")
val ideaDir = file(providers.gradleProperty("ideaPath").orElse(providers.environmentVariable("FMTK_IDEA_PATH")).orElse(defaultIdea.path).get())
val lsp4ijDir = file(providers.gradleProperty("lsp4ijPath").orElse("../dev/plugins/lsp4ij").get())
val emmyDir = file(providers.gradleProperty("emmyPath").orElse("../upstream/Intellij-EmmyLua2/build/prepared/IntelliJ-EmmyLua2").get())
val toolkitDir = file(providers.gradleProperty("toolkitPath").orElse("../upstream/vscode-factoriomod-debug").get())
val npmCommand = providers.gradleProperty("npmExecutable").orElse("npm")
val npmCache = workspaceDir.resolve("dev/cache/npm")
val ideaContents = if (ideaDir.resolve("Contents").isDirectory) ideaDir.resolve("Contents") else ideaDir

// Do not resolve external IDE dependencies just to run clean/help/tasks.
dependencies {
    intellijPlatform {
        if (ideaDir.isDirectory) local(ideaDir)
        if (lsp4ijDir.isDirectory) localPlugin(lsp4ijDir)
        if (emmyDir.isDirectory) localPlugin(emmyDir)
        if (ideaContents.resolve("plugins/json").isDirectory) localPlugin(ideaContents.resolve("plugins/json"))
        if (ideaContents.resolve("plugins/java").isDirectory) localPlugin(ideaContents.resolve("plugins/java"))
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.code.gson:gson:2.11.0")
}
java { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
intellijPlatform {
    pluginConfiguration {
        id = "de.softwareforge.factorio"
        name = "Factorio Modding Tool Kit"
        version = project.version.toString()
        ideaVersion { sinceBuild = "262"; untilBuild = "262.*" }
        vendor { name = "Softwareforge" }
    }
    buildSearchableOptions = false
}
val verifyIdeDependencies = tasks.register("verifyIdeDependencies") {
    group = "verification"
    description = "Check the prepared IDE/plugin dependencies and their pins."
    val lspPin = file("lsp4ij.lock")
    val emmyPin = file("emmy-analyzer.lock")
    doLast {
        check(ideaDir.isDirectory) { "IDE SDK missing: $ideaDir. Set -PideaPath=/path/to/IDE." }
        check(lsp4ijDir.isDirectory) { "LSP4IJ missing: $lsp4ijDir. Run bash scripts/build-lsp4ij.sh or set -Plsp4ijPath." }
        check(emmyDir.isDirectory) { "EmmyLua2 missing: $emmyDir. Run its scripts/build-module-patch.sh or set -PemmyPath." }
        fun checkPin(expected: File, actual: File) {
            check(actual.isFile && actual.readText().trim() == expected.readText().trim()) {
                "Dependency pin mismatch: $actual must match $expected. Prepare the pinned dependency again."
            }
        }
        checkPin(lspPin, lsp4ijDir.resolve("artifact.lock"))
        checkPin(emmyPin, emmyDir.resolve("server/darwin-arm64/analyzer.lock"))
        check(emmyDir.resolve("lib").listFiles().orEmpty().filter { it.extension == "jar" }.any { jar ->
            ZipFile(jar).use { it.getEntry("com/cppcxy/ide/lsp/EmmyLuaServerProvider.class") != null }
        }) { "EmmyLua2 is missing the file-routing hook. Rebuild work/patched-build." }
        check(emmyDir.resolve("lib").listFiles().orEmpty().filter { it.extension == "jar" }.any { jar ->
            ZipFile(jar).use { it.getEntry("com/tang/intellij/lua/lang/LuaLanguageLevelProvider.class") != null }
        }) { "EmmyLua2 is missing the language-level hook. Install/prepare patched build 121 or later." }
    }
}
val toolkitRevision = providers.exec {
    workingDir(toolkitDir)
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.map { it.trim() }
val verifyToolkit = tasks.register("verifyToolkit") {
    group = "verification"
    description = "Check the toolkit checkout against toolkit.lock."
    val pin = file("toolkit.lock")
    doLast {
        check(toolkitDir.resolve("package-lock.json").isFile) { "Toolkit missing: $toolkitDir. Set -PtoolkitPath to the pinned checkout." }
        check(toolkitRevision.get() == pin.readText().trim()) { "Toolkit revision differs from toolkit.lock; review the pin before building." }
    }
}
val installToolkitDependencies = tasks.register<Exec>("installToolkitDependencies") {
    group = "build setup"
    description = "Install the toolkit's locked npm dependencies when inputs change."
    dependsOn(verifyToolkit)
    workingDir(toolkitDir)
    commandLine(npmCommand.get(), "ci", "--no-audit", "--no-fund")
    environment("npm_config_cache", npmCache)
    inputs.files(toolkitDir.resolve("package.json"), toolkitDir.resolve("package-lock.json"))
    inputs.dir(toolkitDir.resolve("patches"))
    inputs.property("npmExecutable", npmCommand)
    outputs.dir(toolkitDir.resolve("node_modules"))
}
val buildToolkit = tasks.register<Exec>("buildToolkit") {
    group = "build"
    description = "Build the pinned Node CLI and language server bundles."
    dependsOn(installToolkitDependencies)
    workingDir(toolkitDir)
    commandLine(npmCommand.get(), "run", "esbuild")
    environment("npm_config_cache", npmCache)
    inputs.files(fileTree(toolkitDir) {
        include("src/**", "luals-addon/**", "language/**", "schema/**", "images/**",
            "build.ts", "tsconfig.json", "package.json", "package-lock.json", "patches/**")
    })
    inputs.property("revision", toolkitRevision)
    outputs.dir(toolkitDir.resolve("dist"))
}
val generateToolkitNotices = tasks.register("generateToolkitNotices") {
    group = "build"
    description = "Collect third-party licenses from the locked npm installation."
    dependsOn(installToolkitDependencies)
    val lock = toolkitDir.resolve("package-lock.json")
    val modules = toolkitDir.resolve("node_modules")
    val output = layout.buildDirectory.file("notices/THIRD-PARTY-NOTICES.txt")
    inputs.file(lock)
    inputs.dir(modules)
    outputs.file(output)
    doLast {
        val packages = (JsonSlurper().parse(lock) as Map<*, *>)["packages"] as Map<*, *>
        val text = StringBuilder("Third-party notices for the bundled FMTK CLI\nThis inventory includes build-time dependencies as well as runtime dependencies.\n")
        packages.entries.filter { it.key != "" }.sortedBy { it.key.toString() }.forEach { (name, value) ->
            val meta = value as Map<*, *>
            text.append("\n--- $name ${meta["version"] ?: ""} (${meta["license"] ?: "see license text"}) ---\n")
            toolkitDir.resolve(name.toString()).listFiles().orEmpty().sortedBy { it.name }.filter { path ->
                path.isFile && listOf("license", "licence", "copying", "notice").any { path.name.lowercase().startsWith(it) }
            }.forEach { text.append(it.readText()).append('\n') }
        }
        output.get().asFile.apply { parentFile.mkdirs(); writeText(text.toString()) }
    }
}
val toolkitMetadata = tasks.register("toolkitMetadata") {
    dependsOn(verifyToolkit)
    val output = layout.buildDirectory.dir("toolkit-metadata")
    inputs.property("revision", toolkitRevision)
    outputs.dir(output)
    doLast {
        output.get().asFile.apply {
            mkdirs()
            resolve("package.json").writeText("""{"type":"module"}""")
            resolve("BUILD.txt").writeText(toolkitRevision.get() + "\n")
        }
    }
}
val bundleToolkit = tasks.register<Sync>("bundleToolkit") {
    dependsOn(buildToolkit, generateToolkitNotices)
    from(toolkitDir.resolve("dist")) { include("*.js"); exclude("fmtk-vscode.js", "*Webview.js") }
    from(toolkitDir.resolve("LICENSE.txt"))
    from(generateToolkitNotices)
    from(toolkitMetadata)
    into(layout.buildDirectory.dir("toolkit/fmtk"))
}
tasks.withType<JavaCompile>().configureEach { dependsOn(verifyIdeDependencies) }
tasks.prepareSandbox { dependsOn(verifyIdeDependencies, bundleToolkit); from(layout.buildDirectory.dir("toolkit")) { into("${rootProject.name}") } }
tasks.test { useJUnitPlatform() }
// IDEA 262 moved the boot filesystem provider into a separate jar.
tasks.test { jvmArgs("-Xbootclasspath/a:" + ideaContents.resolve("lib/nio-fs.jar")) }
tasks.verifyPlugin { ides.setFrom(ideaContents) }
dependencies { intellijPlatform { pluginVerifier() } }

// Real LSP4IJ lifecycle tests run separately from the fast filesystem/configuration suite.
if (providers.gradleProperty("platformTests").isPresent) {
    dependencies {
        intellijPlatform { testFramework(TestFrameworkType.Platform) }
        testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
        testImplementation("junit:junit:4.13.2")
    }
    sourceSets.test { java.setSrcDirs(listOf("src/ideTest/java")) }
    tasks.test {
        dependsOn(bundleToolkit)
        systemProperty("idea.load.plugins.id", "de.softwareforge.factorio,com.cppcxy.Intellij-EmmyLua,com.redhat.devtools.lsp4ij")
        systemProperty("factorio.test.work", layout.buildDirectory.dir("test-work/platform").get().asFile.absolutePath)
        systemProperty("factorio.test.workspace", projectDir.parentFile.absolutePath)
        systemProperty("factorio.test.apiDocs", providers.gradleProperty("apiDocs").orElse("/Applications/factorio.app/Contents/doc-html").get())
        systemProperty("factorio.test.realMods", providers.gradleProperty("realMods").orElse("").get())
    }
}

// Verify against the exact prepared dependencies used to compile and test.
val prepareVerifierDependencies = tasks.register<Sync>("prepareVerifierDependencies") {
    into(layout.buildDirectory.dir("verifier-home/loaded-plugins"))
    from(emmyDir) { into("IntelliJ-EmmyLua2") }
    from(lsp4ijDir) { into("lsp4ij") }
}
tasks.verifyPlugin {
    dependsOn(prepareVerifierDependencies)
    offline.set(true)
    systemProperty("plugin.verifier.home.dir", layout.buildDirectory.dir("verifier-home").get().asFile.absolutePath)
}

// Generate the highlighting lexer as part of normal Java compilation.
tasks.generateLexer {
    sourceFile.set(file("src/main/grammars/FactorioLocale.flex"))
    targetRootOutputDir.set(layout.buildDirectory.dir("generated/sources/locale"))
    purgeOldFiles.set(true)
}
sourceSets.main { java.srcDir(layout.buildDirectory.dir("generated/sources/locale")) }
tasks.compileJava { dependsOn(tasks.generateLexer) }

// Give the pinned schemas stable IDs so IntelliJ resolves bundled references through its registry.
val bundleModSchemas = tasks.register("bundleModSchemas") {
    dependsOn(verifyToolkit)
    val mod = toolkitDir.resolve("schema/modinfo.json")
    val base = toolkitDir.resolve("schema/datainfo.json")
    val output = layout.buildDirectory.dir("generated/resources/mod-schemas/schemas/factorio")
    inputs.files(mod, base)
    outputs.dir(output)
    doLast {
        val prefix = "urn:de.softwareforge.factorio:schema:"
        @Suppress("UNCHECKED_CAST")
        val schema = JsonSlurper().parse(mod) as MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val allOf = schema["allOf"] as MutableList<Any?>
        check((allOf.first() as Map<*, *>)["\$ref"] == "datainfo.json") { "Review the changed toolkit schema reference." }
        schema["\$id"] = prefix + "modinfo"
        allOf[0] = mapOf("\$ref" to prefix + "datainfo")
        @Suppress("UNCHECKED_CAST")
        val data = JsonSlurper().parse(base) as MutableMap<String, Any?>
        data["\$id"] = prefix + "datainfo"
        output.get().asFile.apply {
            mkdirs()
            resolve("modinfo.json").writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(schema)))
            resolve("datainfo.json").writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(data)))
        }
    }
}
sourceSets.main { resources.srcDir(layout.buildDirectory.dir("generated/resources/mod-schemas")) }
tasks.processResources { dependsOn(bundleModSchemas) }
