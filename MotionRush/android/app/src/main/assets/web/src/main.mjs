import { createGameState, applyAction, stepGame, spawnEntity, snapshot } from './game.mjs';
import { createGestureInterpreter } from './gestures.mjs';
import { createRenderer } from './render.mjs';
import { createPoseCamera } from './pose-camera.mjs';

const $ = (id) => document.getElementById(id);
const canvas = $('game');
const renderer = createRenderer(canvas);
let state = createGameState(Date.now());
let gesture = createGestureInterpreter();
let camera = null;
let latestPose = null;
let lastTime = performance.now();
let spawnTimer = 0.8;
let running = false;
let toastTimer = 0;

const actionLabels = {
  MOVE_LEFT: '← ВЛЕВО', MOVE_RIGHT: 'ВПРАВО →', JUMP: 'ПРЫЖОК ↑', CROUCH: 'ПРИСЕД ↓',
  PUNCH_LEFT: '✊ ЛЕВЫЙ УДАР', PUNCH_RIGHT: 'ПРАВЫЙ УДАР ✊',
  RAISE_LEFT: '🙋 ЛЕВАЯ РУКА', RAISE_RIGHT: 'ПРАВАЯ РУКА 🙋', RAISE_BOTH: '🙌 ДВЕ РУКИ',
};

function toast(text) {
  const el = $('actionToast');
  el.textContent = text;
  el.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 320);
}

function dispatch(action, show = true) {
  applyAction(state, action);
  if (show && actionLabels[action]) toast(actionLabels[action]);
}

function randomLane() { return [-1, 0, 1][Math.floor(Math.random() * 3)]; }
function randomOf(items) { return items[Math.floor(Math.random() * items.length)]; }

function spawnWave() {
  const roll = Math.random();
  const lane = randomLane();
  const z = 42 + Math.random() * 9;
  if (roll < .34) {
    spawnEntity(state, { id: crypto.randomUUID(), type: 'obstacle', lane, z, obstacle: randomOf(['low', 'high', 'solid']) });
  } else if (roll < .53) {
    spawnEntity(state, { id: crypto.randomUUID(), type: 'enemy', lane, z, side: Math.random() < .5 ? 'left' : 'right' });
  } else if (roll < .73) {
    spawnEntity(state, { id: crypto.randomUUID(), type: 'pickup', lane, z, pickup: Math.random() < .7 ? 'coin' : randomOf(['shield', 'magnet']) });
  } else {
    const handRoll = Math.random();
    spawnEntity(state, {
      id: crypto.randomUUID(), type: 'airPickup', lane, z,
      hand: handRoll < .42 ? 'left' : handRoll < .84 ? 'right' : 'both',
      pickup: handRoll > .84 ? 'boost' : randomOf(['shield', 'magnet', 'coin']),
    });
  }

  if (Math.random() < .16) {
    const other = lane === 0 ? (Math.random() < .5 ? -1 : 1) : 0;
    spawnEntity(state, { id: crypto.randomUUID(), type: 'pickup', lane: other, z: z + 3.5, pickup: 'coin' });
  }
}

function updateHud(view) {
  $('health').textContent = view.health;
  $('score').textContent = String(view.score).padStart(5, '0');
  $('coins').textContent = view.coins;
  const effects = [];
  if (view.effects.shieldRemaining > 0) effects.push(`<span class="effect">ЩИТ ${Math.ceil(view.effects.shieldRemaining)}с</span>`);
  if (view.effects.boostRemaining > 0) effects.push(`<span class="effect">УСКОРЕНИЕ ${Math.ceil(view.effects.boostRemaining)}с</span>`);
  if (view.effects.magnetRemaining > 0) effects.push(`<span class="effect">МАГНИТ ${Math.ceil(view.effects.magnetRemaining)}с</span>`);
  $('effectBar').innerHTML = effects.join('');
}

function loop(now) {
  const dt = Math.min(.05, (now - lastTime) / 1000);
  lastTime = now;
  if (running && !state.gameOver) {
    stepGame(state, dt);
    spawnTimer -= dt;
    if (spawnTimer <= 0) {
      spawnWave();
      spawnTimer = Math.max(.38, .95 - state.elapsed * .006) + Math.random() * .28;
    }
  }
  const view = snapshot(state);
  renderer.render(view, now / 1000);
  updateHud(view);
  if (state.gameOver && running) {
    running = false;
    $('finalScore').textContent = view.score;
    $('gameOverOverlay').classList.add('visible');
  }
  requestAnimationFrame(loop);
}
requestAnimationFrame(loop);

