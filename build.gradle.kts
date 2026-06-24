plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "9.0.0"
}

group = "net.whero"
version = "2.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.72-stable")
    implementation(kotlin("stdlib"))
}

// Minecraft 26.1+ servers run on Java 25, and the 26.x Paper API is published
// requiring a Java 25 runtime, so the plugin must be built and compiled to target
// Java 25.
kotlin {
    jvmToolchain(25)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveBaseName.set("WheroPluginManager")
    archiveClassifier.set("")
    relocate("kotlin", "net.whero.pluginmanager.libs.kotlin")
    relocate("org.jetbrains", "net.whero.pluginmanager.libs.org.jetbrains")
    relocate("org.intellij", "net.whero.pluginmanager.libs.org.intellij")
}

tasks.jar {
    archiveBaseName.set("WheroPluginManager")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
