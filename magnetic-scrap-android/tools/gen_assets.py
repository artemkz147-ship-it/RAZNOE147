from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
import math, random

OUT = Path(__file__).resolve().parents[1] / "app/src/main/res/drawable-nodpi"
OUT.mkdir(parents=True, exist_ok=True)
S = 3

STEEL=(48,62,67,255); EDGE=(144,161,163,255); DARK=(10,20,23,255)
CYAN=(70,242,224,255); ORANGE=(235,145,45,255); RED=(230,83,53,255)


def cv(sz=320): return Image.new("RGBA", (sz*S, sz*S), (0,0,0,0))
def dr(im): return ImageDraw.Draw(im, "RGBA")
def q(v): return int(v*S)
def poly(d, pts, fill, outline=None, width=1):
    p=[(q(x),q(y)) for x,y in pts]; d.polygon(p, fill=fill)
    if outline: d.line(p+[p[0]], fill=outline, width=q(width), joint="curve")
def ell(d,b,fill,outline=None,width=1): d.ellipse(tuple(q(x) for x in b), fill=fill, outline=outline, width=q(width) if outline else 1)
def rr(d,b,r,fill,outline=None,width=1): d.rounded_rectangle(tuple(q(x) for x in b), radius=q(r), fill=fill, outline=outline, width=q(width) if outline else 1)
def line(d,pts,fill,width): d.line([(q(x),q(y)) for x,y in pts], fill=fill, width=q(width), joint="curve")
def finish(im, sz=320): return im.resize((sz,sz), Image.Resampling.LANCZOS)
def glow(im,cx,cy,r,color,blur=16):
    g=Image.new("RGBA", im.size, (0,0,0,0)); gd=dr(g); ell(gd,(cx-r,cy-r,cx+r,cy+r),color)
    im.alpha_composite(g.filter(ImageFilter.GaussianBlur(q(blur))))
def bolts(d, pts, r=5):
    for x,y in pts:
        ell(d,(x-r,y-r,x+r,y+r),(91,109,112,255),(183,197,196,255),1)
        line(d,[(x-2,y),(x+2,y)],(28,36,38,255),1)


def core():
    im=cv(); d=dr(im); glow(im,160,160,88,(44,240,220,75),18)
    ell(d,(54,54,266,266),(9,15,18,255),(79,96,99,255),5)
    for i in range(12):
        a=2*math.pi*i/12; x=160+math.cos(a)*96; y=160+math.sin(a)*96
        rr(d,(x-10,y-16,x+10,y+16),5,(70,84,89,255),(162,176,176,255),1)
    ell(d,(82,82,238,238),(29,40,44,255),(147,165,166,255),4)
    ell(d,(104,104,216,216),(8,25,29,255),CYAN,6)
    ell(d,(130,130,190,190),(185,255,247,255),CYAN,3)
    bolts(d,[(160,69),(251,160),(160,251),(69,160)])
    return finish(im)


