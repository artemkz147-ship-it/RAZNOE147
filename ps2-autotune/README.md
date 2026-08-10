# PS2 AutoTune for Android

Android PS2 emulator experiment built as a source overlay on top of the current open-source ARMSX2/PCSX2 ARM64 core.

## What is new in this fork

- **Device-aware first launch:** CPU core count, RAM, SoC and GPU family seed a safe starting profile.
- **Per-game learning:** every title can remember the internal resolution that actually maintained native PS2 speed on this device.
- **Closed-loop tuning:** while a game runs, `getFPS()` is compared with the title's nominal PS2 refresh rate. Sustained misses reduce internal resolution; stable headroom can raise it.
- **Hysteresis/cooldowns:** prevents quality from bouncing every few seconds when a scene changes.
- **User settings win:** explicit per-game overrides are merged after AutoTune. Explicit global/per-game resolution disables live resolution learning.
- **Correctness first:** the tuner never enables the aggressive VU deferred-write or pipeline-stall-skip hacks automatically.
- **No server/API required:** tuning and learning happen locally on the Android device.

## Source base

The build workflow checks out `ARMSX2/ARMSX2` and records the exact upstream commit SHA in the APK build artifact. Our source overlay is in this folder.

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

The adaptive loop waits for boot/shader warm-up, samples FPS every 2 seconds and uses windows of multiple samples. It changes only the internal GS upscale multiplier in 0.25x steps. Learned values are stored locally as `autotune.scale.<gameKey>`.

Current modes supported by the engine:

- `performance`
- `balanced` (default)
- `quality`

The first implementation intentionally keeps these as preferences in the engine so the core can be validated before adding more UI surface.

## Build

The workflow `.github/workflows/build-ps2-autotune.yml` performs the Android build on GitHub Actions and uploads an installable APK artifact together with `SOURCE-MANIFEST.txt`.
