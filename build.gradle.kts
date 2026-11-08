plugins {
    id("com.android.application") version "8.9.1"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

val buildVersionCode = providers.gradleProperty("appVersionCode").orNull?.toIntOrNull() ?: 2
val buildVersionName = providers.gradleProperty("appVersionName").orNull ?: "0.2.0"
val signingStorePath = System.getenv("ANDROID_KEYSTORE_PATH")

android {
    namespace = "com.dincer552.hattrickssh"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dincer552.hattrickssh"
        minSdk = 26
        targetSdk = 36
        versionCode = buildVersionCode
        versionName = buildVersionName
        buildConfigField("boolean", "PLAY_DISTRIBUTION", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("releaseKey") {
            if (!signingStorePath.isNullOrBlank()) {
                storeFile = file(signingStorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = "hattrickssh"
                keyPassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            if (!signingStorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("releaseKey")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }

    packaging {
        jniLibs {
            // Android 10+ (API 29+) blocks executing files from the writable app home (filesDir).
            // Keep cloudflared/sshbridge in jniLibs so the Package Manager extracts them to
            // nativeLibraryDir, which is executable on every target SDK.
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES"
            )
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.google.android.play:app-update:2.1.0")

    // Maintained JSch fork: RSA/SHA-2 and current SSH algorithm support.
    implementation("com.github.mwiede:jsch:2.28.8")

    // Termux VT emulator/view provides a real ANSI/VT terminal UI.
    implementation("com.termux.termux-app:terminal-view:0.118.0")
    implementation("com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
