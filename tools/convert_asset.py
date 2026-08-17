import bpy
import os
import sys

args = sys.argv[sys.argv.index('--') + 1:]
if len(args) < 2:
    raise SystemExit('usage: convert_asset.py SOURCE OUTPUT [static]')
source, output = map(os.path.abspath, args[:2])
static = len(args) > 2 and args[2].lower() == 'static'

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
for datablocks in (bpy.data.meshes, bpy.data.curves, bpy.data.materials):
    pass

ext = os.path.splitext(source)[1].lower()
if ext == '.fbx':
    bpy.ops.import_scene.fbx(filepath=source, automatic_bone_orientation=False)
elif ext == '.obj':
    try:
        bpy.ops.wm.obj_import(filepath=source)
    except Exception:
        bpy.ops.import_scene.obj(filepath=source)
else:
    raise RuntimeError(f'Unsupported asset format: {ext}')

for obj in bpy.context.scene.objects:
    if hasattr(obj, 'hide_render'):
        obj.hide_render = False

os.makedirs(os.path.dirname(output), exist_ok=True)
bpy.ops.export_scene.gltf(
    filepath=output,
    export_format='GLB',
    export_yup=True,
    export_apply=False,
    export_animations=not static,
    export_nla_strips=not static,
    export_force_sampling=not static,
    export_materials='EXPORT',
)
print(f'EXPORTED {source} -> {output}')
