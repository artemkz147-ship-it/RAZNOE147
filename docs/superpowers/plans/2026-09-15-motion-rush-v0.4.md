# Motion Rush v0.4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn Motion Rush v0.3 into a substantially more responsive full-body runner with continuous motion state, adaptive filtering/calibration, authored gameplay patterns, richer procedural 3D animation/zones, diagnostics, and reproducible Android CI.

**Architecture:** Keep CameraX + MediaPipe native on Android and Three.js/WebGL for gameplay/rendering. Refactor native tracking into typed pose/motion state + discrete confirmed actions, send motion snapshots to WebGL at a controlled rate, and interpolate them on render frames. Split gameplay patterns, motion interpolation, diagnostics, and renderer responsibilities so each subsystem is independently testable.

**Tech Stack:** Kotlin 2.0.21, AndroidX CameraX 1.6.2, MediaPipe Tasks Vision 0.10.35, Android WebView/WebViewAssetLoader, Three.js 0.186.0, ES modules, Node built-in test runner, JUnit 4, GitHub Actions, Android API 36/JDK 17/Gradle 8.11.1.

**Spec:** `docs/superpowers/specs/2026-09-15-motion-rush-v0.4-design.md`

## Global Constraints

- Front camera stays native through CameraX with `STRATEGY_KEEP_ONLY_LATEST`.
- Analysis target is approximately 640x480 and must never introduce a queued-frame backlog.
- Preferred backend order is GPU Full -> GPU Lite -> CPU Full/Lite according to initialization/latency -> CPU Lite fallback.
- Critical runtime assets remain local inside the APK.
- Continuous motion state and discrete confirmed actions are separate channels.
- Low-confidence pose data suppresses actions instead of guessing.
- Three.js rendering must prioritize stable frame pacing; repeated geometry uses pooling/instancing where practical.
- GitHub Actions must run JavaScript tests, Kotlin unit tests, Android debug build, and upload the APK.
- Do not deliver the APK unless the same final commit passes all required CI checks.

---

### Task 1: Typed pose data, adaptive filtering, and multi-frame calibration

**Files:**
- Create: `MotionRush/android/app/src/main/java/com/openai/bodyrunner/MotionTypes.kt`
- Create: `MotionRush/android/app/src/main/java/com/openai/bodyrunner/OneEuroFilter.kt`
- Create: `MotionRush/android/app/src/main/java/com/openai/bodyrunner/MotionStateEstimator.kt`
- Modify: `MotionRush/android/app/src/main/java/com/openai/bodyrunner/MotionGestureEngine.kt`
- Create: `MotionRush/android/app/src/test/java/com/openai/bodyrunner/OneEuroFilterTest.kt`
- Create: `MotionRush/android/app/src/test/java/com/openai/bodyrunner/MotionStateEstimatorTest.kt`
- Modify: `MotionRush/android/app/src/test/java/com/openai/bodyrunner/MotionGestureEngineTest.kt`

**Interfaces:**
- Produces `data class MotionPoint(val x: Float, val y: Float, val z: Float = 0f, val confidence: Float = 1f)`.
- Produces `data class MotionPose(...)` with nose, shoulders, elbows, wrists, hips, knees, ankles.
- Produces `data class MotionState(centerX, centerY, verticalVelocity, torsoLean, leftArmElevation, rightArmElevation, leftArmExtension, rightArmExtension, leftWristVelocity, rightWristVelocity, leftElbowAngle, rightElbowAngle, hipDelta, kneeCompression, jumpImpulse, trackingConfidence, timestampMs)`.
- Produces `data class MotionFrame(val state: MotionState, val actions: List<String>)`.
- `MotionStateEstimator.push(pose: MotionPose, nowMs: Long): MotionFrame?` is the native hot-path API.
- `MotionStateEstimator.addCalibrationSample(pose: MotionPose): Boolean` accumulates stable samples; `finishCalibration(): Boolean` commits the baseline.

- [ ] **Step 1: Add failing adaptive-filter tests**

