from pathlib import Path
import math, re
import numpy as np
import trimesh

ROOT=Path('physics-build/src/physics2')
OUT=ROOT/'app/src/main/assets/models'; OUT.mkdir(parents=True,exist_ok=True)
FACTORY=Path('/tmp/kenney/factory'); TOY=Path('/tmp/kenney/toy')

def cat(*meshes): return trimesh.util.concatenate([m for m in meshes if m is not None])
def box(ext, at=(0,0,0)):
    m=trimesh.creation.box(extents=ext); m.apply_translation(at); return m
def cyl(r,h,at=(0,0,0),axis='z',sections=32):
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

# High quality bundled fallbacks. Release builds replace many of these with CC0 Kenney assets.
save('ball',trimesh.creation.icosphere(subdivisions=3,radius=.5))
wheel=cat(
    trimesh.creation.torus(major_radius=.36,minor_radius=.13,major_sections=48,minor_sections=16),
    cyl(.21,.20),
    *[box((.64,.055,.12)) if i==0 else box((.055,.64,.12)) for i in range(2)],
    *[cyl(.035,.22,(math.cos(a)*.24,math.sin(a)*.24,0)) for a in np.linspace(0,2*math.pi,8,endpoint=False)]
)
save('wheel',wheel)
save('wheel2',cat(trimesh.creation.torus(major_radius=.37,minor_radius=.14,major_sections=48,minor_sections=18),cyl(.25,.22),*[box((.58,.045,.12)) if i==0 else box((.045,.58,.12)) for i in range(2)]))
crate=cat(box((1,.82,.72)),box((1.05,.08,.08),(0,.38,.38)),box((1.05,.08,.08),(0,-.38,.38)),box((.08,.86,.08),(.46,0,.38)),box((.08,.86,.08),(-.46,0,.38)),box((.08,.08,.78),(.46,.38,0)),box((.08,.08,.78),(-.46,-.38,0)))
save('crate',crate)
steel=cat(box((1,.72,.7)),*[cyl(.055,.05,(sx*.43,sy*.31,.37)) for sx in (-1,1) for sy in (-1,1)],box((.72,.05,.05),(0,.0,.38)))
save('steel_block',steel)
save('weight',cat(cyl(.38,.72),cyl(.18,.92),cyl(.08,1.04)))
save('ice_block',box((1,.82,.72))); save('glass_block',box((1,.82,.72)))
jelly=trimesh.creation.icosphere(subdivisions=3,radius=.5); jelly.apply_scale([1,.82,.72]); save('jelly',jelly)
save('beam',cat(box((1,.16,.32)),cyl(.075,.36,(-.43,0,0)),cyl(.075,.36,(.43,0,0)),box((.90,.035,.36),(0,0,.02))))
save('motor',cat(cyl(.34,.62),cyl(.14,.95),box((.70,.18,.18),(0,-.24,0)),*[cyl(.04,.70,(math.cos(a)*.27,math.sin(a)*.27,0)) for a in np.linspace(0,2*math.pi,8,endpoint=False)]))
save('piston',cat(cyl(.16,.72,(-.13,0,0),'x'),cyl(.09,.72,(.25,0,0),'x'),box((.22,.42,.42),(-.40,0,0)),cyl(.22,.10,(-.48,0,0),'x')))
save('machine',cat(box((1,.48,.36),(0,0,.08)),box((.58,.48,.34),(-.10,-.15,.36)),cyl(.16,.52,(.34,0,.25),'y'),box((.30,.18,.22),(.30,-.05,.42))))
save('machine2',cat(box((1,.42,.30),(0,0,.02)),box((.46,.42,.38),(-.18,-.10,.31)),box((.28,.42,.20),(.32,.02,.24)),cyl(.10,.48,(.44,0,.12),'y')))
save('barrel',cat(cyl(.40,.82),trimesh.creation.torus(major_radius=.40,minor_radius=.025,major_sections=32,minor_sections=8),trimesh.creation.torus(major_radius=.40,minor_radius=.025,major_sections=32,minor_sections=8)))
save('pipe',cat(cyl(.18,1.0,axis='x'),cyl(.24,.12,(-.44,0,0),'x'),cyl(.24,.12,(.44,0,0),'x')))
gear=trimesh.creation.cylinder(radius=.38,height=.18,sections=24)
for a in np.linspace(0,2*math.pi,12,endpoint=False):
    tooth=box((.18,.10,.20),(math.cos(a)*.43,math.sin(a)*.43,0)); tooth.apply_transform(trimesh.transformations.rotation_matrix(a,[0,0,1])); gear=cat(gear,tooth)
save('gear',cat(gear,cyl(.12,.24)))
save('platform',cat(box((1,.14,.36)),box((.92,.03,.42),(0,.05,0)),cyl(.05,.40,(-.44,0,0)),cyl(.05,.40,(.44,0,0))))

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
    mesh.apply_transform(trimesh.transformations.rotation_matrix(-math.pi/2,[1,0,0]))
    save(dest,mesh)
    print(f'KENNEY_MODEL {dest}: {src}')
    return True

selected={
    'wheel': pick(TOY,[r'wheel',r'tire']),
    'wheel2': pick(TOY,[r'wheel.*wide',r'tire.*wide',r'wheel']),
    'machine': pick(TOY,[r'body',r'chassis',r'vehicle',r'car']),
    'machine2': pick(TOY,[r'body.*truck',r'chassis.*truck',r'truck',r'body']),
    'crate': pick(FACTORY,[r'crate',r'container',r'box']),
    'motor': pick(FACTORY,[r'generator',r'motor',r'engine',r'machine']),
    'piston': pick(FACTORY,[r'piston',r'press',r'hydraulic',r'pipe']),
    'beam': pick(FACTORY,[r'beam',r'girder',r'support',r'bar']),
    'platform': pick(FACTORY,[r'platform',r'walkway',r'bridge',r'beam']),
    'steel_block': pick(FACTORY,[r'container',r'machine',r'crate']),
    'barrel': pick(FACTORY,[r'barrel',r'drum',r'tank']),
    'pipe': pick(FACTORY,[r'pipe',r'tube',r'duct']),
    'gear': pick(FACTORY,[r'gear',r'cog',r'wheel']),
}
for dest,src in selected.items():
    if src: import_obj(src,dest)

if not selected['wheel'] or not selected['machine']:
    print('Toy Car OBJ inventory sample:')
    for p in all_obj(TOY)[:80]: print(p)
    raise SystemExit('Required Kenney wheel/body model not found')
if not selected['crate']:
    print('Factory OBJ inventory sample:')
    for p in all_obj(FACTORY)[:100]: print(p)
    raise SystemExit('Required Kenney factory crate/container model not found')

print('4.0 3D assets ready:',len(list(OUT.glob('*.obj'))),'stable OBJ files')
