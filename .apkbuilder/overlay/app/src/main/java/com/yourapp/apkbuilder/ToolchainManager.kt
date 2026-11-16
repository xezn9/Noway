package com.yourapp.apkbuilder

import android.content.Context
import java.io.File
import java.net.URL
import java.util.zip.ZipFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

data class ToolchainPaths(
    val javaHome: File,
    val aapt2: File,
    val d8: File,
    val zipalign: File,
    val apksigner: File,
    val androidJar: File
)

/**
 * يتأكد من وجود JDK المطلوب وAndroid SDK المطلوب، وينزلهم تلقائياً لو ناقصين،
 * ويخزنهم داخل filesDir بحيث ما يعيد التنزيل كل مرة.
 *
 * المصادر:
 *  - JDK: Eclipse Temurin (Adoptium) builds, aarch64 Linux
 *  - Android SDK: Google الرسمية (commandlinetools) + build-tools/platforms عبر sdkmanager نفسه
 */
class ToolchainManager(
    private val ctx: Context,
    private val onProgress: (pct: Int, msg: String) -> Unit
) {
    private val toolsRoot = File(ctx.filesDir, "toolchains").apply { mkdirs() }

    fun ensureToolchain(req: ProjectRequirements): ToolchainPaths {
        val javaHome = ensureJdk(req.javaVersion)
        val sdkRoot = ensureAndroidSdkBase()
        val buildTools = ensureBuildTools(sdkRoot, req.buildToolsVersion)
        val androidJar = ensurePlatform(sdkRoot, req.compileSdk)

        return ToolchainPaths(
            javaHome = javaHome,
            aapt2 = File(buildTools, "aapt2"),
            d8 = File(buildTools, "d8"),
            zipalign = File(buildTools, "zipalign"),
            apksigner = File(buildTools, "apksigner"),
            androidJar = androidJar
        ).also { makeExecutable(it) }
    }

    private fun makeExecutable(p: ToolchainPaths) {
        for (f in listOf(p.aapt2, p.d8, p.zipalign, p.apksigner)) f.setExecutable(true)
        File(p.javaHome, "bin/javac").setExecutable(true)
        File(p.javaHome, "bin/java").setExecutable(true)
    }

    // ---------- JDK ----------

    private fun ensureJdk(version: Int): File {
        val dir = File(toolsRoot, "jdk-$version")
        if (File(dir, "bin/javac").exists()) {
            onProgress(100, "JDK $version موجود مسبقاً.")
            return findSingleSubdir(dir) ?: dir
        }
        onProgress(0, "تنزيل JDK $version (Eclipse Temurin)...")
        // رابط Adoptium API يرجع أحدث بناء لنسخة major محددة، منصة aarch64 لينكس
        val apiUrl = "https://api.adoptium.net/v3/binary/latest/$version/ga/linux/aarch64/jdk/hotspot/normal/eclipse"
        val tarGz = File(toolsRoot, "jdk-$version.tar.gz")
        downloadWithProgress(apiUrl, tarGz) { pct, info -> onProgress(pct, "تنزيل JDK $version: $pct% ($info)") }
        dir.mkdirs()
        extractTarGz(tarGz, dir)
        tarGz.delete()
        onProgress(100, "تم تنصيب JDK $version")
        return findSingleSubdir(dir) ?: dir
    }

    // ---------- Android SDK ----------

    private fun ensureAndroidSdkBase(): File {
        val sdkRoot = File(toolsRoot, "android-sdk")
        val cmdlineTools = File(sdkRoot, "cmdline-tools/latest/bin/sdkmanager")
        if (cmdlineTools.exists()) return sdkRoot

        onProgress(0, "تنزيل Android SDK command-line tools...")
        val url = "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
        val zip = File(toolsRoot, "cmdline-tools.zip")
        downloadWithProgress(url, zip) { pct, info -> onProgress(pct, "تنزيل cmdline-tools: $pct% ($info)") }

        val tmp = File(toolsRoot, "cmdline-tools-tmp")
        extractZip(zip, tmp)
        val latest = File(sdkRoot, "cmdline-tools/latest").apply { mkdirs() }
        File(tmp, "cmdline-tools").copyRecursively(latest, overwrite = true)
        tmp.deleteRecursively(); zip.delete()
        File(latest, "bin/sdkmanager").setExecutable(true)
        onProgress(100, "تم تنصيب cmdline-tools")
        return sdkRoot
    }

    private fun ensureBuildTools(sdkRoot: File, version: String): File {
        val dir = File(sdkRoot, "build-tools/$version")
        if (dir.exists()) return dir
        onProgress(0, "تنزيل build-tools $version عبر sdkmanager...")
        runSdkManager(sdkRoot, "build-tools;$version")
        return dir
    }

    private fun ensurePlatform(sdkRoot: File, api: Int): File {
        val jar = File(sdkRoot, "platforms/android-$api/android.jar")
        if (jar.exists()) return jar
        onProgress(0, "تنزيل platform android-$api عبر sdkmanager...")
        runSdkManager(sdkRoot, "platforms;android-$api")
        return jar
    }

    private fun runSdkManager(sdkRoot: File, pkg: String) {
        val sdkmanager = File(sdkRoot, "cmdline-tools/latest/bin/sdkmanager")
        val proc = ProcessBuilder(sdkmanager.absolutePath, "--sdk_root=${sdkRoot.absolutePath}", pkg)
            .redirectErrorStream(true).start()
        // يوافق تلقائياً على ترخيص الاستخدام (--licenses يحتاج تفاعل y/n)
        proc.outputStream.bufferedWriter().use { it.write("y\n".repeat(10)) }
        proc.inputStream.bufferedReader().forEachLine { onProgress(50, it) }
        proc.waitFor()
    }

    // ---------- أدوات مساعدة عامة ----------

    /**
     * progress(pct, infoLine) يُستدعى فقط لما:
     *   - النسبة المئوية تتغير فعلياً (مو كل قطعة 64KB)، أو
     *   - مرّت ثانية وحدة على آخر تحديث (حتى لو النسبة ثابتة بسبب ملف كبير جداً)
     * بكل استدعاء نمرر سطر معلومات فيه السرعة الحالية والحجم المحمّل/الكلي،
     * عشان يكون واضح انه فعلاً يتحمل مو متوقف.
     */
    private fun downloadWithProgress(url: String, dest: File, maxRetries: Int = 3, progress: (Int, String) -> Unit) {
        var lastError: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                val conn = URL(url).openConnection()
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.connect()
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
                var read = 0L
                var lastPct = -1
                var lastReportMs = System.currentTimeMillis()
                var bytesSinceLastReport = 0L

                conn.getInputStream().use { input ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            bytesSinceLastReport += n

                            val pct = if (total > 0) ((read * 100) / total).toInt() else -1
                            val now = System.currentTimeMillis()
                            val elapsedMs = now - lastReportMs

                            // نبلّغ فقط عند تغيّر النسبة أو كل ~1 ثانية (مو كل قطعة)
                            if (pct != lastPct || elapsedMs >= 1000) {
                                val speedKbS = if (elapsedMs > 0) (bytesSinceLastReport * 1000 / elapsedMs) / 1024 else 0
                                val readMb = read / (1024 * 1024)
                                val totalMb = if (total > 0) total / (1024 * 1024) else -1
                                val sizeInfo = if (totalMb > 0) "$readMb/$totalMb MB" else "$readMb MB"
                                progress(if (pct >= 0) pct else 0, "$sizeInfo — ${speedKbS} KB/s")
                                lastPct = pct
                                lastReportMs = now
                                bytesSinceLastReport = 0
                            }
                        }
                    }
                }
                return // نجح
            } catch (e: Exception) {
                lastError = e
                dest.delete()
                if (attempt < maxRetries) {
                    onProgress(0, "فشلت محاولة $attempt (${e.javaClass.simpleName}: ${e.message}) — إعادة محاولة...")
                    Thread.sleep(2000L * attempt) // تأخير متزايد قبل كل إعادة محاولة
                }
            }
        }
        throw lastError ?: RuntimeException("فشل التنزيل بدون سبب محدد")
    }

    private fun extractZip(zip: File, outDir: File) {
        outDir.mkdirs()
        ZipFile(zip).use { z ->
            for (e in z.entries()) {
                val f = File(outDir, e.name)
                if (!f.canonicalPath.startsWith(outDir.canonicalPath)) continue
                if (e.isDirectory) f.mkdirs() else {
                    f.parentFile?.mkdirs()
                    z.getInputStream(e).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                }
            }
        }
    }

    private fun extractTarGz(tarGz: File, outDir: File) {
        outDir.mkdirs()
        TarArchiveInputStream(GzipCompressorInputStream(tarGz.inputStream())).use { tar ->
            while (true) {
                val entry = tar.nextTarEntry ?: break
                val f = File(outDir, entry.name)
                if (!f.canonicalPath.startsWith(outDir.canonicalPath)) continue
                if (entry.isDirectory) f.mkdirs() else {
                    f.parentFile?.mkdirs()
                    f.outputStream().use { out -> tar.copyTo(out) }
                }
            }
        }
    }

    private fun findSingleSubdir(dir: File): File? =
        dir.listFiles()?.firstOrNull { it.isDirectory }
}
