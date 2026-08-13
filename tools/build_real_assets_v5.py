#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageOps, ImageDraw, ImageEnhance, ImageFilter, ImageChops
import json, math
import build_real_assets as b

SRC=b.SRC
OUT=b.OUT
SIZE=(b.STAGE_W,b.STAGE_H)
ORIG_SOURCE=b.source_for
ORIG_RAW=b.raw_frames

MALE=SRC/'v5'/'stonewall-realistic.gif'
FEMALE=SRC/'v5'/'erika-realistic.gif'

# ---------------------------------------------------------------------------
# Fighter sources: replace the old low-resolution generic bodies with two
# compatible high-resolution Universal Prototype 2 masters. Specialist bodies
# (cyborgs, Motaro, Shao Kahn) keep dedicated source sets.
# ---------------------------------------------------------------------------
def source_for_v5(d):
    i=d['id']; st=d['style']
    if i in {'cyrax','sektor','smoke'} or st=='cyborg':
        return ORIG_SOURCE(d)
    if i=='motaro' or st=='motaro':
        return ORIG_SOURCE(d)
    if i=='shao-kahn' or st=='shaokahn':
        return ORIG_SOURCE(d)
    if i in {'kitana','jade','mileena','sonya','sindel','sheeva'} or st in {'femaleNinja','sheeva'}:
        if FEMALE.exists(): return FEMALE
    if MALE.exists(): return MALE
    return ORIG_SOURCE(d)

# Normalize a complete animation with ONE scale and one bottom-center anchor.
# This removes the previous frame-by-frame size pumping / floating costume bug.
seq_cache={}
def q(vals,p):
    vals=sorted(vals)
    return vals[min(len(vals)-1,max(0,round((len(vals)-1)*p)))] if vals else 1

