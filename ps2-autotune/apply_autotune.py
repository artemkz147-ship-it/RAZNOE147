#!/usr/bin/env python3
from __future__ import annotations

import json
import pathlib
import re
import shutil
import sys

ROOT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "upstream").resolve()
HERE = pathlib.Path(__file__).resolve().parent
ANDROID = ROOT / "platforms/android/app/src/main"
KOTLIN = ANDROID / "java/com/armsx2"


def fail(msg: str) -> None:
    raise SystemExit(f"PS2 AutoTune patch failed: {msg}")


def replace_exact(path: pathlib.Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        fail(f"expected source block not found in {path}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


if not (ROOT / ".git").exists():
    fail(f"{ROOT} is not an ARMSX2 git checkout")
if not KOTLIN.exists():
    fail(f"Android Kotlin source tree missing: {KOTLIN}")

# ---------------------------------------------------------------------------
# 1.0.3 SAFE BOOT
# ---------------------------------------------------------------------------
# Do NOT patch ConfigStore, NativeApp.java, native-lib.cpp, renderer settings,
# affinity, ADPF or the VM run loop in this build. A tester reported that the
# game surface appeared and then the VM exited. Until that device is stable we
# keep the emulation launch path byte-for-byte upstream ARMSX2/PCSX2.
#
# AutoTune sources stay in this repository for later re-introduction, but they
# are intentionally not copied into the Android source tree here.

# Optional private built-in BIOS installer. This is pure Kotlin and performs no
# JNI/native calls before emucore init. Public/source builds contain no firmware
# assets; a private post-build can add assets/builtin_bios/*.bin.
bios_dir = KOTLIN / "bios"
bios_dir.mkdir(parents=True, exist_ok=True)
builtin_bios_src = HERE / "src" / "BuiltinBios.kt"
if not builtin_bios_src.exists():
    fail(f"missing overlay source {builtin_bios_src}")
shutil.copy2(builtin_bios_src, bios_dir / "BuiltinBios.kt")

runtime = KOTLIN / "runtime/MainActivityRuntime.kt"
replace_exact(
    runtime,
    '''        bios.value = prefs.getString("bios", null)
        biosDir.value = prefs.getString("biosDir", null)
''',
    '''        bios.value = prefs.getString("bios", null)
        biosDir.value = prefs.getString("biosDir", null)
        // Safe before emucore init: this installer uses Kotlin file checks only.
        com.armsx2.bios.BuiltinBios.installIfPresent(applicationContext)
''',
)

# One remaining user-facing toast in MainActivityRuntime was still hard-coded in English.
runtime_text = runtime.read_text(encoding="utf-8")
runtime_text = runtime_text.replace(
    '"Turn on Emulate USB Keyboard (Network settings) first"',
    '"Сначала включите «Эмуляция USB-клавиатуры» в настройках сети"',
)
runtime.write_text(runtime_text, encoding="utf-8")

# ---------------------------------------------------------------------------
# Russian-first UI
# ---------------------------------------------------------------------------
i18n = KOTLIN / "i18n/I18n.kt"
replace_exact(i18n, '    var current by mutableStateOf("en")\n', '    var current by mutableStateOf("ru")\n')
replace_exact(i18n, '    var selected by mutableStateOf(SYSTEM_CODE)\n', '    var selected by mutableStateOf("ru")\n')
replace_exact(
    i18n,
    '        val selection = if (saved != null && languages.any { it.code == saved }) saved else SYSTEM_CODE\n',
    '        val selection = if (saved != null && languages.any { it.code == saved }) saved else "ru"\n',
)

ru_path = ANDROID / "assets/i18n/ru.json"
if not ru_path.exists():
    fail(f"Russian translation missing: {ru_path}")
ru = json.loads(ru_path.read_text(encoding="utf-8"))
ru.update({
    "about.tagline": "Быстрый современный эмулятор PlayStation 2 для Android.",
    "about.pcsx2.description": "PS2 AutoTune создан на базе открытого эмулятора PCSX2 и ядра ARMSX2.",
    "about.repository.description": "Исходный код, сборки и список известных проблем.",
    "info.serial": "Серийный номер",
    "info.crc": "CRC",
    "info.cover.label": "Своя обложка",
    "info.setCover": "Установить обложку",
    "info.changeCover": "Сменить обложку",
    "info.removeCover": "Удалить обложку",
    "info.exportSettings": "Экспортировать настройки",
    "info.importSettings": "Импортировать настройки",
    "tab.fixes": "Дополнительно",
    "tab.controls": "Управление",
    "tab.overlay": "Экранное управление",
    "app.language.machineNote": "Интерфейс переведён на русский. Язык можно сменить в любой момент.",
    "app.theme.system": "Как в системе",
    "app.theme.light": "Светлая",
    "app.theme.dark": "Тёмная",
    "app.bootLogo": "Анимация запуска",
    "action.play": "Запустить",
    "action.resume": "Продолжить",
    "action.settings": "Настройки",
    "action.allSettings": "Все настройки",
    "action.reset": "Сбросить",
    "action.apply": "Применить",
    "action.import": "Импортировать",
    "action.importFolder": "Импортировать папку",
    "action.export": "Экспортировать",
    "action.confirm": "Подтвердить",

    # Do not call game images "ROM/ПЗУ" in Russian onboarding. For a normal
    # Android user these are simply PS2 games / game files.
    "setup.page.roms.title": "Выберите папку с играми",
    "setup.button.pickRomsFolder": "Выбрать папку с играми",
    "setup.step.rom.title": "Папки с играми",
    "setup.step.rom.description": "Выберите одну или несколько папок, где хранятся ваши игры для PS2. Поддерживаются ISO, CHD, BIN, IMG, MDF и GZ.",
    "games.card.rescanRomsFolder": "Пересканировать папки с играми",
    "games.empty.noFolders.title": "Папки с играми не выбраны",
    "games.empty.noFolders.body": "Добавьте одну или несколько папок с играми в настройках.",
    "setup.step.appData.description.allFiles": "Здесь хранятся карты памяти, сохранения и настройки эмулятора. Игры добавляются отдельно.",
    "setup.step.appData.description.play": "Здесь хранятся карты памяти, сохранения и настройки эмулятора. Игры добавляются отдельно.",
    "setup.step.bios.title": "BIOS PS2",
    "setup.step.bios.description": "BIOS уже встроен в эту сборку. При необходимости здесь можно выбрать другой BIOS вручную.",
    "setup.page.bios.title": "BIOS PS2",
})
ru_path.write_text(json.dumps(ru, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

# ---------------------------------------------------------------------------
# Clean source-only GPL build fixes / fork branding
# ---------------------------------------------------------------------------
discord_auth = KOTLIN / "discord/DiscordAuthActivity.kt"
replace_exact(
    discord_auth,
    '''        runCatching { com.discord.socialsdk.DiscordSocialSdkInit.setEngineActivity(this) }
            .onFailure { Log.w("ARMSX2DiscordSvc", "setEngineActivity failed: ${it.message}") }
''',
    '''        runCatching {
            val initClass = Class.forName("com.discord.socialsdk.DiscordSocialSdkInit")
            val method = initClass.methods.firstOrNull {
                it.name == "setEngineActivity" && it.parameterCount == 1
            } ?: error("Discord setEngineActivity is unavailable")
            method.invoke(null, this)
        }.onFailure { Log.w("ARMSX2DiscordSvc", "setEngineActivity failed: ${it.message}") }
''',
)

build_gradle = ROOT / "platforms/android/app/build.gradle.kts"
replace_exact(
    build_gradle,
    '            buildConfigField("boolean", "IN_APP_UPDATER", "true")\n',
    '            buildConfigField("boolean", "IN_APP_UPDATER", "false")\n',
)

strings = ANDROID / "res/values/strings.xml"
if strings.exists():
    text = strings.read_text(encoding="utf-8")
    text2, count = re.subn(
        r'(<string\s+name="app_name"[^>]*>)(.*?)(</string>)',
        r'\1PS2 AutoTune\3',
        text,
        count=1,
        flags=re.DOTALL,
    )
    if count:
        strings.write_text(text2, encoding="utf-8")

print("PS2 AutoTune 1.0.3 SAFE BOOT overlay applied: upstream VM path, Russian UI, built-in BIOS installer")
