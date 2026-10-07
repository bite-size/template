import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import xyz.jpenilla.runpaper.task.RunServer
import java.net.URI

plugins {
    kotlin("jvm") version "2.4.0"
    id("com.gradleup.shadow") version "9.4.2"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "gg.bitesize"
version = "2.0"

// Minecraft version used by every test server task (runServer, runPurpur, runSpigot)
val testServerVersion = "26.3"

// BiteFoo -> gg.bitesize.foo, so shaded libraries relocate under the plugin's own package
val pluginPackage = "gg.bitesize.${rootProject.name.removePrefix("Bite").lowercase()}"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    // Spigot API is the common surface of Spigot, Paper, and Purpur
    compileOnly("org.spigotmc:spigot-api:$testServerVersion-R0.1-SNAPSHOT")

    // Downloaded at runtime via plugin.yml `libraries`
    compileOnly(kotlin("stdlib"))

    implementation("net.kyori:adventure-text-minimessage:5.2.0")
    implementation("net.kyori:adventure-text-serializer-legacy:5.2.0")
    implementation("net.kyori:adventure-text-serializer-plain:5.2.0")
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    jar {
        archiveClassifier.set("plain")
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("net.kyori", "$pluginPackage.libs.kyori")
        mergeServiceFiles()
        exclude("META-INF/maven/**", "META-INF/versions/*/module-info.class", "module-info.class", "org/jspecify/**")
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()

        val props = mapOf(
            "version" to project.version,
            "kotlinVersion" to project.getKotlinPluginVersion(),
        )
        inputs.properties(props)
        filesMatching(listOf("plugin.yml", "config.yml")) {
            expand(props)
        }
    }

    runServer {
        minecraftVersion(testServerVersion)
        doFirst { acceptEula("run") }
    }
}

kotlin {
    jvmToolchain(25)
}

// ---------------------------------------------------------------------------
// Test servers for every supported platform. Each runs in its own folder:
//   ./gradlew runServer   Paper   -> run/
//   ./gradlew runPurpur   Purpur  -> run-purpur/
//   ./gradlew runSpigot   Spigot  -> run-spigot/ (first run builds Spigot with BuildTools, ~10 min)
// Server jars are cached in .servers/. Pass -PrefreshServers to fetch the latest builds.
// ---------------------------------------------------------------------------

val serverJarsDir: File = file(".servers")
val refreshServers = providers.gradleProperty("refreshServers").isPresent
val purpurJar = serverJarsDir.resolve("purpur-$testServerVersion.jar")
val spigotJar = serverJarsDir.resolve("spigot-$testServerVersion.jar")

fun download(url: String, target: File) {
    target.parentFile.mkdirs()
    URI(url).toURL().openStream().use { input -> target.outputStream().use(input::copyTo) }
}

// These are local test servers for this project, so the EULA is accepted on first run
fun acceptEula(directory: String) {
    file(directory).resolve("eula.txt").apply { parentFile.mkdirs(); if (!exists()) writeText("eula=true\n") }
}

val downloadPurpur by tasks.registering {
    group = "run paper"
    description = "Downloads the latest Purpur $testServerVersion build into .servers/."
    outputs.file(purpurJar)
    onlyIf { refreshServers || !purpurJar.exists() }
    doLast { download("https://api.purpurmc.org/v2/purpur/$testServerVersion/latest/download", purpurJar) }
}

val buildSpigot by tasks.registering(Exec::class) {
    group = "run paper"
    description = "Builds Spigot $testServerVersion with BuildTools into .servers/."
    val workDir = serverJarsDir.resolve("buildtools")
    val launcher = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }

    outputs.file(spigotJar)
    onlyIf { refreshServers || !spigotJar.exists() }
    workingDir(workDir)
    args("-jar", "BuildTools.jar", "--rev", testServerVersion,
        "--output-dir", serverJarsDir.absolutePath, "--final-name", spigotJar.name)

    doFirst {
        download("https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar",
            workDir.resolve("BuildTools.jar"))
        executable = launcher.get().executablePath.asFile.absolutePath
    }
}

fun RunServer.testServer(name: String, jar: File, directory: String) {
    group = "run paper"
    description = "Runs a $name $testServerVersion test server in $directory/."
    displayName.set(name)
    minecraftVersion(testServerVersion)
    serverJar(jar)
    runDirectory(file(directory))
    pluginJars(tasks.shadowJar.flatMap { it.archiveFile })
    doFirst { acceptEula(directory) }
}

val runPurpur by tasks.registering(RunServer::class) {
    testServer("Purpur", purpurJar, "run-purpur")
    dependsOn(downloadPurpur)
}

val runSpigot by tasks.registering(RunServer::class) {
    testServer("Spigot", spigotJar, "run-spigot")
    dependsOn(buildSpigot)
    // Spigot has no -add-plugin flag, so the plugin jar is copied into plugins/ instead
    legacyPluginLoading()
}
