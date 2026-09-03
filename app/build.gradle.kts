plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.memory"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.memory"
        minSdk = 24
        targetSdk = 37
        versionCode = 3
        versionName = "0.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing reads from Gradle properties (set in ~/.gradle/gradle.properties, never
    // committed) rather than being hardcoded. If any property is missing - e.g. on a fresh clone
    // that doesn't have Karl's local gradle.properties - hasReleaseSigning is false, no signing
    // config is created, and the release build type just builds unsigned instead of failing.
    val releaseStoreFile = providers.gradleProperty("MEMORY_STORE_FILE")
    val releaseStorePassword = providers.gradleProperty("MEMORY_STORE_PASSWORD")
    val releaseKeyAlias = providers.gradleProperty("MEMORY_KEY_ALIAS")
    val releaseKeyPassword = providers.gradleProperty("MEMORY_KEY_PASSWORD")
    val hasReleaseSigning = releaseStoreFile.isPresent &&
        releaseStorePassword.isPresent &&
        releaseKeyAlias.isPresent &&
        releaseKeyPassword.isPresent

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    // MigrationTestHelper reads exported schema JSON as an asset at test runtime, so the
    // schemas/ directory (see the `room` block below) has to be on the androidTest assets path.
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

// The Room Gradle plugin (id 'androidx.room') registers a copy task but never actually wires
// room.schemaLocation into the kspDebugKotlin/kspReleaseKotlin tasks on this project's AGP 9
// setup - the task runs NO-SOURCE and no schema JSON is produced. So the location is passed
// straight to KSP instead, via a CommandLineArgumentProvider (see RoomSchemaArgProvider below)
// rather than a plain `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` string. A bare
// string bakes the absolute $projectDir path into the task's inputs verbatim, which isn't a
// recognized/cacheable Gradle input and breaks the build cache and incremental builds. The
// provider instead declares the schema directory as a proper @InputDirectory with RELATIVE path
// sensitivity, so Gradle can hash its contents and cache correctly across machines/checkouts.
ksp {
    arg(RoomSchemaArgProvider(File(projectDir, "schemas")))
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.reorderable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.documentfile)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// room.schemaLocation as a proper Gradle task input (see the ksp{} block above) instead of a
// bare string baked into the command line.
class RoomSchemaArgProvider(
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val schemaDir: File,
) : CommandLineArgumentProvider {
    init {
        // Must exist before Gradle snapshots it as an input - true on the very first export,
        // since nothing has ever written to schemas/ yet.
        schemaDir.mkdirs()
    }

    override fun asArguments(): Iterable<String> = listOf("room.schemaLocation=${schemaDir.path}")
}