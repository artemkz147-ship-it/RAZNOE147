import Phaser from 'phaser';
import './style.css';
import { GameScene } from './game/GameScene';
import { yandex, type DailyProgress, type PlayerProfile } from './platform/yandex';

const $ = <T extends HTMLElement>(selector: string) => document.querySelector(selector) as T;

const distanceEl = $('#distance');
const comboEl = $('#combo');
const speedEl = $('#speed');
const tokensEl = $('#tokens');
const trickEl = $('#trick');
const menuEl = $('#menu');
const gameoverEl = $('#gameover');
const pauseMenuEl = $('#pauseMenu');
const scoreTitle = $('#scoreTitle');
const bestTitle = $('#bestTitle');
const runStats = $('#runStats');
const rankEl = $('#rank');
const coinsEl = $('#coins');
const menuBestEl = $('#menuBest');
const dailyTitleEl = $('#dailyTitle');
const dailyRewardEl = $('#dailyReward');
const dailyProgressEl = $('#dailyProgress');
const dailyBar = $('#dailyBar') as HTMLElement;
const dailyCard = $('#dailyCard');
const missionRewardEl = $('#missionReward');
const playButton = $('#play') as HTMLButtonElement;
const retryButton = $('#retry') as HTMLButtonElement;
const rewardButton = $('#reward') as HTMLButtonElement;
const pauseButton = $('#pause') as HTMLButtonElement;
const resumeButton = $('#resume') as HTMLButtonElement;

type RunResult = {
  distance: number;
  runIndex: number;
  canRevive: boolean;
  tokens: number;
  tricks: number;
};

type MissionMetric = 'distance' | 'tokens' | 'tricks';
type DailyMission = {
  metric: MissionMetric;
  title: string;
  target: number;
  reward: number;
  suffix: string;
};

const MISSIONS: DailyMission[] = [
  { metric: 'distance', title: 'Пробеги 600 м', target: 600, reward: 50, suffix: 'м' },
  { metric: 'tokens', title: 'Собери 18 Flow-монет', target: 18, reward: 60, suffix: '◆' },
  { metric: 'tricks', title: 'Сделай 10 трюков', target: 10, reward: 70, suffix: 'трюков' }
];

let scene: GameScene | null = null;
let profile: PlayerProfile = {
  bestDistance: 0,
  coins: 0,
  lifetimeDistance: 0,
  runs: 0,
  daily: { date: '', distance: 0, tokens: 0, tricks: 0, claimed: false }
};
let pendingResult: RunResult | null = null;
let trickTimer = 0;

function show(element: HTMLElement, visible: boolean) {
  element.classList.toggle('visible', visible);
}

function missionForDate(date: string): DailyMission {
  const hash = [...date].reduce((sum, char) => sum + char.charCodeAt(0), 0);
  return MISSIONS[hash % MISSIONS.length];
}

function missionValue(daily: DailyProgress, mission: DailyMission) {
  return daily[mission.metric];
}

function rankName(distance: number) {
  if (distance >= 50000) return 'Легенда крыш';
  if (distance >= 20000) return 'Мастер потока';
  if (distance >= 7500) return 'Ночной бегун';
  if (distance >= 2000) return 'Трейсер';
  return 'Новичок';
}

function renderProfile() {
  const mission = missionForDate(profile.daily.date);
  const value = missionValue(profile.daily, mission);
  const progress = profile.daily.claimed ? 1 : Math.min(1, value / mission.target);

  rankEl.textContent = rankName(profile.lifetimeDistance);
  coinsEl.textContent = `◆ ${profile.coins}`;
  menuBestEl.textContent = `${profile.bestDistance} м`;
  bestTitle.textContent = `Рекорд: ${profile.bestDistance} м`;
  dailyTitleEl.textContent = profile.daily.claimed ? 'Задание выполнено' : mission.title;
  dailyRewardEl.textContent = profile.daily.claimed ? 'ГОТОВО' : `+${mission.reward} ◆`;
  dailyProgressEl.textContent = profile.daily.claimed
    ? 'Новая цель появится завтра'
    : `${Math.min(value, mission.target)} / ${mission.target} ${mission.suffix}`;
  dailyBar.style.width = `${Math.round(progress * 100)}%`;
  dailyCard.classList.toggle('done', profile.daily.claimed);
}

async function settlePendingResult() {
  if (!pendingResult) return null;
  const result = pendingResult;
  pendingResult = null;

  profile.runs += 1;
  profile.bestDistance = Math.max(profile.bestDistance, result.distance);
  profile.lifetimeDistance += result.distance;
  profile.coins += result.tokens;
  profile.daily.distance += result.distance;
  profile.daily.tokens += result.tokens;
  profile.daily.tricks += result.tricks;

  const mission = missionForDate(profile.daily.date);
  const completedNow = !profile.daily.claimed && missionValue(profile.daily, mission) >= mission.target;
  if (completedNow) {
    profile.daily.claimed = true;
    profile.coins += mission.reward;
  }

  missionRewardEl.hidden = !completedNow;
  if (completedNow) missionRewardEl.textContent = `ЗАДАНИЕ ВЫПОЛНЕНО · +${mission.reward} ◆`;

  renderProfile();
  await yandex.saveProfile(profile);
  return { ...result, completedNow };
}

async function beginRun() {
  if (!scene) return;
  show(menuEl, false);
  show(gameoverEl, false);
  show(pauseMenuEl, false);
  missionRewardEl.hidden = true;
  rewardButton.disabled = false;
  scene.startRun();
  yandex.gameplayStart();
}

async function bootstrap() {
  playButton.disabled = true;
  await yandex.init();
  profile = await yandex.loadProfile();
  renderProfile();

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
retryButton.addEventListener('click', async () => {
  const settled = await settlePendingResult();
  if (settled && settled.runIndex > 0 && settled.runIndex % 3 === 0) {
    await yandex.showInterstitial();
  }
  await beginRun();
});

pauseButton.addEventListener('click', () => scene?.togglePause());
resumeButton.addEventListener('click', () => scene?.togglePause());

rewardButton.addEventListener('click', async () => {
  if (!scene || rewardButton.disabled) return;
  rewardButton.disabled = true;
  const rewarded = await yandex.showRewarded();
  if (rewarded && scene.revive()) {
    pendingResult = null;
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
  const detail = (event as CustomEvent<{ distance: number; flow: number; speed: number; tokens: number }>).detail;
  distanceEl.textContent = `${detail.distance} м`;
  comboEl.textContent = `x${detail.flow.toFixed(1)}`;
  tokensEl.textContent = `◆ ${detail.tokens}`;
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
  const detail = (event as CustomEvent<RunResult>).detail;
  yandex.gameplayStop();
  pendingResult = detail;
  scoreTitle.textContent = `${detail.distance} м`;
  runStats.textContent = `◆ +${detail.tokens} · ${detail.tricks} трюков`;
  rewardButton.hidden = !detail.canRevive;
  missionRewardEl.hidden = true;
  show(gameoverEl, true);

  if (!detail.canRevive) {
    const settled = await settlePendingResult();
    if (settled && settled.runIndex > 0 && settled.runIndex % 3 === 0) {
      await new Promise((resolve) => window.setTimeout(resolve, 280));
      await yandex.showInterstitial();
    }
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

window.addEventListener('beforeunload', () => {
  if (pendingResult) void settlePendingResult();
});

bootstrap().catch((error) => {
  console.error('Rooftop Flow boot failed', error);
  playButton.disabled = false;
});
