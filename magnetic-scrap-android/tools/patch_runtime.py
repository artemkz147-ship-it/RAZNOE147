from pathlib import Path

p = Path(__file__).resolve().parents[1] / "app/src/main/java/com/artem/magneticscrap/GameView.java"
s = p.read_text(encoding="utf-8")

# Forge boss used to mutate enemies while updateEnemies() iterated it, which can throw
# ConcurrentModificationException. Queue spawned minions and flush them after iteration.
s = s.replace(
    "    private float cameraShake=0,damageFlash=0,tutorialTimer=0;\n",
    "    private float cameraShake=0,damageFlash=0,tutorialTimer=0;\n"
    "    private int pendingMinions=0;\n"
    "    private float pendingMinionX=0,pendingMinionY=0;\n"
)
s = s.replace(
    "        updateEnemies(dt);updatePickups(dt);updateWeapons(dt);updateProjectiles(dt);updateFx(dt);\n",
    "        updateEnemies(dt);flushPendingMinions();updatePickups(dt);updateWeapons(dt);updateProjectiles(dt);updateFx(dt);\n"
)
s = s.replace(
    "if(e.shootCd<=0&&enemies.size()<18){spawnMinionNear(e.x,e.y);e.shootCd=2.4f;}",
    "if(e.shootCd<=0&&enemies.size()+pendingMinions<18){pendingMinions++;pendingMinionX=e.x;pendingMinionY=e.y;e.shootCd=2.4f;}"
)
s = s.replace(
    "    private void spawnMinionNear(float x,float y){Enemy m=new Enemy();m.type=0;m.radius=33;m.maxHp=36+wave*5;m.hp=m.maxHp;m.speed=90;m.x=x+rng.nextFloat()*120-60;m.y=y+rng.nextFloat()*120-60;enemies.add(m);}\n",
    "    private void flushPendingMinions(){while(pendingMinions>0){spawnMinionNear(pendingMinionX,pendingMinionY);pendingMinions--;}}\n"
    "    private void spawnMinionNear(float x,float y){Enemy m=new Enemy();m.type=0;m.radius=33;m.maxHp=36+wave*5;m.hp=m.maxHp;m.speed=90;m.x=x+rng.nextFloat()*120-60;m.y=y+rng.nextFloat()*120-60;enemies.add(m);}\n"
)

# Make the floating joystick visually follow its actual base instead of showing the fixed ring.
s = s.replace(
    "p.setColor(Color.argb(75,24,54,57));c.drawCircle(185,735,108,p);stroke.setStrokeWidth(4);stroke.setColor(Color.argb(130,78,220,207));c.drawCircle(185,735,108,stroke);float kx=185,ky=735;if(joyActive){",
    "float ringX=(joyActive&&!joyUsesFixedBase)?joyBaseX:185,ringY=(joyActive&&!joyUsesFixedBase)?joyBaseY:735;p.setColor(Color.argb(75,24,54,57));c.drawCircle(ringX,ringY,108,p);stroke.setStrokeWidth(4);stroke.setColor(Color.argb(130,78,220,207));c.drawCircle(ringX,ringY,108,stroke);float kx=ringX,ky=ringY;if(joyActive){"
)

p.write_text(s, encoding="utf-8")

required = ["pendingMinions", "flushPendingMinions()", "ringX=(joyActive&&!joyUsesFixedBase)"]
for token in required:
    if token not in s:
        raise SystemExit(f"runtime patch failed: missing {token}")
print("Patched runtime safety and floating joystick presentation")
