plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yourapp.apkbuilder"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.yourapp.apkbuilder"
        minSdk = 26
        targetSdk = 34
        // لازم تزيد versionCode بكل إصدار جديد حتى يعتبره أندرويد "تحديث" بدل تطبيق منفصل.
        // versionName بس للعرض للمستخدم، ما يؤثر على منطق التحديث.
        versionCode = 2
        versionName = "1.1"
    }

    // لازم تكون كل نسخة موقعة بنفس المفتاح حتى تنصب كتحديث فوق النسخة السابقة.
    // لو وقعت بمفتاح مختلف، أندرويد يرفض التثبيت كتحديث ("INSTALL_FAILED_UPDATE_INCOMPATIBLE").
    signingConfigs {
        create("release") {
            // القيم تجي من متغيرات بيئة (مثالية لـ GitHub Actions secrets) أو من gradle.properties محلياً.
            // لو ما كانت موجودة، يرجع لنفس مفتاح debug (يسمح ببناء تجريبي بدون أسرار، لكن ما يصلح للنشر الفعلي).
            val ksPath = System.getenv("KEYSTORE_PATH") ?: project.findProperty("RELEASE_KEYSTORE_PATH") as String?
            val ksPass = System.getenv("KEYSTORE_PASSWORD") ?: project.findProperty("RELEASE_KEYSTORE_PASSWORD") as String?
            val alias = System.getenv("KEY_ALIAS") ?: project.findProperty("RELEASE_KEY_ALIAS") as String?
            val keyPass = System.getenv("KEY_PASSWORD") ?: project.findProperty("RELEASE_KEY_PASSWORD") as String?

            if (ksPath != null && ksPass != null && alias != null) {
                storeFile = file(ksPath)
                storePassword = ksPass
                keyAlias = alias
                keyPassword = keyPass ?: ksPass
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // يستخدم توقيع release لو الأسرار متوفرة، وإلا يرجع تلقائياً لمفتاح debug (بناء محلي بدون نشر)
            signingConfig = if (signingConfigs.getByName("release").storeFile != null)
                signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    buildFeatures { viewBinding = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.apache.commons:commons-compress:1.26.2")   // فك/ضغط zip المشروع
}
