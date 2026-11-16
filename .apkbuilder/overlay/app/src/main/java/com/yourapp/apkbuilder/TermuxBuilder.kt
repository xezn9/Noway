package com.yourapp.apkbuilder

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.io.File

/**
 * يبني APK عن طريق تفويض التنفيذ الفعلي لتطبيق Termux (المثبت على جهاز المستخدم).
 * Termux عنده بيئة متوافقة مع Android (Bionic) وحزم مبنية خصيصاً لها
 * (openjdk-17/21, aapt2, d8, apksigner) فيحل مشكلتين دفعة وحدة:
 *   1) قيد noexec على ملفات filesDir
 *   2) عدم توافق بنايات Linux/glibc العادية مع بيئة أندرويد
 *
 * المتطلبات على جهاز المستخدم (مرة وحدة):
 *   - تثبيت Termux من F-Droid (مو من Play Store، نسخته قديمة ومكسورة)
 *   - بالـ Termux: تفعيل allow-external-apps=true بملف ~/.termux/termux.properties
 *     (أو termux-reload-settings بعد التعديل)
 *   - داخل Termux أول مرة: pkg install openjdk-17 aapt2 d8 apksigner zip
 */
object TermuxBuilder {

    private const val TERMUX_PKG = "com.termux"
    private const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"

    fun isTermuxInstalled(ctx: Context): Boolean =
        try { ctx.packageManager.getPackageInfo(TERMUX_PKG, 0); true }
        catch (e: PackageManager.NameNotFoundException) { false }

    /**
     * يشغل سكربت البناء داخل Termux بالخلفية (بدون فتح واجهة الطرفية).
     * sharedProjectDir و outputApk لازم يكونوا بمسار يقدر Termux يوصله فعلياً
     * (مجلد تخزين مشترك، مو filesDir الخاص بتطبيقنا، لأن كل تطبيق منعزل عن الثاني).
     */
    fun runBuild(ctx: Context, scriptPath: String, sharedProjectDir: File, outputApk: File, javaVersion: Int) {
        val intent = Intent(RUN_COMMAND_ACTION).apply {
            setClassName(TERMUX_PKG, "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf(
                scriptPath, sharedProjectDir.absolutePath, outputApk.absolutePath, javaVersion.toString()
            ))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", sharedProjectDir.absolutePath)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0") // ما يفتح واجهة الطرفية
        }
        // لازم startForegroundService على أندرويد 8+، وإلا أندرويد يرفض بدء خدمة تطبيق ثاني (Termux)
        // وهو ليس بالواجهة الأمامية حالياً ("app is in background"). Termux نفسه يتكفل يرفع
        // خدمته لـ foreground (يطلع إشعار) خلال ثواني حتى ما تصير ANR.
        ContextCompat.startForegroundService(ctx, intent)
    }
}
