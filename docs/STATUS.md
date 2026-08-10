# Project status — UMK3 HD Fan Remake 0.5.0

## Реализовано

- Отдельная ветка `umk3-hd-fan-remake`.
- Офлайн HTML5/Canvas fighting runtime внутри Android WebView.
- Логическое разрешение 1280×720 с адаптивным масштабированием.
- Landscape fullscreen, immersive mode и display cutout support.
- 25 доступных бойцов + Motaro и Shao Kahn.
- 16 арен.
- Character Select и Arcade/Kombat Tower.
- Best-of-three, таймер 99, health bars и round markers.
- LP / HP / LK / HK / BLOCK / RUN.
- Командный input buffer F/B/U/D.
- Standing/crouching/jumping attacks, sweep, uppercut, throw, block и run meter.
- Индивидуальные special move archetypes и Fatality command checking.
- CPU AI, combo counter, hit-chain logic.
- Finish Him / Finish Her и stage fatalities.
- Particles, blood toggle, hit flash, screen shake, synthesized SFX и haptics.
- Android touch controls.

## HD presentation layer 0.5.0

- `hd-atlases.js` создаёт ленивый фиксированный 16-frame atlas для каждого бойца.
- Кадры: idle, walk/run, crouch, jump, базовые удары, hit, block, special, victory.
- Atlas создаётся только для реально нужных персонажей, чтобы не раздувать память Android WebView.
- Поддержаны отдельные силуэты/детали для основных типов бойцов, включая Motaro и Shao Kahn.
- `hd-renderer.js` работает отдельным presentation canvas поверх боевого runtime.
- Физика, hitboxes, damage, input buffer и AI не зависят от нового renderer.
- Character Select использует atlas-представление бойцов.
- Все 16 арен получили отдельный многослойный HD/vector presentation renderer с освещением, перспективой, glow и анимированным окружением.
- Переработаны title, tower, HUD, game-over и ending screens.
- Если в будущем заменить atlas source на baked PNG/WebP/AI/hand-drawn assets, боевой код менять не потребуется.

## Проверено

Предварительный HD CI run `31388146699` успешно прошёл:

1. checkout;
2. Node setup;
3. syntax check `umk3-data.js`;
4. syntax check `hd-atlases.js`;
5. syntax check `game.js`;
6. syntax check `hd-renderer.js`;
7. расширенный HD smoke-test;
8. Java 17;
9. Android SDK 35;
10. Gradle 8.7;
11. `assembleDebug`;
12. проверку ненулевого APK;
13. upload artifact.

После фиксации `versionName 0.5.0`, `versionCode 5`, `version.js` и документации запускается отдельная финальная CI-пересборка; её run/artifact фиксируется после завершения.

## Что ещё не называем финальным 1.0

- HD atlas 0.5.0 — собственный фиксированный vector-atlas layer. Это заметный шаг выше runtime stick/vector renderer 0.4.0, но он не выдаётся за финальный hand-drawn/AI artwork коммерческого уровня.
- Для максимального one-to-one ощущения UMK3 ещё требуется ручной tuning некоторых frame timing/hitbox tables и редких finishing/secret systems.
- Оригинальные Midway/Warner sprite sheets, музыка и аудио не включаются в репозиторий.
- Установка APK на физический Android-девайс этим CI не автоматизирована; проверяются логика, компиляция и APK artifact.
