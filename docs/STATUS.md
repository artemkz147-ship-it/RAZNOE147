# Project status — UMK3 HD Fan Remake

## Implemented now

- Dedicated project branch: `umk3-hd-fan-remake`.
- Clean-room HTML5/Canvas fighting runtime.
- 1280x720 logical render target with responsive scaling.
- Android landscape WebView shell with immersive fullscreen and display-cutout handling.
- Offline packaging: the APK bundles the `web/` game files and does not require a server or API.
- 28-character roster data including classic/console-era UMK3 fighters and bosses.
- 10 arena definitions with procedural/parallax-style vector backgrounds.
- Character select screen.
- Arcade tower progression.
- Best-of-three round flow, 99-second timer, health bars and round markers.
- Movement, crouch, jump, run meter, block, punch, kick and character special action.
- CPU opponent logic.
- Projectiles, teleport/charge/stomp-style specials, freeze effect, hit stun, block stun and knockback.
- Combo counter.
- Hit particles, screen shake, impact flash and synthesized sound effects.
- Mobile touch controls and keyboard controls.
- Pause handling and pause-on-background.
- HD asset replacement convention documented in `docs/ASSET_PIPELINE.md`.
- GitHub Actions Android build pipeline.
- Automated Node smoke test wired into CI before APK compilation.

## Verified in the current development environment

- `web/game.js` passes Node syntax checking.
- Automated mock-runtime smoke test passes the flow:
  - title
  - character select
  - arcade tower
  - fight
  - touch HUD activation
  - punch input
  - special input
  - render/update loop remains alive

## Not yet claimed as finished

- Final hand-drawn/AI-assisted HD frame-by-frame fighter sprite atlases are not yet present.
- Final HD stage artwork is not yet present; current stages are original procedural/vector fallbacks.
- Exact frame data, every original UMK3 combo, every fatality, friendship, babality, brutality and hidden-code behavior have not yet been recreated one-for-one.
- Original Midway/Warner sprites, music and sound files are intentionally not bundled.
- A successful Android APK artifact has not yet been observed through the available GitHub connector, so APK completion is not claimed yet.

## Next production milestones

1. Replace vector fighter fallback with atlas-based animation renderer.
2. Produce complete HD atlas for Scorpion and Sub-Zero as the reference quality bar.
3. Lock UMK3-faithful frame timing and move tables for those two fighters.
4. Produce HD Subway stage and validate camera/parallax composition.
5. Expand exact move/animation data across the roster.
6. Add finishers and stage fatalities.
7. Confirm GitHub Actions APK artifact and run-device installation.
8. Continue fighter/stage asset production until the full roster and stage set are converted.
