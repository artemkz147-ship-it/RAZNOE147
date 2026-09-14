from pathlib import Path
import shutil

ROOT = Path('physics-build/src/physics2')
PATCH = Path('physics-playground-3-patch')
PKG = ROOT/'app/src/main/java/com/partymotion/playground'

for name in ['ObjMesh.kt','RenderSnapshot.kt','LocalSoundEngine.kt','Physics3DView.kt','Physics3DRenderer.kt','MainActivity.kt']:
    shutil.copy2(PATCH/name, PKG/name)

proc = PKG/'ProceduralSoundEngine.kt'
if proc.exists(): proc.unlink()

p = PKG/'PlaygroundView.kt'
s = p.read_text()
def rep(old,new):
    global s
    if old not in s: raise SystemExit('missing PlaygroundView patch marker: '+old[:80])
    s=s.replace(old,new,1)
rep('class PlaygroundView(context: Context) : View(context) {','class PlaygroundView(context: Context, private val renderView: Physics3DView? = null) : View(context) {')
rep('private val soundEngine = ProceduralSoundEngine()','private val soundEngine = LocalSoundEngine(context)')
rep('setBackgroundColor(Color.rgb(7, 11, 18))','setBackgroundColor(if (renderView == null) Color.rgb(7, 11, 18) else Color.TRANSPARENT)')
rep('''        updateModeStatus(now)\n        drawBackground(canvas)\n        drawWorld(canvas)\n        drawHud(canvas)''','''        updateModeStatus(now)\n        if (renderView == null) {\n            drawBackground(canvas)\n            drawWorld(canvas)\n        } else {\n            renderView.submitSnapshot(buildRenderSnapshot())\n            drawWorldOverlay(canvas)\n        }\n        drawHud(canvas)''')
marker='    private fun drawBackground(canvas: Canvas) {'
insert='''    private fun buildRenderSnapshot(): RenderFrameSnapshot {\n        val state = scene ?: return RenderFrameSnapshot(1f, 1f, viewportWorldWidth, viewportWorldHeight, cameraCenter.x, cameraCenter.y, cameraZoom, screenShake, null, null, emptyList(), emptyList(), emptyList())\n        return RenderFrameSnapshot(\n            state.worldWidth, state.worldHeight, viewportWorldWidth, viewportWorldHeight,\n            cameraCenter.x, cameraCenter.y, cameraZoom, screenShake, state.goal?.x, state.goal?.y,\n            state.world.bodies.map { it.toRenderSnapshot() },\n            state.world.joints.filterNot { it.broken }.map { it.toRenderSnapshot() },\n            particles.map { RenderParticleSnapshot(it.position.x, it.position.y, (it.life / 0.8f).coerceIn(0f, 1f), it.color) },\n        )\n    }\n\n    private fun drawWorldOverlay(canvas: Canvas) {\n        scene ?: return\n        canvas.save()\n        canvas.translate(width / 2f, height / 2f)\n        canvas.scale(cameraZoom, cameraZoom)\n        canvas.translate(-cameraCenter.x * scale, -cameraCenter.y * scale)\n        forceFields.forEach { drawForceField(canvas, it) }\n        drawSelection(canvas)\n        linkStart?.let { selected ->\n            stroke.color = Color.rgb(255, 230, 95)\n            stroke.strokeWidth = 4f / cameraZoom\n            canvas.drawCircle(selected.position.x * scale, selected.position.y * scale, selected.approximateRadius() * scale * 1.25f, stroke)\n        }\n        canvas.restore()\n    }\n\n'''
if marker not in s: raise SystemExit('missing drawBackground marker')
s=s.replace(marker,insert+marker,1)
rep('Tool.WHEEL -> addBodyCapped(SceneFactory.dynamicCircle(0.52f, pos, material).also { it.friction = max(it.friction, 0.82f) })','Tool.WHEEL -> addBodyCapped(SceneFactory.dynamicCircle(0.52f, pos, material).also { it.friction = max(it.friction, 0.82f); (it.userData as? RenderTag)?.role = "wheel" })')
p.write_text(s)

p=PKG/'GameModel.kt'; s=p.read_text()
def game_rep(old,new):
    global s
    if old not in s: raise SystemExit('missing GameModel patch marker: '+old[:80])
    s=s.replace(old,new,1)
game_rep('val chassis = world.add(dynamicBox(2.3f, 0.48f, Vec2(w * 0.22f, floor - 1.0f), MaterialStyle.WOOD))','val chassis = world.add(dynamicBox(2.3f, 0.48f, Vec2(w * 0.22f, floor - 1.0f), MaterialStyle.WOOD).also { (it.userData as? RenderTag)?.role = "vehicle-chassis" })')
game_rep('val left = world.add(dynamicCircle(0.48f, Vec2(chassis.position.x - 0.78f, floor - 0.58f), MaterialStyle.RUBBER))','val left = world.add(dynamicCircle(0.48f, Vec2(chassis.position.x - 0.78f, floor - 0.58f), MaterialStyle.RUBBER).also { (it.userData as? RenderTag)?.role = "wheel" })')
game_rep('val right = world.add(dynamicCircle(0.48f, Vec2(chassis.position.x + 0.78f, floor - 0.58f), MaterialStyle.RUBBER))','val right = world.add(dynamicCircle(0.48f, Vec2(chassis.position.x + 0.78f, floor - 0.58f), MaterialStyle.RUBBER).also { (it.userData as? RenderTag)?.role = "wheel" })')
game_rep('val wheel = world.add(dynamicCircle(0.9f, anchor.position.copy(), MaterialStyle.STEEL))','val wheel = world.add(dynamicCircle(0.9f, anchor.position.copy(), MaterialStyle.STEEL).also { (it.userData as? RenderTag)?.role = "wheel" })')
p.write_text(s)

p=ROOT/'app/build.gradle.kts'
s=p.read_text().replace('versionCode = 3','versionCode = 5').replace('versionName = "2.0.0"','versionName = "4.0.0"')
p.write_text(s)
print('Physics Playground 4.0 source patch applied')
