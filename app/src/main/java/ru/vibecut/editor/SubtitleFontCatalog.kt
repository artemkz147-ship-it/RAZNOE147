package ru.vibecut.editor

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

enum class CyrillicCoverage(val title: String) {
    FULL("полная кириллица"),
    PARTIAL("частичная кириллица"),
    NONE("без кириллицы"),
}

data class SubtitleFontOption(
    val key: String,
    val title: String,
    val coverage: CyrillicCoverage,
)

data class ImportedSubtitleFont(
    val name: String,
    val path: String,
    val coverage: CyrillicCoverage,
)

object SubtitleFontCatalog {
    private val candidates = listOf(
        "sans-serif" to "Roboto / системный",
        "sans-serif-medium" to "Roboto Medium",
        "sans-serif-black" to "Roboto Black",
        "sans-serif-light" to "Roboto Light",
        "sans-serif-condensed" to "Roboto Condensed",
        "sans-serif-condensed-medium" to "Roboto Condensed Medium",
        "sans-serif-condensed-black" to "Roboto Condensed Black",
        "serif" to "Noto Serif / с засечками",
        "monospace" to "Roboto Mono / моноширинный",
        "casual" to "Свободный",
        "cursive" to "Рукописный",
    )

    fun availableSystemFonts(): List<SubtitleFontOption> = candidates.map { (key, title) ->
        SubtitleFontOption(key, title, coverage(Typeface.create(key, Typeface.NORMAL)))
    }.distinctBy { option -> option.key }

    fun resolve(style: SubtitleStyle): Typeface {
        val base = if (style.fontFilePath.isNotBlank()) {
            val file = File(style.fontFilePath)
            if (file.exists() && file.length() > 0L) runCatching { Typeface.createFromFile(file) }.getOrNull() else null
        } else null
        val fallback = base ?: Typeface.create(style.fontKey.ifBlank { "sans-serif" }, Typeface.NORMAL)
        val typefaceStyle = when {
            style.bold && style.italic -> Typeface.BOLD_ITALIC
            style.bold -> Typeface.BOLD
            style.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(fallback, typefaceStyle)
    }

    fun importFont(context: Context, uri: Uri): ImportedSubtitleFont {
        val displayName = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?.trim().orEmpty().ifBlank { "Пользовательский шрифт" }

        val extension = displayName.substringAfterLast('.', "ttf").lowercase().let {
            if (it == "ttf" || it == "otf") it else "ttf"
        }
        val dir = File(context.filesDir, "subtitle_fonts").apply { mkdirs() }
        val file = File(dir, "font_${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Не удалось открыть файл шрифта")
        if (file.length() == 0L) {
            file.delete(); error("Файл шрифта пуст")
        }
        if (file.length() > 30L * 1024L * 1024L) {
            file.delete(); error("Шрифт слишком большой")
        }
        val typeface = runCatching { Typeface.createFromFile(file) }.getOrElse {
            file.delete(); error("Файл не распознан как TTF/OTF")
        }
        return ImportedSubtitleFont(
            name = displayName.substringBeforeLast('.'),
            path = file.absolutePath,
            coverage = coverage(typeface),
        )
    }

    fun coverage(typeface: Typeface): CyrillicCoverage {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = 42f }
        val full = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя"
        val basic = "АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЫЭЮЯабвгдежзийклмнопрстуфхцчшщыэюя"
        return when {
            full.all { paint.hasGlyph(it.toString()) } -> CyrillicCoverage.FULL
            basic.count { paint.hasGlyph(it.toString()) } >= basic.length * 3 / 4 -> CyrillicCoverage.PARTIAL
            else -> CyrillicCoverage.NONE
        }
    }

    fun supportsRussian(typeface: Typeface): Boolean = coverage(typeface) == CyrillicCoverage.FULL
}
