# UMK3 HD Fan Remake 0.6.0

Личный некоммерческий fan-remake Ultimate Mortal Kombat 3 для Android. Рабочая ветка: `umk3-hd-fan-remake`.

## Что принципиально изменено в 0.6.0

- APK больше не считается готовым только по факту `assembleDebug`.
- Перед сборкой генерируется настоящий baked raster pack: отдельные PNG-atlas для бойцов и отдельные PNG-фоны арен.
- CI отклоняет сборку, если raster pack или APK подозрительно малы.
- Android Activity получила защиту запуска и диагностический fallback вместо молчаливого закрытия.
- После Gradle-сборки APK устанавливается через ADB на Android-эмулятор и реально запускается.
- CI проверяет, что `MainActivity` остаётся resumed, процесс жив и JavaScript runtime сообщает `Runtime check: true`.
- Сохраняется скриншот Android-эмулятора как доказательство запуска.

## Контент

- 25+ доступных бойцов, Motaro и Shao Kahn;
- 16 арен;
- Arcade/Kombat Tower;
- LP / HP / LK / HK / BLOCK / RUN;
- command input F/B/U/D;
- спецприёмы, projectiles, freeze, spear, net, teleport, bombs, reflect, morph, ground hazards;
- CPU AI;
- best-of-three, таймер 99, combo counter;
- Finish Him / Finish Her, Fatality-команды и stage fatalities;
- мобильное управление, fullscreen и safe-area/cutout;
- офлайн-работа.

## Важное ограничение

Raster pack 0.6.0 — это собственная baked-графика проекта, а не выдранные оригинальные Midway/Warner sprite sheets. Следующий художественный этап — дальнейшая ручная/AI-перерисовка каждого atlas до уровня полноценного современного ремастера.
