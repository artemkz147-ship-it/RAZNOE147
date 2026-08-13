package com.armsx2.config

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.armsx2.runtime.MainActivityRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kr.co.iefriends.pcsx2.NativeApp
import kotlin.math.max

/**
 * Conservative closed-loop tuner. Stability build never changes renderer,
 * thread placement, EE/VU timing or experimental core switches. It waits for a
 * fully established game session and then may change only internal resolution.
 */
object AdaptiveAutoTune {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var job: Job? = null

    private data class CoreLoad(
        val eeMs: Float,
        val gsMs: Float,
        val vuMs: Float,
        val gpuMs: Float,
    ) {
        val cpuCriticalMs: Float get() = max(eeMs, vuMs)
        val graphicsCriticalMs: Float get() = max(gsMs, gpuMs)

        fun clearlyCpuBound(): Boolean {
            if (!isUsable()) return false
            return cpuCriticalMs >= 4.0f && cpuCriticalMs > graphicsCriticalMs * 1.30f
        }

        fun isUsable(): Boolean =
            cpuCriticalMs.isFinite() && graphicsCriticalMs.isFinite() &&
                cpuCriticalMs > 0.05f && graphicsCriticalMs > 0.05f

        fun line(): String =
            "ee=${"%.2f".format(eeMs)}ms gs=${"%.2f".format(gsMs)}ms " +
                "vu=${"%.2f".format(vuMs)}ms gpu=${"%.2f".format(gpuMs)}ms"
    }

