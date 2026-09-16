import { createGameState, applyAction, stepGame, applyPattern, snapshot } from './game.mjs';
import { createRenderer } from './render.mjs';
import { createMotionInterpolator } from './motion-state.mjs';
import { createPatternDirector } from './patterns.mjs';

const $ = (id) => document.getElementById(id);
const canvas = $('game');
const renderer = createRenderer(canvas);
const motion = createMotionInterpolator();
let state = createGameState(Date.now());
let director = createPatternDirector(state.seed);
let lastTime = performance.now();
let patternCooldown = 0.45;
let running = false;
let toastTimer = 0;
let nativeCameraReady = false;
let nativePoseFound = false;

$('cameraCard').style.display = 'none';

const actionLabels = {
  MOVE_LEFT: '← ВЛЕВО', MOVE_RIGHT: 'ВПРАВО →', JUMP: 'ПРЫЖОК ↑', CROUCH: 'ПРИСЕД ↓',
  PUNCH_LEFT: '✊ ЛЕВЫЙ УДАР', PUNCH_RIGHT: 'ПРАВЫЙ УДАР ✊',
  RAISE_LEFT: '🙋 ЛЕВАЯ РУКА', RAISE_RIGHT: 'ПРАВАЯ РУКА 🙋', RAISE_BOTH: '🙌 ДВЕ РУКИ',
};
function toast(text) { const el = $('actionToast'); el.textContent = text; el.classList.add('show'); clearTimeout(toastTimer); toastTimer = setTimeout(() => el.classList.remove('show'), 420); }
function dispatch(action, show = true) { applyAction(state, action); if (show && actionLabels[action]) toast(actionLabels[action]); }
function difficultyFor(s) { return Math.max(0, Math.min(4, Math.floor(s.elapsed / 28) + Math.floor(s.streak / 18))); }
function ensurePatterns() {
  const farthest = state.entities.reduce((m, e) => Math.max(m, e.z), 0);
  if (farthest > 44 || patternCooldown > 0) return;
  const pattern = director.nextPattern(difficultyFor(state));
  const startZ = Math.max(38, farthest + 12);
  applyPattern(state, director.materialize(pattern, startZ));
  patternCooldown = Math.max(.25, .62 - state.speed * .012);
}
function updateHud(view) {
  $('health').textContent = view.health; $('score').textContent = String(view.score).padStart(5, '0'); $('coins').textContent = view.coins;
  const effects = [];
  if (view.comboMultiplier > 1) effects.push(`<span class="effect">КОМБО ×${view.comboMultiplier}</span>`);
  if (view.effects.shieldRemaining > 0) effects.push(`<span class="effect">ЩИТ ${Math.ceil(view.effects.shieldRemaining)}с</span>`);
  if (view.effects.boostRemaining > 0) effects.push(`<span class="effect">УСКОРЕНИЕ ${Math.ceil(view.effects.boostRemaining)}с</span>`);
  if (view.effects.magnetRemaining > 0) effects.push(`<span class="effect">МАГНИТ ${Math.ceil(view.effects.magnetRemaining)}с</span>`);
  $('effectBar').innerHTML = effects.join('');
}
function loop(now) {
  const dt = Math.min(.05, (now - lastTime) / 1000); lastTime = now;
  if (running && !state.gameOver) { stepGame(state, dt); patternCooldown -= dt; ensurePatterns(); }
  const view = snapshot(state); const motionState = motion.sample(now); renderer.render(view, now / 1000, motionState); updateHud(view);
  if (view.events?.some((e) => e.type === 'near-miss')) toast('БЛИЗКО! +NEAR MISS');
  if (state.gameOver && running) { running = false; $('finalScore').textContent = view.score; $('gameOverOverlay').classList.add('visible'); }
  requestAnimationFrame(loop);
}
requestAnimationFrame(loop);
function setStartStatus(text) { $('startButton').textContent = text; }
window.onNativeMotionStatus = (code, message = '') => {
  switch (code) {
    case 'requesting-permission': setStartStatus('РАЗРЕШИ КАМЕРУ'); break;
    case 'initializing': setStartStatus('ЗАПУСК КАМЕРЫ И ИИ…'); break;
    case 'camera-ready': nativeCameraReady = true; $('startOverlay').classList.remove('visible'); $('calibrationOverlay').classList.add('visible'); $('calibrationText').textContent = 'Отойди так, чтобы были видны плечи, таз и колени.'; break;
    case 'pose-found': nativePoseFound = true; if ($('calibrationOverlay').classList.contains('visible')) $('calibrationText').textContent = 'Тело найдено. Стой прямо, руки опущены, затем нажми «Калибровать».'; break;
    case 'calibrating': if ($('calibrationOverlay').classList.contains('visible')) $('calibrationText').textContent = message || 'Стой прямо и спокойно несколько кадров…'; break;
    case 'no-pose': nativePoseFound = false; if ($('calibrationOverlay').classList.contains('visible')) $('calibrationText').textContent = message || 'Тело не найдено. Отойди чуть дальше.'; break;
    case 'permission-denied': $('startButton').disabled = false; setStartStatus('ВКЛЮЧИТЬ КАМЕРУ И ИГРАТЬ'); toast('НУЖНО РАЗРЕШЕНИЕ НА КАМЕРУ'); break;
    case 'error': $('startButton').disabled = false; setStartStatus('ПОВТОРИТЬ ЗАПУСК КАМЕРЫ'); $('calibrationText').textContent = message || 'Ошибка камеры или распознавания.'; toast(message || 'ОШИБКА КАМЕРЫ'); break;
    case 'calibrated': toast('КАЛИБРОВКА ГОТОВА'); break;
  }
};
window.onNativeMotionAction = (action) => { if (running) dispatch(action); };
window.onNativeMotionState = (payload) => { try { motion.push(typeof payload === 'string' ? JSON.parse(payload) : payload, performance.now()); } catch {} };
window.onNativeCalibrationResult = (ok) => {
  if (!ok) { $('calibrationText').textContent = nativePoseFound ? 'Не удалось зафиксировать стойку. Встань ровно и повтори.' : 'Тело не найдено. Отойди так, чтобы были видны плечи, таз и колени.'; return; }
  $('calibrationOverlay').classList.remove('visible'); running = true; lastTime = performance.now(); ensurePatterns();
};
$('startButton').addEventListener('click', () => { const bridge = window.AndroidMotion; if (!bridge?.startTracking) return toast('НАТИВНАЯ КАМЕРА НЕДОСТУПНА'); $('startButton').disabled = true; setStartStatus('ЗАПУСК КАМЕРЫ…'); bridge.startTracking(); });
$('demoButton').addEventListener('click', () => { $('startOverlay').classList.remove('visible'); running = true; lastTime = performance.now(); ensurePatterns(); toast('ДЕМО-УПРАВЛЕНИЕ'); });
$('calibrateButton').addEventListener('click', () => { if (!nativeCameraReady) return void ($('calibrationText').textContent = 'Камера ещё не готова.'); if (!nativePoseFound) return void ($('calibrationText').textContent = 'Тело пока не найдено. Отойди чуть дальше.'); const bridge = window.AndroidMotion; if (!bridge?.calibrate) return void ($('calibrationText').textContent = 'Нативное распознавание недоступно.'); $('calibrationText').textContent = 'Набираю стабильную нейтральную стойку…'; bridge.calibrate(); });
$('restartButton').addEventListener('click', () => { state = createGameState(Date.now()); director = createPatternDirector(state.seed); patternCooldown = .2; $('gameOverOverlay').classList.remove('visible'); running = true; lastTime = performance.now(); ensurePatterns(); });
const keyMap = { ArrowLeft: 'MOVE_LEFT', KeyA: 'MOVE_LEFT', ArrowRight: 'MOVE_RIGHT', KeyD: 'MOVE_RIGHT', Space: 'JUMP', ArrowUp: 'JUMP', KeyW: 'JUMP', ArrowDown: 'CROUCH', KeyS: 'CROUCH', KeyJ: 'PUNCH_LEFT', KeyK: 'PUNCH_RIGHT', KeyQ: 'RAISE_LEFT', KeyE: 'RAISE_RIGHT', KeyR: 'RAISE_BOTH' };
window.addEventListener('keydown', (event) => { const action = keyMap[event.code]; if (!action) return; event.preventDefault(); if (!running && !$('startOverlay').classList.contains('visible')) running = true; dispatch(action); });
let touchStart = null;
canvas.addEventListener('pointerdown', (event) => { touchStart = { x: event.clientX, y: event.clientY }; });
canvas.addEventListener('pointerup', (event) => { if (!touchStart) return; const dx = event.clientX - touchStart.x; const dy = event.clientY - touchStart.y; if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 35) dispatch(dx < 0 ? 'MOVE_LEFT' : 'MOVE_RIGHT'); else if (dy < -35) dispatch('JUMP'); else if (dy > 35) dispatch('CROUCH'); touchStart = null; });
