package com.yourapp.apkbuilder

import java.io.File

/**
 * يبني APK فعلي من مشروع تم فكه، باستخدام مسارات الأدوات التي جهّزها ToolchainManager.
 * يدعم حالتين:
 *  1) مشروع Gradle كامل (فيه gradlew) -> يشغل gradlew assembleDebug مباشرة (أبسط وأقوى)
 *  2) مشروع بسيط (src/res/AndroidManifest.xml فقط) -> يبني يدوياً javac/d8/aapt2 مثل سكربت build_apk.py
 */
class ProjectBuilder(
    private val tools: ToolchainPaths,
    private val log: (String) -> Unit
) {
    fun build(projectDir: File, req: ProjectRequirements): File {
        val gradlew = File(projectDir, "gradlew")
        return if (gradlew.exists()) buildWithGradle(projectDir, gradlew) else buildManually(projectDir, req)
    }

    // ---------- مسار Gradle (مشروع Android Studio كامل) ----------

    private fun buildWithGradle(projectDir: File, gradlew: File): File {
        gradlew.setExecutable(true)
        log("تشغيل Gradle Wrapper (قد يأخذ عدة دقائق أول مرة)...")

        val env = System.getenv().toMutableMap()
        env["JAVA_HOME"] = tools.javaHome.absolutePath

        val proc = ProcessBuilder(gradlew.absolutePath, "assembleDebug", "--no-daemon")
            .directory(projectDir)
            .redirectErrorStream(true)
            .apply { environment().putAll(env) }
            .start()

        proc.inputStream.bufferedReader().forEachLine { log(it) }
        val code = proc.waitFor()
        if (code != 0) error("فشل Gradle build، exit=$code")

        val apk = projectDir.walkTopDown()
            .firstOrNull { it.name.endsWith("-debug.apk") || (it.name.endsWith(".apk") && it.path.contains("outputs/apk")) }
            ?: error("ما لگيت ملف APK بالمخرجات")
        return apk
    }

    // ---------- المسار اليدوي (مشروع بسيط src/res) ----------

    private fun buildManually(projectDir: File, req: ProjectRequirements): File {
        val build = File(projectDir, "build_out").apply { deleteRecursively(); mkdirs() }
        val classes = File(build, "classes").apply { mkdirs() }
        val dexDir = File(build, "dex").apply { mkdirs() }

        val javac = File(tools.javaHome, "bin/javac")
        val javaFiles = projectDir.walkTopDown().filter { it.extension == "java" }.map { it.absolutePath }.toList()
        if (javaFiles.isNotEmpty()) {
            log("تصريف ${javaFiles.size} ملف Java...")
            run(listOf(javac.absolutePath, "-encoding", "UTF-8",
                "-classpath", tools.androidJar.absolutePath,
                "-d", classes.absolutePath) + javaFiles)
        }

        log("تحويل class -> dex...")
        val classFiles = classes.walkTopDown().filter { it.extension == "class" }.map { it.absolutePath }.toList()
        run(listOf(tools.d8.absolutePath, "--output", dexDir.absolutePath,
            "--lib", tools.androidJar.absolutePath, "--min-api", req.minSdk.toString()) + classFiles)

        log("تجميع الموارد عبر aapt2...")
        val resApk = File(build, "res.apk")
        val manifest = File(projectDir, "AndroidManifest.xml")
        val resDir = File(projectDir, "res")
        val compiled = File(build, "compiled_res").apply { mkdirs() }
        if (resDir.exists()) run(listOf(tools.aapt2.absolutePath, "compile", "--dir", resDir.absolutePath, "-o", compiled.absolutePath))
        val flata = compiled.listFiles()?.filter { it.extension == "flat" }?.map { it.absolutePath } ?: emptyList()
        run(listOf(tools.aapt2.absolutePath, "link", "-o", resApk.absolutePath,
            "--manifest", manifest.absolutePath, "-I", tools.androidJar.absolutePath) + flata)

        log("تعبئة APK...")
        val unsigned = File(build, "unsigned.apk")
        resApk.copyTo(unsigned, overwrite = true)
        run(listOf("zip", "-j", unsigned.absolutePath) + dexDir.listFiles()!!.map { it.absolutePath })

        log("محاذاة APK (zipalign)...")
        val aligned = File(build, "aligned.apk")
        run(listOf(tools.zipalign.absolutePath, "-f", "-p", "4", unsigned.absolutePath, aligned.absolutePath))

        log("توليد مفتاح توقيع وتوقيع APK...")
        val keystore = File(build, "debug.keystore")
        if (!keystore.exists()) {
            run(listOf("keytool", "-genkeypair", "-keystore", keystore.absolutePath, "-alias", "debug",
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
                "-storepass", "android", "-keypass", "android", "-dname", "CN=Debug"))
        }
        val finalApk = File(build, "app-debug.apk")
        run(listOf(tools.apksigner.absolutePath, "sign", "--ks", keystore.absolutePath,
            "--ks-key-alias", "debug", "--ks-pass", "pass:android", "--out", finalApk.absolutePath, aligned.absolutePath))

        return finalApk
    }

    private fun run(cmd: List<String>) {
        log("$ ${cmd.joinToString(" ")}")
        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        proc.inputStream.bufferedReader().forEachLine { log(it) }
        val code = proc.waitFor()
        if (code != 0) error("فشل الأمر (exit=$code): ${cmd.firstOrNull()}")
    }
}
