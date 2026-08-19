import './style.css';
import { DropGame3D } from './drop/DropGame3D';
import type { DropLevelSpec } from './drop/DropTypes';
import { yandex, type Medal, type ParkourProgress } from './platform/yandex';

const $ = <T extends HTMLElement>(selector: string) => document.querySelector(selector) as T;
const MAX_LEVEL = 18;
const MEDAL_RANK: Record<Medal, number> = { bronze: 1, silver: 2, gold: 3 };
const MEDAL_NAME: Record<Medal, string> = { bronze: 'БРОНЗА', silver: 'СЕРЕБРО', gold: 'ЗОЛОТО' };

const gameHost = $('#game');
const menu = $('#menu');
const levelsHost = $('#levels');
const hud = $('#hud');
const finish = $('#finish');
const finishTitle = $('#finishTitle');
const finishStats = $('#finishStats');
const scoreEl = $('#score');
const stageEl = $('#stage');
const dropEl = $('#drop');
const comboEl = $('#combo');
const stateEl = $('#state');
const targetEl = $('#target');
const chainEl = $('#chain');
const tokenEl = $('#tokens');
const nextButton = $('#next') as HTMLButtonElement;
const retryButton = $('#retry') as HTMLButtonElement;
const levelMenuButton = $('#levelMenu') as HTMLButtonElement;
const pauseButton = $('#pause') as HTMLButtonElement;
const resumeButton = $('#resume') as HTMLButtonElement;
const pauseOverlay = $('#pauseOverlay');

let game: DropGame3D;
let currentLevel = 1;
let pendingNextLevel = 1;
let completionCount = 0;
let launching = false;
let progress: ParkourProgress = {
  unlockedLevel: 1,
  bestScores: {},
  bestCombos: {},
  bestTimes: {},
  bestMedals: {},
  cleanLevels: [],
  completedLevels: [],
  tokens: 0,
  totalFalls: 0
};

function show(element: HTMLElement, visible: boolean) {
  element.classList.toggle('visible', visible);
}

function popToast(message: string) {
  const toast = $('#toast');
  toast.textContent = message;
  toast.classList.remove('pop');
  void toast.offsetWidth;
  toast.classList.add('pop');
}

function medalFor(level: DropLevelSpec, score: number): Medal | null {
  const ratio = score / Math.max(1, level.parScore);
  if (ratio >= 1.35) return 'gold';
  if (ratio >= 1.0) return 'silver';
  if (ratio >= 0.7) return 'bronze';
  return null;
}

function medalClass(medal: Medal | undefined) {
  return medal ? ` medal-${medal}` : '';
}

function setLevelButtonsDisabled(value: boolean) {
  for (const button of levelsHost.querySelectorAll<HTMLButtonElement>('.level-card')) {
    if (value) button.disabled = true;
    else {
      const levelId = Number(button.dataset.levelId || 0);
      button.disabled = levelId > progress.unlockedLevel;
    }
  }
}

function renderLevels() {
  levelsHost.innerHTML = '';
  for (const level of game.levels) {
    const unlocked = level.id <= progress.unlockedLevel;
    const complete = progress.completedLevels.includes(level.id);
    const clean = progress.cleanLevels.includes(level.id);
    const best = progress.bestScores[String(level.id)] ?? 0;
    const medal = progress.bestMedals[String(level.id)];
    const button = document.createElement('button');
    button.className = `level-card${complete ? ' complete' : ''}${medalClass(medal)}`;
    button.dataset.levelId = String(level.id);
    button.disabled = !unlocked;
    const meta = !unlocked
      ? '<b>ЗАКРЫТО</b>'
      : best
        ? `<b>${best.toLocaleString('ru-RU')} очк.</b><small>${medal ? MEDAL_NAME[medal] : 'БЕЗ МЕДАЛИ'}${clean ? ' · ЧИСТО' : ''}</small>`
        : '<b>СТАРТ</b><small>новый спуск</small>';
    button.innerHTML = `
      <span class="level-number">${String(level.id).padStart(2, '0')}</span>
      <span class="level-copy"><b>${level.name}</b><small>${level.subtitle}</small></span>
      <span class="level-meta">${meta}</span>
    `;
    button.title = `Рекомендация: ${level.recommended}`;
    button.addEventListener('click', () => void launch(level.id));
    levelsHost.appendChild(button);
  }
  tokenEl.textContent = `◆ ${progress.tokens}`;
}

async function launch(levelId: number) {
  if (launching) return;
  launching = true;
  currentLevel = levelId;
  show(finish, false);
  show(pauseOverlay, false);
  show(hud, false);
  chainEl.textContent = '';
  setLevelButtonsDisabled(true);
  const loading = $('#loading');
  const oldLoading = loading.textContent;
  loading.textContent = `Загрузка спуска ${String(levelId).padStart(2, '0')}…`;
  try {
    await game.startLevel(levelId);
    show(menu, false);
    show(hud, true);
    yandex.gameplayStart();
  } catch (error) {
    console.error('Drop Flow level load failed', error);
    loading.textContent = 'Не удалось загрузить уровень. Попробуй ещё раз.';
    setLevelButtonsDisabled(false);
    throw error;
  } finally {
    launching = false;
    if (!menu.classList.contains('visible')) loading.textContent = oldLoading;
  }
}

