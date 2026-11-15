package com.yourapp.apkbuilder

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnBuild: Button
    private lateinit var btnSaveApk: Button

    private var pickedZip: File? = null
    private var outputApk: File? = null

    // 1) اختيار ملف الـ zip
    private val pickZip = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val dest = File(cacheDir, "project.zip")
        contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { out -> input.copyTo(out) }
        }
        pickedZip = dest
        btnBuild.isEnabled = true
        log("تم اختيار المشروع: ${dest.length() / 1024} KB")
    }

    // 2) حفظ APK الناتج بمكان يختاره المستخدم
    private val saveApk = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri: Uri? ->
        val apk = outputApk ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        contentResolver.openOutputStream(uri)?.use { out -> apk.inputStream().use { it.copyTo(out) } }
        log("تم الحفظ بنجاح.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLog = findViewById(R.id.tvLog)
        progress = findViewById(R.id.progress)
        btnBuild = findViewById(R.id.btnBuild)
        btnSaveApk = findViewById(R.id.btnSaveApk)

        findViewById<Button>(R.id.btnPickZip).setOnClickListener { pickZip.launch("application/zip") }

        btnBuild.setOnClickListener { runBuild() }

        btnSaveApk.setOnClickListener {
            saveApk.launch("app-release.apk")
        }
    }

    private fun log(s: String) = runOnUiThread {
        tvLog.append("$s\n")
    }

    private fun runBuild() = lifecycleScope.launch {
        btnBuild.isEnabled = false
        try {
            val zip = pickedZip!!
            val projectDir = File(filesDir, "project").also { it.deleteRecursively(); it.mkdirs() }

            withContext(Dispatchers.IO) {
                log("1/6 فك ضغط المشروع...")
                ProjectUnzipper.unzip(zip, projectDir)

                log("2/6 تحليل متطلبات المشروع...")
                val req = ProjectRequirementsDetector.detect(projectDir)
                log("   Java المطلوب: ${req.javaVersion}")
                log("   compileSdk: ${req.compileSdk}  minSdk: ${req.minSdk}  buildTools: ${req.buildToolsVersion}")

                log("3/6 التحقق من الأدوات المحلية / تنزيل الناقص...")
                val toolchain = ToolchainManager(this@MainActivity) { pct, msg ->
                    runOnUiThread { progress.progress = pct; }
                    log(msg)
                }
                val paths = toolchain.ensureToolchain(req)

                log("4/6 بدء البناء...")
                val builder = ProjectBuilder(paths) { msg -> log(msg) }
                val apk = builder.build(projectDir, req)

                log("5/6 تم إنشاء الملف: ${apk.absolutePath} (${apk.length()/1024} KB)")
                outputApk = apk
            }

            log("6/6 جاهز. اضغط زر الحفظ.")
            btnSaveApk.isEnabled = true
        } catch (e: Exception) {
            log("فشل البناء: ${e.message}")
            e.printStackTrace()
        } finally {
            btnBuild.isEnabled = true
        }
    }
}
