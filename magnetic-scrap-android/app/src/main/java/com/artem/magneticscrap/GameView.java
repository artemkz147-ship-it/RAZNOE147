package com.artem.magneticscrap;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
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
    private static final String[] PART_NAMES={"ПУШКА","ПИЛА","БРОНЯ","БАТАРЕЯ","КОЛЁСА","TESLA","РАКЕТЫ","ЩИТ","ЛАЗЕР"};
    private static final String[] PART_SUB={"Автоогонь","Контактный урон","Больше корпуса","Скорострельность","Скорость","Цепной разряд","Взрыв по площади","Поглощает урон","Прожигает цель"};
    private static final String[] META_NAMES={"КОРПУС","МАГНИТ","МОЩНОСТЬ","ДВИГАТЕЛЬ","ЩИТ","УДАЧА"};

    private final SurfaceHolder holder;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng=new Random();
    private final Object inputLock=new Object();
    private final SharedPreferences prefs;
    private final Vibrator vibrator;

    private volatile boolean surfaceReady=false,hostResumed=false,running=false;
    private Thread thread;
    private float viewScale=1f,viewOffX=0f,viewOffY=0f;
    private int state=MENU;
    private float tapX=-1,tapY=-1;

    private int joyPointer=-1;
    private float joyBaseX=185,joyBaseY=735,joyX=185,joyY=735;
    private volatile boolean joyActive=false;
    private boolean joyUsesFixedBase=true;

    private Bitmap floorJunk,floorFoundry,floorReactor,core,cannon,blade,armor,battery,wheel,tesla,rocket,shield,laser;
    private Bitmap drone,heavy,sniper,rammer,bossCrusher,bossMagnetar,bossForge,pickupGlow;

    private final ArrayList<Enemy> enemies=new ArrayList<>();
    private final ArrayList<Projectile> projectiles=new ArrayList<>();
    private final ArrayList<Part> parts=new ArrayList<>();
    private final ArrayList<Pickup> pickups=new ArrayList<>();
    private final ArrayList<Fx> fx=new ArrayList<>();

    private float px=VW*.5f,py=VH*.53f,heading=-1.5708f;
    private float hp=100,maxHp=100,shieldHp=0,maxShield=0;
    private float hitCd=0,shieldDelay=0,empCd=0,fireCd=0,teslaCd=0,rocketCd=0,laserCd=0;
    private float cameraShake=0,damageFlash=0,tutorialTimer=0;
    private int wave=1,score=0,kills=0,combo=0,bestCombo=0,spawnLeft=0;
    private float comboTimer=0,spawnTimer=0,waveClearTimer=0;
    private int[] choices={CANNON,ARMOR,WHEEL};
    private int[] rarity={0,0,0};
    private int rerolls=1,scrap=0;
    private final int[] meta=new int[6];
    private String toast="";
    private float toastTimer=0;

    private static final float[][] SLOTS={
            {0,-104},{-86,-72},{86,-72},{-126,-8},{126,-8},{-104,70},{104,70},{0,112},
            {-175,-68},{175,-68},{-175,66},{175,66},{-88,145},{88,145},{-88,-147},{88,-147},
            {-225,0},{225,0},{0,185},{0,-188},{-160,135},{160,135},{-160,-135},{160,-135}
    };

    public GameView(Context context){
        super(context);
        holder=getHolder();holder.addCallback(this);
        setFocusable(true);setFocusableInTouchMode(true);setKeepScreenOn(true);
        stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeCap(Paint.Cap.ROUND);
        prefs=context.getSharedPreferences("magnetic_scrap_rebuild_v4",Context.MODE_PRIVATE);
        vibrator=(Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
        loadMeta();loadBitmaps();
    }

    private Bitmap decode(int id){return BitmapFactory.decodeResource(getResources(),id);}
    private void loadBitmaps(){
        floorJunk=decode(R.drawable.floor_junkyard);floorFoundry=decode(R.drawable.floor_foundry);floorReactor=decode(R.drawable.floor_reactor);
        core=decode(R.drawable.core);cannon=decode(R.drawable.cannon);blade=decode(R.drawable.blade);armor=decode(R.drawable.armor);battery=decode(R.drawable.battery);wheel=decode(R.drawable.wheel);tesla=decode(R.drawable.tesla);rocket=decode(R.drawable.rocket);shield=decode(R.drawable.shield);laser=decode(R.drawable.laser);
        drone=decode(R.drawable.drone);heavy=decode(R.drawable.heavy);sniper=decode(R.drawable.sniper);rammer=decode(R.drawable.rammer);
        bossCrusher=decode(R.drawable.boss_crusher);bossMagnetar=decode(R.drawable.boss_magnetar);bossForge=decode(R.drawable.boss_forge);pickupGlow=decode(R.drawable.pickup_glow);
    }
    private void loadMeta(){scrap=prefs.getInt("scrap",0);for(int i=0;i<meta.length;i++)meta[i]=prefs.getInt("m"+i,0);}
    private void saveMeta(){SharedPreferences.Editor e=prefs.edit().putInt("scrap",scrap);for(int i=0;i<meta.length;i++)e.putInt("m"+i,meta[i]);e.apply();}

    public void onHostResume(){hostResumed=true;tryStartThread();}
    public void onHostPause(){hostResumed=false;joyPointer=-1;joyActive=false;saveMeta();}
    @Override public void surfaceCreated(SurfaceHolder h){surfaceReady=true;tryStartThread();}
    @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int ht){}
    @Override public void surfaceDestroyed(SurfaceHolder h){surfaceReady=false;stopThread();}
    private synchronized void tryStartThread(){if(!surfaceReady||!hostResumed||running)return;running=true;thread=new Thread(this,"MagneticScrapV4");thread.start();}
    private synchronized void stopThread(){running=false;Thread t=thread;thread=null;if(t!=null&&t!=Thread.currentThread()){try{t.join(900);}catch(InterruptedException e){Thread.currentThread().interrupt();}}}

    @Override public void run(){
        long last=System.nanoTime();
        while(running){
            if(!surfaceReady||!hostResumed){sleep(40);last=System.nanoTime();continue;}
            long frame=System.nanoTime();float dt=Math.min(.033f,Math.max(.001f,(frame-last)/1_000_000_000f));last=frame;
            consumeTap();if(state==PLAY)update(dt);if(toastTimer>0)toastTimer-=dt;if(damageFlash>0)damageFlash-=dt;if(tutorialTimer>0)tutorialTimer-=dt;drawFrame();
            long rest=16_666_667L-(System.nanoTime()-frame);if(rest>0){try{Thread.sleep(rest/1_000_000L,(int)(rest%1_000_000L));}catch(InterruptedException e){Thread.currentThread().interrupt();}}
        }
    }
    private void sleep(long ms){try{Thread.sleep(ms);}catch(InterruptedException e){Thread.currentThread().interrupt();}}

    private void consumeTap(){
        float x,y;synchronized(inputLock){x=tapX;y=tapY;tapX=tapY=-1;}if(x<0)return;
        if(state==MENU){if(inRect(x,y,550,485,500,105))startRun();else if(inRect(x,y,550,615,500,80))state=HANGAR;}
        else if(state==HANGAR){if(inRect(x,y,45,40,190,70))state=MENU;else for(int i=0;i<6;i++){float bx=245+(i%3)*375,by=215+(i/3)*270;if(inRect(x,y,bx,by,330,210))buyMeta(i);}}
        else if(state==PLAY){float dx=x-1430,dy=y-735;if(dx*dx+dy*dy<118*118)useEmp();}
        else if(state==UPGRADE){for(int i=0;i<3;i++){float bx=195+i*410;if(inRect(x,y,bx,245,380,365)){applyUpgrade(choices[i],rarity[i]);return;}}if(inRect(x,y,625,660,350,76)&&rerolls>0){rerolls--;rollChoices();toast="ВЫБОР ОБНОВЛЁН";toastTimer=1.1f;}}
        else if(state==GAMEOVER){if(inRect(x,y,485,585,300,92))startRun();else if(inRect(x,y,815,585,300,92))state=MENU;}
    }

    private void startRun(){
        enemies.clear();projectiles.clear();parts.clear();pickups.clear();fx.clear();px=VW*.5f;py=VH*.53f;heading=-1.5708f;
        maxHp=115+meta[0]*18;hp=maxHp;maxShield=meta[4]*14;shieldHp=maxShield;
        wave=1;score=kills=combo=bestCombo=0;comboTimer=0;hitCd=shieldDelay=empCd=fireCd=teslaCd=rocketCd=laserCd=0;cameraShake=0;
        rerolls=1+meta[5]/3;addPart(CANNON);addPart(ARMOR);addPart(WHEEL);if(meta[4]>0)addPart(SHIELD);
        state=PLAY;tutorialTimer=6.5f;startWave();toast="ПРИТЯГИВАЙ ДЕТАЛИ • СОБИРАЙ МАШИНУ";toastTimer=2.4f;
    }
    private void startWave(){waveClearTimer=0;spawnTimer=.25f;spawnLeft=(wave%5==0)?1:5+wave*2;toast=wave%5==0?"⚠ БОСС • ВОЛНА "+wave:"ВОЛНА "+wave;toastTimer=wave%5==0?2.1f:1.15f;}

    private void update(float dt){
        float jx=0,jy=0;if(joyActive){float dx=joyX-joyBaseX,dy=joyY-joyBaseY,len=(float)Math.sqrt(dx*dx+dy*dy);if(len>8){float m=Math.min(1,len/95f);jx=dx/len*m;jy=dy/len*m;}}
        float speed=285+meta[3]*18+countPart(WHEEL)*17;px+=jx*speed*dt;py+=jy*speed*dt;if(Math.abs(jx)+Math.abs(jy)>.06f)heading=(float)Math.atan2(jy,jx);px=clamp(px,90,VW-90);py=clamp(py,100,VH-90);
        if(empCd>0)empCd-=dt;if(hitCd>0)hitCd-=dt;if(shieldDelay>0)shieldDelay-=dt;else if(shieldHp<maxShield)shieldHp=Math.min(maxShield,shieldHp+(16+countPart(SHIELD)*3)*dt);if(comboTimer>0){comboTimer-=dt;if(comboTimer<=0)combo=0;}cameraShake=Math.max(0,cameraShake-30*dt);
        spawnTimer-=dt;if(spawnLeft>0&&spawnTimer<=0){spawnEnemy();spawnLeft--;spawnTimer=wave%5==0?99:Math.max(.24f,.65f-wave*.013f);}
        updateEnemies(dt);updatePickups(dt);updateWeapons(dt);updateProjectiles(dt);updateFx(dt);
        if(spawnLeft==0&&enemies.isEmpty()){waveClearTimer+=dt;if(waveClearTimer>1.1f){rollChoices();state=UPGRADE;joyActive=false;joyPointer=-1;}}
        if(hp<=0&&state==PLAY){hp=0;state=GAMEOVER;joyActive=false;joyPointer=-1;int reward=Math.max(10,wave*5+kills/4+bestCombo);scrap+=reward;saveMeta();toast="+"+reward+" ЛОМА";toastTimer=3;haptic(55);}
    }

    private void spawnEnemy(){
        Enemy e=new Enemy();float side=rng.nextFloat();if(side<.25){e.x=-110;e.y=rng.nextFloat()*VH;}else if(side<.5){e.x=VW+110;e.y=rng.nextFloat()*VH;}else if(side<.75){e.x=rng.nextFloat()*VW;e.y=-110;}else{e.x=rng.nextFloat()*VW;e.y=VH+110;}
        if(wave%5==0){e.type=4+((wave/5-1)%3);e.radius=96;e.maxHp=520+wave*85;e.speed=48+wave*.8f;e.phase=rng.nextFloat()*6.28f;}
        else{float r=rng.nextFloat();if(wave>=7&&r<.17){e.type=2;e.radius=35;e.maxHp=60+wave*8;e.speed=75;}else if(wave>=4&&r<.36){e.type=1;e.radius=48;e.maxHp=110+wave*12;e.speed=51;}else if(wave>=9&&r<.52){e.type=3;e.radius=36;e.maxHp=76+wave*8;e.speed=123;}else{e.type=0;e.radius=35;e.maxHp=45+wave*7;e.speed=82+wave*1.8f;}if(wave>=3&&rng.nextFloat()<Math.min(.3f,.05f+wave*.013f)){e.elite=true;e.maxHp*=1.8f;e.speed*=1.12f;e.radius*=1.1f;}}
        e.hp=e.maxHp;e.shootCd=.6f+rng.nextFloat();enemies.add(e);
    }

    private void updateEnemies(float dt){
        Iterator<Enemy> it=enemies.iterator();while(it.hasNext()){Enemy e=it.next();e.phase+=dt;
            if(e.stun>0)e.stun-=dt;else{
                float dx=px-e.x,dy=py-e.y,d=Math.max(1,dist(px,py,e.x,e.y)),ux=dx/d,uy=dy/d;
                if(e.type==2&&d<560){if(d<370){e.x-=ux*e.speed*.5f*dt;e.y-=uy*e.speed*.5f*dt;}e.shootCd-=dt;if(e.shootCd<=0){enemyShot(e,ux,uy);e.shootCd=Math.max(.82f,1.6f-wave*.022f);}}
                else if(e.type==3){e.shootCd-=dt;float boost=e.shootCd<0?2.5f:1f;e.x+=ux*e.speed*boost*dt;e.y+=uy*e.speed*boost*dt;if(e.shootCd<-0.7f)e.shootCd=1.45f;}
                else if(e.type==5){float tx=-uy,ty=ux;e.x+=(ux*.65f+tx*(float)Math.sin(e.phase)*.35f)*e.speed*dt;e.y+=(uy*.65f+ty*(float)Math.sin(e.phase)*.35f)*e.speed*dt;for(Pickup q:pickups){float pd=dist(e.x,e.y,q.x,q.y);if(pd<330&&pd>4){q.vx+=(e.x-q.x)/pd*95*dt;q.vy+=(e.y-q.y)/pd*95*dt;}}}
                else if(e.type==6){e.x+=ux*e.speed*.7f*dt;e.y+=uy*e.speed*.7f*dt;e.shootCd-=dt;if(e.shootCd<=0&&enemies.size()<18){spawnMinionNear(e.x,e.y);e.shootCd=2.4f;}}
                else {e.x+=ux*e.speed*dt;e.y+=uy*e.speed*dt;}
                if(d<e.radius+62){if(countPart(BLADE)>0)damageEnemy(e,(27+countPart(BLADE)*9)*dt);if(hitCd<=0){damagePlayer(e.type>=4?25:(e.type==1?18:12));hitCd=.52f;e.x-=ux*46;e.y-=uy*46;}}
            }
            if(e.flash>0)e.flash-=dt;if(e.hp<=0){killEnemy(e);it.remove();}
        }
    }
    private void spawnMinionNear(float x,float y){Enemy m=new Enemy();m.type=0;m.radius=33;m.maxHp=36+wave*5;m.hp=m.maxHp;m.speed=90;m.x=x+rng.nextFloat()*120-60;m.y=y+rng.nextFloat()*120-60;enemies.add(m);}
    private void enemyShot(Enemy e,float ux,float uy){Projectile q=new Projectile();q.hostile=true;q.x=e.x;q.y=e.y;q.vx=ux*285;q.vy=uy*285;q.damage=10+wave*.6f;q.life=5;q.radius=9;q.type=3;projectiles.add(q);}

    private void updatePickups(float dt){
        float magnet=235+meta[1]*24+countPart(BATTERY)*5;
        Iterator<Pickup> it=pickups.iterator();while(it.hasNext()){Pickup q=it.next();q.life-=dt;if(q.life<=0){it.remove();continue;}q.spin+=dt*2.4f;float d=dist(q.x,q.y,px,py);if(d<magnet){float ux=(px-q.x)/Math.max(1,d),uy=(py-q.y)/Math.max(1,d);float pull=140+(1-d/magnet)*600;q.vx+=ux*pull*dt;q.vy+=uy*pull*dt;q.magnet=true;}else q.magnet=false;q.vx*=.985f;q.vy*=.985f;q.x+=q.vx*dt;q.y+=q.vy*dt;if(d<62){addPart(q.type);particleBurst(q.x,q.y,Color.rgb(76,245,226),13);toast=PART_NAMES[q.type]+" ПОДКЛЮЧЕНА";toastTimer=.85f;haptic(22);it.remove();}}
    }

    private void updateWeapons(float dt){
        int cannons=countPart(CANNON),bats=countPart(BATTERY),teslas=countPart(TESLA),rockets=countPart(ROCKET),lasers=countPart(LASER);float power=1+meta[2]*.09f;fireCd-=dt;teslaCd-=dt;rocketCd-=dt;laserCd-=dt;Enemy t=nearestEnemy(px,py);
        if(t!=null&&cannons>0&&fireCd<=0){fireCd=Math.max(.12f,.52f/(1+bats*.1f));int shots=Math.min(6,1+cannons/2);for(int i=0;i<shots;i++)bullet(t,(i-(shots-1)*.5f)*.065f,16*power+cannons*2.4f);}
        if(t!=null&&teslas>0&&teslaCd<=0){teslaCd=Math.max(.58f,1.2f-teslas*.075f-bats*.03f);teslaZap(teslas,20*power+teslas*5);}
        if(t!=null&&rockets>0&&rocketCd<=0){rocketCd=Math.max(.68f,1.75f-rockets*.11f-bats*.035f);playerRocket(t,36*power+rockets*6);}
        if(t!=null&&lasers>0&&laserCd<=0){laserCd=Math.max(.38f,.92f-lasers*.06f-bats*.02f);damageEnemy(t,28*power+lasers*7);Fx f=new Fx();f.kind=2;f.x=px;f.y=py;f.x2=t.x;f.y2=t.y;f.life=f.max=.14f;fx.add(f);}
    }
    private void bullet(Enemy t,float spread,float damage){float a=(float)Math.atan2(t.y-py,t.x-px)+spread;Projectile q=new Projectile();q.x=px;q.y=py;q.vx=(float)Math.cos(a)*650;q.vy=(float)Math.sin(a)*650;q.damage=damage;q.life=2;q.radius=6;q.type=0;projectiles.add(q);}
    private void playerRocket(Enemy t,float damage){float a=(float)Math.atan2(t.y-py,t.x-px);Projectile q=new Projectile();q.type=1;q.x=px;q.y=py;q.vx=(float)Math.cos(a)*300;q.vy=(float)Math.sin(a)*300;q.damage=damage;q.life=4;q.radius=12;q.splash=95;projectiles.add(q);}
    private void teslaZap(int count,float damage){float sx=px,sy=py;ArrayList<Enemy> hit=new ArrayList<>();for(int chain=0;chain<Math.min(6,1+count);chain++){Enemy best=null;float bd=chain==0?530:300;for(Enemy e:enemies){if(hit.contains(e))continue;float d=dist(sx,sy,e.x,e.y);if(d<bd){bd=d;best=e;}}if(best==null)break;damageEnemy(best,damage*(1-chain*.1f));Fx f=new Fx();f.kind=1;f.x=sx;f.y=sy;f.x2=best.x;f.y2=best.y;f.life=f.max=.19f;fx.add(f);hit.add(best);sx=best.x;sy=best.y;}}

    private void updateProjectiles(float dt){
        Iterator<Projectile> it=projectiles.iterator();while(it.hasNext()){Projectile q=it.next();q.life-=dt;if(q.life<=0){it.remove();continue;}if(q.type==1&&!q.hostile){Enemy t=nearestEnemy(q.x,q.y);if(t!=null){float a=(float)Math.atan2(t.y-q.y,t.x-q.x);float tx=(float)Math.cos(a)*370,ty=(float)Math.sin(a)*370;q.vx+=(tx-q.vx)*Math.min(1,dt*3);q.vy+=(ty-q.vy)*Math.min(1,dt*3);}}q.x+=q.vx*dt;q.y+=q.vy*dt;
            if(q.hostile){if(dist(q.x,q.y,px,py)<q.radius+49){damagePlayer(q.damage);particleBurst(q.x,q.y,Color.rgb(255,88,54),8);it.remove();}}
            else{Enemy hit=null;for(Enemy e:enemies){if(dist(q.x,q.y,e.x,e.y)<q.radius+e.radius){hit=e;break;}}if(hit!=null){damageEnemy(hit,q.damage);if(q.type==1){for(Enemy e:enemies)if(dist(q.x,q.y,e.x,e.y)<q.splash)damageEnemy(e,q.damage*.45f);particleBurst(q.x,q.y,Color.rgb(255,150,50),18);cameraShake=Math.max(cameraShake,8);}else particleBurst(q.x,q.y,Color.rgb(70,235,219),5);it.remove();}}
        }
    }
    private void updateFx(float dt){Iterator<Fx>it=fx.iterator();while(it.hasNext()){Fx f=it.next();f.life-=dt;if(f.kind==0){f.x+=f.vx*dt;f.y+=f.vy*dt;f.vx*=.96f;f.vy*=.96f;}if(f.life<=0)it.remove();}}
    private void damageEnemy(Enemy e,float d){e.hp-=d;e.flash=.09f;}
    private void killEnemy(Enemy e){int gain=e.type>=4?100:(e.elite?16:(e.type==1?9:5));score+=gain*10;kills++;scrap+=e.type>=4?7:(e.elite?2:0);combo++;comboTimer=2.3f;bestCombo=Math.max(bestCombo,combo);particleBurst(e.x,e.y,e.type>=4?Color.rgb(255,91,51):Color.rgb(222,106,65),e.type>=4?38:13);cameraShake=Math.max(cameraShake,e.type>=4?14:4);float drop=e.type>=4?1f:(.5f+meta[5]*.025f);if(rng.nextFloat()<drop&&parts.size()+pickups.size()<24)spawnPickup(e.x,e.y,randomPartForWave());if(e.type>=4){for(int i=0;i<2;i++)spawnPickup(e.x+rng.nextFloat()*90-45,e.y+rng.nextFloat()*90-45,randomPartForWave());hp=Math.min(maxHp,hp+32);toast="БОСС РАЗОБРАН • ДЕТАЛИ СВОБОДНЫ";toastTimer=2.1f;haptic(65);}}
    private void spawnPickup(float x,float y,int type){Pickup q=new Pickup();q.x=x;q.y=y;q.type=type;q.life=12;q.spin=rng.nextFloat()*6.28f;float a=rng.nextFloat()*6.28f,s=80+rng.nextFloat()*150;q.vx=(float)Math.cos(a)*s;q.vy=(float)Math.sin(a)*s;pickups.add(q);}
    private void damagePlayer(float d){shieldDelay=2.2f;if(shieldHp>0){float used=Math.min(shieldHp,d);shieldHp-=used;d-=used;}if(d>0)hp-=d;damageFlash=.18f;cameraShake=Math.max(cameraShake,10);particleBurst(px,py,Color.rgb(255,82,58),11);haptic(30);}
    private void useEmp(){if(empCd>0)return;empCd=9.5f;for(Enemy e:enemies){e.stun=Math.max(e.stun,2.1f);damageEnemy(e,20+meta[2]*2);}Fx f=new Fx();f.kind=3;f.x=px;f.y=py;f.life=f.max=.55f;fx.add(f);cameraShake=8;toast="EMP • СИСТЕМЫ ПОДАВЛЕНЫ";toastTimer=.9f;haptic(38);}

    private void rollChoices(){for(int i=0;i<3;i++){int v;do{v=randomPartForWave();}while((i>0&&v==choices[0])||(i>1&&v==choices[1]));choices[i]=v;float r=rng.nextFloat()+meta[5]*.012f;rarity[i]=r>.94f?2:(r>.72f?1:0);}}
    private int randomPartForWave(){int max=wave<3?5:wave<5?6:wave<7?7:wave<9?8:9;return rng.nextInt(max);}
    private void applyUpgrade(int type,int r){int copies=1+r;for(int i=0;i<copies;i++)addPart(type);if(type==ARMOR){maxHp+=18+12*r;hp=Math.min(maxHp,hp+30+12*r);}if(type==SHIELD){maxShield+=26+16*r;shieldHp=maxShield;}wave++;state=PLAY;startWave();}
    private void addPart(int type){if(parts.size()>=SLOTS.length)return;Part s=new Part();s.type=type;s.slot=parts.size();s.pulse=rng.nextFloat()*6.28f;parts.add(s);}
    private int countPart(int type){int n=0;for(Part s:parts)if(s.type==type)n++;return n;}
    private void buyMeta(int i){int cost=metaCost(i);if(scrap<cost){toast="НЕ ХВАТАЕТ ЛОМА";toastTimer=1.1f;return;}scrap-=cost;meta[i]++;saveMeta();toast=META_NAMES[i]+" • УРОВЕНЬ "+meta[i];toastTimer=1.1f;haptic(20);}
    private int metaCost(int i){int l=meta[i]+1;return 30*l*l+(i>=4?20:0);}

    private void particleBurst(float x,float y,int color,int n){for(int i=0;i<n;i++){Fx f=new Fx();f.kind=0;f.x=x;f.y=y;float a=rng.nextFloat()*6.283f,s=80+rng.nextFloat()*260;f.vx=(float)Math.cos(a)*s;f.vy=(float)Math.sin(a)*s;f.life=f.max=.24f+rng.nextFloat()*.5f;f.color=color;fx.add(f);}}
    private Enemy nearestEnemy(float x,float y){Enemy best=null;float bd=99999;for(Enemy e:enemies){float d=dist(x,y,e.x,e.y);if(d<bd){bd=d;best=e;}}return best;}

    private void drawFrame(){Canvas c=null;try{c=holder.lockCanvas();if(c==null)return;int w=c.getWidth(),h=c.getHeight();c.drawColor(Color.rgb(4,8,11));viewScale=Math.min(w/VW,h/VH);viewOffX=(w-VW*viewScale)*.5f;viewOffY=(h-VH*viewScale)*.5f;c.save();c.translate(viewOffX,viewOffY);c.scale(viewScale,viewScale);drawBackground(c);if(state==MENU)drawMenu(c);else if(state==HANGAR)drawHangar(c);else{drawWorld(c);if(state==UPGRADE)drawUpgrade(c);if(state==GAMEOVER)drawGameOver(c);}if(toastTimer>0)drawToast(c,toast);c.restore();}catch(Throwable ignored){}finally{if(c!=null)holder.unlockCanvasAndPost(c);}}
    private Bitmap currentFloor(){int a=((wave-1)/5)%3;return a==0?floorJunk:(a==1?floorFoundry:floorReactor);}
    private void drawBackground(Canvas c){Bitmap f=currentFloor();p.setAlpha(255);for(int y=0;y<VH+512;y+=512)for(int x=0;x<VW+512;x+=512)c.drawBitmap(f,x,y,p);p.setShader(new LinearGradient(0,0,0,VH,Color.argb(35,26,214,199),Color.argb(100,0,0,0),Shader.TileMode.CLAMP));c.drawRect(0,0,VW,VH,p);p.setShader(null);}
    private void drawWorld(Canvas c){float sx=cameraShake>0?(rng.nextFloat()*2-1)*cameraShake:0,sy=cameraShake>0?(rng.nextFloat()*2-1)*cameraShake:0;c.save();c.translate(sx,sy);for(Pickup q:pickups)drawPickup(c,q);for(Fx f:fx)drawFx(c,f);for(Projectile q:projectiles)drawProjectile(c,q);for(Enemy e:enemies)drawEnemy(c,e);drawPlayer(c);c.restore();drawHud(c);if(tutorialTimer>0)drawTutorial(c);if(damageFlash>0){p.setColor(Color.argb((int)(90*damageFlash/.18f),255,43,31));c.drawRect(0,0,VW,VH,p);}}

    private void drawPlayer(Canvas c){p.setColor(Color.argb(100,0,0,0));c.drawOval(new RectF(px-86,py+49,px+86,py+91),p);if(maxShield>0){float r=maxShield<=0?0:shieldHp/maxShield;stroke.setStrokeWidth(6);stroke.setColor(Color.argb((int)(70+150*r),71,241,223));c.drawCircle(px,py,88+countPart(SHIELD)*1.5f,stroke);}float ca=(float)Math.cos(heading),sa=(float)Math.sin(heading);for(Part s:parts){float ox=SLOTS[s.slot][0],oy=SLOTS[s.slot][1];float rx=ox*ca-oy*sa,ry=ox*sa+oy*ca;float x=px+rx,y=py+ry;stroke.setStrokeWidth(6);stroke.setColor(Color.argb(130,88,102,105));c.drawLine(px,py,x,y,stroke);float bob=(float)Math.sin(System.nanoTime()/400000000.0+s.pulse)*2.5f;drawBitmapCentered(c,bitmapForPart(s.type),x,y+bob,72,72,heading+1.5708f);}drawBitmapCentered(c,core,px,py,124,124,heading+1.5708f);stroke.setStrokeWidth(3);stroke.setColor(Color.argb(80,72,244,226));c.drawCircle(px,py,137+(float)Math.sin(System.nanoTime()/350000000.0)*5,stroke);}
    private void drawPickup(Canvas c,Pickup q){if(q.magnet){stroke.setStrokeWidth(2.5f);stroke.setColor(Color.argb(120,75,242,224));c.drawLine(q.x,q.y,px,py,stroke);}drawBitmapCentered(c,pickupGlow,q.x,q.y,62,62,q.spin);drawBitmapCentered(c,bitmapForPart(q.type),q.x,q.y,43,43,q.spin*.7f);}
    private Bitmap enemyBitmap(int type){if(type==1)return heavy;if(type==2)return sniper;if(type==3)return rammer;if(type==4)return bossCrusher;if(type==5)return bossMagnetar;if(type==6)return bossForge;return drone;}
    private void drawEnemy(Canvas c,Enemy e){Bitmap b=enemyBitmap(e.type);float size=e.type>=4?205:(e.type==1?112:82);if(e.elite)size*=1.12f;if(e.elite){stroke.setStrokeWidth(5);stroke.setColor(Color.rgb(246,179,53));c.drawCircle(e.x,e.y,size*.57f,stroke);}p.setAlpha(e.flash>0?155:255);drawBitmapCentered(c,b,e.x,e.y,size,size,e.type==3?(float)Math.atan2(py-e.y,px-e.x)+1.5708f:0);p.setAlpha(255);if(e.type>=4||e.elite){float wr=e.type>=4?180:72,r=clamp(e.hp/e.maxHp,0,1);p.setColor(Color.argb(170,0,0,0));c.drawRoundRect(e.x-wr/2,e.y-size*.62f,e.x+wr/2,e.y-size*.62f+11,6,6,p);p.setColor(e.type>=4?Color.rgb(255,83,52):Color.rgb(245,181,57));c.drawRoundRect(e.x-wr/2,e.y-size*.62f,e.x-wr/2+wr*r,e.y-size*.62f+11,6,6,p);}}
    private void drawProjectile(Canvas c,Projectile q){p.setColor(q.hostile?Color.rgb(255,77,47):(q.type==1?Color.rgb(255,153,48):Color.rgb(103,255,237)));c.drawCircle(q.x,q.y,q.radius,p);p.setColor(Color.argb(65,255,255,255));c.drawCircle(q.x,q.y,q.radius*2.4f,p);}
    private void drawFx(Canvas c,Fx f){float r=clamp(f.life/f.max,0,1);if(f.kind==0){p.setColor((f.color&0x00ffffff)|((int)(255*r)<<24));c.drawCircle(f.x,f.y,2+7*r,p);}else if(f.kind==1){stroke.setColor(Color.argb((int)(235*r),94,255,238));stroke.setStrokeWidth(4+5*r);c.drawLine(f.x,f.y,f.x2,f.y2,stroke);}else if(f.kind==2){stroke.setColor(Color.argb((int)(250*r),94,255,235));stroke.setStrokeWidth(11*r+3);c.drawLine(f.x,f.y,f.x2,f.y2,stroke);}else if(f.kind==3){stroke.setColor(Color.argb((int)(225*r),74,255,232));stroke.setStrokeWidth(10*r+2);c.drawCircle(f.x,f.y,(1-r)*500,stroke);}}

    private void drawHud(Canvas c){panel(c,30,26,410,112,.72f);text(c,"КОРПУС",55,58,18,Color.rgb(186,199,200),false);bar(c,55,72,330,18,hp/maxHp,Color.rgb(59,221,194));if(maxShield>0){text(c,"ЩИТ",55,118,15,Color.rgb(105,244,229),false);bar(c,106,106,279,11,maxShield==0?0:shieldHp/maxShield,Color.rgb(70,171,239));}panel(c,622,26,356,83,.62f);text(c,"ВОЛНА "+wave,650,73,27,Color.WHITE,true);text(c,"СЧЁТ "+score,805,73,20,Color.rgb(235,187,86),false);panel(c,1190,26,380,112,.66f);text(c,"ЛОМ  "+scrap,1218,62,22,Color.rgb(238,182,76),true);text(c,"КОМБО x"+combo,1218,101,20,combo>=5?Color.rgb(255,111,74):Color.LTGRAY,false);
        p.setColor(Color.argb(75,24,54,57));c.drawCircle(185,735,108,p);stroke.setStrokeWidth(4);stroke.setColor(Color.argb(130,78,220,207));c.drawCircle(185,735,108,stroke);float kx=185,ky=735;if(joyActive){float dx=joyX-joyBaseX,dy=joyY-joyBaseY,l=(float)Math.sqrt(dx*dx+dy*dy);if(l>76){dx=dx/l*76;dy=dy/l*76;}kx=(joyUsesFixedBase?185:joyBaseX)+dx;ky=(joyUsesFixedBase?735:joyBaseY)+dy;}p.setColor(Color.argb(185,61,223,208));c.drawCircle(kx,ky,38,p);
        p.setColor(Color.argb(200,12,31,37));c.drawCircle(1430,735,98,p);stroke.setStrokeWidth(7);stroke.setColor(empCd<=0?Color.rgb(70,238,217):Color.rgb(82,99,103));c.drawCircle(1430,735,98,stroke);text(c,"EMP",1430,729,26,Color.WHITE,true,true);text(c,empCd<=0?"ГОТОВ":String.format("%.1f",empCd),1430,765,16,Color.LTGRAY,false,true);
        text(c,"ДЕТАЛИ "+parts.size()+"/"+SLOTS.length,800,852,17,Color.rgb(170,188,188),false,true);
    }
    private void drawTutorial(Canvas c){panel(c,475,710,650,120,.75f);text(c,"ЛЕВАЯ СТОРОНА — ТЯНИ ПАЛЕЦ, ЧТОБЫ ЕХАТЬ",800,751,20,Color.WHITE,true,true);text(c,"ДЕТАЛИ ПРИТЯГИВАЮТСЯ САМИ • EMP СПРАВА",800,789,17,Color.rgb(115,228,214),false,true);}
    private void drawMenu(Canvas c){panel(c,240,105,1120,660,.87f);text(c,"MAGNETIC SCRAP",800,185,58,Color.WHITE,true,true);text(c,"СОБЕРИ БОЕВУЮ МАШИНУ ИЗ ТОГО, ЧТО ОТВАЛИЛОСЬ ОТ ВРАГОВ",800,228,18,Color.rgb(125,220,209),false,true);drawBitmapCentered(c,core,800,350,220,220,0);drawBitmapCentered(c,cannon,650,355,86,86,-.3f);drawBitmapCentered(c,blade,950,350,92,92,.25f);drawBitmapCentered(c,tesla,720,445,78,78,0);drawBitmapCentered(c,rocket,885,445,78,78,0);button(c,550,485,500,105,"ИГРАТЬ",Color.rgb(42,199,178));button(c,550,615,500,80,"АНГАР • "+scrap+" ЛОМА",Color.rgb(170,119,54));text(c,"REBUILD v4 • новое управление • новая графика",800,742,16,Color.rgb(132,148,151),false,true);}
    private void drawHangar(Canvas c){panel(c,70,40,1460,810,.88f);button(c,45,40,190,70,"← НАЗАД",Color.rgb(65,94,100));text(c,"АНГАР",800,115,48,Color.WHITE,true,true);text(c,"ЛОМ: "+scrap,1360,112,24,Color.rgb(242,187,82),true,true);for(int i=0;i<6;i++){float x=245+(i%3)*375,y=215+(i/3)*270;panel(c,x,y,330,210,.76f);text(c,META_NAMES[i],x+165,y+46,27,Color.WHITE,true,true);text(c,"УРОВЕНЬ "+meta[i],x+165,y+84,18,Color.rgb(117,224,211),false,true);text(c,metaDescription(i),x+165,y+118,15,Color.LTGRAY,false,true);int cost=metaCost(i);smallButton(c,x+55,y+148,220,48,"УЛУЧШИТЬ • "+cost,scrap>=cost?Color.rgb(47,183,164):Color.rgb(70,77,79));}}
    private String metaDescription(int i){switch(i){case 0:return "+18 HP";case 1:return "+24 к радиусу";case 2:return "+9% урона";case 3:return "+18 скорости";case 4:return "+14 щита";default:return "редкость и дроп";}}
    private void drawUpgrade(Canvas c){p.setColor(Color.argb(205,3,8,11));c.drawRect(0,0,VW,VH,p);text(c,"ВЫБЕРИ, ЧЕМ ОБРАСТЁТ МАШИНА",800,145,40,Color.WHITE,true,true);text(c,"Редкие модули дают сразу несколько копий",800,185,17,Color.rgb(162,187,188),false,true);for(int i=0;i<3;i++){float x=195+i*410;int t=choices[i],r=rarity[i];int accent=r==2?Color.rgb(181,91,255):(r==1?Color.rgb(70,176,255):Color.rgb(64,213,193));panelAccent(c,x,245,380,365,.94f,accent);drawBitmapCentered(c,bitmapForPart(t),x+190,355,135,135,0);text(c,r==2?"ЭПИЧЕСКИЙ":(r==1?"РЕДКИЙ":"ОБЫЧНЫЙ"),x+190,278,15,accent,true,true);text(c,PART_NAMES[t],x+190,474,26,Color.WHITE,true,true);text(c,PART_SUB[t],x+190,512,15,Color.rgb(132,220,210),false,true);text(c,"+"+(1+r)+" МОДУЛЬ",x+190,563,18,Color.rgb(237,185,81),true,true);}smallButton(c,625,660,350,76,rerolls>0?"ПЕРЕТАСОВАТЬ  x"+rerolls:"ПЕРЕТАСОВКА НЕТ",rerolls>0?Color.rgb(63,118,127):Color.rgb(54,60,62));}
    private void drawGameOver(Canvas c){p.setColor(Color.argb(215,3,6,8));c.drawRect(0,0,VW,VH,p);panel(c,390,170,820,545,.95f);text(c,"МАШИНА РАЗОБРАНА",800,260,44,Color.rgb(255,103,72),true,true);text(c,"ВОЛНА "+wave,800,337,29,Color.WHITE,true,true);text(c,"СЧЁТ "+score+"   •   УБИТО "+kills,800,390,21,Color.LTGRAY,false,true);text(c,"ЛУЧШЕЕ КОМБО x"+bestCombo,800,430,18,Color.rgb(236,184,81),false,true);text(c,"СОБРАНО МОДУЛЕЙ: "+parts.size(),800,470,17,Color.rgb(115,225,211),false,true);button(c,485,585,300,92,"ЕЩЁ РАЗ",Color.rgb(42,197,176));button(c,815,585,300,92,"МЕНЮ",Color.rgb(82,101,106));}
    private void drawToast(Canvas c,String s){float a=Math.min(1,toastTimer*2);p.setColor(Color.argb((int)(190*a),5,16,20));c.drawRoundRect(460,168,1140,232,24,24,p);text(c,s,800,211,21,Color.argb((int)(255*a),255,255,255),true,true);}

    private void panel(Canvas c,float x,float y,float w,float h,float a){p.setColor(Color.argb((int)(225*a),8,19,23));c.drawRoundRect(x,y,x+w,y+h,24,24,p);stroke.setStrokeWidth(2);stroke.setColor(Color.argb((int)(135*a),71,170,162));c.drawRoundRect(x,y,x+w,y+h,24,24,stroke);}
    private void panelAccent(Canvas c,float x,float y,float w,float h,float a,int accent){p.setColor(Color.argb((int)(230*a),8,18,22));c.drawRoundRect(x,y,x+w,y+h,24,24,p);stroke.setStrokeWidth(3);stroke.setColor(accent);c.drawRoundRect(x,y,x+w,y+h,24,24,stroke);}
    private void button(Canvas c,float x,float y,float w,float h,String s,int color){p.setColor(color);c.drawRoundRect(x,y,x+w,y+h,23,23,p);p.setColor(Color.argb(55,255,255,255));c.drawRoundRect(x+3,y+3,x+w-3,y+h*.46f,20,20,p);text(c,s,x+w/2,y+h*.62f,26,Color.WHITE,true,true);}
    private void smallButton(Canvas c,float x,float y,float w,float h,String s,int color){p.setColor(color);c.drawRoundRect(x,y,x+w,y+h,16,16,p);text(c,s,x+w/2,y+h*.64f,16,Color.WHITE,true,true);}
    private void bar(Canvas c,float x,float y,float w,float h,float ratio,int color){ratio=clamp(ratio,0,1);p.setColor(Color.rgb(28,40,44));c.drawRoundRect(x,y,x+w,y+h,h/2,h/2,p);p.setColor(color);c.drawRoundRect(x,y,x+w*ratio,y+h,h/2,h/2,p);}
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){text(c,s,x,y,size,color,bold,false);}
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold,boolean center){p.setShader(null);p.setColor(color);p.setTextSize(size);p.setTypeface(bold?android.graphics.Typeface.DEFAULT_BOLD:android.graphics.Typeface.DEFAULT);p.setTextAlign(center?Paint.Align.CENTER:Paint.Align.LEFT);c.drawText(s,x,y,p);}
    private void drawBitmapCentered(Canvas c,Bitmap b,float x,float y,float w,float h,float rot){if(b==null)return;c.save();c.translate(x,y);if(rot!=0)c.rotate((float)Math.toDegrees(rot));p.setAlpha(255);c.drawBitmap(b,null,new RectF(-w/2,-h/2,w/2,h/2),p);c.restore();}
    private Bitmap bitmapForPart(int t){switch(t){case CANNON:return cannon;case BLADE:return blade;case ARMOR:return armor;case BATTERY:return battery;case WHEEL:return wheel;case TESLA:return tesla;case ROCKET:return rocket;case SHIELD:return shield;default:return laser;}}

    @Override public boolean onTouchEvent(MotionEvent e){
        getParent().requestDisallowInterceptTouchEvent(true);int action=e.getActionMasked(),index=e.getActionIndex();
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){float[]q=toVirtual(e.getX(index),e.getY(index));int id=e.getPointerId(index);
            if(state==PLAY){float ex=q[0]-1430,ey=q[1]-735;if(ex*ex+ey*ey<125*125){synchronized(inputLock){tapX=q[0];tapY=q[1];}return true;}if(joyPointer<0&&q[0]<1000){joyPointer=id;joyActive=true;float fd=dist(q[0],q[1],185,735);joyUsesFixedBase=fd<180;if(joyUsesFixedBase){joyBaseX=185;joyBaseY=735;}else{joyBaseX=q[0];joyBaseY=q[1];}joyX=q[0];joyY=q[1];return true;}}
            synchronized(inputLock){tapX=q[0];tapY=q[1];}
        }else if(action==MotionEvent.ACTION_MOVE){if(joyPointer>=0){int idx=e.findPointerIndex(joyPointer);if(idx>=0){float[]q=toVirtual(e.getX(idx),e.getY(idx));joyX=q[0];joyY=q[1];}}}
        else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){int id=e.getPointerId(index);if(id==joyPointer){joyPointer=-1;joyActive=false;joyX=joyBaseX;joyY=joyBaseY;}}
        else if(action==MotionEvent.ACTION_CANCEL){joyPointer=-1;joyActive=false;}
        return true;
    }
    private float[] toVirtual(float sx,float sy){float sc=viewScale<=0?1:viewScale;return new float[]{(sx-viewOffX)/sc,(sy-viewOffY)/sc};}
    private void haptic(long ms){try{if(vibrator==null||!vibrator.hasVibrator())return;if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(VibrationEffect.createOneShot(ms,VibrationEffect.DEFAULT_AMPLITUDE));else vibrator.vibrate(ms);}catch(Throwable ignored){}}
    private static boolean inRect(float x,float y,float bx,float by,float bw,float bh){return x>=bx&&x<=bx+bw&&y>=by&&y<=by+bh;}
    private static float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}
    private static float dist(float x1,float y1,float x2,float y2){float dx=x2-x1,dy=y2-y1;return(float)Math.sqrt(dx*dx+dy*dy);}

    private static final class Enemy{float x,y,hp,maxHp,speed,radius,shootCd,stun,flash,phase;int type;boolean elite;}
    private static final class Projectile{float x,y,vx,vy,damage,life,radius,splash;int type;boolean hostile;}
    private static final class Part{int type,slot;float pulse;}
    private static final class Pickup{float x,y,vx,vy,life,spin;int type;boolean magnet;}
    private static final class Fx{float x,y,x2,y2,vx,vy,life,max;int color,kind;}
}
