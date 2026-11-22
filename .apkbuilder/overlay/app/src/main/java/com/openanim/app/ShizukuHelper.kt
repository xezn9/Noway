package com.openanim.app

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuHelper {
    const val PKG = "moe.shizuku.privileged.api"

    fun installed(c: Context): Boolean = try {
        c.packageManager.getPackageInfo(PKG, 0)
        true
    } catch (e: Exception) {
        false
    }

    fun running(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    fun permitted(): Boolean = try {
        running() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    /**
     * تشغيل أمر shell بصلاحيات Shizuku (shell/ADB).
     * newProcess صارت private في الإصدار 13 فنستدعيها بالـ reflection.
     */
    fun shell(cmd: String): Pair<Int, String> {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        val code = p.waitFor()
        return code to (out + err)
    }
}
