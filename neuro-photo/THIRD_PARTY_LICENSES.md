# НейроФото — сторонние компоненты и лицензии

НейроФото строится только на компонентах, которые можно распространять без платного API.

## Local Diffusion

- Проект: `rmatif/Local-Diffusion`
- Лицензия: Apache License 2.0
- Используемый upstream commit: `184b7f92cf2f810e7d5eb4b04b190a5da829005f`
- В APK используется как Android/Flutter оболочка для локального inference.

## stable-diffusion.cpp

- Проект: `leejet/stable-diffusion.cpp`
- Лицензия: MIT
- Используется Local Diffusion как нативный локальный inference backend.

## Stable Diffusion v1.5 GGUF

- Базовая модель: Stable Diffusion v1.5
- Плановый встроенный файл: `stable-diffusion-v1-5-pruned-emaonly-Q4_0.gguf`
- Квантизация: Second State
- Лицензия модели: CreativeML Open RAIL-M
- SHA-256 опубликованного файла: `b8944e9fe0b69b36ae1b5bb0185b3a7b8ef14347fe0fa9af6c64c4829022261f`

При Full-сборке CI обязан проверить SHA-256 модели до упаковки. Если контрольная сумма не совпадает, сборка должна завершиться ошибкой.

Полные тексты применимых лицензий должны включаться в дистрибутив Full APK/Release и сохраняться рядом с исходниками проекта.
