import { useMemo, useState } from "react";
import {
  createGameState,
  openMultiverse,
  placeGuess,
  selectUniverse,
  submitGuess,
  type GameState,
  type Point,
} from "./game/state";
import { mapPointFromClient } from "./game/mapCoordinates";
import { SCENARIOS, WORLD_BY_ID, WORLDS, type World } from "./game/worlds";
import PanoramaViewer from "./PanoramaViewer";

const universeNodes = [
  [16, 27], [37, 13], [58, 22], [80, 34], [67, 49],
  [83, 72], [60, 82], [39, 72], [20, 83], [30, 48],
];

function App() {
  const [roundIndex, setRoundIndex] = useState(0);
  const scenario = SCENARIOS[roundIndex];
  const [game, setGame] = useState<GameState>(() => createGameState());
  const [selectedWorld, setSelectedWorld] = useState<World | null>(null);
  const [savedBest, setSavedBest] = useState(() => Number(localStorage.getItem("worlds-best-score") || 0));
  const score = game.phase === "result" ? game.score.total : 0;

  const startUniversePicker = () => setGame((current) => current.phase === "panorama" ? openMultiverse(current) : current);

  const chooseWorld = (world: World) => {
    setSelectedWorld(world);
    setGame((current) => current.phase === "multiverse" ? selectUniverse(current, world.id) : current);
  };

  const handleMapClick = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!selectedWorld || game.phase !== "map") return;
    const rect = event.currentTarget.getBoundingClientRect();
    const point = mapPointFromClient(event.clientX, event.clientY, rect);
    setGame((current) => current.phase === "map" ? placeGuess(current, point) : current);
  };

  const handleSubmit = () => {
    if (game.phase !== "map") return;
    const result = submitGuess(game, {
      universeId: scenario.universeId,
      point: scenario.point,
      label: scenario.locationName,
    });
    setGame(result);
    if (result.score.total > savedBest) {
      localStorage.setItem("worlds-best-score", String(result.score.total));
      setSavedBest(result.score.total);
    }
  };

  const restart = () => {
    setSelectedWorld(null);
    setRoundIndex((current) => (current + 1) % SCENARIOS.length);
    setGame(createGameState());
  };

  const backToWorlds = () => {
    setSelectedWorld(null);
    setGame({ phase: "multiverse", selectedUniverseId: null, guess: null });
  };

  const answerWorld = WORLD_BY_ID[scenario.universeId];

  return (
    <main className="game-shell">
      <PanoramaScene imagePath={scenario.panoramaPath} />
      <div className="edge-vignette" />

      <header className="top-hud">
        <a className="brand-mark" href="#" onClick={(event) => event.preventDefault()} aria-label="Worlds home">
          <span className="brand-orbit" aria-hidden="true"><i /><b /></span>
          <span className="brand-copy"><strong>WORLDS</strong><small>THE CINEMATIC GUESS</small></span>
        </a>
        <div className="round-chip"><span className="live-dot" /> <span>РАУНД</span><b>{String(roundIndex + 1).padStart(2, "0")} <i>/ 10</i></b></div>
        <div className="score-chip"><small>ОЧКИ</small><strong>{score.toLocaleString("ru-RU")}</strong><span>РЕКОРД {savedBest.toLocaleString("ru-RU")}</span></div>
      </header>

      {game.phase === "panorama" && (
        <section className="scene-controls" aria-label="Управление раундом">
          <div className="scene-caption"><span className="eyebrow"><i /> БЕЗ ДАТ · БЕЗ ПОДСКАЗОК</span><h1>Где ты оказался?</h1><p>Осмотрись. Сначала угадай мир, потом — точку на карте.</p></div>
          <div className="scene-actions">
            <div className="control-hint"><span className="drag-icon">⤢</span><span>Зажми и вращай<br /><small>Колесо — приблизить</small></span></div>
            <button className="primary-button" onClick={startUniversePicker}>УГАДАТЬ МИР <span>↗</span></button>
          </div>
        </section>
      )}

      {game.phase === "multiverse" && (
        <UniversePicker onChoose={chooseWorld} onBack={() => setGame(createGameState())} />
      )}

      {(game.phase === "map" || game.phase === "result") && selectedWorld && (
        <section className="map-stage" aria-label="Карта вселенной">
          <div className="map-heading">
            <button className="back-button" onClick={backToWorlds}>← <span>Миры</span></button>
            <div><span className="eyebrow">МИР ВЫБРАН</span><h1>{selectedWorld.title}</h1><p>{selectedWorld.mapScale}</p></div>
            <div className="map-step"><span>ШАГ 02</span><b>Найди место</b></div>
          </div>
          <WorldMap
            world={selectedWorld}
            guess={game.guess}
            answer={game.phase === "result" ? scenario.point : null}
            onMapClick={handleMapClick}
          />
          {game.phase === "map" ? (
            <div className="map-bottom-row">
              <p>{game.guess ? "Точка выбрана — можно передвинуть её на карте" : "Нажми на карту, чтобы поставить точку"}</p>
              <button className="primary-button map-submit" disabled={!game.guess} onClick={handleSubmit}>ЗАФИКСИРОВАТЬ <span>↗</span></button>
            </div>
          ) : (
            <ResultCard game={game} answerWorld={answerWorld} onRestart={restart} />
          )}
        </section>
      )}

      <footer className="source-credit">
        <span className="source-ring" aria-hidden="true">360°</span>
        <span>СОЗДАНО ИИ · ПАНОРАМА {String(roundIndex + 1).padStart(2, "0")} / 10</span>
      </footer>
    </main>
  );
}

