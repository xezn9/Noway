plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.glassnav"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.glassnav"
        minSdk = 33   // lens effect of Backdrop needs Android 13+
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }
    // Fixed signing key: same signature on every build, so updates install over the old app
    signingConfigs {
        create("fixed") {
            storeFile = file("glassnav.keystore")
            storePassword = "glassnav123"
            keyAlias = "glassnav"
            keyPassword = "glassnav123"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("fixed") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")

    // Liquid Glass
    implementation("io.github.kyant0:backdrop:2.0.0")
}
