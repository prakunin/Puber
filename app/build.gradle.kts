import dev.detekt.gradle.Detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    id("kotlin-parcelize")
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.androidx.room)
}

val currentVersion = "1.8.62"

private fun readProperties(name: String): Properties = Properties().apply {
    rootProject.file(name)
        .takeIf(File::exists)
        ?.inputStream()
        ?.use(::load)
}

val localEnvironment: Properties by lazy { readProperties(".env") }

/**
 * `local.properties` is still read, and still documented in the README, because it is where every
 * existing checkout keeps these secrets. Dropping it in favour of `.env` alone would leave those
 * developers with an empty CLIENT_SECRET and a login that silently stops working.
 */
val localProperties: Properties by lazy { readProperties("local.properties") }

fun envOrNull(name: String): String? {
    return localEnvironment.property(name)
        ?: localProperties.property(name)
        ?: System.getenv(name)?.trim()?.takeIf(String::isNotEmpty)
}

private fun Properties.property(name: String): String? =
    getProperty(name)?.trim()?.takeIf(String::isNotEmpty)

android {
    namespace = "com.kino.puber"
    compileSdk = Versions.CompileSdk

    defaultConfig {
        applicationId = "com.kino.puber"
        minSdk = Versions.MinSdk
        targetSdk = Versions.TargetSdk
        versionCode = Versions.DebugVersionCode
        versionName = currentVersion

        // Add CLIENT_SECRET to BuildConfig
        buildConfigField("String", "CLIENT_SECRET", "\"${envOrNull("PUBER_CLIENT_SECRET").orEmpty()}\"")
        buildConfigField("String", "TMDB_READ_ACCESS_TOKEN", "\"${envOrNull("TMDB_READ_ACCESS_TOKEN").orEmpty()}\"")
        // Default mirror for a fresh install. A domain saved in the app still wins over it.
        buildConfigField("String", "API_DOMAIN_OVERRIDE", "\"${envOrNull("PUBER_API_DOMAIN").orEmpty()}\"")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Android Lint covers what detekt structurally cannot reach: the manifest, the resources, and
    // API levels weighed against `minSdk` - the check that caught `java.time` crashing on an
    // Android TV 7.x box. It runs with no baseline file: every error the project had was fixed
    // rather than grandfathered, so a new one cannot hide behind a backlog of old ones.
    lint {
        abortOnError = true

        // The 112 warnings the run still reports are left as warnings on purpose. Promoting them
        // is its own piece of work, not a side effect of turning the gate on.
        warningsAsErrors = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    flavorDimensions += "buildType"

    compileOptions {
        sourceCompatibility = Versions.JavaVersionCompat
        targetCompatibility = Versions.JavaVersionCompat

        // The episode schedule works in `java.time`, which the platform only carries from API 26.
        // With `minSdk` at 24 those calls are a NoClassDefFoundError on an Android TV 7.x box, so
        // the library is desugared into the APK rather than the dates being rewritten around it.
        isCoreLibraryDesugaringEnabled = true
    }

    composeCompiler {
        stabilityConfigurationFiles.addAll(
            rootProject.layout.projectDirectory.file("config/compose/compiler_config.conf")
        )
    }


    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.jks")
        }

        create("release") {
            val keystorePropertiesFile = file("keystore.properties")
            when {
                // 1. Local: keystore.properties file
                keystorePropertiesFile.exists() -> {
                    val keystoreProperties = Properties()
                    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
                    keyAlias = keystoreProperties["keyAlias"] as String
                    keyPassword = keystoreProperties["keyPassword"] as String
                    storePassword = keystoreProperties["storePassword"] as String
                    storeFile = file("release.jks")
                }
                // 2. CI: base64-encoded keystore from RELEASE_KEYSTORE_BASE64 env var
                envOrNull("RELEASE_KEYSTORE_BASE64") != null -> {
                    val decoded = Base64.getDecoder().decode(envOrNull("RELEASE_KEYSTORE_BASE64"))
                    val keystoreFile = file("release.jks")
                    keystoreFile.writeBytes(decoded)
                    storeFile = keystoreFile
                    storePassword = envOrNull("STOREPASS")
                    keyAlias = envOrNull("KEYALIAS") ?: "puber"
                    keyPassword = envOrNull("KEYPASS") ?: envOrNull("STOREPASS")
                }
                // 3. CI: release.jks already present (e.g. copied in CI step) + env vars
                envOrNull("STOREPASS") != null -> {
                    storePassword = envOrNull("STOREPASS")
                    keyAlias = envOrNull("KEYALIAS") ?: "puber"
                    keyPassword = envOrNull("KEYPASS") ?: envOrNull("STOREPASS")
                    storeFile = file("release.jks")
                }
                // 4. Fallback: debug signing (allows build without release keys)
                else -> {
                    storeFile = file("debug.jks")
                }
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
        }

        // Fast local TV deployment: keeps the dev flavor and debug signing, but disables all
        // BuildConfig.DEBUG-only logging without paying the R8/resource-shrinking build cost.
        create("deploy") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            versionNameSuffix = "-deploy"
            matchingFallbacks += listOf("release")
        }

        create("nonMinifiedRelease") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
        }
        create("benchmarkRelease") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }


    productFlavors {
        create("dev") {
            dimension = "buildType"
            versionName = "$currentVersion-$name"
            applicationIdSuffix = ".stage"
            resValue("string", "app_name", "Puber(${name.replaceFirstChar { it.uppercaseChar() }})")
        }

        create("prod") {
            dimension = "buildType"
            versionCode = Versions.VersionCode
            resValue("string", "app_name", "Puber")
        }
    }

}