function PanoramaScene({ imagePath }: { imagePath: string }) {
  return (
    <PanoramaViewer imagePath={imagePath} />
  );
}

function UniversePicker({ onChoose, onBack }: { onChoose: (world: World) => void; onBack: () => void }) {
  return (
    <section className="universe-overlay" aria-label="Карта мультивселенной">
      <div className="universe-topline">
        <button className="back-button" onClick={onBack}>← <span>Панорама</span></button>
        <div><span className="eyebrow">ШАГ 01 · ОПРЕДЕЛИ ВСЕЛЕННУЮ</span><h1>Куда занесло?</h1></div>
        <span className="atlas-coordinate">ATLAS / 001</span>
      </div>
      <div className="multiverse-map">
        <svg className="orbit-lines" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
          <ellipse cx="50" cy="50" rx="36" ry="23" />
          <ellipse cx="50" cy="50" rx="27" ry="42" transform="rotate(38 50 50)" />
          <ellipse cx="50" cy="50" rx="29" ry="42" transform="rotate(-43 50 50)" />
          <path d="M16 27 37 13 58 22 80 34 67 49 83 72 60 82 39 72 20 83 30 48Z" />
          <circle cx="50" cy="50" r="2.2" />
        </svg>
        <div className="atlas-core"><div className="core-rings"><i /><i /><i /></div><span>МУЛЬТИ<br />ВСЕЛЕННАЯ</span></div>
        {WORLDS.map((world, index) => {
          const [x, y] = universeNodes[index];
          return (
            <button
              className="universe-node"
              style={{ left: `${x}%`, top: `${y}%`, "--world-accent": world.accent } as React.CSSProperties}
              key={world.id}
              onClick={() => onChoose(world)}
            >
              <span className="node-star">✦</span>
              <span className="node-label">{world.title}</span>
              <small>{world.subtitle}</small>
            </button>
          );
        })}
      </div>
      <div className="universe-hint"><span>01</span><p>Выбери один из <b>10 миров</b>, который видишь в панораме</p><span className="hint-rule" /></div>
    </section>
  );
}

