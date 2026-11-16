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
        versionCode = 3
        versionName = "1.2"
    }

    // لازم تكون كل نسخة موقعة بنفس المفتاح حتى تنصب كتحديث فوق النسخة السابقة.
    // لو وقعت بمفتاح مختلف، أندرويد يرفض التثبيت كتحديث ("INSTALL_FAILED_UPDATE_INCOMPATIBLE").
    signingConfigs {
        create("release") {
            // مفتاح ثابت مخصص لهذا التطبيق (keystore/apkbuilder-release.jks) — نفس المفتاح
            // بكل بناء، فأي نسخة جديدة تنصب كـ"تحديث" فوق القديمة تلقائياً.
            // تقدر تستبدل القيم من متغيرات بيئة (GitHub Actions secrets) لو ما تبي تحط كلمة
            // المرور مباشرة بالكود، لكن الافتراضي هنا جاهز يشتغل مباشرة بدون أي إعداد إضافي.
            val ksPath = System.getenv("KEYSTORE_PATH")
                ?: (project.findProperty("RELEASE_KEYSTORE_PATH") as String?)
                ?: "${rootDir}/keystore/apkbuilder-release.jks"
            val ksPass = System.getenv("KEYSTORE_PASSWORD")
                ?: (project.findProperty("RELEASE_KEYSTORE_PASSWORD") as String?)
                ?: "ApkBuilder#2025Secure"
            val alias = System.getenv("KEY_ALIAS")
                ?: (project.findProperty("RELEASE_KEY_ALIAS") as String?)
                ?: "apkbuilder"
            val keyPass = System.getenv("KEY_PASSWORD")
                ?: (project.findProperty("RELEASE_KEY_PASSWORD") as String?)
                ?: ksPass

            val ksFile = file(ksPath)
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = ksPass
                keyAlias = alias
                keyPassword = keyPass
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