```kotlin
@Test fun staticNoiseIsSmoothedButFastMotionRemainsResponsive() {
    val f = OneEuroFilter(minCutoff = 1.2f, beta = 0.12f, derivativeCutoff = 1.0f)
    val quiet = listOf(0.500f, 0.507f, 0.494f, 0.503f).mapIndexed { i, v -> f.filter(v, i * 0.033f) }
    assertTrue(quiet.maxOrNull()!! - quiet.minOrNull()!! < 0.010f)
    val fast = f.filter(0.75f, 0.165f)
    assertTrue(fast > 0.64f)
}
```

Run: `cd MotionRush/android && gradle testDebugUnitTest --tests '*OneEuroFilterTest*'`
Expected: FAIL because `OneEuroFilter` does not exist.

- [ ] **Step 2: Implement `OneEuroFilter`**

Use the canonical velocity-adaptive cutoff:

```kotlin
internal class OneEuroFilter(
    private val minCutoff: Float,
    private val beta: Float,
    private val derivativeCutoff: Float,
) {
    private var lastTime: Float? = null
    private var lastRaw: Float? = null
    private var lastFiltered: Float? = null
    private var filteredDerivative = 0f

    fun filter(value: Float, timeSeconds: Float): Float {
        val previousTime = lastTime
        if (previousTime == null) {
            lastTime = timeSeconds; lastRaw = value; lastFiltered = value
            return value
        }
        val dt = (timeSeconds - previousTime).coerceIn(1f / 240f, .25f)
        val derivative = (value - (lastRaw ?: value)) / dt
        filteredDerivative = lowPass(filteredDerivative, derivative, alpha(derivativeCutoff, dt))
        val cutoff = minCutoff + beta * kotlin.math.abs(filteredDerivative)
        val out = lowPass(lastFiltered ?: value, value, alpha(cutoff, dt))
        lastTime = timeSeconds; lastRaw = value; lastFiltered = out
        return out
    }
    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * Math.PI.toFloat() * cutoff.coerceAtLeast(.01f))
        return 1f / (1f + tau / dt)
    }
    private fun lowPass(previous: Float, current: Float, a: Float) = previous + a * (current - previous)
}
```

Run the filter test again; expected PASS.

- [ ] **Step 3: Add failing calibration/confidence/state tests**

Add tests proving: 10 stable calibration samples succeed; one noisy/outlier frame cannot dominate baseline; confidence below 0.45 suppresses a frame; sustained lean does not alter baseline quickly; fast upward hip velocity raises `jumpImpulse`; static extended arm does not punch.

Run: `cd MotionRush/android && gradle testDebugUnitTest --tests '*MotionStateEstimatorTest*'`
Expected: FAIL before estimator implementation.

- [ ] **Step 4: Implement `MotionTypes` and `MotionStateEstimator`**

Use separate One-Euro filters for body center (`minCutoff=1.0,beta=.08`), wrists (`1.35,.20`), and ankles (`1.1,.12`). Compute confidence as the minimum/weighted average of required joints and reject a state below 0.45. Calibration uses a 10-frame mean after rejecting samples whose center differs by >0.04 or shoulder width by >15% from the running mean. Adaptive baseline updates at <=0.35% per accepted neutral frame and freezes while `abs(torsoLean) > .22`, `abs(verticalVelocity) > .35`, knee compression > .18, or wrist velocity > .9.

- [ ] **Step 5: Refactor `MotionGestureEngine` to consume `MotionState`**

Preserve actions `MOVE_LEFT`, `MOVE_RIGHT`, `JUMP`, `CROUCH`, `PUNCH_LEFT`, `PUNCH_RIGHT`, `RAISE_LEFT`, `RAISE_RIGHT`, `RAISE_BOTH`. Lane commit uses displacement + lateral velocity; jump uses `jumpImpulse`; crouch uses hip/knee compression; punches use wrist velocity + elbow angle + extension; raised-hand actions retain shorter hold/debounce.

Run: `cd MotionRush/android && gradle testDebugUnitTest`
Expected: all native tests PASS.

- [ ] **Step 6: Commit**

Commit message: `feat: add adaptive motion state estimator`

---

### Task 2: MediaPipe confidence/world landmarks and resilient backend selection

