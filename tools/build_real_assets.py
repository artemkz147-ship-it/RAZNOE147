#!/usr/bin/env python3
from pathlib import Path
from PIL import Image,ImageOps,ImageDraw,ImageEnhance,ImageFilter,ImageStat
import re,json,math,random,sys

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'.cc0-sources'
OUT=ROOT/'web'/'assets'
DATA=(ROOT/'web'/'umk3-data.js').read_text(encoding='utf-8')
FW,FH,COLS=360,480,8
ROWS=6
ATLAS_W,ATLAS_H=FW*COLS,FH*ROWS
STAGE_W,STAGE_H=1920,1080

ANIMS=[
 ('idle',4,.000,.025),('walk',6,.045,.105),('crouch',3,.155,.190),('jump',4,.215,.285),
 ('LP',3,.315,.355),('HP',3,.375,.420),('LK',3,.455,.500),('HK',3,.520,.575),
 ('block',3,.605,.645),('hit',3,.675,.715),('special',5,.745,.840),('run',4,.110,.145),('win',4,.900,.980)
]
assert sum(x[1] for x in ANIMS)==48

def rgb(h):
    h=h.lstrip('#');return tuple(int(h[i:i+2],16) for i in (0,2,4))
def clamp(v,a=0,b=255):return max(a,min(b,int(v)))
def shade(c,n):return tuple(clamp(x+n) for x in c)

def fighters():
    p=re.compile(r"F\('([^']+)'\s*,\s*'([^']+)'\s*,\s*'(#[0-9A-Fa-f]{6})'\s*,\s*'(#[0-9A-Fa-f]{6})'\s*,\s*'([^']+)'",re.M)
    return [dict(id=m.group(1),name=m.group(2),primary=rgb(m.group(3)),dark=rgb(m.group(4)),style=m.group(5)) for m in p.finditer(DATA)]
def stages():
    p=re.compile(r"\{id:'([^']+)',name:(?:'([^']+)'|\"([^\"]+)\"),kind:'([^']+)',sky:\['(#[0-9A-Fa-f]{6})','(#[0-9A-Fa-f]{6})'\],accent:'(#[0-9A-Fa-f]{6})'",re.M)
    return [dict(id=m.group(1),name=m.group(2) or m.group(3),kind=m.group(4),sky0=rgb(m.group(5)),sky1=rgb(m.group(6)),accent=rgb(m.group(7))) for m in p.finditer(DATA)]

def one(pattern):
    xs=sorted(SRC.glob(pattern))
    if not xs: raise FileNotFoundError(pattern)
    return xs[0]

def source_for(d):
    i=d['id'];st=d['style']
    if i in {'scorpion','reptile','ermac','subzero','classic-subzero','rain','noob','human-smoke'} or st in {'ninja','subzero'}:
        return one('singles/verlaine.gif')
    if i in {'kitana','jade','mileena'} or st=='femaleNinja':
        return one('mustermenschen/Tasen woman 1 Tasen mil. combat*.gif')
    if i=='sonya': return one('mustermenschen/Tasen woman 2 Tasen mil. combat*.gif')
    if i=='sindel': return one('mustermenschen/Ava Lee (adult) Thai boxe*.gif')
    if i=='jax': return one('mustermenschen/Raf Boulder Thai Boxe*.gif')
    if i=='nightwolf': return one('mustermenschen/Rhivan male thai boxe*.gif')
    if i=='kano': return one('mustermenschen/Danny Van Damage Thai boxe*.gif')
    if i=='stryker': return one("mustermenschen/Nick Adler Chains of compassion*.gif")
    if i=='kung-lao': return one('mustermenschen/Susa No Mikoto Tasen mil. combat*.gif')
    if i=='liukang': return one('mustermenschen/Kelvin Zone muai thai*.gif')
    if i=='kabal': return one('mustermenschen/Byron V. chain of compassion*.gif')
    if i in {'cyrax','sektor','smoke'} or st=='cyborg': return one('singles/tasen-defender.png')
    if i=='shao-kahn' or st=='shaokahn': return Path('__DARK_KNIGHT__')
    if i=='motaro' or st=='motaro': return one('mustermenschen/Komato berserker alpha contact.gif')
    if i=='sheeva' or st=='sheeva': return one('mustermenschen/Tasen woman 1 Chains of compassion*.gif')
    if i=='shang-tsung' or st=='shang': return one('mustermenschen/Rhivan male chains of compassion*.gif')
    # Stable pool for remaining human styles.
    pool=[
      'mustermenschen/Tasen man 1 chains of compassion*.gif',
      'mustermenschen/George Nguyen WrongCookieDo*.gif',
      'mustermenschen/Antony Nagasaki WrongCookieDo*.gif',
      'mustermenschen/Danny Van Damage Thai boxe*.gif',
      'mustermenschen/Kelvin Area muai thai*.gif'
    ]
    return one(pool[sum(map(ord,i))%len(pool)])