    fun start(gameKey: String?, boot: Settings) {
        stop()
        if (!AutoTune.isEnabled() || gameKey.isNullOrBlank()) return
        if (!boot.frameLimitEnable || boot.nominalSpeedPercent != 100) return

        val explicitGlobalScale = runCatching { ConfigStore.loadGlobal().upscaleFloat != 1.0f }.getOrDefault(false)
        val explicitGameScale = runCatching { ConfigStore.loadOverrides(gameKey)?.has("upscaleFloat") == true }.getOrDefault(false)
        if (explicitGlobalScale || explicitGameScale) return

        val context = MainActivityRuntime.instance?.applicationContext ?: return
        val cap = AutoTune.capability(context)
        val minScale = AutoTune.minScale(cap)
        val maxScale = AutoTune.maxScale(context)

        job = scope.launch {
            var currentScale = boot.upscaleFloat.coerceIn(minScale, maxScale)
            val samples = ArrayDeque<Float>(6)
            var cooldownUntil = 0L
            var lastCpuBoundLogAt = 0L

            // Stability hotfix: do nothing during BIOS, renderer creation, shader-cache
            // setup, early FMVs and initial GameDB patches. A crash in that phase can
            // therefore not be caused by the adaptive controller.
            delay(30_000)
            val initialSpeed = runCatching { NativeApp.getEmulationSpeed() }.getOrDefault(0f)
            if (!initialSpeed.isFinite() || initialSpeed < 5f) return@launch

            NativeApp.emulog("AUTOTUNE stable-start game=$gameKey scale=$currentScale ${AutoTune.diagnosticLine(context)}")

            while (isActive) {
                delay(2_000)
                val speedPercent = runCatching { NativeApp.getEmulationSpeed() }.getOrDefault(0f)
                if (!speedPercent.isFinite() || speedPercent < 5f) continue

                val speedRatio = (speedPercent / 100f).coerceIn(0f, 1.25f)
                samples.addLast(speedRatio)
                while (samples.size > 6) samples.removeFirst()
                if (samples.size < 5) continue

                val now = android.os.SystemClock.elapsedRealtime()
                if (now < cooldownUntil) continue

                val avg = samples.average().toFloat()
                val worst = samples.minOrNull() ?: avg
                val best = samples.maxOrNull() ?: avg
                val spread = best - worst
                val thermal = thermalStatus(context)
                val forecastHeadroom = thermalHeadroom(context, 10)
                val load = coreLoad()

                var next = currentScale
                var reason: String? = null
                var persistAdjustment = true

                if (forecastHeadroom != null && forecastHeadroom >= 0.92f && currentScale > minScale) {
                    val drop = if (forecastHeadroom >= 1.0f) 0.50f else 0.25f
                    next = (currentScale - drop).coerceAtLeast(minScale)
                    reason = "thermal forecast=${"%.2f".format(forecastHeadroom)}"
                    persistAdjustment = false
                } else if (thermal >= PowerManager.THERMAL_STATUS_SEVERE && currentScale > minScale) {
                    val drop = if (thermal >= PowerManager.THERMAL_STATUS_CRITICAL) 0.50f else 0.25f
                    next = (currentScale - drop).coerceAtLeast(minScale)
                    reason = "thermal status=$thermal"
                    persistAdjustment = false
                } else if (avg < 0.90f && currentScale > minScale) {
                    if (load.clearlyCpuBound()) {
                        if (now - lastCpuBoundLogAt >= 20_000L) {
                            NativeApp.emulog(
                                "AUTOTUNE CPU-bound: keep scale=$currentScale speed=${"%.1f".format(avg * 100f)}% ${load.line()}"
                            )
                            lastCpuBoundLogAt = now
                        }
                    } else {
                        val drop = if (avg < 0.78f) 0.50f else 0.25f
                        next = (currentScale - drop).coerceAtLeast(minScale)
                        reason = "slow speed=${"%.1f".format(avg * 100f)}% ${load.line()}"
                    }
                } else if (
                    thermal <= PowerManager.THERMAL_STATUS_LIGHT &&
                    (forecastHeadroom == null || forecastHeadroom < 0.75f) &&
                    avg >= 0.992f && worst >= 0.975f && spread <= 0.03f &&
                    currentScale < maxScale
                ) {
                    next = (currentScale + 0.25f).coerceAtMost(maxScale)
                    reason = "headroom speed=${"%.1f".format(avg * 100f)}%"
                }

                if (reason != null && next != currentScale) {
                    val old = currentScale
                    currentScale = next
                    runCatching { NativeApp.renderUpscalemultiplier(currentScale) }
                    if (persistAdjustment) AutoTune.saveLearnedScale(gameKey, currentScale)
                    NativeApp.emulog("AUTOTUNE scale $old -> $currentScale ($reason)")
                    samples.clear()
                    cooldownUntil = now + if (next < old) 14_000L else 28_000L
                } else if (
                    avg >= 0.96f && thermal < PowerManager.THERMAL_STATUS_MODERATE &&
                    (forecastHeadroom == null || forecastHeadroom < 0.85f)
                ) {
                    AutoTune.saveLearnedScale(gameKey, currentScale)
                }
            }
        }
    }

    fun stop() {
        val old = job ?: return
        job = null
        old.cancel()
    }

    suspend fun stopAndJoin() {
        val old = job ?: return
        job = null
        old.cancelAndJoin()
    }

    private fun coreLoad(): CoreLoad = CoreLoad(
        eeMs = runCatching { NativeApp.getCpuThreadTimeMs() }.getOrDefault(0f),
        gsMs = runCatching { NativeApp.getGsThreadTimeMs() }.getOrDefault(0f),
        vuMs = runCatching { NativeApp.getVuThreadTimeMs() }.getOrDefault(0f),
        gpuMs = runCatching { NativeApp.getGpuTimeMs() }.getOrDefault(0f),
    )

    private fun thermalStatus(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return PowerManager.THERMAL_STATUS_NONE
        return runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.currentThermalStatus
                ?: PowerManager.THERMAL_STATUS_NONE
        }.getOrDefault(PowerManager.THERMAL_STATUS_NONE)
    }

    private fun thermalHeadroom(context: Context, forecastSeconds: Int): Float? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val value = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.getThermalHeadroom(forecastSeconds)
        }.getOrNull() ?: return null
        return value.takeIf { it.isFinite() && it >= 0f }
    }
}
