from pathlib import Path
import json, re, subprocess, time, urllib.request
import websocket
OUT=Path('smoke-output');OUT.mkdir(exist_ok=True)
def adb(*args):return subprocess.check_output(['adb',*args],text=True)
def shot(name):
    with (OUT/name).open('wb') as f: subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True)
client=None;seq=0

def evaluate(expression):
    global seq
    seq+=1
    client.send(json.dumps({'id':seq,'method':'Runtime.evaluate','params':{'expression':expression,'returnByValue':True}}))
    while True:
        message=json.loads(client.recv())
        if message.get('id')!=seq:continue
        if message.get('error') or message.get('result',{}).get('exceptionDetails'):
            raise RuntimeError(json.dumps(message))
        return message['result']['result'].get('value')

def touch(selector,duration=0):
    rect=evaluate("JSON.stringify((function(){let r=document.querySelector("+json.dumps(selector)+").getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,ratio:devicePixelRatio};})())")
    r=json.loads(rect);x=str(round(r['x']*r['ratio']));y=str(round(r['y']*r['ratio']))
    if duration:adb('shell','input','touchscreen','swipe',x,y,x,y,str(duration))
    else:adb('shell','input','touchscreen','tap',x,y)

def game():return json.loads(evaluate("JSON.stringify({state:window.__game.state,x:window.__game.p.x,y:window.__game.p.y,attack:window.__game.p.attack,dashCd:window.__game.p.dashCd,jumps:window.__game.p.jumps})"))