room3 {
    // Room writes the schema JSON here so migrations can be diffed against it in review.
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    jvmToolchain(Versions.ToolchainJavaVersion)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(Versions.JvmTargetVersion))
        // `-Xjvm-default=all` under its post-2.2 name; `no-compatibility` is the same mode.
        freeCompilerArgs.add("-jvm-default=no-compatibility")
        optIn.addAll(
            listOf(
                "androidx.compose.material3.ExperimentalMaterial3Api",
                "androidx.compose.foundation.ExperimentalFoundationApi",
                "kotlinx.coroutines.ExperimentalCoroutinesApi",
                "kotlinx.coroutines.FlowPreview",
                "androidx.tv.material3.ExperimentalTvMaterial3Api",
            )
        )
    }
}

// Shared settings for the variant-aware detekt tasks (detektMain, detektDevDebug, ...).
// Those run with type resolution, so they see the rules that a source-set-only pass skips.
detekt {
    parallel = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/detekt-baseline.xml")
    buildUponDefaultConfig = false
}

// Coverage is measured here, not enforced. There is no verification rule and no threshold: a
// number picked before anyone has read a report only teaches people to write tests that move it.
// `make coverage` prints the figure and writes the HTML report.
kover {
    reports {
        filters {
            excludes {
                // Generated code. Room's DAO implementations, BuildConfig and the resource classes
                // are not written in this repository, so they are not ours to cover.
                classes(
                    "*_Impl",
                    "*_Impl\$*",
                    "com.kino.puber.BuildConfig",
                    "com.kino.puber.R",
                    "com.kino.puber.R\$*",
                )

                // @Preview composables exist to be looked at in the IDE; nothing runs them.
                annotatedBy("androidx.compose.ui.tooling.preview.Preview")
            }
        }
    }
}

