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

# The source FBX files contain a library of separate Actions. Blender's glTF
# exporter only includes actions that are active or stashed on NLA tracks.
# Stash every armature action explicitly so Idle / Run / Attack / Death and
# the rest of the authored clips survive the FBX -> GLB production pipeline.
def stash_armature_actions():
    armatures = [obj for obj in bpy.context.scene.objects if obj.type == 'ARMATURE']
    if not armatures:
        return 0

    actions = []
    for action in list(bpy.data.actions):
        paths = [fc.data_path for fc in action.fcurves]
        if any(path.startswith('pose.bones[') for path in paths):
            actions.append(action)

    if not actions:
        return 0

    # Quaternius animated character files used here contain one armature each.
    # Associate the complete imported action library with that armature.
    arm = armatures[0]
    if arm.animation_data is None:
        arm.animation_data_create()
    arm.animation_data.action = None
    tracks = arm.animation_data.nla_tracks
    while len(tracks):
        tracks.remove(tracks[0])

    for action in actions:
        start = int(action.frame_range[0])
        track = tracks.new()
        track.name = action.name
        strip = track.strips.new(action.name, start, action)
        strip.action_frame_start = action.frame_range[0]
        strip.action_frame_end = action.frame_range[1]

    print(f'Prepared {len(actions)} armature actions for glTF export')
    return len(actions)

if not static:
    stash_armature_actions()

# The Knight pack contains idle/roll/run clips whose names also include
# "sword". Keep that token only on actual attack actions so runtime semantic
# matching cannot mistake Idle_swordRight or Roll_sword for an attack.
if os.path.basename(source).lower() == 'knightcharacter.fbx':
    for action in bpy.data.actions:
        lower_name = action.name.lower()
        if 'sword' in lower_name and 'attack' not in lower_name:
            action.name = action.name.replace('sword', 'weapon').replace('Sword', 'Weapon')
    # Keep NLA track names synchronized with renamed actions.
    for obj in bpy.context.scene.objects:
        if obj.type == 'ARMATURE' and obj.animation_data:
            for track in obj.animation_data.nla_tracks:
                if len(track.strips):
                    track.name = track.strips[0].action.name
                    track.strips[0].name = track.strips[0].action.name

for obj in bpy.context.scene.objects:
    if hasattr(obj, 'hide_render'):
        obj.hide_render = False

bpy.context.scene.frame_set(0)
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
