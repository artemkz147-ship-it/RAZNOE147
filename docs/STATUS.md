# Project status — UMK3 HD Fan Remake 0.4.0

## Реализовано

- Отдельная ветка `umk3-hd-fan-remake`.
- Офлайн HTML5/Canvas fighting runtime внутри Android WebView.
- Логическое разрешение 1280×720 с адаптивным масштабированием.
- Landscape fullscreen, immersive mode и поддержка display cutout.
- 25 доступных бойцов + Motaro и Shao Kahn как боссы.
- 16 переработанных арен.
- Character Select и Arcade/Kombat Tower.
- Best-of-three, таймер 99, health bars и round markers.
- Шестикнопочная схема LP / HP / LK / HK / BLOCK / RUN.
- Командный input buffer с относительными F/B/U/D.
- Standing/crouching/jumping attacks, sweep, uppercut, throw, block и run meter.
- Индивидуальные special move archetypes: projectiles, spear, freeze, net, teleport, bombs, ground attacks, reflect, morph, telekinesis, stomp и др.
- CPU AI.
- Combo counter и hit-chain logic.
- Finish Him / Finish Her.
- Character Fatality command checking и stage fatalities.
- Procedural HD/vector fighter renderer с отдельными стилями персонажей.
- Динамические procedural/vector версии всех текущих арен.
- Particles, blood toggle, hit flash, screen shake, synthesized SFX и haptics.
- Мобильный D-pad + LP/HP/LK/HK/BLOCK/RUN.
- GitHub Actions APK pipeline.

## Проверено

Для commit `8eb35b5786edab2c328e9caadcdaacc33763bf7e` GitHub Actions run `31386810132` завершился успешно.

Успешно прошли:

1. checkout;
2. Node setup;
3. `node --check web/umk3-data.js`;
4. `node --check web/game.js`;
5. `node tests/smoke.cjs`;
6. Java 17 setup;
7. Android SDK 35 setup;
8. Gradle 8.7 setup;
9. `gradle :app:assembleDebug --stacktrace`;
10. проверка существования ненулевого `app-debug.apk`;
11. загрузка APK в GitHub Actions artifact.

Artifact: `umk3-hd-debug-apk`, artifact id `9062039739`.

## Что ещё не называем финальным 1.0

- Текущий современный визуал бойцов — собственный runtime HD/vector redraw, а не полный комплект покадровых hand-drawn/AI sprite atlases для каждого движения.
- Для абсолютного one-to-one соответствия ещё потребуется вручную довести frame timing/hitboxes каждого оригинального приёма и все редкие finishing systems/secret codes.
- Оригинальные Midway/Warner sprite sheets, музыка и аудио не включаются в репозиторий.
- Реальная установка APK на физический Android-девайс этим CI не проверяется; проверены успешная компиляция и наличие APK.

## Следующий уровень качества

- Atlas renderer поверх уже существующей боевой логики.
- Reference-quality HD animation sets для Scorpion/Sub-Zero, после чего масштабирование пайплайна на весь ростер.
- Более сложные многослойные HD stage assets при сохранении текущих динамических fallback-арен.
- Полное расширение finishing systems и tuning точных hitbox/frame tables.
