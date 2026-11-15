package com.yourapp.apkbuilder

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.UnknownHostException

enum class LogLevel(val color: Int) {
    INFO(Color.parseColor("#CCCCCC")),
    STEP(Color.parseColor("#90CAF9")),     // أزرق فاتح لعناوين الخطوات
    SUCCESS(Color.parseColor("#81C784")),  // أخضر
    ERROR(Color.parseColor("#E57373")),    // أحمر
    RAW(Color.parseColor("#888888"))       // رمادي لمخرجات الأوامر الخام
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

    private val pickZip = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val dest = File(cacheDir, "project.zip")
        contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { out -> input.copyTo(out) }
        }
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
        findViewById<Button>(R.id.btnScrollDown).setOnClickListener {
            autoScroll = true
            scrollToBottom()
        }

        // لو المستخدم يسحب لأعلى يدوياً، نوقف النزول التلقائي لحد ما يضغط زر "آخر السجل" مرة ثانية
        svLog.viewTreeObserver.addOnScrollChangedListener {
            val child = svLog.getChildAt(0) ?: return@addOnScrollChangedListener
            val diff = (child.bottom - (svLog.height + svLog.scrollY))
            autoScroll = diff <= 24
        }

        btnBuild.setOnClickListener { runBuild() }
        btnSaveApk.setOnClickListener { saveApk.launch("app-release.apk") }
    }

    private fun scrollToBottom() {
        svLog.post { svLog.fullScroll(ScrollView.FOCUS_DOWN) }
    }

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
        btnBuild.isEnabled = false
        autoScroll = true
        try {
            val zip = pickedZip!!
            val projectDir = File(filesDir, "project").also { it.deleteRecursively(); it.mkdirs() }

            withContext(Dispatchers.IO) {
                log("1/6 فك ضغط المشروع...", LogLevel.STEP)
                ProjectUnzipper.unzip(zip, projectDir)

                log("2/6 تحليل متطلبات المشروع...", LogLevel.STEP)
                val req = ProjectRequirementsDetector.detect(projectDir)
                log("   Java المطلوب: ${req.javaVersion}")
                log("   compileSdk: ${req.compileSdk}  minSdk: ${req.minSdk}  buildTools: ${req.buildToolsVersion}")

                log("3/6 التحقق من الأدوات المحلية / تنزيل الناقص...", LogLevel.STEP)
                val toolchain = ToolchainManager(this@MainActivity) { pct, msg ->
                    updateProgress(pct)
                    log(msg, LogLevel.RAW)
                }
                val paths = toolchain.ensureToolchain(req)

                log("4/6 بدء البناء...", LogLevel.STEP)
                val builder = ProjectBuilder(paths) { msg -> log(msg, LogLevel.RAW) }
                val apk = builder.build(projectDir, req)

                log("5/6 تم إنشاء الملف: ${apk.absolutePath} (${apk.length() / 1024} KB)", LogLevel.SUCCESS)
                outputApk = apk
            }

            log("6/6 جاهز. اضغط زر الحفظ.", LogLevel.SUCCESS)
            btnSaveApk.isEnabled = true
        } catch (e: UnknownHostException) {
            log("فشل الاتصال: ما قدر الجهاز يوصل لـ ${e.message ?: "السيرفر"}.", LogLevel.ERROR)
            log("السبب غالباً DNS أو حجب من الشبكة/المزود — جرب تغيّر DNS الجهاز (8.8.8.8 أو 1.1.1.1)" +
                " من إعدادات الواي فاي، أو بدّل بين بيانات الموبايل والواي فاي، وأعد المحاولة.", LogLevel.ERROR)
        } catch (e: Exception) {
            log("فشل البناء: ${e.message}", LogLevel.ERROR)
            e.printStackTrace()
        } finally {
            btnBuild.isEnabled = true
        }
    }
}
