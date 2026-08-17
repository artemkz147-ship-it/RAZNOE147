# Shadowvale Survivor

Production-oriented 3D survival roguelike for Yandex Games.

## Gameplay
- Third-person / elevated follow camera.
- Automatic sword combat, manual movement and invulnerable dash.
- Skeleton, Slime, Bat and Dragon enemies with different roles.
- Elite waves and a multi-phase Lord of Mist boss.
- 14 run upgrades including orbiting imported swords and crystal projectiles.
- Persistent Soul Shards and five permanent altar upgrades.
- Rewarded revive, rewarded reroll, rewarded double run reward, fullscreen ad between runs.
- Desktop and touch controls, pause, quality/audio/vibration settings.
- RU/EN localization, cloud + local progress, optional Yandex review prompt.

## Art policy
All visible gameplay models are converted from CC0 Quaternius packs. The build validator rejects common Three.js primitive geometry constructors in gameplay source. No procedural placeholder geometry is shipped as visible game art.

## Build
GitHub Actions downloads source art, converts FBX to GLB with Blender, runs Vite production build, validates Yandex packaging rules, boots the game in headless Chrome/WebGL on desktop and mobile viewports, captures smoke screenshots, and publishes the upload ZIP.
