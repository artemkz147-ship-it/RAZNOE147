# Motion Rush v0.5

Android full-body runner controlled through the front camera.

## Runtime

- Native CameraX front-camera capture with `STRATEGY_KEEP_ONLY_LATEST` at an approximately 640×480 analysis target.
- Native MediaPipe Pose Landmarker with ordered fallback: GPU Full → GPU Lite → CPU Full → CPU Lite.
- Full and Lite pose models are packaged inside the APK after the build task downloads them.
- Continuous filtered body state is sent to the WebGL game at a bounded rate; discrete confirmed actions remain a separate immediate channel.
- Multi-frame neutral calibration, One-Euro filtering, confidence rejection, short prediction and adaptive baseline handling.

## Gameplay

- Body lean / lateral movement changes lanes.
- Physical jump, crouch, left/right punch and raised-hand pickups.
- Authored pattern director with ten pattern families instead of pure random spawning.
- Combo multiplier up to ×5, streaks and near-miss scoring.
- Guard, sweeper and charger enemy behaviors.

## Visuals

- Three.js/WebGL renderer packaged locally in the APK.
- Upgraded articulated 3D runner with rounded human proportions, layered suit/armor materials, gloves, boots and emissive visor; body-driven animation remains layered over running, jump, crouch and punch motion.
- Five seamless visual zones: neon city, industrial, tunnel, rooftop and megacity.
- Pooled gameplay objects, instanced zone props, dynamic FOV/camera impulses, particles, shield/boost feedback and adaptive render quality.

## Diagnostics

Tap the compact diagnostics bar in the lower-left corner to view game FPS, pose FPS, inference latency, selected backend, tracking confidence, body lean, knee compression, jump impulse, current zone, quality level and last recognized action. The same panel can hide/show the native camera preview without stopping inference.

## Debug controls

- `A/D` or arrow left/right: lane change
- `Space/W/↑`: jump
- `S/↓`: crouch
- `J/K`: left/right punch
- `Q/E`: raise left/right hand
- `R`: raise both hands

## Verification

GitHub Actions runs JavaScript syntax checks, all Node tests, Kotlin/JUnit tests, `assembleDebug`, and uploads `motionrush-v0.4-debug-apk` only after the build succeeds.


## v0.5 visual pass

- Reworked the runner from boxy primitives into a denser articulated model while keeping it generated locally and fully offline.
- Preserved the existing pose-driven shoulder, elbow, hip and knee rig so camera controls still drive the same gameplay actions.
- This branch is the active visual/model upgrade line for Motion Rush.
