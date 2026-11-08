val buildVersionCode = providers.gradleProperty("appVersionCode").orNull?.toIntOrNull() ?: 2
val buildVersionName = providers.gradleProperty("appVersionName").orNull ?: "0.2.0"

plugins {
    id("com.android.application") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

android {
    namespace = "com.dincer552.hattrickssh"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dincer552.hattrickssh"
        minSdk = 26
        targetSdk = 35
        versionCode = buildVersionCode
        versionName = buildVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
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
            // Android 10+ blocks exec() from the writable app home directory.
            // Extract APK-packaged executable payloads to nativeLibraryDir.
            useLegacyPackaging = true
            keepDebugSymbols += setOf("**/libcloudflared.so", "**/libsshbridge.so")
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

    // Maintained JSch fork: RSA/SHA-2 and current SSH algorithm support.
    implementation("com.github.mwiede:jsch:2.28.8")

    // Termux VT emulator/view provides a real ANSI/VT terminal UI.
    implementation("com.termux.termux-app:terminal-view:0.118.0")
    // ProfileInstaller references ResolvableFuture; package the real AndroidX class.
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
    implementation("com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
