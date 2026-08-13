# Project status — UMK3 HD Fan Remake 0.6.0

## Исправление после неудачной 0.5.0

Версия 0.5.0 была слишком маленькой WebView-сборкой (~53 KB APK без настоящего raster asset pack) и пользователь сообщил, что она не открывалась на устройстве. Эта версия больше не считается валидным результатом.

## 0.6.0

- Android versionCode 6 / versionName 0.6.0.
- Добавлен boot guard в JavaScript.
- MainActivity обёрнута защитой запуска и показывает диагностический экран вместо молчаливого завершения при ошибке.
- Добавлен baked raster pipeline `tools/build_assets.py`.
- В build генерируются 27 fighter PNG atlases и 16 raster stage PNG.
- В APK проверяется наличие реальных fighter/stage assets.
- CI отклоняет APK меньше 5 MB.
- Добавлен отдельный raster presentation runtime.
- Добавлен реальный Android emulator launch-test через ADB.

## Реально проверено

GitHub Actions run: `31397761355`.
Head commit: `fdad03e8dca43aede75ad14c37b50c70e9d7e2df`.

Успешно прошли:

1. генерация baked raster asset pack;
2. проверка количества/размера raster assets;
3. JavaScript syntax checks;
4. raster fighting-loop smoke test;
5. Android SDK / Gradle setup;
6. `assembleDebug`;
7. проверка, что APK больше 5 MB;
8. проверка наличия `assets/fighters/scorpion/atlas.png` и `assets/stages/subway.png` внутри APK;
9. установка APK на Android API 31 emulator через `adb install`;
10. холодный запуск `com.raznoe147.umk3hd/.MainActivity` со статусом `ok`;
11. процесс приложения остаётся жив;
12. MainActivity остаётся resumed;
13. logcat: `Page finished: file:///android_asset/index.html`;
14. logcat: `Runtime check: true`;
15. отсутствие `FATAL EXCEPTION`;
16. сохранение screenshot emulator evidence.

APK artifact: `umk3-hd-0.6.0-debug-apk`, artifact id `9066423388`.
Emulator evidence artifact: `umk3-0.6.0-emulator-evidence`, artifact id `9066422443`.
APK artifact size: 11,692,719 bytes zipped artifact; extracted APK 11,825,921 bytes.

## Честное ограничение

0.6.0 теперь является реальным устанавливаемым raster build и подтверждённо запускается на Android emulator. При этом художественный уровень sprite atlases всё ещё не является финальным коммерческим HD-remaster: это следующий отдельный этап качества графики.
