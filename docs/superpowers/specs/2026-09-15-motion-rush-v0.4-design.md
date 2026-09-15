# Motion Rush v0.4 Design

## Goal

Turn the current Motion Rush prototype into a substantially more responsive and visually convincing full-body controlled runner without discarding the parts that already work. The target is not a cosmetic patch. The next version should feel like direct body control rather than delayed camera recognition, while keeping the Android-first APK workflow and the existing three-lane runner concept.

## Architectural Direction

Use a hybrid architecture:

- Native Android layer for camera capture, pose inference, motion filtering, calibration, diagnostics, and gesture/state interpretation.
- Three.js/WebGL layer for the runner simulation, animation, 3D scene, effects, world generation, UI feedback, and game rules.
- Keep all critical runtime assets local inside the APK: pose models, Three.js modules, shaders/materials, and game assets.
- Preserve a safe fallback path for devices where the preferred pose backend or GPU delegate is unavailable.

Do not move the whole game to a new native engine in this iteration. The existing hybrid stack is retained, but the boundary between native tracking and WebGL gameplay is upgraded from sporadic button-like commands to a low-latency continuous motion state plus discrete confirmed actions.

## Tracking and Motion Pipeline

### Camera

- Front camera through CameraX.
- Use an analysis resolution around 640x480 unless device testing proves a higher resolution materially improves pose quality.
- `STRATEGY_KEEP_ONLY_LATEST` remains mandatory to avoid frame backlog.
- Avoid unnecessary allocation and transformation work in the hot path.
- Reuse buffers where possible.
- Keep preview rendering separate from inference so the user can see the camera even if inference rate is lower than display rate.

### Pose backend

Preferred order:

1. MediaPipe Pose Landmarker Full with GPU delegate.
2. MediaPipe Pose Landmarker Lite with GPU delegate if Full cannot initialize.
3. MediaPipe Pose Landmarker Full or Lite on CPU depending measured latency.
4. Last-resort safe CPU Lite path.

The runtime must expose the active backend in diagnostics.

### Pose data

Track at minimum:

- nose
- shoulders
- elbows
- wrists
- hips
- knees
- ankles

Use normalized landmarks and world landmarks when available. Store per-joint visibility/presence confidence so low-confidence joints can be ignored instead of contaminating motion classification.

### Continuous motion state

The native side should publish a compact state snapshot rather than only sending isolated actions. State should include:

- normalized body center X/Y
- body vertical velocity
- torso lean
- left/right arm elevation
- left/right arm extension
- left/right wrist velocity
- left/right elbow angle
- hip height relative to baseline
- knee compression
- estimated jump impulse
- tracking confidence
- current active backend
- inference FPS and estimated processing latency

Discrete actions remain for confirmed events such as punch, jump trigger, crouch trigger, and lane commit.

## Filtering and Prediction

Replace one fixed EMA value with an adaptive low-latency filter.

Recommended design:

- One-Euro style filtering or equivalent velocity-aware adaptive filtering.
- Stronger smoothing while nearly static.
- Lower smoothing during fast intentional movement.
- Short velocity prediction window for responsive visual feedback, limited so it cannot create visible overshoot.
- Per-joint filtering parameters can differ for torso, wrists, and ankles.

Filtering must not add more perceived latency than it removes jitter.

## Calibration

Calibration becomes a system instead of a single stored pose.

### Initial calibration

- Require a neutral standing pose.
- Sample multiple consecutive frames rather than one frame.
- Reject calibration if pose confidence is unstable.
- Derive body scale from shoulder width and torso/hip geometry.

### Runtime adaptive baseline

- Slowly update neutral center only while the user is in a stable standing/running state.
- Do not adapt during jumps, crouches, punches, large side movements, or low-confidence frames.
- Prevent drift from turning a sustained lean into the new neutral too quickly.

## Gesture and Action Detection

### Lane changes

- Use continuous torso/hip displacement plus lateral velocity.
- Immediate visual lean and character movement begin before the lane is committed.
- Commit after a short confidence window.
- Require return toward neutral before another same-direction lane commit unless a distinct second impulse is detected.
- Avoid repeated commands while the user simply remains leaned.

### Jump

Use a combination of:

- upward hip/body-center velocity
- upward displacement relative to adaptive baseline
- ankle/knee motion
- short temporal impulse window

The jump should trigger near takeoff, not after the user is already in the air.

### Crouch/slide

Use:

- hip drop
- knee compression
- torso height change
- short temporal hold to reject noise

### Punch

Punches are dynamic actions, not static poses.

Use:

- wrist velocity
- elbow extension
- shoulder-to-wrist reach
- direction consistency
- cooldown/re-arm logic

Holding an arm extended must not repeatedly punch.

### Raised-hand bonuses

- React faster than combat punches.
- Left/right/both-hand classification remains.
- Use confidence and short hold/debounce.
- Permit combinations such as jumping while raising one hand.

