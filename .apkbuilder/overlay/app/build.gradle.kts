plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.glassvolume"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.example.glassvolume"
        minSdk = 26
        targetSdk = 36
        // versionCode يزيد تلقائياً مع كل بناء (دقائق منذ 1970) حتى كل نسخة جديدة تنزل «تحديث» فوق القديمة
        val buildMinutes = (System.currentTimeMillis() / 60_000L).toInt()
        versionCode = buildMinutes
        versionName = "1.0.$buildMinutes"
    }

    // توقيع ثابت: نفس المفتاح بكل بناء (محلي أو GitHub Actions)، وإلا كل بناء ينوقّع بمفتاح عشوائي
    // ويرفض الأندرويد التحديث فوق النسخة المثبّتة.
    signingConfigs {
        create("shared") {
            storeFile = file("glassvolume.jks")
            storePassword = "glassvolume"
            keyAlias = "glassvolume"
            keyPassword = "glassvolume"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("shared") }
        getByName("release") {
            signingConfig = signingConfigs.getByName("shared")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    // backdrop تتطلب compileSdk 37 (تحذير AGP مكتوم من gradle.properties)
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")   // Capsule للسلايدر الزجاجي

    // enforcedPlatform: يثبّت Compose على 1.10 حتى backdrop/shapes ما يسحبون 1.12 (يتطلب AGP 9.1)
    implementation(enforcedPlatform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
}
