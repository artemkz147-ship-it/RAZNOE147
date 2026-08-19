(() => {
  'use strict';

  const $ = (s, root = document) => root.querySelector(s);
  const $$ = (s, root = document) => [...root.querySelectorAll(s)];
  const launchModal = $('#launchModal');
  const settingsModal = $('#settingsModal');
  const toast = $('#toast');
  const APP_ORIGIN = location.origin;
  const defaults = {
    volume: 80,
    touchMode: 'full',
    stretch: false,
    vibration: true,
    virtual8: 'dpad',
    virtualPs: 'both',
    quickSlot: 1
  };

  let selected = { core: 'nes', system: 'Dendy / NES', extensions: 'NES, UNIF, UNF, ZIP, 7Z, RAR' };
  let toastTimer = 0;
  let biosFiles = [];
  let playing = false;
  let gameFrame = null;
  let bootTimer = 0;
  let gameMessageHandler = null;
  let romObjectUrl = '';
  let biosObjectUrl = '';

  function normalizeSettings(raw = {}) {
    const next = { ...defaults, ...(raw && typeof raw === 'object' ? raw : {}) };
    if (!raw.touchMode && typeof raw.touchControls === 'boolean') next.touchMode = raw.touchControls ? 'full' : 'off';
    if (!['full', 'minimal', 'off'].includes(next.touchMode)) next.touchMode = 'full';
    next.volume = Math.max(0, Math.min(100, Number(next.volume ?? 80)));
    next.quickSlot = Math.max(1, Math.min(9, Number(next.quickSlot || 1)));
    return next;
  }

  function getSettings() {
    try { return normalizeSettings(JSON.parse(localStorage.getItem('retro-settings') || '{}')); }
    catch (_) { return { ...defaults }; }
  }

  function storeSettings(settings) {
    const normalized = normalizeSettings(settings);
    localStorage.setItem('retro-settings', JSON.stringify(normalized));
    return normalized;
  }

  function readGlobalForm() {
    const old = getSettings();
    return normalizeSettings({
      ...old,
      volume: Number($('#volume')?.value ?? old.volume),
      touchMode: $('#touchMode')?.value || old.touchMode,
      stretch: !!$('#stretch')?.checked,
      vibration: !!$('#vibration')?.checked,
      virtual8: $('#virtual8')?.value || old.virtual8,
      virtualPs: $('#virtualPs')?.value || old.virtualPs
    });
  }

  function updateSettingsForm() {
    const s = getSettings();
    if ($('#volume')) $('#volume').value = s.volume;
    if ($('#volumeValue')) $('#volumeValue').value = `${s.volume}%`;
    if ($('#touchMode')) $('#touchMode').value = s.touchMode;
    if ($('#stretch')) $('#stretch').checked = !!s.stretch;
    if ($('#vibration')) $('#vibration').checked = !!s.vibration;
    if ($('#virtual8')) $('#virtual8').value = s.virtual8 || 'dpad';
    if ($('#virtualPs')) $('#virtualPs').value = s.virtualPs || 'both';
  }

  function saveSettings() {
    storeSettings(readGlobalForm());
    closeModal(settingsModal);
    showToast('Настройки сохранены');
  }

  function showToast(message, long = false) {
    if (!toast) return;
    toast.textContent = message;
    toast.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove('show'), long ? 5200 : 2300);
  }

  function vibrate(ms = 20) {
    if (!getSettings().vibration) return;
    try { navigator.vibrate?.(ms); } catch (_) {}
  }

  function openLaunch(card) {
    selected = { core: card.dataset.core, system: card.dataset.system, extensions: card.dataset.extensions };
    $$('.console-card').forEach(c => c.classList.toggle('selected', c === card));
    $('#launchKicker').textContent = `СИСТЕМА ${card.querySelector('.console-number').textContent}`;
    $('#launchTitle').textContent = selected.system;
    $('#fileFormats').textContent = `Поддерживаются: ${selected.extensions}`;
    const ps = selected.core === 'psx';
    $('#biosRow').hidden = !ps;
    $('.privacy-note').textContent = ps
      ? 'Для PS1 приложение автоматически выберет подходящий BIOS из добавленных.'
      : 'ROM и сохранения остаются только на этом устройстве.';
    updateBiosState();
    launchModal.hidden = false;
    vibrate();
  }

  function openSettings() {
    updateSettingsForm();
    updateGamepadStatus();
    settingsModal.hidden = false;
    vibrate();
  }

  function closeModal(modal) {
    if (modal) modal.hidden = true;
    vibrate(10);
  }

  function biosRegion(name) {
    const n = String(name || '').toLowerCase();
    if (/5500|7000|1000|japan|jpn|ntsc.?j|jp\b/.test(n)) return 'JP';
    if (/5502|7002|1002|europe|eur|pal/.test(n)) return 'EU';
    if (/5501|7001|1001|usa|us\b|ntsc.?u/.test(n)) return 'US';
    return 'AUTO';
  }

  function romRegion(name) {
    const n = String(name || '').toLowerCase();
    if (/\(j\)|\[j\]|japan|ntsc.?j|slps|scps|slpm|sczs/.test(n)) return 'JP';
    if (/\(e\)|\[e\]|europe|pal|sles|sces|sced/.test(n)) return 'EU';
    if (/\(u\)|\[u\]|usa|ntsc.?u|slus|scus/.test(n)) return 'US';
    return 'AUTO';
  }

  function chooseBiosForRom(file) {
    if (!biosFiles.length) return null;
    const wanted = romRegion(file?.name);
    if (wanted !== 'AUTO') {
      const exact = biosFiles.find(f => biosRegion(f.name) === wanted);
      if (exact) return exact;
    }
    return biosFiles.find(f => /scph.?5501/i.test(f.name))
      || biosFiles.find(f => biosRegion(f.name) === 'US')
      || biosFiles[0];
  }

  function updateBiosState() {
    const state = $('#biosState');
    const list = $('#biosList');
    const clear = $('#biosClear');
    if (!state || !list || !clear) return;
    list.replaceChildren();
    clear.hidden = biosFiles.length === 0;
    if (!biosFiles.length) {
      state.textContent = 'Не выбран — можно добавить несколько регионов';
      return;
    }
    const regions = [...new Set(biosFiles.map(f => biosRegion(f.name)))].filter(x => x !== 'AUTO');
    state.textContent = `Выбрано: ${biosFiles.length}${regions.length ? ` · ${regions.join(' / ')}` : ''}`;
    biosFiles.forEach((file, index) => {
      const chip = document.createElement('span');
      chip.className = 'bios-chip';
      const label = document.createElement('span');
      label.textContent = `${file.name} · ${biosRegion(file.name)}`;
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.textContent = '×';
      remove.setAttribute('aria-label', `Удалить ${file.name}`);
      remove.dataset.action = 'bios-remove';
      remove.dataset.index = String(index);
      chip.append(label, remove);
      list.appendChild(chip);
    });
  }

  function updateGamepadStatus() {
    let pads = [];
    try { pads = [...(navigator.getGamepads?.() || [])].filter(Boolean); } catch (_) {}
    const names = pads.map(p => p.id).filter(Boolean);
    const connected = names.length > 0;
    $('#gamepadStatus')?.classList.toggle('connected', connected);
    if ($('#gamepadText')) $('#gamepadText').textContent = connected ? names.join(' • ') : 'Геймпад не подключён';
  }

  function extensionOf(name) {
    const i = String(name || '').lastIndexOf('.');
    return i >= 0 ? name.slice(i + 1).toLowerCase() : '';
  }

  function loadControlProfile(core) {
    try {
      const value = JSON.parse(localStorage.getItem(`retro-controls-${core}`) || 'null');
      return value && typeof value === 'object' ? value : null;
    } catch (_) { return null; }
  }

  function saveControlProfile(core, controls) {
    if (!controls || typeof controls !== 'object') return;
    try { localStorage.setItem(`retro-controls-${core}`, JSON.stringify(controls)); } catch (_) {}
  }

  function allowedExtensions(core) {
    if (core === 'nes') return ['nes', 'unif', 'unf', 'zip', '7z', 'rar'];
    if (core === 'segaMD') return ['md', 'smd', 'gen', 'bin', 'mdx', '68k', 'sgd', 'sms', 'gg', 'sg', 'bms', 'zip', '7z', 'rar'];
    return ['chd', 'pbp', 'bin', 'iso', 'img', 'exe', 'zip', '7z', 'rar'];
  }

  function chooseFiles({ accept = '', multiple = false, onFiles }) {
    const input = document.createElement('input');
    input.type = 'file';
    input.multiple = !!multiple;
    input.style.position = 'fixed';
    input.style.left = '-10000px';
    if (accept) input.accept = accept;
    input.addEventListener('change', () => {
      const files = [...(input.files || [])];
      input.remove();
      if (files.length) onFiles(files);
    }, { once: true });
    document.body.appendChild(input);
    input.click();
  }

  function pickBios() {
    if (selected.core !== 'psx') return;
    chooseFiles({
      accept: '.bin,.rom,.bios,application/octet-stream',
      multiple: true,
      onFiles(files) {
        const valid = files.filter(f => f.size > 0);
        if (!valid.length) return showToast('Не удалось добавить BIOS: файлы пустые.', true);
        for (const file of valid) {
          if (!biosFiles.some(old => old.name === file.name && old.size === file.size)) biosFiles.push(file);
        }
        updateBiosState();
        showToast(`BIOS добавлено: ${valid.length}`);
      }
    });
  }

  function clearBios() {
    biosFiles = [];
    updateBiosState();
    showToast('Список BIOS очищен');
  }

  function removeBios(index) {
    const i = Number(index);
    if (!Number.isInteger(i) || i < 0 || i >= biosFiles.length) return;
    biosFiles.splice(i, 1);
    updateBiosState();
  }

  function pickRom() {
    vibrate(30);
    const allowed = allowedExtensions(selected.core);
    chooseFiles({
      accept: allowed.map(x => `.${x}`).join(','),
      onFiles(files) {
        const file = files[0];
        const ext = extensionOf(file.name);
        if (!file.size) return showToast('Файл игры пустой. Выберите другой файл.', true);
        if (!allowed.includes(ext)) return showToast(`Формат .${ext || '?'} не подходит для ${selected.system}.`, true);
        startGame(file);
      }
    });
  }

  function revokeObjectUrls() {
    for (const value of [romObjectUrl, biosObjectUrl]) {
      if (!value) continue;
      try { URL.revokeObjectURL(value); } catch (_) {}
    }
    romObjectUrl = '';
    biosObjectUrl = '';
  }

  function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
  }

  function runtimeDirectionLabel(settings) {
    return selected.core === 'psx' ? (settings.virtualPs || 'both') : (settings.virtual8 || 'dpad');
  }

  function runtimeDirectionOptions(settings) {
    if (selected.core === 'psx') {
      return `<option value="both"${settings.virtualPs === 'both' ? ' selected' : ''}>Крестовина + стик</option>
        <option value="dpad"${settings.virtualPs === 'dpad' ? ' selected' : ''}>Только крестовина</option>
        <option value="stick"${settings.virtualPs === 'stick' ? ' selected' : ''}>Только стик</option>`;
    }
    return `<option value="dpad"${settings.virtual8 === 'dpad' ? ' selected' : ''}>Крестовина</option>
      <option value="stick"${settings.virtual8 === 'stick' ? ' selected' : ''}>Стик</option>`;
  }

  function touchModeOptions(settings) {
    return `<option value="full"${settings.touchMode === 'full' ? ' selected' : ''}>Полный</option>
      <option value="minimal"${settings.touchMode === 'minimal' ? ' selected' : ''}>Только Start + Select</option>
      <option value="off"${settings.touchMode === 'off' ? ' selected' : ''}>Выключен</option>`;
  }

  function installPlayShell(file) {
    const settings = getSettings();
    const style = document.createElement('style');
    style.id = 'runtimeStyles';
    style.textContent = `
      html,body{width:100%;height:100%;overflow:hidden;background:#000!important}
      body.playing>.app-shell,body.playing>#launchModal,body.playing>#settingsModal,body.playing>.toast{display:none!important}
      #playScreen{position:fixed;inset:0;background:#000;z-index:9999;overflow:hidden;font-family:Inter,Roboto,system-ui,sans-serif}
      #gameFrame{display:block;position:absolute;inset:0;width:100%;height:100%;border:0;background:#000;visibility:hidden}
      #runtimeBar{position:absolute;inset:10px max(10px,env(safe-area-inset-right)) auto max(10px,env(safe-area-inset-left));z-index:100004;display:flex;justify-content:space-between;pointer-events:none}
      #runtimeBar button{pointer-events:auto;border:1px solid rgba(255,255,255,.20);background:rgba(8,9,12,.82);color:#fff;border-radius:11px;height:40px;padding:0 13px;font:850 12px system-ui,sans-serif;backdrop-filter:blur(10px)}
      #runtimeBar .runtimeGear{width:40px;padding:0;font-size:18px}
      #bootOverlay{position:absolute;inset:0;display:grid;place-items:center;background:#08090c;color:white;z-index:100002;text-align:center;padding:24px;transition:opacity .18s}
      #bootOverlay .box{max-width:500px}#bootOverlay strong{display:block;font-size:23px;margin-bottom:8px}#bootOverlay span{display:block;color:#a9b0bc;line-height:1.45;font-size:13px}#bootOverlay small{display:block;color:#666f7c;margin-top:10px;font-size:10px}#bootOverlay.error span{color:#ff9a9a}
      #bootOverlay button{margin-top:16px;padding:11px 17px;border:0;border-radius:9px;font-weight:850}.bootSpinner{width:32px;height:32px;border:3px solid rgba(255,255,255,.16);border-top-color:#fff;border-radius:50%;margin:0 auto 16px;animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
      #runtimeSettings{position:absolute;inset:0;z-index:100006;background:rgba(0,0,0,.72);backdrop-filter:blur(9px);display:grid;place-items:center;padding:12px}#runtimeSettings[hidden]{display:none}
      .runtimePanel{width:min(650px,94vw);max-height:94vh;overflow:auto;background:linear-gradient(145deg,#181c25,#0e1117);border:1px solid #353c49;border-radius:18px;padding:18px 20px;box-shadow:0 30px 90px #000}
      .runtimeHead{display:flex;justify-content:space-between;align-items:center;margin-bottom:9px}.runtimeHead div{display:flex;flex-direction:column}.runtimeHead strong{font-size:20px}.runtimeHead small{color:#8f97a7;font-size:9px;margin-top:2px}.runtimeHead button{width:34px;height:34px;border:1px solid #343b47;border-radius:9px;background:#20242d;color:#fff;font-size:20px}
      .rRow{min-height:45px;border-top:1px solid #292f3a;display:flex;align-items:center;justify-content:space-between;gap:12px}.rRow>span{display:flex;flex-direction:column}.rRow strong{font-size:11px}.rRow small{color:#8f97a7;font-size:8px;margin-top:2px}.rRow input[type=range]{width:min(180px,30vw);accent-color:#ffe45c}.rRow select{height:32px;max-width:225px;border:1px solid #343b47;border-radius:8px;background:#10131a;color:#fff;padding:0 9px;font-size:10px}
      .quickBlock{display:grid;grid-template-columns:1fr 1fr auto;gap:8px;padding:11px 0;border-top:1px solid #292f3a}.quickBlock button,.keysBtn{height:38px;border:1px solid #343b47;border-radius:9px;background:#20252f;color:#fff;font-weight:850;font-size:10px}.quickBlock .saveBtn{background:#ffe45c;color:#111;border-color:#ffe45c}.quickBlock select{border:1px solid #343b47;border-radius:9px;background:#10131a;color:#fff;padding:0 8px;font-size:10px}
      .keysBtn{width:100%;display:flex;justify-content:space-between;align-items:center;padding:0 12px;margin-bottom:8px;background:#2b3140;border-color:#454f61}.keysBtn b{color:#ffe45c}.keysBtn small{color:#aeb6c5;font-weight:600}#runtimeNotice{min-height:18px;color:#ffe45c;font-size:9px;text-align:center;padding-top:5px}
      @media(max-height:430px){.runtimePanel{padding:11px 15px}.runtimeHead{margin-bottom:3px}.runtimeHead strong{font-size:17px}.rRow{min-height:35px}.rRow small{display:none}.quickBlock{padding:6px 0}.quickBlock button,.keysBtn{height:33px}}
    `;
    document.head.appendChild(style);
    document.body.classList.add('playing');
    const slot = Math.max(1, Math.min(9, Number(settings.quickSlot || 1)));
    const slots = Array.from({length:9},(_,i)=>`<option value="${i+1}"${slot===i+1?' selected':''}>Слот ${i+1}</option>`).join('');
    const isPs = selected.core === 'psx';
    document.body.insertAdjacentHTML('beforeend', `
      <div id="playScreen">
        <iframe id="gameFrame" src="game.html" allow="autoplay; fullscreen; gamepad" referrerpolicy="no-referrer"></iframe>
        <div id="runtimeBar"><button id="backToMenu">← Меню</button><button id="openRuntimeSettings" class="runtimeGear" aria-label="Настройки">⚙</button></div>
        <div id="bootOverlay"><div class="box"><div class="bootSpinner"></div><strong>${escapeHtml(selected.system)}</strong><span>Подготавливаем ${escapeHtml(file.name)}…</span><small>Технические меню эмулятора скрыты.</small></div></div>
        <div id="runtimeSettings" hidden>
          <section class="runtimePanel">
            <div class="runtimeHead"><div><strong>Настройки игры</strong><small>${escapeHtml(selected.system)}</small></div><button id="closeRuntimeSettings">×</button></div>
            <label class="rRow"><span><strong>Громкость</strong><small>Применяется сразу</small></span><input id="rVolume" type="range" min="0" max="100" step="5" value="${Number(settings.volume)}"></label>
            <label class="rRow"><span><strong>Виртуальный геймпад</strong><small>Полный / только Start + Select / выключен</small></span><select id="rTouchMode">${touchModeOptions(settings)}</select></label>
            <label class="rRow"><span><strong>Растянуть изображение</strong><small>Заполнить весь экран</small></span><input id="rStretch" type="checkbox"${settings.stretch?' checked':''}></label>
            <label class="rRow"><span><strong>${isPs ? 'PS1 — движение' : 'Движение слева'}</strong><small>${isPs ? 'Крестовина, стик или оба' : 'Физический стик всегда дублирует крестовину'}</small></span><select id="rDirection">${runtimeDirectionOptions(settings)}</select></label>
            <div class="quickBlock"><button id="quickSave" class="saveBtn">Быстро сохранить</button><button id="quickLoad">Быстро загрузить</button><select id="quickSlot">${slots}</select></div>
            <button class="keysBtn" id="openKeySettings"><span><b>Настроить кнопки</b><small>Обычные кнопки + Quick Save / Quick Load</small></span><strong>→</strong></button>
            <div id="runtimeNotice"></div>
          </section>
        </div>
      </div>
    `;
    $('#backToMenu')?.addEventListener('click', stopGame);
    $('#openRuntimeSettings')?.addEventListener('click', openRuntimeSettings);
    $('#closeRuntimeSettings')?.addEventListener('click', closeRuntimeSettings);
    $('#quickSave')?.addEventListener('click', () => quickAction('retro-quick-save'));
    $('#quickLoad')?.addEventListener('click', () => quickAction('retro-quick-load'));
    $('#openKeySettings')?.addEventListener('click', openKeySettings);
    for (const id of ['rVolume','rTouchMode','rStretch','rDirection','quickSlot']) {
      $(`#${id}`)?.addEventListener('change', applyRuntimeSettings);
      if (id === 'rVolume') $(`#${id}`)?.addEventListener('input', applyRuntimeSettings);
    }
    return settings;
  }

  function runtimeNotice(message, error = false) {
    const el = $('#runtimeNotice');
    if (!el) return;
    el.textContent = message;
    el.style.color = error ? '#ff9a9a' : '#ffe45c';
  }

  function collectRuntimeSettings() {
    const base = getSettings();
    const direction = $('#rDirection')?.value || runtimeDirectionLabel(base);
    const next = normalizeSettings({
      ...base,
      volume: Number($('#rVolume')?.value ?? base.volume),
      touchMode: $('#rTouchMode')?.value || base.touchMode,
      stretch: !!$('#rStretch')?.checked,
      quickSlot: Math.max(1, Math.min(9, Number($('#quickSlot')?.value || base.quickSlot || 1)))
    });
    if (selected.core === 'psx') next.virtualPs = direction;
    else next.virtual8 = direction;
    return next;
  }

  function postToGame(type, extra = {}) {
    if (!gameFrame?.contentWindow) return;
    try { gameFrame.contentWindow.postMessage({ type, ...extra }, APP_ORIGIN); } catch (_) {}
  }

  function applyRuntimeSettings() {
    if (!playing) return;
    const settings = collectRuntimeSettings();
    storeSettings(settings);
    postToGame('retro-apply-settings', { settings });
  }

  function openRuntimeSettings() {
    if (!playing || !$('#runtimeSettings')) return;
    $('#runtimeSettings').hidden = false;
    postToGame('retro-pause');
    runtimeNotice('');
  }

  function closeRuntimeSettings() {
    if (!playing || !$('#runtimeSettings')) return;
    applyRuntimeSettings();
    $('#runtimeSettings').hidden = true;
    postToGame('retro-resume');
  }

  function quickAction(type) {
    if (!playing) return;
    const settings = collectRuntimeSettings();
    storeSettings(settings);
    runtimeNotice(type === 'retro-quick-save' ? 'Сохраняем…' : 'Загружаем…');
    postToGame(type, { slot: settings.quickSlot });
  }

  function openKeySettings() {
    if (!playing) return;
    applyRuntimeSettings();
    $('#runtimeSettings').hidden = true;
    $('#runtimeBar').style.display = 'none';
    postToGame('retro-open-controls');
  }

  function setBootText(text) {
    const target = $('#bootOverlay')?.querySelector('span');
    if (target) target.textContent = text;
  }

  function revealGame() {
    clearTimeout(bootTimer);
    const frame = $('#gameFrame');
    const boot = $('#bootOverlay');
    if (frame) frame.style.visibility = 'visible';
    if (boot) { boot.style.opacity = '0'; setTimeout(() => boot.remove(), 190); }
  }

  function failBoot(message) {
    clearTimeout(bootTimer);
    const frame = $('#gameFrame');
    if (frame) frame.style.visibility = 'hidden';
    const boot = $('#bootOverlay');
    if (!boot) return;
    boot.classList.add('error');
    boot.style.opacity = '1';
    boot.innerHTML = `<div class="box"><strong>Игра не запустилась</strong><span>${escapeHtml(message || 'Не удалось открыть этот файл.')}</span><button id="bootBack">Вернуться в меню</button></div>`;
    $('#bootBack')?.addEventListener('click', stopGame);
  }

  function stopGame() {
    clearTimeout(bootTimer);
    postToGame('retro-stop');
    playing = false;
    if (gameMessageHandler) { window.removeEventListener('message', gameMessageHandler); gameMessageHandler = null; }
    gameFrame = null;
    $('#playScreen')?.remove();
    $('#runtimeStyles')?.remove();
    document.body.classList.remove('playing');
    revokeObjectUrls();
    updateSettingsForm();
    history.replaceState(null, '', location.pathname + location.search);
  }

  function startGame(file) {
    if (playing) return;
    playing = true;
    launchModal.hidden = true;
    settingsModal.hidden = true;
    revokeObjectUrls();
    const chosenBios = selected.core === 'psx' ? chooseBiosForRom(file) : null;
    try {
      romObjectUrl = URL.createObjectURL(file);
      if (chosenBios) biosObjectUrl = URL.createObjectURL(chosenBios);
    } catch (_) {
      playing = false;
      showToast('Не удалось открыть выбранный файл.', true);
      return;
    }

    const settings = installPlayShell(file);
    gameFrame = $('#gameFrame');
    const ext = extensionOf(file.name);
    if (['zip','7z','rar'].includes(ext)) setBootText('Распаковываем архив внутри приложения…');

    gameMessageHandler = event => {
      if (!playing || event.origin !== APP_ORIGIN || event.source !== gameFrame?.contentWindow) return;
      const data = event.data || {};
      if (data.type === 'retro-ready') {
        postToGame('retro-start', {
          core: selected.core,
          system: selected.system,
          romUrl: romObjectUrl,
          romName: file.name,
          romSize: file.size,
          biosUrl: biosObjectUrl,
          biosName: chosenBios?.name || '',
          settings,
          controlProfile: loadControlProfile(selected.core)
        });
        setBootText(['zip','7z','rar'].includes(ext) ? 'Распаковываем и ищем подходящий ROM…' : 'Загружаем игру…');
      } else if (data.type === 'retro-emulator-ready') setBootText('Ядро готово. Запускаем игру…');
      else if (data.type === 'retro-game-start') revealGame();
      else if (data.type === 'retro-error') failBoot(data.message || 'Эмулятор не смог открыть этот файл.');
      else if (data.type === 'retro-exit') stopGame();
      else if (data.type === 'retro-quick-result') runtimeNotice(data.message || (data.ok ? 'Готово' : 'Не удалось выполнить действие'), !data.ok);
      else if (data.type === 'retro-controls-closed') {
        saveControlProfile(selected.core, data.controls);
        if ($('#runtimeBar')) $('#runtimeBar').style.display = 'flex';
        if ($('#runtimeSettings')) $('#runtimeSettings').hidden = false;
        runtimeNotice('Назначение кнопок сохранено');
      }
    };
    window.addEventListener('message', gameMessageHandler);
    bootTimer = setTimeout(() => {
      if (playing && $('#bootOverlay')) failBoot('Игра не запустилась за 40 секунд. Проверьте ROM или архив. Для PS1 надёжнее CHD/PBP или архив BIN+CUE.');
    }, 40000);
  }

  function handleAction(action, element) {
    if (action === 'console') openLaunch(element);
    else if (action === 'settings') openSettings();
    else if (action === 'close') closeModal(launchModal);
    else if (action === 'close-settings') closeModal(settingsModal);
    else if (action === 'save-settings') saveSettings();
    else if (action === 'rom') pickRom();
    else if (action === 'bios') pickBios();
    else if (action === 'bios-clear') clearBios();
    else if (action === 'bios-remove') removeBios(element.dataset.index);
    else if (action === 'bluetooth') showToast('Подключите Bluetooth-геймпад в настройках Android — приложение увидит его автоматически.', true);
  }

  document.addEventListener('click', event => {
    const el = event.target.closest('[data-action]');
    if (el) handleAction(el.dataset.action, el);
  });
  $('#volume')?.addEventListener('input', e => { $('#volumeValue').value = `${e.target.value}%`; });
  window.addEventListener('gamepadconnected', updateGamepadStatus);
  window.addEventListener('gamepaddisconnected', updateGamepadStatus);
  setInterval(() => { if (!playing) updateGamepadStatus(); }, 3000);
  window.addEventListener('popstate', () => { if (playing) stopGame(); });
  window.addEventListener('beforeunload', revokeObjectUrls);

  updateSettingsForm();
  updateGamepadStatus();
})();
