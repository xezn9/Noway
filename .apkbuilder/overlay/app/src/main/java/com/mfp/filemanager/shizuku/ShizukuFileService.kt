package com.mfp.filemanager.shizuku

import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.system.exitProcess

/**
 * Runs in the Shizuku user-service process (shell uid), which is allowed to read and write
 * Android/data and Android/obb. Needs a public no-arg constructor.
 */
class ShizukuFileService() : IShizukuFileService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun list(path: String): String? {
        val children = File(path).listFiles() ?: return null
        val sb = StringBuilder()
        for (f in children) {
            if (sb.isNotEmpty()) sb.append(ENTRY)
            val dir = f.isDirectory
            sb.append(if (dir) '1' else '0').append(FIELD)
                .append(if (dir) 0L else f.length()).append(FIELD)
                .append(f.lastModified()).append(FIELD)
                .append(if (dir) (f.list()?.size ?: 0) else 0).append(FIELD)
                .append(f.name)
        }
        return sb.toString()
    }

    override fun exists(path: String): Boolean = File(path).exists()

    override fun delete(path: String): Boolean = try {
        File(path).deleteRecursively()
    } catch (e: Exception) {
        false
    }

    override fun rename(from: String, to: String): Boolean = try {
        File(from).renameTo(File(to))
    } catch (e: Exception) {
        false
    }

    override fun mkdirs(path: String): Boolean = try {
        val f = File(path)
        f.isDirectory || f.mkdirs()
    } catch (e: Exception) {
        false
    }

    override fun openRead(path: String): ParcelFileDescriptor? = try {
        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
    } catch (e: Exception) {
        null
    }

    override fun openWrite(path: String): ParcelFileDescriptor? = try {
        ParcelFileDescriptor.open(
            File(path),
            ParcelFileDescriptor.MODE_WRITE_ONLY or
                ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE
        )
    } catch (e: Exception) {
        null
    }

    companion object {
        const val FIELD = '\u001F'
        const val ENTRY = '\u001E'
    }
}