def bg_to_alpha(im):
    im=im.convert('RGBA')
    a=im.getchannel('A')
    if a.getextrema()!=(255,255):return im
    px=im.load();w,h=im.size
    corners=[px[0,0][:3],px[w-1,0][:3],px[0,h-1][:3],px[w-1,h-1][:3]]
    bg=tuple(sum(c[k] for c in corners)//4 for k in range(3))
    # Only remove near-uniform dark/light backing; preserve detailed opaque source sheets.
    if max(abs(corners[j][k]-bg[k]) for j in range(4) for k in range(3))>28:return im
    dat=[]
    for r,g,b,a0 in im.getdata():
        dist=abs(r-bg[0])+abs(g-bg[1])+abs(b-bg[2])
        if dist<42:dat.append((r,g,b,0))
        elif dist<86:dat.append((r,g,b,clamp((dist-42)*255/44)))
        else:dat.append((r,g,b,a0))
    im.putdata(dat);return im

def sample_indices(n):
    out=[]
    for _,count,a,b in ANIMS:
        lo=round((n-1)*a);hi=round((n-1)*b)
        if count==1:out.append((lo+hi)//2)
        else:
            for j in range(count):out.append(round(lo+(hi-lo)*j/(count-1)))
    return [max(0,min(n-1,x)) for x in out]

raw_cache={}
def gif_frames(path):
    key=str(path)
    if key in raw_cache:return [x.copy() for x in raw_cache[key]]
    im=Image.open(path);n=getattr(im,'n_frames',1);want=sample_indices(n);wanted=set(want);got={}
    for k in range(n):
        if k in wanted:
            im.seek(k);got[k]=bg_to_alpha(im.convert('RGBA'))
        if len(got)==len(wanted):break
    frames=[got.get(i,next(iter(got.values())).copy()).copy() for i in want]
    raw_cache[key]=[x.copy() for x in frames]
    return frames

def grid_cells(path):
    key='grid:'+str(path)
    if key in raw_cache:return [x.copy() for x in raw_cache[key]]
    im=Image.open(path).convert('RGBA');rgbim=im.convert('RGB');w,h=im.size
    # Detect empty black gutters to recover the cell grid from a giant sprite sheet.
    small=rgbim.resize((max(64,w//8),max(64,h//8)))
    # Estimate likely grid from the source aspect and the visual sheet density.
    best=[]
    for cols in range(24,37):
        rows=round(cols*h/w)
        if rows<18 or rows>40:continue
        cw=w/cols;ch=h/rows;score=abs(cw-ch)
        best.append((score,cols,rows))
    _,cols,rows=min(best)
    cells=[]
    for r in range(rows):
        for c in range(cols):
            x0=round(c*w/cols);x1=round((c+1)*w/cols);y0=round(r*h/rows);y1=round((r+1)*h/rows)
            fr=bg_to_alpha(im.crop((x0,y0,x1,y1)))
            bbox=fr.getchannel('A').getbbox()
            if bbox and (bbox[2]-bbox[0])*(bbox[3]-bbox[1])>120:cells.append(fr)
    if len(cells)<80:
        raise RuntimeError(f'Could not recover enough sprite cells from {path}: {len(cells)}')
    idx=sample_indices(len(cells));frames=[cells[i].copy() for i in idx]
    raw_cache[key]=[x.copy() for x in frames]
    return frames

def extract_sheet(path):
    im=Image.open(path).convert('RGBA');w,h=im.size
    # Dark Knight sheets are a simple grid-like atlas with transparency.
    # Split by 256-ish source cells and discard empty cells.
    cells=[]
    for cw,ch in [(256,256),(256,512),(512,512)]:
        tmp=[]
        for y in range(0,h,ch):
            for x in range(0,w,cw):
                fr=im.crop((x,y,min(w,x+cw),min(h,y+ch)))
                bbox=fr.getchannel('A').getbbox()
                if bbox and (bbox[2]-bbox[0])*(bbox[3]-bbox[1])>1500:tmp.append(fr)
        if len(tmp)>=3:cells=tmp;break
    return cells

def dark_knight_frames():
    key='darkknight'
    if key in raw_cache:return [x.copy() for x in raw_cache[key]]
    root=SRC/'dark-knight'/'Knight 2D'/'Sprites'
    groups={
      'idle':['Idle.png','IdleVain.png'],'walk':['Walk.png','WalkBack.png'],'crouch':['Crouch.png','CrouchBlockIdle.png'],
      'jump':['Jump.png','RunJump.png'],'LP':['Attack1.png'],'HP':['Slash.png'],'LK':['Kick.png'],'HK':['Slash2.png'],
      'block':['Block.png','BlockIdle.png'],'hit':['Impact.png','Impact2.png'],'special':['PowerUp.png','Casting1.png','Slash4.png'],
      'run':['Run.png','RunBack.png'],'win':['IdleVain2.png','PowerUp.png']
    }
    out=[]
    for name,count,_,_ in ANIMS:
        cells=[]
        for fn in groups[name]:
            p=root/fn
            if p.exists():cells+=extract_sheet(p)
        if not cells:cells=[Image.new('RGBA',(256,256),(0,0,0,0))]
        for j in range(count):out.append(cells[round(j*(len(cells)-1)/max(1,count-1))].copy())
    raw_cache[key]=[x.copy() for x in out];return out

def raw_frames(path):
    if str(path)=='__DARK_KNIGHT__':return dark_knight_frames()
    return grid_cells(path) if path.suffix.lower()=='.png' else gif_frames(path)

def normalize(fr):
    fr=bg_to_alpha(fr);a=fr.getchannel('A');bbox=a.getbbox()
    if not bbox:return Image.new('RGBA',(FW,FH),(0,0,0,0))
    fr=fr.crop(bbox)
    # Slightly larger body than classic MK3 sprites, designed for phone screens.
    scale=min((FW-42)/fr.width,(FH-24)/fr.height)
    nw=max(1,round(fr.width*scale));nh=max(1,round(fr.height*scale))
    fr=fr.resize((nw,nh),Image.Resampling.LANCZOS)
    if scale>1.35:fr=fr.filter(ImageFilter.UnsharpMask(radius=1.0,percent=135,threshold=2))
    dst=Image.new('RGBA',(FW,FH),(0,0,0,0));dst.alpha_composite(fr,((FW-nw)//2,FH-nh-10));return dst

def tint_sprite(im,d,strength=None):
    st=d['style'];strength = strength if strength is not None else (.50 if st in {'ninja','subzero','femaleNinja','cyborg'} else .18)
    gray=ImageOps.grayscale(im);col=ImageOps.colorize(gray,d['dark'],shade(d['primary'],45)).convert('RGBA');col.putalpha(im.getchannel('A'))
    return Image.blend(im,col,strength)

def decorate(im,d):
    st=d['style'];i=d['id'];pri=d['primary'];dark=d['dark'];dst=Image.new('RGBA',(FW,FH),(0,0,0,0))
    dr=ImageDraw.Draw(dst,'RGBA')
    # Underlays first.
    if st=='sheeva' or i=='sheeva':
        skin=(154,91,67,255)
        for pts in [((165,180),(82,246),(46,220)),((195,180),(278,246),(314,220))]:dr.line(pts,fill=skin,width=22,joint='curve')
    if st=='motaro' or i=='motaro':
        # Centaur silhouette beneath the real monster torso.
        body=(92,48,31,255);hi=(137,81,49,255)
        dr.ellipse((70,260,290,390),fill=body,outline=(25,12,8,255),width=7)
        for x in (92,142,230,270):dr.line((x,340,x+(x-180)//3,462),fill=body,width=30)
        dr.line((78,300,20,360,8,410),fill=hi,width=15,joint='curve')
    dst.alpha_composite(im)
    dr=ImageDraw.Draw(dst,'RGBA')
    # Recognition layer: costume detail only; body/pose remains from prerendered source.
    if st in {'ninja','subzero','femaleNinja'} or i in {'scorpion','reptile','ermac','rain','noob','human-smoke'}:
        dr.rounded_rectangle((139,58,221,132),22,fill=dark+(220,),outline=pri+(245,),width=4)
        dr.rectangle((143,91,217,111),fill=pri+(235,))
        dr.rectangle((153,91,176,98),fill=(235,237,230,245));dr.rectangle((184,91,207,98),fill=(235,237,230,245))
        dr.polygon([(126,150),(234,150),(218,286),(142,286)],fill=pri+(125,),outline=dark+(230,))
        if i in {'scorpion'}:dr.polygon([(180,108),(196,122),(180,139),(164,122)],fill=(226,174,41,210))
        if i in {'subzero','classic-subzero'}:dr.line((145,154,180,276,215,154),fill=(159,229,255,170),width=5)
    elif st=='cyborg' or i in {'cyrax','sektor','smoke'}:
        dr.rounded_rectangle((134,58,226,135),18,fill=shade(pri,-30)+(230,),outline=(180,188,194,255),width=5)
        dr.rectangle((150,91,210,105),fill=(20,23,27,255));dr.rectangle((158,94,202,100),fill=(255,220,70,230))
        dr.rounded_rectangle((125,142,235,275),16,fill=pri+(105,),outline=(155,164,171,190),width=5)
        dr.line((142,166,218,166),fill=(195,203,208,180),width=5)
    elif st=='kano' or i=='kano':
        dr.ellipse((187,72,210,95),fill=(210,210,214,235));dr.ellipse((194,78,204,88),fill=(255,36,32,255))
    elif st=='jax' or i=='jax':
        for x in (126,234):dr.line((x,160,x-20 if x<180 else x+20,270),fill=(171,180,187,150),width=16)
    elif st=='kabal' or i=='kabal':
        dr.rounded_rectangle((143,84,217,129),14,fill=(38,42,46,220));dr.line((146,120,112,176),fill=(100,108,115,210),width=8);dr.line((214,120,248,176),fill=(100,108,115,210),width=8)
    elif st=='sindel' or i=='sindel':
        for off in (-18,0,18):dr.arc((92+off,35,268+off,235),190,350,fill=(229,226,235,205),width=9)
    elif st=='shaokahn' or i=='shao-kahn':
        dr.polygon([(122,55),(238,55),(254,112),(218,145),(142,145),(106,112)],fill=(166,169,174,225),outline=(38,25,26,255))
        dr.polygon([(125,65),(74,24),(143,46)],fill=(105,106,110,245));dr.polygon([(235,65),(286,24),(217,46)],fill=(105,106,110,245))
        dr.rectangle((151,92,172,101),fill=(255,207,87,255));dr.rectangle((188,92,209,101),fill=(255,207,87,255))
    if st=='sheeva' or i=='sheeva':
        dr.line((148,185,75,260),fill=(154,91,67,230),width=20);dr.line((212,185,285,260),fill=(154,91,67,230),width=20)
    return dst

def motaro_adjust(im,d):
    if d['id']!='motaro' and d['style']!='motaro':return im
    # Raise upper body so it reads as a centaur boss over the generated horse base.
    a=im.getchannel('A');bbox=a.getbbox()
    return im

def portrait(frame,d):
    bg=Image.new('RGB',(512,512),shade(d['dark'],-10));pix=bg.load();rnd=random.Random(d['id'])
    for _ in range(260):
        x=rnd.randrange(512);y=rnd.randrange(512);r=rnd.randrange(4,32);c=shade(d['primary'],rnd.randrange(-55,35));ImageDraw.Draw(bg).ellipse((x-r,y-r,x+r,y+r),fill=c+( ) if False else c)
    fg=frame.copy();bbox=fg.getchannel('A').getbbox()
    if bbox:fg=fg.crop((max(0,bbox[0]-10),bbox[1],min(FW,bbox[2]+10),min(FH,bbox[1]+300)))
    fg.thumbnail((430,500),Image.Resampling.LANCZOS);layer=bg.convert('RGBA');layer.alpha_composite(fg,((512-fg.width)//2,512-fg.height))
    dr=ImageDraw.Draw(layer,'RGBA');dr.rectangle((0,0,511,511),outline=d['primary']+(220,),width=10);dr.rectangle((12,12,499,499),outline=(240,226,185,70),width=2)
    return layer

def build_fighter(d):
    src=source_for(d);frames=raw_frames(src);norm=[]
    for fr in frames:
        x=normalize(fr);x=tint_sprite(x,d);x=decorate(x,d);norm.append(x)
    atlas=Image.new('RGBA',(ATLAS_W,ATLAS_H),(0,0,0,0))
    for idx,fr in enumerate(norm):atlas.alpha_composite(fr,((idx%COLS)*FW,(idx//COLS)*FH))
    out=OUT/'fighters'/d['id'];out.mkdir(parents=True,exist_ok=True)
    atlas.save(out/'atlas.png',optimize=True,compress_level=7)
    portrait(norm[0],d).save(out/'portrait.png',optimize=True,compress_level=7)
    return {'id':d['id'],'source':src.name if str(src)!='__DARK_KNIGHT__' else 'Dark Knight 2D','frames':len(norm),'atlas':[ATLAS_W,ATLAS_H],'frame':[FW,FH]}

# ---------------- stages ----------------
def raster_candidates(root):
    exts={'.png','.jpg','.jpeg','.webp'};xs=[]
    if not root.exists():return xs
    for p in root.rglob('*'):
        if not p.is_file() or p.suffix.lower() not in exts:continue
        try:
            im=Image.open(p);w,h=im.size;im.close()
            if w>=640 and h>=300:xs.append((w*h,w,h,p))
        except:pass
    return sorted(xs,reverse=True)

def pack_base(name):
    root=SRC/'backgrounds'/name;xs=raster_candidates(root)
    if not xs:return None
    # Prefer landscape images with useful resolution, not tiny props/atlases.
    landscape=[x for x in xs if x[1]/max(1,x[2])>1.25]
    area,w,h,p=(landscape or xs)[0]
    im=Image.open(p).convert('RGB');return ImageOps.fit(im,(STAGE_W,STAGE_H),method=Image.Resampling.LANCZOS),p

def grade(im,s,amount=.34):
    tint=Image.new('RGB',im.size,s['sky1']);im=Image.blend(im,tint,amount)
    im=ImageEnhance.Contrast(im).enhance(1.12);im=ImageEnhance.Color(im).enhance(.90)
    return im

def glow(layer,xy,color,r=90):
    x,y=xy;g=Image.new('RGBA',layer.size,(0,0,0,0));d=ImageDraw.Draw(g,'RGBA')
    for rr in range(r,2,-8):
        a=max(2,int(48*(1-rr/r)));d.ellipse((x-rr,y-rr,x+rr,y+rr),fill=color+(a,))
    layer.alpha_composite(g)

def build_stage(s,idx):
    packmap={'subway':'Industrial','street':'DarkCity','roof':'DarkCity','bank':'AbandonCity','soul':'Industrial','tower':'StarryNight','temple':'StarryNight','grave':'AbandonCity','water':'DarkCity','portal':'StarryNight','desert':'StarryNight','cave':'Industrial','hell':'Industrial','balcony':'AbandonCity','noob':'DarkCity','pit':'Industrial'}
    pack=packmap.get(s['kind'],'DarkCity');res=pack_base(pack)
    if res is None:
        base=Image.new('RGB',(STAGE_W,STAGE_H),s['sky0']);src='generated-fallback'
    else:base,p=res;src=str(p.relative_to(SRC))
    base=grade(base,s,.28 if res else .55).convert('RGBA');d=ImageDraw.Draw(base,'RGBA');k=s['kind'];ac=s['accent']
    # Depth fog and playable floor.
    d.rectangle((0,790,STAGE_W,1080),fill=(10,11,13,205));d.line((0,790,STAGE_W,790),fill=ac+(150,),width=4)
    if k=='subway':
        d.rectangle((0,730,STAGE_W,835),fill=(22,28,31,235));d.rectangle((0,825,STAGE_W,846),fill=(197,163,74,220));
        for y in (900,1015):d.line((0,y,STAGE_W,y),fill=(122,127,130,230),width=13)
        for x in range(-40,STAGE_W,105):d.rectangle((x,910,x+54,1027),fill=(66,59,49,210))
        # Train silhouette and lit windows.
        d.rounded_rectangle((1120,380,1900,700),28,fill=(63,50,52,230),outline=(135,50,48,240),width=10)
        for x in range(1160,1850,145):d.rounded_rectangle((x,430,x+105,555),8,fill=(201,174,103,185))
    elif k in {'street','roof'}:
        d.rectangle((0,800,STAGE_W,1080),fill=(12,13,17,210));
        for x,c in [(170,(220,61,66)),(1510,(45,157,205)),(1040,(202,141,48))]:d.rounded_rectangle((x,330,x+210,405),10,fill=c+(140,));glow(base,(x+105,370),c,100)
        if k=='roof':d.rectangle((0,730,STAGE_W,815),fill=(42,42,48,245));d.rectangle((0,715,STAGE_W,735),fill=(111,105,112,210))
    elif k=='bank':
        for x in range(130,STAGE_W,300):d.rectangle((x,280,x+54,820),fill=(152,132,94,195));d.rectangle((x-25,275,x+79,305),fill=(202,180,129,180))
        d.ellipse((715,370,1205,860),outline=(185,169,126,185),width=18);d.ellipse((805,460,1115,770),outline=(105,98,79,190),width=12)
    elif k=='soul':
        for x in range(120,STAGE_W,270):
            d.rounded_rectangle((x,270,x+150,800),34,fill=(13,32,21,195),outline=ac+(190,),width=9);d.ellipse((x+42,360,x+108,700),fill=ac+(80,));glow(base,(x+75,520),ac,75)
    elif k=='tower':
        d.ellipse((720,140,1200,620),fill=(39,31,20,190),outline=(186,148,81,220),width=18);d.line((960,380,960,210),fill=(215,188,123,240),width=12);d.line((960,380,1080,430),fill=(215,188,123,240),width=9)
    elif k=='temple':
        for x in range(100,STAGE_W,310):d.rectangle((x,270,x+60,810),fill=(80,36,28,210));d.rectangle((x-25,260,x+85,295),fill=(142,59,39,180))
        for x in range(230,STAGE_W,430):glow(base,(x,560),(255,94,30),80);d.ellipse((x-18,535,x+18,610),fill=(255,113,36,210))
    elif k=='grave':
        for x in range(70,STAGE_W,155):
            y=800-(x%4)*18;d.rounded_rectangle((x,y-105,x+62,y),16,fill=(64,71,75,190));d.ellipse((x,y-135,x+62,y-75),fill=(64,71,75,190))
        fog=Image.new('RGBA',base.size,(0,0,0,0));fd=ImageDraw.Draw(fog,'RGBA');
        for j in range(18):fd.ellipse((j*130-150,650+(j%3)*40,j*130+330,900+(j%3)*50),fill=(170,190,194,18));base.alpha_composite(fog)
    elif k=='water':
        d.rectangle((0,690,STAGE_W,880),fill=(18,67,83,175));
        for y in range(720,870,28):d.line((0,y,STAGE_W,y),fill=(82,184,199,55),width=5)
    elif k=='portal':
        for r in range(280,40,-25):d.ellipse((960-r,470-r,960+r,470+r),outline=(74,130,255,max(30,170-r//2)),width=16)
        glow(base,(960,470),(91,132,255),230)
    elif k=='desert':
        warm=Image.new('RGBA',base.size,(218,121,54,80));base.alpha_composite(warm);d.polygon([(0,800),(340,560),(720,810),(1150,520),(STAGE_W,790),(STAGE_W,900),(0,900)],fill=(136,78,44,155))
    elif k in {'cave','hell'}:
        d.polygon([(0,0),(0,300),(180,180),(320,330),(510,150),(690,310),(870,120),(1080,320),(1320,130),(1510,320),(1740,140),(1920,300),(1920,0)],fill=(31,13,12,210))
        if k=='hell':
            d.rectangle((0,890,STAGE_W,1080),fill=(96,28,8,210));
            for x in range(0,STAGE_W,150):glow(base,(x,930),(255,75,15),65)
    elif k in {'balcony','noob'}:
        for x in range(110,STAGE_W,300):d.rectangle((x,270,x+70,825),fill=(74,67,89,180));d.rectangle((x-30,260,x+100,294),fill=(143,126,169,160))
        if k=='noob':base=ImageEnhance.Brightness(base.convert('RGB')).enhance(.45).convert('RGBA')
    elif k=='pit':
        d.rectangle((0,760,STAGE_W,850),fill=(73,42,37,235));
        for x in range(70,STAGE_W,180):
            cx=x;cy=940;d.ellipse((cx-48,cy-48,cx+48,cy+48),fill=(75,75,78,230));
            for a in range(0,360,60):
                r=85;ex=cx+math.cos(math.radians(a))*r;ey=cy+math.sin(math.radians(a))*r;d.polygon([(cx,cy),(ex-12,ey-8),(ex+14,ey+6)],fill=(171,174,178,235))
    out=OUT/'stages';out.mkdir(parents=True,exist_ok=True);base.convert('RGB').save(out/(s['id']+'.png'),optimize=True,compress_level=6)
    return {'id':s['id'],'source':src,'pack':pack,'size':[STAGE_W,STAGE_H]}

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    fs=fighters();ss=stages();print('BUILD_REAL fighters',len(fs),'stages',len(ss))
    frep=[]
    for n,d in enumerate(fs,1):
        print(f'FIGHTER {n}/{len(fs)} {d["id"]}',flush=True);frep.append(build_fighter(d))
    srep=[]
    for n,s in enumerate(ss,1):
        print(f'STAGE {n}/{len(ss)} {s["id"]}',flush=True);srep.append(build_stage(s,n-1))
    report={'renderer':'prerendered-48f-hd-v3','fighterFrame':[FW,FH],'fighterAtlas':[ATLAS_W,ATLAS_H],'animations':{name:count for name,count,_,_ in ANIMS},'fighters':frep,'stages':srep}
    (OUT/'build-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    real_stage=sum(1 for x in srep if x['source']!='generated-fallback')
    print('REAL_ASSETS_OK',len(frep),real_stage)
    if len(frep)<25:raise SystemExit('fighter build incomplete')
    if real_stage<10:raise SystemExit(f'not enough real stage bases: {real_stage}')

if __name__=='__main__':main()
