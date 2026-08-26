from pathlib import Path

root=Path('skyfrontiers3d') if Path('skyfrontiers3d').exists() else Path('.')
html_p=root/'index.html'; css_p=root/'style.css'

html=r'''<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover" />
  <meta name="theme-color" content="#080d12" />
  <title>Sky Frontiers 3D</title>
  <script type="module" src="/game.js"></script>
</head>
<body>
  <canvas id="game"></canvas>

  <div id="boot" class="boot">
    <div class="boot-mark">
      <div class="logo">SKY FRONTIERS <span>3D</span></div>
      <div class="boot-title">FLIGHT SYSTEM INITIALIZATION</div>
    </div>
    <div class="boot-bottom">
      <div class="progress"><i id="bootBar"></i></div>
      <div id="bootText" class="muted">Подготовка...</div>
    </div>
  </div>

  <div id="hud" class="hud hidden">
    <div class="mission-strip">
      <div class="brand">SKY FRONTIERS</div>
      <div class="mission-copy">
        <div id="objective" class="objective">Свободный полёт</div>
        <div id="missionProgress" class="subobjective">Исследуйте карту или войдите в маяк задания</div>
      </div>
    </div>

    <div class="flight-data speed-tape">
      <small>SPD</small>
      <b id="speed">0</b>
      <span>KM/H</span>
    </div>
    <div class="flight-data altitude-tape">
      <small>ALT</small>
      <b id="altitude">0</b>
      <span>M AGL</span>
    </div>
    <div class="flight-data throttle-tape">
      <small>THR</small>
      <b id="throttle">0</b>
      <span>%</span>
    </div>

    <div class="center-marker" aria-hidden="true">
      <i class="wing wing-l"></i><i class="dot"></i><i class="wing wing-r"></i>
      <span class="pitch p1"></span><span class="pitch p2"></span>
    </div>

    <div class="wallet"><span>CR</span><b id="credits">0</b></div>
    <div class="nav-card"><div class="nav-head"><span>NAV</span><small>LOCAL</small></div><canvas id="minimap" width="220" height="220"></canvas></div>
    <div id="warning" class="warning"></div>
    <div id="hint" class="hint">W/S — тангаж · A/D — крен · Shift/Ctrl — газ · C — камера · M — меню</div>

    <div class="quick-actions bottom-dock">
      <button id="missionsBtn" type="button"><svg viewBox="0 0 24 24"><path d="M6 3h12v3H6zM5 8h14v13H5zM8 11h8M8 15h6"/></svg><span>МИССИИ</span></button>
      <button id="cameraBtn" type="button"><svg viewBox="0 0 24 24"><path d="M4 7h4l2-2h4l2 2h4v12H4z"/><circle cx="12" cy="13" r="3.5"/></svg><span>КАМЕРА</span></button>
      <button id="hangarBtn" type="button"><svg viewBox="0 0 24 24"><path d="M3 20V9l9-6 9 6v11M7 20v-7h10v7"/></svg><span>АНГАР</span></button>
      <button id="freeBtn" type="button"><svg viewBox="0 0 24 24"><path d="M4 13l7-2 2-7 2 7 5 2-5 2-2 5-2-5z"/></svg><span>FREE</span></button>
      <button id="uiMenuBtn" type="button"><svg viewBox="0 0 24 24"><path d="M5 7h14M5 12h14M5 17h14"/></svg><span>МЕНЮ</span></button>
    </div>
  </div>

  <div id="touch" class="touch hidden">
    <div class="stick-wrap"><span>FLIGHT</span><div class="stick" id="touchStick"><i></i></div></div>
    <div class="touch-system">
      <button id="gyroQuickCalibrate" class="sys-btn gyro-quick" type="button"><span>GYRO</span><small>CAL</small></button>
      <button id="gearBtn" class="sys-btn" type="button"><span>GEAR</span><small>G</small></button>
      <button id="actionBtn" class="sys-btn action" type="button"><span>ACTION</span><small>SPACE</small></button>
    </div>
    <div class="throttle-control">
      <span class="control-label">THROTTLE</span>
      <button data-touch="throttleUp" aria-label="Газ больше">+</button>
      <div class="throttle-slot"><i></i></div>
      <button data-touch="throttleDown" aria-label="Газ меньше">−</button>
    </div>
    <div class="yaw-control">
      <button data-touch="yawLeft" aria-label="Руль влево">◀</button><span>RUDDER</span><button data-touch="yawRight" aria-label="Руль вправо">▶</button>
    </div>
  </div>

  <div id="menu" class="panel hidden">
    <div class="panel-shell menu-sheet">
      <button class="close" data-close="menu" aria-label="Закрыть">×</button>
      <div class="panel-kicker">FLIGHT / PAUSED</div>
      <div class="panel-header">
        <div><h1>Пауза</h1><p>Продолжите полёт или откройте нужный раздел.</p></div>
        <div class="pill" id="menuPlane">—</div>
      </div>
      <div class="menu-grid">
        <button id="resumeBtn" class="big primary"><span>ПРОДОЛЖИТЬ ПОЛЁТ</span><small>Вернуться в кабину</small></button>
        <button id="menuMissions" class="big"><span>КАРЬЕРА И МИССИИ</span><small>Выбрать следующее задание</small></button>
        <button id="menuHangar" class="big"><span>АНГАР</span><small>Самолёты, характеристики, окраска</small></button>
        <button id="menuFree" class="big"><span>СВОБОДНЫЙ ПОЛЁТ</span><small>Сбросить активную миссию</small></button>
      </div>
      <div class="controls-card">
        <div class="settings-title"><b>Управление</b><span>INPUT</span></div>
        <p><kbd>Shift</kbd>/<kbd>Ctrl</kbd> газ · <kbd>W/S</kbd> тангаж · <kbd>A/D</kbd> крен · <kbd>Q/E</kbd> курс · <kbd>G</kbd> шасси · <kbd>C</kbd> камера</p>
        <div class="gyro-settings">
          <label class="gyro-toggle"><input id="gyroToggle" type="checkbox"> <span>Гироскоп</span></label>
          <button id="gyroCalibrate" type="button">КАЛИБРОВАТЬ</button>
          <label class="gyro-range"><span>Чувствительность</span><input id="gyroSensitivity" type="range" min="0.45" max="2.0" step="0.05" value="0.72"><b id="gyroSensitivityValue">0.72×</b></label>
          <small id="gyroStatus">Гироскоп выключен</small>
        </div>
      </div>
    </div>
  </div>

  <div id="missions" class="panel hidden">
    <div class="panel-shell wide content-sheet">
      <button class="close" data-close="missions" aria-label="Закрыть">×</button>
      <div class="panel-kicker">CAREER / OPERATIONS</div>
      <div class="panel-header">
        <div><h1>Полётные операции</h1><p>80 заданий от учебных маршрутов до посадок на авианосец.</p></div>
        <div class="pill">RINGS · CARGO · SAR · LANDING · RACE</div>
      </div>
      <div id="missionGrid" class="cards mission-cards"></div>
    </div>
  </div>

  <div id="hangar" class="panel hidden">
    <div class="panel-shell wide content-sheet">
      <button class="close" data-close="hangar" aria-label="Закрыть">×</button>
      <div class="panel-kicker">FLEET / HANGAR</div>
      <div class="panel-header">
        <div><h1>Ваш парк</h1><p>Выберите самолёт по задаче и стилю полёта.</p></div>
        <div class="pill"><span id="hangarCredits">0</span> CR</div>
      </div>
      <div class="hangar-tools">
        <input id="planeSearch" placeholder="Поиск самолёта" />
        <select id="planeFilter">
          <option value="all">Все классы</option><option value="light">Лёгкие</option><option value="glider">Планеры</option><option value="turboprop">Турбовинтовые</option><option value="regional">Региональные</option><option value="airliner">Лайнеры</option><option value="cargo">Грузовые</option><option value="military">Военные</option><option value="special">Особые</option>
        </select>
        <button id="paintBtn">ОКРАСКА</button>
      </div>
      <div id="planeGrid" class="cards plane-cards"></div>
      <div class="license-note">Стартовые самолёты и HD-карта находятся в APK. Остальные модели скачиваются один раз и затем работают офлайн.</div>
    </div>
  </div>

  <div id="toast" class="toast"></div>
  <script>
  (()=>{
    const key=(code)=>window.dispatchEvent(new KeyboardEvent('keydown',{code,bubbles:true}));
    const bind=(id,code)=>{const el=document.getElementById(id);if(el)el.addEventListener('click',()=>key(code));};
    bind('cameraBtn','KeyC'); bind('gearBtn','KeyG'); bind('actionBtn','Space'); bind('uiMenuBtn','KeyM');
  })();
  </script>
</body>
</html>
'''

