plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.kino.puber.playertestfixtures"
    compileSdk = Versions.CompileSdk
    defaultConfig { minSdk = Versions.MinSdk }
    compileOptions {
        sourceCompatibility = Versions.JavaVersionCompat
        targetCompatibility = Versions.JavaVersionCompat
    }
    // The assets are the JVM tests' classpath resources too (PlayerTestFixtures.openPath falls back to the class loader).
    sourceSets.getByName("test").resources.directories.add("src/main/assets")
}

kotlin {
    jvmToolchain(Versions.ToolchainJavaVersion)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(Versions.JvmTargetVersion))
    }
}

dependencies {
    api(libs.mockwebserver3)
    api(libs.okhttp.tls)
    testImplementation(libs.junit5)
    testRuntimeOnly(libs.junit5.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
