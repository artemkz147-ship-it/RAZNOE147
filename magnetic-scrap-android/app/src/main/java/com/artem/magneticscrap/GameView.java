package com.artem.magneticscrap;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public final class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final float VW=1600f,VH=900f;
    private static final int MENU=0,PLAY=1,UPGRADE=2,GAMEOVER=3,HANGAR=4;
    private static final int CANNON=0,BLADE=1,ARMOR=2,BATTERY=3,WHEEL=4,TESLA=5,ROCKET=6,SHIELD=7,LASER=8;
    private static final String[] PART_NAMES={"ПУШКА","ЦИРКУЛЯРКА","БРОНЯ","БАТАРЕЯ","КОЛЁСА","TESLA","РАКЕТЫ","ЩИТ","ЛАЗЕР"};
    private static final String[] PART_SUB={"Автоматический огонь","Контактный урон","Больше корпуса","Быстрее оружие","Быстрее движение","Цепной разряд","Самонаведение + взрыв","Поглощает урон","Прожигает цель"};
    private static final String[] META_NAMES={"КОРПУС","МАГНИТ","МОЩНОСТЬ","ДВИГАТЕЛЬ","ЩИТ","УДАЧА"};

    private final SurfaceHolder holder;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng=new Random();
    private final SharedPreferences prefs;
    private final Object inputLock=new Object();
    private final ArrayList<Enemy> enemies=new ArrayList<>();
    private final ArrayList<Projectile> projectiles=new ArrayList<>();
    private final ArrayList<Part> parts=new ArrayList<>();
    private final ArrayList<Fx> fx=new ArrayList<>();

    private volatile boolean surfaceReady=false,hostResumed=false,running=false;
    private Thread thread;
    private int state=MENU;
    private float scale=1f,offX=0f,offY=0f;
    private float tapX=-1f,tapY=-1f;
    private int joyPointer=-1;
    private float joyStartX,joyStartY,joyX,joyY;
    private volatile boolean joyActive=false;

    private float px=VW*.5f,py=VH*.52f,heading=0f;
    private float hp=100,maxHp=100,shieldHp=0,maxShield=0;
    private float hitCd=0,shieldDelay=0,empCd=0,fireCd=0,teslaCd=0,rocketCd=0,laserCd=0;
    private int wave=1,score=0,kills=0,combo=0,bestCombo=0,spawnLeft=0;
    private float comboTimer=0,spawnTimer=0,waveClearTimer=0;
    private int[] choices={CANNON,ARMOR,WHEEL};
    private int rerolls=1,scrap=0;
    private final int[] meta=new int[6];
    private String toast="";
    private float toastTimer=0;

    public GameView(Context context){
        super(context);
        holder=getHolder(); holder.addCallback(this);
        setFocusable(true); setKeepScreenOn(true);
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeCap(Paint.Cap.ROUND);
        prefs=context.getSharedPreferences("magnetic_scrap_meta_v3",Context.MODE_PRIVATE);
        loadMeta();
    }

    private void loadMeta(){scrap=prefs.getInt("scrap",0);for(int i=0;i<meta.length;i++)meta[i]=prefs.getInt("m"+i,0);}
    private void saveMeta(){SharedPreferences.Editor e=prefs.edit().putInt("scrap",scrap);for(int i=0;i<meta.length;i++)e.putInt("m"+i,meta[i]);e.apply();}
    public void onHostResume(){hostResumed=true;tryStartThread();}
    public void onHostPause(){hostResumed=false;saveMeta();}
    @Override public void surfaceCreated(SurfaceHolder h){surfaceReady=true;tryStartThread();}
    @Override public void surfaceChanged(SurfaceHolder h,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder h){surfaceReady=false;stopThread();}

    private synchronized void tryStartThread(){
        if(!surfaceReady||!hostResumed||running)return;
        running=true;thread=new Thread(this,"MagneticScrapLoop");thread.start();
    }
    private synchronized void stopThread(){
        running=false;Thread t=thread;thread=null;
        if(t!=null&&t!=Thread.currentThread()){try{t.join(900);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    }
    @Override public void run(){
        long last=System.nanoTime();
        while(running){
            if(!surfaceReady||!hostResumed){sleep(40);last=System.nanoTime();continue;}
            long frameStart=System.nanoTime();
            float dt=Math.min(.033f,Math.max(.001f,(frameStart-last)/1_000_000_000f));last=frameStart;
            consumeTap();if(state==PLAY)update(dt);if(toastTimer>0)toastTimer-=dt;drawFrame();
            long remaining=16_666_667L-(System.nanoTime()-frameStart);
            if(remaining>0){try{Thread.sleep(remaining/1_000_000L,(int)(remaining%1_000_000L));}catch(InterruptedException e){Thread.currentThread().interrupt();}}
        }
    }
    private void sleep(long ms){try{Thread.sleep(ms);}catch(InterruptedException e){Thread.currentThread().interrupt();}}

    private void consumeTap(){
        float x,y;synchronized(inputLock){x=tapX;y=tapY;tapX=tapY=-1;}
        if(x<0)return;
        if(state==MENU){if(inRect(x,y,580,435,440,105))startRun();else if(inRect(x,y,580,570,440,90))state=HANGAR;}
        else if(state==HANGAR){if(inRect(x,y,55,55,180,70))state=MENU;else for(int i=0;i<6;i++){float bx=250+(i%3)*370,by=220+(i/3)*260;if(inRect(x,y,bx,by,320,200))buyMeta(i);}}
        else if(state==PLAY){float dx=x-1450,dy=y-755;if(dx*dx+dy*dy<10000)useEmp();}
        else if(state==UPGRADE){for(int i=0;i<3;i++){float bx=205+i*405;if(inRect(x,y,bx,260,360,330)){applyUpgrade(choices[i]);return;}}if(inRect(x,y,640,655,320,75)&&rerolls>0){rerolls--;rollChoices();toast="ВЫБОР ПЕРЕТАСОВАН";toastTimer=1.2f;}}
        else if(state==GAMEOVER){if(inRect(x,y,510,570,280,90))startRun();else if(inRect(x,y,810,570,280,90))state=MENU;}
    }

    private void startRun(){
        enemies.clear();projectiles.clear();parts.clear();fx.clear();px=VW*.5f;py=VH*.52f;heading=0;
        maxHp=100+meta[0]*15;hp=maxHp;maxShield=meta[4]*12;shieldHp=maxShield;
        wave=1;score=kills=combo=bestCombo=0;comboTimer=0;hitCd=shieldDelay=empCd=fireCd=teslaCd=rocketCd=laserCd=0;
        rerolls=1+meta[5]/3;addPart(CANNON);addPart(ARMOR);if(meta[4]>0)addPart(SHIELD);
        state=PLAY;startWave();toast="СОБИРАЙ ЛОМ • ПЕРЕЖИВИ ВОЛНУ";toastTimer=2.4f;
    }
    private void startWave(){waveClearTimer=0;spawnTimer=.2f;spawnLeft=(wave%5==0)?1:4+wave*2;toast=(wave%5==0?"⚠ БОСС • ":"")+"ВОЛНА "+wave;toastTimer=wave%5==0?2f:1.1f;}

    private void update(float dt){
        float jx=0,jy=0;if(joyActive){float dx=joyX-joyStartX,dy=joyY-joyStartY,len=(float)Math.sqrt(dx*dx+dy*dy);if(len>1){float m=Math.min(1,len/110f);jx=dx/len*m;jy=dy/len*m;}}
        float speed=270+meta[3]*16+countPart(WHEEL)*24;px+=jx*speed*dt;py+=jy*speed*dt;if(Math.abs(jx)+Math.abs(jy)>.08)heading=(float)Math.atan2(jy,jx)+1.5708f;px=clamp(px,85,VW-85);py=clamp(py,90,VH-80);
        if(empCd>0)empCd-=dt;if(hitCd>0)hitCd-=dt;if(shieldDelay>0)shieldDelay-=dt;else if(shieldHp<maxShield)shieldHp=Math.min(maxShield,shieldHp+18*dt);if(comboTimer>0){comboTimer-=dt;if(comboTimer<=0)combo=0;}
        spawnTimer-=dt;if(spawnLeft>0&&spawnTimer<=0){spawnEnemy();spawnLeft--;spawnTimer=wave%5==0?99:Math.max(.22f,.62f-wave*.012f);}
        updateEnemies(dt);updateWeapons(dt);updateProjectiles(dt);updateFx(dt);
        if(spawnLeft==0&&enemies.isEmpty()){waveClearTimer+=dt;if(waveClearTimer>1){rollChoices();state=UPGRADE;}}
        if(hp<=0&&state==PLAY){hp=0;state=GAMEOVER;int reward=Math.max(6,wave*4+kills/3+bestCombo);scrap+=reward;saveMeta();toast="+"+reward+" ЛОМА";toastTimer=3;}
    }

    private void spawnEnemy(){
        Enemy e=new Enemy();float side=rng.nextFloat();if(side<.25){e.x=-100;e.y=rng.nextFloat()*VH;}else if(side<.5){e.x=VW+100;e.y=rng.nextFloat()*VH;}else if(side<.75){e.x=rng.nextFloat()*VW;e.y=-100;}else{e.x=rng.nextFloat()*VW;e.y=VH+100;}
        if(wave%5==0){e.type=4;e.radius=92;e.maxHp=420+wave*70;e.speed=46+wave*.8f;}else{float r=rng.nextFloat();if(wave>=6&&r<.16){e.type=2;e.radius=36;e.maxHp=55+wave*8;e.speed=72;}else if(wave>=4&&r<.34){e.type=1;e.radius=48;e.maxHp=105+wave*11;e.speed=48;}else if(wave>=8&&r<.48){e.type=3;e.radius=34;e.maxHp=70+wave*7;e.speed=130;}else{e.type=0;e.radius=34;e.maxHp=42+wave*6;e.speed=78+wave*2;}if(wave>=3&&rng.nextFloat()<Math.min(.28f,.05f+wave*.012f)){e.elite=true;e.maxHp*=1.75;e.speed*=1.16;e.radius*=1.12;}}
        e.hp=e.maxHp;e.shootCd=.5f+rng.nextFloat();enemies.add(e);
    }

    private void updateEnemies(float dt){
        Iterator<Enemy> it=enemies.iterator();while(it.hasNext()){Enemy e=it.next();
            if(e.stun>0)e.stun-=dt;else{float dx=px-e.x,dy=py-e.y,d=Math.max(1,dist(px,py,e.x,e.y)),ux=dx/d,uy=dy/d;
                if(e.type==2&&d<520){if(d<350){e.x-=ux*e.speed*.45f*dt;e.y-=uy*e.speed*.45f*dt;}e.shootCd-=dt;if(e.shootCd<=0){enemyShot(e,ux,uy);e.shootCd=Math.max(.8f,1.55f-wave*.02f);}}
                else{float boost=(e.type==3&&e.shootCd<=0)?2:1;e.x+=ux*e.speed*boost*dt;e.y+=uy*e.speed*boost*dt;if(e.type==3){e.shootCd-=dt;if(e.shootCd<-1.2)e.shootCd=1.5f;}}
                if(d<e.radius+63){if(countPart(BLADE)>0)damageEnemy(e,(24+countPart(BLADE)*10)*dt);if(hitCd<=0){damagePlayer(e.type==4?24:e.type==1?18:12);hitCd=.55f;e.x-=ux*40;e.y-=uy*40;}}
            }
            if(e.flash>0)e.flash-=dt;if(e.hp<=0){killEnemy(e);it.remove();}
        }
    }
    private void enemyShot(Enemy e,float ux,float uy){Projectile q=new Projectile();q.hostile=true;q.x=e.x;q.y=e.y;q.vx=ux*260;q.vy=uy*260;q.damage=10+wave*.55f;q.life=5;q.radius=8;q.type=3;projectiles.add(q);}

    private void updateWeapons(float dt){
        int cannons=countPart(CANNON),bats=countPart(BATTERY),teslas=countPart(TESLA),rockets=countPart(ROCKET),lasers=countPart(LASER);float power=1+meta[2]*.08f;fireCd-=dt;teslaCd-=dt;rocketCd-=dt;laserCd-=dt;Enemy t=nearestEnemy(px,py);
        if(t!=null&&cannons>0&&fireCd<=0){fireCd=Math.max(.13f,.54f/(1+bats*.12f));int shots=Math.min(5,1+cannons/2);for(int i=0;i<shots;i++)bullet(t,(i-(shots-1)*.5f)*.08f,16*power+cannons*2.2f);}
        if(t!=null&&teslas>0&&teslaCd<=0){teslaCd=Math.max(.65f,1.25f-teslas*.08f-bats*.035f);tesla(teslas,18*power+teslas*5);}
        if(t!=null&&rockets>0&&rocketCd<=0){rocketCd=Math.max(.75f,1.9f-rockets*.12f-bats*.04f);rocket(t,34*power+rockets*6);}
        if(t!=null&&lasers>0&&laserCd<=0){laserCd=Math.max(.45f,1f-lasers*.07f-bats*.025f);damageEnemy(t,26*power+lasers*7);Fx f=new Fx();f.kind=2;f.x=px;f.y=py;f.x2=t.x;f.y2=t.y;f.life=f.max=.12f;fx.add(f);}
    }
    private void bullet(Enemy t,float spread,float damage){float a=(float)Math.atan2(t.y-py,t.x-px)+spread;Projectile q=new Projectile();q.x=px;q.y=py;q.vx=(float)Math.cos(a)*580;q.vy=(float)Math.sin(a)*580;q.damage=damage;q.life=2.2f;q.radius=6;projectiles.add(q);}
    private void rocket(Enemy t,float damage){float a=(float)Math.atan2(t.y-py,t.x-px);Projectile q=new Projectile();q.type=1;q.x=px;q.y=py;q.vx=(float)Math.cos(a)*280;q.vy=(float)Math.sin(a)*280;q.damage=damage;q.life=4;q.radius=11;q.splash=85;projectiles.add(q);}
    private void tesla(int count,float damage){float sx=px,sy=py;ArrayList<Enemy> hit=new ArrayList<>();for(int chain=0;chain<Math.min(5,1+count);chain++){Enemy best=null;float bd=chain==0?520:280;for(Enemy e:enemies){if(hit.contains(e))continue;float d=dist(sx,sy,e.x,e.y);if(d<bd){bd=d;best=e;}}if(best==null)break;damageEnemy(best,damage*(1-chain*.12f));Fx f=new Fx();f.kind=1;f.x=sx;f.y=sy;f.x2=best.x;f.y2=best.y;f.life=f.max=.18f;fx.add(f);hit.add(best);sx=best.x;sy=best.y;}}

    private void updateProjectiles(float dt){
        Iterator<Projectile> it=projectiles.iterator();while(it.hasNext()){Projectile q=it.next();q.life-=dt;if(q.life<=0){it.remove();continue;}if(q.type==1&&!q.hostile){Enemy t=nearestEnemy(q.x,q.y);if(t!=null){float a=(float)Math.atan2(t.y-q.y,t.x-q.x),tx=(float)Math.cos(a)*340,ty=(float)Math.sin(a)*340;q.vx+=(tx-q.vx)*Math.min(1,dt*2.8f);q.vy+=(ty-q.vy)*Math.min(1,dt*2.8f);}}q.x+=q.vx*dt;q.y+=q.vy*dt;
            if(q.hostile){if(dist(q.x,q.y,px,py)<q.radius+50){damagePlayer(q.damage);burst(q.x,q.y,Color.rgb(255,91,55),7);it.remove();}}
            else{Enemy hit=null;for(Enemy e:enemies)if(dist(q.x,q.y,e.x,e.y)<q.radius+e.radius){hit=e;break;}if(hit!=null){damageEnemy(hit,q.damage);if(q.type==1){for(Enemy e:enemies)if(dist(q.x,q.y,e.x,e.y)<q.splash)damageEnemy(e,q.damage*.45f);burst(q.x,q.y,Color.rgb(255,136,50),14);}else burst(q.x,q.y,Color.rgb(71,235,215),5);it.remove();}}
        }
    }
    private void updateFx(float dt){Iterator<Fx>it=fx.iterator();while(it.hasNext()){Fx f=it.next();f.life-=dt;if(f.kind==0){f.x+=f.vx*dt;f.y+=f.vy*dt;f.vx*=.96;f.vy*=.96;}if(f.life<=0)it.remove();}}
    private void damageEnemy(Enemy e,float d){e.hp-=d;e.flash=.08f;}
    private void killEnemy(Enemy e){int gain=e.type==4?80:e.elite?14:e.type==1?8:4;score+=gain*10;kills++;scrap+=e.type==4?8:e.elite?2:1;combo++;comboTimer=2.25f;bestCombo=Math.max(bestCombo,combo);burst(e.x,e.y,e.type==4?Color.rgb(255,85,50):Color.rgb(219,105,66),e.type==4?30:10);if(rng.nextFloat()<.38f+meta[5]*.02f&&parts.size()<24)addPart(randomPart());if(e.type==4){hp=Math.min(maxHp,hp+30);toast="БОСС УНИЧТОЖЕН • +8 ЛОМА";toastTimer=2;}}
    private void damagePlayer(float d){shieldDelay=2.2f;if(shieldHp>0){float used=Math.min(shieldHp,d);shieldHp-=used;d-=used;}if(d>0)hp-=d;burst(px,py,Color.rgb(255,86,62),8);}
    private void useEmp(){if(empCd>0)return;empCd=10;for(Enemy e:enemies){e.stun=Math.max(e.stun,2);damageEnemy(e,18+meta[2]*2);}Fx f=new Fx();f.kind=3;f.x=px;f.y=py;f.life=f.max=.5f;fx.add(f);toast="EMP";toastTimer=.8f;}

    private void rollChoices(){for(int i=0;i<3;i++){int v;do{v=randomPart();}while((i>0&&v==choices[0])||(i>1&&v==choices[1]));choices[i]=v;}}
    private int randomPart(){int max=wave<3?5:wave<5?6:wave<7?7:wave<9?8:9;return rng.nextInt(max);}
    private void applyUpgrade(int type){addPart(type);if(type==ARMOR){maxHp+=18;hp=Math.min(maxHp,hp+28);}if(type==SHIELD){maxShield+=28;shieldHp=maxShield;}wave++;state=PLAY;startWave();}
    private void addPart(int type){if(parts.size()>=24)return;Part s=new Part();s.type=type;int i=parts.size(),ring=i/8;s.angle=(i%8)*(float)Math.PI/4+ring*.22f;s.distance=92+ring*63;s.spin=rng.nextFloat()*6.28f;parts.add(s);}
    private int countPart(int type){int n=0;for(Part s:parts)if(s.type==type)n++;return n;}
    private void buyMeta(int i){int cost=metaCost(i);if(scrap<cost){toast="НЕ ХВАТАЕТ ЛОМА";toastTimer=1.2f;return;}scrap-=cost;meta[i]++;saveMeta();toast=META_NAMES[i]+" УЛУЧШЕН";toastTimer=1.2f;}
    private int metaCost(int i){int l=meta[i]+1;return 35*l*l+(i>=4?20:0);}
    private String metaDesc(int i){switch(i){case 0:return "+15 HP";case 1:return "радиус магнита";case 2:return "+8% урона";case 3:return "+16 скорости";case 4:return "+12 щита";default:return "лучше добыча";}}
    private Enemy nearestEnemy(float x,float y){Enemy best=null;float bd=99999;for(Enemy e:enemies){float d=dist(x,y,e.x,e.y);if(d<bd){bd=d;best=e;}}return best;}
    private void burst(float x,float y,int color,int n){for(int i=0;i<n;i++){Fx f=new Fx();f.kind=0;f.x=x;f.y=y;float a=rng.nextFloat()*6.283f,s=70+rng.nextFloat()*230;f.vx=(float)Math.cos(a)*s;f.vy=(float)Math.sin(a)*s;f.life=f.max=.25f+rng.nextFloat()*.45f;f.color=color;fx.add(f);}}

    private void drawFrame(){
        Canvas c=null;try{c=holder.lockCanvas();if(c==null)return;int w=c.getWidth(),h=c.getHeight();c.drawColor(Color.rgb(5,10,14));scale=Math.min(w/VW,h/VH);offX=(w-VW*scale)*.5f;offY=(h-VH*scale)*.5f;c.save();c.translate(offX,offY);c.scale(scale,scale);drawBackground(c);if(state==MENU)drawMenu(c);else if(state==HANGAR)drawHangar(c);else{drawWorld(c);if(state==UPGRADE)drawUpgrade(c);if(state==GAMEOVER)drawGameOver(c);}if(toastTimer>0)drawToast(c);c.restore();}catch(Throwable t){running=false;}finally{if(c!=null)holder.unlockCanvasAndPost(c);}
    }
    private void drawBackground(Canvas c){p.setShader(null);p.setColor(Color.rgb(10,17,20));c.drawRect(0,0,VW,VH,p);stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth(2);stroke.setColor(Color.argb(36,87,132,128));for(int x=0;x<=1600;x+=100)c.drawLine(x,0,x,VH,stroke);for(int y=0;y<=900;y+=100)c.drawLine(0,y,VW,y,stroke);p.setColor(Color.argb(30,214,139,48));for(int x=-40;x<1650;x+=220){c.save();c.rotate(-18,x,0);c.drawRect(x,-80,x+28,980,p);c.restore();}p.setShader(new LinearGradient(0,0,0,VH,Color.argb(20,34,255,224),Color.argb(105,0,0,0),Shader.TileMode.CLAMP));c.drawRect(0,0,VW,VH,p);p.setShader(null);}
    private void drawWorld(Canvas c){for(Fx f:fx)drawFx(c,f);for(Projectile q:projectiles)drawProjectile(c,q);for(Enemy e:enemies)drawEnemy(c,e);drawPlayer(c);drawHud(c);}
    private void drawPlayer(Canvas c){p.setColor(Color.argb(95,0,0,0));c.drawOval(new RectF(px-75,py+38,px+75,py+80),p);if(maxShield>0){float r=maxShield==0?0:shieldHp/maxShield;stroke.setStrokeWidth(5);stroke.setColor(Color.argb((int)(85+120*r),63,230,214));c.drawCircle(px,py,83+countPart(SHIELD)*2,stroke);}stroke.setStrokeWidth(2.5f);stroke.setColor(Color.argb(70,54,232,213));c.drawCircle(px,py,125+10*(float)Math.sin(System.nanoTime()/350000000.0),stroke);for(Part s:parts){float a=s.angle+heading*.18f,x=px+(float)Math.cos(a)*s.distance,y=py+(float)Math.sin(a)*s.distance;drawPartIcon(c,s.type,x,y,68,a+1.5708f);}drawCore(c,px,py,112,heading);}
    private void drawEnemy(Canvas c,Enemy e){float size=e.type==4?185:e.type==1?105:78;if(e.elite)size*=1.12;if(e.elite){stroke.setStrokeWidth(5);stroke.setColor(Color.rgb(246,178,52));c.drawCircle(e.x,e.y,size*.58f,stroke);}drawEnemyIcon(c,e,e.x,e.y,size);if(e.type==4||e.elite){float wr=e.type==4?150:62,r=clamp(e.hp/e.maxHp,0,1);p.setColor(Color.argb(150,0,0,0));c.drawRoundRect(e.x-wr/2,e.y-size*.62f,e.x+wr/2,e.y-size*.62f+10,5,5,p);p.setColor(e.type==4?Color.rgb(255,78,49):Color.rgb(245,177,56));c.drawRoundRect(e.x-wr/2,e.y-size*.62f,e.x-wr/2+wr*r,e.y-size*.62f+10,5,5,p);}}
    private void drawProjectile(Canvas c,Projectile q){p.setColor(q.hostile?Color.rgb(255,82,48):q.type==1?Color.rgb(255,149,52):Color.rgb(103,255,235));c.drawCircle(q.x,q.y,q.radius,p);p.setColor(Color.argb(70,255,255,255));c.drawCircle(q.x,q.y,q.radius*2.2f,p);}
    private void drawFx(Canvas c,Fx f){float r=clamp(f.life/f.max,0,1);if(f.kind==0){p.setColor((f.color&0x00ffffff)|((int)(255*r)<<24));c.drawCircle(f.x,f.y,2+6*r,p);}else if(f.kind==1){stroke.setColor(Color.argb((int)(230*r),88,255,236));stroke.setStrokeWidth(4+4*r);c.drawLine(f.x,f.y,f.x2,f.y2,stroke);}else if(f.kind==2){stroke.setColor(Color.argb((int)(245*r),102,255,236));stroke.setStrokeWidth(8*r+2);c.drawLine(f.x,f.y,f.x2,f.y2,stroke);}else{stroke.setColor(Color.argb((int)(210*r),80,255,233));stroke.setStrokeWidth(9*r+2);c.drawCircle(f.x,f.y,(1-r)*470,stroke);}}

    private void drawHud(Canvas c){panel(c,30,28,390,118,.68f);text(c,"КОРПУС",55,62,20,Color.LTGRAY,false,false);bar(c,55,76,320,18,hp/maxHp,Color.rgb(57,220,194));if(maxShield>0){text(c,"ЩИТ",55,120,17,Color.rgb(113,244,230),false,false);bar(c,110,106,265,13,maxShield==0?0:shieldHp/maxShield,Color.rgb(70,174,239));}panel(c,610,28,380,95,.62f);text(c,"ВОЛНА "+wave,650,75,28,Color.WHITE,true,false);text(c,"СЧЁТ  "+score,820,75,22,Color.rgb(233,186,95),false,false);panel(c,1200,28,365,118,.62f);text(c,"ЛОМ  "+scrap,1230,66,23,Color.rgb(235,180,74),true,false);text(c,"КОМБО x"+combo,1230,105,21,combo>=5?Color.rgb(255,113,74):Color.LTGRAY,false,false);p.setColor(Color.argb(190,12,31,37));c.drawCircle(1450,755,88,p);stroke.setStrokeWidth(6);stroke.setColor(empCd<=0?Color.rgb(66,237,215):Color.rgb(79,100,105));c.drawCircle(1450,755,88,stroke);text(c,"EMP",1450,748,25,Color.WHITE,true,true);text(c,empCd<=0?"ГОТОВ":String.format("%.1f",empCd),1450,782,16,Color.LTGRAY,false,true);if(joyActive){p.setColor(Color.argb(60,90,237,218));c.drawCircle(joyStartX,joyStartY,100,p);float dx=joyX-joyStartX,dy=joyY-joyStartY,l=(float)Math.sqrt(dx*dx+dy*dy);if(l>75){dx=dx/l*75;dy=dy/l*75;}p.setColor(Color.argb(130,90,237,218));c.drawCircle(joyStartX+dx,joyStartY+dy,34,p);}}
    private void drawMenu(Canvas c){panel(c,310,145,980,590,.82f);text(c,"MAGNETIC SCRAP",800,165,54,Color.WHITE,true,true);drawCore(c,800,305,210,0);text(c,"МАШИНА, КОТОРУЮ ТЫ СОБИРАЕШЬ НА ХОДУ",800,405,21,Color.rgb(127,218,207),false,true);button(c,580,435,440,105,"ИГРАТЬ",Color.rgb(44,205,183));button(c,580,570,440,90,"АНГАР • "+scrap+" ЛОМА",Color.rgb(177,126,56));text(c,"v3.0 FIX • SurfaceView runtime",800,707,17,Color.rgb(135,146,150),false,true);}
    private void drawHangar(Canvas c){panel(c,80,55,1440,790,.86f);button(c,55,55,180,70,"← НАЗАД",Color.rgb(72,102,108));text(c,"АНГАР",800,120,46,Color.WHITE,true,true);text(c,"ЛОМ: "+scrap,1320,118,24,Color.rgb(240,187,82),true,true);for(int i=0;i<6;i++){float x=250+(i%3)*370,y=220+(i/3)*260;panel(c,x,y,320,200,.75f);text(c,META_NAMES[i],x+160,y+45,26,Color.WHITE,true,true);text(c,"УРОВЕНЬ "+meta[i],x+160,y+82,18,Color.rgb(118,223,211),false,true);text(c,metaDesc(i),x+160,y+113,15,Color.LTGRAY,false,true);smallButton(c,x+55,y+140,210,48,"УЛУЧШИТЬ • "+metaCost(i),scrap>=metaCost(i)?Color.rgb(48,185,166):Color.rgb(77,82,84));}}
    private void drawUpgrade(Canvas c){p.setColor(Color.argb(185,3,8,11));c.drawRect(0,0,VW,VH,p);text(c,"ВЫБЕРИ МОДУЛЬ",800,150,43,Color.WHITE,true,true);text(c,"Следующая волна станет сложнее",800,195,18,Color.rgb(160,184,187),false,true);for(int i=0;i<3;i++){float x=205+i*405;int type=choices[i];panel(c,x,260,360,330,.92f);drawPartIcon(c,type,x+180,365,125,0);text(c,PART_NAMES[type],x+180,475,25,Color.WHITE,true,true);text(c,PART_SUB[type],x+180,514,15,Color.rgb(132,220,210),false,true);text(c,"НАЖАТЬ",x+180,565,16,Color.rgb(237,185,81),true,true);}smallButton(c,640,655,320,75,rerolls>0?"ПЕРЕТАСОВАТЬ  x"+rerolls:"ПЕРЕТАСОВКА НЕТ",rerolls>0?Color.rgb(67,120,129):Color.rgb(54,60,62));}
    private void drawGameOver(Canvas c){p.setColor(Color.argb(200,4,7,10));c.drawRect(0,0,VW,VH,p);panel(c,410,180,780,520,.93f);text(c,"МАШИНА РАЗОБРАНА",800,265,43,Color.rgb(255,105,74),true,true);text(c,"ВОЛНА "+wave,800,340,28,Color.WHITE,true,true);text(c,"СЧЁТ  "+score+"   •   УБИТО  "+kills,800,390,21,Color.LTGRAY,false,true);text(c,"ЛУЧШЕЕ КОМБО  x"+bestCombo,800,430,18,Color.rgb(235,184,81),false,true);button(c,510,570,280,90,"ЕЩЁ РАЗ",Color.rgb(44,199,178));button(c,810,570,280,90,"МЕНЮ",Color.rgb(89,105,110));}
    private void drawToast(Canvas c){float a=Math.min(1,toastTimer*2);p.setColor(Color.argb((int)(190*a),5,16,20));c.drawRoundRect(500,185,1100,245,24,24,p);text(c,toast,800,225,22,Color.argb((int)(255*a),255,255,255),true,true);}

    private void panel(Canvas c,float x,float y,float w,float h,float a){p.setColor(Color.argb((int)(225*a),9,20,24));c.drawRoundRect(x,y,x+w,y+h,24,24,p);stroke.setStrokeWidth(2);stroke.setColor(Color.argb((int)(130*a),70,173,164));c.drawRoundRect(x,y,x+w,y+h,24,24,stroke);}
    private void button(Canvas c,float x,float y,float w,float h,String s,int color){p.setColor(color);c.drawRoundRect(x,y,x+w,y+h,23,23,p);p.setColor(Color.argb(65,255,255,255));c.drawRoundRect(x+3,y+3,x+w-3,y+h*.48f,20,20,p);text(c,s,x+w/2,y+h*.61f,26,Color.WHITE,true,true);}
    private void smallButton(Canvas c,float x,float y,float w,float h,String s,int color){p.setColor(color);c.drawRoundRect(x,y,x+w,y+h,16,16,p);text(c,s,x+w/2,y+h*.63f,16,Color.WHITE,true,true);}
    private void bar(Canvas c,float x,float y,float w,float h,float ratio,int color){ratio=clamp(ratio,0,1);p.setColor(Color.rgb(31,42,46));c.drawRoundRect(x,y,x+w,y+h,h/2,h/2,p);p.setColor(color);c.drawRoundRect(x,y,x+w*ratio,y+h,h/2,h/2,p);}
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold,boolean center){p.setShader(null);p.setColor(color);p.setTextSize(size);p.setTypeface(bold?android.graphics.Typeface.DEFAULT_BOLD:android.graphics.Typeface.DEFAULT);p.setTextAlign(center?Paint.Align.CENTER:Paint.Align.LEFT);c.drawText(s,x,y,p);}

    private void drawCore(Canvas c,float x,float y,float size,float rot){c.save();c.translate(x,y);c.rotate((float)Math.toDegrees(rot));float r=size*.5f;p.setColor(Color.rgb(29,45,52));c.drawCircle(0,0,r,p);stroke.setStrokeWidth(Math.max(3,size*.035f));stroke.setColor(Color.rgb(126,153,159));c.drawCircle(0,0,r*.88f,stroke);p.setColor(Color.rgb(9,27,33));c.drawCircle(0,0,r*.62f,p);stroke.setColor(Color.rgb(52,231,210));stroke.setStrokeWidth(Math.max(3,size*.045f));c.drawCircle(0,0,r*.54f,stroke);p.setColor(Color.rgb(149,255,240));c.drawCircle(0,0,r*.20f,p);p.setColor(Color.rgb(209,139,48));for(int i=0;i<6;i++){float a=i*1.0472f,bx=(float)Math.cos(a)*r*.77f,by=(float)Math.sin(a)*r*.77f;c.save();c.translate(bx,by);c.rotate(i*60);c.drawRoundRect(-r*.12f,-r*.06f,r*.12f,r*.06f,r*.03f,r*.03f,p);c.restore();}c.restore();}
    private void drawPartIcon(Canvas c,int type,float x,float y,float size,float rot){c.save();c.translate(x,y);c.rotate((float)Math.toDegrees(rot));float r=size*.5f;p.setColor(Color.argb(85,0,0,0));c.drawCircle(5,7,r*.78f,p);if(type==CANNON){p.setColor(Color.rgb(55,70,76));c.drawRoundRect(-r*.40f,-r*.05f,r*.40f,r*.45f,r*.12f,r*.12f,p);p.setColor(Color.rgb(132,151,154));c.drawRoundRect(-r*.10f,-r*.75f,r*.10f,r*.05f,r*.06f,r*.06f,p);p.setColor(Color.rgb(52,226,204));c.drawCircle(0,r*.18f,r*.13f,p);}else if(type==BLADE){p.setColor(Color.rgb(123,139,143));c.drawCircle(0,0,r*.78f,p);stroke.setStrokeWidth(size*.09f);stroke.setColor(Color.rgb(205,216,216));c.drawCircle(0,0,r*.62f,stroke);p.setColor(Color.rgb(21,39,44));c.drawCircle(0,0,r*.28f,p);for(int i=0;i<8;i++){c.save();c.rotate(i*45);p.setColor(Color.rgb(198,207,207));c.drawRect(-r*.08f,-r*.88f,r*.08f,-r*.57f,p);c.restore();}}else if(type==ARMOR){Path q=new Path();q.moveTo(0,-r*.8f);q.lineTo(r*.68f,-r*.4f);q.lineTo(r*.55f,r*.67f);q.lineTo(0,r*.83f);q.lineTo(-r*.55f,r*.67f);q.lineTo(-r*.68f,-r*.4f);q.close();p.setColor(Color.rgb(70,84,88));c.drawPath(q,p);stroke.setStrokeWidth(size*.06f);stroke.setColor(Color.rgb(166,181,182));c.drawPath(q,stroke);}else if(type==BATTERY){p.setColor(Color.rgb(53,69,75));c.drawRoundRect(-r*.5f,-r*.72f,r*.5f,r*.72f,r*.16f,r*.16f,p);p.setColor(Color.rgb(43,225,199));c.drawRoundRect(-r*.29f,-r*.52f,r*.29f,r*.45f,r*.1f,r*.1f,p);}else if(type==WHEEL){p.setColor(Color.rgb(30,35,38));c.drawCircle(0,0,r*.82f,p);stroke.setStrokeWidth(size*.1f);stroke.setColor(Color.rgb(88,101,105));c.drawCircle(0,0,r*.6f,stroke);p.setColor(Color.rgb(55,226,205));c.drawCircle(0,0,r*.14f,p);}else if(type==TESLA){p.setColor(Color.rgb(51,65,72));c.drawRoundRect(-r*.45f,r*.36f,r*.45f,r*.72f,r*.08f,r*.08f,p);stroke.setStrokeWidth(size*.1f);stroke.setColor(Color.rgb(72,241,220));c.drawCircle(0,-r*.05f,r*.38f,stroke);}else if(type==ROCKET){p.setColor(Color.rgb(55,69,75));c.drawRoundRect(-r*.62f,-r*.55f,r*.62f,r*.65f,r*.14f,r*.14f,p);p.setColor(Color.rgb(218,137,48));for(int yy=-1;yy<=1;yy+=2)for(int xx=-1;xx<=1;xx+=2)c.drawCircle(xx*r*.3f,yy*r*.28f,r*.13f,p);}else if(type==SHIELD){Path q=new Path();q.moveTo(0,-r*.85f);q.lineTo(r*.64f,-r*.48f);q.lineTo(r*.52f,r*.42f);q.lineTo(0,r*.82f);q.lineTo(-r*.52f,r*.42f);q.lineTo(-r*.64f,-r*.48f);q.close();p.setColor(Color.rgb(29,137,139));c.drawPath(q,p);stroke.setStrokeWidth(size*.06f);stroke.setColor(Color.rgb(145,255,241));c.drawPath(q,stroke);}else{p.setColor(Color.rgb(45,61,67));c.drawRoundRect(-r*.38f,-r*.18f,r*.38f,r*.65f,r*.12f,r*.12f,p);stroke.setStrokeWidth(size*.07f);stroke.setColor(Color.rgb(87,244,225));c.drawLine(0,-r*.70f,0,r*.12f,stroke);}c.restore();}
    private void drawEnemyIcon(Canvas c,Enemy e,float x,float y,float size){c.save();c.translate(x,y);float r=size*.5f;int outer=e.type==4?Color.rgb(111,31,40):Color.rgb(108,44,36);if(e.elite)outer=Color.rgb(142,91,30);Path q=new Path();int points=e.type==4?12:8;for(int i=0;i<points;i++){float a=(float)(-Math.PI/2+i*2*Math.PI/points),rr=i%2==0?r*.92f:r*.72f,xx=(float)Math.cos(a)*rr,yy=(float)Math.sin(a)*rr;if(i==0)q.moveTo(xx,yy);else q.lineTo(xx,yy);}q.close();p.setColor(outer);c.drawPath(q,p);stroke.setStrokeWidth(Math.max(3,size*.055f));stroke.setColor(e.type==4?Color.rgb(229,100,77):Color.rgb(184,93,72));c.drawPath(q,stroke);p.setColor(Color.rgb(29,25,28));c.drawCircle(0,0,r*.52f,p);p.setColor(e.type==4?Color.rgb(255,105,71):Color.rgb(238,78,51));c.drawCircle(0,0,r*.25f,p);p.setColor(Color.rgb(255,230,214));c.drawCircle(0,0,r*.08f,p);c.restore();}

    @Override public boolean onTouchEvent(MotionEvent event){int action=event.getActionMasked(),index=event.getActionIndex();if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){float[] q=toVirtual(event.getX(index),event.getY(index));int id=event.getPointerId(index);if(state==PLAY&&q[0]<620&&q[1]>410&&joyPointer<0){joyPointer=id;joyStartX=joyX=q[0];joyStartY=joyY=q[1];joyActive=true;}else synchronized(inputLock){tapX=q[0];tapY=q[1];}}else if(action==MotionEvent.ACTION_MOVE){if(joyPointer>=0){int idx=event.findPointerIndex(joyPointer);if(idx>=0){float[] q=toVirtual(event.getX(idx),event.getY(idx));joyX=q[0];joyY=q[1];}}}else if(action==MotionEvent.ACTION_CANCEL){joyPointer=-1;joyActive=false;}else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){if(index<event.getPointerCount()){int id=event.getPointerId(index);if(id==joyPointer){joyPointer=-1;joyActive=false;}}}return true;}
    private float[] toVirtual(float sx,float sy){float sc=scale<=0?1:scale;return new float[]{(sx-offX)/sc,(sy-offY)/sc};}
    private static boolean inRect(float x,float y,float bx,float by,float bw,float bh){return x>=bx&&x<=bx+bw&&y>=by&&y<=by+bh;}
    private static float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}
    private static float dist(float x1,float y1,float x2,float y2){float dx=x2-x1,dy=y2-y1;return(float)Math.sqrt(dx*dx+dy*dy);}
    private static final class Enemy{float x,y,hp,maxHp,speed,radius,shootCd,stun,flash;int type;boolean elite;}
    private static final class Projectile{float x,y,vx,vy,damage,life,radius,splash;int type;boolean hostile;}
    private static final class Part{int type;float angle,distance,spin;}
    private static final class Fx{float x,y,x2,y2,vx,vy,life,max;int color,kind;}
}
