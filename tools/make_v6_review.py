#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageDraw, ImageOps

ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'web'/'assets'
R=ROOT/'v6-review'
R.mkdir(parents=True,exist_ok=True)

# All 16 stages in one readable sheet.
stages=sorted((A/'stages').glob('*.png'))
cell=(480,300);cols=4;rows=(len(stages)+cols-1)//cols
sheet=Image.new('RGB',(cell[0]*cols,cell[1]*rows),(4,5,7))
for i,p in enumerate(stages):
    im=ImageOps.fit(Image.open(p).convert('RGB'),(480,270),method=Image.Resampling.LANCZOS)
    x=(i%cols)*cell[0];y=(i//cols)*cell[1]
    sheet.paste(im,(x,y));d=ImageDraw.Draw(sheet);d.rectangle((x,y+270,x+480,y+300),fill=(5,6,8));d.text((x+10,y+280),p.stem,fill=(235,235,235))
sheet.save(R/'stages-contact.jpg',quality=90,optimize=True)

# 48-frame fighter contacts catch bad anchors, floating costume parts and pose mismatches.
for fid in ['scorpion','kitana','cyrax','shao-kahn']:
    p=A/'fighters'/fid/'atlas.png'
    if not p.exists():continue
    atlas=Image.open(p).convert('RGBA');FW,FH=360,480
    tw,th=180,240;cols=6;rows=8
    out=Image.new('RGB',(tw*cols,th*rows),(22,23,26))
    for i in range(48):
        sx=(i%8)*FW;sy=(i//8)*FH
        fr=atlas.crop((sx,sy,sx+FW,sy+FH))
        fr.thumbnail((tw,th),Image.Resampling.LANCZOS)
        slot=Image.new('RGBA',(tw,th),(22,23,26,255));slot.alpha_composite(fr,((tw-fr.width)//2,th-fr.height))
        x=(i%cols)*tw;y=(i//cols)*th
        out.paste(slot.convert('RGB'),(x,y));ImageDraw.Draw(out).text((x+5,y+5),str(i),fill='white')
    out.save(R/f'{fid}-48f.jpg',quality=90,optimize=True)

# Copy small UI review assets.
for n in ['portrait-atlas.jpg','title-bg.jpg']:
    p=A/'ui'/n
    if p.exists():Image.open(p).save(R/n,quality=92)
print('V6_REVIEW_OK',len(stages))