def normalize_sequence(frames):
    boxes=[]
    for fr in frames:
        box=fr.getchannel('A').getbbox()
        if box:
            boxes.append((box[2]-box[0],box[3]-box[1]))
    if not boxes:
        return [Image.new('RGBA',(b.FW,b.FH),(0,0,0,0)) for _ in frames]
    widths=[x[0] for x in boxes]; heights=[x[1] for x in boxes]
    # 95th percentile keeps the scale stable while allowing rare extreme poses.
    scale=min(326/max(1,q(widths,.95)),438/max(1,q(heights,.95)))
    scale=min(scale,348/max(1,max(widths)),454/max(1,max(heights)))
    scale=max(.12,scale)
    out=[]
    for fr in frames:
        fr=b.bg_to_alpha(fr)
        box=fr.getchannel('A').getbbox()
        if not box:
            out.append(Image.new('RGBA',(b.FW,b.FH),(0,0,0,0)));continue
        crop=fr.crop(box)
        nw=max(1,round(crop.width*scale)); nh=max(1,round(crop.height*scale))
        crop=crop.resize((nw,nh),Image.Resampling.LANCZOS)
        if scale>1.05:
            crop=crop.filter(ImageFilter.UnsharpMask(radius=.75,percent=115,threshold=2))
        dst=Image.new('RGBA',(b.FW,b.FH),(0,0,0,0))
        dst.alpha_composite(crop,((b.FW-nw)//2,b.FH-nh-9))
        out.append(dst)
    return out

def raw_frames_v5(path):
    key='v5:'+str(path)
    if key in seq_cache:return [x.copy() for x in seq_cache[key]]
    frames=ORIG_RAW(path)
    frames=normalize_sequence(frames)
    seq_cache[key]=[x.copy() for x in frames]
    return frames

def identity_normalize(fr): return fr.copy()

# ---------------------------------------------------------------------------
# Silhouette-aware costume treatment. No fixed rectangular helmet/vest stuck
# over a moving body: every color region is intersected with the actual alpha
# silhouette of the current frame.
# ---------------------------------------------------------------------------
def region_mask(im,box,soft=0):
    alpha=im.getchannel('A')
    m=Image.new('L',im.size,0); d=ImageDraw.Draw(m)
    d.rectangle(tuple(map(int,box)),fill=255)
    if soft:m=m.filter(ImageFilter.GaussianBlur(soft))
    return ImageChops.multiply(alpha,m)

def tint_region(im,box,color,amount=.7,soft=2):
    mask=region_mask(im,box,soft).point(lambda x:int(x*amount))
    layer=Image.new('RGBA',im.size,color+(255,)); layer.putalpha(mask)
    out=im.copy(); out.alpha_composite(layer); return out

def subtle_base(im,d):
    # Preserve photographic/high-res skin and shading. Dedicated cyborg sources
    # can take a stronger palette tint; humans only get a small grade.
    if d['style']=='cyborg' or d['id'] in {'cyrax','sektor','smoke'}:
        gray=ImageOps.grayscale(im); col=ImageOps.colorize(gray,d['dark'],tuple(min(255,x+45) for x in d['primary'])).convert('RGBA'); col.putalpha(im.getchannel('A'))
        return Image.blend(im,col,.30)
    x=ImageEnhance.Contrast(im).enhance(1.06)
    x=ImageEnhance.Color(x).enhance(.96)
    return x

def decorate_v5(im,d):
    a=im.getchannel('A'); box=a.getbbox()
    if not box:return im
    l,t,r,bt=box; w=max(1,r-l); h=max(1,bt-t); cx=(l+r)/2
    pri=d['primary']; dark=d['dark']; i=d['id']; st=d['style']
    out=im.copy()
    # Reusable body-relative regions.
    torso=(l+w*.20,t+h*.20,r-w*.20,t+h*.60)
    waist=(l+w*.17,t+h*.49,r-w*.17,t+h*.62)
    head=(l+w*.28,t+h*.01,r-w*.28,t+h*.22)
    legs=(l+w*.18,t+h*.57,r-w*.18,bt-h*.03)

    if st in {'ninja','subzero','femaleNinja'} or i in {'scorpion','reptile','ermac','rain','noob','human-smoke','subzero','classic-subzero','kitana','jade','mileena'}:
        out=tint_region(out,torso,pri,.56,3)
        out=tint_region(out,waist,dark,.72,1)
        out=tint_region(out,legs,dark,.26,4)
        out=tint_region(out,head,dark,.66,2)
        # Lower-face mask is still clipped to the head silhouette, so it follows
        # crouches/jumps instead of floating at a hard-coded screen coordinate.
        maskbox=(l+w*.31,t+h*.115,r-w*.31,t+h*.205)
        out=tint_region(out,maskbox,pri,.76,1)
        if i in {'subzero','classic-subzero'}:
            out=tint_region(out,(l+w*.35,t+h*.25,r-w*.35,t+h*.57),(120,210,245),.24,2)
        if i=='noob':
            shade=Image.new('RGBA',out.size,(2,2,5,115));shade.putalpha(out.getchannel('A').point(lambda x:int(x*.45)));out.alpha_composite(shade)
    elif i=='jax' or st=='jax':
        # Metallic arms, clipped to outer upper-body silhouette.
        out=tint_region(out,(l,t+h*.18,l+w*.33,t+h*.58),(184,190,197),.55,2)
        out=tint_region(out,(r-w*.33,t+h*.18,r,t+h*.58),(184,190,197),.55,2)
        out=tint_region(out,torso,(112,36,32),.23,4)
    elif i=='nightwolf' or st=='nightwolf':
        out=tint_region(out,torso,(42,91,72),.34,4)
        out=tint_region(out,(l+w*.28,t+h*.055,r-w*.28,t+h*.105),(180,35,34),.70,1)
    elif i=='kano' or st=='kano':
        out=tint_region(out,torso,(38,40,43),.38,4)
        out=tint_region(out,(cx+w*.02,t+h*.055,cx+w*.17,t+h*.14),(185,190,196),.62,1)
        dr=ImageDraw.Draw(out,'RGBA');dr.ellipse((cx+w*.075,t+h*.085,cx+w*.105,t+h*.115),fill=(255,36,31,245))
    elif i=='stryker' or st=='stryker':
        out=tint_region(out,torso,(41,62,88),.40,4)
        out=tint_region(out,(l+w*.23,t+h*.03,r-w*.23,t+h*.11),(31,38,48),.60,1)
    elif i=='kung-lao' or st=='kunglao':
        out=tint_region(out,torso,(30,45,70),.38,4)
        # Iconic hat, anchored to the current top-center body bbox.
        dr=ImageDraw.Draw(out,'RGBA');hy=t+h*.035
        dr.ellipse((cx-w*.28,hy-h*.025,cx+w*.28,hy+h*.035),fill=(18,20,24,235),outline=(190,194,199,235),width=max(2,int(w*.025)))
    elif i=='liukang' or st=='liukang':
        out=tint_region(out,legs,(24,25,29),.42,4)
        out=tint_region(out,waist,(161,35,31),.56,2)
    elif i=='kabal' or st=='kabal':
        out=tint_region(out,torso,(58,45,36),.42,3)
        out=tint_region(out,head,(42,45,48),.68,1)
    elif i=='shang-tsung' or st=='shang':
        out=tint_region(out,torso,(113,83,32),.38,4)
        out=tint_region(out,waist,(31,31,34),.50,2)
    elif i=='sonya' or st=='sonya':
        out=tint_region(out,torso,(55,97,46),.38,4)
        out=tint_region(out,legs,(32,38,31),.25,4)
    elif i=='sindel' or st=='sindel':
        out=tint_region(out,torso,(94,53,120),.38,4)
        out=tint_region(out,(l+w*.25,t,r-w*.25,t+h*.18),(220,219,225),.42,2)
    elif i=='sheeva' or st=='sheeva':
        out=tint_region(out,torso,(116,45,38),.33,4)
        # Extra arms behind the body, sized/anchored from the current silhouette.
        under=Image.new('RGBA',out.size,(0,0,0,0));dr=ImageDraw.Draw(under,'RGBA');skin=(151,91,70,220)
        y=t+h*.37
        dr.line((cx-w*.10,y,l-w*.16,y+h*.10,l-w*.21,y+h*.03),fill=skin,width=max(9,int(w*.08)),joint='curve')
        dr.line((cx+w*.10,y,r+w*.16,y+h*.10,r+w*.21,y+h*.03),fill=skin,width=max(9,int(w*.08)),joint='curve')
        under.alpha_composite(out);out=under
    elif i in {'cyrax','sektor','smoke'} or st=='cyborg':
        out=tint_region(out,torso,pri,.42,3)
        out=tint_region(out,head,(45,49,54),.54,1)

    # Keep alpha clean after overlays and apply a tiny edge sharpen only once.
    return out.filter(ImageFilter.UnsharpMask(radius=.55,percent=108,threshold=2))

b.source_for=source_for_v5
b.raw_frames=raw_frames_v5
b.normalize=identity_normalize
b.tint_sprite=subtle_base
b.decorate=decorate_v5

# ---------------------------------------------------------------------------
# Layered stage renderer. Use the actual CC0 scene art as the dominant visual;
# procedural geometry is now limited to floor/readability and subtle stage FX.
# ---------------------------------------------------------------------------
def fit_layer(path):
    return ImageOps.fit(Image.open(path).convert('RGBA'),SIZE,method=Image.Resampling.LANCZOS)

def alpha_stack(paths,base=(6,8,12,255)):
    out=Image.new('RGBA',SIZE,base);used=[]
    for p in paths:
        p=Path(p)
        if not p.exists():continue
        try:out.alpha_composite(fit_layer(p));used.append(p)
        except Exception as e:print('LAYER_SKIP',p,e)
    return out.convert('RGB'),used

def darkcity():
    r=SRC/'backgrounds'/'DarkCity'; paths=[]
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
    # Keep props near the edges so the combat plane stays readable.
    placements=[(20,350),(1510,390),(80,650),(1580,650)]
    for ep,pos in zip(elems,placements):
        try:
            e=Image.open(ep).convert('RGBA');e.thumbnail((360,320),Image.Resampling.LANCZOS);out.alpha_composite(e,pos)
        except Exception as exc:print('ELEMENT_SKIP',ep,exc)
    return out.convert('RGB'),[ps[0],*elems]

def starry():
    r=SRC/'backgrounds'/'StarryNight';stars=list(r.rglob('Stars iphone6+.png'));b1=list(r.rglob('buildings1 iphone6+.png'));b2=list(r.rglob('buildings2 iphone6+.png'))
    out=Image.new('RGBA',SIZE,(7,10,27,255));used=[]
    for xs in [stars,b2,b1]:
        if xs:out.alpha_composite(fit_layer(xs[0]));used.append(xs[0])
    return out.convert('RGB'),used

def industrial():
    r=SRC/'backgrounds'/'Industrial';preferred=[]
    for pat in ['*0003_bg.png','*0002*.png','*0001_buildings.png','*0000_foreground.png']:
        xs=list(r.rglob(pat))
        if xs:preferred.append(xs[0])
    return alpha_stack(preferred,(8,10,14,255)) if preferred else (None,[])

def streets():
    r=SRC/'backgrounds'/'StreetsOfFight';back=list(r.rglob('Stage Layers/back.png'));fore=list(r.rglob('Stage Layers/fore.png'));paths=back[:1]+fore[:1]
    return alpha_stack(paths,(15,15,18,255)) if paths else (None,[])

def pack_base_v5(name):
    fn={'DarkCity':darkcity,'AbandonCity':abandon,'StarryNight':starry,'Industrial':industrial,'StreetsOfFight':streets}.get(name)
    if fn:
        im,used=fn()
        if im is not None and used:return im,used[0]
    return b.pack_base(name)

def add_glow(base,xy,color,radius,alpha=110):
    layer=Image.new('RGBA',base.size,(0,0,0,0));d=ImageDraw.Draw(layer,'RGBA');x,y=xy
    d.ellipse((x-radius,y-radius,x+radius,y+radius),fill=color+(alpha,));layer=layer.filter(ImageFilter.GaussianBlur(radius*.48));base.alpha_composite(layer)

def grade(base,s,amount=.12):
    x=base.convert('RGB')
    x=Image.blend(x,Image.new('RGB',x.size,s['sky1']),amount)
    x=ImageEnhance.Contrast(x).enhance(1.12)
    x=ImageEnhance.Color(x).enhance(.94)
    return x.convert('RGBA')

def floor_depth(base,accent):
    # Transparent 290px gradient: keeps the source scene visible instead of
    # covering it with a single flat rectangle.
    ov=Image.new('RGBA',base.size,(0,0,0,0));d=ImageDraw.Draw(ov,'RGBA')
    y0=790
    for y in range(y0,b.STAGE_H):
        p=(y-y0)/max(1,b.STAGE_H-y0);a=int(45+150*p)
        d.line((0,y,b.STAGE_W,y),fill=(5,6,8,a),width=1)
    d.line((0,y0,b.STAGE_W,y0),fill=accent+(118,),width=3)
    base.alpha_composite(ov)

def build_stage_v5(s,idx):
    k=s['kind']
    packmap={'subway':'Industrial','street':'DarkCity','roof':'DarkCity','bank':'AbandonCity','soul':'DarkCity','tower':'DarkCity','temple':'StarryNight','grave':'AbandonCity','water':'DarkCity','portal':'Industrial','desert':'AbandonCity','cave':'Industrial','hell':'Industrial','balcony':'AbandonCity','noob':'DarkCity','pit':'Industrial'}
    pack=packmap.get(k,'DarkCity');res=pack_base_v5(pack)
    if res is None:base=Image.new('RGB',SIZE,s['sky0']);src='generated-fallback'
    else:base,p=res;src=str(Path(p).relative_to(SRC))
    amt=.10 if res else .42
    if k in {'hell','desert'}:amt=.18
    base=grade(base,s,amt)
    d=ImageDraw.Draw(base,'RGBA');ac=s['accent']

    # Atmospheric stage identity; no giant flat placeholder structures.
    if k=='subway':
        d.line((0,905,b.STAGE_W,905),fill=(188,160,78,190),width=8)
        d.line((0,970,b.STAGE_W,970),fill=(118,123,128,210),width=12)
        d.line((0,1035,b.STAGE_W,1035),fill=(118,123,128,190),width=10)
    elif k=='street':
        for x,c in [(260,(229,58,72)),(1030,(38,157,214)),(1570,(226,155,45))]:add_glow(base,(x,560),c,95,72)
    elif k=='roof':
        d.rectangle((0,810,b.STAGE_W,842),fill=(29,31,37,205));d.line((0,807,b.STAGE_W,807),fill=(142,146,154,115),width=4)
    elif k=='bank':
        warm=Image.new('RGBA',SIZE,(110,78,34,24));base.alpha_composite(warm)
        for x in (80,1740):
            d.rounded_rectangle((x,275,x+100,820),18,fill=(111,96,72,130),outline=(201,180,128,145),width=6)
    elif k=='soul':
        green=Image.new('RGBA',SIZE,(24,100,61,24));base.alpha_composite(green)
        for x in (360,760,1160,1560):add_glow(base,(x,555),(57,230,124),70,75)
    elif k=='tower':
        add_glow(base,(1500,270),(226,202,145),155,65)
        d.ellipse((1430,200,1570,340),outline=(226,202,145,145),width=8)
    elif k=='temple':
        # The layered Japanese silhouette stays visible; lanterns supply depth.
        for x in (270,650,1030,1410,1770):
            add_glow(base,(x,600),(255,94,30),58,88);d.ellipse((x-14,572,x+14,628),fill=(255,126,50,205))
    elif k=='grave':
        fog=Image.new('RGBA',SIZE,(0,0,0,0));fd=ImageDraw.Draw(fog,'RGBA')
        for j in range(12):fd.ellipse((j*190-170,700+(j%3)*28,j*190+360,940+(j%3)*34),fill=(180,194,198,19))
        base.alpha_composite(fog.filter(ImageFilter.GaussianBlur(18)))
    elif k=='water':
        water=Image.new('RGBA',SIZE,(0,0,0,0));wd=ImageDraw.Draw(water,'RGBA')
        for y in range(735,950):wd.line((0,y,b.STAGE_W,y),fill=(25,105,132,45+int((y-735)*.35)),width=1)
        for y in range(755,930,38):wd.line((0,y,b.STAGE_W,y),fill=(114,211,225,45),width=4)
        base.alpha_composite(water)
    elif k=='portal':
        for rad,a in [(230,45),(180,70),(125,95)]:
            d.ellipse((960-rad,505-rad,960+rad,505+rad),outline=(76,135,255,a),width=12)
        add_glow(base,(960,505),(90,135,255),185,60)
    elif k=='desert':
        base.alpha_composite(Image.new('RGBA',SIZE,(216,117,52,38)))
    elif k=='cave':
        base=ImageEnhance.Brightness(base.convert('RGB')).enhance(.72).convert('RGBA')
    elif k=='hell':
        base.alpha_composite(Image.new('RGBA',SIZE,(137,29,7,42)))
        for x in range(100,1900,260):add_glow(base,(x,905),(255,70,12),70,75)
    elif k=='balcony':
        d.rectangle((0,820,b.STAGE_W,844),fill=(66,61,72,150));d.line((0,816,b.STAGE_W,816),fill=(177,165,190,90),width=4)
    elif k=='noob':
        base=ImageEnhance.Brightness(base.convert('RGB')).enhance(.48).convert('RGBA')
    elif k=='pit':
        d.rectangle((0,806,b.STAGE_W,838),fill=(68,48,47,190));d.line((0,802,b.STAGE_W,802),fill=(172,139,119,115),width=4)
        # Spikes remain below the playable plane, not giant foreground circles.
        for x in range(40,b.STAGE_W,95):d.polygon([(x,1040),(x+28,915),(x+56,1040)],fill=(132,136,141,145))

    floor_depth(base,ac)
    out=OUT/'stages';out.mkdir(parents=True,exist_ok=True)
    base.convert('RGB').save(out/(s['id']+'.png'),optimize=True,compress_level=6)
    return {'id':s['id'],'source':src,'pack':pack,'size':[b.STAGE_W,b.STAGE_H]}

b.build_stage=build_stage_v5

# Build all fighters/stages with the patched pipeline.
b.main()

# ---------------------------------------------------------------------------
# Fast UI assets: one portrait atlas avoids decoding 25 separate 512px PNGs on
# the character-select screen. Title uses a small precomposed JPEG so Android
# never flashes a procedural fallback while a large stage image decodes.
# ---------------------------------------------------------------------------
ui=OUT/'ui';ui.mkdir(parents=True,exist_ok=True)
fs=b.fighters();cell=160;cols=7;rows=math.ceil(len(fs)/cols)
atlas=Image.new('RGB',(cols*cell,rows*cell),(7,8,11))
for idx,d in enumerate(fs):
    p=OUT/'fighters'/d['id']/'portrait.png'
    if not p.exists():continue
    im=Image.open(p).convert('RGBA');bg=Image.new('RGBA',(cell,cell),(8,9,12,255));fit=ImageOps.fit(im,(cell,cell),method=Image.Resampling.LANCZOS);bg.alpha_composite(fit)
    atlas.paste(bg.convert('RGB'),((idx%cols)*cell,(idx//cols)*cell))
atlas.save(ui/'portrait-atlas.jpg',quality=88,optimize=True,progressive=True)

# Prefer the dark-city street stage for title art; it reads immediately on phone.
title_src=OUT/'stages'/'street.png'
if not title_src.exists():title_src=next((OUT/'stages').glob('*.png'))
ti=ImageOps.fit(Image.open(title_src).convert('RGB'),(1280,720),method=Image.Resampling.LANCZOS)
ti=ImageEnhance.Brightness(ti).enhance(.58);ti=ImageEnhance.Contrast(ti).enhance(1.12)
# Center darkening leaves clean space for title typography.
ov=Image.new('RGBA',ti.size,(0,0,0,0));od=ImageDraw.Draw(ov,'RGBA');od.rectangle((0,250,1280,610),fill=(0,0,0,95));ti=ti.convert('RGBA');ti.alpha_composite(ov)
ti.convert('RGB').save(ui/'title-bg.jpg',quality=86,optimize=True,progressive=True)

report_path=OUT/'build-report.json'
r=json.loads(report_path.read_text(encoding='utf-8'))
r['renderer']='prerendered-48f-hd-v5-anchored-realistic'
r['fighterPipeline']='shared-scale-bottom-anchor+silhouette-costume'
r['stagePipeline']='layered-cc0-scene-first-v5'
r['portraitAtlas']='ui/portrait-atlas.jpg'
r['titleBackground']='ui/title-bg.jpg'
r['v5Masters']=['v5/stonewall-realistic.gif','v5/erika-realistic.gif']
report_path.write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf-8')
print('REAL_V5_OK')
