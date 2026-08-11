package com.armsx2.bios

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.core.content.edit
import com.armsx2.BiosInfo
import com.armsx2.runtime.MainActivityRuntime
import kr.co.iefriends.pcsx2.NativeApp
import java.io.File

/**
 * Installs BIOS images which are optionally present in APK assets/builtin_bios/.
 *
 * The public/source build deliberately contains no firmware files. A private
 * post-build may add user-supplied BIOS images to that asset directory without
 * changing code. On first launch every embedded candidate is copied into the
 * app-private BIOS folder and validated by emucore itself via getBiosInfoFromFd.
 * Invalid/truncated images are ignored instead of ever being selected for boot.
 *
 * Selection priority is Europe -> USA -> Japan -> any other valid region. An
 * already configured valid BIOS always wins, so the automatic installer never
 * overwrites a later manual choice.
 */
object BuiltinBios {
    private const val ASSET_DIR = "builtin_bios"
    private const val FILE_PREFIX = "builtin_"

    private data class Candidate(val file: File, val info: BiosInfo)

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
            val copied = copyAssetAtomically(context, "$ASSET_DIR/$assetName", dst)
            if (!copied) continue
            val info = probe(dst) ?: continue
            valid += Candidate(dst, info)
        }
        if (valid.isEmpty()) return null

        // Region values are the native BiosTools values: 0 JP, 1 USA, 2 EU.
        val chosen = valid.firstOrNull { it.info.region == 2 }
            ?: valid.firstOrNull { it.info.region == 1 }
            ?: valid.firstOrNull { it.info.region == 0 }
            ?: valid.first()

        MainActivityRuntime.bios.value = chosen.file.absolutePath
        MainActivityRuntime.prefs.edit { putString("bios", chosen.file.absolutePath) }
        runCatching {
            NativeApp.emulog(
                "BUILTIN_BIOS selected=${chosen.file.name} region=${chosen.info.region} " +
                    "version=${chosen.info.versionString} valid=${valid.size}/${assetNames.size}"
            )
        }
        return chosen.file.absolutePath
    }

    private fun probe(file: File): BiosInfo? {
        if (!file.isFile || file.length() < 512L) return null
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                NativeApp.getBiosInfoFromFd(pfd.detachFd())
            }
        }.getOrNull()
    }

    private fun copyAssetAtomically(context: Context, assetPath: String, dst: File): Boolean {
        // Keep an already extracted non-empty copy; validation below is still authoritative.
        if (dst.isFile && dst.length() > 0L) return true
        val tmp = File(dst.parentFile, ".${dst.name}.tmp")
        return runCatching {
            context.assets.open(assetPath).use { input ->
                tmp.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            if (dst.exists()) dst.delete()
            if (!tmp.renameTo(dst)) {
                tmp.inputStream().use { input -> dst.outputStream().use { output -> input.copyTo(output) } }
                tmp.delete()
            }
            dst.isFile && dst.length() > 0L
        }.getOrElse {
            runCatching { tmp.delete() }
            false
        }
    }
}
