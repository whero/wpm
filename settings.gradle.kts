plugins {
    // Lets Gradle auto-provision the Java 25 toolchain required by the 26.x
    // Paper API on machines where it isn't already installed/detected.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "whero-plugin-manager"