// Detekt's Android integration hands each task the variant's dependencies but not the variant's own
// output, so `BuildConfig` - a class AGP generates and javac compiles - resolves nowhere. That cost
// 26 of the compiler errors that degrade the type resolution `detektAll` exists to provide.
//
// The classpath cannot simply be added to: the plugin supplies it as a Gradle convention, and any
// `from()` here would discard that convention wholesale rather than extend it, leaving detekt with
// nothing but what we added. So the task takes the classpath over completely, and builds it out of
// the compilation's own inputs rather than a second guess at detekt's: the Kotlin compile task's
// libraries (dependencies plus the generated R.jar), the Android boot classpath, and the javac
// output holding BuildConfig. Reusing the real compilation keeps the two from drifting apart.
// Each variant task is matched to its own compilation by name rather than from a hand-written
// list, so adding a flavour or a build type cannot leave a detekt task quietly analysing against
// the plugin's classpath again - a regression that shows up as nothing at all, just a less
// accurate report. Tasks with no variant in their name (the source-set `detekt`) derive nothing
// and keep the convention, which is what they should have.
tasks.withType<Detekt>().configureEach {
    val variant = name.removePrefix("detekt").takeIf { it != name && it.isNotEmpty() }
        ?: return@configureEach
    val kotlinCompileName = "compile${variant}Kotlin"
    if (kotlinCompileName !in tasks.names) return@configureEach

    // `BuildConfig` belongs to the production variant: the unit-test compilation compiles no Java
    // of its own, so `detektDevDebugUnitTest` reads the class out of `devDebug`.
    val javaCompileName = "compile${variant.removeSuffix("UnitTest")}JavaWithJavac"

    classpath.setFrom(
        tasks.named<KotlinCompile>(kotlinCompileName).map { it.libraries },
        androidComponents.sdkComponents.bootClasspath,
        tasks.named<JavaCompile>(javaCompileName).flatMap(JavaCompile::getDestinationDirectory),
    )
}

tasks {
    // Single entry point for the static-analysis gate. It delegates to the variant tasks rather
    // than scanning the source tree directly: those compile the variant first, so detekt runs with
    // type resolution and sees the rules a source-set-only pass silently skips (swallowed
    // cancellation, unsafe !!, unused declarations, ...).
    //
    // That resolution is good but not complete, and the gate should not be read as stronger than
    // it is. Detekt 2.0 analyses through the Kotlin Analysis API, and its standalone session
    // registers no compiler-plugin extensions: kotlin-parcelize and kotlinx-serialization are
    // simply absent from it, so every @Parcelize class looks like it never implements Parcelable
    // and every @Serializable model loses serializer(). Those are the ~83 compiler errors a run
    // still reports, and rules that need types may stay quiet on the files behind them. Handing
    // the plugins over as -Xplugin through freeCompilerArgs does not help: detekt drops compiler
    // arguments it does not recognise without a word. Revisit when 2.0 ships a way to load them.
    register("detektAll") {
        group = "verification"
        description = "Runs detekt with type resolution over the dev debug production and unit-test sources."
        dependsOn("detektDevDebug", "detektDevDebugUnitTest")
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.text)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tvprovider)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.phosphor.icons)
    implementation(libs.androidx.security.crypto)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.compose.placeholder.material3)

    //coil
    implementation(libs.coil.core)
    implementation(libs.coil.video)
    implementation(libs.coil.compose)
    implementation(libs.coil.ktor)

    // OkHttp extensions
    implementation(libs.okhttp.doh)

    // Ktor HTTP client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Serialization & Utils
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    //navigation
    implementation(libs.voyager.navigator)
    implementation(libs.voyager.tab.navigator)

    // Media3 (Video Player)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource.okhttp)

    // Local watch-state database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.framework)
    ksp(libs.androidx.room.compiler)

    // Logging
    implementation(libs.timber)

    // Testing
    testImplementation(libs.junit5)
    testRuntimeOnly(libs.junit5.launcher)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)

    detektPlugins(libs.detekt.compose.rules)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.coroutines.test)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
}

baselineProfile {
    saveInSrc = true
    automaticGenerationDuringBuild = false
    mergeIntoMain = true
    warnings {
        maxAgpVersion = false
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    maxHeapSize = "2g"
}
