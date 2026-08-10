package com.armsx2.config

import android.app.ActivityManager
import android.content.Context
import com.armsx2.DeviceTier
import com.armsx2.GpuInfo
import com.armsx2.runtime.MainActivityRuntime
import kotlin.math.round

/**
 * PS2 AutoTune capability + learned-quality store.
 *
 * Stability rule: NEVER rewrite renderer / MTVU / framebuffer paths / audio
 * SIMD or any other core setting before the VM has successfully booted.
 * ARMSX2/PCSX2 GameDB + user settings remain the complete launch-time source
 * of truth. AdaptiveAutoTune may later change only the live internal resolution,
 * which does not alter PS2 timing or renderer backend selection.
 */
object AutoTune {
    private const val PREF_ENABLED = "autotune.enabled"
    private const val PREF_MODE = "autotune.mode"
    private const val SCALE_PREFIX = "autotune.scale."

    enum class DeviceClass { LOW, MID, HIGH, ULTRA }

    data class Capability(
        val deviceClass: DeviceClass,
        val cores: Int,
        val ramGb: Float,
        val gpu: String,
        val soc: String,
        val isAdreno: Boolean,
        val isMali: Boolean,
        val maxBalancedScale: Float,
    )

    @Volatile private var cachedCapability: Capability? = null

    fun isEnabled(): Boolean =
        runCatching { MainActivityRuntime.prefs.getBoolean(PREF_ENABLED, true) }.getOrDefault(true)

    fun setEnabled(enabled: Boolean) {
        MainActivityRuntime.prefs.edit().putBoolean(PREF_ENABLED, enabled).apply()
    }

    fun mode(): String =
        runCatching { MainActivityRuntime.prefs.getString(PREF_MODE, "balanced") ?: "balanced" }
            .getOrDefault("balanced")

    fun setMode(mode: String) {
        val clean = when (mode.lowercase()) {
            "performance", "quality" -> mode.lowercase()
            else -> "balanced"
        }
        MainActivityRuntime.prefs.edit().putString(PREF_MODE, clean).apply()
    }

    fun capability(context: Context): Capability {
        cachedCapability?.let { return it }

        val cores = DeviceTier.coreCount()
        val ramBytes = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.totalMem
        }.getOrDefault(4_000_000_000L)
        val ramGb = ramBytes / 1_073_741_824f
        val gpu = GpuInfo.rendererName().orEmpty().ifBlank { "unknown" }
        val soc = DeviceTier.socModel()
        val isAdreno = gpu.contains("Adreno", ignoreCase = true)
        val isMali = gpu.contains("Mali", ignoreCase = true)

        var score = 0
        score += when {
            cores >= 8 -> 2
            cores >= 6 -> 1
            else -> -2
        }
        score += when {
            ramGb >= 10f -> 2
            ramGb >= 6f -> 1
            ramGb < 4f -> -2
            else -> 0
        }

        val adreno = Regex("""(?i)Adreno[^0-9]*(\d{3,4})""").find(gpu)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (adreno != null) {
            score += when {
                adreno >= 800 -> 4
                adreno >= 740 -> 3
                adreno >= 700 -> 2
                adreno >= 640 -> 1
                adreno < 600 -> -2
                else -> 0
            }
        }

        if (isMali) {
            score += when {
                Regex("""(?i)Mali-G7\d\d""").containsMatchIn(gpu) -> 2
                Regex("""(?i)Mali-G6\d\d""").containsMatchIn(gpu) -> 1
                Regex("""(?i)Mali-G5\d\d""").containsMatchIn(gpu) -> -1
                else -> 0
            }
        }

        val cls = when {
            DeviceTier.isLowEnd(context) || score <= -1 -> DeviceClass.LOW
            score <= 2 -> DeviceClass.MID
            score <= 5 -> DeviceClass.HIGH
            else -> DeviceClass.ULTRA
        }
        val scale = when (cls) {
            DeviceClass.LOW -> 1.0f
            DeviceClass.MID -> 1.5f
            DeviceClass.HIGH -> 2.0f
            DeviceClass.ULTRA -> 2.5f
        }
        return Capability(cls, cores, ramGb, gpu, soc, isAdreno, isMali, scale)
            .also { cachedCapability = it }
    }

    /**
     * Launch-time stability gate. Keep ARMSX2/PCSX2 settings byte-for-byte as
     * resolved by its normal config + GameDB layers. AutoTune starts only after
     * boot and only touches live internal resolution.
     */
    fun resolve(context: Context, gameKey: String?, base: Settings): Settings = base

    fun learnedScale(gameKey: String): Float? {
        val raw = runCatching {
            MainActivityRuntime.prefs.getFloat(SCALE_PREFIX + gameKey, Float.NaN)
        }.getOrDefault(Float.NaN)
        return raw.takeIf { it.isFinite() && it in 0.75f..5.0f }
    }

    fun saveLearnedScale(gameKey: String, scale: Float) {
        MainActivityRuntime.prefs.edit()
            .putFloat(SCALE_PREFIX + gameKey, quarterStep(scale).coerceIn(0.75f, 5.0f))
            .apply()
    }

    fun clearLearnedScale(gameKey: String) {
        MainActivityRuntime.prefs.edit().remove(SCALE_PREFIX + gameKey).apply()
    }

    fun minScale(capability: Capability): Float =
        if (capability.deviceClass == DeviceClass.LOW) 0.75f else 1.0f

    fun maxScale(context: Context): Float {
        val c = capability(context)
        val delta = when (mode()) {
            "performance" -> -0.5f
            "quality" -> 0.5f
            else -> 0f
        }
        return (c.maxBalancedScale + delta).coerceIn(minScale(c), 3.0f)
    }

    fun diagnosticLine(context: Context): String {
        val c = capability(context)
        return "AutoTune class=${c.deviceClass} cores=${c.cores} ram=${"%.1f".format(c.ramGb)}GB gpu=${c.gpu} soc=${c.soc} mode=${mode()} stableBoot=1"
    }

    private fun quarterStep(value: Float): Float = round(value * 4f) / 4f
}
