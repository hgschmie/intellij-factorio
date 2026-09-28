import org.jetbrains.intellij.platform.gradle.TestFrameworkType
plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.18.1"
}
group = "de.softwareforge.factorio"
version = "0.2.3-dev"
repositories { mavenCentral(); intellijPlatform { defaultRepositories() } }
dependencies {
    intellijPlatform {
        local(providers.gradleProperty("ideaPath").get())
        localPlugin(providers.gradleProperty("lsp4ijPath").get())
        localPlugin(providers.gradleProperty("emmyPath").get())
        localPlugin(file(providers.gradleProperty("ideaPath").get()).resolve("Contents/plugins/java"))
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
val toolkitDir = providers.gradleProperty("toolkitPath").orElse("../upstream/vscode-factoriomod-debug")
val bundleToolkit by tasks.registering(Sync::class) {
    from(file(toolkitDir.get()).resolve("dist")) { include("*.js"); exclude("fmtk-vscode.js", "*Webview.js") }
    from(file(toolkitDir.get()).resolve("LICENSE.txt"))
    from(layout.buildDirectory.dir("notices"))
    into(layout.buildDirectory.dir("toolkit/fmtk"))
    doLast {
        destinationDir.resolve("package.json").writeText("""{"type":"module"}""")
        destinationDir.resolve("BUILD.txt").writeText(providers.exec { commandLine("git", "-C", file(toolkitDir.get()).absolutePath, "rev-parse", "HEAD") }.standardOutput.asText.get())
    }
}
tasks.prepareSandbox { dependsOn(bundleToolkit); from(layout.buildDirectory.dir("toolkit")) { into("${rootProject.name}") } }
tasks.test { useJUnitPlatform() }
// IDEA 262 moved the boot filesystem provider into a separate jar.
tasks.test { jvmArgs("-Xbootclasspath/a:" + file(providers.gradleProperty("ideaPath").get()).resolve("Contents/lib/nio-fs.jar")) }
tasks.verifyPlugin { ides.setFrom(file(providers.gradleProperty("ideaPath").get()).resolve("Contents")) }
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
        systemProperty("idea.load.plugins.id", "de.softwareforge.factorio,com.cppcxy.Intellij-EmmyLua,com.redhat.devtools.lsp4ij")
        systemProperty("factorio.test.work", layout.buildDirectory.dir("test-work/platform").get().asFile.absolutePath)
        systemProperty("factorio.test.workspace", projectDir.parentFile.absolutePath)
        systemProperty("factorio.test.apiDocs", providers.gradleProperty("apiDocs").orElse("/Applications/factorio.app/Contents/doc-html").get())
        systemProperty("factorio.test.realMods", providers.gradleProperty("realMods").orElse("").get())
    }
}

// Verify against the same patched dependencies used to compile and test, not Marketplace releases.
val prepareVerifierDependencies = tasks.register<Sync>("prepareVerifierDependencies") {
    into(layout.buildDirectory.dir("verifier-home/loaded-plugins"))
    from(providers.gradleProperty("emmyPath")) { into("IntelliJ-EmmyLua2") }
    from(providers.gradleProperty("lsp4ijPath")) { into("lsp4ij") }
}
tasks.verifyPlugin {
    dependsOn(prepareVerifierDependencies)
    offline.set(true)
    systemProperty("plugin.verifier.home.dir", layout.buildDirectory.dir("verifier-home").get().asFile.absolutePath)
}