def module(kind):
    im=cv(); d=dr(im); glow(im,160,170,55,(54,240,220,38),12)
    if kind=="cannon":
        poly(d,[(88,237),(72,201),(88,145),(119,119),(201,119),(232,145),(248,201),(232,237)],(31,43,48,255),EDGE,4)
        rr(d,(105,142,215,231),18,STEEL,EDGE,2)
        poly(d,[(140,143),(144,48),(176,48),(180,143)],(102,116,118,255),(220,230,226,255),3)
        rr(d,(148,35,172,58),5,ORANGE,(255,197,76,255),2)
        ell(d,(132,171,188,227),DARK,CYAN,4); ell(d,(146,185,174,213),(118,255,239,255))
        bolts(d,[(100,159),(220,159),(100,220),(220,220)])
    elif kind=="armor":
        poly(d,[(78,80),(124,58),(211,58),(247,91),(244,217),(208,255),(108,246),(72,211)],(40,53,58,255),EDGE,5)
        poly(d,[(91,95),(127,76),(197,76),(227,102),(224,196),(195,226),(119,220),(92,194)],(69,82,86,255),(110,127,129,255),2)
        poly(d,[(126,126),(194,126),(211,160),(191,197),(129,197),(111,160)],(25,39,44,255),CYAN,3)
        poly(d,[(78,183),(99,161),(119,168),(111,213),(87,220)],(128,61,37,210)); bolts(d,[(96,105),(223,113),(102,213),(218,205)])
    elif kind=="blade":
        pts=[]
        for i in range(36):
            a=-math.pi/2+i*math.pi/18; r=119 if i%2==0 else 92; pts.append((160+math.cos(a)*r,160+math.sin(a)*r))
        poly(d,pts,(179,193,193,255),(239,247,243,255),2)
        ell(d,(82,82,238,238),(35,48,52,255),(93,107,109,255),4); ell(d,(111,111,209,209),DARK,CYAN,5); ell(d,(143,143,177,177),(118,255,239,255))
    elif kind=="battery":
        rr(d,(86,52,234,265),28,(31,43,48,255),EDGE,5); rr(d,(110,83,210,232),15,(8,20,23,255),(64,89,92,255),3)
        glow(im,160,160,49,(55,241,218,80),10); rr(d,(122,100,198,218),12,(20,83,78,255),CYAN,3)
        poly(d,[(170,115),(143,163),(163,163),(151,207),(187,151),(168,151)],(230,255,247,255)); rr(d,(113,42,143,66),5,ORANGE); rr(d,(178,42,208,66),5,(104,121,123,255))
    elif kind=="wheel":
        ell(d,(49,49,271,271),(18,25,29,255),(91,101,104,255),5)
        for i in range(20):
            a=2*math.pi*i/20; line(d,[(160+math.cos(a)*98,160+math.sin(a)*98),(160+math.cos(a)*112,160+math.sin(a)*112)],(7,11,14,255),7)
        ell(d,(82,82,238,238),(66,77,79,255),(137,147,148,255),4); ell(d,(123,123,197,197),(25,39,43,255),CYAN,5); ell(d,(146,146,174,174),(120,255,241,255))
    elif kind=="tesla":
        rr(d,(106,208,214,256),12,(41,55,61,255),EDGE,4); line(d,[(160,211),(160,77)],(185,201,201,255),8)
        for y,r in [(180,56),(150,48),(120,40),(92,31)]: ell(d,(160-r,y-r*.45,160+r,y+r*.45),(0,0,0,0),CYAN,7)
        ell(d,(144,54,176,86),(210,255,250,255),CYAN,4); bolts(d,[(120,232),(200,232)])
    elif kind=="rocket":
        rr(d,(76,94,244,225),24,(42,55,59,255),EDGE,4)
        for cx,cy in [(120,135),(200,135),(120,185),(200,185)]:
            ell(d,(cx-23,cy-23,cx+23,cy+23),(24,31,34,255),ORANGE,4); ell(d,(cx-10,cy-10,cx+10,cy+10),(255,106,58,255))
        rr(d,(135,54,185,82),8,(8,21,24,255),CYAN,3); line(d,[(160,54),(160,38)],CYAN,4); poly(d,[(160,27),(150,44),(170,44)],CYAN)
    elif kind=="shield":
        glow(im,160,158,105,(52,239,222,55),18)
        poly(d,[(160,43),(248,80),(235,186),(160,266),(85,186),(72,80)],(38,109,105,235),(112,255,239,255),5)
        poly(d,[(160,69),(222,95),(212,176),(160,232),(108,176),(98,95)],(12,32,37,255),(90,211,201,255),3)
        line(d,[(160,110),(160,188)],(212,255,247,255),9); line(d,[(121,149),(199,149)],(212,255,247,255),9)
    elif kind=="laser":
        poly(d,[(104,236),(90,208),(107,130),(137,105),(183,105),(213,130),(230,208),(216,236)],(37,50,55,255),EDGE,4)
        poly(d,[(144,131),(151,52),(169,52),(176,131)],(175,189,189,255),(233,240,235,255),2)
        ell(d,(128,157,192,221),DARK,CYAN,5); ell(d,(146,175,174,203),(165,255,244,255)); glow(im,160,187,28,(57,239,222,65),8)
    return finish(im)


def enemy(kind):
    im=cv(); d=dr(im)
    if kind=="drone":
        poly(d,[(160,47),(226,77),(261,140),(246,214),(188,264),(115,255),(62,201),(59,126),(102,72)],(58,38,35,255),RED,5)
        poly(d,[(160,79),(210,100),(228,148),(216,198),(177,227),(125,218),(89,181),(89,130),(118,94)],(35,43,45,255),(107,119,120,255),2)
    elif kind=="heavy":
        poly(d,[(89,68),(231,68),(265,104),(265,216),(230,252),(90,252),(55,216),(55,104)],(64,38,31,255),RED,5)
        poly(d,[(105,91),(215,91),(238,115),(238,205),(211,229),(109,229),(82,205),(82,115)],(40,48,49,255),(104,113,114,255),3)
    elif kind=="sniper":
        poly(d,[(160,43),(220,93),(246,182),(207,244),(113,244),(74,182),(100,93)],(47,37,29,255),(235,161,54,255),5)
        poly(d,[(134,85),(186,85),(193,183),(127,183)],(35,44,46,255),(125,137,138,255),3)
    else:
        poly(d,[(160,29),(245,160),(160,276),(75,160)],(58,32,28,255),RED,5); poly(d,[(160,65),(216,160),(160,236),(104,160)],(42,49,49,255),(113,125,126,255),3)
    ell(d,(119,119,201,201),(22,19,18,255),RED if kind!="sniper" else ORANGE,6); ell(d,(145,145,175,175),(255,224,195,255))
    return finish(im)


