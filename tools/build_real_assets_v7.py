#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageOps, ImageDraw, ImageEnhance, ImageFilter, ImageChops
import json
import build_real_assets as b

SRC=b.SRC
OUT=b.OUT
(OUT/'ui').mkdir(parents=True, exist_ok=True)
ORIG_SOURCE=b.source_for
ORIG_RAW=b.raw_frames
MALE=SRC/'v5'/'stonewall-realistic.gif'
FEMALE=SRC/'v5'/'erika-realistic.gif'

MALE_NINJAS={'scorpion','reptile','ermac','rain','noob','human-smoke','subzero','classic-subzero'}
FEMALE_NINJAS={'kitana','jade','mileena'}

# Keep the high-resolution master only where MK intentionally uses palette-swap
# ninja families. Every other fighter goes back to an independent real source.
def source_for_v7(d):
    i=d['id'];st=d['style']
    if (i in MALE_NINJAS or st in {'ninja','subzero'}) and MALE.exists(): return MALE
    if (i in FEMALE_NINJAS or st=='femaleNinja') and FEMALE.exists(): return FEMALE
    return ORIG_SOURCE(d)

seq_cache={}
def quantile(vals,p):
    vals=sorted(vals)
    return vals[min(len(vals)-1,max(0,round((len(vals)-1)*p)))] if vals else 1

