#!/usr/bin/env python3
from pathlib import Path
from PIL import Image,ImageOps,ImageDraw
import math,sys
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'.cc0-sources'
OUT=ROOT/'cc0-previews'
OUT.mkdir(exist_ok=True)

def safe(s):
    return ''.join(c if c.isalnum() else '_' for c in s)[-90:]

def thumb(im,size=(150,190)):
    im=im.convert('RGBA')
    alpha=im.getchannel('A')
    bbox=alpha.getbbox()
    if bbox: im=im.crop(bbox)
    bg=Image.new('RGBA',size,(24,25,29,255))
    im.thumbnail((size[0]-10,size[1]-10),Image.Resampling.LANCZOS)
    x=(size[0]-im.width)//2;y=size[1]-im.height-5
    bg.alpha_composite(im,(x,y))
    return bg.convert('RGB')

def animated_sheet(path):
    im=Image.open(path);n=getattr(im,'n_frames',1)
    if n<2:return None
    count=min(36,n);idx=[]
    for i in range(count):idx.append(round(i*(n-1)/max(1,count-1)))
    cell=(150,190);cols=9;rows=math.ceil(count/cols)
    canvas=Image.new('RGB',(cols*cell[0],rows*cell[1]+32),(12,13,16));d=ImageDraw.Draw(canvas)
    d.text((8,8),f'{path.name} | frames={n}',fill=(235,225,200))
    for j,k in enumerate(idx):
        im.seek(k);fr=thumb(im.copy(),cell)
        canvas.paste(fr,((j%cols)*cell[0],32+(j//cols)*cell[1]))
    return canvas

def static_sheet(path):
    im=Image.open(path).convert('RGB');w,h=im.size
    if w*h<400000:return None
    maxw=1500;scale=min(1,maxw/w)
    im=im.resize((max(1,int(w*scale)),max(1,int(h*scale))),Image.Resampling.LANCZOS)
    canvas=Image.new('RGB',(im.width,im.height+32),(12,13,16));ImageDraw.Draw(canvas).text((8,8),f'{path.name} | {w}x{h}',fill=(235,225,200));canvas.paste(im,(0,32));return canvas

made=0
for p in sorted(SRC.rglob('*')):
    if not p.is_file() or p.suffix.lower() not in {'.gif','.png','.jpg','.jpeg','.webp'}:continue
    try:
        im=Image.open(p);n=getattr(im,'n_frames',1);im.close()
        sheet=animated_sheet(p) if n>1 else static_sheet(p)
        if sheet is None:continue
        out=OUT/(safe(str(p.relative_to(SRC)))+'.jpg');sheet.save(out,quality=86,optimize=True);made+=1
        print(f'PREVIEW {out.name}')
        if made>=80:break
    except Exception as e:print('SKIP',p,e,file=sys.stderr)
print('CONTACT_SHEETS',made)
if made<3:raise SystemExit('Not enough visual source previews')
