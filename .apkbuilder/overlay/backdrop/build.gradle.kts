import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Backdrop 2.0.0 (Kyant0/AndroidLiquidGlass) - source included in the project, modified:
// + effects/StickyLens.kt and internal/StickyShaders.kt (sticky / metaball liquid glass)
plugins {
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    android {
        minSdk = 21
        compileSdk = 37
        buildToolsVersion = "37.0.0"
        namespace = "com.kyant.backdrop"
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.compose.foundation)
                implementation(libs.compose.ui)
                implementation(libs.compose.ui.graphics)
                implementation(libs.kyant.shapes)
                implementation("org.jetbrains:annotations:26.1.0")
            }
        }

        all {
            languageSettings.enableLanguageFeature("ContextParameters")
        }
    }
}
