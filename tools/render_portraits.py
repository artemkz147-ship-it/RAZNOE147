import bpy, math, os, sys
from mathutils import Vector

args=sys.argv
args=args[args.index('--')+1:] if '--' in args else []
assets=args[0] if len(args)>0 else 'public/assets'
out=args[1] if len(args)>1 else 'public/portraits'
os.makedirs(out,exist_ok=True)

scene=bpy.context.scene
scene.render.engine='BLENDER_EEVEE'
scene.render.resolution_x=256
scene.render.resolution_y=256
scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG'
scene.render.film_transparent=True
scene.view_settings.view_transform='Standard'
scene.view_settings.look='Medium High Contrast'
scene.view_settings.exposure=0.15
scene.view_settings.gamma=1.0
scene.render.image_settings.color_mode='RGBA'


def clear_scene():
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)
    # Imported glTF actions must not leak into the next portrait.
    for action in list(bpy.data.actions):
        if action.users==0:
            bpy.data.actions.remove(action)


def look_at(obj,target):
    direction=Vector(target)-obj.location
    obj.rotation_euler=direction.to_track_quat('-Z','Y').to_euler()


def world_bounds(objects):
    pts=[]
    for ob in objects:
        if ob.type not in {'MESH','CURVE','SURFACE','FONT','META'}: continue
        for c in ob.bound_box:
            pts.append(ob.matrix_world @ Vector(c))
    if not pts:
        return Vector((-1,0,-1)),Vector((1,2,1))
    return Vector((min(p.x for p in pts),min(p.y for p in pts),min(p.z for p in pts))),Vector((max(p.x for p in pts),max(p.y for p in pts),max(p.z for p in pts)))


def pose_idle():
    arms=[o for o in scene.objects if o.type=='ARMATURE']
    actions=list(bpy.data.actions)
    preferred=None
    for token in ('idle','standing','stand'):
        preferred=next((a for a in actions if token in a.name.lower() and not any(x in a.name.lower() for x in ('death','dead','die'))),None)
        if preferred: break
    if preferred is None and actions:
        preferred=next((a for a in actions if not any(x in a.name.lower() for x in ('death','dead','die'))),actions[0])
    if preferred:
        for arm in arms:
            arm.animation_data_create();arm.animation_data.action=preferred
        lo,hi=preferred.frame_range
        scene.frame_start=int(lo);scene.frame_end=max(int(hi),int(lo)+1)
        scene.frame_set(int(lo+(hi-lo)*.32))
        bpy.context.view_layer.update()
        print('portrait pose:',preferred.name,preferred.frame_range)


def add_lights(center,size):
    world=scene.world or bpy.data.worlds.new('PortraitWorld')
    scene.world=world
    world.use_nodes=True
    world.node_tree.nodes['Background'].inputs['Color'].default_value=(0.025,0.045,0.065,1)
    world.node_tree.nodes['Background'].inputs['Strength'].default_value=0.38
    def area(name,loc,energy,color,scale):
        data=bpy.data.lights.new(name,'AREA');data.energy=energy;data.color=color;data.size=scale
        ob=bpy.data.objects.new(name,data);scene.collection.objects.link(ob);ob.location=loc;look_at(ob,center)
    area('Key',(center[0]-size*1.7,center[1]-size*2.0,center[2]+size*2.2),980,(1.0,.78,.55),size*2.2)
    area('Fill',(center[0]+size*1.8,center[1]-size*1.4,center[2]+size*1.3),540,(.48,.73,1.0),size*1.8)
    area('Rim',(center[0]+size*.6,center[1]+size*2.0,center[2]+size*2.0),820,(.82,.56,1.0),size*1.5)


def render_one(src,dst,kind):
    clear_scene()
    bpy.ops.import_scene.gltf(filepath=src)
    imported=list(scene.objects)
    roots=[o for o in imported if o.parent is None and o.type not in {'CAMERA','LIGHT'}]
    if kind=='hero':
        pose_idle()
        # Match the front-facing gameplay presentation, then give cards a small 3/4 turn.
        for r in roots:r.rotation_euler[2]+=math.pi+math.radians(8)
    bpy.context.view_layer.update()
    mn,mx=world_bounds(imported)
    center=(mn+mx)*.5
    height=max(.1,mx.z-mn.z);width=max(.1,mx.x-mn.x);depth=max(.1,mx.y-mn.y);size=max(height,width,depth)
    cam_data=bpy.data.cameras.new('PortraitCamera');cam_data.type='ORTHO'
    cam=bpy.data.objects.new('PortraitCamera',cam_data);scene.collection.objects.link(cam);scene.camera=cam
    if kind=='hero':
        target=Vector((center.x,center.y,mn.z+height*.59))
        cam.location=(center.x,center.y-size*3.0,mn.z+height*.64)
        cam_data.ortho_scale=max(height*1.04,width*1.34)
    else:
        target=center.copy();cam.location=(center.x,center.y-size*3.2,center.z+size*.34);cam_data.ortho_scale=size*1.42
        for r in roots:
            r.rotation_euler[2]+=math.radians(-18);r.rotation_euler[0]+=math.radians(6)
    look_at(cam,target)
    add_lights(target,size)
    scene.render.filepath=dst
    bpy.ops.render.render(write_still=True)
    if not os.path.isfile(dst) or os.path.getsize(dst)<1000:
        raise RuntimeError(f'portrait render failed: {dst}')
    print('rendered',dst,os.path.getsize(dst))

for i in range(1,16):
    render_one(os.path.join(assets,f'hero-{i:02d}.glb'),os.path.join(out,f'hero-{i:02d}.png'),'hero')
for i in range(1,16):
    render_one(os.path.join(assets,f'weapon-{i:02d}.glb'),os.path.join(out,f'weapon-{i:02d}.png'),'weapon')
print('portrait atlas complete:',out)
