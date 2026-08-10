#!/usr/bin/env python3
from pathlib import Path
from PIL import Image,ImageOps,ImageDraw
import json
import build_real_assets as b

SRC=b.SRC
SIZE=(b.STAGE_W,b.STAGE_H)

def fit_layer(path):
    im=Image.open(path).convert('RGBA')
    return ImageOps.fit(im,SIZE,method=Image.Resampling.LANCZOS)

def alpha_stack(paths,base=(6,8,12,255)):
    out=Image.new('RGBA',SIZE,base);used=[]
    for p in paths:
        p=Path(p)
        if not p.exists():continue
        try:
            out.alpha_composite(fit_layer(p));used.append(p)
        except Exception as e:print('LAYER_SKIP',p,e)
    return out.convert('RGB'),used

def darkcity():
    r=SRC/'backgrounds'/'DarkCity';paths=[]
    for n in ['1.png','2.png','3.png','4.png','5.png','6.png','BG_Moon.png','BG_FOG.png']:
        p=r/'BG'/n
        if p.exists():paths.append(p)
    for n in ['FG1.png','FG2.png']:
        p=r/'FG'/n
        if p.exists():paths.append(p)
    return alpha_stack(paths,(4,7,14,255))

def abandon():
    r=SRC/'backgrounds'/'AbandonCity'
    ps=list(r.rglob('Background city Seamless.png')) or list(r.rglob('BG City.jpg'))
    if not ps:return None,[]
    out=ImageOps.fit(Image.open(ps[0]).convert('RGB'),SIZE,method=Image.Resampling.LANCZOS).convert('RGBA')
    elems=[]
    for pat in ['Broken Window.png','Damage 1.png','Damage 2.png','plank.png']:
        xs=list(r.rglob(pat))
        if xs:elems.append(xs[0])
    x=120
    for ep in elems:
        try:
            e=Image.open(ep).convert('RGBA');e.thumbnail((420,360),Image.Resampling.LANCZOS);out.alpha_composite(e,(x,180+(x%170)));x+=390
        except Exception as exc:print('ELEMENT_SKIP',ep,exc)
    return out.convert('RGB'),[ps[0],*elems]

def starry():
    r=SRC/'backgrounds'/'StarryNight'
    stars=list(r.rglob('Stars iphone6+.png'));b1=list(r.rglob('buildings1 iphone6+.png'));b2=list(r.rglob('buildings2 iphone6+.png'))
    out=Image.new('RGBA',SIZE,(7,10,27,255));used=[]
    for xs in [stars,b2,b1]:
        if xs:out.alpha_composite(fit_layer(xs[0]));used.append(xs[0])
    return out.convert('RGB'),used

def industrial():
    r=SRC/'backgrounds'/'Industrial';preferred=[]
    for pat in ['*0003_bg.png','*0002*.png','*0001_buildings.png','*0000_foreground.png']:
        xs=list(r.rglob(pat))
        if xs:preferred.append(xs[0])
    if not preferred:return None,[]
    return alpha_stack(preferred,(8,10,14,255))

def streets():
    r=SRC/'backgrounds'/'StreetsOfFight';back=list(r.rglob('Stage Layers/back.png'));fore=list(r.rglob('Stage Layers/fore.png'));paths=back[:1]+fore[:1]
    return alpha_stack(paths,(15,15,18,255)) if paths else (None,[])

def layered_pack_base(name):
    fn={'DarkCity':darkcity,'AbandonCity':abandon,'StarryNight':starry,'Industrial':industrial,'StreetsOfFight':streets}.get(name)
    if fn:
        im,used=fn()
        if im is not None and used:return im,used[0]
    return ORIGINAL(name)

ORIGINAL=b.pack_base
b.pack_base=layered_pack_base
b.main()

report_path=b.OUT/'build-report.json'
r=json.loads(report_path.read_text(encoding='utf-8'))
r['renderer']='prerendered-48f-hd-v4-layered-stages'
r['stagePipeline']='layered-cc0-bg-fog-fg'
report_path.write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf-8')
print('REAL_V4_OK')
