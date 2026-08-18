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
scene.render.resolution_percentage=100


def clear_scene():
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)
    for datablocks in (bpy.data.meshes,bpy.data.curves,bpy.data.armatures,bpy.data.cameras,bpy.data.lights):
        pass


def look_at(obj, target):
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
    mn=Vector((min(p.x for p in pts),min(p.y for p in pts),min(p.z for p in pts)))
    mx=Vector((max(p.x for p in pts),max(p.y for p in pts),max(p.z for p in pts)))
    return mn,mx


def add_lights(center,size):
    world=scene.world or bpy.data.worlds.new('PortraitWorld')
    scene.world=world
    world.use_nodes=True
    world.node_tree.nodes['Background'].inputs['Color'].default_value=(0.035,0.055,0.07,1)
    world.node_tree.nodes['Background'].inputs['Strength'].default_value=0.45
    def area(name,loc,energy,color,scale):
        data=bpy.data.lights.new(name,'AREA');data.energy=energy;data.color=color;data.size=scale
        ob=bpy.data.objects.new(name,data);scene.collection.objects.link(ob);ob.location=loc;look_at(ob,center);return ob
    area('Key',(center[0]-size*1.8,center[1]-size*2.2,center[2]+size*2.4),900,(1.0,.78,.56),size*2.4)
    area('Fill',(center[0]+size*2.0,center[1]-size*1.6,center[2]+size*1.4),560,(.52,.78,1.0),size*2.0)
    area('Rim',(center[0]+size*.5,center[1]+size*2.0,center[2]+size*2.2),760,(.82,.60,1.0),size*1.6)


def render_one(src,dst,kind):
    clear_scene()
    bpy.ops.import_scene.gltf(filepath=src)
    imported=list(scene.objects)
    roots=[o for o in imported if o.parent is None and o.type not in {'CAMERA','LIGHT'}]
    # Game characters face the gameplay camera after a PI turn; match that presentation.
    if kind=='hero':
        for r in roots:r.rotation_euler[2]+=math.pi
    bpy.context.view_layer.update()
    mn,mx=world_bounds(imported)
    center=(mn+mx)*.5
    height=max(.1,mx.z-mn.z)
    width=max(.1,mx.x-mn.x)
    depth=max(.1,mx.y-mn.y)
    size=max(height,width,depth)
    cam_data=bpy.data.cameras.new('PortraitCamera');cam_data.type='ORTHO'
    cam=bpy.data.objects.new('PortraitCamera',cam_data);scene.collection.objects.link(cam);scene.camera=cam
    if kind=='hero':
        target=Vector((center.x,center.y,mn.z+height*.58))
        cam.location=(center.x,center.y-size*3.0,mn.z+height*.62)
        cam_data.ortho_scale=max(height*1.08,width*1.38)
    else:
        target=center.copy();cam.location=(center.x,center.y-size*3.2,center.z+size*.34);cam_data.ortho_scale=size*1.45
    look_at(cam,target)
    add_lights(target,size)
    # Small turntable angle makes weapons read better in cards.
    if kind=='weapon':
        for r in roots:
            r.rotation_euler[2]+=math.radians(-18)
            r.rotation_euler[0]+=math.radians(6)
    scene.render.filepath=dst
    bpy.ops.render.render(write_still=True)
    if not os.path.isfile(dst) or os.path.getsize(dst)<1000:
        raise RuntimeError(f'portrait render failed: {dst}')
    print('rendered',dst)

for i in range(1,16):
    render_one(os.path.join(assets,f'hero-{i:02d}.glb'),os.path.join(out,f'hero-{i:02d}.png'),'hero')
for i in range(1,16):
    render_one(os.path.join(assets,f'weapon-{i:02d}.glb'),os.path.join(out,f'weapon-{i:02d}.png'),'weapon')
print('portrait atlas complete:',out)