## Native-to-Web Data Flow

Reduce bridge overhead.

- Do not send repeated status strings every pose frame.
- Send status only when state changes materially.
- Send continuous motion snapshots at a controlled rate suitable for smooth rendering.
- Send confirmed discrete actions immediately.
- The WebGL layer interpolates between motion snapshots on render frames.

## 3D Character

Replace the rigid prototype feel with a procedural rigged character built from articulated segments.

Required animated states:

- idle/ready
- run cycle
- lane shift
- torso lean
- jump anticipation
- airborne jump
- landing compression
- crouch/slide
- left punch
- right punch
- left hand up
- right hand up
- both hands up
- hit reaction
- shield/boost reaction

Animation blending should be continuous. Body-controlled upper-body motion should blend with the authored run cycle instead of replacing it abruptly.

## Rendering and Visual Quality

### Environment

Create multiple seamless visual zones:

1. Neon city streets.
2. Industrial district.
3. Tunnel section.
4. Rooftop/highway section.
5. Night megacity section.

Zones should transition without loading screens.

### World detail

Add:

- moving trains or traffic where appropriate
- bridges and overhead structures
- animated signs and billboards
- drones
- tunnel lighting
- roadside props
- better ground/track materials
- fog/depth treatment
- dynamic environmental lights

### Performance strategy

- Instanced meshes for repeated environment geometry.
- Object pooling for obstacles, pickups, particles, and transient effects.
- Reuse geometries/materials.
- Avoid per-frame allocation in main render/update loops.
- Cap particle counts based on device performance.
- Keep post-processing lightweight; prioritize stable frame pacing over expensive effects.

## Camera and Effects

Add restrained game-feel effects:

- FOV increase during boost/high speed
- slight camera roll during lane changes
- impact shake on hits
- landing impulse
- speed lines during boosts
- short contact flash and particles on enemy hits
- shield distortion/glow
- pickup trails

Effects must not obstruct body-control readability.

## Gameplay Improvements

Replace mostly random spawning with authored procedural patterns.

Patterns should include:

- jump lanes
- slide lanes
- side choice
- alternating lanes
- coin arcs
- hand-catch sequences
- jump + hand catch
- crouch + punch sequences
- enemy plus obstacle combinations
- risk/reward branches

Add:

- combo multiplier
- near-miss bonus
- streak feedback
- variable pattern difficulty
- speed-dependent pattern selection
- rare two-hand rewards
- several enemy behavior variants

Difficulty should increase by pattern complexity and density, not only forward speed.

## Diagnostics

Test builds include a collapsible diagnostics HUD showing:

- game FPS
- pose inference FPS
- inference/backend label
- estimated pose latency in ms
- tracking confidence
- current motion state
- last confirmed gesture/action
- optional skeleton overlay

This HUD is for development/testing and can be disabled in production builds.

## Error Handling and Fallbacks

- If GPU Full fails, automatically select the next supported backend.
- If pose confidence drops, temporarily suppress actions rather than sending guesses.
- If the body leaves frame, show clear placement guidance.
- If inference becomes too slow, reduce model/resolution before allowing frame backlog.
- The game should remain responsive to rendering even when inference cadence drops.

## Testing Strategy

### Unit tests

Expand native gesture tests to cover:

- jitter/noise rejection
- fast vs slow movement filtering
- lane re-arm
- sustained lean without spam
- jump impulse timing
- crouch timing
- static arm does not punch
- punch velocity detection
- simultaneous jump + hand raise
- low-confidence joint suppression
- adaptive baseline drift limits

### Web/game tests

Cover:

- authored pattern validity
- collision correctness
- combo and near-miss logic
- continuous body-state interpolation
- character animation state transitions
- object-pool reuse

### CI

GitHub Actions must run:

1. JavaScript syntax/tests.
2. Kotlin/JVM unit tests.
3. Android debug build.
4. Artifact upload.

The APK is not delivered until all required checks are green.

## Success Criteria

The iteration is successful when:

- body movements feel visibly more immediate than v0.3;
- single-frame noise rarely causes actions;
- punches and lane changes do not spam when a pose is held;
- jump triggers close to takeoff;
- raised-hand pickups feel fast;
- game rendering remains smooth while pose inference runs;
- environment and character are visibly beyond prototype quality;
- gameplay presents recognizable authored obstacle sequences rather than random clutter;
- diagnostic HUD makes latency/FPS/tracking problems measurable on the user's actual phone;
- the APK builds reproducibly through GitHub Actions.

## Scope Boundaries

Not part of this iteration unless needed for stability:

- store monetization
- accounts/cloud saves
- multiplayer
- complete native-engine rewrite
- large downloadable cosmetic catalog
- online backend

The priority is control quality, visual quality, animation, gameplay feel, and measurable performance.