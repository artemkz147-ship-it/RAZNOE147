package com.armsx2.config

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.armsx2.runtime.MainActivityRuntime
import java.io.File

/**
 * One-time recovery for PS2 AutoTune 1.0.4.
 *
 * Early experimental builds could persist renderer/core values in SharedPreferences
 * and PCSX2-Android.ini. Installing a newer APK over them keeps that state, so a clean
 * upstream VM path can still inherit a broken old configuration. This migration clears
 * emulator configuration only. ROM folder grants, BIOS selection, memory cards,
 * savestates and user game files are preserved.
 */
object CleanCoreReset {
    private const val DONE_KEY = "ps2autotune.cleanCore104.done"

    fun runOnce(context: Context) {
        val prefs = MainActivityRuntime.prefs
        if (prefs.getBoolean(DONE_KEY, false)) return

        val keys = prefs.all.keys.toList()
        prefs.edit(commit = true) {
            for (key in keys) {
                if (
                    key == "config.global" ||
                    key.startsWith("config.game.") ||
                    key.startsWith("config.migrated.") ||
                    key.equals("renderer", ignoreCase = true) ||
                    key.equals("upscale", ignoreCase = true) ||
                    key.equals("upscaleFloat", ignoreCase = true) ||
                    key.equals("customDriverId", ignoreCase = true) ||
                    key.contains("adpf", ignoreCase = true) ||
                    key.contains("affinity", ignoreCase = true)
                ) {
                    remove(key)
                }
            }
            putBoolean(DONE_KEY, true)
        }

        // Remove only generated emulator configuration/mirror files. Saves, memory cards,
        // game images and folder permissions are deliberately untouched.
        val roots = linkedSetOf<File>()
        MainActivityRuntime.systemDirPosix()?.let { roots += File(it) }
        context.getExternalFilesDir(null)?.let { roots += it }
        roots += context.filesDir

        for (root in roots) {
            runCatching { File(root, "PCSX2-Android.ini").delete() }
            // Otherwise ConfigStore.reconcileReusedFolder can restore the just-cleared
            // global settings from the data-folder mirror on the same launch.
            runCatching { File(root, "armsx2-settings.json").delete() }
            // Shader caches are generated data. A stale cache from a renderer used by an
            // older build should not survive the compatibility reset.
            listOf("cache", "shadercache", "shaders/cache").forEach { rel ->
                runCatching {
                    val f = File(root, rel)
                    if (f.isDirectory) f.deleteRecursively() else f.delete()
                }
            }
        }

        Log.w("PS2AutoTune", "1.0.4 clean-core recovery applied; game folders/saves preserved")
    }
}