async function enableCamera() {
  try {
    $('cameraStatus').textContent = 'ЗАПРОС КАМЕРЫ';
    camera = await createPoseCamera({
      video: $('camera'),
      onFrame(frame, now) {
        latestPose = frame;
        if (!gesture.calibrated) return;
        const actions = gesture.update(frame, now);
        for (const action of actions) dispatch(action);
      },
      onStatus(status) {
        const labels = {
          'requesting-camera': 'РАЗРЕШЕНИЕ…', 'loading-pose': 'ЗАГРУЗКА ИИ…', tracking: 'ТЕЛО В КАДРЕ',
          'no-pose': 'ВСТАНЬ В КАДР', 'tracking-error': 'ОШИБКА ТРЕКИНГА',
        };
        $('cameraStatus').textContent = labels[status] || status;
      },
    });
    await camera.start();
    return true;
  } catch (error) {
    console.error(error);
    $('cameraStatus').textContent = 'КАМЕРА НЕДОСТУПНА';
    toast('КАМЕРА НЕДОСТУПНА');
    return false;
  }
}

$('startButton').addEventListener('click', async () => {
  $('startButton').disabled = true;
  const ok = await enableCamera();
  $('startButton').disabled = false;
  if (!ok) return;
  $('startOverlay').classList.remove('visible');
  $('calibrationOverlay').classList.add('visible');
});

$('demoButton').addEventListener('click', () => {
  $('startOverlay').classList.remove('visible');
  running = true;
  lastTime = performance.now();
  toast('ДЕМО-УПРАВЛЕНИЕ');
});

$('calibrateButton').addEventListener('click', () => {
  if (!latestPose) {
    $('calibrationText').textContent = 'Тело пока не найдено. Отойди чуть дальше.';
    return;
  }
  if (!gesture.calibrate(latestPose)) {
    $('calibrationText').textContent = 'Не вижу тело достаточно уверенно.';
    return;
  }
  $('calibrationOverlay').classList.remove('visible');
  running = true;
  lastTime = performance.now();
  toast('КАЛИБРОВКА ГОТОВА');
});

$('restartButton').addEventListener('click', () => {
  state = createGameState(Date.now());
  spawnTimer = .7;
  $('gameOverOverlay').classList.remove('visible');
  running = true;
  lastTime = performance.now();
});

$('cameraToggle').addEventListener('click', () => $('cameraCard').classList.toggle('hidden-preview'));

const keyMap = {
  ArrowLeft: 'MOVE_LEFT', KeyA: 'MOVE_LEFT', ArrowRight: 'MOVE_RIGHT', KeyD: 'MOVE_RIGHT',
  Space: 'JUMP', ArrowUp: 'JUMP', KeyW: 'JUMP', ArrowDown: 'CROUCH', KeyS: 'CROUCH',
  KeyJ: 'PUNCH_LEFT', KeyK: 'PUNCH_RIGHT', KeyQ: 'RAISE_LEFT', KeyE: 'RAISE_RIGHT', KeyR: 'RAISE_BOTH',
};
window.addEventListener('keydown', (event) => {
  const action = keyMap[event.code];
  if (!action) return;
  event.preventDefault();
  if (!running && !$('startOverlay').classList.contains('visible')) running = true;
  dispatch(action);
});

let touchStart = null;
canvas.addEventListener('pointerdown', (event) => { touchStart = { x: event.clientX, y: event.clientY }; });
canvas.addEventListener('pointerup', (event) => {
  if (!touchStart) return;
  const dx = event.clientX - touchStart.x;
  const dy = event.clientY - touchStart.y;
  if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 35) dispatch(dx < 0 ? 'MOVE_LEFT' : 'MOVE_RIGHT');
  else if (dy < -35) dispatch('JUMP');
  else if (dy > 35) dispatch('CROUCH');
  touchStart = null;
});

window.addEventListener('beforeunload', () => camera?.stop());
