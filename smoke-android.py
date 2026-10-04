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
    if duration:adb('shell','input','swipe',x,y,x,y,str(duration))
    else:adb('shell','input','tap',x,y)

def game():return json.loads(evaluate("JSON.stringify({state:window.__game.state,x:window.__game.p.x,y:window.__game.p.y,attack:window.__game.p.attack,dashCd:window.__game.p.dashCd,jumps:window.__game.p.jumps})"))

try:
    adb('install','-r','DEADLIGHT-1.0.apk');adb('logcat','-c')
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
            client=websocket.create_connection(target['webSocketDebuggerUrl'],timeout=10,skip_origin=True)
            if evaluate("Boolean(window.__game&&document.querySelector('#play'))"):break
        except (OSError,StopIteration,RuntimeError):time.sleep(.5)
    else:raise RuntimeError('Android WebView game did not become ready')
    assert game()['state']=='menu'
    shot('01-menu.png')
    touch('#play');time.sleep(.15);assert game()['state']=='play'
    before=game();touch('[data-key="d"]',350);time.sleep(.1);after=game()
    assert after['x']>before['x']+20,(before,after)
    shot('02-gameplay.png')
    touch('[data-key=" "]');time.sleep(.1);after=game();assert after['jumps']>=1,after
    shot('03-jump.png')
    touch('[data-key=" "]');time.sleep(.1);assert game()['jumps']==2
    touch('[data-key="j"]');time.sleep(.02);assert game()['attack']>0
    touch('[data-key="k"]');time.sleep(.02);assert game()['dashCd']>0
    adb('shell','input','keyevent','4');time.sleep(.2);assert game()['state']=='paused'
    shot('04-pause.png')
    touch('#resume');time.sleep(.1);assert game()['state']=='play'
    errors=evaluate("Boolean(window.__game&&document.querySelector('canvas').width>0)");assert errors
    logs=adb('logcat','-d')
    assert 'FATAL EXCEPTION' not in logs,'Android crash; see logcat.txt'
    assert not re.search(r'Uncaught (SyntaxError|ReferenceError|TypeError)',logs),'JavaScript error; see logcat.txt'
    (OUT/'result.json').write_text(json.dumps({'launch':'passed','touch_movement':'passed','double_jump':'passed','attack':'passed','dash':'passed','pause_resume':'passed','crash_check':'passed'},indent=2))
    print('PASS: APK installed, Android WebView rendered, physical touch movement/double jump/attack/dash, native back pause/resume, no crash')
finally:
    (OUT/'logcat.txt').write_text(adb('logcat','-d'))
    if client:client.close()
