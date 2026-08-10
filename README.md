# UMK3 HD Fan Remake 0.4.0

Личный некоммерческий fan-remake Ultimate Mortal Kombat 3 для Android. Проект находится только в ветке `umk3-hd-fan-remake` и не смешивается с `main`.

## Что уже работает

- автономная Android-игра без сервера и API;
- arcade tower с боссами Motaro и Shao Kahn;
- 25 доступных бойцов, включая секретных/бонусных Rain, Noob Saibot, Human Smoke и Classic Sub-Zero;
- 16 арен: Subway, Street, Rooftop, Bank, Soul Chamber, Bell Tower, Kombat Temple, Graveyard, Waterfront, Lost Portal, Jade's Desert, Kahn's Kave, Scorpion's Lair, Balcony, Noob's Dorfen, Pit III;
- управление в шестикнопочной схеме UMK3: LP / HP / LK / HK / BLOCK / RUN;
- команды спецприёмов вводятся последовательностями относительно соперника: F / B / U / D;
- удары стоя, в прыжке и сидя, sweep, uppercut, throw, block, run meter;
- projectiles, freeze, spear, net, teleport, bombs, reflect, morph, ground hazards и другие индивидуальные типы спецприёмов;
- CPU AI;
- best-of-three раунды и таймер 99;
- combo counter;
- Finish Him / Finish Her;
- Fatality-команды персонажей и stage fatalities на подходящих аренах;
- кровь/частицы, screen shake, синтезированный звук и вибрация;
- переработанный процедурный HD/vector renderer бойцов с отдельными силуэтами и деталями для ниндзей, киборгов, Jax, Kano, Nightwolf, Sindel, Kung Lao, Kabal, Sheeva, Motaro, Shao Kahn и др.;
- современные динамические версии арен с анимацией окружения;
- адаптивное экранное управление с учётом safe-area/cutout;
- полноэкранный Android WebView shell.

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

Версия приложения: `0.4.0` (`versionCode 4`).

Сборка:

```bash
gradle :app:assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions перед сборкой выполняет:

1. `node --check web/umk3-data.js`;
2. `node --check web/game.js`;
3. `node tests/smoke.cjs`;
4. Gradle `assembleDebug`;
5. проверку существования ненулевого APK;
6. загрузку APK как Actions artifact.

## Графика

В текущей версии бойцы и арены рисуются собственным HD/vector renderer, поэтому APK полностью автономен и не содержит выдранные оригинальные sprite sheets или фоновые изображения. Архитектура проекта также предусматривает дальнейшую замену отдельных визуальных состояний на HD sprite atlases без изменения hitbox/timing логики.

См. `docs/ASSET_PIPELINE.md`.

## Проверка 0.4.0

CI-проверка версии 0.4.0 завершилась успешно: JavaScript verification, smoke-test игрового цикла, Android SDK setup, `assembleDebug`, проверка APK и upload artifact прошли без ошибок.
