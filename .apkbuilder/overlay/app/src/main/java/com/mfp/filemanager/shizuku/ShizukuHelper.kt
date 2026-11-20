package com.mfp.filemanager.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku
import java.io.InputStream
import java.io.OutputStream

enum class ShizukuStatus {
    /** Shizuku app is not on this device. */
    NOT_INSTALLED,
    /** Installed, but the Shizuku service is not started. */
    NOT_RUNNING,
    /** Running, our app has no permission yet. */
    NEED_PERMISSION,
    /** Permission granted, binding the file service. */
    CONNECTING,
    /** File service is ready: Android/data and Android/obb are reachable. */
    READY
}

/** One entry returned by the privileged service. */
data class PrivEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val itemCount: Int
)

/**
 * Detects Shizuku, asks for permission and keeps a connection to [ShizukuFileService].
 * Used by FileRepository for paths the app cannot touch itself (Android/data, Android/obb).
 */
object ShizukuHelper {

    const val PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 4242

    private val _status = MutableStateFlow(ShizukuStatus.NOT_INSTALLED)
    val status: StateFlow<ShizukuStatus> = _status

    @Volatile
    private var service: IShizukuFileService? = null

    private var appContext: Context? = null
    private var binding = false
    private var permissionAsked = false

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(appContext!!.packageName, ShizukuFileService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("files")
            .debuggable(false)
            .version(1)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            binding = false
            if (binder != null && binder.pingBinder()) {
                service = IShizukuFileService.Stub.asInterface(binder)
                _status.value = ShizukuStatus.READY
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            binding = false
            refresh()
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        service = null
        binding = false
        refresh()
    }
    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) refresh()
        }

    /** Call once from Application.onCreate. */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        refresh()
    }

    private fun isInstalled(): Boolean {
        val ctx = appContext ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.packageManager.getPackageInfo(PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                ctx.packageManager.getPackageInfo(PACKAGE, 0)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Throwable) {
        false
    }

    private fun hasPermission(): Boolean = try {
        !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) {
        false
    }

    /** Re-evaluates the state; binds the file service as soon as everything is in place. */
    fun refresh() {
        if (appContext == null) return
        when {
            !isRunning() -> {
                service = null
                _status.value = if (isInstalled()) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED
            }
            !hasPermission() -> {
                service = null
                _status.value = ShizukuStatus.NEED_PERMISSION
            }
            service != null -> _status.value = ShizukuStatus.READY
            else -> {
                _status.value = ShizukuStatus.CONNECTING
                bind()
            }
        }
    }

    private fun bind() {
        if (binding) return
        binding = true
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (e: Throwable) {
            binding = false
            e.printStackTrace()
        }
    }

    /** Shows Shizuku's own permission dialog (once per process, unless the user refused for good). */
    fun requestPermissionIfNeeded(): Boolean {
        if (_status.value != ShizukuStatus.NEED_PERMISSION) return false
        return try {
            if (Shizuku.shouldShowRequestPermissionRationale()) return false // "deny and don't ask again"
            if (permissionAsked) return false
            permissionAsked = true
            Shizuku.requestPermission(REQUEST_CODE)
            true
        } catch (e: Throwable) {
            false
        }
    }

    /** Opens the Shizuku app so the user can start it. */
    fun openShizukuApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // ------------------------------------------------------------------ path helpers

    private val restricted = Regex("^/(storage/emulated|sdcard|data/media)(/\\d+)?/Android/(data|obb)(/.*)?$", RegexOption.IGNORE_CASE)

    /** Android/data and Android/obb of the primary storage (and nothing else). */
    fun isRestrictedPath(path: String): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        return restricted.matches(path.trimEnd('/'))
    }

    /** The service when [path] needs it AND it is ready, otherwise null (use normal file access). */
    fun serviceFor(path: String): IShizukuFileService? =
        if (isRestrictedPath(path)) service else null

    // ------------------------------------------------------------------ operations

    fun list(path: String): List<PrivEntry>? {
        val raw = serviceFor(path)?.list(path) ?: return null
        if (raw.isEmpty()) return emptyList()
        return raw.split(ShizukuFileService.ENTRY).mapNotNull { line ->
            val p = line.split(ShizukuFileService.FIELD, limit = 5)
            if (p.size < 5) null
            else PrivEntry(
                name = p[4],
                isDirectory = p[0] == "1",
                size = p[1].toLongOrNull() ?: 0L,
                lastModified = p[2].toLongOrNull() ?: 0L,
                itemCount = p[3].toIntOrNull() ?: 0
            )
        }
    }

    fun exists(path: String): Boolean = serviceFor(path)?.exists(path) ?: java.io.File(path).exists()

    fun isDirectory(path: String): Boolean {
        val parent = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        val svc = serviceFor(parent)
        if (svc == null) return java.io.File(path).isDirectory
        return list(parent)?.firstOrNull { it.name == name }?.isDirectory == true
    }

    fun delete(path: String): Boolean = serviceFor(path)?.delete(path) ?: java.io.File(path).deleteRecursively()

    fun rename(from: String, to: String): Boolean = serviceFor(from)?.rename(from, to) ?: java.io.File(from).renameTo(java.io.File(to))

    fun mkdirs(path: String): Boolean = serviceFor(path)?.mkdirs(path) ?: java.io.File(path).let { it.isDirectory || it.mkdirs() }

    fun openRead(path: String): InputStream {
        val svc = serviceFor(path) ?: return java.io.FileInputStream(path)
        val pfd: ParcelFileDescriptor = svc.openRead(path) ?: throw java.io.IOException("Cannot read $path")
        return ParcelFileDescriptor.AutoCloseInputStream(pfd)
    }

    fun openWrite(path: String): OutputStream {
        val svc = serviceFor(path) ?: return java.io.FileOutputStream(path)
        val pfd: ParcelFileDescriptor = svc.openWrite(path) ?: throw java.io.IOException("Cannot write $path")
        return ParcelFileDescriptor.AutoCloseOutputStream(pfd)
    }
}
