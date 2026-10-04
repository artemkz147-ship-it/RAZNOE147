# DEADLIGHT 2 — sprite action overhaul
User goal: Android zombie action platformer with proper sprite visuals and animations, progression, normal and special attacks, items, and eating restoring life and energy. Existing geometric artwork was rejected.

Art: original generated hand-painted RGBA character atlases, illustrated ruined city, rooftop/catwalk tiles, crates, checkpoint shrine, four item icons. Characters use real animation frames, shared scale and ground anchors, no code-drawn limbs. Effects may be lightweight particles/lights. No claim of AAA production scale.

Combat: three-step claw combo with delayed hit frames and one-hit-per-target per attack; dash; 25-energy ranged plague attack; 35-energy area ground smash; two enemy archetypes, rifle and shield. Corpses persist, eating requires proximity and grounded uninterrupted 1.4-second animation; grants 35 health and 45 energy once, not automatic on killing. Energy regenerates slowly. Hit flashes, hit stop, damage numbers and knockback.

Progression: kills/eating award XP; levels award mutation points; permanent upgrades to maximum health, energy, claws and recovery. Equipment relics modify damage/armour. Persistent local profile and recoverable checkpoint run. Item inventory: life potion, energy vial, claw relic, armour amulet; accessible pause inventory, consumable shortcut buttons, crates and drops. Storage failures must not crash play.

Mobile: landscape Android 8+, offline assets, no network or extra permissions; compact HP/energy/XP HUD, large multi-touch controls, separate eat and special buttons; upgrade/inventory screens pause simulation. Resume clears inputs; background and Android Back pause. Asset errors show retry, no geometric fallback. Maintain isolated deadlight-android branch.

Verification: deterministic movement/jump/attack timing/energy/eat/cancel/once-only tests; progression invalid-save and persistence tests; asset dimensions/alpha/frame count; Android build/lint/signature, actual emulator launch and touch; screenshot review. Deliver version 2 APK with source and honest scope.
