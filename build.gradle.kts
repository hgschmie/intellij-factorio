plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.6.0"
}
group = "de.softwareforge.factorio"
version = "0.1.0-dev"
repositories { mavenCentral(); intellijPlatform { defaultRepositories() } }
dependencies {
    intellijPlatform {
        local(providers.gradleProperty("ideaPath").get())
        localPlugin(providers.gradleProperty("lsp4ijPath").get())
        localPlugin(providers.gradleProperty("emmyPath").get())
        instrumentationTools()
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
val toolkitDir = providers.gradleProperty("toolkitPath").orElse("../vscode-factoriomod-debug")
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
