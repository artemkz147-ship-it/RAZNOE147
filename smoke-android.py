from pathlib import Path
import json, re, subprocess, time, xml.etree.ElementTree as ET
OUT=Path('smoke-output');OUT.mkdir(exist_ok=True)
def adb(*args):return subprocess.check_output(['adb',*args],text=True)
def shot(name):
    with (OUT/name).open('wb') as f: subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True)
def find_button(label):
    adb('shell','uiautomator','dump','/sdcard/window.xml')
    text=adb('shell','cat','/sdcard/window.xml')
    (OUT/'ui.xml').write_text(text)
    root=ET.fromstring(text)
    for node in root.iter('node'):
        if label in (node.get('text','')+' '+node.get('content-desc','')):
            bounds=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
            if len(bounds)==4:return ((bounds[0]+bounds[2])//2,(bounds[1]+bounds[3])//2)
    raise RuntimeError('Android WebView button missing: '+label)
adb('install','-r','DEADLIGHT-1.0.apk');adb('logcat','-c')
result=adb('shell','am','start','-W','-n','com.deadlight.game/.MainActivity')
(OUT/'launch.txt').write_text(result)
assert 'Status: ok' in result,result
time.sleep(5)
assert 'com.deadlight.game' in adb('shell','dumpsys','activity','activities')
shot('01-menu.png')
x,y=find_button('ПРОБУДИТЬСЯ');adb('shell','input','tap',str(x),str(y));time.sleep(1)
x,y=find_button('Направо');adb('shell','input','swipe',str(x),str(y),str(x),str(y),'700');time.sleep(1)
shot('02-gameplay.png')
x,y=find_button('Прыжок');adb('shell','input','tap',str(x),str(y));time.sleep(.15);shot('03-jump.png')
x,y=find_button('Удар');adb('shell','input','tap',str(x),str(y))
x,y=find_button('Рывок');adb('shell','input','tap',str(x),str(y))
adb('shell','input','keyevent','4');time.sleep(.5)
find_button('ПРОДОЛЖИТЬ');shot('04-pause.png')
logs=adb('logcat','-d');(OUT/'logcat.txt').write_text(logs)
assert 'FATAL EXCEPTION' not in logs,'Android crash; see logcat.txt'
assert not re.search(r'Uncaught (SyntaxError|ReferenceError|TypeError)',logs),'JavaScript error; see logcat.txt'
(OUT/'result.json').write_text(json.dumps({'launch':'passed','touch_controls':'passed','pause':'passed','crash_check':'passed'},indent=2))
print('PASS: APK installed, Android Activity launched, WebView menu visible, touch movement/jump/attack/dash, pause, no crash')
