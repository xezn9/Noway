package com.yourapp.apkbuilder

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class LogLevel(val color: Int) {
    INFO(Color.parseColor("#CCCCCC")),
    STEP(Color.parseColor("#90CAF9")),
    SUCCESS(Color.parseColor("#81C784")),
    ERROR(Color.parseColor("#E57373")),
    RAW(Color.parseColor("#888888"))
}

class MainActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var svLog: ScrollView
    private lateinit var progress: ProgressBar
    private lateinit var tvPercent: TextView
    private lateinit var btnBuild: Button
    private lateinit var btnSaveApk: Button

    private var pickedZip: File? = null
    private var outputApk: File? = null
    private var autoScroll = true
    private var askedStoragePermissionThisSession = false

    private val TERMUX_FDROID_URL = "https://f-droid.org/packages/com.termux/"

    // مجلد تخزين مشترك يقدر Termux يوصله (خارج filesDir الخاص بتطبيقنا)
    private val sharedRoot: File get() = File(Environment.getExternalStorageDirectory(), "ApkBuilderApp")
    private val sharedProjectDir: File get() = File(sharedRoot, "project")
    private val scriptFile: File get() = File(sharedRoot, "build_in_termux.sh")

    private val pickZip = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val dest = File(cacheDir, "project.zip")
        contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { out -> input.copyTo(out) } }
        pickedZip = dest
        btnBuild.isEnabled = true
        log("تم اختيار المشروع: ${dest.length() / 1024} KB", LogLevel.SUCCESS)
    }

    private val saveApk = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri: Uri? ->
        val apk = outputApk ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        contentResolver.openOutputStream(uri)?.use { out -> apk.inputStream().use { it.copyTo(out) } }
        log("تم الحفظ بنجاح.", LogLevel.SUCCESS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLog = findViewById(R.id.tvLog)
        svLog = findViewById(R.id.svLog)
        progress = findViewById(R.id.progress)
        tvPercent = findViewById(R.id.tvPercent)
        btnBuild = findViewById(R.id.btnBuild)
        btnSaveApk = findViewById(R.id.btnSaveApk)

        findViewById<Button>(R.id.btnPickZip).setOnClickListener { pickZip.launch("application/zip") }
        findViewById<Button>(R.id.btnScrollDown).setOnClickListener { autoScroll = true; scrollToBottom() }
        svLog.viewTreeObserver.addOnScrollChangedListener {
            val child = svLog.getChildAt(0) ?: return@addOnScrollChangedListener
            autoScroll = (child.bottom - (svLog.height + svLog.scrollY)) <= 24
        }

        btnBuild.setOnClickListener { runBuild() }
        btnSaveApk.setOnClickListener { saveApk.launch("app-release.apk") }
    }

    /** يتحقق من كل المتطلبات تلقائياً كل مرة يرجع فيها المستخدم للتطبيق (مثلاً بعد تنصيب Termux أو منح صلاحية) */
    override fun onResume() {
        super.onResume()
        checkAndRequestEverything()
    }

    private fun checkAndRequestEverything() {
        if (!TermuxBuilder.isTermuxInstalled(this)) {
            btnBuild.isEnabled = false
            showTermuxRequiredDialog()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            btnBuild.isEnabled = pickedZip != null
            if (!askedStoragePermissionThisSession) {
                askedStoragePermissionThisSession = true
                log("نطلب صلاحية الوصول لكل الملفات (ضرورية حتى يوصل Termux لمجلد المشروع)...", LogLevel.STEP)
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
            return
        }
        // كل شي جاهز
        btnBuild.isEnabled = pickedZip != null
    }

    /** نافذة تطلب تنصيب Termux، مع زر يفتح رابط التنزيل مباشرة */
    private fun showTermuxRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("تطبيق Termux غير مثبت")
            .setMessage("هذا التطبيق يحتاج Termux لتنفيذ عملية البناء الفعلية (يوفر بيئة متوافقة مع أندرويد).\n\nنزّله من F-Droid، ثم ارجع لهذا التطبيق.")
            .setCancelable(false)
            .setPositiveButton("تنزيل Termux") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TERMUX_FDROID_URL)))
            }
            .setNegativeButton("إعادة فحص") { _, _ -> checkAndRequestEverything() }
            .show()
        log("⚠ Termux غير مثبت. رابط التنزيل: $TERMUX_FDROID_URL", LogLevel.ERROR)
    }

    private fun scrollToBottom() = svLog.post { svLog.fullScroll(ScrollView.FOCUS_DOWN) }

    private fun log(text: String, level: LogLevel = LogLevel.INFO) = runOnUiThread {
        val line = SpannableStringBuilder("$text\n")
        line.setSpan(ForegroundColorSpan(level.color), 0, line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        tvLog.append(line)
        if (autoScroll) scrollToBottom()
    }

    private fun updateProgress(pct: Int) = runOnUiThread {
        progress.progress = pct.coerceIn(0, 100)
        tvPercent.text = "$pct%"
    }

    private fun runBuild() = lifecycleScope.launch {
        // تحقق أخير قبل البدء (تحسباً لو المستخدم رجع من الإعدادات بدون ما ينعاد فحصه بـ onResume لسبب ما)
        if (!TermuxBuilder.isTermuxInstalled(this@MainActivity)) { showTermuxRequiredDialog(); return@launch }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            checkAndRequestEverything(); return@launch
        }

        btnBuild.isEnabled = false
        autoScroll = true
        try {
            val zip = pickedZip!!
            sharedRoot.mkdirs()
            sharedProjectDir.deleteRecursively(); sharedProjectDir.mkdirs()

            withContext(Dispatchers.IO) {
                log("1/5 نسخ سكربت البناء لمجلد مشترك مع Termux...", LogLevel.STEP)
                assets.open("build_in_termux.sh").use { input ->
                    scriptFile.outputStream().use { out -> input.copyTo(out) }
                }
                scriptFile.setExecutable(true)

                log("2/5 فك ضغط المشروع...", LogLevel.STEP)
                ProjectUnzipper.unzip(zip, sharedProjectDir)

                log("3/5 تحليل متطلبات المشروع...", LogLevel.STEP)
                val req = ProjectRequirementsDetector.detect(sharedProjectDir)
                log("   Java المطلوب: ${req.javaVersion}")

                log("4/5 إرسال أمر البناء لـ Termux (قد يأخذ عدة دقائق أول مرة لتنصيب الحزم)...", LogLevel.STEP)
                val outApk = File(sharedRoot, "app-output.apk")
                outApk.delete()
                TermuxBuilder.runBuild(this@MainActivity, scriptFile.absolutePath, sharedProjectDir, outApk, req.javaVersion)

                log("5/5 بانتظار Termux يخلص البناء...", LogLevel.STEP)
                waitForBuildDone(sharedProjectDir, outApk)
            }
        } catch (e: Exception) {
            log("فشل البناء: ${e.message}", LogLevel.ERROR)
            e.printStackTrace()
        } finally {
            btnBuild.isEnabled = true
        }
    }

    /** يراقب ملف build.done وملف اللوق، ويطبع أي سطر جديد لحد ما يخلص البناء أو ينتهي الوقت */
    private suspend fun waitForBuildDone(projectDir: File, outApk: File) {
        val doneMark = File(projectDir, "build.done")
        val logFile = File(projectDir, "build.log")
        var lastSize = 0L
        val timeoutMs = 20 * 60 * 1000L
        val start = System.currentTimeMillis()

        while (System.currentTimeMillis() - start < timeoutMs) {
            if (logFile.exists() && logFile.length() > lastSize) {
                val newText = logFile.readText().substring(lastSize.toInt())
                lastSize = logFile.length()
                newText.lines().filter { it.isNotBlank() }.forEach { log(it, LogLevel.RAW) }
            }
            if (doneMark.exists()) {
                val result = doneMark.readText().trim()
                if (result == "DONE:0" && outApk.exists()) {
                    outputApk = outApk
                    log("تم إنشاء الملف: ${outApk.absolutePath} (${outApk.length() / 1024} KB)", LogLevel.SUCCESS)
                    log("جاهز. اضغط زر الحفظ.", LogLevel.SUCCESS)
                    runOnUiThread { btnSaveApk.isEnabled = true }
                } else {
                    log("فشل البناء داخل Termux (راجع السجل فوق للتفاصيل).", LogLevel.ERROR)
                }
                return
            }
            delay(1500)
        }
        log("انتهت مهلة الانتظار (20 دقيقة) بدون ما يخلص البناء. تأكد Termux مفعّل فيه allow-external-apps.", LogLevel.ERROR)
    }
}
