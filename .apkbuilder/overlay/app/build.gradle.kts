import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ecogif.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ecogif.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }

    // One fixed signing key for every build (debug AND release), so each new APK
    // installs over the old one as an update. Keep ecogif.jks safe: never change or lose it.
    signingConfigs {
        create("ecogif") {
            storeFile = file("ecogif.jks")
            storePassword = "ecogif2026"
            keyAlias = "ecogif"
            keyPassword = "ecogif2026"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("ecogif")
        }
        release {
            signingConfig = signingConfigs.getByName("ecogif")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures { compose = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    // Liquid Glass
    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation("io.github.kyant0:shapes:1.2.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}

// Skip the "AAR metadata" check (it only enforces minimum compileSdk / AGP versions
// requested by libraries and can fail on mobile build tools that ship older SDKs).
tasks.configureEach {
    if (name.endsWith("AarMetadata")) {
        enabled = false
    }
}
