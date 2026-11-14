package com.example.glassvolume

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * كل شي يخلّي التطبيق يشتغل دائماً:
 *  - الخدمة تشتغل كـ Foreground (إشعار صامت) حتى النظام ما يقتلها
 *  - إعفاء من توفير البطارية
 *  - التشغيل التلقائي (حسب شركة الجهاز)
 *  - بعد إعادة التشغيل أو التحديث: إعادة تفعيل خدمة الوصول تلقائياً (إذا انمنحت WRITE_SECURE_SETTINGS مرة وحدة بـ adb)
 */
object AlwaysOn {
    const val CHANNEL_ID = "glassvolume_keepalive"
    const val NOTIF_ID = 1001
    const val ADB_GRANT_CMD =
        "adb shell pm grant com.example.glassvolume android.permission.WRITE_SECURE_SETTINGS"

    /* ───────────── خدمة الوصول ───────────── */

    private fun serviceComponent(ctx: Context) =
        ComponentName(ctx, VolumeAccessibilityService::class.java)

    fun isServiceEnabled(ctx: Context): Boolean {
        val raw = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val me = serviceComponent(ctx)
        return raw.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun canWriteSecure(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** يرجّع true إذا الخدمة مفعّلة (أو فعّلناها الآن) */
    fun ensureEnabled(ctx: Context): Boolean {
        if (isServiceEnabled(ctx)) return true
        if (!canWriteSecure(ctx)) return false
        return try {
            val cr = ctx.contentResolver
            val cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val flat = serviceComponent(ctx).flattenToString()
            val next = if (cur.isEmpty()) flat else "$cur:$flat"
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** يفتح صفحة خدمتنا مباشرة (فيها مفتاح واحد) بدل قائمة كل الخدمات. القديم يفتح القائمة العامة */
    fun openAccessibilityPage(ctx: Context) {
        markReturn(ctx)
        val flat = serviceComponent(ctx).flattenToString()
        try {
            ctx.startActivity(
                Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                    .putExtra(Intent.EXTRA_COMPONENT_NAME, flat)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        } catch (_: Throwable) { }
        try {
            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) { }
    }

    /* ───────────── عدم الإزعاج ───────────── */

    fun hasDndAccess(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted ?: false

    /** يفتح صفحة تطبيقنا مباشرة (مفتاح واحد) إذا النظام يدعمها */
    fun openDndPage(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            } catch (_: Throwable) { }
        }
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) { }
    }

    /* ───────────── الرجوع للتطبيق تلقائياً بعد تفعيل خدمة الوصول ───────────── */

    private const val KEY_RETURN = "return_after_setup"

    private fun markReturn(ctx: Context) {
        ctx.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_RETURN, true).apply()
    }

    /** الخدمة تنادي هذا أول ما تشتغل: إذا المستخدم جاي من زر التفعيل نرجّعه للتطبيق */
    fun returnToAppIfWaiting(ctx: Context) {
        val prefs = ctx.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_RETURN, false)) return
        prefs.edit().putBoolean(KEY_RETURN, false).apply()
        try {
            ctx.startActivity(
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (_: Throwable) { }
    }

    /* ───────────── البطارية ───────────── */

    fun isIgnoringBattery(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) ?: false

    fun requestIgnoreBattery(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Throwable) { }
        }
    }

    /* ───────────── الإشعارات ───────────── */

    fun hasNotificationPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /* ───────────── التشغيل التلقائي (يختلف حسب الشركة) ───────────── */

    private val AUTOSTART_SCREENS = listOf(
        // Oppo / Realme / OnePlus (ColorOS)
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        // Xiaomi
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        // Huawei
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        // Vivo
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        // Samsung
        "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
    )

    /** يفتح شاشة التشغيل التلقائي إذا لقاها، وإلا يفتح معلومات التطبيق */
    fun openAutoStartSettings(ctx: Context) {
        for ((pkg, cls) in AUTOSTART_SCREENS) {
            try {
                ctx.startActivity(
                    Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            } catch (_: Throwable) { }
        }
        openAppDetails(ctx)
    }

    fun openAppDetails(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Throwable) { }
    }

    /* ───────────── الخدمة الأمامية (Foreground) ───────────── */

    private fun buildNotification(ctx: Context): Notification {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Glass Volume", NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                    description = "إشعار صامت يخلّي الخدمة شغّالة دائماً"
                }
            )
        }
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Glass Volume شغّال")
            .setContentText("لوحة الصوت الزجاجية جاهزة")
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(open)
            .build()
    }

    /** يخلّي خدمة الوصول Foreground. أي فشل (مثلاً قيود النظام) ما يوقّف الخدمة نفسها */
    fun startKeepAlive(service: Service) {
        try {
            val n = buildNotification(service)
            if (Build.VERSION.SDK_INT >= 34) {
                service.startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                service.startForeground(NOTIF_ID, n)
            }
        } catch (_: Throwable) { }
    }

    fun stopKeepAlive(service: Service) {
        try {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) { }
    }
}

/** بعد إعادة تشغيل الجهاز أو تحديث التطبيق: نعيد تفعيل خدمة الوصول إذا انمنحنا الصلاحية */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlwaysOn.ensureEnabled(context)
    }
}
