#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageOps, ImageDraw, ImageEnhance, ImageFilter, ImageChops, ImageStat
import json, math, os, re
import build_real_assets as b

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'web'/'assets'
SRC=ROOT/'.cc0-sources'
FW,FH=360,480
COLS=8
STAGE=(1920,1080)

fighters={x['id']:x for x in b.fighters()}

# ---------- image helpers ----------
def rgba(c,a=255): return tuple(c)+(a,)
def clamp(v,a,b): return max(a,min(b,v))
def mask_rect(alpha,box,soft=0):
    m=Image.new('L',alpha.size,0); d=ImageDraw.Draw(m)
    d.rectangle(tuple(map(int,box)),fill=255)
    if soft:m=m.filter(ImageFilter.GaussianBlur(soft))
    return ImageChops.multiply(alpha,m)
def overlay_color(im,box,color,amount=.7,soft=1):
    m=mask_rect(im.getchannel('A'),box,soft).point(lambda x:int(x*amount))
    lay=Image.new('RGBA',im.size,rgba(color));lay.putalpha(m)
    out=im.copy();out.alpha_composite(lay);return out
def recolor(im,lo,hi,strength=.78):
    a=im.getchannel('A'); g=ImageOps.grayscale(im)
    c=ImageOps.colorize(g,lo,hi).convert('RGBA');c.putalpha(a)
    out=Image.blend(im,c,strength);out.putalpha(a);return out
def alpha_bbox(im): return im.getchannel('A').getbbox()
def body_geom(im):
    box=alpha_bbox(im)
    if not box:return None
    l,t,r,bt=box;w=max(1,r-l);h=max(1,bt-t);cx=(l+r)/2
    return l,t,r,bt,w,h,cx

def paste_original_eye(out,orig,g):
    l,t,r,bt,w,h,cx=g
    eye=(l+w*.31,t+h*.075,r-w*.31,t+h*.135)
    m=mask_rect(orig.getchannel('A'),eye,1)
    out.paste(orig,(0,0),m)
    return out

def hood(out,g,primary,eye=True,round_top=True):
    l,t,r,bt,w,h,cx=g
    lay=Image.new('RGBA',out.size,(0,0,0,0));d=ImageDraw.Draw(lay,'RGBA')
    hb=(cx-w*.25,t-h*.018,cx+w*.25,t+h*.205)
    if round_top:d.rounded_rectangle(tuple(map(int,hb)),radius=max(5,int(w*.11)),fill=(8,10,13,242))
    else:d.rectangle(tuple(map(int,hb)),fill=(8,10,13,242))
    # lower mask plate and colored brow trim
    d.polygon([(cx-w*.22,t+h*.11),(cx+w*.22,t+h*.11),(cx+w*.18,t+h*.205),(cx-w*.18,t+h*.205)],fill=rgba(primary,225))
    d.rectangle((cx-w*.19,t+h*.085,cx+w*.19,t+h*.12),fill=(2,3,5,245))
    out.alpha_composite(lay)
    return out

def bands(out,g,color):
    l,t,r,bt,w,h,cx=g
    # These are intersected with the real silhouette so they bend/disappear with poses.
    for y0,y1,a in [(t+h*.40,t+h*.47,.64),(t+h*.72,t+h*.79,.58)]:
        out=overlay_color(out,(l,y0,r,y1),color,a,1)
    return out