function WorldMap({ world, guess, answer, onMapClick }: { world: World; guess: Point | null; answer: Point | null; onMapClick: (event: React.PointerEvent<HTMLDivElement>) => void }) {
  const landStyles = useMemo(() => ({ fill: `${world.accent}1c`, stroke: `${world.accent}a8` }), [world.accent]);
  const handlePointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;
    onMapClick(event);
  };

  return (
    <div className={`world-map map-${world.mapType}`} onPointerDown={handlePointerDown} role="button" tabIndex={0} aria-label={`Карта: ${world.mapTitle}. Нажмите, чтобы отметить место`}>
      <div className="map-grid" aria-hidden="true" />
      <div className="map-compass" aria-hidden="true"><span>N</span><i>✦</i></div>
      <div className="map-ruler" aria-hidden="true"><span>0</span><i /><span>MAP COORDINATES</span><i /><span>100</span></div>
      <div className="map-art">
        {world.mapType === "city" ? <CityGrid accent={world.accent} /> : world.mapType === "galaxy" ? <GalaxyLines accent={world.accent} /> : null}
        <svg viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
          <path d={world.mapShape} fill={landStyles.fill} stroke={landStyles.stroke} strokeWidth="0.7" strokeLinejoin="round" />
          {world.landmarks.map((landmark) => (
            <g key={landmark.label} transform={`translate(${landmark.x * 100}, ${landmark.y * 100})`} className="landmark-dot">
              <circle r="0.95" /><text x="1.8" y="-1.4">{landmark.label}</text>
            </g>
          ))}
        </svg>
      </div>
      <div className="map-title-plaque"><span className="eyebrow">КАРТА МИРА</span><strong>{world.mapTitle}</strong><small>{world.mapScale}</small></div>
      {guess && <Marker point={guess} className="guess-marker" label="ТВОЯ ТОЧКА" />}
      {answer && <Marker point={answer} className="answer-marker" label="ОТВЕТ" />}
      <div className="map-mode-tag"><span className="live-dot" /> ТАПНИ НА КАРТУ</div>
    </div>
  );
}

function CityGrid({ accent }: { accent: string }) {
  return (
    <svg className="city-lines" viewBox="0 0 100 100" aria-hidden="true" style={{ color: accent }}>
      {Array.from({ length: 11 }, (_, i) => <line key={`h${i}`} x1="12" x2="88" y1={17 + i * 6.6} y2={17 + i * 6.6} />)}
      {Array.from({ length: 11 }, (_, i) => <line key={`v${i}`} y1="15" y2="85" x1={14 + i * 7.2} x2={14 + i * 7.2} />)}
    </svg>
  );
}

function GalaxyLines({ accent }: { accent: string }) {
  return (
    <svg className="city-lines galaxy-lines" viewBox="0 0 100 100" aria-hidden="true" style={{ color: accent }}>
      <path d="M13 73 30 40 52 53 75 23 88 65M20 24 39 72 67 45 83 81M17 49 43 18 58 78 81 34" />
      {[ [13,73], [30,40], [52,53], [75,23], [88,65], [20,24], [39,72], [67,45], [83,81] ].map(([x,y], i) => <circle key={i} cx={x} cy={y} r="1.1" />)}
    </svg>
  );
}

function Marker({ point, className, label }: { point: Point; className: string; label: string }) {
  return <div className={`map-marker ${className}`} style={{ left: `${point.x * 100}%`, top: `${point.y * 100}%` }}><span /><b>{label}</b></div>;
}

function ResultCard({ game, answerWorld, onRestart }: { game: Extract<GameState, { phase: "result" }>; answerWorld: World; onRestart: () => void }) {
  const score = game.score;
  const worldCorrect = game.selectedUniverseId === game.answer?.universeId;
  return (
    <div className="result-card">
      <div className="result-copy"><span className="eyebrow">РАУНД ЗАВЕРШЁН</span><h2>{worldCorrect ? "Мир угадан" : "Мимо вселенной"}</h2><p>Это <b>{answerWorld.title}</b> · {game.answer?.label}</p></div>
      <div className="score-breakdown"><span><small>ВСЕЛЕННАЯ</small><b>{score.universe.toLocaleString("ru-RU")} <i>/ 1 000</i></b></span><span><small>ЛОКАЦИЯ</small><b>{score.location.toLocaleString("ru-RU")} <i>/ 4 000</i></b></span><strong>{score.total.toLocaleString("ru-RU")} <small>ОЧКОВ</small></strong></div>
      <button className="primary-button" onClick={onRestart}>ЕЩЁ РАУНД <span>↻</span></button>
    </div>
  );
}

export default App;
