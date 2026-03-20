plugins {
    kotlin("jvm") version "2.0.21"
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

group = "net.whero"
version = "1.0.2"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    implementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(21)
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
