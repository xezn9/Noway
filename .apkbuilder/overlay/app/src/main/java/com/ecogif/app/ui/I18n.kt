package com.ecogif.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/** Current app language (snapshot state: every Text redraws when it changes). */
object I18n {
    var arabic by mutableStateOf(false)

    private val ar = mapOf(
        // tabs / headers
        "Resize" to "الحجم", "To GIF" to "إلى GIF", "To Video" to "إلى فيديو", "Settings" to "الإعدادات",
        "Resize & Crop" to "تغيير الحجم والقص", "Video → GIF" to "فيديو إلى GIF", "GIF → Video" to "GIF إلى فيديو",
        "Change a GIF's size or cut it with the crop box" to "غيّر حجم الـ GIF أو قصّه بمربع القص",
        "Turn a clip of a video into a GIF" to "حوّل مقطعاً من فيديو إلى GIF",
        "Save a GIF as an MP4 video" to "احفظ الـ GIF كفيديو MP4",
        "Language, splash, page colours and liquid glass" to "اللغة وشاشة البداية وألوان الصفحات والزجاج السائل",
        // pick cards
        "Pick a GIF to change its dimensions." to "اختر GIF لتغيير أبعاده.",
        "Choose GIF" to "اختيار GIF", "Choose another GIF" to "اختيار GIF آخر",
        "Video" to "فيديو", "Pick a video and turn a clip of it into a GIF." to "اختر فيديو وحوّل مقطعاً منه إلى GIF.",
        "Choose video" to "اختيار فيديو", "Choose another video" to "اختيار فيديو آخر",
        "Pick a GIF and save it as an MP4 video." to "اختر GIF واحفظه كفيديو MP4.",
        // resize
        "Done" to "تم", "Crop box" to "مربع القص", "Reset crop" to "إعادة ضبط القص", "crop" to "قص",
        "Drag any side or corner of the box to cut the GIF. Drag inside the box to move it." to
            "اسحب أي ضلع أو زاوية من المربع لقص الـ GIF، واسحب داخل المربع لتحريكه.",
        "output px" to "بكسل الناتج", "frames" to "إطار", "fps" to "إطار/ث", "px" to "بكسل", "s" to "ث",
        "New size" to "الحجم الجديد", "Scale" to "النسبة", "Width (px)" to "العرض (بكسل)", "Height (px)" to "الارتفاع (بكسل)",
        "Keep aspect ratio" to "الحفاظ على النسبة", "Changing one side updates the other" to "تغيير ضلع يحدّث الآخر",
        "Options" to "الخيارات", "Eco mode" to "الوضع الاقتصادي",
        "Drops every 2nd frame for a much smaller file" to "يحذف كل إطار ثانٍ لملف أصغر بكثير",
        "Dithering" to "التنعيم (Dithering)", "Smoother gradients, bigger file" to "تدرجات أنعم وملف أكبر",
        "Enter a valid size" to "أدخل حجماً صالحاً", "Resize & Save" to "تغيير الحجم وحفظ",
        // video -> gif
        "Clip" to "المقطع", "Start" to "البداية", "Length" to "المدة", "Quality" to "الجودة",
        "Frames per second" to "عدد الإطارات في الثانية", "Width" to "العرض", "Create GIF" to "إنشاء GIF",
        // gif -> video
        "MP4 result" to "ناتج MP4", "Size" to "الحجم", "Repeat the GIF" to "تكرار الـ GIF",
        "Convert to MP4" to "تحويل إلى MP4",
        "about" to "حوالي", "long" to "مدة", "repeat" to "تكرار", "size" to "حجم",
        // settings
        "General" to "عام", "Language" to "اللغة", "Splash screen" to "شاشة البداية",
        "Show the animated splash when the app opens" to "إظهار شاشة البداية المتحركة عند فتح التطبيق",
        "Page colors" to "ألوان الصفحات",
        "Choose a page, then a colour. This page shows a live preview of it." to
            "اختر صفحة ثم لوناً. هذه الصفحة تعرض معاينة مباشرة له.",
        "Liquid glass" to "الزجاج السائل", "Changes every glass surface in the app, live." to
            "يغيّر كل الأسطح الزجاجية في التطبيق مباشرة.",
        "Blur" to "الضبابية", "Transparency" to "الشفافية", "Refraction" to "الانكسار", "Reset glass" to "إعادة ضبط الزجاج",
        // colours
        "Green" to "أخضر", "Orange" to "برتقالي", "Blue" to "أزرق", "Red" to "أحمر", "Pink" to "وردي",
        "Purple" to "بنفسجي", "Teal" to "فيروزي", "Cyan" to "سماوي", "Lime" to "ليموني", "Yellow" to "أصفر",
        "Amber" to "كهرماني", "Coral" to "مرجاني", "Indigo" to "نيلي", "Violet" to "بنفسجي غامق",
        "Magenta" to "أرجواني", "Mint" to "نعناعي", "Sky" to "أزرق سماوي", "Gold" to "ذهبي",
        "Rose" to "وردي فاتح", "Slate" to "رمادي مزرق",
        // progress / dialogs
        "Resizing GIF…" to "جارٍ تغيير حجم الـ GIF…", "Creating GIF…" to "جارٍ إنشاء الـ GIF…",
        "Encoding MP4…" to "جارٍ ترميز MP4…", "Cancel" to "إلغاء", "Share" to "مشاركة", "Close" to "إغلاق",
        "OK" to "حسناً", "Done!" to "تم!", "Saved to" to "تم الحفظ في", "Something went wrong" to "حدث خطأ ما",
        "GIF plays at" to "يعرض الـ GIF بحد أقصى",
        "Could not read this GIF." to "تعذّرت قراءة هذا الـ GIF.",
        "Could not read this video." to "تعذّرت قراءة هذا الفيديو.",
        "Cannot write the output file" to "تعذّرت كتابة الملف الناتج",
        "No H.264 encoder on this device" to "لا يوجد مرمّز H.264 على هذا الجهاز",
        "Encoder has no input image" to "المرمّز لا يملك صورة إدخال",
        "Could not create the output file" to "تعذّر إنشاء الملف الناتج",
        "Could not open the file" to "تعذّر فتح الملف",
        "Unsupported video" to "فيديو غير مدعوم", "Not a GIF file" to "هذا ليس ملف GIF", "Bad GIF size" to "حجم GIF غير صالح",
        "Smaller GIFs, made simple" to "جيفات أخف، بكل بساطة"
    )

    /** Translates [s] when the app is in Arabic; unknown text is returned unchanged. */
    fun tr(s: String): String {
        if (!arabic) return s
        ar[s]?.let { return it }
        if (s.startsWith("File is too large (max ")) {
            return "الملف كبير جداً (الحد الأقصى " + s.removePrefix("File is too large (max ")
        }
        return s
    }
}

fun tr(s: String): String = I18n.tr(s)

/** Drop-in replacement for material3 Text that translates known UI strings. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
) {
    androidx.compose.material3.Text(
        text = I18n.tr(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        letterSpacing = letterSpacing,
        textAlign = textAlign
            ?: if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextAlign.Right else null,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines
    )
}
