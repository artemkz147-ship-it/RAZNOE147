# Echo Ruins — 3D Platformer

Мобильный 3D-платформер без Unity/Godot. Runtime: Three.js + Rapier + Vite, Android shell: Capacitor.

## Что уже есть
- управление с телефона: виртуальный стик + прыжок + рывок + атака;
- клавиатура/мышь на ПК;
- двойной прыжок, coyote time, jump buffer, dash;
- 16 островов/платформ, движущиеся платформы, пропасти;
- 8 осколков, 5 врагов, 2 чекпоинта, портал-финиш;
- HP, урон, респаун, таймер и best time в localStorage;
- процедурная low-poly/PBR графика, тени, fog, звёзды, частицы;
- процедурные звуки через WebAudio — игра офлайн;
- адаптивное качество для мобильных GPU;
- GitHub Actions автоматически собирает debug APK.

## Локальный запуск
```bash
npm install
npm run dev
```

## APK
Workflow `Build Echo Ruins APK` запускается при push в ветку `game/3d-platformer-echo-ruins` и публикует артефакт `Echo-Ruins-debug-apk`.

## Лицензии зависимостей
- Three.js — MIT
- Rapier — Apache-2.0
- Capacitor — MIT

Для следующего арт-прохода можно подключать CC0-модели Kenney и собственные GLB/AI-generated модели; игровая логика от ассетов не зависит.