def ninja_frame(orig,d,female=False):
    g=body_geom(orig)
    if not g:return orig
    l,t,r,bt,w,h,cx=g;pri=tuple(d['primary']);dark=tuple(d['dark'])
    # Replace the military shirt/cap with a unified dark cloth base while preserving
    # all photographic/high-res shading from the source render.
    out=recolor(orig,(3,4,6),(57,62,70),.91)
    # Long dark sleeves/gloves/leggings are already the base; add a readable tabard.
    torso=(l+w*(.22 if female else .18),t+h*.20,r-w*(.22 if female else .18),t+h*.58)
    out=overlay_color(out,torso,pri,.82,2)
    out=overlay_color(out,(l+w*.16,t+h*.49,r-w*.16,t+h*.61),dark,.88,1)
    # Split front panel avoids a flat T-shirt rectangle.
    panel=Image.new('RGBA',out.size,(0,0,0,0));pd=ImageDraw.Draw(panel,'RGBA')
    pd.polygon([(cx-w*.12,t+h*.23),(cx+w*.12,t+h*.23),(cx+w*.085,t+h*.56),(cx,t+h*.62),(cx-w*.085,t+h*.56)],fill=rgba(tuple(int(x*.72) for x in pri),190))
    out.alpha_composite(panel)
    out=hood(out,g,pri)
    out=paste_original_eye(out,orig,g)
    out=bands(out,g,tuple(min(255,x+45) for x in pri))
    # shoulder guards, anchored to current frame bbox
    lay=Image.new('RGBA',out.size,(0,0,0,0));dd=ImageDraw.Draw(lay,'RGBA')
    y=t+h*.22
    dd.polygon([(l+w*.12,y),(l+w*.31,y-h*.025),(l+w*.35,y+h*.07),(l+w*.15,y+h*.09)],fill=rgba(pri,175))
    dd.polygon([(r-w*.12,y),(r-w*.31,y-h*.025),(r-w*.35,y+h*.07),(r-w*.15,y+h*.09)],fill=rgba(pri,175))
    out.alpha_composite(lay)
    return out

