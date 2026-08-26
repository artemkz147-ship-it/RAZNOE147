import trimesh, numpy as np, math
from trimesh.visual.material import PBRMaterial
from pathlib import Path

import sys
out=Path(sys.argv[1] if len(sys.argv)>1 else 'carrier_hd.glb')
out.parent.mkdir(parents=True,exist_ok=True)
scene=trimesh.Scene()

def mat(name,color,metal=0.0,rough=0.7,emissive=None):
    rgba=np.array(color,dtype=np.uint8)
    if len(rgba)==3: rgba=np.r_[rgba,255]
    m=PBRMaterial(name=name, baseColorFactor=rgba, metallicFactor=float(metal), roughnessFactor=float(rough))
    if emissive is not None:
        m.emissiveFactor=np.array(emissive,dtype=float)
    return m
M={
'hull':mat('hull',(62,75,84),.35,.55),'dark':mat('deck',(40,45,50),.18,.83),
'island':mat('island',(104,115,121),.3,.58),'white':mat('deck_white',(230,231,218),0,.65),
'yellow':mat('deck_yellow',(236,197,54),0,.62),'red':mat('deck_red',(179,55,43),0,.6),
'glass':mat('glass',(45,87,104),.15,.24),'black':mat('black',(20,22,24),.5,.4),
'jet':mat('parked_aircraft',(112,120,128),.22,.55),'light':mat('deck_light',(245,239,205),0,.3,(.9,.82,.55)),
}

def add(mesh,name,material=None,transform=None):
    mesh=mesh.copy()
    if material is not None: mesh.visual.material=material
    scene.add_geometry(mesh,node_name=name,geom_name=name,transform=transform)

def box(ext,pos=(0,0,0),rot=0,name='box',material=None):
    mesh=trimesh.creation.box(extents=ext)
    T=trimesh.transformations.rotation_matrix(rot,[0,1,0]) if rot else np.eye(4)
    T[:3,3]=pos
    add(mesh,name,material,T)

def cyl(radius,height,pos=(0,0,0),axis='y',name='cyl',material=None,sections=20):
    mesh=trimesh.creation.cylinder(radius=radius,height=height,sections=sections)
    T=trimesh.transformations.rotation_matrix(math.pi/2,[1,0,0]) if axis=='y' else np.eye(4)
    T[:3,3]=pos
    add(mesh,name,material,T)

def sphere(radius,pos,name,material):
    mesh=trimesh.creation.icosphere(subdivisions=2,radius=radius)
    T=np.eye(4);T[:3,3]=pos;add(mesh,name,material,T)

box((148,28,420),(0,-13,10),name='hull_center',material=M['hull'])
for i in range(7):
    z=225+i*34; width=148-(i+1)*12
    box((max(width,52),25,38),(0,-12,z),name=f'hull_bow_{i}',material=M['hull'])
for i in range(4):
    z=-225-i*38; width=148-(i+1)*17
    box((max(width,70),25,42),(0,-12,z),name=f'hull_stern_{i}',material=M['hull'])
box((142,4,565),(0,-28,-5),name='waterline',material=M['red'])
box((188,8,642),(0,8,0),name='flight_deck',material=M['dark'])
box((72,5,410),(-56,13,-25),rot=-0.12,name='angled_deck',material=M['dark'])
box((150,5,70),(0,10,320),name='bow_deck',material=M['dark'])
box((166,5,60),(0,10,-320),name='stern_deck',material=M['dark'])
box((44,45,108),(63,36,45),name='island_base',material=M['island'])
box((36,32,74),(64,72,42),name='island_mid',material=M['island'])
box((30,24,54),(62,100,33),name='island_top',material=M['island'])
for z in [18,30,42,54]: box((39,8,8),(63,84,z),name=f'bridge_window_{z}',material=M['glass'])
cyl(3,60,(62,137,33),name='mast',material=M['island'],sections=16)
for y,r in [(162,12),(176,8)]:
    sphere(r,(62,y,33),f'radar_dome_{y}',M['white'])
    box((3,2,28),(62,y+2,33),rot=.45,name=f'radar_bar_{y}',material=M['black'])
for dx,dz in [(-7,-6),(8,-3),(-6,8),(7,9)]: cyl(.7,25,(62+dx,156,33+dz),name=f'antenna_{dx}_{dz}',material=M['black'],sections=8)
for x,z,w,d in [(58,-140,40,64),(-65,140,44,70),(56,205,38,58),(-62,-245,42,62)]:
    box((w,.8,d),(x,12.5,z),name=f'elevator_{x}_{z}',material=M['island'])
    for sx in (-1,1): box((1.1,.6,d),(x+sx*w/2,13,z),name=f'elev_edge_x_{x}_{z}_{sx}',material=M['yellow'])
    for sz in (-1,1): box((w,.6,1.1),(x,13,z+sz*d/2),name=f'elev_edge_z_{x}_{z}_{sz}',material=M['yellow'])
for z in range(-245,220,32): box((2,.4,18),(-25,13.2,z),name=f'centerline_{z}',material=M['white'])
for x in [-46,-10,28,58]: box((1.2,.45,245),(x,13.3,130),name=f'catapult_{x}',material=M['yellow'])
for z in [-175,-157,-139,-121]: box((145,.5,1.3),(-18,13.6,z),rot=-.12,name=f'arrest_{z}',material=M['white'])
for x in [-88,88]: box((1.4,.4,600),(x,13.2,0),name=f'edge_line_{x}',material=M['white'])
for z in [-90,-60,-30,0,30,60,90]: box((11,.5,28),(-45,13.4,z),rot=-.12,name=f'touch_{z}',material=M['white'])
for side,x in [('L',-91),('R',91)]:
    for i,z in enumerate(range(-290,291,40)): sphere(1.35,(x,14,z),f'light_{side}_{i}',M['light'])

def add_jet(x,z,heading,idx,scale=1.0):
    Trot=trimesh.transformations.rotation_matrix(heading,[0,1,0])
    def local_box(ext,lp,sub):
        mesh=trimesh.creation.box(extents=np.array(ext)*scale)
        T=np.array(Trot); T[:3,3]=[x+lp[0]*scale, 16+lp[1]*scale, z+lp[2]*scale]
        add(mesh,f'jet{idx}_{sub}',M['jet'],T)
    local_box((3,2.4,21),(0,0,0),'body'); local_box((19,.7,5),(0,.2,-1),'wing'); local_box((8,.6,3),(0,1.2,-8),'tailplane'); local_box((1,.8,5),(0,3,-8),'tailfin')
for i,(x,z,h) in enumerate([(-67,220,.05),(-55,175,.08),(55,255,-.1),(58,180,-.05),(-65,-220,3.05),(55,-245,3.15),(-63,115,.12),(54,105,-.08)]): add_jet(x,z,h,i,.8)
for i,z in enumerate([-250,-145,-45,75,185,270]):
    box((18,5,28),(-99,0,z),name=f'sponson_l_{i}',material=M['hull']); box((18,5,28),(99,0,z),name=f'sponson_r_{i}',material=M['hull'])
for i,(x,z) in enumerate([(-98,-245),(98,-230),(-98,235),(98,250)]):
    cyl(5,5,(x,16,z),name=f'turret_{i}',material=M['island'],sections=16); box((2,2,18),(x,19,z-7),name=f'turret_barrel_{i}',material=M['black'])
scene.export(out)
print(out, out.stat().st_size, 'bytes', 'geometries', len(scene.geometry))
