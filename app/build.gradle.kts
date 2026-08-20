plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.callbackdev.snake"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.callbackdev.snake"
        minSdk = 33
        targetSdk = 36
        versionCode = 30
        versionName = "3.0.0"
    }

    signingConfigs {
        // Shared debug keystore (committed on purpose: a debug certificate has
        // no release value) so every build - local or CI - carries the same
        // signature. Without it each machine would sign with its own
        // ~/.android/debug.keystore and Android would refuse to update the app
        // in place, forcing an uninstall that wipes the test device's progress.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        // Real release key. The keystore lives OUTSIDE the repo; the four
        // properties come from ~/.gradle/gradle.properties locally and from
        // ORG_GRADLE_PROJECT_* env vars (GitHub Secrets) in the release
        // workflow. Only created when fully configured, so a clean checkout
        // still builds.
        val releaseStore = findProperty("SNAKE_KEYSTORE") as String?
        val releaseStorePassword = findProperty("SNAKE_KEYSTORE_PASSWORD") as String?
        val releaseKeyAlias = findProperty("SNAKE_KEY_ALIAS") as String?
        val releaseKeyPassword = findProperty("SNAKE_KEY_PASSWORD") as String?
        if (!releaseStore.isNullOrBlank() && !releaseStorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()
        ) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // The real key wins whenever it is configured. Otherwise the
            // debug-key opt-in: with the flag, per-push CI signs the minified
            // build so it can actually be installed and smoke-tested (R8
            // breakage only shows up in a release build). Off by default so an
            // unconfigured checkout can never produce an installable release
            // by accident.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
                    .takeIf { project.hasProperty("signReleaseWithDebugKey") }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // Generates BuildConfig so debug-only UI (e.g. the theme-unlock shortcut)
        // can gate on BuildConfig.DEBUG and be stripped from release builds.
        buildConfig = true
    }

    // Kotlin source lives under src/main/kotlin (configured below) instead of
    // the default src/main/java.
    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    // Compose — versions resolved by the BOM.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