def special_frame(orig,d):
    i=d['id'];g=body_geom(orig)
    if not g:return orig
    l,t,r,bt,w,h,cx=g;pri=tuple(d['primary']);dark=tuple(d['dark'])
    if i in {'cyrax','sektor','smoke','motaro','shao-kahn'}:
        # Dedicated renders already carry the correct silhouette; only cinematic grade.
        out=ImageEnhance.Contrast(orig).enhance(1.10)
        out=ImageEnhance.Color(out).enhance(1.06)
        return out
    if i in {'kitana','jade','mileena'}: return ninja_frame(orig,d,True)
    if i in {'scorpion','reptile','ermac','rain','noob','human-smoke','subzero','classic-subzero'}: return ninja_frame(orig,d,False)
    out=orig.copy()
    if i=='stryker':
        out=recolor(out,(11,14,13),(98,111,86),.48);out=overlay_color(out,(l+w*.18,t+h*.20,r-w*.18,t+h*.58),(38,54,43),.45,2)
    elif i=='jax':
        out=recolor(out,(7,7,9),(75,57,49),.62);out=overlay_color(out,(l+w*.22,t+h*.20,r-w*.22,t+h*.56),(92,25,25),.64,2)
        metal=(185,194,202);out=overlay_color(out,(l,t+h*.20,l+w*.34,t+h*.60),metal,.72,1);out=overlay_color(out,(r-w*.34,t+h*.20,r,t+h*.60),metal,.72,1)
        # dark skull cap hides the source military cap while keeping head proportions
        lay=Image.new('RGBA',out.size,(0,0,0,0));dd=ImageDraw.Draw(lay,'RGBA');dd.ellipse((cx-w*.19,t-h*.01,cx+w*.19,t+h*.13),fill=(15,15,17,235));out.alpha_composite(lay)
    elif i=='nightwolf':
        out=recolor(out,(5,8,9),(50,76,69),.68);out=overlay_color(out,(l+w*.18,t+h*.19,r-w*.18,t+h*.59),(34,107,78),.64,2)
        lay=Image.new('RGBA',out.size,(0,0,0,0));dd=ImageDraw.Draw(lay,'RGBA');dd.rectangle((cx-w*.21,t+h*.07,cx+w*.21,t+h*.105),fill=(170,31,32,235));dd.polygon([(cx+w*.15,t+h*.09),(cx+w*.30,t+h*.32),(cx+w*.20,t+h*.34)],fill=(20,20,23,210));out.alpha_composite(lay)
    elif i=='kano':
        out=recolor(out,(4,5,7),(56,59,63),.78);out=overlay_color(out,(l+w*.18,t+h*.18,r-w*.18,t+h*.58),(28,30,32),.72,2)
        lay=Image.new('RGBA',out.size,(0,0,0,0));dd=ImageDraw.Draw(lay,'RGBA');dd.ellipse((cx+w*.02,t+h*.065,cx+w*.18,t+h*.16),fill=(125,133,141,225));dd.ellipse((cx+w*.09,t+h*.105,cx+w*.125,t+h*.14),fill=(255,28,23,255));out.alpha_composite(lay)
    elif i=='kung-lao':
        out=recolor(out,(4,6,10),(42,52,70),.70);out=overlay_color(out,(l+w*.20,t+h*.19,r-w*.20,t+h*.58),(35,62,106),.56,2)
        lay=Image.new('RGBA',out.size,(0,0,0,0));dd=ImageDraw.Draw(lay,'RGBA');hy=t+h*.035;dd.ellipse((cx-w*.34,hy-h*.035,cx+w*.34,hy+h*.035),fill=(12,13,16,248),outline=(192,195,201,245),width=max(2,int(w*.024)));out.alpha_composite(lay)
    elif i=='kabal':
        out=recolor(out,(4,5,6),(63,58,55),.78);out=overlay_color(out,(l+w*.17,t+h*.18,r-w*.17,t+h*.59),(88,65,43),.56,2);out=hood(out,g,(82,85,89),False,False)
        lay=Image.new('RGBA',out.size,(0,0,0,0));dd=ImageDraw.Draw(lay,'RGBA');dd.ellipse((cx-w*.17,t+h*.10,cx-w*.06,t+h*.16),outline=(169,176,181,240),width=3);dd.ellipse((cx+w*.06,t+h*.10,cx+w*.17,t+h*.16),outline=(169,176,181,240),width=3);dd.line((cx,t+h*.16,cx+w*.23,t+h*.29),fill=(80,84,88,230),width=4);out.alpha_composite(lay)
    elif i=='liukang':
        out=recolor(out,(7,6,6),(85,55,43),.55);out=overlay_color(out,(l+w*.10,t+h*.58,r-w*.10,bt),(20,21,24),.66,2);out=overlay_color(out,(l+w*.16,t+h*.49,r-w*.16,t+h*.60),(154,28,30),.78,1)
        lay=Image.new('RGBA',out.size,(0,0,0,0));dd=ImageDraw.Draw(lay,'RGBA');dd.rectangle((cx-w*.23,t+h*.07,cx+w*.23,t+h*.105),fill=(181,27,28,230));out.alpha_composite(lay)
    elif i=='shang-tsung':
        out=recolor(out,(5,5,7),(72,62,45),.70);out=overlay_color(out,(l+w*.16,t+h*.18,r-w*.16,t+h*.67),(119,86,31),.57,2);out=overlay_color(out,(l+w*.12,t+h*.52,r-w*.12,t+h*.62),(24,25,28),.78,1)
    elif i=='sonya':
        out=recolor(out,(6,8,6),(55,81,49),.55);out=overlay_color(out,(l+w*.18,t+h*.18,r-w*.18,t+h*.57),(52,112,49),.64,2)
    elif i=='sindel':
        out=recolor(out,(6,5,8),(78,55,92),.62);out=overlay_color(out,(l+w*.18,t+h*.18,r-w*.18,t+h*.62),(105,54,137),.66,2)
        lay=Image.new('RGBA',out.size,(0,0,0,0));dd=ImageDraw.Draw(lay,'RGBA');dd.ellipse((cx-w*.25,t-h*.04,cx+w*.34,t+h*.21),fill=(205,207,214,190));out.alpha_composite(lay);out.paste(orig,(0,0),mask_rect(orig.getchannel('A'),(cx-w*.16,t+h*.055,cx+w*.16,t+h*.17),1))
    elif i=='sheeva':
        out=recolor(out,(8,4,4),(104,61,49),.54);out=overlay_color(out,(l+w*.16,t+h*.18,r-w*.16,t+h*.62),(132,45,38),.64,2)
    else:
        out=ImageEnhance.Contrast(out).enhance(1.07)
    return out.filter(ImageFilter.UnsharpMask(radius=.55,percent=108,threshold=2))