**Files:**
- Modify: `MotionRush/android/app/src/main/java/com/openai/bodyrunner/PoseLandmarkerHelper.kt`
- Modify: `MotionRush/android/app/src/test/java/com/openai/bodyrunner/MotionStateEstimatorTest.kt`

**Interfaces:**
- `PoseLandmarkerHelper.Listener.onPose(pose: MotionPose, timestampMs: Long, latencyMs: Long)` replaces width/height arguments.
- `PoseLandmarkerHelper.backendLabel` exposes exact selected backend.
- Normalized points are mirrored for the front camera; `z` prefers world-landmark depth when available.

- [ ] **Step 1: Add mapper-level tests through extracted pure helper functions**

Test that confidence is carried from MediaPipe visibility/presence values, mirrored X uses `1f - x`, and low-confidence joints remain low-confidence rather than being normalized away.

- [ ] **Step 2: Replace two-option initialization with ordered candidates**

```kotlin
private val candidates = listOf(
    Backend("GPU FULL", "pose_landmarker_full.task", Delegate.GPU),
    Backend("GPU LITE", "pose_landmarker_lite.task", Delegate.GPU),
    Backend("CPU FULL", "pose_landmarker_full.task", Delegate.CPU),
    Backend("CPU LITE", "pose_landmarker_lite.task", Delegate.CPU),
)
```

Try in order, record failure messages, throw only after every candidate fails.

- [ ] **Step 3: Map normalized + world landmarks and confidence**

`MotionPoint.confidence` is `min(visibility,presence)` where available; `z` uses world landmark Z, falling back to normalized Z. Track submission time by timestamp so result latency is `SystemClock.uptimeMillis() - submittedAt[timestamp]`.

- [ ] **Step 4: Keep the hot path bounded**

Maintain latest-frame throttling. Reuse the RGBA bitmap. Rotation/mirroring remains unavoidable in the current MediaPipe Bitmap API path but must not allocate any additional scratch arrays/maps per frame other than bounded timestamp bookkeeping.

Run: `cd MotionRush/android && gradle testDebugUnitTest assembleDebug`
Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `perf: improve pose backend and landmark quality`

---

### Task 3: Continuous native-to-Web motion bridge and diagnostics metrics

**Files:**
- Modify: `MotionRush/android/app/src/main/java/com/openai/bodyrunner/MainActivity.kt`
- Create: `MotionRush/android/app/src/main/assets/web/src/motion-state.mjs`
- Create: `MotionRush/web-test/motion-state.test.mjs`
- Modify: `MotionRush/android/app/src/main/assets/web/src/main.mjs`

**Interfaces:**
- Native callback: `window.onNativeMotionState(jsonString)` at max ~30 Hz.
- Web module exports `createMotionInterpolator()` with methods `push(snapshot, receivedAtMs)` and `sample(nowMs)`.
- Discrete native callback remains `window.onNativeMotionAction(action)` and is immediate.

- [ ] **Step 1: Add failing interpolation tests**

```js
import test from 'node:test';
import assert from 'node:assert/strict';
import { createMotionInterpolator } from '../android/app/src/main/assets/web/src/motion-state.mjs';

test('interpolates center and lean between snapshots', () => {
  const m = createMotionInterpolator();
  m.push({t:1000, centerX:0, torsoLean:0, confidence:1}, 1000);
  m.push({t:1033, centerX:1, torsoLean:.5, confidence:1}, 1033);
  const s = m.sample(1016.5);
  assert.ok(s.centerX > .4 && s.centerX < .6);
  assert.ok(s.torsoLean > .2 && s.torsoLean < .3);
});
```

Run: `node --test MotionRush/web-test/motion-state.test.mjs`
Expected: FAIL before module exists.

- [ ] **Step 2: Implement bounded interpolation/prediction**

Keep only previous/current snapshots. Interpolate by timestamps and permit prediction no more than 45ms using center/vertical/wrist velocity; clamp all predicted normalized values to sane ranges. Confidence decays if no snapshot arrives for >120ms.

- [ ] **Step 3: Serialize compact motion JSON natively**

