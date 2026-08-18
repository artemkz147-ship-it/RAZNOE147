import './style.css';
import { Game3D } from './game3d/Game3D';
import { yandex, type ParkourProgress } from './platform/yandex';

const $ = <T extends HTMLElement>(selector: string) => document.querySelector(selector) as T;

const gameHost = $('#game');
const menu = $('#menu');
const levelsHost = $('#levels');
const hud = $('#hud');
const finish = $('#finish');
const finishTitle = $('#finishTitle');
const finishStats = $('#finishStats');
const timeEl = $('#time');
const speedEl = $('#speed');
const checkpointEl = $('#checkpoint');
const stateEl = $('#state');
const tokenEl = $('#tokens');
const nextButton = $('#next') as HTMLButtonElement;
const retryButton = $('#retry') as HTMLButtonElement;
const levelMenuButton = $('#levelMenu') as HTMLButtonElement;
const pauseButton = $('#pause') as HTMLButtonElement;
const resumeButton = $('#resume') as HTMLButtonElement;
const pauseOverlay = $('#pauseOverlay');

let game: Game3D;
let currentLevel = 1;
let pendingNextLevel = 1;
let progress: ParkourProgress = { unlockedLevel: 1, bestTimes: {}, completedLevels: [], tokens: 0, totalFalls: 0 };
let completionCount = 0;

function show(element: HTMLElement, visible: boolean) {
  element.classList.toggle('visible', visible);
}

function formatTime(seconds: number) {
  const minutes = Math.floor(seconds / 60);
  const rest = seconds - minutes * 60;
  return minutes > 0 ? `${minutes}:${rest.toFixed(2).padStart(5, '0')}` : `${rest.toFixed(2)} с`;
}

function renderLevels() {
  levelsHost.innerHTML = '';
  for (const level of game.levels) {
    const unlocked = level.id <= progress.unlockedLevel;
    const complete = progress.completedLevels.includes(level.id);
    const best = progress.bestTimes[String(level.id)];
    const button = document.createElement('button');
    button.className = `level-card${complete ? ' complete' : ''}`;
    button.disabled = !unlocked;
    button.innerHTML = `
      <span class="level-number">${String(level.id).padStart(2, '0')}</span>
      <span class="level-copy"><b>${level.name}</b><small>${level.subtitle}</small></span>
      <span class="level-meta">${!unlocked ? 'ЗАКРЫТО' : best ? formatTime(best) : complete ? 'ПРОЙДЕНО' : 'СТАРТ'}</span>
    `;
    button.addEventListener('click', () => void launch(level.id));
    levelsHost.appendChild(button);
  }
  tokenEl.textContent = `◆ ${progress.tokens}`;
}

async function launch(levelId: number) {
  currentLevel = levelId;
  show(menu, false);
  show(finish, false);
  show(pauseOverlay, false);
  show(hud, true);
  await game.startLevel(levelId);
  yandex.gameplayStart();
}

async function bootstrap() {
  await yandex.init();
  progress = await yandex.loadProgress();

  game = new Game3D(gameHost, {
    onReady: () => {
      renderLevels();
      yandex.ready();
    },
    onHud: ({ level, time, speed, checkpoint, checkpointCount, wallRun, breaks }) => {
      $('#objective').textContent = `${String(level.id).padStart(2, '0')} · ${level.name}`;
      timeEl.textContent = formatTime(time);
      speedEl.textContent = `${Math.round(speed * 3.6)} км/ч`;
      checkpointEl.textContent = checkpointCount ? `${checkpoint}/${checkpointCount}` : '—';
      stateEl.textContent = wallRun ? 'WALL RUN' : breaks ? `РАЗРУШЕНО ${breaks}` : 'FLOW';
    },
    onCheckpoint: (index, total) => {
      const toast = $('#toast');
      toast.textContent = `ЧЕКПОИНТ ${index}/${total}`;
      toast.classList.remove('pop');
      void toast.offsetWidth;
      toast.classList.add('pop');
    },
    onFall: (falls) => {
      progress.totalFalls += 1;
      document.body.classList.remove('impact');
      void document.body.offsetWidth;
      document.body.classList.add('impact');
      $('#toast').textContent = falls === 1 ? 'СОРВАЛСЯ · ВОЗВРАТ К ЧЕКПОИНТУ' : `СРЫВ ${falls}`;
    },
    onFinish: async ({ level, time, stats, reward }) => {
      yandex.gameplayStop();
      completionCount += 1;
      const key = String(level.id);
      const previous = progress.bestTimes[key];
      if (!previous || time < previous) progress.bestTimes[key] = time;
      if (!progress.completedLevels.includes(level.id)) progress.completedLevels.push(level.id);
      progress.unlockedLevel = Math.max(progress.unlockedLevel, Math.min(8, level.id + 1));
      progress.tokens += reward;
      await yandex.saveProgress(progress);

      pendingNextLevel = Math.min(8, level.id + 1);
      finishTitle.textContent = `${level.name} пройден`;
      finishStats.innerHTML = `<b>${formatTime(time)}</b><span>падения: ${stats.falls}</span><span>разрушено: ${stats.breaks}</span><span>награда: +${reward} ◆</span>`;
      nextButton.hidden = level.id >= 8;
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

window.addEventListener('platform-pause', () => game?.setPaused(true));
window.addEventListener('platform-resume', () => game?.setPaused(false));

bootstrap().catch((error) => {
  console.error('3D parkour boot failed', error);
  $('#loading').textContent = 'Ошибка запуска. Обнови страницу.';
});
