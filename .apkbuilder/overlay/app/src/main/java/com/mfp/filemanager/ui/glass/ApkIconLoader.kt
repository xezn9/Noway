package com.mfp.filemanager.ui.glass

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import java.util.Collections
import java.util.concurrent.Executors

/** Reads the real launcher icon out of an .apk file (no install needed), cached in memory. */
object ApkIconLoader {
    private val cache = LruCache<String, Bitmap>(150)
    private val failed = Collections.synchronizedSet(HashSet<String>())
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun key(path: String, modified: Long) = "$path|$modified"

    fun cached(key: String): Bitmap? = cache.get(key)

    /** Calls [onResult] on the main thread with the icon, or never if the apk has no readable icon. */
    fun load(context: Context, path: String, key: String, onResult: (Bitmap) -> Unit) {
        if (failed.contains(key)) return
        val app = context.applicationContext
        pool.execute {
            val bmp = try {
                val pm = app.packageManager
                val info = pm.getPackageArchiveInfo(path, 0)
                val ai = info?.applicationInfo
                if (ai != null) {
                    ai.sourceDir = path
                    ai.publicSourceDir = path
                    ai.loadIcon(pm).toBitmap(144, 144, Bitmap.Config.ARGB_8888)
                } else null
            } catch (e: Throwable) {
                null
            }
            if (bmp != null) {
                cache.put(key, bmp)
                main.post { onResult(bmp) }
            } else {
                failed.add(key)
            }
        }
    }
}
