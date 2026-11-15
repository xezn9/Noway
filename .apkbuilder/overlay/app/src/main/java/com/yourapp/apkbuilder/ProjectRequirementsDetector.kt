package com.yourapp.apkbuilder

import java.io.File

data class ProjectRequirements(
    val javaVersion: Int,          // 8, 11, 17, 21 ...
    val compileSdk: Int,
    val minSdk: Int,
    val buildToolsVersion: String,
    val usesKotlin: Boolean,
    val gradleModuleDir: File?      // null = مشروع بسيط src/res (زي سكربتنا الأصلي)، غير null = مشروع Gradle حقيقي
)

/**
 * يفحص ملفات المشروع (build.gradle / build.gradle.kts / gradle.properties)
 * ويطلع أرقام النسخ المطلوبة فعلياً، بدون أي افتراض ثابت من عندنا.
 */
object ProjectRequirementsDetector {

    fun detect(projectDir: File): ProjectRequirements {
        val gradleFiles = projectDir.walkTopDown()
            .filter { it.name == "build.gradle" || it.name == "build.gradle.kts" }
            .toList()

        val allText = gradleFiles.joinToString("\n") { it.readText() }

        val javaVersion = findJavaVersion(allText)
        val compileSdk = findIntSetting(allText, listOf("compileSdk", "compileSdkVersion")) ?: 34
        val minSdk = findIntSetting(allText, listOf("minSdk", "minSdkVersion")) ?: 21
        val buildTools = Regex("""buildToolsVersion\s*=?\s*["']([\d.]+)["']""")
            .find(allText)?.groupValues?.get(1) ?: "34.0.0"

        val usesKotlin = projectDir.walkTopDown().any { it.extension == "kt" } ||
            allText.contains("kotlin-android") || allText.contains("org.jetbrains.kotlin")

        // إذا المشروع فيه app/ module حقيقي (مشروع Android Studio كامل) نستخدم gradlew
        val appModule = projectDir.walkTopDown().firstOrNull {
            it.isDirectory && File(it, "build.gradle").exists() || File(it, "build.gradle.kts").exists()
        }
        val hasWrapper = File(projectDir, "gradlew").exists()

        return ProjectRequirements(
            javaVersion = javaVersion,
            compileSdk = compileSdk,
            minSdk = minSdk,
            buildToolsVersion = buildTools,
            usesKotlin = usesKotlin,
            gradleModuleDir = if (hasWrapper) projectDir else null
        )
    }

    private fun findJavaVersion(text: String): Int {
        // يدور على sourceCompatibility = JavaVersion.VERSION_21 أو jvmTarget = "21" أو --release 21
        Regex("""VERSION_(\d+)""").find(text)?.let { return it.groupValues[1].toInt() }
        Regex("""jvmTarget\s*=\s*["'](\d+)["']""").find(text)?.let { return it.groupValues[1].toInt() }
        Regex("""sourceCompatibility\s*=\s*["']?(\d+)["']?""").find(text)?.let { return it.groupValues[1].toInt() }
        return 17 // افتراضي معقول لو ما لقينا شي صريح، مو قيد- بس تخمين
    }

    private fun findIntSetting(text: String, keys: List<String>): Int? {
        for (k in keys) {
            Regex("""$k\s*=?\s*(\d+)""").find(text)?.let { return it.groupValues[1].toInt() }
        }
        return null
    }
}
