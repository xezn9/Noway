plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.icehome.up"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.icehome.up"
        minSdk = 30
        targetSdk = 35
        versionCode = 70
        versionName = "3.0.2"
    }

    buildTypes {
        release {
            // Sin ofuscar: LSPosed necesita encontrar MainHook por nombre.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Las dos API de Xposed, solo para compilar: en el móvil las pone el framework.
    compileOnly("de.robv.android.xposed:api:82")          // clásica, motor LegacyEngine
    compileOnly("io.github.libxposed:api:102.0.0")        // libxposed, motor ModernEngine

    // Cristal líquido de la app de ajustes (barra de pestañas, interruptor y deslizador).
    // Solo los usa SettingsActivity: los hooks del launcher y de SystemUI no los tocan.
    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation("io.github.kyant0:shapes:1.2.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
}
