package com.armsx2.bios

import android.content.Context
import androidx.core.content.edit
import com.armsx2.runtime.MainActivityRuntime
import java.io.File

/**
 * Installs optional user-supplied PS2 BIOS images from APK assets/builtin_bios/.
 *
 * IMPORTANT: this code deliberately does NOT call emucore/JNI. It runs while the
 * Android activity is still starting, before NativeApp.initializeOnce() installs
 * PCSX2's base settings layer. Calling BIOS parser JNI that early is unnecessary
 * and can destabilize startup on some devices.
 *
 * We only do a conservative ROM sanity check here: a normal PS2 BIOS dump is
 * exactly 4 MiB and contains the ROMVER entry plus a 14-character ROM version
 * record such as 0200EC20041104. The real core still performs its normal BIOS
 * loading/validation when the VM boots.
 */
object BuiltinBios {
    private const val ASSET_DIR = "builtin_bios"
    private const val FILE_PREFIX = "builtin_"
    private const val PS2_BIOS_SIZE = 4L * 1024L * 1024L
    private const val PROBE_BYTES = 256 * 1024

    enum class Region { JAPAN, USA, EUROPE, OTHER }

    data class Candidate(
        val file: File,
        val region: Region,
        val romVersion: String,
    )

    fun installIfPresent(context: Context): String? {
        val current = MainActivityRuntime.bios.value
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
        if (current?.isFile == true && probe(current) != null)
            return current.absolutePath

        val assetNames = runCatching { context.assets.list(ASSET_DIR)?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.endsWith(".bin", ignoreCase = true) || it.endsWith(".rom", ignoreCase = true) }
        if (assetNames.isEmpty()) return null

        val biosDir = MainActivityRuntime.internalBiosDir(context)
        if (!biosDir.exists() && !biosDir.mkdirs()) return null

        val valid = ArrayList<Candidate>()
        for (assetName in assetNames) {
            val safeName = assetName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val dst = File(biosDir, FILE_PREFIX + safeName)
            if (!copyAssetAtomically(context, "$ASSET_DIR/$assetName", dst)) continue
            val candidate = probe(dst) ?: run {
                runCatching { dst.delete() }
                continue
            }
            valid += candidate
        }
        if (valid.isEmpty()) return null

        // Prefer the modern European dump supplied for this private build.
        // USA is next if a valid one is supplied later; Japan remains a fallback.
        val chosen = valid.firstOrNull { it.region == Region.EUROPE }
            ?: valid.firstOrNull { it.region == Region.USA }
            ?: valid.firstOrNull { it.region == Region.JAPAN }
            ?: valid.first()

        MainActivityRuntime.bios.value = chosen.file.absolutePath
        MainActivityRuntime.prefs.edit { putString("bios", chosen.file.absolutePath) }
        android.util.Log.i(
            "PS2AutoTune",
            "Bundled BIOS selected ${chosen.file.name} ${chosen.romVersion} region=${chosen.region}"
        )
        return chosen.file.absolutePath
    }

    /** Pure-Java/Kotlin sanity probe; safe before native/emucore init. */
    private fun probe(file: File): Candidate? {
        if (!file.isFile || file.length() != PS2_BIOS_SIZE) return null

        return runCatching {
            val bytes = ByteArray(PROBE_BYTES)
            val count = file.inputStream().buffered().use { it.read(bytes) }
            if (count <= 0) return@runCatching null
            val text = bytes.copyOf(count).toString(Charsets.ISO_8859_1)
            if (!text.contains("ROMVER")) return@runCatching null

            // Sony ROMVER payload examples:
            //   0200EC20041104 = 2.00 Europe
            //   0100JC20000117 = 1.00 Japan
            // Fifth character identifies region (E/A/J/H/C/T...).
            val match = Regex("[0-9]{4}[A-Z][A-Z][0-9]{8}").find(text)
                ?: return@runCatching null
            val rom = match.value
            val region = when (rom.getOrNull(4)) {
                'E' -> Region.EUROPE
                'A' -> Region.USA
                'J' -> Region.JAPAN
                else -> Region.OTHER
            }
            Candidate(file, region, rom)
        }.getOrNull()
    }

    private fun copyAssetAtomically(context: Context, assetPath: String, dst: File): Boolean {
        if (dst.isFile && probe(dst) != null) return true
        val tmp = File(dst.parentFile, ".${dst.name}.tmp")
        return runCatching {
            context.assets.open(assetPath).use { input ->
                tmp.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            if (dst.exists()) dst.delete()
            if (!tmp.renameTo(dst)) {
                tmp.inputStream().use { input ->
                    dst.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                tmp.delete()
            }
            dst.isFile && dst.length() == PS2_BIOS_SIZE
        }.getOrElse {
            runCatching { tmp.delete() }
            false
        }
    }
}