Add `sendMotionState(frame: MotionFrame, fps: Float, latencyMs: Long)` in `MainActivity`. Throttle to one JS bridge update every 30-34ms. JSON fields: `t,cx,cy,vy,lean,la,ra,le,re,lwv,rwv,lk,rk,hip,knee,jump,conf,backend,poseFps,latency`.

- [ ] **Step 4: Convert calibration to multi-frame sampling**

`calibrate()` starts a calibration collection state. The next stable frames are added automatically until the estimator reports enough samples, then JS receives `onNativeCalibrationResult(true)`. A failed/unstable sequence updates calibration text rather than committing a bad baseline.

- [ ] **Step 5: Wire `main.mjs`**

`window.onNativeMotionState = json => motion.push(JSON.parse(json), performance.now())`. Every render frame calls `motion.sample(now)` and passes it to `renderer.render(view, time, motionState)`.

Run: `node --test MotionRush/web-test/motion-state.test.mjs && cd MotionRush/android && gradle testDebugUnitTest assembleDebug`
Expected: PASS.

- [ ] **Step 6: Commit**

Commit message: `feat: stream continuous body motion to WebGL`

---

### Task 4: Authored gameplay patterns, combos, near-miss, and enemy variants

**Files:**
- Create: `MotionRush/android/app/src/main/assets/web/src/patterns.mjs`
- Modify: `MotionRush/android/app/src/main/assets/web/src/game.mjs`
- Modify: `MotionRush/android/app/src/main/assets/web/src/main.mjs`
- Create: `MotionRush/web-test/patterns.test.mjs`
- Create: `MotionRush/web-test/game.test.mjs`

**Interfaces:**
- `createPatternDirector(seed)` returns `{ nextPattern(difficulty), materialize(pattern, startZ) }`.
- `game.mjs` exports `applyPattern(state, entities)` and retains `spawnEntity` for tests/debug.
- State adds `combo`, `comboMultiplier`, `streak`, `nearMisses`, `lastSuccessAt`, `currentZone`.

- [ ] **Step 1: Add failing pattern-validity tests**

Every pattern must have at least one survivable path. Required families: `jump`, `slide`, `side-choice`, `alternating`, `coin-arc`, `hand-catch`, `jump-hand`, `crouch-punch`, `enemy-obstacle`, `risk-reward`. Materialized entities must use lanes -1/0/1 and increasing offsets.

- [ ] **Step 2: Implement deterministic pattern catalog/director**

Difficulty tiers 0-4 unlock patterns gradually. Pattern spacing decreases with speed but never places mutually impossible mandatory actions inside <0.55 seconds of travel time.

- [ ] **Step 3: Add combo/near-miss tests**

A successful obstacle avoid/enemy hit/pickup increments streak; hit resets streak. Multiplier grows from 1x to max 5x. Passing a dangerous entity inside a narrow timing/lane margin without collision emits `near-miss` and bonus score once.

- [ ] **Step 4: Implement game changes and enemy variants**

Enemy variants: `guard` (static attack side), `sweeper` (changes lane once), `charger` (faster Z approach). Update enemy lane/behavior deterministically in `stepGame`.

Run: `node --test MotionRush/web-test/*.test.mjs`
Expected: PASS.

- [ ] **Step 5: Replace `spawnWave()` in `main.mjs` with director scheduling**

Keep a `patternCursorZ`; materialize the next pattern when entity horizon falls below a threshold. Difficulty derives from elapsed time/speed/streak.

- [ ] **Step 6: Commit**

Commit message: `feat: add authored runner patterns and combos`

---

### Task 5: Body-driven procedural animation and richer player feedback

**Files:**
- Create: `MotionRush/android/app/src/main/assets/web/src/character-rig.mjs`
- Modify: `MotionRush/android/app/src/main/assets/web/src/render.mjs`
- Create: `MotionRush/web-test/character-rig.test.mjs`

**Interfaces:**
- `createCharacterRig(THREE, helpers)` returns `{ root, update(view, motion, dt, time), setQuality(level) }`.
- `computeCharacterPose(view, motion, time)` is a pure exported function used by tests and produces torso/arm/leg target rotations and squash/landing values.

