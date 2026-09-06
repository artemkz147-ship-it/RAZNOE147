from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
import math, random

OUT=Path(__file__).resolve().parents[1]/'app/src/main/res/drawable-nodpi'
OUT.mkdir(parents=True,exist_ok=True)
S=2
C={
'dark':(8,14,17,255),'steel':(42,55,60,255),'steel2':(72,88,92,255),'edge':(164,180,181,255),
'cyan':(63,238,220,255),'cyan2':(144,255,245,255),'orange':(240,153,48,255),'red':(238,82,53,255),
'violet':(179,96,255,255),'yellow':(247,205,83,255),'blue':(75,157,255,255),'white':(232,245,242,255)
}
def Q(v):return int(round(v*S))
def im(sz=384):return Image.new('RGBA',(sz*S,sz*S),(0,0,0,0))
def D(x):return ImageDraw.Draw(x,'RGBA')
def rr(d,b,r,fill,outline=None,w=1):d.rounded_rectangle(tuple(Q(v) for v in b),radius=Q(r),fill=fill,outline=outline,width=Q(w) if outline else 1)
def el(d,b,fill,outline=None,w=1):d.ellipse(tuple(Q(v) for v in b),fill=fill,outline=outline,width=Q(w) if outline else 1)
def ln(d,pts,fill,w=1):d.line([(Q(x),Q(y)) for x,y in pts],fill=fill,width=Q(w),joint='curve')
def poly(d,pts,fill,outline=None,w=1):
    pp=[(Q(x),Q(y)) for x,y in pts];d.polygon(pp,fill=fill)
    if outline:d.line(pp+[pp[0]],fill=outline,width=Q(w),joint='curve')
def glow(base,cx,cy,r,col,blur=18):
    g=Image.new('RGBA',base.size,(0,0,0,0));gd=D(g);el(gd,(cx-r,cy-r,cx+r,cy+r),col);base.alpha_composite(g.filter(ImageFilter.GaussianBlur(Q(blur))))
def finish(base,sz):return base.resize((sz,sz),Image.Resampling.LANCZOS)
def bolts(d,pts):
    for x,y in pts:el(d,(x-5,y-5,x+5,y+5),(105,120,123,255),(214,224,220,255),1);ln(d,[(x-2,y),(x+2,y)],(25,30,31,255),1)
def scratches(d,seed,n=8):
    r=random.Random(seed)
    for _ in range(n):
        x=r.randint(90,290);y=r.randint(90,290);l=r.randint(10,34);ln(d,[(x,y),(x+l,y+r.randint(-3,3))],(210,220,214,45),1)
def base_module(accent,seed=0):
    a=im();d=D(a);glow(a,192,205,73,accent[:-1]+(55,),18);rr(d,(72,72,312,310),42,(23,34,38,255),C['edge'],5);rr(d,(91,91,293,291),32,(48,61,65,255),(102,119,121,255),3);bolts(d,[(101,105),(283,105),(101,278),(283,278)]);scratches(d,seed,10);return a,d

def core():
    a=im();d=D(a);glow(a,192,192,118,(52,244,225,85),24);el(d,(45,45,339,339),(10,17,20,255),(101,119,121,255),7)
    for i in range(16):
        ang=2*math.pi*i/16;x=192+math.cos(ang)*136;y=192+math.sin(ang)*136;rr(d,(x-9,y-17,x+9,y+17),5,(73,89,94,255),C['edge'],2)
    el(d,(79,79,305,305),(37,51,55,255),C['edge'],4);el(d,(104,104,280,280),(7,25,29,255),C['cyan'],7);el(d,(133,133,251,251),(20,48,50,255),C['cyan2'],4);el(d,(157,157,227,227),(194,255,248,255),C['cyan'],4)
    for ang in [0,math.pi/2,math.pi,3*math.pi/2]:
        x=192+math.cos(ang)*99;y=192+math.sin(ang)*99;rr(d,(x-10,y-20,x+10,y+20),5,C['orange'],(255,205,100,255),2)
    scratches(d,1,14);return finish(a,384)

