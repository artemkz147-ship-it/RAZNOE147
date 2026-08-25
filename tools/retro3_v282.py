from pathlib import Path


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'v282 patch missing: {label}')
    return text.replace(old, new, 1)

# Standard Android gamepads should work without manual setup.  Keep the PS1 browser
# button 8/9 workaround from v2.8.1, but accept the canonical Android START/SELECT
# keycodes automatically.  Non-standard pads still use the native manual mapper.
p = Path('app/src/main/assets/launcher.js')
s = p.read_text(encoding='utf-8')
old = "window.__retroNativePad=(code,down)=>{const n=Number(code);if(!Number.isFinite(n))return;const token=`ANDROID_KEY_${n}`;if(captureState?.kind==='gamepad'){if(!down)return;const id=captureState.id;if(!mapperDraft?.[0]?.[id])mapperDraft[0][id]={};mapperDraft[0][id]={...mapperDraft[0][id],value2:token};closeCapture();updateMapperBinding(id,'gamepad');return}if(!playing)return;try{const p=loadMapperProfile(selected.core)?.[0]||{};for(const [id,cfg] of Object.entries(p)){if(cfg?.value2!==token)continue;const k=Number(id);if(k===24){if(down)quickAction('retro-quick-save');continue}if(k===25){if(down)quickAction('retro-quick-load');continue}if(k>=0&&k<24)postToGame('retro-input',{index:k,value:down?1:0})}}catch(_){}};"
new = "window.__retroNativePad=(code,down)=>{const n=Number(code);if(!Number.isFinite(n))return;const token=`ANDROID_KEY_${n}`;if(captureState?.kind==='gamepad'){if(!down)return;const id=captureState.id;if(!mapperDraft?.[0]?.[id])mapperDraft[0][id]={};mapperDraft[0][id]={...mapperDraft[0][id],value2:token};closeCapture();updateMapperBinding(id,'gamepad');return}if(!playing)return;try{const p=loadMapperProfile(selected.core)?.[0]||{};if(selected.core==='psx'){const auto=(id,keyCode)=>{const cfg=p[id]||{},explicit=String(cfg.value2||'');if(explicit)return false;const autoToken=`ANDROID_KEY_${keyCode}`;const usedElsewhere=Object.entries(p).some(([other,c])=>Number(other)!==id&&c?.value2===autoToken);if(usedElsewhere)return false;if(n===keyCode){postToGame('retro-input',{index:id,value:down?1:0});return true}return false};if(auto(2,109)||auto(3,108))return}for(const [id,cfg] of Object.entries(p)){if(cfg?.value2!==token)continue;const k=Number(id);if(k===24){if(down)quickAction('retro-quick-save');continue}if(k===25){if(down)quickAction('retro-quick-load');continue}if(k>=0&&k<24)postToGame('retro-input',{index:k,value:down?1:0})}}catch(_){}};"
s = must_replace(s, old, new, 'standard Android PS1 Start/Select auto fallback')
p.write_text(s, encoding='utf-8')

# Same package and permanent signing certificate.
g = Path('app/build.gradle')
t = g.read_text(encoding='utf-8')
t = must_replace(t, 'versionCode 22', 'versionCode 23', 'version code')
t = must_replace(t, "versionName '2.8.1'", "versionName '2.8.2'", 'version name')
g.write_text(t, encoding='utf-8')

print('Retro 3 v2.8.2 standard gamepad auto mapping applied')
