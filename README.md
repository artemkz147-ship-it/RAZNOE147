# UMK3 HD Fan Remake (personal-use project)

Private fan remake project inspired by Ultimate Mortal Kombat 3. The codebase contains an original clean-room HTML5 fighting engine, Android WebView shell, procedural/vector fallback visuals, mobile touch controls, arcade tower flow, CPU AI, parallax arenas, hit effects, synthesized audio and an asset replacement pipeline for HD sprite sheets.

## Status

This branch is a playable engine build, not a dump of original Midway/Warner assets. Original copyrighted sprites/audio are not included. HD character sprite atlases can be dropped into the documented asset slots without changing combat code.

## Run in a browser

Open `web/index.html` in a modern browser, or serve `web/` with any static HTTP server.

## Android build

The Android app is a minimal Java/WebView shell and packages the same `web/` directory offline. GitHub Actions builds a debug APK on every push to this branch.

Local CLI build (with Android SDK + JDK 17 + Gradle installed):

```bash
gradle :app:assembleDebug
```

APK path:

`app/build/outputs/apk/debug/app-debug.apk`

## Controls

Keyboard:
- Move: A / D
- Jump: W
- Crouch: S
- Punch: J
- Kick: K
- Block: L
- Special: I
- Run/dash: U

Android: on-screen controls are enabled automatically.

## Project goals

- UMK3-style speed, spacing and round structure
- full classic/console-era UMK3 roster represented in game data
- arcade tower flow
- modernized parallax stage rendering
- per-character special archetypes
- local offline play vs CPU
- Android-first landscape UI
- replaceable HD sprite/portrait/stage asset pipeline

See `docs/ASSET_PIPELINE.md`.