css=r''':root{font-family:Inter,Roboto,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color:#f3f7fa;background:#080d12;--bg:#080d12;--panel:rgba(10,16,22,.94);--glass:rgba(8,14,20,.64);--glass2:rgba(15,22,29,.82);--line:rgba(255,255,255,.13);--line2:rgba(255,255,255,.07);--text:#f5f8fa;--muted:#8f9aa3;--cyan:#68e2ff;--amber:#ffcf67;--green:#73efb1;--red:#ff766d}*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#080d12;overscroll-behavior:none}button,input,select{font:inherit}button{cursor:pointer}button:focus-visible,input:focus-visible,select:focus-visible{outline:2px solid var(--cyan);outline-offset:2px}#game{position:fixed;inset:0;width:100%;height:100%;display:block}.hidden{display:none!important}.boot{position:fixed;inset:0;z-index:50;background:radial-gradient(120% 100% at 70% 15%,#1b3445 0,#0b141c 42%,#05080c 82%);display:flex;flex-direction:column;justify-content:space-between;padding:max(28px,env(safe-area-inset-top)) max(34px,env(safe-area-inset-right)) max(28px,env(safe-area-inset-bottom)) max(34px,env(safe-area-inset-left))}.boot:before{content:"";position:absolute;inset:0;background:linear-gradient(110deg,transparent 0 48%,rgba(104,226,255,.05) 48.2% 48.5%,transparent 48.7%),linear-gradient(rgba(255,255,255,.025) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.025) 1px,transparent 1px);background-size:auto,54px 54px,54px 54px;mask-image:linear-gradient(to bottom,black,transparent 82%);pointer-events:none}.boot-mark,.boot-bottom{position:relative}.logo{font-size:17px;font-weight:850;letter-spacing:.18em}.logo span{color:var(--cyan)}.boot-title{margin-top:9px;font-size:10px;letter-spacing:.2em;color:#91a0ab}.boot-bottom{width:min(540px,70vw)}.progress{height:2px;background:rgba(255,255,255,.12);overflow:hidden}.progress i{display:block;width:4%;height:100%;background:var(--cyan);box-shadow:0 0 18px rgba(104,226,255,.55);transition:width .3s}.muted{font-size:10px;letter-spacing:.08em;color:#9aa6af;margin-top:10px}.hud{position:fixed;inset:0;z-index:10;pointer-events:none;color:#fff;text-shadow:0 1px 4px rgba(0,0,0,.7)}.mission-strip{position:absolute;left:max(16px,env(safe-area-inset-left));top:max(12px,env(safe-area-inset-top));width:min(390px,45vw);display:flex;align-items:flex-start;gap:14px}.brand{padding-top:3px;font-size:8px;font-weight:900;letter-spacing:.2em;color:var(--cyan);white-space:nowrap}.mission-copy{min-width:0;padding-left:13px;border-left:1px solid rgba(255,255,255,.22)}.objective{font-size:17px;font-weight:760;line-height:1.05;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.subobjective{margin-top:5px;font-size:9px;line-height:1.25;color:rgba(255,255,255,.68);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.flight-data{position:absolute;width:70px;min-height:82px;padding:9px 8px 8px;background:linear-gradient(180deg,rgba(5,10,14,.70),rgba(5,10,14,.46));border-top:1px solid rgba(255,255,255,.32);border-bottom:1px solid rgba(255,255,255,.18);backdrop-filter:blur(7px);text-align:center}.flight-data small{display:block;font-size:7px;letter-spacing:.19em;color:rgba(255,255,255,.58)}.flight-data b{display:block;margin-top:4px;font:700 27px/1 ui-monospace,SFMono-Regular,Consolas,monospace;letter-spacing:-.05em}.flight-data span{display:block;margin-top:5px;font-size:6px;letter-spacing:.13em;color:rgba(255,255,255,.52)}.speed-tape{left:max(16px,env(safe-area-inset-left));top:50%;transform:translateY(-52%);border-left:2px solid var(--cyan)}.altitude-tape{right:max(16px,env(safe-area-inset-right));top:50%;transform:translateY(-52%);border-right:2px solid var(--cyan)}.throttle-tape{left:max(16px,env(safe-area-inset-left));top:calc(50% + 94px);min-height:53px}.throttle-tape b{font-size:19px}.wallet{position:absolute;right:max(16px,env(safe-area-inset-right));top:max(12px,env(safe-area-inset-top));height:27px;display:flex;align-items:center;gap:7px;padding:0 9px;background:rgba(5,10,14,.52);border:1px solid rgba(255,255,255,.1);border-radius:4px;backdrop-filter:blur(6px)}.wallet span{font-size:7px;font-weight:900;color:var(--amber);letter-spacing:.15em}.wallet b{font:700 11px/1 ui-monospace,monospace;color:#fff}.center-marker{position:absolute;left:50%;top:49%;width:126px;height:64px;transform:translate(-50%,-50%);opacity:.78}.center-marker .wing{position:absolute;top:31px;width:45px;border-top:1.5px solid rgba(196,245,255,.92)}.center-marker .wing-l{left:0}.center-marker .wing-r{right:0}.center-marker .dot{position:absolute;left:50%;top:27px;width:8px;height:8px;border:1.5px solid rgba(196,245,255,.92);border-radius:50%;transform:translateX(-50%)}.center-marker .pitch{position:absolute;left:50%;width:22px;border-top:1px solid rgba(196,245,255,.45);transform:translateX(-50%)}.center-marker .p1{top:9px}.center-marker .p2{top:53px}.nav-card{position:absolute;right:max(16px,env(safe-area-inset-right));top:52px;width:136px;background:rgba(5,10,14,.58);border:1px solid rgba(255,255,255,.11);backdrop-filter:blur(7px);border-radius:5px;overflow:hidden}.nav-head{height:23px;display:flex;align-items:center;justify-content:space-between;padding:0 8px;border-bottom:1px solid rgba(255,255,255,.08)}.nav-head span{font-size:7px;font-weight:900;letter-spacing:.18em;color:var(--cyan)}.nav-head small{font-size:6px;letter-spacing:.12em;color:rgba(255,255,255,.43)}#minimap{display:block;width:134px;height:105px;background:rgba(1,5,8,.55)}.warning{position:absolute;left:50%;top:23%;transform:translate(-50%,-50%);font-size:17px;font-weight:900;letter-spacing:.12em;color:var(--red);text-shadow:0 2px 12px #000}.hint{position:absolute;left:50%;bottom:66px;transform:translateX(-50%);padding:5px 8px;background:rgba(3,7,10,.48);font-size:7px;letter-spacing:.04em;color:rgba(255,255,255,.55);border-radius:3px;white-space:nowrap}.bottom-dock{position:absolute;left:50%;bottom:max(11px,env(safe-area-inset-bottom));transform:translateX(-50%);display:flex;gap:3px;padding:3px;background:rgba(5,9,13,.60);border:1px solid rgba(255,255,255,.10);border-radius:7px;backdrop-filter:blur(10px);pointer-events:auto;box-shadow:0 8px 28px rgba(0,0,0,.22)}.bottom-dock button{width:68px;height:44px;border:0;background:transparent;color:rgba(255,255,255,.78);border-radius:5px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px}.bottom-dock button:active,.bottom-dock button:hover{background:rgba(104,226,255,.10);color:#fff}.bottom-dock svg{width:16px;height:16px;fill:none;stroke:currentColor;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round}.bottom-dock span{font-size:6px;font-weight:800;letter-spacing:.1em}.touch{position:fixed;inset:0;z-index:12;pointer-events:none}.stick-wrap{position:absolute;left:max(19px,env(safe-area-inset-left));bottom:max(20px,env(safe-area-inset-bottom));width:118px;height:132px}.stick-wrap>span,.control-label{display:block;text-align:center;margin-bottom:5px;font-size:6px;font-weight:900;letter-spacing:.18em;color:rgba(255,255,255,.36)}.stick{position:relative;width:112px;height:112px;margin:auto;border-radius:50%;border:1px solid rgba(255,255,255,.18);background:radial-gradient(circle,rgba(255,255,255,.055),rgba(0,0,0,.13));pointer-events:auto}.stick:before,.stick:after{content:"";position:absolute;background:rgba(255,255,255,.10)}.stick:before{left:50%;top:13px;bottom:13px;width:1px}.stick:after{top:50%;left:13px;right:13px;height:1px}.stick i{position:absolute;left:50%;top:50%;width:43px;height:43px;border-radius:50%;transform:translate(-50%,-50%);background:rgba(226,244,251,.17);border:1px solid rgba(255,255,255,.25);box-shadow:0 0 0 5px rgba(0,0,0,.08)}.throttle-control{position:absolute;right:max(20px,env(safe-area-inset-right));bottom:max(17px,env(safe-area-inset-bottom));width:47px;display:grid;grid-template-rows:auto 38px 28px 38px;gap:3px;pointer-events:auto}.throttle-control button{border:1px solid rgba(255,255,255,.16);background:rgba(5,10,14,.54);color:#fff;border-radius:5px;font-size:20px}.throttle-slot{position:relative;width:4px;height:28px;margin:auto;background:rgba(255,255,255,.13);border-radius:3px}.throttle-slot i{position:absolute;left:-3px;bottom:35%;width:10px;height:2px;background:var(--cyan)}.yaw-control{position:absolute;right:max(82px,calc(env(safe-area-inset-right) + 82px));bottom:max(18px,env(safe-area-inset-bottom));display:grid;grid-template-columns:37px auto 37px;align-items:center;gap:6px;pointer-events:auto}.yaw-control button{width:37px;height:34px;border:1px solid rgba(255,255,255,.14);border-radius:5px;background:rgba(5,10,14,.50);color:rgba(255,255,255,.72);font-size:11px}.yaw-control span{font-size:5px;letter-spacing:.12em;color:rgba(255,255,255,.34)}.touch-system{position:absolute;right:max(82px,calc(env(safe-area-inset-right) + 82px));bottom:61px;display:flex;gap:5px;pointer-events:auto}.sys-btn{width:54px;height:43px;border:1px solid rgba(255,255,255,.14);border-radius:6px;background:rgba(5,10,14,.52);color:rgba(255,255,255,.78);display:flex;flex-direction:column;align-items:center;justify-content:center}.sys-btn span{font-size:7px;font-weight:900;letter-spacing:.08em}.sys-btn small{font-size:5px;margin-top:2px;color:rgba(255,255,255,.36)}.sys-btn.action{border-color:rgba(104,226,255,.28);color:var(--cyan)}.panel{position:fixed;inset:0;z-index:30;background:rgba(0,3,6,.47);backdrop-filter:blur(8px);display:flex;align-items:stretch;padding:max(8px,env(safe-area-inset-top)) max(8px,env(safe-area-inset-right)) max(8px,env(safe-area-inset-bottom)) max(8px,env(safe-area-inset-left))}.panel-shell{position:relative;width:min(470px,94vw);height:100%;overflow:auto;background:linear-gradient(180deg,rgba(15,22,29,.98),rgba(7,12,17,.98));border:1px solid rgba(255,255,255,.10);border-radius:8px;padding:24px 22px;box-shadow:18px 0 70px rgba(0,0,0,.42)}.panel-shell.wide{width:min(1060px,93vw)}.menu-sheet{margin-left:0}.content-sheet{margin:auto}.close{position:absolute;right:13px;top:12px;width:34px;height:34px;border:1px solid rgba(255,255,255,.11);background:rgba(255,255,255,.045);color:#fff;border-radius:5px;font-size:20px;line-height:1}.panel-kicker{font-size:7px;font-weight:900;letter-spacing:.22em;color:var(--cyan);margin-bottom:9px}.panel-header{display:flex;align-items:flex-end;justify-content:space-between;gap:20px;padding-right:42px;margin-bottom:18px}.panel-header h1{margin:0;font-size:28px;font-weight:730;letter-spacing:-.03em}.panel-header p{margin:6px 0 0;font-size:10px;color:var(--muted);line-height:1.35}.pill{flex:0 0 auto;min-height:28px;display:flex;align-items:center;padding:0 9px;border:1px solid rgba(255,255,255,.11);border-radius:4px;background:rgba(255,255,255,.035);font-size:7px;font-weight:800;letter-spacing:.08em;color:#b8c2c9}.menu-grid{display:grid;grid-template-columns:1fr;gap:6px}.big{min-height:58px;border:1px solid rgba(255,255,255,.09);background:rgba(255,255,255,.035);color:#fff;border-radius:5px;text-align:left;padding:10px 13px;display:flex;flex-direction:column;justify-content:center}.big span{font-size:10px;font-weight:850;letter-spacing:.04em}.big small{font-size:8px;color:#7f8b94;margin-top:4px}.big:hover{background:rgba(255,255,255,.065);border-color:rgba(255,255,255,.15)}.big.primary{background:linear-gradient(90deg,rgba(53,183,212,.23),rgba(53,183,212,.07));border-color:rgba(104,226,255,.35);box-shadow:inset 3px 0 0 var(--cyan)}.controls-card{margin-top:16px;padding:13px;border-top:1px solid rgba(255,255,255,.08);background:rgba(0,0,0,.10);font-size:8px;color:#9da8b0}.settings-title{display:flex;justify-content:space-between;align-items:center}.settings-title b{font-size:9px;color:#eef3f6}.settings-title span{font-size:6px;letter-spacing:.16em;color:#6f7b84}.controls-card p{margin:8px 0;line-height:1.65}.controls-card kbd{padding:1px 4px;border:1px solid rgba(255,255,255,.10);border-radius:3px;background:rgba(255,255,255,.05);color:#d5dde2}.gyro-settings{margin-top:9px;padding-top:9px;border-top:1px solid rgba(255,255,255,.07);display:grid;grid-template-columns:auto auto 1fr;gap:8px;align-items:center}.gyro-toggle{display:flex;align-items:center;gap:7px;font-size:8px;color:#d9e1e6}.gyro-toggle input{width:15px;height:15px;accent-color:var(--cyan)}.gyro-settings button{height:28px;border:1px solid rgba(104,226,255,.25);background:rgba(104,226,255,.07);color:#dff8ff;border-radius:4px;font-size:7px;font-weight:800}.gyro-range{display:flex;align-items:center;gap:7px}.gyro-range span{font-size:7px}.gyro-range input{flex:1;accent-color:var(--cyan)}.gyro-range b{min-width:34px;color:#eef6fa}.gyro-settings small{grid-column:1/-1;font-size:7px;color:#79ccdf}.cards{display:grid;gap:6px}.mission-cards{grid-template-columns:1fr}.card{position:relative;border:1px solid rgba(255,255,255,.085);background:rgba(255,255,255,.028);border-radius:5px;padding:11px 12px;min-height:88px}.mission-cards .card{display:grid;grid-template-columns:58px minmax(0,1fr) 116px;grid-template-areas:"num title action" "num desc action" "num meta action";column-gap:12px;align-items:center}.mission-cards .card .num{grid-area:num;align-self:stretch;display:flex;align-items:center;justify-content:center;border-right:1px solid rgba(255,255,255,.07);font:700 15px/1 ui-monospace,monospace;color:var(--cyan)}.card h3{margin:0;font-size:13px;font-weight:720}.mission-cards .card h3{grid-area:title;align-self:end}.card p{margin:5px 0 0;font-size:8px;line-height:1.35;color:#87939c}.mission-cards .card p{grid-area:desc}.card .meta{display:flex;gap:5px;flex-wrap:wrap;margin-top:6px}.mission-cards .card .meta{grid-area:meta;align-self:start}.tag{padding:3px 5px;border:1px solid rgba(255,255,255,.09);border-radius:3px;font-size:6px;letter-spacing:.06em;color:#9ba7af}.card button{height:34px;border:1px solid rgba(104,226,255,.22);background:rgba(104,226,255,.07);color:#e8fbff;border-radius:4px;font-size:7px;font-weight:850;letter-spacing:.05em}.mission-cards .card button{grid-area:action;width:100%;align-self:center}.card.locked{opacity:.44}.card.complete{border-left:2px solid var(--green)}.card.complete .num{color:var(--green)}.hangar-tools{display:grid;grid-template-columns:minmax(180px,1fr) 180px 100px;gap:6px;margin-bottom:9px}.hangar-tools input,.hangar-tools select,.hangar-tools button{height:35px;border:1px solid rgba(255,255,255,.10);background:rgba(255,255,255,.035);color:#e6edf1;border-radius:4px;padding:0 10px;font-size:8px;outline:0}.hangar-tools option{background:#111920}.hangar-tools button{font-weight:850;color:var(--cyan)}.plane-cards{grid-template-columns:repeat(3,minmax(0,1fr))}.plane-card{min-height:174px;padding:13px}.plane-card.selected{border-color:rgba(104,226,255,.48);box-shadow:inset 3px 0 0 var(--cyan)}.plane-card .num{font-size:6px;letter-spacing:.12em;color:var(--cyan)}.plane-card h3{font-size:14px;margin-top:5px}.stats{display:grid;grid-template-columns:repeat(3,1fr);gap:4px;margin:10px 0}.stat{padding:6px;background:rgba(255,255,255,.025);border-top:1px solid rgba(255,255,255,.06)}.stat small{display:block;font-size:5px;color:#73808a}.stat b{font-size:8px}.price{color:var(--amber);font-weight:800}.storage{margin:7px 0 4px;padding-top:6px;border-top:1px solid rgba(255,255,255,.055);font-size:6px;font-weight:800;letter-spacing:.08em;color:var(--amber)}.storage.ready{color:var(--green)}.license{font-size:6px;color:#6f7b84;margin-top:6px}.license-note{font-size:7px;color:#68747d;margin-top:11px}.toast{position:fixed;z-index:60;left:50%;bottom:74px;transform:translate(-50%,12px);opacity:0;pointer-events:none;min-width:180px;max-width:70vw;padding:9px 12px;background:rgba(8,14,19,.92);border:1px solid rgba(255,255,255,.10);border-radius:5px;text-align:center;font-size:8px;font-weight:700;box-shadow:0 12px 35px rgba(0,0,0,.38);transition:.2s}.toast.show{opacity:1;transform:translate(-50%,0)}@media(max-width:820px){.mission-strip{width:44vw}.objective{font-size:14px}.subobjective{font-size:8px}.flight-data{width:62px;min-height:70px;padding:8px 6px}.flight-data b{font-size:22px}.throttle-tape{top:calc(50% + 80px);min-height:47px}.nav-card{width:118px}.nav-card #minimap{width:116px;height:89px}.bottom-dock button{width:59px;height:40px}.bottom-dock svg{width:14px;height:14px}.bottom-dock span{font-size:5px}.panel-shell.wide{width:96vw}.plane-cards{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:600px){.brand{display:none}.mission-copy{padding-left:0;border-left:0}.mission-strip{width:48vw}.wallet{display:none}.hint{display:none}.mission-cards .card{grid-template-columns:42px minmax(0,1fr) 90px;column-gap:8px}.panel-header h1{font-size:22px}.panel-header p{display:none}.pill{display:none}.hangar-tools{grid-template-columns:1fr 110px}.hangar-tools button{grid-column:1/-1}.plane-cards{grid-template-columns:1fr}}@media(orientation:landscape) and (max-height:620px){.mission-strip{left:max(11px,env(safe-area-inset-left));top:max(8px,env(safe-area-inset-top));width:42vw}.objective{font-size:13px}.subobjective{font-size:7px}.speed-tape{left:max(11px,env(safe-area-inset-left))}.altitude-tape{right:max(11px,env(safe-area-inset-right))}.flight-data{width:57px;min-height:63px}.flight-data b{font-size:20px}.throttle-tape{left:max(11px,env(safe-area-inset-left));top:calc(50% + 70px);min-height:43px}.throttle-tape b{font-size:16px}.wallet{right:max(11px,env(safe-area-inset-right));top:max(8px,env(safe-area-inset-top))}.nav-card{right:max(11px,env(safe-area-inset-right));top:41px;width:108px}.nav-card #minimap{width:106px;height:76px}.center-marker{width:112px}.bottom-dock{bottom:max(7px,env(safe-area-inset-bottom))}.bottom-dock button{width:55px;height:35px}.bottom-dock svg{width:13px;height:13px}.stick-wrap{left:max(13px,env(safe-area-inset-left));bottom:max(12px,env(safe-area-inset-bottom));width:98px;height:109px}.stick{width:92px;height:92px}.stick i{width:36px;height:36px}.touch-system{right:max(67px,calc(env(safe-area-inset-right) + 67px));bottom:52px}.sys-btn{width:46px;height:35px}.throttle-control{right:max(12px,env(safe-area-inset-right));bottom:max(9px,env(safe-area-inset-bottom));width:42px;grid-template-rows:auto 32px 22px 32px}.yaw-control{right:max(67px,calc(env(safe-area-inset-right) + 67px));bottom:max(10px,env(safe-area-inset-bottom))}.panel-shell{padding:18px;height:100%}.panel-header{margin-bottom:12px}.panel-header h1{font-size:22px}.big{min-height:48px}.controls-card{margin-top:10px}.content-sheet{padding:16px 18px}.card{min-height:76px}.mission-cards .card{min-height:76px}.plane-card{min-height:150px}}'''

html_p.write_text(html)
css_p.write_text(css)
print('Applied V7 simulator UI redesign')