def module(kind):
    accent={'cannon':C['cyan'],'blade':C['red'],'armor':C['orange'],'battery':C['cyan'],'wheel':C['cyan'],'tesla':C['cyan'],'rocket':C['orange'],'shield':C['cyan'],'laser':C['cyan'],'shotgun':C['yellow'],'plasma':C['violet'],'mortar':C['yellow']}[kind]
    a,d=base_module(accent,hash(kind)&999)
    if kind=='cannon':
        rr(d,(150,50,234,220),18,(83,98,101,255),C['white'],4);rr(d,(164,31,220,77),10,(25,34,36,255),C['orange'],4);rr(d,(123,185,261,285),28,(28,41,45,255),C['edge'],4);el(d,(153,207,231,285),C['dark'],C['cyan'],6);el(d,(176,230,208,262),C['cyan2'])
    elif kind=='shotgun':
        rr(d,(112,196,272,285),28,(31,43,46,255),C['edge'],4)
        for x in [137,166,195,224,253]:rr(d,(x-10,56,x+10,204),8,(92,105,108,255),C['white'],2);el(d,(x-8,42,x+8,70),C['dark'],C['yellow'],3)
        rr(d,(135,224,249,270),14,(51,62,65,255),C['yellow'],3)
    elif kind=='plasma':
        poly(d,[(142,260),(107,220),(122,118),(160,81),(224,81),(262,118),(277,220),(242,260)],(37,47,54,255),C['edge'],5);glow(a,192,176,62,(180,96,255,90),13);el(d,(130,114,254,238),(18,23,34,255),C['violet'],7);el(d,(155,139,229,213),(96,50,142,255),C['violet'],4);el(d,(175,159,209,193),(236,219,255,255))
    elif kind=='mortar':
        rr(d,(104,182,280,292),30,(32,44,47,255),C['edge'],4);poly(d,[(138,195),(150,74),(234,74),(246,195)],(72,83,86,255),C['white'],4);el(d,(151,46,233,98),(20,25,26,255),C['yellow'],6);el(d,(171,66,213,88),(11,15,16,255));rr(d,(132,229,252,274),12,(55,67,69,255),C['orange'],3)
    elif kind=='blade':
        pts=[]
        for i in range(44):
            ang=-math.pi/2+i*2*math.pi/44;r=132 if i%2==0 else 101;pts.append((192+math.cos(ang)*r,192+math.sin(ang)*r))
        poly(d,pts,(183,195,194,255),C['white'],3);el(d,(102,102,282,282),(40,53,57,255),C['edge'],4);el(d,(132,132,252,252),C['dark'],C['red'],7);el(d,(166,166,218,218),(255,194,160,255),C['red'],3)
    elif kind=='armor':
        poly(d,[(91,85),(145,58),(253,58),(304,104),(296,259),(250,315),(132,303),(80,250),(76,132)],(55,68,72,255),C['edge'],6);poly(d,[(112,109),(154,88),(241,88),(274,120),(269,237),(235,273),(145,269),(110,235)],(28,40,44,255),(108,128,130,255),3);poly(d,[(149,141),(236,141),(258,192),(232,244),(149,244),(126,192)],(18,35,39,255),C['cyan'],4)
    elif kind=='battery':
        rr(d,(111,60,273,318),30,(29,40,43,255),C['edge'],5);rr(d,(137,97,247,279),18,(8,20,22,255),(78,101,102,255),3);glow(a,192,190,56,(65,241,220,85),12);rr(d,(149,114,235,265),14,(19,82,77,255),C['cyan'],4);poly(d,[(207,130),(170,188),(192,188),(176,248),(222,173),(199,173)],C['white']);rr(d,(138,45,166,73),5,C['orange']);rr(d,(218,45,246,73),5,(112,128,130,255))
    elif kind=='wheel':
        el(d,(62,62,322,322),(17,24,27,255),(93,107,110,255),7)
        for i in range(24):
            ang=2*math.pi*i/24;ln(d,[(192+math.cos(ang)*111,192+math.sin(ang)*111),(192+math.cos(ang)*129,192+math.sin(ang)*129)],(4,8,10,255),8)
        el(d,(108,108,276,276),(64,78,82,255),C['edge'],4);el(d,(145,145,239,239),(21,38,42,255),C['cyan'],6);el(d,(177,177,207,207),C['cyan2'])
    elif kind=='tesla':
        rr(d,(119,258,265,318),15,(45,58,62,255),C['edge'],4);ln(d,[(192,261),(192,83)],(184,199,200,255),10)
        for y,r in [(230,68),(194,60),(157,51),(122,42)]:el(d,(192-r,y-r*.38,192+r,y+r*.38),(0,0,0,0),C['cyan'],8)
        el(d,(170,58,214,102),C['white'],C['cyan'],5)
    elif kind=='rocket':
        rr(d,(83,102,301,284),34,(40,53,57,255),C['edge'],5)
        for cx,cy in [(139,154),(245,154),(139,232),(245,232)]:el(d,(cx-31,cy-31,cx+31,cy+31),(22,29,31,255),C['orange'],5);el(d,(cx-13,cy-13,cx+13,cy+13),C['red'])
        rr(d,(159,62,225,94),8,C['dark'],C['cyan'],3);ln(d,[(192,62),(192,39)],C['cyan'],5);poly(d,[(192,25),(179,46),(205,46)],C['cyan'])
    elif kind=='shield':
        a=im();d=D(a);glow(a,192,190,127,(64,244,225,65),25);poly(d,[(192,37),(311,84),(293,231),(192,340),(91,231),(73,84)],(36,108,103,230),C['cyan2'],7);poly(d,[(192,76),(274,109),(263,215),(192,292),(121,215),(110,109)],(11,29,33,255),(91,210,201,255),4);ln(d,[(192,134),(192,244)],C['white'],12);ln(d,[(137,189),(247,189)],C['white'],12)
    elif kind=='laser':
        poly(d,[(118,300),(95,258),(120,132),(157,98),(227,98),(264,132),(289,258),(266,300)],(34,48,52,255),C['edge'],5);poly(d,[(171,144),(178,47),(206,47),(213,144)],(174,189,189,255),C['white'],3);el(d,(144,177,240,273),C['dark'],C['cyan'],7);el(d,(171,204,213,246),C['cyan2']);glow(a,192,225,44,(65,242,225,80),10)
    return finish(a,384)

