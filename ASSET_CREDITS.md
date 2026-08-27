# Лицензии и источники ассетов

## Tyrannosaurus rex

**Animated Tyrannosaurus Rex Dinosaur Running Loop**  
Автор: **LasquetiSpice**  
Источник: https://sketchfab.com/3d-models/animated-tyrannosaurus-rex-dinosaur-running-loop-38007d947ae74dea83988cb0b08ee053  
Лицензия: **CC BY 4.0**  

Исходные glTF-файлы берутся из публичного зеркала `adawolfs/ar`. При сборке они перепаковываются для Godot без удаления PBR-материалов, скелета и анимаций `run`, `roar`, `bite`, `idle`, `tail attack`.

## Triceratops

**Triceratops dinosaur**  
Автор: **wojciechmiedziocha**  
Источник: https://sketchfab.com/3d-models/triceratops-dinosaur-87527079bad44917ab1b98a456b46c7e  
Лицензия: **CC BY 4.0**.  
В приложение загружается проверенная производная из `s010s/prehistoric-animal-museum`, commit `4d824cb1973861c1463b012cb0d6bc5976cf9c1f`. Производная нормализована, снабжена 10-костным ригом и восьмисекундной Idle-анимацией головы и хвоста.

## Velociraptor

**PBR Velociraptor (Animated)**  
Автор: **Ferocious Industries**  
Источник: https://sketchfab.com/3d-models/pbr-velociraptor-animated-8f1744af7b0847a2aabe3df90be802f0  
Лицензия: **CC BY 4.0**, коммерческое использование разрешено.  
Проверенная копия исходных glTF/PBR-файлов берётся из `CarlosHenriqueMkt/portfolio`, commit `d11faeee4fb0e3c24288a018c905f9bf4e4d256e`, и при сборке перепаковывается в GLB.

## Stegosaurus

**PBR Stegasaurus (Animated)** — название источника сохранено с авторским написанием.  
Автор: **Ferocious Industries**  
Источник: https://sketchfab.com/3d-models/pbr-stegasaurus-animated-ec254ea1554941fe8a131f62db0faf3d  
Лицензия: **CC BY 4.0**.  
В приложение загружается проверенная производная из `s010s/prehistoric-animal-museum`, commit `4d824cb1973861c1463b012cb0d6bc5976cf9c1f`.

## Apatosaurus

**Apatosaurus**  
Автор: **toro ardido modelos 3d**  
Источник: https://sketchfab.com/3d-models/apatosaurus-fecabec8e4ef42ef98b5480dbf50c57d  
Лицензия: **CC BY 4.0**.  
В приложение загружается проверенная производная из `s010s/prehistoric-animal-museum`, commit `4d824cb1973861c1463b012cb0d6bc5976cf9c1f`. В производной заново подготовлены карты материала, масштаб, контакт с грунтом и восьмисекундная Idle-анимация.

## Dilophosaurus

**Dilophosaurus**  
Автор: **Marcel Schanz**  
Источник: https://sketchfab.com/3d-models/dilophosaurus-d09b3aa874db4e1cbf29a14797ca351f  
Лицензия: **CC BY 4.0**.  
В приложение загружается проверенная производная из `s010s/prehistoric-animal-museum`, commit `4d824cb1973861c1463b012cb0d6bc5976cf9c1f`. Исходный SHA-256, зафиксированный производным проектом: `c209fee5e214739ee4582bf11ce46aefe47f8030de131cb3c8c63a75cffeeeae`.

## Окружение

Сканированные и фотографические ассеты **Poly Haven**, лицензия **CC0**:

- Fern 02 — `fern_02`
- Dead Tree Trunk — `dead_tree_trunk`
- Tree Stump 01 — `tree_stump_01`
- Rock Moss Set 01 — `rock_moss_set_01`
- Shrub 03 — `shrub_03`
- Mud Forest — `mud_forest`
- Xanderklinge HDRI — `xanderklinge`

Источник: https://polyhaven.com/  
Лицензия: https://polyhaven.com/license

Для видов из более сухих экосистем добавлены синтетические нейтральные фоновые дорожки ветра/равнины, создаваемые локально при сборке. Они не содержат современных голосов животных.

## Звуки

### Реконструкция рыка / низкочастотной вокализации T. rex

Основа — реальные записи американского аллигатора от **U.S. Fish and Wildlife Service**, public domain (PD-USGov-FWS): `Alligatorbellow1.ogg` и `27alligator2bellow.ogg`. Записи сведены, понижены по тону, дополнены низкочастотным слоем и динамически обработаны. Это **художественно-научная реконструкция**, а не запись настоящего тираннозавра: точный голос T. rex неизвестен.

### Окружение Hell Creek

- `20090610 0 ambience.ogg` — forest ambience, public domain.
- `Swale.ogg` — небольшой ручей, CC0 1.0.

Источник: Wikimedia Commons.

## Русская озвучка справок

Все шесть офлайн-дорожек создаются при сборке через **Silero TTS v5.5**, русский мужской голос `eugene`, 48 кГц. Готовые WAV-файлы упаковываются в APK; интернет во время работы приложения не требуется.

## Лицензия Creative Commons

Для всех моделей с пометкой CC BY 4.0 действует Creative Commons Attribution 4.0 International: https://creativecommons.org/licenses/by/4.0/

<!-- build-trigger: package latest locomotion pass -->
