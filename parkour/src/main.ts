import Phaser from 'phaser';
import './style.css';
import { GameScene } from './game/GameScene';
import { yandex } from './platform/yandex';

const $ = <T extends HTMLElement>(selector: string) => document.querySelector(selector) as T;

const distanceEl = $('#distance');
const comboEl = $('#combo');
const speedEl = $('#speed');
const trickEl = $('#trick');
const menuEl = $('#menu');
const gameoverEl = $('#gameover');
const pauseMenuEl = $('#pauseMenu');
const scoreTitle = $('#scoreTitle');
const bestTitle = $('#bestTitle');
const playButton = $('#play') as HTMLButtonElement;
const retryButton = $('#retry') as HTMLButtonElement;
const rewardButton = $('#reward') as HTMLButtonElement;
const pauseButton = $('#pause') as HTMLButtonElement;
const resumeButton = $('#resume') as HTMLButtonElement;

let scene: GameScene | null = null;
let bestDistance = 0;
let trickTimer = 0;

function show(element: HTMLElement, visible: boolean) {
  element.classList.toggle('visible', visible);
}

function renderBest() {
  bestTitle.textContent = `Рекорд: ${Math.floor(bestDistance)} м`;
}

async function beginRun() {
  if (!scene) return;
  show(menuEl, false);
  show(gameoverEl, false);
  show(pauseMenuEl, false);
  rewardButton.disabled = false;
  scene.startRun();
  yandex.gameplayStart();
}

async function bootstrap() {
  playButton.disabled = true;
  await yandex.init();
  bestDistance = await yandex.loadBest();
  renderBest();

  const game = new Phaser.Game({
    type: Phaser.AUTO,
    parent: 'game',
    backgroundColor: '#0b1022',
    transparent: false,
    pixelArt: false,
    antialias: true,
    roundPixels: false,
    physics: {
      default: 'arcade',
      arcade: {
        gravity: { x: 0, y: 0 },
        debug: false
      }
    },
    scale: {
      mode: Phaser.Scale.RESIZE,
      autoCenter: Phaser.Scale.CENTER_BOTH,
      width: window.innerWidth,
      height: window.innerHeight
    },
    scene: [GameScene]
  });

  window.addEventListener('parkour-ready', () => {
    scene = game.scene.getScene('Game') as GameScene;
    playButton.disabled = false;
    yandex.ready();
  }, { once: true });
}

playButton.addEventListener('click', beginRun);
retryButton.addEventListener('click', beginRun);

pauseButton.addEventListener('click', () => scene?.togglePause());
resumeButton.addEventListener('click', () => scene?.togglePause());

rewardButton.addEventListener('click', async () => {
  if (!scene || rewardButton.disabled) return;
  rewardButton.disabled = true;
  const rewarded = await yandex.showRewarded();
  if (rewarded && scene.revive()) {
    show(gameoverEl, false);
    yandex.gameplayStart();
  } else {
    rewardButton.disabled = false;
  }
});

for (const button of document.querySelectorAll<HTMLButtonElement>('[data-action]')) {
  const action = button.dataset.action as 'jump' | 'slide';
  const down = (event: Event) => {
    event.preventDefault();
    scene?.setTouchAction(action, true);
    button.classList.add('pressed');
  };
  const up = (event: Event) => {
    event.preventDefault();
    scene?.setTouchAction(action, false);
    button.classList.remove('pressed');
  };
  button.addEventListener('pointerdown', down, { passive: false });
  button.addEventListener('pointerup', up, { passive: false });
  button.addEventListener('pointercancel', up, { passive: false });
  button.addEventListener('pointerleave', up, { passive: false });
}

window.addEventListener('parkour-hud', (event) => {
  const detail = (event as CustomEvent<{ distance: number; flow: number; speed: number }>).detail;
  distanceEl.textContent = `${detail.distance} м`;
  comboEl.textContent = `x${detail.flow.toFixed(1)}`;
  speedEl.textContent = `${Math.round(detail.speed / 10)} км/ч`;
});

window.addEventListener('parkour-trick', (event) => {
  const detail = (event as CustomEvent<{ label: string }>).detail;
  trickEl.textContent = detail.label;
  trickEl.classList.remove('pop');
  void trickEl.offsetWidth;
  trickEl.classList.add('pop');
  window.clearTimeout(trickTimer);
  trickTimer = window.setTimeout(() => trickEl.classList.remove('pop'), 650);
});

window.addEventListener('parkour-gameover', async (event) => {
  const detail = (event as CustomEvent<{ distance: number; runIndex: number; canRevive: boolean }>).detail;
  yandex.gameplayStop();
  scoreTitle.textContent = `${detail.distance} м`;
  if (detail.distance > bestDistance) {
    bestDistance = detail.distance;
    await yandex.saveBest(bestDistance);
  }
  renderBest();
  rewardButton.hidden = !detail.canRevive;
  show(gameoverEl, true);

  if (detail.runIndex > 0 && detail.runIndex % 3 === 0) {
    await new Promise((resolve) => window.setTimeout(resolve, 280));
    await yandex.showInterstitial();
  }
});

window.addEventListener('parkour-pause-state', (event) => {
  const detail = (event as CustomEvent<{ paused: boolean; manual: boolean }>).detail;
  show(pauseMenuEl, detail.paused && detail.manual);
  pauseButton.textContent = detail.paused ? '▶' : 'Ⅱ';
  if (detail.paused) yandex.gameplayStop();
  else if (scene?.isRunning()) yandex.gameplayStart();
});

window.addEventListener('platform-pause', () => scene?.setExternalPaused(true));
window.addEventListener('platform-resume', () => scene?.setExternalPaused(false));

document.addEventListener('visibilitychange', () => {
  if (!scene?.isRunning()) return;
  scene.setExternalPaused(document.hidden);
});

bootstrap().catch((error) => {
  console.error('Rooftop Flow boot failed', error);
  playButton.disabled = false;
});