# ---------- fighters + clean portraits ----------
def slice_frames(atlas):
    return [atlas.crop(((i%COLS)*FW,(i//COLS)*FH,(i%COLS+1)*FW,(i//COLS+1)*FH)) for i in range(48)]
def pack_frames(frames):
    a=Image.new('RGBA',(COLS*FW,6*FH),(0,0,0,0))
    for i,fr in enumerate(frames):a.alpha_composite(fr,((i%COLS)*FW,(i//COLS)*FH))
    return a

def portrait_from_frames(frames,d):
    # Pick a readable upright fighting pose rather than a back-facing idle cel.
    candidates=[18,19,24,25,31,37,43,47,4,5,0]
    best=None;best_score=-1
    for idx in candidates:
        fr=frames[idx];box=alpha_bbox(fr)
        if not box:continue
        l,t,r,bt=box;area=(r-l)*(bt-t)
        # prefer centered, tall, non-floor poses
        score=area-(abs((l+r)/2-FW/2)*140)
        if score>best_score:best_score=score;best=fr
    if best is None:best=frames[0]
    box=alpha_bbox(best) or (0,0,FW,FH);l,t,r,bt=box;w=r-l;h=bt-t
    # Upper 72% of body, enlarged into a clean arcade portrait.
    crop=best.crop((max(0,l-18),max(0,t-18),min(FW,r+18),min(FH,t+h*.76)))
    pri=tuple(d['primary']);dark=tuple(d['dark'])
    bg=Image.new('RGBA',(512,512),rgba(tuple(int(x*.28) for x in dark)))
    # cinematic diagonal gradient / vignette, no bubble noise
    grad=Image.new('RGBA',(512,512),(0,0,0,0));gd=ImageDraw.Draw(grad,'RGBA')
    for y in range(512):
        p=y/511;gd.line((0,y,512,y),fill=(int(pri[0]*(.24-.12*p)),int(pri[1]*(.24-.12*p)),int(pri[2]*(.24-.12*p)),150))
    gd.polygon([(0,0),(245,0),(0,340)],fill=rgba(pri,42));bg.alpha_composite(grad)
    # rim silhouette behind subject
    sc=min(465/max(1,crop.height),420/max(1,crop.width));nw=max(1,int(crop.width*sc));nh=max(1,int(crop.height*sc));subject=crop.resize((nw,nh),Image.Resampling.LANCZOS)
    x=(512-nw)//2;y=512-nh+24
    a=subject.getchannel('A');rim=a.filter(ImageFilter.MaxFilter(11));rim=ImageChops.subtract(rim,a);rl=Image.new('RGBA',(nw,nh),rgba(tuple(min(255,x+70) for x in pri),180));rl.putalpha(rim);bg.alpha_composite(rl,(x,y));bg.alpha_composite(subject,(x,y))
    # bottom name-safe fade
    fade=Image.new('RGBA',(512,512),(0,0,0,0));fd=ImageDraw.Draw(fade,'RGBA');
    for yy in range(390,512):fd.line((0,yy,512,yy),fill=(0,0,0,int((yy-390)/122*170)))
    bg.alpha_composite(fade)
    return bg

processed=[]
for fid,d in fighters.items():
    p=OUT/'fighters'/fid/'atlas.png'
    if not p.exists():continue
    atlas=Image.open(p).convert('RGBA');frames=slice_frames(atlas)
    new=[special_frame(fr,d) for fr in frames]
    pack_frames(new).save(p,optimize=True,compress_level=6)
    portrait=portrait_from_frames(new,d);portrait.save(OUT/'fighters'/fid/'portrait.png',optimize=True,compress_level=6)
    processed.append(fid)
    print('V6_FIGHTER',fid)

# Rebuild single fast portrait atlas.
fs=b.fighters();cell=160;cols=7;rows=math.ceil(len(fs)/cols)
pa=Image.new('RGB',(cols*cell,rows*cell),(7,8,11))
for idx,d in enumerate(fs):
    p=OUT/'fighters'/d['id']/'portrait.png'
    if not p.exists():continue
    im=ImageOps.fit(Image.open(p).convert('RGB'),(cell,cell),method=Image.Resampling.LANCZOS)
    pa.paste(im,((idx%cols)*cell,(idx//cols)*cell))
pa.save(OUT/'ui'/'portrait-atlas.jpg',quality=91,optimize=True,progressive=True)

# ---------- cinematic stages ----------
def all_images(root):
    if not root.exists():return []
    return [p for p in root.rglob('*') if p.suffix.lower() in {'.png','.jpg','.jpeg','.webp'} and 'preview' not in p.name.lower()]
def image_area(p):
    try:
        with Image.open(p) as im:return im.width*im.height
    except:return 0
def best_image(root):
    ims=all_images(root)
    return max(ims,key=lambda p:(image_area(p),p.stat().st_size),default=None)
def fit(p):return ImageOps.fit(Image.open(p).convert('RGB'),STAGE,method=Image.Resampling.LANCZOS).convert('RGBA')
def donor(kind):
    roots={'sea':SRC/'backgrounds'/'SeaView','cave':SRC/'backgrounds'/'Cave','space':SRC/'backgrounds'/'Space'}
    if kind=='lava':
        p=SRC/'backgrounds'/'lava-1920x1080.png';return fit(p) if p.exists() else None
    p=best_image(roots[kind]);
    if p:print('V6_DONOR',kind,p.relative_to(SRC),image_area(p));return fit(p)
    return None

def stage_old(name):return Image.open(OUT/'stages'/f'{name}.png').convert('RGBA')
urban=stage_old('bank')
sea=donor('sea') or stage_old('waterfront')
cave=donor('cave') or stage_old('kahn-kave')
space=donor('space') or stage_old('lost-portal')
lava=donor('lava') or stage_old('scorpion-lair')

def grade(im,tint,bright=1,contrast=1.08,sat=.92):
    x=ImageEnhance.Brightness(im.convert('RGB')).enhance(bright)
    x=ImageEnhance.Contrast(x).enhance(contrast);x=ImageEnhance.Color(x).enhance(sat).convert('RGBA')
    lay=Image.new('RGBA',x.size,rgba(tint,55));x.alpha_composite(lay);return x

def glow(im,xy,c,r,a=90):
    l=Image.new('RGBA',im.size,(0,0,0,0));d=ImageDraw.Draw(l,'RGBA');x,y=xy;d.ellipse((x-r,y-r,x+r,y+r),fill=rgba(c,a));im.alpha_composite(l.filter(ImageFilter.GaussianBlur(r*.42)))
def floor(im,c=(28,30,34),y0=825):
    l=Image.new('RGBA',im.size,(0,0,0,0));d=ImageDraw.Draw(l,'RGBA')
    for y in range(y0,1080):
        p=(y-y0)/(1080-y0);d.line((0,y,1920,y),fill=rgba(c,int(48+155*p)),width=1)
    d.line((0,y0,1920,y0),fill=(190,175,135,110),width=3);im.alpha_composite(l)
def fog(im,c=(180,190,195),strength=32):
    l=Image.new('RGBA',im.size,(0,0,0,0));d=ImageDraw.Draw(l,'RGBA')
    for i in range(12):d.ellipse((i*190-280,610+(i%3)*45,i*190+420,1000+(i%2)*30),fill=rgba(c,strength))
    im.alpha_composite(l.filter(ImageFilter.GaussianBlur(28)))

def stage_variant(name):
    if name=='waterfront':
        im=grade(sea,(15,48,67),.78,1.13,.88);floor(im,(12,27,34),850)
        for x in (240,760,1320,1710):glow(im,(x,650),(76,176,219),65,55)
    elif name in {'kahn-kave','pit3','scorpion-lair','soul'}:
        base=lava if name=='scorpion-lair' else cave
        tint={'kahn-kave':(70,22,18),'pit3':(30,28,34),'scorpion-lair':(105,22,8),'soul':(10,74,42)}[name]
        im=grade(base,tint,.72 if name!='soul' else .67,1.16,.88);floor(im,(18,16,18),840)
        if name=='scorpion-lair':
            for x in range(120,1850,240):glow(im,(x,875),(255,75,13),68,82)
        if name=='soul':
            for x in (300,650,1040,1420,1700):glow(im,(x,600),(44,235,116),74,80)
        if name=='pit3':
            d=ImageDraw.Draw(im,'RGBA');d.rectangle((0,800,1920,834),fill=(46,42,45,225));
            for x in range(15,1920,78):d.polygon([(x,1055),(x+24,920),(x+48,1055)],fill=(147,151,157,190))
    elif name=='lost-portal':
        im=grade(space,(20,35,80),.66,1.18,.95);floor(im,(10,15,28),850);glow(im,(960,530),(78,131,255),260,65)
        d=ImageDraw.Draw(im,'RGBA')
        for r,a in [(205,210),(154,180),(106,145)]:d.ellipse((960-r,530-r,960+r,530+r),outline=(112,163,255,a),width=12)
    else:
        # Urban donor is the detailed 2467px abandoned-city scene. Each stage gets
        # its own lighting/foreground grammar rather than a black placeholder.
        presets={
          'bank':((54,42,25),.88),'balcony':((44,37,55),.74),'graveyard':((23,48,38),.60),'jade-desert':((111,65,29),.90),
          'bell':((44,43,55),.64),'noob-dorfen':((12,15,24),.48),'rooftop':((22,33,49),.60),'street':((26,34,52),.66),
          'subway':((24,41,51),.58),'temple':((69,28,34),.60)}
        tint,br=presets.get(name,((32,34,38),.68));im=grade(urban,tint,br,1.14,.86);floor(im,(18,20,23),835);d=ImageDraw.Draw(im,'RGBA')
        if name=='street':
            for x,c in [(260,(236,52,68)),(790,(42,158,229)),(1390,(230,150,44)),(1710,(76,210,159))]:glow(im,(x,540),c,95,72)
            for x,c in [(210,(228,41,61)),(735,(42,135,220)),(1340,(211,135,38))]:d.rounded_rectangle((x,360,x+160,430),10,fill=rgba(tuple(int(v*.35) for v in c),220),outline=rgba(c,185),width=5)
        elif name=='subway':
            d.rectangle((0,0,1920,130),fill=(20,24,28,235));
            for x in range(150,1900,390):d.rounded_rectangle((x,48,x+210,83),8,fill=(220,228,221,185));glow(im,(x+105,70),(215,230,220),78,45)
            d.line((0,940,1920,940),fill=(132,139,145,210),width=12);d.line((0,1010,1920,1010),fill=(132,139,145,190),width=9)
        elif name=='rooftop':
            d.rectangle((0,800,1920,845),fill=(31,34,40,225));d.line((0,796,1920,796),fill=(155,159,167,125),width=4)
        elif name=='graveyard':
            fog(im,(175,190,185),38)
            for x in range(80,1900,165):
                hh=60+(x//165%3)*28;d.rounded_rectangle((x,785-hh,x+55,785),10,fill=(19,25,23,210));d.rectangle((x+20,785-hh-28,x+35,785-hh+12),fill=(19,25,23,210))
        elif name=='temple':
            fog(im,(125,109,111),22)
            # Torii frame at the edges, not in the combat plane.
            d.rectangle((95,225,145,820),fill=(86,25,23,225));d.rectangle((1775,225,1825,820),fill=(86,25,23,225));d.rectangle((45,205,1875,248),fill=(104,31,27,225));d.rectangle((85,170,1835,205),fill=(61,20,20,225))
            for x in (240,560,960,1360,1680):glow(im,(x,615),(255,95,34),60,88);d.ellipse((x-14,584,x+14,642),fill=(255,128,55,215))
        elif name=='bank':
            d.line((0,790,1920,790),fill=(205,182,122,130),width=5)
        elif name=='bell':
            glow(im,(1540,260),(225,215,170),190,55);d.ellipse((1455,175,1625,345),outline=(228,214,170,175),width=7)
        elif name=='jade-desert':
            haze=Image.new('RGBA',STAGE,(190,103,45,42));im.alpha_composite(haze);d.polygon([(0,870),(420,785),(820,870)],fill=(126,75,39,100));d.polygon([(650,880),(1250,750),(1920,900)],fill=(135,80,41,105))
        elif name=='noob-dorfen':fog(im,(60,70,82),28)
    im.convert('RGB').save(OUT/'stages'/f'{name}.png',optimize=True,compress_level=6)
    print('V6_STAGE',name)

for name in ['balcony','bank','bell','graveyard','jade-desert','kahn-kave','lost-portal','noob-dorfen','pit3','rooftop','scorpion-lair','soul','street','subway','temple','waterfront']:
    stage_variant(name)

# Title follows the upgraded neon street, and is intentionally small/fast.
title=ImageOps.fit(Image.open(OUT/'stages'/'street.png').convert('RGB'),(1280,720),method=Image.Resampling.LANCZOS)
title=ImageEnhance.Brightness(title).enhance(.56);title=ImageEnhance.Contrast(title).enhance(1.14)
title.save(OUT/'ui'/'title-bg.jpg',quality=88,optimize=True,progressive=True)

report=OUT/'build-report.json';r=json.loads(report.read_text(encoding='utf-8'))
r['renderer']='prerendered-48f-hd-v6-costumed-cinematic'
r['fighterPipeline']='v6-dark-cloth-costume+anchored-hires+clean-portraits'
r['stagePipeline']='v6-multidonor-cinematic-16'
r['v6ProcessedFighters']=processed
r['v6StageDonors']=['AbandonCity','SeaView','Cave','Space','lava-1920x1080']
report.write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf-8')
print('V6_ART_OK',len(processed))
