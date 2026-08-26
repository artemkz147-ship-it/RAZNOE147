# Динозавры: Живая энциклопедия

Рабочая ветка: `dino-encyclopedia-3d`.

## Реализовано в коде
- Godot 4.7.2 Mobile/Vulkan-проект под Android.
- Загрузка динозавра и среды из отдельных GLB-ассетов.
- Вращение одним пальцем, pinch-zoom, мышь/колесо для проверки на ПК.
- Автоподстройка камеры под габариты модели.
- Контроллер анимаций и интерактивные действия.
- Кнопки «Рык», «Действие», «Справка», «Слушать», листание существ.
- Локальные ambience/narration/roar дорожки; системный TTS только как dev fallback.
- Три режима качества: Производительность / Высокое / Кино.
- JSON-каталог с научными оговорками и источниками.
- GitHub Actions workflow для debug APK.

## Не выдаётся за готовое
Фотореалистичная живая модель T. rex, финальная Hell Creek-среда и записанные звуки ещё отсутствуют. Код ожидает их по путям:
- `assets/dinosaurs/tyrannosaurus_rex/model.glb`
- `assets/environments/hell_creek/environment.glb`
- `assets/audio/tyrannosaurus_rex/ambience.ogg`
- `assets/audio/tyrannosaurus_rex/narration_ru.ogg`
- `assets/audio/tyrannosaurus_rex/roar_reconstruction.ogg`

Дешёвые placeholder-ассеты не считаются финальной версией.