try:
    # boot_completed precedes Android 15's initial package/resource updates.
    time.sleep(25)
    adb('install','-r','DEADLIGHT-2.0.apk');adb('logcat','-c')
    adb('shell','settings','put','secure','immersive_mode_confirmations','confirmed')
    result=adb('shell','am','start','-W','-n','com.deadlight.game/.MainActivity')
    (OUT/'launch.txt').write_text(result)
    assert 'Status: ok' in result,result
    pid=adb('shell','pidof','com.deadlight.game').strip()
    adb('forward','tcp:9222','localabstract:webview_devtools_remote_'+pid)
    deadline=time.monotonic()+30
    while time.monotonic()<deadline:
        try:
            targets=json.loads(urllib.request.urlopen('http://127.0.0.1:9222/json',timeout=3).read())
            target=next(t for t in targets if '/assets/index.html' in t.get('url',''))
            client=websocket.create_connection(target['webSocketDebuggerUrl'],timeout=10,suppress_origin=True)
            if evaluate("Boolean(window.__game&&window.__game.spritesReady&&document.querySelector('#play'))"):break
        except (OSError,StopIteration,RuntimeError):time.sleep(.5)
    else:raise RuntimeError('Android WebView game did not become ready')
    time.sleep(5)
    assert game()['state']=='menu'
    shot('01-menu.png')
    touch('#play');
    for _ in range(30):
        if game()['state']=='play':break
        time.sleep(.1)
    assert game()['state']=='play'
    evaluate("window.__touchEvents=[];for(let name of ['pointerdown','pointerup','pointercancel','lostpointercapture','touchstart','touchend'])document.addEventListener(name,e=>window.__touchEvents.push({name:name,target:e.target.outerHTML,key:window.__game.keys.d}),true)")
    (OUT/'layout.json').write_text(evaluate("JSON.stringify({width:innerWidth,height:innerHeight,ratio:devicePixelRatio,hidden:document.hidden,buttons:[...document.querySelectorAll('[data-key]')].map(b=>({key:b.dataset.key,rect:b.getBoundingClientRect().toJSON(),display:getComputedStyle(b.parentElement.parentElement).display}))})"))
    shot('02-gameplay.png')
    before=game();touch('[data-key="d"]',1000);time.sleep(.1);after=game()
    (OUT/'input.json').write_text(evaluate("JSON.stringify({events:window.__touchEvents,keys:window.__game.keys,step:window.__game.p.step})"))
    assert after['x']>before['x']+20,(before,after)
    shot('02-gameplay.png')
    touch('[data-key=" "]');time.sleep(.1);after=game();assert after['jumps']>=1,after
    touch('[data-key=" "]');time.sleep(.1);after=game();assert after['jumps']==2,after
    shot('03-jump.png')
    touch('[data-key="j"]');time.sleep(.02);assert game()['attack']>0
    touch('[data-key="k"]');time.sleep(.02);assert game()['dashCd']>0
    adb('shell','input','keyevent','4');time.sleep(.7);assert game()['state']=='paused'
    shot('04-pause.png')
    time.sleep(1);touch('#resume');
    for _ in range(30):
        if game()['state']=='play':break
        time.sleep(.1)
    assert game()['state']=='play'
    # Physical controls with deterministic health/energy test fixtures.
    evaluate("Object.assign(__game.p,{energy:100,attack:0,attackCd:0,special:0,specialCd:0,dash:0,inv:99});")
    touch('[data-key="q"]');time.sleep(.1)
    assert evaluate("__game.p.energy<80&&__game.p.special>0"),'Plague button or energy cost failed'
    time.sleep(.8)
    evaluate("Object.assign(__game.p,{y:565,vy:0,ground:true,x:200,specialCd:0,energy:100});")
    touch('[data-key="r"]');time.sleep(.15)
    assert evaluate("__game.p.energy<70&&__game.p.special>0"),'Slam button or energy cost failed'
    time.sleep(1)
    evaluate("(()=>{let e=__game.enemies[0];e.hp=12;Object.assign(__game.p,{x:e.x-60,y:e.y,vy:0,ground:true,face:1,attack:0,attackCd:0,special:0,hp:40,energy:10,inv:99});})()")
    touch('[data-key="j"]');time.sleep(.6)
    assert evaluate("__game.kills>=1&&__game.corpses.length>0"),'Claw hit did not create corpse'
    evaluate("(()=>{let c=__game.corpses[0];Object.assign(__game.p,{x:c.x,y:c.y,vy:0,ground:true,attack:0,attackCd:0});})()")
    touch('[data-key="e"]');time.sleep(2)
    assert evaluate("__game.corpses[0].eaten&&__game.p.hp>=75&&__game.p.energy>=55"),'Feeding did not restore health and energy'
    shot('05-feeding.png')
    evaluate("__game.p.hp=10;__game.profile.inventory.life=2")
    touch('#use-life');time.sleep(.2)
    assert evaluate("__game.p.hp===60&&__game.profile.inventory.life===1"),'Potion consumption failed'
    evaluate("__game.profile.points=1")
    touch('#mutations');time.sleep(.3)
    touch('[data-perk="vitality"]');time.sleep(.2)
    assert evaluate("__game.profile.upgrades.vitality===1&&__game.profile.points===0"),'Mutation purchase failed'
    shot('06-mutations.png')
    touch('#close-panel');time.sleep(.3)
    evaluate("__game.profile.inventory.claw=1")
    touch('#inventory');time.sleep(.3)
    touch('[data-item="claw"]');time.sleep(.2)
    assert evaluate("__game.profile.equipment.claw===true"),'Relic equip failed'
    shot('07-inventory.png')
    touch('#close-panel');time.sleep(.3)
    evaluate("__game.saveRun()")
    before_reload=evaluate("localStorage.getItem('deadlight-run-v2')")
    evaluate("setTimeout(()=>location.reload(),0)")
    time.sleep(2)
    for _ in range(60):
        try:
            if evaluate("Boolean(window.__game&&__game.spritesReady)"):break
        except RuntimeError:pass
        time.sleep(.2)
    assert game()['state']=='menu'
    saved_run=evaluate("localStorage.getItem('deadlight-run-v2')")
    touch('#inventory');time.sleep(.3)
    after_panel=evaluate("localStorage.getItem('deadlight-run-v2')")
    (OUT/'reload-saves.json').write_text(json.dumps({'before_reload':json.loads(before_reload),'after_reload':json.loads(saved_run),'after_menu_panel':json.loads(after_panel)},indent=2))
    assert after_panel==saved_run,'Menu panel overwrote raid snapshot'
    assert evaluate("__game.profile.equipment.claw&&__game.profile.upgrades.vitality===1"),'Profile did not persist reload'
    touch('#close-panel');time.sleep(.3)
    touch('#play');time.sleep(.3)
    assert game()['state']=='play'
    assert evaluate("__game.kills>=1&&__game.corpses[0].eaten"),'Raid did not persist reload'
    shot('08-restored-raid.png')
    errors=evaluate("Boolean(window.__game&&document.querySelector('canvas').width>0)");assert errors
    logs=adb('logcat','-d')
    assert 'FATAL EXCEPTION' not in logs,'Android crash; see logcat.txt'
    assert not re.search(r'Uncaught (SyntaxError|ReferenceError|TypeError)',logs),'JavaScript error; see logcat.txt'
    (OUT/'result.json').write_text(json.dumps({'launch':'passed','touch_movement':'passed','double_jump':'passed','attack':'passed','dash':'passed','pause_resume':'passed','crash_check':'passed','sprites':'passed','specials_energy':'passed','corpse_feeding':'passed','potions':'passed','mutation_purchase':'passed','inventory_equip':'passed','offline_reload':'passed','menu_save_preserved':'passed'},indent=2))
    print('PASS: APK installed, Android WebView rendered, physical touch movement/double jump/attack/dash, native back pause/resume, no crash')
finally:
    if client:
        try:(OUT/'last-game.json').write_text(json.dumps(game()))
        except Exception:pass
    (OUT/'logcat.txt').write_text(adb('logcat','-d'))
    if client:client.close()
