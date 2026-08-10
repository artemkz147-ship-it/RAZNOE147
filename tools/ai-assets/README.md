# AI asset pipeline

The runtime APK does **not** bundle a heavyweight 3D neural network. Neural tools are used only during development to create source meshes, then the optimized GLB assets are shipped with the game.

## Preferred free tools

1. **TripoSR** — `VAST-AI-Research/TripoSR`, MIT. Fast image-to-3D reconstruction. Preferred for vehicles, props and roadside objects after creating a clean concept image.
2. **InstantMesh** — `TencentARC/InstantMesh`, Apache-2.0. Alternative image-to-3D reconstruction for assets that need stronger multi-view consistency.
3. **Shap-E** — `openai/shap-e`, MIT. Useful for early text/image 3D blockouts, but lower priority for final hero cars.

## Shipping rules

- Never ship raw OBJ/PLY from an AI model.
- Clean topology and pivots, then export GLB/glTF 2.0.
- Use one consistent convention: meters, Y-up, car forward = -Z.
- Generate explicit low-poly collision proxies.
- Optimize with glTF Transform/Meshopt before adding an asset to `public/assets/`.
- Keep hero vehicle textures around 1K–2K on mobile; environment props should usually be lower.
- Verify the license of every third-party checkpoint or source image separately. The repository license of a tool does not automatically license arbitrary training checkpoints or images from elsewhere.

## Current vertical slice

The first playable build intentionally uses procedural cars and scenery so that the branch builds immediately and has no unverified third-party art. AI-generated production GLBs can replace these meshes incrementally without changing the physics/gameplay architecture.
