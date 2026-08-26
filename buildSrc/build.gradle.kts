plugins {
    // `kotlin-dsl-base` rather than `kotlin-dsl`: this source set holds shared constants, not
    // precompiled script plugins, and the full plugin would warn on every build that it found no
    // plugin descriptors to publish.
    `kotlin-dsl-base`
}

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

dependencies {
    // Versions.kt refers to org.gradle.api types; no third-party plugin dependencies belong here.
    // Plugin versions are managed through the version catalog in the main build.
    implementation(gradleApi())
}
