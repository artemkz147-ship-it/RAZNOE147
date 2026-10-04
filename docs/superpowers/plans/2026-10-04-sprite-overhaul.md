# DEADLIGHT Sprite Overhaul Implementation Plan
> Execution: implement inline in current session; no delegation.
**Goal:** Replace geometric visuals with sprite action game and persistent RPG mechanics in Android APK.
**Architecture:** Classic offline scripts: progression.js owns validated saves/perks/items; assets.js loads raster atlases; game.js owns simulation/render/input; HTML/CSS owns responsive menus/HUD. Existing Android WebView wrapper retained.
**Tech Stack:** Canvas 2D, generated WebP RGBA sprites, Android SDK35/min26, AGP8.7.3, Gradle8.9, Java17.
**Spec:** docs/superpowers/specs/2026-10-04-sprite-overhaul.md

## Global Constraints
Offline assets; no permissions or external APIs; isolated deadlight-android; version2; honest test reporting.
## Review Focus
Corrupt save clamps/rejects values; multitouch cancel releases keys; eating cannot heal twice and damage cancels; specials cannot spend negative energy; asset loads complete before start and retry supported.

### Task 1: Art and persistence
- [ ] Normalize sheets to shared-scale 256-pixel frames; inspect atlas/animation.
- [ ] Write/run failing progression tests; implement progression.js XP/levels/perks/equipment/consumables and validated local save.
- [ ] Compress generated city and atlas with alpha preserved, inventory icons separated.
### Task 2: Game systems and phone UI
- [ ] Write/run failing game tests for hit timing, combo, energy gating, corpse feeding/cancel/once-only, double jump, pausing.
- [ ] Implement assets.js, game.js with fixed-step simulation and sprite frame rendering; mobile menus/index/style; corpse and loot lifecycle.
- [ ] Run tests and static syntax checks; review live screenshots/animation and controls.
### Task 3: Android delivery
- [ ] Update version2/name/readme and Actions tests.
- [ ] Commit text and binary assets in existing GitHub branch.
- [ ] Build/lint/sign, run Android emulator touch tests with sprite readiness and feeding/inventory checks; inspect screenshots.
- [ ] Download tested APK and persist deliverable; report implemented scope with link.