def enemy(kind):
    a=im();d=D(a);accent=C['red']
    if kind=='drone':poly(d,[(192,45),(270,80),(325,151),(309,251),(242,327),(142,318),(66,253),(57,151),(112,78)],(57,36,33,255),C['red'],6)
    elif kind=='heavy':poly(d,[(91,58),(293,58),(333,103),(333,281),(289,326),(95,326),(51,281),(51,103)],(63,38,30,255),C['red'],7)
    elif kind=='sniper':accent=C['yellow'];poly(d,[(192,39),(270,103),(305,226),(255,321),(129,321),(79,226),(114,103)],(47,36,28,255),accent,6);rr(d,(162,72,222,226),14,(38,48,50,255),C['edge'],4)
    elif kind=='rammer':poly(d,[(192,24),(328,192),(192,358),(56,192)],(62,31,27,255),C['red'],7);poly(d,[(192,77),(282,192),(192,307),(102,192)],(39,48,49,255),C['edge'],3)
    elif kind=='swarmer':
        accent=C['orange'];poly(d,[(192,45),(289,147),(257,278),(192,340),(127,278),(95,147)],(44,32,28,255),accent,6)
        for x in [126,258]:poly(d,[(x,135),(x+(-32 if x<192 else 32),192),(x,249)],(80,50,28,255),accent,3)
    elif kind=='bomber':
        accent=C['orange'];el(d,(58,58,326,326),(49,34,29,255),accent,7)
        for ang in [0,math.pi/2,math.pi,3*math.pi/2]:
            x=192+math.cos(ang)*116;y=192+math.sin(ang)*116;el(d,(x-27,y-27,x+27,y+27),(30,35,35,255),C['red'],4)
    elif kind=='scrapper':accent=C['yellow'];poly(d,[(66,108),(131,53),(253,53),(318,108),(294,285),(235,337),(149,337),(90,285)],(44,47,43,255),accent,6);poly(d,[(80,176),(31,154),(55,221)],(83,89,78,255),accent,4);poly(d,[(304,176),(353,154),(329,221)],(83,89,78,255),accent,4)
    else:raise ValueError(kind)
    el(d,(132,132,252,252),(23,21,20,255),accent,7);el(d,(168,168,216,216),(255,229,195,255),accent,2);bolts(d,[(116,112),(268,112),(116,272),(268,272)]);scratches(d,hash(kind)&999,8);return finish(a,384)