- [ ] **Step 1: Add failing pure animation-state tests**

Verify: positive body lean immediately affects torso roll; left arm elevation affects left shoulder without cancelling run legs; jump anticipation/airborne/landing are distinct; crouch compresses hips/knees; punch overrides only the relevant upper limb; hit reaction decays.

- [ ] **Step 2: Extract current player construction into `character-rig.mjs`**

Preserve current articulated geometry but add neck/spine groups, forearm rotation, ankle/foot groups, and stronger silhouette. Reuse materials/geometries within the rig.

- [ ] **Step 3: Implement layered blending**

Base locomotion = run/jump/crouch. Overlay continuous body motion = torso lean + hand elevations/extensions. Overlay discrete action = punch/hit with finite weight. Apply damped targets; no pose hard-switching.

- [ ] **Step 4: Add anticipation and landing game feel**

Use player `vy/y` plus recent jump action to apply anticipation for ~80ms; landing compresses body for ~120ms and triggers renderer landing impulse.

Run: `node --test MotionRush/web-test/character-rig.test.mjs`
Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: add body-driven procedural character animation`

---

### Task 6: Five seamless visual zones, pooling, and performance-aware effects

**Files:**
- Create: `MotionRush/android/app/src/main/assets/web/src/zones.mjs`
- Create: `MotionRush/android/app/src/main/assets/web/src/object-pool.mjs`
- Modify: `MotionRush/android/app/src/main/assets/web/src/render.mjs`
- Create: `MotionRush/web-test/object-pool.test.mjs`
- Create: `MotionRush/web-test/zones.test.mjs`

**Interfaces:**
- `zoneForDistance(distance)` returns `neon-city|industrial|tunnel|rooftop|megacity` plus transition progress.
- `ObjectPool(create, reset)` exports `acquire()` / `release(object)` and tracks created count for tests.

- [ ] **Step 1: Add pool and deterministic zone tests**

Pool must reuse released objects; zones must transition in fixed distance windows and expose 0..1 blend progress.

- [ ] **Step 2: Implement zone definitions**

Each zone supplies background/fog/light palette and prop families. Neon city: buildings/signs; industrial: pipes/cranes; tunnel: wall ribs/lights; rooftop: barriers/skyline; megacity: towers/traffic/drones.

- [ ] **Step 3: Refactor renderer world construction**

Use shared geometries/materials and instanced meshes for repeated buildings/posts/ribs where possible. Replace entity creation/disposal with pools keyed by `obstacle:kind`, `enemy:variant`, `pickup:type:air`.

- [ ] **Step 4: Add moving world props and transitions**

Traffic/trains are visual-only and pooled. Blend scene background, fog density/color, key/neon lights, and roadside props across zone transitions without loading screens.

- [ ] **Step 5: Improve camera/effects without obscuring control**

Keep boost FOV, add lane roll driven by continuous lean, landing impulse, near-miss pulse, pickup trails, hit flashes, shield glow. Device-quality level reduces pixel ratio/particles/prop density when measured render FPS remains <45 for 3 seconds.

Run: `node --test MotionRush/web-test/object-pool.test.mjs MotionRush/web-test/zones.test.mjs`
Expected: PASS.

- [ ] **Step 6: Commit**

Commit message: `feat: add seamless runner zones and pooled rendering`

---

### Task 7: Development diagnostics HUD and skeleton data

**Files:**
- Create: `MotionRush/android/app/src/main/assets/web/src/diagnostics.mjs`
- Modify: `MotionRush/android/app/src/main/assets/web/index.html`
- Modify: `MotionRush/android/app/src/main/assets/web/styles.css`
- Modify: `MotionRush/android/app/src/main/assets/web/src/main.mjs`
- Modify: `MotionRush/android/app/src/main/java/com/openai/bodyrunner/MainActivity.kt`

**Interfaces:**
- `createDiagnosticsHud(element)` returns `update({gameFps, poseFps, latency, backend, confidence, motion, lastAction})` and `toggle()`.
- Native motion snapshots contain performance fields; optional skeleton payload is throttled separately to <=15Hz when diagnostics are expanded.

- [ ] **Step 1: Add diagnostics markup**

Add collapsible `#diagnostics` with compact top row (`FPS`, `POSE`, `MS`) and expanded details. Keep it clear of the playfield center and camera preview.

