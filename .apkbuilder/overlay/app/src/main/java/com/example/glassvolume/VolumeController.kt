package com.example.glassvolume

import android.content.Context
import android.media.AudioManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.roundToInt

/** إعدادات التطبيق (تتشارك بين الشاشة الرئيسية والخدمة) */
object Prefs {
    const val NAME = "glassvolume"
    /** تأخير ظهور اللوحة مع زر «خفض الصوت» فقط، حتى ما تنلقط بلقطة الشاشة (خفض + باور) */
    const val KEY_DOWN_DELAY = "down_delay_ms"
    const val DEFAULT_DOWN_DELAY = 180

    /** مدة أنيميشن إغلاق اللوحة (بالملي ثانية) لأي إغلاق (تلقائي، سحب، لمسة برّه) — السلايدر الزجاجي يتحكم بيها */
    const val KEY_CLOSE_DELAY = "close_duration_ms"
    const val DEFAULT_CLOSE_DELAY = 380
    const val MIN_CLOSE_DELAY = 120
    const val MAX_CLOSE_DELAY = 1500

    /** خلفية حية: الزجاج يتحرك مع الفيديو/التطبيق اللي تحت اللوحة (يحتاج أندرويد 14+) */
    const val KEY_LIVE_BG = "live_backdrop"

    /** وضع التطبيق (داكن/فاتح) */
    const val KEY_APP_DARK = "app_dark"

    /** السلايدر 0..1  (0 = أسرع إغلاق، 1 = أبطأ إغلاق) */
    fun fractionToCloseDelay(f: Float): Int =
        (MIN_CLOSE_DELAY + (MAX_CLOSE_DELAY - MIN_CLOSE_DELAY) * f.coerceIn(0f, 1f)).roundToInt()

    fun closeDelayToFraction(ms: Int): Float =
        ((ms - MIN_CLOSE_DELAY).toFloat() / (MAX_CLOSE_DELAY - MIN_CLOSE_DELAY)).coerceIn(0f, 1f)
}

/** يمسك حالة الصوت + نمط الرنين + لقطة الشاشة اللي تنرسم خلف الزجاج */
class VolumeController(private val ctx: Context) {
    val audio: AudioManager = ctx.getSystemService(AudioManager::class.java)
    private val prefs = ctx.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

    /** مدة أنيميشن الإغلاق (تنقرا كل مرة حتى أي تغيير من التطبيق ينطبق فوراً) */
    fun closeDurationMs(): Int =
        prefs.getInt(Prefs.KEY_CLOSE_DELAY, Prefs.DEFAULT_CLOSE_DELAY)
            .coerceIn(Prefs.MIN_CLOSE_DELAY, Prefs.MAX_CLOSE_DELAY)

    /** مظهر الزجاج + الألوان (من إعدادات التطبيق). نعيد قراءته كل مرة تظهر اللوحة حتى أي تغيير ينطبق فوراً */
    var style by mutableStateOf(GlassStyle.load(prefs))
    fun reloadStyle() { style = GlassStyle.load(prefs) }

    /** طلب إغلاق من الخدمة (لمسة خارج اللوحة، مثلاً سحب بين صفحات الشاشة الرئيسية) */
    var closeRequest by mutableIntStateOf(0)
    fun requestClose() { closeRequest++ }

    val streams = listOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_ALARM,
    )
    val levels = mutableStateMapOf<Int, Float>()
    var ringer by mutableIntStateOf(audio.ringerMode)
    var interaction by mutableIntStateOf(0)
    var dragging by mutableStateOf(false)
    var shot by mutableStateOf<ImageBitmap?>(null)

    /** لقطة حية لنافذة التطبيق اللي تحت اللوحة (تتجدد كل ~0.35 ثانية) + مكانها بالشاشة */
    var live by mutableStateOf<ImageBitmap?>(null)
    var liveRect by mutableStateOf<android.graphics.Rect?>(null)
    /** اللقطة الحية السابقة: نمزجها مع الجديدة (crossfade) حتى الحركة تبين أنعم من 3 لقطات بالثانية */
    var livePrev by mutableStateOf<ImageBitmap?>(null)
    var livePrevRect by mutableStateOf<android.graphics.Rect?>(null)
    var liveSeq by mutableIntStateOf(0)

    /** النافذة ظاهرة وفيها اللوحة (الخدمة تفعّلها لحظة الظهور، واللوحة تطفيها بعد ما تخلص أنيميشن الخروج) */
    var active by mutableStateOf(false)

    /** نبضة خفيفة كل ما ينضغط زر صوت واللوحة ظاهرة */
    var keyPulse by mutableIntStateOf(0)

    /** ارتداد مطّاطي لما توصل للحد الأعلى أو الأدنى وتكمّل تضغط */
    var edgeKick by mutableIntStateOf(0)
    var edgeDir = 0f

    /** آخر لقطة ناجحة + وقتها: نستخدمها إذا ضغطت زر مرتين بسرعة (بدون لقطة جديدة) */
    var lastShot: ImageBitmap? = null
    var lastShotAt = 0L

    init { refresh() }

    fun bump() { interaction++ }

    fun refresh() {
        streams.forEach { levels[it] = audio.getStreamVolume(it).toFloat() / audio.getStreamMaxVolume(it) }
        ringer = audio.ringerMode
        bump()
    }

    /** يرجّع true إذا الصوت تغيّر فعلاً (false = وصلنا للحد الأعلى/الأدنى) */
    fun adjustMusic(direction: Int): Boolean {
        val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        val after = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (before == after) {
            edgeDir = if (direction == AudioManager.ADJUST_RAISE) 1f else -1f
            edgeKick++
        }
        keyPulse++
        refresh()
        return before != after
    }

    fun setLevel(stream: Int, fraction: Float) {
        val max = audio.getStreamMaxVolume(stream)
        levels[stream] = fraction
        try {
            audio.setStreamVolume(stream, (fraction * max).roundToInt().coerceIn(0, max), 0)
        } catch (_: SecurityException) { }
        ringer = audio.ringerMode
        bump()
    }

    /** زر واحد: مفتوح -> اهتزاز -> صامت -> مفتوح */
    fun cycleRinger() {
        val next = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        try {
            audio.ringerMode = next
        } catch (_: SecurityException) {
            audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
            Toast.makeText(ctx, "فعّل صلاحية «عدم الإزعاج» للتطبيق حتى يشتغل الصامت", Toast.LENGTH_LONG).show()
        }
        refresh()
    }
}