def boss(kind):
    a=im(448);d=D(a);cx=cy=224
    accent=[C['red'],C['cyan'],C['orange'],C['yellow'],C['blue']][kind]
    glow(a,cx,cy,162,accent[:-1]+(52,),25)
    if kind==0:
        pts=[]
        for i in range(16):
            ang=-math.pi/2+i*2*math.pi/16;r=178 if i%2==0 else 151;pts.append((cx+math.cos(ang)*r,cy+math.sin(ang)*r))
        poly(d,pts,(61,34,31,255),accent,8)
    elif kind==1:
        el(d,(43,43,405,405),(27,42,47,255),accent,9)
        for i in range(8):
            ang=i*math.pi/4;x=cx+math.cos(ang)*158;y=cy+math.sin(ang)*158;rr(d,(x-14,y-28,x+14,y+28),6,C['orange'])
    elif kind==2:
        poly(d,[(224,35),(367,91),(413,224),(367,357),(224,413),(81,357),(35,224),(81,91)],(54,40,27,255),accent,9);rr(d,(115,115,333,333),44,(33,44,47,255),C['edge'],5)
    elif kind==3:
        poly(d,[(224,31),(350,77),(417,180),(389,326),(287,414),(161,414),(59,326),(31,180),(98,77)],(49,45,31,255),accent,9)
        for x in [148,224,300]:rr(d,(x-22,52,x+22,205),10,(79,84,75,255),C['white'],3)
    elif kind==4:
        el(d,(42,42,406,406),(29,37,51,255),accent,9)
        for i in range(12):
            ang=i*math.pi/6;x=cx+math.cos(ang)*158;y=cy+math.sin(ang)*158;el(d,(x-20,y-20,x+20,y+20),(32,47,62,255),C['blue'],4)
    el(d,(126,126,322,322),(26,31,33,255),C['edge'],5);el(d,(161,161,287,287),(24,19,18,255),accent,9);el(d,(202,202,246,246),(255,231,198,255));scratches(d,100+kind,14);return finish(a,448)

def projectile(kind):
    a=im(128);d=D(a);cx=cy=64
    if kind=='bullet':glow(a,cx,cy,22,(65,255,235,120),10);rr(d,(53,18,75,110),10,(196,255,246,255),C['cyan'],3)
    elif kind=='rocket':poly(d,[(64,9),(91,49),(84,99),(64,119),(44,99),(37,49)],(78,83,80,255),C['white'],3);el(d,(50,35,78,63),C['red'],C['orange'],3);poly(d,[(44,90),(25,112),(48,107)],C['orange']);poly(d,[(84,90),(103,112),(80,107)],C['orange'])
    elif kind=='plasma':glow(a,cx,cy,40,(181,95,255,120),12);el(d,(25,25,103,103),(75,37,111,255),C['violet'],5);el(d,(46,46,82,82),(241,221,255,255))
    elif kind=='mortar':el(d,(28,23,100,95),(48,53,50,255),C['yellow'],5);rr(d,(48,77,80,118),8,C['orange']);ln(d,[(44,43),(84,43)],C['white'],4)
    else:glow(a,cx,cy,28,(255,78,49,100),9);poly(d,[(64,12),(100,64),(64,116),(28,64)],(94,31,26,255),C['red'],4);el(d,(51,51,77,77),(255,218,183,255))
    return finish(a,128)

def prop(kind):
    a=im(192);d=D(a)
    if kind=='crate':rr(d,(26,34,166,160),18,(60,66,62,255),C['edge'],4);rr(d,(43,51,149,143),10,(41,48,47,255),C['orange'],3);ln(d,[(50,60),(142,134)],(140,113,64,220),5);ln(d,[(142,60),(50,134)],(140,113,64,220),5)
    elif kind=='pipe':rr(d,(39,76,153,118),18,(78,91,91,255),C['edge'],4);el(d,(20,67,58,127),(43,51,52,255),C['edge'],4);el(d,(134,67,172,127),(43,51,52,255),C['edge'],4);rr(d,(80,54,113,140),8,(52,64,65,255),C['orange'],3)
    elif kind=='beacon':glow(a,96,82,44,(64,242,222,80),14);poly(d,[(96,18),(135,82),(117,161),(75,161),(57,82)],(35,47,50,255),C['cyan'],4);el(d,(75,61,117,103),C['cyan2'],C['cyan'],4);rr(d,(58,152,134,177),9,(51,62,64,255),C['edge'],3)
    else:poly(d,[(30,139),(55,49),(96,73),(121,33),(164,68),(147,155)],(61,54,43,255),C['yellow'],4);ln(d,[(51,126),(142,62)],(116,128,123,255),8);el(d,(70,91,103,124),(37,45,44,255),C['cyan'],3)
    return finish(a,192)

