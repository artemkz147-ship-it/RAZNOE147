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

/**
 * Conservative closed-loop tuner for the one setting which is both measurable
 * and safe to change during gameplay: internal rendering resolution.
 *
 * IMPORTANT: tuning uses PerformanceMetrics::GetSpeed(), not rendered FPS.
 * A perfectly healthy 30 FPS PS2 title still runs at 100% emulation speed on a
 * 59.94 Hz virtual console. Using FPS/refresh here would incorrectly punish every
 * native-30-FPS title.
 *
 * CPU/VU timing hacks are intentionally NOT changed at runtime. A wrong cycle
 * hack can make a game faster but less correct; a resolution change cannot alter
 * PS2 game logic. This is what makes the learning loop suitable as a default.
 */
object AdaptiveAutoTune {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var job: Job? = null

    fun start(gameKey: String?, boot: Settings) {
        stop()
        if (!AutoTune.isEnabled() || gameKey.isNullOrBlank()) return

        // Don't fight intentional caps / speed changes. GetSpeed() would correctly
        // report the requested speed, but that target is not an AutoTune failure.
        if (!boot.frameLimitEnable || boot.nominalSpeedPercent != 100) return

        // Explicit global/per-game resolution means "hands off".
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

            // Let BIOS/boot logos, shader compilation and initial FMV settle. They are
            // poor representatives of the actual game workload.
            delay(12_000)
            NativeApp.emulog("AUTOTUNE start game=$gameKey scale=$currentScale ${AutoTune.diagnosticLine(context)}")

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

                var next = currentScale
                var reason: String? = null
                var persistAdjustment = true

                // If Android reports serious heat, lower GPU work before thermal throttling
                // turns into frame-time spikes. This is temporary and is deliberately NOT
                // learned as the game's normal profile.
                if (thermal >= PowerManager.THERMAL_STATUS_SEVERE && currentScale > minScale) {
                    val drop = if (thermal >= PowerManager.THERMAL_STATUS_CRITICAL) 0.50f else 0.25f
                    next = (currentScale - drop).coerceAtLeast(minScale)
                    reason = "thermal status=$thermal"
                    persistAdjustment = false
                }
                // Sustained <90% native emulation speed: lower resolution. Severe misses
                // use a half-step; otherwise quarter-step. Never go below device floor.
                else if (avg < 0.90f && currentScale > minScale) {
                    val drop = if (avg < 0.78f) 0.50f else 0.25f
                    next = (currentScale - drop).coerceAtLeast(minScale)
                    reason = "slow speed=${"%.1f".format(avg * 100f)}%"
                }
                // Only raise quality when emulation is full-speed, stable AND the
                // phone is not already warming up.
                else if (
                    thermal <= PowerManager.THERMAL_STATUS_LIGHT &&
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
                } else if (avg >= 0.96f && thermal < PowerManager.THERMAL_STATUS_MODERATE) {
                    // A stable working value is useful on the next launch even when no
                    // adjustment was necessary this pass. Never learn a heat-degraded value.
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

    private fun thermalStatus(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return PowerManager.THERMAL_STATUS_NONE
        return runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.currentThermalStatus
                ?: PowerManager.THERMAL_STATUS_NONE
        }.getOrDefault(PowerManager.THERMAL_STATUS_NONE)
    }
}