def normalize_sequence(frames):
    # One scale for all 48 cels: no breathing/jumping sprite-size pump.
    boxes=[]
    prepared=[]
    for fr in frames:
        fr=b.bg_to_alpha(fr)
        prepared.append(fr)
        box=fr.getchannel('A').getbbox()
        if box: boxes.append((box[2]-box[0],box[3]-box[1]))
    if not boxes:return [Image.new('RGBA',(b.FW,b.FH),(0,0,0,0)) for _ in frames]
    widths=[x for x,_ in boxes];heights=[y for _,y in boxes]
    scale=min(326/max(1,quantile(widths,.94)),438/max(1,quantile(heights,.94)))
    scale=min(scale,348/max(1,max(widths)),454/max(1,max(heights)))
    scale=max(.10,scale)
    out=[]
    for fr in prepared:
        box=fr.getchannel('A').getbbox()
        if not box:
            out.append(Image.new('RGBA',(b.FW,b.FH),(0,0,0,0)));continue
        crop=fr.crop(box)
        nw=max(1,round(crop.width*scale));nh=max(1,round(crop.height*scale))
        crop=crop.resize((nw,nh),Image.Resampling.LANCZOS)
        if scale>1.08:crop=crop.filter(ImageFilter.UnsharpMask(radius=.75,percent=120,threshold=2))
        dst=Image.new('RGBA',(b.FW,b.FH),(0,0,0,0));dst.alpha_composite(crop,((b.FW-nw)//2,b.FH-nh-9));out.append(dst)
    return out

def raw_frames_v7(path):
    key='v7:'+str(path)
    if key in seq_cache:return [x.copy() for x in seq_cache[key]]
    frames=normalize_sequence(ORIG_RAW(path))
    seq_cache[key]=[x.copy() for x in frames]
    return frames

def identity(fr):return fr.copy()

def poly_mask(im,pts,soft=1):
    m=Image.new('L',im.size,0);ImageDraw.Draw(m).polygon([(int(x),int(y)) for x,y in pts],fill=255)
    if soft:m=m.filter(ImageFilter.GaussianBlur(soft))
    return ImageChops.multiply(im.getchannel('A'),m)

def rect_mask(im,box,soft=1):
    m=Image.new('L',im.size,0);ImageDraw.Draw(m).rectangle(tuple(map(int,box)),fill=255)
    if soft:m=m.filter(ImageFilter.GaussianBlur(soft))
    return ImageChops.multiply(im.getchannel('A'),m)

def apply_color(im,mask,color,alpha=.65):
    mask=mask.point(lambda x:int(x*alpha));layer=Image.new('RGBA',im.size,tuple(color)+(255,));layer.putalpha(mask)
    out=im.copy();out.alpha_composite(layer);return out

def grade(im,d):
    # Preserve the actual rendered material/shading; only a small cinematic grade.
    x=ImageEnhance.Contrast(im).enhance(1.08)
    x=ImageEnhance.Color(x).enhance(1.03)
    if d['id'] in MALE_NINJAS|FEMALE_NINJAS:
        gray=ImageOps.grayscale(x);cold=ImageOps.colorize(gray,(4,5,7),(72,76,82)).convert('RGBA');cold.putalpha(x.getchannel('A'));x=Image.blend(x,cold,.28)
    return x

def organic_ninja(im,d):
    box=im.getchannel('A').getbbox()
    if not box:return im
    l,t,r,bt=box;w=max(1,r-l);h=max(1,bt-t);cx=(l+r)/2
    pri=tuple(d['primary']);dark=tuple(d['dark'])
    out=im.copy()
    # Tapered tabard follows the body silhouette; unlike v6 this never paints
    # opaque geometric rectangles outside the source character.
    torso=[(cx-w*.23,t+h*.20),(cx+w*.23,t+h*.20),(cx+w*.17,t+h*.52),(cx+w*.07,t+h*.61),(cx,t+h*.66),(cx-w*.07,t+h*.61),(cx-w*.17,t+h*.52)]
    out=apply_color(out,poly_mask(out,torso,2),pri,.58)
    # Dark upper head / cloth and lower-face mask are alpha-clipped to the real head.
    out=apply_color(out,rect_mask(out,(cx-w*.23,t+h*.015,cx+w*.23,t+h*.19),2),dark,.56)
    face=[(cx-w*.18,t+h*.105),(cx+w*.18,t+h*.105),(cx+w*.14,t+h*.18),(cx-w*.14,t+h*.18)]
    out=apply_color(out,poly_mask(out,face,1),pri,.64)
    # Slim belt/bracers/greaves: still clipped to the real body, no floating armor.
    out=apply_color(out,rect_mask(out,(cx-w*.22,t+h*.50,cx+w*.22,t+h*.565),1),dark,.58)
    if d['id'] in {'subzero','classic-subzero'}:
        ice=(126,218,244);out=apply_color(out,poly_mask(out,[(cx-w*.10,t+h*.24),(cx+w*.10,t+h*.24),(cx+w*.07,t+h*.48),(cx-w*.07,t+h*.48)],2),ice,.20)
    if d['id']=='noob':
        shade=Image.new('RGBA',out.size,(0,0,3,60));shade.putalpha(out.getchannel('A').point(lambda a:int(a*.35)));out.alpha_composite(shade)
    return out.filter(ImageFilter.UnsharpMask(radius=.45,percent=108,threshold=2))

def decorate_v7(im,d):
    i=d['id'];st=d['style']
    if i in MALE_NINJAS|FEMALE_NINJAS or st in {'ninja','subzero','femaleNinja'}:
        return organic_ninja(im,d)
    # Independent source fighters already have coherent clothes/body proportions.
    # Only add genuinely iconic small accessories where the source lacks them.
    out=im.copy();box=out.getchannel('A').getbbox()
    if not box:return out
    l,t,r,bt=box;w=max(1,r-l);h=max(1,bt-t);cx=(l+r)/2
    if i=='kano':
        dr=ImageDraw.Draw(out,'RGBA');dr.ellipse((cx+w*.055,t+h*.075,cx+w*.13,t+h*.145),fill=(155,160,166,175));dr.ellipse((cx+w*.082,t+h*.100,cx+w*.108,t+h*.126),fill=(255,35,28,245))
    elif i=='kung-lao':
        dr=ImageDraw.Draw(out,'RGBA');y=t+h*.035;dr.ellipse((cx-w*.29,y-h*.025,cx+w*.29,y+h*.035),fill=(15,16,19,225),outline=(185,190,196,220),width=max(2,int(w*.018)))
    elif i=='jax':
        metal=(184,190,198);out=apply_color(out,rect_mask(out,(l,t+h*.20,l+w*.28,t+h*.62),2),metal,.22);out=apply_color(out,rect_mask(out,(r-w*.28,t+h*.20,r,t+h*.62),2),metal,.22)
    return ImageEnhance.Contrast(out).enhance(1.05)

b.source_for=source_for_v7
b.raw_frames=raw_frames_v7
b.normalize=identity
b.tint_sprite=grade
b.decorate=decorate_v7
b.main()

report=OUT/'build-report.json'
r=json.loads(report.read_text(encoding='utf-8'))
r['renderer']='prerendered-48f-hd-v7-diverse-roster'
r['fighterPipeline']='v7-diverse-sources+stable-anchor+organic-ninja-cloth'
r['v7MaleNinjaMaster']='v5/stonewall-realistic.gif'
r['v7FemaleNinjaMaster']='v5/erika-realistic.gif'
r['v7IndependentSourceCount']=len([d for d in b.fighters() if d['id'] not in MALE_NINJAS|FEMALE_NINJAS])
report.write_text(json.dumps(r,ensure_ascii=False,indent=2),encoding='utf-8')
print('REAL_V7_OK')