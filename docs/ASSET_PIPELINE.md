# HD asset replacement pipeline

The current engine always has a vector fallback, so combat remains playable even when an HD asset is missing.

## Fighters

Preferred future structure:

```
web/assets/fighters/<fighter-id>/portrait.webp
web/assets/fighters/<fighter-id>/atlas.webp
web/assets/fighters/<fighter-id>/atlas.json
```

Recommended atlas conventions:

- transparent WebP
- 2x or 3x internal resolution
- 60 fps source animation where practical
- consistent ground/contact pivot across all frames
- states: idle, walk_f, walk_b, crouch, jump, punch1, punch2, kick1, kick2, block_hi, block_lo, hit_hi, hit_lo, knockdown, getup, win, special_1..n

The JSON atlas should map animation names to frame rectangles and frame durations. Combat timing is engine-driven; visual frames must not redefine hitboxes.

## Stages

Preferred structure:

```
web/assets/stages/<stage-id>/back.webp
web/assets/stages/<stage-id>/mid.webp
web/assets/stages/<stage-id>/front.webp
web/assets/stages/<stage-id>/fx.webp
```

All stage images should be original redraws. The project intentionally does not contain ripped Midway/Warner sprites, backgrounds or audio.

## Build-time AI workflow

If neural generation is used, it should be used only to create/export assets during development. The APK should contain the final baked images and should not require a model, API key, server or internet connection to display them.