def floor(seed,theme):
    rnd=random.Random(seed);sz=512;pal=[((22,29,31),(44,57,58),(64,89,86),(38,96,89)),((30,27,23),(63,48,34),(105,69,41),(121,60,35)),((22,27,34),(43,55,70),(69,85,102),(54,85,120)),((39,31,25),(75,58,39),(112,80,49),(104,65,41)),((17,27,30),(28,66,66),(42,113,106),(35,161,146))][theme]
    out=Image.new('RGB',(sz,sz),pal[0]);d=ImageDraw.Draw(out)
    for y in range(0,sz,128):
        for x in range(0,sz,128):
            base=tuple(max(0,min(255,c+rnd.randint(-5,5))) for c in pal[0]);d.rectangle([x+2,y+2,x+125,y+125],fill=base,outline=pal[1],width=3);d.line([(x+8,y+8),(x+120,y+8)],fill=pal[2],width=2);d.line([(x+8,y+8),(x+8,y+120)],fill=pal[2],width=2)
            for bx,by in [(x+14,y+14),(x+114,y+14),(x+14,y+114),(x+114,y+114)]:d.ellipse([bx-3,by-3,bx+3,by+3],fill=(105,115,112))
            if rnd.random()<.78:
                sx=x+rnd.randint(20,106);sy=y+rnd.randint(20,106);r=rnd.randint(8,28);d.ellipse([sx-r,sy-r,sx+r,sy+r],fill=pal[3])
            for _ in range(2):
                sx=x+rnd.randint(15,110);sy=y+rnd.randint(15,110);d.line([(sx,sy),(sx+rnd.randint(15,46),sy+rnd.randint(-7,7))],fill=(148,151,143),width=1)
    return out

def pickup_glow():
    a=im(128);glow(a,64,64,46,(76,245,226,130),11);d=D(a);el(d,(34,34,94,94),(20,51,53,85),C['cyan'],4);return finish(a,128)

assets={
'core.png':core(),
'cannon.png':module('cannon'),'blade.png':module('blade'),'armor.png':module('armor'),'battery.png':module('battery'),'wheel.png':module('wheel'),'tesla.png':module('tesla'),'rocket.png':module('rocket'),'shield.png':module('shield'),'laser.png':module('laser'),'shotgun.png':module('shotgun'),'plasma.png':module('plasma'),'mortar.png':module('mortar'),
'drone.png':enemy('drone'),'heavy.png':enemy('heavy'),'sniper.png':enemy('sniper'),'rammer.png':enemy('rammer'),'swarmer.png':enemy('swarmer'),'bomber.png':enemy('bomber'),'scrapper.png':enemy('scrapper'),
'boss_crusher.png':boss(0),'boss_magnetar.png':boss(1),'boss_forge.png':boss(2),'boss_artillery.png':boss(3),'boss_storm.png':boss(4),
'floor_junkyard.png':floor(11,0),'floor_foundry.png':floor(22,1),'floor_reactor.png':floor(33,2),'floor_wasteland.png':floor(44,3),'floor_lab.png':floor(55,4),
'pickup_glow.png':pickup_glow(),'proj_bullet.png':projectile('bullet'),'proj_rocket.png':projectile('rocket'),'proj_plasma.png':projectile('plasma'),'proj_mortar.png':projectile('mortar'),'proj_enemy.png':projectile('enemy'),
'prop_crate.png':prop('crate'),'prop_pipe.png':prop('pipe'),'prop_beacon.png':prop('beacon'),'prop_scrap.png':prop('scrap')
}
for name,img in assets.items():img.save(OUT/name,optimize=True)
print('Generated',len(assets),'static v5 assets')