- [ ] **Step 2: Implement FPS sampling and HUD updates**

Game FPS uses a 30-frame moving/EMA window. Display backend, tracking confidence %, lean, hip delta, knee compression, last action. Use text updates at 4Hz max to avoid DOM churn.

- [ ] **Step 3: Add optional skeleton overlay data path**

When diagnostics is expanded, native side may send normalized required joints at <=15Hz; diagnostics module draws them into a tiny canvas. When collapsed, do not serialize skeleton points.

- [ ] **Step 4: Add camera-preview toggle bridge wiring**

Use existing `setPreviewVisible`; add a UI control that hides/shows native preview without stopping inference.

Run: `node --check MotionRush/android/app/src/main/assets/web/src/diagnostics.mjs && node --check MotionRush/android/app/src/main/assets/web/src/main.mjs`
Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: add motion and performance diagnostics`

---

### Task 8: CI expansion, version bump, verification, and APK artifact

**Files:**
- Modify: `MotionRush/android/app/build.gradle.kts`
- Modify: `.github/workflows/motionrush-apk.yml`
- Modify: `MotionRush/README.md`

**Interfaces:**
- Version becomes `versionCode = 4`, `versionName = "0.4.0"`.
- Artifact name becomes `motionrush-v0.4-debug-apk`.

- [ ] **Step 1: Expand JavaScript CI**

Workflow step:

```yaml
- name: Test web game
  run: |
    node --check MotionRush/android/app/src/main/assets/web/src/main.mjs
    node --check MotionRush/android/app/src/main/assets/web/src/game.mjs
    node --check MotionRush/android/app/src/main/assets/web/src/render.mjs
    node --check MotionRush/android/app/src/main/assets/web/src/motion-state.mjs
    node --check MotionRush/android/app/src/main/assets/web/src/patterns.mjs
    node --check MotionRush/android/app/src/main/assets/web/src/character-rig.mjs
    node --check MotionRush/android/app/src/main/assets/web/src/zones.mjs
    node --test MotionRush/web-test/*.test.mjs
```

- [ ] **Step 2: Keep native verification + build in one final job**

Run: `gradle testDebugUnitTest assembleDebug --stacktrace` from `MotionRush/android`.

- [ ] **Step 3: Bump app version and README**

Document continuous tracking, fallback order, diagnostics, five zones, patterns, and test controls.

- [ ] **Step 4: Trigger final CI from the exact final commit**

Expected all steps: checkout PASS, web syntax/tests PASS, Java/SDK/Gradle setup PASS, Kotlin/JUnit PASS, assembleDebug PASS, artifact upload PASS.

- [ ] **Step 5: Download and inspect artifact**

Verify ZIP integrity. Verify APK exists and contains `pose_landmarker_full.task`, `pose_landmarker_lite.task`, `three.module.js`, `three.core.js`, `motion-state.mjs`, `patterns.mjs`, `character-rig.mjs`, and `zones.mjs`. Compute SHA-256.

- [ ] **Step 6: Deliver only the verified APK**

Do not claim physical-device FPS/gesture accuracy beyond what CI can prove; diagnostics in the test build are the evidence source for the user's phone.

---

## Plan self-review

- Spec coverage: camera/backends, confidence/world pose, continuous state, adaptive filter/prediction, multi-frame calibration/adaptive baseline, gestures, bridge, procedural character animation, five zones, effects/performance, authored patterns, combo/near-miss, diagnostics, fallbacks, native/web tests, CI, artifact verification are all mapped to tasks.
- Placeholder scan: no TBD/TODO/"implement later" instructions remain.
- Type consistency: `MotionPose` -> `MotionStateEstimator.push()` -> `MotionFrame` -> `MainActivity.sendMotionState()` -> `motion-state.mjs` -> `renderer.render(view,time,motion)` is the single continuous-state data path; discrete actions remain separate.
