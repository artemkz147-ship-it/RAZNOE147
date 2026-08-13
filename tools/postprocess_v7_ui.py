#!/usr/bin/env python3
from pathlib import Path
from PIL import Image,ImageOps,ImageDraw,ImageEnhance,ImageFilter,ImageChops
import json,math
import build_real_assets as b

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'web'/'assets';FW,FH=360,480;COLS=8

def bbox(im):return im.getchannel('A').getbbox()
def frames(atlas):return [atlas.crop(((i%COLS)*FW,(i//COLS)*FH,(i%COLS+1)*FW,(i//COLS+1)*FH)) for i in range(48)]

def pick(frames):
    # Prefer readable upright combat frames; reject floor-heavy/extreme poses.
    cand=[4,5,17,18,20,23,24,26,37,43,0,6]
    best=None;score=-1
    for i in cand:
        fr=frames[i];bb=bbox(fr)
        if not bb:continue
        l,t,r,bt=bb;w=r-l;h=bt-t
        s=w*h-abs((l+r)/2-FW/2)*160-(FH-bt)*35
        if h>w*1.15:s+=25000
        if s>score:score=s;best=fr
    return best or frames[0]

def portrait(fid,d):
    atlas=Image.open(OUT/'fighters'/fid/'atlas.png').convert('RGBA');fr=pick(frames(atlas));bb=bbox(fr) or (0,0,FW,FH)
    l,t,r,bt=bb;h=bt-t
    crop=fr.crop((max(0,l-18),max(0,t-15),min(FW,r+18),min(FH,t+h*.80)))
    pri=tuple(d['primary']);dark=tuple(d['dark'])
    bg=Image.new('RGBA',(512,512),dark+(255,));gd=ImageDraw.Draw(bg,'RGBA')
    # restrained arcade gradient: no bubbles, halos or fake body parts
    for y in range(512):
        p=y/511;gd.line((0,y,512,y),fill=(int(dark[0]*(1-p)+6*p),int(dark[1]*(1-p)+7*p),int(dark[2]*(1-p)+10*p),255))
    gd.polygon([(0,0),(300,0),(0,410)],fill=pri+(34,))
    sc=min(470/max(1,crop.height),430/max(1,crop.width));nw=max(1,int(crop.width*sc));nh=max(1,int(crop.height*sc))
    subject=crop.resize((nw,nh),Image.Resampling.LANCZOS)
    x=(512-nw)//2;y=512-nh+22
    # thin colored edge only, preserving the source render
    a=subject.getchannel('A');edge=ImageChops.subtract(a.filter(ImageFilter.MaxFilter(5)),a)
    rim=Image.new('RGBA',(nw,nh),tuple(min(255,c+55) for c in pri)+(160,));rim.putalpha(edge);bg.alpha_composite(rim,(x,y));bg.alpha_composite(subject,(x,y))
    shade=Image.new('RGBA',(512,130),(0,0,0,0));sd=ImageDraw.Draw(shade,'RGBA')
    for yy in range(130):sd.line((0,yy,512,yy),fill=(0,0,0,int(180*yy/129)))
    bg.alpha_composite(shade,(0,382))
    bg.save(OUT/'fighters'/fid/'portrait.png',optimize=True,compress_level=6)
    return bg

fs=b.fighters();images=[]
for d in fs:
    p=OUT/'fighters'/d['id']/'atlas.png'
    if p.exists():images.append((d,portrait(d['id'],d)))

cell=160;cols=7;rows=math.ceil(len(fs)/cols);pa=Image.new('RGB',(cols*cell,rows*cell),(7,8,11))
for idx,d in enumerate(fs):
    p=OUT/'fighters'/d['id']/'portrait.png'
    if not p.exists():continue
    im=ImageOps.fit(Image.open(p).convert('RGB'),(cell,cell),method=Image.Resampling.LANCZOS);pa.paste(im,((idx%cols)*cell,(idx//cols)*cell))
pa.save(OUT/'ui'/'portrait-atlas.jpg',quality=92,optimize=True,progressive=True)

report=OUT/'build-report.json';r=json.loads(report.read_text(encoding='utf-8'))
r['renderer']='prerendered-48f-hd-v7-diverse-roster-cinematic'
r['fighterPipeline']='v7-diverse-sources+stable-anchor+organic-ninja-cloth+clean-portraits'
r['stagePipeline']='v6-multidonor-cinematic-16'
r['v7Portraits']='clean-action-frame-crop'
r['v7RosterSourceStrategy']='hires-only-for-palette-ninjas; independent prerender source for remaining fighters'
report.write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf-8')
print('V7_UI_OK',len(images))
