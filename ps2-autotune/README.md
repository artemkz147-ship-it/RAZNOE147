# PS2 AutoTune for Android

Android PS2 emulator fork built as a source overlay on top of the open-source ARMSX2/PCSX2 ARM64 core.

## What is new in this fork

- **Device-aware first launch:** CPU core count, RAM, SoC and GPU family seed a safe starting profile.
- **Per-game learning:** every title can remember the internal resolution that actually maintained native PS2 emulation speed on this device.
- **Correct speed metric:** AutoTune uses `PerformanceMetrics::GetSpeed()` through a small JNI bridge, so tuning follows PCSX2's own canonical 100%-speed measurement instead of guessing from frontend counters.
- **Closed-loop tuning:** sustained speed loss reduces internal resolution; stable full-speed headroom can raise it.
- **Thermal-aware:** Android thermal status can temporarily lower GPU load before severe throttling; heat-degraded values are not learned as the game's permanent profile.
- **Hysteresis/cooldowns:** prevents quality from bouncing every few seconds when a scene changes.
- **User settings win:** explicit per-game overrides are merged after AutoTune. Explicit global/per-game resolution disables live resolution learning.
- **Correctness first:** the tuner never automatically enables the aggressive VU deferred-write or pipeline-stall-skip hacks which can break games.
- **No server/API required:** tuning and learning happen locally on the Android device.
- **Separate Android package:** `com.artemkz147.ps2autotune`, so the test build can coexist with official ARMSX2.
- **Universal page-size APK:** CI uses ARMSX2's own 4K + 16K Android builder for old and new ARM64 Android devices.
- **Source-only build:** ARMSX2's optional proprietary Discord Social SDK is not required. The one direct upstream reference is resolved reflectively so Discord simply stays unavailable when the proprietary SDK is absent; PS2 emulation is unaffected.

## Source base

The build workflow pins stable ARMSX2 tag `2.6.5.9` and records the exact upstream commit SHA in the APK build artifact. Our source overlay is in this folder.

ARMSX2 is a GPL-3.0 PS2 emulator based on PCSX2. This project remains GPL-3.0-compatible and preserves upstream notices/source availability. Do not bundle Sony BIOS files or commercial game images. Users provide their own legally obtained BIOS and game dumps.

## AutoTune precedence

```text
ARMSX2 defaults
    ↓
global settings
    ↓
PS2 AutoTune safe automatic layer
    ↓
explicit per-game overrides (highest priority)
```

## Runtime learning

The adaptive loop waits for boot/shader warm-up, samples **emulation speed** every 2 seconds and uses a multi-sample window. It changes only the internal GS upscale multiplier in 0.25x steps during gameplay. Learned values are stored locally as `autotune.scale.<gameKey>`.

If speed remains below 90%, resolution is reduced. If speed stays effectively at 100% with stable headroom and the phone is cool, resolution can be raised. Severe thermal status can trigger a temporary reduction without poisoning the learned profile.

Current modes supported by the engine:

- `performance`
- `balanced` (default)
- `quality`

The engine is functional without a network connection. Per-title compatibility/game fixes from PCSX2/ARMSX2 still remain active underneath AutoTune, and a user can always override a game manually when a title needs a special renderer or fix.

## Build

The workflow `.github/workflows/build-ps2-autotune.yml`:

1. Checks out ARMSX2 `2.6.5.9` recursively.
2. Applies this source overlay, including the `GetSpeed()` JNI bridge and source-only Discord fallback.
3. Builds the official universal 4K + 16K sideload APK.
4. Verifies APK signing/alignment and writes SHA-256.
5. Uploads the APK as an Actions artifact and, on a successful branch build, publishes it as the `ps2-autotune-ci` prerelease together with `SOURCE-MANIFEST.txt`.

The upstream self-updater is disabled in this fork because an official ARMSX2 package has a different Android application ID and cannot update this APK in place.
