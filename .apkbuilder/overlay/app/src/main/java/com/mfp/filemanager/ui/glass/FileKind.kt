package com.mfp.filemanager.ui.glass

import android.graphics.Color
import com.mfp.filemanager.data.FileType

/** Silhouette of the glass badge. Every special file type gets its own one. */
enum class GlassShape { FOLDER, PAGE, SQUIRCLE, CIRCLE, HEXAGON, CHIP }

/** What is drawn on top of the glass. */
enum class GlassGlyph { NONE, LINES, BRACES, TAGS, ZIPPER, BOOKS, ANDROID, CHIP, PLAY, MOUNTAIN, NOTE }

/**
 * @param label fixed text shown under the glyph; null => the real extension of the file is shown
 */
enum class FileKind(
    val tint: Int,
    val shape: GlassShape,
    val glyph: GlassGlyph,
    val label: String? = null
) {
    // tint is replaced at draw time by the "App color" (see GlassFileIconDrawable)
    FOLDER(Color.parseColor("#4DA3FF"), GlassShape.FOLDER, GlassGlyph.NONE),

    // ---- the special ones ----
    APK(Color.parseColor("#3DDC84"), GlassShape.HEXAGON, GlassGlyph.ANDROID, "APK"),
    ZIP(Color.parseColor("#FFB020"), GlassShape.SQUIRCLE, GlassGlyph.ZIPPER, "ZIP"),
    RAR(Color.parseColor("#B05CFF"), GlassShape.CIRCLE, GlassGlyph.BOOKS, "RAR"),
    TEXT(Color.parseColor("#8E9BB0"), GlassShape.PAGE, GlassGlyph.LINES, "TXT"),
    DEX(Color.parseColor("#14C8C8"), GlassShape.CHIP, GlassGlyph.CHIP, "DEX"),
    XML(Color.parseColor("#FF5C8A"), GlassShape.PAGE, GlassGlyph.TAGS, "XML"),
    JSON(Color.parseColor("#F5A300"), GlassShape.PAGE, GlassGlyph.BRACES, "JSON"),

    // ---- the rest (still glass, so the whole list matches) ----
    ARCHIVE(Color.parseColor("#FF8A4C"), GlassShape.SQUIRCLE, GlassGlyph.ZIPPER),
    PDF(Color.parseColor("#FF4D4D"), GlassShape.PAGE, GlassGlyph.LINES, "PDF"),
    DOC(Color.parseColor("#3B82F6"), GlassShape.PAGE, GlassGlyph.LINES, "DOC"),
    SHEET(Color.parseColor("#22C55E"), GlassShape.PAGE, GlassGlyph.LINES, "XLS"),
    SLIDES(Color.parseColor("#FF7A2F"), GlassShape.PAGE, GlassGlyph.LINES, "PPT"),
    IMAGE(Color.parseColor("#34C3FF"), GlassShape.SQUIRCLE, GlassGlyph.MOUNTAIN),
    VIDEO(Color.parseColor("#9B6BFF"), GlassShape.SQUIRCLE, GlassGlyph.PLAY),
    AUDIO(Color.parseColor("#FF5CA8"), GlassShape.SQUIRCLE, GlassGlyph.NOTE),
    GENERIC(Color.parseColor("#6EA8FE"), GlassShape.PAGE, GlassGlyph.LINES);

    companion object {
        fun of(name: String, isDirectory: Boolean, type: FileType): FileKind {
            if (isDirectory) return FOLDER
            return when (extensionOf(name)) {
                "apk", "apks", "xapk", "apkm" -> APK
                "zip", "jar", "aar" -> ZIP
                "rar" -> RAR
                "txt", "log", "md", "ini", "cfg", "conf", "properties" -> TEXT
                "dex" -> DEX
                "xml", "html", "htm" -> XML
                "json", "jsonl" -> JSON
                "7z", "tar", "gz", "tgz", "bz2", "xz", "zst", "iso" -> ARCHIVE
                "pdf" -> PDF
                "doc", "docx", "odt", "rtf" -> DOC
                "xls", "xlsx", "csv", "ods" -> SHEET
                "ppt", "pptx", "odp" -> SLIDES
                else -> when (type) {
                    FileType.IMAGE -> IMAGE
                    FileType.VIDEO -> VIDEO
                    FileType.AUDIO -> AUDIO
                    FileType.APK -> APK
                    FileType.ARCHIVE -> ARCHIVE
                    else -> GENERIC
                }
            }
        }

        fun extensionOf(name: String): String =
            if (name.contains('.')) name.substringAfterLast('.').lowercase() else ""
    }
}
