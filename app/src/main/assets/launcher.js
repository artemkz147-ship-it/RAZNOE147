(() => {
  'use strict';

  const $ = (s, root = document) => root.querySelector(s);
  const $$ = (s, root = document) => [...root.querySelectorAll(s)];
  const launchModal = $('#launchModal');
  const settingsModal = $('#settingsModal');
  const toast = $('#toast');
  const APP_ORIGIN = location.origin;
  const defaults = { volume: 80, touchControls: true, stretch: false, vibration: true };

  let selected = { core: 'nes', system: 'Dendy / NES', extensions: 'NES, FDS, ZIP, 7Z' };
  let toastTimer = 0;
  let biosFile = null;
  let playing = false;
  let gameFrame = null;
  let bootTimer = 0;
  let gameMessageHandler = null;
  let romObjectUrl = '';
  let biosObjectUrl = '';

  function getSettings() {
    try { return { ...defaults, ...JSON.parse(localStorage.getItem('retro-settings') || '{}') }; }
    catch (_) { return { ...defaults }; }
  }

  function updateSettingsForm() {
    const s = getSettings();
    $('#volume').value = s.volume;
    $('#volumeValue').value = `${s.volume}%`;
    $('#touchControls').checked = !!s.touchControls;
    $('#stretch').checked = !!s.stretch;
    $('#vibration').checked = !!s.vibration;
  }

  function saveSettings() {
    const s = {
      volume: Number($('#volume').value),
      touchControls: $('#touchControls').checked,
      stretch: $('#stretch').checked,
      vibration: $('#vibration').checked
    };
    localStorage.setItem('retro-settings', JSON.stringify(s));
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
    selected = {
      core: card.dataset.core,
      system: card.dataset.system,
      extensions: card.dataset.extensions
    };
    $$('.console-card').forEach(c => c.classList.toggle('selected', c === card));
    $('#launchKicker').textContent = `СИСТЕМА ${card.querySelector('.console-number').textContent}`;
    $('#launchTitle').textContent = selected.system;
    $('#fileFormats').textContent = `Поддерживаются: ${selected.extensions}`;
    $('#biosRow').hidden = selected.core !== 'psx';
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

  function updateBiosState() {
    const state = $('#biosState');
    if (!state) return;
    state.textContent = biosFile ? `Выбран: ${biosFile.name}` : 'Не выбран — можно добавить для лучшей совместимости';
  }

  function updateGamepadStatus() {
    let pads = [];
    try { pads = [...(navigator.getGamepads?.() || [])].filter(Boolean); } catch (_) {}
    const names = pads.map(p => p.id).filter(Boolean);
    const name = names.join(' • ');
    const connected = names.length > 0;
    $('#gamepadStatus')?.classList.toggle('connected', connected);
    if ($('#gamepadText')) $('#gamepadText').textContent = connected ? name : 'Геймпад не подключён';
    if ($('#settingsPadState')) $('#settingsPadState').textContent = connected ? name : 'Не подключён';
  }

  function extensionOf(name) {
    const i = String(name || '').lastIndexOf('.');
    return i >= 0 ? name.slice(i + 1).toLowerCase() : '';
  }

  function allowedExtensions(core) {
    if (core === 'nes') return ['nes', 'fds', 'zip', '7z'];
    if (core === 'segaMD') return ['md', 'smd', 'gen', 'bin', 'zip', '7z'];
    return ['chd', 'pbp', 'bin', 'iso', 'img', 'zip', '7z'];
  }

  function chooseFile({ accept = '', onFile }) {
    const input = document.createElement('input');
    input.type = 'file';
    input.style.position = 'fixed';
    input.style.left = '-10000px';
    if (accept) input.accept = accept;
    input.addEventListener('change', () => {
      const file = input.files?.[0] || null;
      input.remove();
      if (file) onFile(file);
    }, { once: true });
    document.body.appendChild(input);
    input.click();
  }

  function pickBios() {
    chooseFile({
      accept: '.bin,.rom,.bios,application/octet-stream',
      onFile(file) {
        if (!file.size) {
          showToast('Этот файл BIOS пустой. Выберите другой.', true);
          return;
        }
        biosFile = file;
        updateBiosState();
        showToast(`BIOS выбран: ${file.name}`);
      }
    });
  }

  function pickRom() {
    vibrate(30);
    const allowed = allowedExtensions(selected.core);
    chooseFile({
      accept: allowed.map(x => `.${x}`).join(','),
      onFile(file) {
        const ext = extensionOf(file.name);
        if (!file.size) {
          showToast('Файл игры пустой. Выберите другой файл.', true);
          return;
        }
        if (!allowed.includes(ext)) {
          showToast(`Формат .${ext || '?'} не подходит для ${selected.system}.`, true);
          return;
        }
        if (selected.core === 'psx' && ext === 'bin') {
          showToast('PS1 BIN запустится, если образ однофайловый. Для многодисковых игр лучше CHD или PBP.', true);
        }
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

  function installPlayShell(file) {
    const settings = getSettings();
    const style = document.createElement('style');
    style.id = 'runtimeStyles';
    style.textContent = `
      html,body{width:100%;height:100%;overflow:hidden;background:#000!important}
      body.playing>.app-shell,body.playing>#launchModal,body.playing>#settingsModal,body.playing>.toast{display:none!important}
      #playScreen{position:fixed;inset:0;background:#000;z-index:9999;overflow:hidden}
      #gameFrame{display:block;position:absolute;inset:0;width:100%;height:100%;border:0;background:#000;visibility:hidden}
      #runtimeBar{position:absolute;left:max(10px,env(safe-area-inset-left));top:max(10px,env(safe-area-inset-top));z-index:100003;display:flex;gap:8px;pointer-events:auto}
      #runtimeBar button{border:1px solid rgba(255,255,255,.20);background:rgba(8,9,12,.82);color:#fff;border-radius:12px;padding:10px 14px;font:800 13px system-ui,sans-serif;backdrop-filter:blur(10px)}
      #bootOverlay{position:absolute;inset:0;display:grid;place-items:center;background:#08090c;color:white;z-index:100002;font-family:system-ui,sans-serif;text-align:center;padding:24px;transition:opacity .18s}
      #bootOverlay .box{max-width:480px}
      #bootOverlay strong{display:block;font-size:24px;margin-bottom:10px}
      #bootOverlay span{display:block;color:#a9b0bc;line-height:1.5}
      #bootOverlay small{display:block;color:#666f7c;margin-top:12px;line-height:1.4}
      #bootOverlay.error span{color:#ff9a9a}
      #bootOverlay button{margin-top:18px;padding:12px 18px;border:0;border-radius:10px;font-weight:800}
      .bootSpinner{width:34px;height:34px;border:3px solid rgba(255,255,255,.16);border-top-color:#fff;border-radius:50%;margin:0 auto 18px;animation:spin .8s linear infinite}
      @keyframes spin{to{transform:rotate(360deg)}}
    `;
    document.head.appendChild(style);
    document.body.classList.add('playing');
    document.body.insertAdjacentHTML('beforeend', `
      <div id="playScreen">
        <iframe id="gameFrame" src="game.html" allow="autoplay; fullscreen; gamepad" referrerpolicy="no-referrer"></iframe>
        <div id="runtimeBar"><button id="backToMenu">← Меню</button></div>
        <div id="bootOverlay"><div class="box"><div class="bootSpinner"></div><strong>${escapeHtml(selected.system)}</strong><span>Подготавливаем ${escapeHtml(file.name)}…</span><small>Если файл не откроется, появится обычное сообщение об ошибке.</small></div></div>
      </div>
    `);
    $('#backToMenu')?.addEventListener('click', stopGame);
    return settings;
  }

  function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
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
    if (boot) {
      boot.style.opacity = '0';
      setTimeout(() => boot.remove(), 190);
    }
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
    playing = false;
    try { gameFrame?.contentWindow?.postMessage({ type: 'retro-stop' }, APP_ORIGIN); } catch (_) {}
    if (gameMessageHandler) {
      window.removeEventListener('message', gameMessageHandler);
      gameMessageHandler = null;
    }
    gameFrame = null;
    $('#playScreen')?.remove();
    $('#runtimeStyles')?.remove();
    document.body.classList.remove('playing');
    revokeObjectUrls();
    history.replaceState(null, '', location.pathname + location.search);
  }

  function startGame(file) {
    if (playing) return;
    playing = true;
    launchModal.hidden = true;
    settingsModal.hidden = true;
    revokeObjectUrls();

    try {
      romObjectUrl = URL.createObjectURL(file);
      if (selected.core === 'psx' && biosFile) biosObjectUrl = URL.createObjectURL(biosFile);
    } catch (_) {
      playing = false;
      showToast('Не удалось открыть выбранный файл.', true);
      return;
    }

    const settings = installPlayShell(file);
    gameFrame = $('#gameFrame');

    gameMessageHandler = event => {
      if (!playing || event.origin !== APP_ORIGIN || event.source !== gameFrame?.contentWindow) return;
      const data = event.data || {};

      if (data.type === 'retro-ready') {
        try {
          gameFrame.contentWindow.postMessage({
            type: 'retro-start',
            core: selected.core,
            system: selected.system,
            romUrl: romObjectUrl,
            romName: file.name,
            romSize: file.size,
            biosUrl: selected.core === 'psx' ? biosObjectUrl : '',
            biosName: selected.core === 'psx' && biosFile ? biosFile.name : '',
            settings
          }, APP_ORIGIN);
          setBootText('Загружаем игру…');
        } catch (error) {
          failBoot(`Не удалось передать игру эмулятору: ${error?.message || error}`);
        }
      } else if (data.type === 'retro-emulator-ready') {
        setBootText('Ядро готово. Запускаем игру…');
      } else if (data.type === 'retro-game-start') {
        revealGame();
      } else if (data.type === 'retro-error') {
        failBoot(data.message || 'Эмулятор не смог открыть этот файл.');
      } else if (data.type === 'retro-exit') {
        stopGame();
      }
    };

    window.addEventListener('message', gameMessageHandler);
    bootTimer = setTimeout(() => {
      if (playing && $('#bootOverlay')) {
        failBoot('Игра не запустилась за 35 секунд. Проверьте ROM. Для PS1 надёжнее использовать CHD или PBP.');
      }
    }, 35000);
  }

  function handleAction(action, element) {
    if (action === 'console') openLaunch(element);
    else if (action === 'settings') openSettings();
    else if (action === 'close') closeModal(launchModal);
    else if (action === 'close-settings') closeModal(settingsModal);
    else if (action === 'save-settings') saveSettings();
    else if (action === 'rom') pickRom();
    else if (action === 'bios') pickBios();
    else if (action === 'bluetooth') showToast('Подключите Bluetooth-геймпад в настройках Android — приложение увидит его автоматически.', true);
  }

  document.addEventListener('click', event => {
    const el = event.target.closest('[data-action]');
    if (!el) return;
    handleAction(el.dataset.action, el);
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
