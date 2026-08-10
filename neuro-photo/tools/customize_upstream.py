#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()


def replace_required(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"Expected text not found in {path}: {old!r}")
    path.write_text(text.replace(old, new), encoding="utf-8")


def replace_if_present(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old in text:
        path.write_text(text.replace(old, new), encoding="utf-8")


def replace_literal(path: Path, old: str, new: str) -> None:
    """Replace only complete Dart string literals, never identifiers."""
    text = path.read_text(encoding="utf-8")
    text = text.replace(f"'{old}'", f"'{new}'")
    text = text.replace(f'"{old}"', f'"{new}"')
    path.write_text(text, encoding="utf-8")


# Branding/package.
replace_required(
    ROOT / "android/app/build.gradle",
    'namespace "com.example.local_diffusion"',
    'namespace "com.artem147.neurophoto"',
)
replace_required(
    ROOT / "android/app/build.gradle",
    'applicationId "com.example.local_diffusion"',
    'applicationId "com.artem147.neurophoto"',
)
replace_required(
    ROOT / "android/app/src/main/AndroidManifest.xml",
    'android:label="Local Diffusion"',
    'android:label="НейроФото"',
)

# Visible UI text only. Exact quoted literals are changed, identifiers remain untouched.
translations = {
    "Local Diffusion": "НейроФото",
    "Storage Permission Required": "Нужен доступ к файлам",
    "Grant Permission": "Разрешить доступ",
    "Load Model": "Загрузить модель",
    "Select Model": "Выбрать модель",
    "Model": "Модель",
    "Generate": "Создать",
    "Image to Image": "Стилизация фото",
    "Img2Img": "Стилизация фото",
    "Inpainting": "Замена по маске",
    "Outpainting": "Расширение кадра",
    "Upscaler": "Улучшение качества",
    "Upscale": "Улучшить",
    "PhotoMaker": "Портрет по референсу",
    "Scribble to Image": "По наброску",
    "Prompt": "Что изменить",
    "Negative Prompt": "Чего не должно быть",
    "Steps": "Шаги",
    "Width": "Ширина",
    "Height": "Высота",
    "Strength": "Сила изменения",
    "Backend": "Ускорение",
    "Save Image": "Сохранить фото",
    "Save": "Сохранить",
    "Cancel": "Отмена",
    "Close": "Закрыть",
    "Error": "Ошибка",
}

for dart_file in (ROOT / "lib").glob("*.dart"):
    for old, new in translations.items():
        replace_literal(dart_file, old, new)

# Replace one longer user-facing permission sentence safely.
main_dart = ROOT / "lib/main.dart"
replace_if_present(
    main_dart,
    'This app needs permission to read and write files (including models) in storage to function correctly. Please grant the "All files access" permission in the app settings.',
    'НейроФото хранит локальные нейросети и результаты на телефоне. Разрешите доступ к файлам — фотографии никуда не отправляются.',
)

(ROOT / ".neurophoto-customized").write_text(
    "NeuroPhoto customization applied\n", encoding="utf-8"
)
print("NeuroPhoto customization applied successfully")
