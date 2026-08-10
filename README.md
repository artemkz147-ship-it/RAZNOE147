# Neon Apex Racing

Commercial-oriented 3D mobile racing project for Android.

**Branch:** `neon-rush-3d-racing`

## Current playable vertical slice

- Three.js 3D renderer with mobile-first performance settings.
- Rapier 3D physics for the player vehicle and track barriers.
- Arcade handling: acceleration, braking, reverse, steering, controlled drift and nitro.
- 5 AI opponents with racing-line movement, speed variation and catch-up balancing.
- Closed night circuit with barriers, road markings, neon poles, procedural city skyline and lighting.
- 3 laps, race timer, position calculation, restart, pause, finish screen.
- Keyboard and touch controls.
- Automatic/high/economy quality modes.
- Capacitor Android wrapper.
- GitHub Actions workflow that builds an installable debug APK artifact on every push to this branch.

## Stack

- Three.js `0.185.1` (MIT)
- Rapier `0.19.3` (Apache-2.0)
- Capacitor `8.4.2` (MIT)
- Vite `8.1.5` (MIT)
- TypeScript `7.0.2` (Apache-2.0)

## Local web run

```bash
npm install
npm run dev
```

## Local Android debug APK

```bash
npm install
npm run build
npx cap add android
npx cap sync android
cd android
./gradlew assembleDebug
```

APK output: `android/app/build/outputs/apk/debug/app-debug.apk`.

## Production roadmap

The current branch is a first playable foundation, not a claim that the full commercial game is finished. Next production layers are:

1. Replace procedural hero cars with optimized authored/AI-assisted GLB vehicles and add a 10–20 car garage.
2. Add multiple tracks/biomes, track selection and championship progression.
3. Upgrade vehicle dynamics with suspension/raycast wheels, traction states, collision damage and surface materials.
4. Add garage tuning, paint/wheels, upgrades, currency and persistent save.
5. Add audio: engine RPM layers, turbo, nitro, tires, impacts, ambience and music.
6. Add particles/VFX: tire smoke, sparks, rain, spray, exhaust and speed streaks.
7. Add race modes: circuit, sprint, time attack, elimination and traffic events.
8. Add Android performance profiling and device presets.
9. Add signed release build after keystore/store configuration is supplied.

## AI-assisted 3D assets

See `tools/ai-assets/README.md`. The preferred free dev-time model is TripoSR (MIT). Heavy neural inference is deliberately not bundled into the APK; only optimized game assets are shipped.
