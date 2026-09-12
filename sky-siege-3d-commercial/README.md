# Sky Siege 3D

Коммерческий release-candidate 3D-физической аркады для Яндекс Игр.

## Состав
- 60 уровней / 6 миров
- 8 классов боеприпасов
- 7 классов врагов
- 13 разрушаемых архитектурных модулей
- Three.js + Rapier 3D
- локальные GLB/glTF-ассеты в production build
- Quaternius RPG Characters + Kenney Castle/Nature
- облачное/локальное сохранение прогресса
- полноэкранная и rewarded-реклама
- адаптивный DOM HUD и мобильное pointer-управление
- автоматический desktop/mobile Playwright smoke-test

## Исходники
Полное дерево исходников хранится в `source.tgz`. CI распаковывает его перед сборкой; это сохраняет ветку самодостаточной и позволяет получить воспроизводимый production ZIP без CDN-зависимостей.

## Сборка
GitHub Actions распаковывает source bundle, запускает `npm install`, `npm run release`, затем реальный Chromium smoke-test на desktop/mobile и формирует `Sky-Siege-3D-Yandex.zip`.

Сторонние ассеты — CC0. Provenance и источники записываются в `public/assets/licenses/ASSETS.txt` во время сборки.
