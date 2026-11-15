package com.yourapp.apkbuilder

import java.io.File
import java.util.zip.ZipFile

object ProjectUnzipper {
    /** يفك ملف zip المشروع بحماية من Zip Slip */
    fun unzip(zip: File, outDir: File) {
        outDir.mkdirs()
        ZipFile(zip).use { z ->
            for (entry in z.entries()) {
                val target = File(outDir, entry.name)
                if (!target.canonicalPath.startsWith(outDir.canonicalPath + File.separator)) {
                    throw SecurityException("مسار غير آمن داخل zip: ${entry.name}")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    z.getInputStream(entry).use { input ->
                        target.outputStream().use { out -> input.copyTo(out) }
                    }
                }
            }
        }
    }
}
