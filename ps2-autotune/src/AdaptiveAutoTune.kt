package com.armsx2.config

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

        // Don't fight intentional caps / speed changes. FPS/nominal would otherwise
        // look like a performance failure even when the user requested it.
        if (!boot.frameLimitEnable || boot.fpsLimit > 0 || boot.nominalSpeedPercent != 100) return

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
                val fps = runCatching { NativeApp.getFPS() }.getOrDefault(0f)
                val nominal = runCatching { NativeApp.getNominalFrameRate() }.getOrDefault(0f)
                if (!fps.isFinite() || !nominal.isFinite() || fps < 5f || nominal < 20f) continue

                val ratio = (fps / nominal).coerceIn(0f, 1.25f)
                samples.addLast(ratio)
                while (samples.size > 6) samples.removeFirst()
                if (samples.size < 5) continue

                val now = android.os.SystemClock.elapsedRealtime()
                if (now < cooldownUntil) continue

                val avg = samples.average().toFloat()
                val worst = samples.minOrNull() ?: avg
                val best = samples.maxOrNull() ?: avg
                val spread = best - worst

                var next = currentScale
                var reason: String? = null

                // Sustained <90% native speed: lower resolution. Severe misses use a
                // half-step; otherwise quarter-step. Never go below the device floor.
                if (avg < 0.90f && currentScale > minScale) {
                    val drop = if (avg < 0.78f) 0.50f else 0.25f
                    next = (currentScale - drop).coerceAtLeast(minScale)
                    reason = "slow avg=${"%.3f".format(avg)}"
                }
                // Only raise quality when the title is both full-speed and stable.
                // Long cooldown prevents oscillation around a scene boundary.
                else if (avg >= 0.992f && worst >= 0.975f && spread <= 0.03f && currentScale < maxScale) {
                    next = (currentScale + 0.25f).coerceAtMost(maxScale)
                    reason = "headroom avg=${"%.3f".format(avg)}"
                }

                if (reason != null && next != currentScale) {
                    val old = currentScale
                    currentScale = next
                    runCatching { NativeApp.renderUpscalemultiplier(currentScale) }
                    AutoTune.saveLearnedScale(gameKey, currentScale)
                    NativeApp.emulog("AUTOTUNE scale $old -> $currentScale ($reason)")
                    samples.clear()
                    cooldownUntil = now + if (next < old) 14_000L else 28_000L
                } else if (avg >= 0.96f) {
                    // A stable working value is useful on the next launch even when no
                    // adjustment was necessary this pass.
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
}