async function bootstrap() {
  await yandex.init();
  progress = await yandex.loadProgress();

  game = new DropGame3D(gameHost, {
    onReady: () => {
      renderLevels();
      $('#loading').textContent = 'Готово. Выбери верхнюю точку и начинай спуск.';
      yandex.ready();
    },
    onHud: ({ level, stage, stageCount, score, combo, dropLeft, state, target, chain }) => {
      $('#objective').textContent = `${String(level.id).padStart(2, '0')} · ${level.name}`;
      scoreEl.textContent = Math.round(score).toLocaleString('ru-RU');
      stageEl.textContent = `${stage}/${stageCount}`;
      dropEl.textContent = `${dropLeft.toFixed(1)} м`;
      comboEl.textContent = `×${combo}`;
      stateEl.textContent = state;
      targetEl.textContent = target;
      chainEl.textContent = chain;
    },
    onTrick: (event, chain) => {
      window.dispatchEvent(new CustomEvent('drop-avatar-trick', { detail: { kind: event.kind } }));
      popToast(`${event.label}  +${event.points}`);
      chainEl.textContent = chain;
    },
    onLanding: (result) => {
      popToast(`${result.label}  +${result.stageScore.toLocaleString('ru-RU')}`);
      document.body.classList.remove('impact', 'perfect');
      void document.body.offsetWidth;
      if (result.grade === 'perfect') document.body.classList.add('perfect');
      if (result.grade === 'rough') document.body.classList.add('impact');
    },
    onMiss: (falls) => {
      progress.totalFalls += 1;
      void yandex.saveProgress(progress);
      document.body.classList.remove('impact');
      void document.body.offsetWidth;
      document.body.classList.add('impact');
      popToast(falls === 1 ? 'МИМО · −120 · ПОВТОР ТОЧКИ' : `ПРОМАХ ${falls} · −120`);
    },
    onFinish: async ({ level, stats, reward }) => {
      yandex.gameplayStop();
      completionCount += 1;
      const key = String(level.id);
      const oldBest = progress.bestScores[key] ?? 0;
      if (stats.score > oldBest) progress.bestScores[key] = stats.score;
      progress.bestCombos[key] = Math.max(progress.bestCombos[key] ?? 1, stats.bestCombo);
      if (!progress.completedLevels.includes(level.id)) progress.completedLevels.push(level.id);
      progress.unlockedLevel = Math.max(progress.unlockedLevel, Math.min(MAX_LEVEL, level.id + 1));

      const medal = medalFor(level, stats.score);
      const previousMedal = progress.bestMedals[key];
      if (medal && (!previousMedal || MEDAL_RANK[medal] > MEDAL_RANK[previousMedal])) progress.bestMedals[key] = medal;
      const cleanNow = stats.falls === 0;
      if (cleanNow && !progress.cleanLevels.includes(level.id)) progress.cleanLevels.push(level.id);
      const firstClear = oldBest === 0;
      const cleanBonus = cleanNow ? 30 : 0;
      const firstBonus = firstClear ? 25 : 0;
      const totalReward = reward + cleanBonus + firstBonus;
      progress.tokens += totalReward;
      await yandex.saveProgress(progress);

      pendingNextLevel = Math.min(MAX_LEVEL, level.id + 1);
      const medalText = medal ? MEDAL_NAME[medal] : 'БЕЗ МЕДАЛИ';
      finishTitle.textContent = `${level.name}: ${stats.score.toLocaleString('ru-RU')} очков`;
      finishStats.innerHTML = `
        <b>${medalText}</b>
        <span>ориентир: ${level.parScore.toLocaleString('ru-RU')} очк.</span>
        <span>трюков: ${stats.tricks} · видов: ${stats.uniqueTricks}</span>
        <span>идеальных посадок: ${stats.perfectLandings}</span>
        <span>лучшее комбо: ×${stats.bestCombo}</span>
        <span>спуск: ${stats.totalDrop.toFixed(1)} м</span>
        <span>промахов: ${stats.falls}${cleanNow ? ' · ЧИСТАЯ ЛИНИЯ' : ''}</span>
        <span>награда: +${totalReward} ◆</span>
      `;
      nextButton.hidden = level.id >= MAX_LEVEL;
      show(hud, false);
      show(finish, true);
      renderLevels();
      if (completionCount % 2 === 0) await yandex.showInterstitial();
    }
  });

  await game.init();
}

nextButton.addEventListener('click', () => void launch(pendingNextLevel));
retryButton.addEventListener('click', () => void launch(currentLevel));
levelMenuButton.addEventListener('click', () => {
  yandex.gameplayStop();
  show(finish, false);
  show(hud, false);
  show(menu, true);
});

pauseButton.addEventListener('click', () => {
  game?.setPaused(true);
  yandex.gameplayStop();
  show(pauseOverlay, true);
});
resumeButton.addEventListener('click', () => {
  game?.setPaused(false);
  yandex.gameplayStart();
  show(pauseOverlay, false);
});

window.addEventListener('platform-pause', () => {
  game?.setPaused(true);
  yandex.gameplayStop();
});
window.addEventListener('platform-resume', () => {
  game?.setPaused(false);
  if (game?.isRunning()) yandex.gameplayStart();
});

bootstrap().catch((error) => {
  console.error('Drop Flow boot failed', error);
  $('#loading').textContent = 'Ошибка запуска. Обнови страницу.';
});
