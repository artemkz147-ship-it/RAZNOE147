from pathlib import Path


def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f'v277 patch missing: {label}')
    return text.replace(old, new, 1)

# --- launcher: give the game iframe a stable per-ROM state key ---
p = Path('app/src/main/assets/launcher.js')
s = p.read_text(encoding='utf-8')
s = must_replace(
    s,
    "romSize:file.size,biosUrl:biosObjectUrl",
    "romSize:file.size,stateKey:opts.libraryId||gameId(selected.core,file),biosUrl:biosObjectUrl",
    'persistent state key handoff',
)
p.write_text(s, encoding='utf-8')

# --- game iframe: persist quick states in IndexedDB instead of temporary core FS ---
p = Path('app/src/main/assets/game.html')
s = p.read_text(encoding='utf-8')
s = must_replace(
    s,
    "let started=false,startConfirmed=false,watchdog=0,currentCore='',currentSettings={touchMode:'full'},currentProfile=null;",
    "let started=false,startConfirmed=false,watchdog=0,currentCore='',currentStateKey='',currentSettings={touchMode:'full'},currentProfile=null;",
    'state key runtime variable',
)

old = """function quickSave(slot){const emu=window.EJS_emulator;try{const s=String(Math.max(1,Math.min(9,Number(slot||1))));emu?.changeSettingOption?.('save-state-slot',s);const ok=!!emu?.gameManager?.quickSave?.(s);send('retro-quick-result',{ok,message:ok?`Сохранено в слот ${s}`:'Не удалось быстро сохранить.'})}catch(e){send('retro-quick-result',{ok:false,message:safeMessage(e)})}}
function quickLoad(slot){const emu=window.EJS_emulator;try{const s=String(Math.max(1,Math.min(9,Number(slot||1))));emu?.changeSettingOption?.('save-state-slot',s);emu?.gameManager?.quickLoad?.(s);send('retro-quick-result',{ok:true,message:`Загружен слот ${s}`})}catch(e){send('retro-quick-result',{ok:false,message:safeMessage(e)})}}
"""
new = """const STATE_DB='retro3-save-states',STATE_STORE='states',STATE_DB_VERSION=1;
function openStateDb(){return new Promise((resolve,reject)=>{const q=indexedDB.open(STATE_DB,STATE_DB_VERSION);q.onupgradeneeded=()=>{const db=q.result;if(!db.objectStoreNames.contains(STATE_STORE))db.createObjectStore(STATE_STORE,{keyPath:'id'})};q.onsuccess=()=>resolve(q.result);q.onerror=()=>reject(q.error||Error('Не удалось открыть хранилище сохранений'))})}
function stateReq(req){return new Promise((resolve,reject)=>{req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error||Error('Ошибка хранилища сохранений'))})}
function slotNumber(slot){return String(Math.max(1,Math.min(9,Number(slot||1))))}
function stateRecordId(slot){return `${currentStateKey}::slot:${slotNumber(slot)}`}
async function putPersistentState(slot,data){let bytes;if(data instanceof Uint8Array)bytes=data.slice();else if(data instanceof ArrayBuffer)bytes=new Uint8Array(data.slice(0));else if(ArrayBuffer.isView(data))bytes=new Uint8Array(data.buffer,data.byteOffset,data.byteLength).slice();else throw Error('Ядро не вернуло данные сохранения.');if(!bytes.byteLength)throw Error('Состояние игры пустое.');const db=await openStateDb();try{const tx=db.transaction(STATE_STORE,'readwrite'),st=tx.objectStore(STATE_STORE);await stateReq(st.put({id:stateRecordId(slot),game:currentStateKey,core:currentCore,slot:slotNumber(slot),updatedAt:Date.now(),data:bytes.buffer}));await new Promise((resolve,reject)=>{tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error||Error('Не удалось записать сохранение'));tx.onabort=()=>reject(tx.error||Error('Запись сохранения отменена'))})}finally{db.close()}}
async function getPersistentState(slot){const db=await openStateDb();try{const rec=await stateReq(db.transaction(STATE_STORE,'readonly').objectStore(STATE_STORE).get(stateRecordId(slot)));if(!rec||!rec.data)return null;const d=rec.data;if(d instanceof Uint8Array)return d.slice();if(d instanceof ArrayBuffer)return new Uint8Array(d.slice(0));if(ArrayBuffer.isView(d))return new Uint8Array(d.buffer,d.byteOffset,d.byteLength).slice();return null}finally{db.close()}}
async function quickSave(slot){const emu=window.EJS_emulator,gm=emu?.gameManager,s=slotNumber(slot);try{if(!startConfirmed||!gm?.getState)throw Error('Игра ещё не готова к сохранению.');emu?.changeSettingOption?.('save-state-slot',s);const data=gm.getState();await putPersistentState(s,data);try{gm.quickSave?.(s)}catch(_){}send('retro-quick-result',{ok:true,message:`Сохранено в слот ${s}. Сохранение останется после выхода.`})}catch(e){send('retro-quick-result',{ok:false,message:`Не удалось сохранить: ${safeMessage(e)}`})}}
async function quickLoad(slot){const emu=window.EJS_emulator,gm=emu?.gameManager,s=slotNumber(slot);try{if(!startConfirmed||!gm?.loadState)throw Error('Игра ещё не готова к загрузке.');emu?.changeSettingOption?.('save-state-slot',s);const data=await getPersistentState(s);if(data&&data.byteLength){gm.loadState(data);send('retro-quick-result',{ok:true,message:`Загружен слот ${s}`});return}let volatileExists=false;try{volatileExists=!!gm?.FS?.analyzePath?.(`/${s}-quick.state`)?.exists}catch(_){}if(volatileExists&&gm?.quickLoad){gm.quickLoad(s);send('retro-quick-result',{ok:true,message:`Загружен временный слот ${s}`});return}send('retro-quick-result',{ok:false,message:`В слоте ${s} нет сохранения для этой игры.`})}catch(e){send('retro-quick-result',{ok:false,message:`Не удалось загрузить: ${safeMessage(e)}`})}}
"""
s = must_replace(s, old, new, 'persistent quick save/load')

s = must_replace(
    s,
    "verifyConfig(config);currentCore=config.core;currentSettings={touchMode:'full',...(config.settings||{})};",
    "verifyConfig(config);currentCore=config.core;currentStateKey=`${String(config.stateKey||`${config.core}:${config.romName}:${config.romSize}`)}|engine:${String(config.nesCore||config.core)}`;currentSettings={touchMode:'full',...(config.settings||{})};",
    'initialize stable save key',
)
p.write_text(s, encoding='utf-8')

# Update in place using the same package and permanent release certificate.
g = Path('app/build.gradle')
t = g.read_text(encoding='utf-8')
t = must_replace(t, 'versionCode 17', 'versionCode 18', 'version code')
t = must_replace(t, "versionName '2.7.6'", "versionName '2.7.7'", 'version name')
g.write_text(t, encoding='utf-8')

print('Retro 3 v2.7.7 persistent save-state patch applied')
