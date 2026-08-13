#!/usr/bin/env python3
import re, zlib, struct, math, random, pathlib
ROOT=pathlib.Path(__file__).resolve().parents[1]
DATA=(ROOT/'web'/'umk3-data.js').read_text(encoding='utf-8')
OUT=ROOT/'web'/'assets'

def rgb(h):
    h=h.lstrip('#'); return tuple(int(h[i:i+2],16) for i in (0,2,4))
def shade(c,a): return tuple(max(0,min(255,x+a)) for x in c)
def write_png(path,w,h,buf,alpha=False):
    path.parent.mkdir(parents=True,exist_ok=True);bpp=4 if alpha else 3
    raw=b''.join(b'\x00'+bytes(buf[y*w*bpp:(y+1)*w*bpp]) for y in range(h))
    def chunk(t,d):return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
    ct=6 if alpha else 2
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,ct,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
class C:
    def __init__(self,w,h,alpha=False,bg=(0,0,0,255)):
        self.w=w;self.h=h;self.bpp=4 if alpha else 3;self.buf=bytearray(w*h*self.bpp);self.clear(bg)
    def color(self,c):c=tuple(c);return c[:self.bpp] if len(c)>=self.bpp else c+(255,)*(self.bpp-len(c))
    def clear(self,c):
        row=bytes(self.color(c))*self.w
        for y in range(self.h):i=y*self.w*self.bpp;self.buf[i:i+len(row)]=row
    def rect(self,x,y,w,h,c):
        x=max(0,int(x));y=max(0,int(y));x2=min(self.w,int(x+w));y2=min(self.h,int(y+h));
        if x2<=x or y2<=y:return
        row=bytes(self.color(c))*(x2-x)
        for yy in range(y,y2):i=(yy*self.w+x)*self.bpp;self.buf[i:i+len(row)]=row
    def circle(self,cx,cy,r,c):
        cx=int(cx);cy=int(cy);r=max(1,int(r));rr=r*r
        for y in range(max(0,cy-r),min(self.h,cy+r+1)):
            dy=y-cy;span=int(math.sqrt(max(0,rr-dy*dy)));self.rect(cx-span,y,span*2+1,1,c)
    def ellipse(self,cx,cy,rx,ry,c):
        cx=int(cx);cy=int(cy);rx=max(1,int(rx));ry=max(1,int(ry))
        for y in range(max(0,cy-ry),min(self.h,cy+ry+1)):
            q=(y-cy)/ry;span=int(rx*math.sqrt(max(0,1-q*q)));self.rect(cx-span,y,span*2+1,1,c)
    def line(self,x0,y0,x1,y1,w,c):
        x0,y0,x1,y1=map(int,(x0,y0,x1,y1));dx=x1-x0;dy=y1-y0;n=max(abs(dx),abs(dy),1)
        for i in range(n+1):t=i/n;self.circle(round(x0+dx*t),round(y0+dy*t),max(1,w//2),c)
    def gradient(self,a,b):
        for y in range(self.h):
            t=y/max(1,self.h-1);c=tuple(int(a[i]*(1-t)+b[i]*t) for i in range(3));self.rect(0,y,self.w,1,c)

def fighters():
    p=re.compile(r"F\('([^']+)'\s*,\s*'([^']+)'\s*,\s*'(#[0-9A-Fa-f]{6})'\s*,\s*'(#[0-9A-Fa-f]{6})'\s*,\s*'([^']+)'",re.M)
    return [dict(id=m.group(1),name=m.group(2),primary=rgb(m.group(3)),dark=rgb(m.group(4)),style=m.group(5)) for m in p.finditer(DATA)]
def stages():
    p=re.compile(r"\{id:'([^']+)',name:(?:'([^']+)'|\"([^\"]+)\"),kind:'([^']+)',sky:\['(#[0-9A-Fa-f]{6})','(#[0-9A-Fa-f]{6})'\],accent:'(#[0-9A-Fa-f]{6})'",re.M)
    return [dict(id=m.group(1),kind=m.group(4),sky0=rgb(m.group(5)),sky1=rgb(m.group(6)),accent=rgb(m.group(7))) for m in p.finditer(DATA)]
POSES=[((120,220),(120,150),(120,92),(77,205),(170,194),(92,306),(150,306)),((120,218),(121,147),(120,89),(70,194),(176,207),(88,306),(156,304)),((120,221),(119,151),(121,94),(82,211),(164,185),(98,306),(146,306)),((118,218),(119,148),(120,91),(69,188),(175,211),(72,306),(161,306)),((122,216),(121,146),(121,90),(80,205),(181,182),(106,306),(177,303)),((118,219),(119,149),(119,92),(65,211),(164,190),(69,304),(152,306)),((120,250),(120,194),(120,145),(75,238),(168,224),(66,306),(173,306)),((120,215),(120,150),(121,92),(80,184),(169,180),(75,267),(172,255)),((120,211),(122,147),(123,90),(72,178),(176,193),(90,257),(180,276)),((120,219),(120,149),(120,91),(78,204),(214,145),(93,306),(150,306)),((118,220),(120,148),(120,91),(75,206),(216,117),(94,306),(151,306)),((116,226),(116,156),(115,99),(73,210),(170,192),(85,306),(215,202)),((120,247),(119,192),(119,143),(76,238),(175,219),(69,306),(217,291)),((108,222),(103,152),(94,95),(65,186),(161,219),(87,306),(153,306)),((120,220),(120,150),(120,92),(95,168),(148,151),(93,306),(150,306)),((120,218),(120,145),(120,86),(66,165),(187,165),(92,306),(150,306))]
def frame(cv,ox,oy,d,p,idx):
    pri,dark,st=d['primary'],d['dark'],d['style'];skin=(166,104,76);metal=(165,173,180)
    if st=='nightwolf':skin=(155,95,68)
    if st=='cyborg':skin=metal
    hip,ch,hd,lh,rh,lf,rf=p;P=lambda q:(ox+q[0],oy+q[1]);cv.ellipse(ox+120,oy+307,62 if st!='motaro' else 92,10,(0,0,0,130))
    if st=='motaro':
        cv.ellipse(ox+108,oy+235,82,44,shade(pri,-35)+(255,));cv.line(ox+62,oy+242,ox+51,oy+307,28,dark+(255,));cv.line(ox+145,oy+241,ox+170,oy+307,28,dark+(255,));cv.rect(ox+96,oy+112,55,92,pri+(255,));cv.circle(ox+122,oy+85,30,(145,86,58,255));cv.line(ox+102,oy+132,ox+65,oy+182,24,pri+(255,));cv.line(ox+145,oy+132,ox+186,oy+180,24,pri+(255,));return
    female=st=='femaleNinja';robot=st=='cyborg';four=st=='sheeva';leg=shade(dark,12);arm=metal if robot else skin
    cv.line(*P(hip),*P(lf),26 if female else 30,leg+(255,));cv.line(*P(hip),*P(rf),26 if female else 30,leg+(255,));cv.line(*P(ch),*P(lh),20 if female else 23,arm+(255,));cv.line(*P(ch),*P(rh),20 if female else 23,arm+(255,))
    if four:
        cv.line(ox+ch[0]-20,oy+ch[1]+15,ox+68,oy+177,18,skin+(255,));cv.line(ox+68,oy+177,ox+48,oy+220,17,skin+(255,));cv.line(ox+ch[0]+20,oy+ch[1]+15,ox+172,oy+177,18,skin+(255,));cv.line(ox+172,oy+177,ox+193,oy+220,17,skin+(255,))
    shoulder=31 if female else (42 if st=='shaokahn' else 37);cv.rect(ox+ch[0]-shoulder,oy+ch[1]-8,shoulder*2,max(20,hip[1]-ch[1]+14),pri+(255,))
    if st in ('ninja','femaleNinja','subzero'):cv.rect(ox+102,oy+160,36,47,shade(pri,20)+(255,));cv.rect(ox+106,oy+207,28,19,dark+(255,))
    elif st=='cyborg':cv.rect(ox+98,oy+151,44,60,shade(pri,20)+(255,));cv.rect(ox+107,oy+162,26,17,(22,27,30,255));cv.circle(ox+120,oy+170,5,(255,222,80,255))
    elif st=='jax':cv.rect(ox+86,oy+151,13,70,metal+(255,));cv.rect(ox+141,oy+151,13,70,metal+(255,))
    elif st=='kano':cv.line(ox+98,oy+151,ox+143,oy+210,8,(155,48,44,255))
    elif st=='sindel':cv.line(ox+105,oy+73,ox+65,oy+145,12,(225,225,232,255));cv.line(ox+135,oy+73,ox+175,oy+145,12,(225,225,232,255))
    elif st=='shaokahn':cv.rect(ox+88,oy+144,64,18,metal+(255,))
    cv.circle(ox+hd[0],oy+hd[1],29,(metal if robot else skin)+(255,))
    if st in ('ninja','femaleNinja','subzero'):
        cv.rect(ox+hd[0]-25,oy+hd[1]-5,50,22,pri+(255,));cv.rect(ox+hd[0]-26,oy+hd[1]-27,52,18,dark+(255,));cv.rect(ox+hd[0]-14,oy+hd[1]-9,10,3,(240,240,230,255));cv.rect(ox+hd[0]+4,oy+hd[1]-9,10,3,(240,240,230,255))
    elif st=='cyborg':cv.rect(ox+hd[0]-24,oy+hd[1]-24,48,44,shade(pri,10)+(255,));cv.rect(ox+hd[0]-13,oy+hd[1]-7,9,4,(255,245,100,255));cv.rect(ox+hd[0]+4,oy+hd[1]-7,9,4,(255,245,100,255))
    elif st=='kano':cv.circle(ox+hd[0]+10,oy+hd[1]-4,6,(255,45,45,255))
    elif st=='kunglao':cv.rect(ox+hd[0]-42,oy+hd[1]-31,84,8,(18,19,22,255));cv.rect(ox+hd[0]-20,oy+hd[1]-40,40,11,(22,23,25,255))
    elif st=='shaokahn':cv.rect(ox+hd[0]-28,oy+hd[1]-28,56,45,(175,177,180,255))
    cv.rect(ox+lf[0]-17,oy+lf[1]-9,34,13,dark+(255,));cv.rect(ox+rf[0]-17,oy+rf[1]-9,34,13,dark+(255,));cv.circle(ox+lh[0],oy+lh[1],10,(metal if robot else dark)+(255,));cv.circle(ox+rh[0],oy+rh[1],10,(metal if robot else dark)+(255,))
    rnd=random.Random(d['id']+str(idx))
    for _ in range(4000):x=ox+rnd.randrange(70,171);y=oy+rnd.randrange(85,285);cv.circle(x,y,1,shade(pri,rnd.randrange(-25,26))+(110,))
def fighter_atlas(d):
    cv=C(1920,640,True,(0,0,0,0))
    for i,p in enumerate(POSES):frame(cv,(i%8)*240,(i//8)*320,d,p,i)
    write_png(OUT/'fighters'/d['id']/'atlas.png',1920,640,cv.buf,True)
def stage(s):
    cv=C(1280,720,False);cv.gradient(s['sky0'],shade(s['sky1'],-18));k=s['kind'];ac=s['accent'];rnd=random.Random(s['id'])
    for _ in range(700):x=rnd.randrange(1280);y=rnd.randrange(560);br=rnd.randrange(20,90);cv.rect(x,y,1,1,tuple(min(255,s['sky1'][i]+br) for i in range(3)))
    if k=='subway':
        cv.rect(0,250,1280,330,(18,28,34))
        for x in range(-10,1280,198):cv.rect(x,274,42,276,(39,54,61));cv.rect(x+42,304,144,184,(15,22,26));cv.rect(x+56,322,116,120,(6,14,17));cv.rect(x+60,327,108,5,shade(ac,20))
        cv.rect(0,500,1280,48,(8,9,10))
    elif k in ('street','roof','water'):
        for i in range(10):
            x=i*142-40;h=170+(i%4)*62;cv.rect(x,490-h,128,h,(22+i%2*8,22+i%2*8,28+i%2*7))
            for yy in range(340,476,28):
                for xx in range(x+15,x+116,28):
                    if ((xx+yy+i*17)%7)<2:cv.rect(xx,yy,11,8,(160,123,55))
        cv.rect(0,455 if k=='roof' else 505,1280,100,(24,24,30));
        if k=='water':cv.rect(0,430,1280,150,(16,56,68))
    elif k=='bank':
        cv.rect(0,250,1280,315,(38,38,41));cv.rect(335,315,610,210,(15,16,18))
        for x in range(48,1280,212):cv.rect(x,282,36,265,(145,122,79));cv.rect(x-20,282,76,18,(191,164,103))
    elif k=='soul':
        cv.rect(0,240,1280,330,(12,34,20))
        for x in range(18,1280,178):cv.rect(x,250,126,286,(21,53,35));cv.rect(x+17,273,92,226,(6,24,14));cv.ellipse(x+63,390,25,72,(53,180,80))
    elif k=='tower':
        cv.rect(0,235,1280,340,(33,22,15))
        for x in range(38,1280,160):cv.rect(x,250,34,300,(60,40,25));cv.rect(x-14,250,62,17,(168,121,57))
        cv.circle(640,210,138,(116,83,41));cv.circle(640,210,101,(23,17,11))
    elif k=='temple':
        cv.rect(0,235,1280,340,(37,17,14))
        for x in range(0,1280,128):cv.rect(x,245,128,304,(50+(x//128%2)*10,23,18));cv.rect(x,245,7,304,(139,61,40))
    elif k=='grave':
        cv.rect(0,280,1280,300,(23,33,39))
        for i in range(15):x=i*91+18;y=510+(i%3)*17;cv.rect(x,y-76,45,76,(61,73,78));cv.circle(x+22,y-76,22,(61,73,78))
    elif k=='portal':
        cv.rect(0,345,1280,225,(23,29,50))
        for r in range(185,20,-22):
            for a in range(0,360,3):x=640+int(math.cos(math.radians(a))*r);y=320+int(math.sin(math.radians(a))*r);cv.circle(x,y,4,shade(ac,-20))
    elif k=='desert':
        cv.rect(0,455,1280,120,(201,135,74))
        for base,peakx,peaky,c in [(0,175,286,(101,57,35)),(260,566,224,(117,67,41)),(720,1055,252,(102,56,36))]:
            for y in range(peaky,465):t=(y-peaky)/(465-peaky);half=int(180*t);cv.rect(peakx-half,y,half*2,1,c)
    elif k in ('cave','hell'):
        cv.rect(0,0,1280,570,(47,9,7) if k=='hell' else (33,16,16))
        for x in range(-20,1280,86):
            h=75+(x%170+170)%170
            for y in range(0,h):half=int((1-y/max(1,h))*45);cv.rect(x+45-half,y,half*2,1,(42,11,8) if k=='hell' else (38,17,16))
    elif k in ('balcony','noob'):
        cv.rect(0,235,1280,335,(32,29,41))
        for x in range(35,1280,188):cv.rect(x,270,47,292,(71,64,90));cv.rect(x-15,270,77,17,(180,155,216))
        if k=='noob':cv.rect(0,0,1280,570,(4,4,6))
    elif k=='pit':
        cv.rect(0,275,1280,300,(42,23,22))
        for x in range(22,1280,138):cv.rect(x,290,22,270,(86,41,36));cv.rect(x-18,290,58,12,(153,62,52))
        for x in range(32,1280,86):
            cv.circle(x,558,22,(120,122,124))
            for a in range(0,360,60):cv.line(x,558,x+int(math.cos(math.radians(a))*58),558+int(math.sin(math.radians(a))*58),8,(165,168,170))
    cv.rect(0,594,1280,126,(24,25,27))
    for y in range(594,720,28):cv.rect(0,y,1280,1,(34,35,37))
    for _ in range(120000):
        x=rnd.randrange(1280);y=rnd.randrange(720);i=(y*1280+x)*3;d=rnd.randrange(-14,15)
        for q in range(3):cv.buf[i+q]=max(0,min(255,cv.buf[i+q]+d))
    write_png(OUT/'stages'/f"{s['id']}.png",1280,720,cv.buf,False)
def main():
    fs=fighters();ss=stages()
    if len(fs)<20 or len(ss)<10:raise SystemExit(f'parse failure fighters={len(fs)} stages={len(ss)}')
    for d in fs:fighter_atlas(d)
    for s in ss:stage(s)
    total=sum(p.stat().st_size for p in OUT.rglob('*.png'))
    print(f'ASSETS_OK fighters={len(fs)} stages={len(ss)} bytes={total}')
    if total<5_000_000:raise SystemExit('Raster pack unexpectedly small')
if __name__=='__main__':main()
