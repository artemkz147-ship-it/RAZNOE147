package ru.vibecut.editor

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

data class SubtitleFontOption(
    val key: String,
    val title: String,
)

data class ImportedSubtitleFont(
    val name: String,
    val path: String,
)

object SubtitleFontCatalog {
    private val candidates = listOf(
        SubtitleFontOption("sans-serif", "Системный"),
        SubtitleFontOption("sans-serif-medium", "Рубленый средний"),
        SubtitleFontOption("sans-serif-black", "Рубленый жирный"),
        SubtitleFontOption("sans-serif-light", "Рубленый лёгкий"),
        SubtitleFontOption("sans-serif-condensed", "Узкий"),
        SubtitleFontOption("sans-serif-condensed-medium", "Узкий средний"),
        SubtitleFontOption("sans-serif-condensed-black", "Узкий жирный"),
        SubtitleFontOption("serif", "С засечками"),
        SubtitleFontOption("monospace", "Моноширинный"),
        SubtitleFontOption("casual", "Свободный"),
        SubtitleFontOption("cursive", "Рукописный"),
    )

    fun availableSystemFonts(): List<SubtitleFontOption> =
        candidates.filter { option -> supportsRussian(Typeface.create(option.key, Typeface.NORMAL)) }
            .distinctBy { option -> Typeface.create(option.key, Typeface.NORMAL).toString() }

    fun resolve(style: SubtitleStyle): Typeface {
        val base = if (style.fontFilePath.isNotBlank()) {
            val file = File(style.fontFilePath)
            if (file.exists() && file.length() > 0L) {
                runCatching { Typeface.createFromFile(file) }.getOrNull()
            } else null
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
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.trim().orEmpty().ifBlank { "Пользовательский шрифт" }

        val extension = displayName.substringAfterLast('.', "ttf").lowercase().let {
            if (it == "ttf" || it == "otf") it else "ttf"
        }
        val dir = File(context.filesDir, "subtitle_fonts").apply { mkdirs() }
        val file = File(dir, "font_${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Не удалось открыть файл шрифта")
        if (file.length() == 0L) {
            file.delete()
            error("Файл шрифта пуст")
        }
        if (file.length() > 30L * 1024L * 1024L) {
            file.delete()
            error("Шрифт слишком большой")
        }
        val typeface = runCatching { Typeface.createFromFile(file) }.getOrElse {
            file.delete()
            error("Файл не распознан как TTF/OTF")
        }
        if (!supportsRussian(typeface)) {
            file.delete()
            error("В этом шрифте нет полного набора русской кириллицы")
        }
        return ImportedSubtitleFont(displayName.substringBeforeLast('.'), file.absolutePath)
    }

    fun supportsRussian(typeface: Typeface): Boolean {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = 42f }
        val sample = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя"
        return sample.all { char -> paint.hasGlyph(char.toString()) }
    }
}
