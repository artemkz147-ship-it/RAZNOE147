import json, math, os, random
import numpy as np
import trimesh
from trimesh.visual.material import PBRMaterial

ROOT=os.path.abspath(os.path.join(os.path.dirname(__file__),'..'))
LV=os.path.join(ROOT,'public','assets3d','levels'); PR=os.path.join(ROOT,'public','assets3d','props'); GEN=os.path.join(ROOT,'src','game3d')
for p in (LV,PR,GEN): os.makedirs(p,exist_ok=True)
COL={'roof':(52,60,72,255),'roof2':(71,77,87,255),'brick':(120,69,57,255),'concrete':(120,125,128,255),'metal':(80,92,105,255),'beam':(197,133,54,255),'wood':(129,86,52,255),'blue':(48,112,160,255),'red':(167,61,61,255),'glass':(120,185,210,150),'dark':(35,38,45,255)}
MAT={k:PBRMaterial(name=k,baseColorFactor=[v[0]/255,v[1]/255,v[2]/255,v[3]/255],metallicFactor=.65 if k in ('metal','beam') else 0,roughnessFactor=.35 if k=='glass' else .82,alphaMode='BLEND' if v[3]<255 else 'OPAQUE') for k,v in COL.items()}

def box(s,p,m='roof',ry=0):
    g=trimesh.creation.box(extents=s)
    if ry:g.apply_transform(trimesh.transformations.rotation_matrix(ry,[0,1,0]))
    g.apply_translation(p); g.visual.material=MAT[m]; return g

def cyl(r,h,p,m='metal',axis='y'):
    g=trimesh.creation.cylinder(radius=r,height=h,sections=16)
    if axis=='y':g.apply_transform(trimesh.transformations.rotation_matrix(math.pi/2,[1,0,0]))
    elif axis=='x':g.apply_transform(trimesh.transformations.rotation_matrix(math.pi/2,[0,1,0]))
    g.apply_translation(p); g.visual.material=MAT[m]; return g

def export(meshes,path): trimesh.Scene(meshes).export(path)
def C(p,s,k='solid'): return {'p':p,'s':s,'kind':k}

def prop_assets():
    export([box((1.4,1.4,1.4),(0,.7,0),'wood')],os.path.join(PR,'crate.glb'))
    export([box((2.8,1.5,.18),(0,.75,0),'wood'),box((.18,1.5,.5),(-1.15,.75,0),'metal'),box((.18,1.5,.5),(1.15,.75,0),'metal')],os.path.join(PR,'breakable_barrier.glb'))
    export([box((2.5,1.8,.08),(0,.9,0),'glass')],os.path.join(PR,'glass_panel.glb'))
    export([box((4.5,.26,.42),(0,.13,0),'beam')],os.path.join(PR,'moving_beam.glb'))
    export([cyl(.18,5,(0,2.5,0))],os.path.join(PR,'pole.glb'))
    export([box((.7,.18,.24),(0,0,0),'wood')],os.path.join(PR,'wood_fragment.glb'))
    export([box((.55,.06,.4),(0,0,0),'glass')],os.path.join(PR,'glass_fragment.glb'))
    export([box((.55,.12,.3),(0,0,0),'metal')],os.path.join(PR,'metal_fragment.glb'))

def dress(meshes,idx):
    rng=random.Random(1400+idx)
    for j in range(24):
        x=rng.uniform(-15,72); z=rng.choice((-1,1))*rng.uniform(14,34); h=rng.uniform(7,25); w=rng.uniform(5,12); d=rng.uniform(5,12)
        meshes.append(box((w,h,d),(x,-h/2-2,z),rng.choice(('dark','brick','roof2','concrete'))))
        if j%4==0: meshes.append(cyl(rng.uniform(.8,1.5),rng.uniform(1.2,2.3),(x,-.3,z),'metal'))
        if j%5==0: meshes.append(cyl(.06,rng.uniform(3,6),(x,1.2,z),'metal'))