def boss(kind):
    im=cv(384); d=dr(im); cx=cy=192
    if kind==0:
        pts=[]
        for i in range(12):
            a=-math.pi/2+2*math.pi*i/12; r=150 if i%2==0 else 132; pts.append((cx+math.cos(a)*r,cy+math.sin(a)*r))
        poly(d,pts,(64,35,31,255),RED,6); ell(d,(87,87,297,297),(32,38,39,255),(116,127,128,255),5); ell(d,(128,128,256,256),(31,18,17,255),RED,8); ell(d,(165,165,219,219),(255,224,196,255))
    elif kind==1:
        ell(d,(49,49,335,335),(28,42,46,255),CYAN,7); ell(d,(112,112,272,272),(16,31,35,255),CYAN,6); ell(d,(159,159,225,225),(198,255,247,255))
        for a in [0,math.pi/2,math.pi,3*math.pi/2]:
            x=cx+math.cos(a)*119; y=cy+math.sin(a)*119; rr(d,(x-12,y-20,x+12,y+20),5,ORANGE)
    else:
        poly(d,[(192,35),(305,83),(345,192),(305,301),(192,349),(79,301),(39,192),(79,83)],(52,39,26,255),(245,150,49,255),7)
        rr(d,(95,95,289,289),40,(35,44,46,255),(117,129,130,255),5); rr(d,(135,130,249,254),24,(27,20,15,255),(255,153,55,255),6)
        for y in [157,185,213]: line(d,[(150,y),(234,y)],(255,111,48,220),7)
    return finish(im,384)


def floor(seed,theme):
    rnd=random.Random(seed); sz=512; im=Image.new("RGB",(sz,sz),(20,27,29)); d=ImageDraw.Draw(im)
    pal=[((26,34,36),(49,61,62),(74,88,88)),((30,28,25),(61,47,36),(92,69,46)),((23,26,31),(46,54,67),(66,77,91))][theme]
    for y in range(0,sz,128):
        for x in range(0,sz,128):
            base=tuple(max(0,min(255,c+rnd.randint(-4,4))) for c in pal[0])
            d.rectangle([x+2,y+2,x+125,y+125],fill=base,outline=pal[1],width=3)
            d.line([(x+5,y+5),(x+122,y+5)],fill=pal[2],width=2); d.line([(x+5,y+5),(x+5,y+122)],fill=pal[2],width=2)
            for bx,by in [(x+12,y+12),(x+116,y+12),(x+12,y+116),(x+116,y+116)]: d.ellipse([bx-3,by-3,bx+3,by+3],fill=(100,110,108))
            if rnd.random()<.7:
                sx=x+rnd.randint(20,105); sy=y+rnd.randint(20,105); r=rnd.randint(9,27)
                stain=(87,47,28) if theme==1 else ((23,69,67) if theme==0 else (45,48,60)); d.ellipse([sx-r,sy-r,sx+r,sy+r],fill=stain)
    return im

assets={
    "core.png":core(),"cannon.png":module("cannon"),"armor.png":module("armor"),"blade.png":module("blade"),
    "battery.png":module("battery"),"wheel.png":module("wheel"),"tesla.png":module("tesla"),"rocket.png":module("rocket"),
    "shield.png":module("shield"),"laser.png":module("laser"),"drone.png":enemy("drone"),"heavy.png":enemy("heavy"),
    "sniper.png":enemy("sniper"),"rammer.png":enemy("rammer"),"boss_crusher.png":boss(0),"boss_magnetar.png":boss(1),
    "boss_forge.png":boss(2),"floor_junkyard.png":floor(100,0),"floor_foundry.png":floor(101,1),"floor_reactor.png":floor(102,2)
}
pick=Image.new("RGBA",(192,192),(0,0,0,0)); pd=ImageDraw.Draw(pick,"RGBA")
pd.ellipse([22,22,170,170],fill=(20,38,42,220),outline=(77,244,225,255),width=7)
pd.polygon([(96,42),(122,84),(108,84),(126,121),(96,150),(66,121),(84,121),(70,84),(84,84)],fill=(233,255,248,255))
assets["pickup_glow.png"]=pick

for name,image in assets.items(): image.save(OUT/name,optimize=True)
print("Generated",len(assets),"static game assets")
