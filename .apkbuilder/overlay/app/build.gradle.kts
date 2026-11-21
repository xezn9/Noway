plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.icehome.up"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.icehome.up"
        minSdk = 30
        targetSdk = 35
        versionCode = 70
        versionName = "3.0.2"
    }

    buildTypes {
        release {
            // R8: reduce el código (quita lo que no se usa, funde y acorta), ofusca los nombres y
            // quita los recursos sin usar. Los nombres que LSPosed necesita encontrar (ModernEntry,
            // LegacyEntry) se conservan en proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // La app solo tiene textos en español e inglés: fuera los idiomas que traen las bibliotecas.
    androidResources {
        localeFilters += listOf("en", "es")
    }

    // Archivos de las bibliotecas que no hacen falta en el móvil (no tocar META-INF/xposed).
    packaging {
        resources {
            excludes += setOf(
                "/DebugProbesKt.bin",
                "/kotlin-tooling-metadata.json",
                "/META-INF/*.version",
                "/META-INF/*.kotlin_module",
                "/META-INF/com/android/build/gradle/**",
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
                "/META-INF/versions/**",
                "/kotlin/**",
                "/**/*.kotlin_builtins",
                "/**/*.kotlin_metadata"
            )
        }
    }

    // Quita del APK la lista de dependencias que Google Play lee (no se sube a Play).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Las dos API de Xposed, solo para compilar: en el móvil las pone el framework.
    compileOnly("de.robv.android.xposed:api:82")          // clásica, motor LegacyEngine
    compileOnly("io.github.libxposed:api:102.0.0")        // libxposed, motor ModernEngine

    // Cristal líquido de la app de ajustes (barra de pestañas, interruptor y deslizador).
    // Solo los usa SettingsActivity: los hooks del launcher y de SystemUI no los tocan.
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
}
