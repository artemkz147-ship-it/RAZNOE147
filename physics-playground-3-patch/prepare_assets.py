from pathlib import Path
import math, re, sys
import numpy as np
import trimesh

ROOT=Path('physics-build/src/physics2')
OUT=ROOT/'app/src/main/assets/models'; OUT.mkdir(parents=True,exist_ok=True)
FACTORY=Path('/tmp/kenney/factory'); TOY=Path('/tmp/kenney/toy')

def cat(*meshes): return trimesh.util.concatenate([m for m in meshes if m is not None])
def box(ext, at=(0,0,0)):
    m=trimesh.creation.box(extents=ext); m.apply_translation(at); return m
def cyl(r,h,at=(0,0,0),axis='z',sections=24):
    m=trimesh.creation.cylinder(radius=r,height=h,sections=sections)
    if axis=='x': m.apply_transform(trimesh.transformations.rotation_matrix(math.pi/2,[0,1,0]))
    if axis=='y': m.apply_transform(trimesh.transformations.rotation_matrix(math.pi/2,[1,0,0]))
    m.apply_translation(at); return m

def normalized(mesh):
    mesh=mesh.copy(); mesh.remove_unreferenced_vertices()
    mesh.apply_translation(-mesh.bounding_box.centroid)
    ext=mesh.extents; scale=max(float(ext.max()),1e-6)
    mesh.apply_scale(1.0/scale)
    return mesh

def save(name,mesh): normalized(mesh).export(OUT/f'{name}.obj')

# Detailed bundled fallback geometry. Release builds then replace key pieces with CC0 Kenney meshes.
save('ball',trimesh.creation.icosphere(subdivisions=2,radius=.5))
wheel=cat(trimesh.creation.torus(major_radius=.38,minor_radius=.12,major_sections=32,minor_sections=12),cyl(.24,.18),*[box((.62,.07,.12)) if i==0 else box((.07,.62,.12)) for i in range(2)])
save('wheel',wheel)
crate=cat(box((1,.82,.72)),box((1.05,.08,.08),(0,.38,.38)),box((1.05,.08,.08),(0,-.38,.38)),box((.08,.86,.08),(.46,0,.38)),box((.08,.86,.08),(-.46,0,.38)))
save('crate',crate)
steel=cat(box((1,.72,.7)),*[cyl(.055,.05,(sx*.43,sy*.31,.37)) for sx in (-1,1) for sy in (-1,1)])
save('steel_block',steel)
save('weight',cat(cyl(.38,.72),cyl(.18,.92)))
save('ice_block',box((1,.82,.72))); save('glass_block',box((1,.82,.72)))
save('jelly',trimesh.creation.icosphere(subdivisions=2,radius=.5).apply_scale([1,.82,.72]) or trimesh.creation.icosphere(subdivisions=2,radius=.5))
save('beam',cat(box((1,.16,.32)),cyl(.075,.36,(-.43,0,0)),cyl(.075,.36,(.43,0,0))))
save('motor',cat(cyl(.34,.62),cyl(.14,.95),box((.70,.18,.18),(0,-.24,0))))
save('piston',cat(cyl(.16,.72,(-.13,0,0),'x'),cyl(.09,.72,(.25,0,0),'x'),box((.22,.42,.42),(-.40,0,0))))
save('machine',cat(box((1,.48,.36),(0,0,.08)),box((.58,.48,.34),(-.10,-.15,.36)),cyl(.16,.52,(.34,0,.25),'y')))

def all_obj(root): return list(root.rglob('*.obj'))+list(root.rglob('*.OBJ'))
def pick(root,patterns):
    files=all_obj(root)
    for pat in patterns:
        rx=re.compile(pat,re.I)
        hits=[p for p in files if rx.search(p.stem)]
        if hits: return sorted(hits,key=lambda p:(len(p.name),str(p)))[0]
    return None

def import_obj(src,dest):
    if not src: return False
    loaded=trimesh.load(src,force='scene',process=False)
    if isinstance(loaded,trimesh.Scene):
        geoms=[g for g in loaded.geometry.values() if len(g.faces)>0]
        if not geoms: return False
        mesh=trimesh.util.concatenate(geoms)
    else: mesh=loaded
    # Kenney uses Y-up. Map original X/Z to the app's X/Y plane.
    mesh.apply_transform(trimesh.transformations.rotation_matrix(-math.pi/2,[1,0,0]))
    save(dest,mesh)
    print(f'KENNEY_MODEL {dest}: {src}')
    return True

selected={}
selected['wheel']=pick(TOY,[r'wheel',r'tire'])
selected['machine']=pick(TOY,[r'body',r'chassis',r'vehicle',r'car'])
selected['crate']=pick(FACTORY,[r'crate',r'container',r'box'])
selected['motor']=pick(FACTORY,[r'generator',r'motor',r'engine',r'machine'])
selected['piston']=pick(FACTORY,[r'piston',r'press',r'hydraulic',r'pipe'])
selected['beam']=pick(FACTORY,[r'beam',r'girder',r'support',r'bar'])
selected['steel_block']=pick(FACTORY,[r'container',r'machine',r'crate'])
for dest,src in selected.items(): import_obj(src,dest)

if not selected['wheel'] or not selected['machine']:
    print('Toy Car OBJ inventory sample:')
    for p in all_obj(TOY)[:80]: print(p)
    raise SystemExit('Required Kenney wheel/body model not found')
if not selected['crate']:
    print('Factory OBJ inventory sample:')
    for p in all_obj(FACTORY)[:100]: print(p)
    raise SystemExit('Required Kenney factory crate/container model not found')

print('3D assets ready:',len(list(OUT.glob('*.obj'))),'stable OBJ files')
