# DEADLIGHT 2.0 — Голод
Android 8+ offline sprite action platformer. Play as an undead claw fighter escaping quarantine.

## Implemented
- Original hand-painted raster artwork: 36 hero animation frames, 24 soldier frames, city, platforms, shrine, crates and items. Sprite frame selection for idle/run/jump/claw combo/feeding/specials and enemy attacks/death. No procedurally drawn characters.
- Three-hit claw combo with hit frames, shield damage reduction, knockback, hit stop, damage numbers; dash, double jump.
- Plague bolt (25 energy) and grounded area smash (35 energy); finite energy, regeneration and cooldowns.
- Corpses remain after kills. Nearby grounded feeding takes 1.4 seconds, interrupted by damage/movement/jump, restores 35 health/45 energy once. Mutations increase recovery.
- XP, levels and permanent mutation points: health, energy, claws and feeding, 10 ranks each.
- Inventory and crates: life potion +50, energy vial +60, equipped claw relic +5 damage, armour amulet 20% mitigation.
- Local persistent mutations/inventory/equipment and resumable raid snapshots; checkpoint recovery, paused inventory and mutation screens; physical touch controls and native Back pause.

Single authored chapter, two enemy archetypes, original AI-generated raster art. This is an expanded independent 2D game, not a claim of AAA production scale. Sound is lightweight synthesized feedback, not a studio soundtrack.

## Build
GitHub Actions `.github/workflows/android-apk.yml` builds, lints and signs `DEADLIGHT-2.0.apk`; Android15 emulator physically exercises touch movement, double jump, attacks, specials, corpse feeding, potion, mutation purchase, inventory equip and native pause/resume. Generated debug signing keys vary across runs; uninstall previous debug APK if Android reports a signature conflict. Uninstalling clears local progress.

Pinned: AGP8.7.3, Gradle8.9, JDK17, AndroidX WebKit1.12.1, SDK35, minSDK26.
Local SDK: `gradle --no-daemon :app:assembleDebug :app:lintDebug`.
Tests: `node qa.cjs`, `node progression-test.cjs`, `node asset-test.cjs`.

The game bundles all artwork and needs no network, login, API key, or extra Android permissions. `art/manifest.json` records verified image dimensions and SHA256 hashes; final WebP atlases preserve alpha and use consistent feet anchors with shared scale across poses.