def make(idx,name,subtitle,spawn,finish,plats,extras=(),breakables=(),movers=(),checkpoints=(),theme='roof'):
    meshes=[]; coll=[]
    for p,s,m in plats: meshes.append(box(s,p,m)); coll.append(C(list(p),list(s)))
    for e in extras:
        kind=e[0]
        if kind=='box': _,p,s,m,*rest=e; meshes.append(box(s,p,m,rest[0] if rest else 0)); coll.append(C(list(p),list(s)))
        elif kind=='pole': _,p,r,h=e; meshes.append(cyl(r,h,p))
        elif kind=='cap': _,p,s=e; meshes.append(box(s,p,'beam')); coll.append(C(list(p),list(s)))
        elif kind=='visual': _,p,s,m=e; meshes.append(box(s,p,m))
    dress(meshes,idx); export(meshes,os.path.join(LV,f'level_{idx:02d}.glb'))
    return {'id':idx,'name':name,'subtitle':subtitle,'asset':f'assets/levels/level_{idx:02d}.glb','spawn':spawn,'finish':finish,'colliders':coll,'breakables':list(breakables),'movers':list(movers),'checkpoints':list(checkpoints),'theme':theme}

prop_assets(); levels=[]
levels.append(make(1,'Первые крыши','Широкие прыжки, высота и контроль приземления',[0,1.2,0],[41,1.2,-1],[((0,0,0),(18,1,14),'roof'),((15,-1,-1),(8,1,9),'roof2'),((26,-.4,2),(9,1,8),'roof'),((38,.2,-1),(10,1,10),'roof2')],[('box',(4,1.1,-3),(2.2,1.2,1.5),'metal'),('box',(8,1.1,3),(2.2,1.2,1.5),'metal'),('box',(29,1.1,-1),(2.2,1.2,1.5),'metal'),('box',(34,1.1,2),(2.2,1.2,1.5),'metal')],[{'asset':'assets/props/breakable_barrier.glb','p':[20,.75,-1],'r':[0,math.pi/2,0],'threshold':7.5,'reward':20}],checkpoints=[[15,0,0],[28,0,1]]))
levels.append(make(2,'Рынок над улицей','Навесы, вывески, разбиваемые преграды и короткие точные прыжки',[0,1.2,0],[42,1.2,0],[((0,0,0),(12,1,12),'brick'),((10,-1,4),(7,1,7),'roof2'),((18,-2,0),(6,1,6),'roof'),((27,-1,-3),(8,1,8),'brick'),((38,0,0),(12,1,12),'roof2')],[('box',(14,1.3,1),(4,.18,1.5),'blue'),('box',(24,1,-1),(4,.18,1.5),'blue'),('box',(31,1.4,-5),(4,.18,1.5),'red')],[{'asset':'assets/props/crate.glb','p':[8,1.2,-2],'threshold':5.5,'reward':10},{'asset':'assets/props/glass_panel.glb','p':[33,1.4,0],'r':[0,math.pi/2,0],'threshold':6.2,'reward':25}],checkpoints=[[18,-1.5,0],[30,-.5,-2]],theme='brick'))
levels.append(make(3,'Стройка','Вертикальный маршрут, леса, балки и разрушаемые доски',[0,1.2,0],[44,9.2,0],[((0,0,0),(12,1,12),'concrete'),((11,2,-1),(7,1,7),'concrete'),((20,4,2),(7,1,7),'concrete'),((29,6,-2),(8,1,8),'concrete'),((40,8,0),(12,1,12),'concrete')],[('box',(5,2,-4),(8,.28,.42),'beam'),('box',(14,4,2),(6,.28,.42),'beam'),('box',(23,6,-2),(7,.28,.42),'beam'),('box',(34,8,2),(8,.28,.42),'beam')],[{'asset':'assets/props/breakable_barrier.glb','p':[25,6.8,-1],'r':[0,math.pi/2,0],'threshold':7,'reward':25}],checkpoints=[[20,4.6,2],[31,6.6,-2]],theme='construction'))
levels.append(make(4,'Старый квартал','Балконы, карнизы, вывески и прыжки через окна',[0,1.2,0],[44,3.2,1],[((0,0,0),(12,1,10),'brick'),((13,-1,-3),(6,1,6),'brick'),((22,1,2),(7,1,6),'brick'),((31,0,-2),(6,1,6),'brick'),((41,2,1),(10,1,9),'brick')],[('box',(6,2,-4.4),(5,.28,1),'metal'),('box',(17,1.6,0),(3.5,.28,1),'metal'),('box',(27,2.8,-4.1),(4,.28,1),'metal'),('box',(36,2.3,1),(3.8,.28,1),'metal')],[{'asset':'assets/props/glass_panel.glb','p':[39,3,1],'r':[0,math.pi/2,0],'threshold':6,'reward':30}],checkpoints=[[22,2,2],[33,1,-2]],theme='oldtown'))
levels.append(make(5,'Высотный ветер','Большие разрывы, движущаяся балка и минимум места для ошибки',[0,1.2,0],[50,4.2,2],[((0,0,0),(12,1,12),'dark'),((16,-2,0),(8,1,8),'dark'),((31,1,-4),(9,1,9),'dark'),((46,3,2),(12,1,12),'dark')],movers=[{'asset':'assets/props/moving_beam.glb','p':[23,.8,3],'axis':'z','distance':5,'speed':.75,'collider':[4.5,.26,.42]}],checkpoints=[[16,-1.4,0],[32,1.6,-4]],theme='highrise'))
levels.append(make(6,'Краны','Узкие крановые балки, вертикальные стойки и движущиеся секции',[0,1.2,0],[50,3.2,0],[((0,0,0),(10,1,10),'concrete'),((48,2,0),(10,1,10),'concrete')],[('box',(10,2,0),(12,.32,.5),'beam'),('box',(22,3,-2),(10,.32,.5),'beam'),('box',(34,4,1),(11,.32,.5),'beam'),('pole',(16,1.2,-2),.16,5),('pole',(28,2.2,1),.16,5),('pole',(40,3.2,0),.16,5)],movers=[{'asset':'assets/props/moving_beam.glb','p':[27,4,4],'axis':'x','distance':4,'speed':.9,'collider':[4.5,.26,.42]}],checkpoints=[[18,3.2,-2],[36,4.3,1]],theme='crane'))
poles=[(9,0),(14,3),(19,-1),(24,2),(29,-3),(34,1),(39,0)]; ex=[]
for x,z in poles: ex += [('pole',(x,1.5,z),.22,4),('cap',(x,3.58,z),(.8,.18,.8))]
levels.append(make(7,'Точка опоры','Те самые точные прыжки: столбы, маленькие площадки и узкие балки',[0,1.2,0],[48,1.2,0],[((0,0,0),(10,1,10),'dark'),((45,0,0),(10,1,10),'dark')],ex,checkpoints=[[24,3.9,2],[39,3.9,0]],theme='precision'))
ex=[]
for x,z,h in [(9,0,3.5),(15,3,4.5),(21,-2,5.2),(28,2,6)]: ex += [('pole',(x,h/2-.5,z),.2,h),('cap',(x,h-.38,z),(.7,.16,.7))]
ex += [('box',(34,5.8,-1),(7,.24,.44),'beam'),('box',(43,6.2,2),(6,.24,.44),'beam'),('box',(51,6,-2),(7,.24,.44),'beam')]
levels.append(make(8,'Небесная линия','Финальная цепочка: столбы, балки, движущаяся секция и прыжок сквозь стекло',[0,1.2,0],[64,6.2,0],[((0,0,0),(10,1,10),'roof'),((60,5,0),(12,1,12),'roof2')],ex,[{'asset':'assets/props/glass_panel.glb','p':[56,6.2,0],'r':[0,math.pi/2,0],'threshold':7.5,'reward':60}],[{'asset':'assets/props/moving_beam.glb','p':[39,6.5,-4],'axis':'z','distance':5,'speed':1.05,'collider':[4.5,.26,.42]}],[[28,6,2],[48,6.4,0]],'final'))
with open(os.path.join(GEN,'levels.json'),'w',encoding='utf-8') as f: json.dump(levels,f,ensure_ascii=False,indent=2)
print(f'Baked {len(levels)} static GLB levels into public/assets3d')
