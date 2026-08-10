# UMK3 HD Fan Remake 0.5.0

Личный некоммерческий fan-remake Ultimate Mortal Kombat 3 для Android. Проект находится только в ветке `umk3-hd-fan-remake` и не смешивается с `main`.

## Что уже работает

- полностью автономная Android-игра без сервера и API;
- arcade/Kombat Tower с Motaro и Shao Kahn;
- 25 доступных бойцов, включая Rain, Noob Saibot, Human Smoke и Classic Sub-Zero;
- 16 арен: Subway, Street, Rooftop, Bank, Soul Chamber, Bell Tower, Kombat Temple, Graveyard, Waterfront, Lost Portal, Jade's Desert, Kahn's Kave, Scorpion's Lair, Balcony, Noob's Dorfen, Pit III;
- шестикнопочная схема UMK3: LP / HP / LK / HK / BLOCK / RUN;
- командный input buffer F / B / U / D относительно соперника;
- standing/crouching/jumping attacks, sweep, uppercut, throw, block и run meter;
- индивидуальные спецприёмы: projectiles, freeze, spear, net, teleport, bombs, reflect, morph, telekinesis, stomp и другие;
- CPU AI;
- best-of-three, таймер 99, combo counter;
- Finish Him / Finish Her;
- character Fatalities и stage fatalities;
- кровь/частицы, hit flash, screen shake, синтезированный звук и haptics;
- адаптивное экранное управление Android с safe-area/cutout;
- fullscreen Android WebView shell.

## Новый графический слой 0.5.0

Версия 0.5.0 отделяет графику от боевой физики. Проверенный Canvas fighting runtime продолжает отвечать за input, hitboxes, damage, AI и state machine, а отдельный `hd-renderer.js` отвечает только за изображение.

Добавлено:

- `hd-atlases.js` — ленивый atlas runtime для всего ростера;
- по 16 фиксированных HD-векторных кадров на бойца: idle, walk/run, crouch, jump, LP/HP/LK/HK, hit, block, special, win;
- отдельные визуальные шаблоны/детали для ninja, female ninja, Sub-Zero, cyborg, Jax, Kano, Nightwolf, Sindel, Stryker, Kung Lao, Kabal, Shang Tsung, Liu Kang, Sheeva, Motaro и Shao Kahn;
- отдельный HD overlay canvas, не вмешивающийся в hitbox/timing логику;
- новый многослойный renderer всех 16 арен с освещением, glow, перспективой, атмосферой и анимированными элементами;
- atlas-портреты на Character Select;
- переработанные HUD, title screen, tower, ending и game-over presentation;
- старый renderer остаётся внутренним fallback боевого runtime, поэтому графический слой можно дальше заменять настоящими hand-drawn/AI atlas assets без переписывания механик.

Это уже фиксированный покадровый atlas-слой, но не выдаётся за финальный hand-drawn/нейросетевой арт уровня коммерческого 2D-файтинга. Следующий арт-проход может заменять эти atlas-кадры на baked PNG/WebP изображения без изменения боевой системы.

## Управление на ПК

- движение: `WASD` или стрелки;
- LP: `J`;
- HP: `U`;
- LK: `K`;
- HK: `I`;
- BLOCK: `L`;
- RUN: `O`;
- пауза: `Esc`.

На Android используются экранные D-pad, LP/HP/LK/HK, BLOCK и RUN.

## Android

Версия приложения: `0.5.0` (`versionCode 5`).

```bash
gradle :app:assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions проверяет синтаксис `umk3-data.js`, `version.js`, `hd-atlases.js`, `game.js`, `hd-renderer.js`, затем запускает `tests/smoke.cjs`, Android SDK/Gradle сборку, проверяет ненулевой APK и публикует artifact.
