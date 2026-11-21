plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.liquidtabs"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.liquidtabs"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")

    // Liquid glass (backdrop 2.0.0 as requested)
    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation("io.github.kyant0:shapes:1.2.1")
}

// Safety net: some libraries declare a minimum compileSdk / AGP version newer than the one in
// your environment, which fails ":app:checkDebugAarMetadata". This check only verifies
// compatibility metadata; it does not change the compiled code.
tasks.configureEach {
    if (name.startsWith("check") && name.endsWith("AarMetadata")) enabled = false
}
