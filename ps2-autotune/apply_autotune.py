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
    raise SystemExit(f"AutoTune patch failed: {msg}")


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
# PS2 AutoTune sources
# ---------------------------------------------------------------------------
config_dir = KOTLIN / "config"
config_dir.mkdir(parents=True, exist_ok=True)
for name in ("AutoTune.kt", "AdaptiveAutoTune.kt"):
    src = HERE / "src" / name
    if not src.exists():
        fail(f"missing overlay source {src}")
    shutil.copy2(src, config_dir / name)

bios_dir = KOTLIN / "bios"
bios_dir.mkdir(parents=True, exist_ok=True)
builtin_bios_src = HERE / "src" / "BuiltinBios.kt"
if not builtin_bios_src.exists():
    fail(f"missing overlay source {builtin_bios_src}")
shutil.copy2(builtin_bios_src, bios_dir / "BuiltinBios.kt")

# Keep upstream/GameDB launch configuration intact. AutoTune.resolve() in the
# stability build returns the base settings unchanged; per-game overrides still
# remain the highest-priority layer exactly as upstream expects.
config_store = config_dir / "ConfigStore.kt"
old_resolve = '''    fun resolveForGame(serial: String?): Settings {
        val global = loadGlobal()
        if (serial == null) return global
        val overrides = loadOverrides(serial) ?: return global
        return Settings.merge(global, overrides)
    }
'''
new_resolve = '''    fun resolveForGame(serial: String?): Settings {
        val global = loadGlobal()
        val context = MainActivityRuntime.instance?.applicationContext
        val automatic = if (context != null) AutoTune.resolve(context, serial, global) else global
        if (serial == null) return automatic
        val overrides = loadOverrides(serial) ?: return automatic
        return Settings.merge(automatic, overrides)
    }
'''
replace_exact(config_store, old_resolve, new_resolve)

# Expose native performance metrics. They are sampled only after the game has
# been running for a while; no experimental scheduler/core switches are enabled
# during VM startup in this stability build.
native_java = ANDROID / "java/kr/co/iefriends/pcsx2/NativeApp.java"
replace_exact(
    native_java,
    "\tpublic static native float getFPS();\n",
    "\tpublic static native float getFPS();\n"
    "\tpublic static native float getEmulationSpeed();\n"
    "\tpublic static native float getCpuThreadTimeMs();\n"
    "\tpublic static native float getGsThreadTimeMs();\n"
    "\tpublic static native float getVuThreadTimeMs();\n"
    "\tpublic static native float getGpuTimeMs();\n",
)

native_cpp = ANDROID / "cpp/native-lib.cpp"
old_speed_jni = '''extern "C"
JNIEXPORT jfloat JNICALL
Java_kr_co_iefriends_pcsx2_NativeApp_getFPS(JNIEnv *env, jclass clazz) {
    return (jfloat)PerformanceMetrics::GetFPS();
}
'''
new_speed_jni = old_speed_jni + '''
extern "C"
JNIEXPORT jfloat JNICALL
Java_kr_co_iefriends_pcsx2_NativeApp_getEmulationSpeed(JNIEnv*, jclass) {
    return (jfloat)PerformanceMetrics::GetSpeed();
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_kr_co_iefriends_pcsx2_NativeApp_getCpuThreadTimeMs(JNIEnv*, jclass) {
    return (jfloat)PerformanceMetrics::GetCPUThreadAverageTime();
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_kr_co_iefriends_pcsx2_NativeApp_getGsThreadTimeMs(JNIEnv*, jclass) {
    return (jfloat)PerformanceMetrics::GetGSThreadAverageTime();
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_kr_co_iefriends_pcsx2_NativeApp_getVuThreadTimeMs(JNIEnv*, jclass) {
    return (jfloat)PerformanceMetrics::GetVUThreadAverageTime();
}

extern "C"
JNIEXPORT jfloat JNICALL
Java_kr_co_iefriends_pcsx2_NativeApp_getGpuTimeMs(JNIEnv*, jclass) {
    return (jfloat)PerformanceMetrics::GetGPUAverageTime();
}
'''
replace_exact(native_cpp, old_speed_jni, new_speed_jni)

# IMPORTANT stability hotfix: leave ARMSX2's experimental ADPF preference at
# its upstream default OFF. It can be re-enabled later after device testing.

runtime = KOTLIN / "runtime/MainActivityRuntime.kt"
replace_exact(
    runtime,
    "                NativeApp.runVMThread(m_szGamefile)\n",
    '''                com.armsx2.config.AdaptiveAutoTune.start(currentGame.value?.settingsKey, bootCfg)
                try {
                    NativeApp.runVMThread(m_szGamefile)
                } finally {
                    com.armsx2.config.AdaptiveAutoTune.stop()
                }
''',
)

# Optional private built-in BIOS package. The public/source build has no firmware
# assets, so this is a no-op there. A post-build APK may contain assets/builtin_bios/*.bin;
# on startup each candidate is copied to app-private storage and validated by emucore
# before one is selected. This avoids ever selecting a truncated/bad BIOS.
replace_exact(
    runtime,
    '''        bios.value = prefs.getString("bios", null)
        biosDir.value = prefs.getString("biosDir", null)
''',
    '''        bios.value = prefs.getString("bios", null)
        biosDir.value = prefs.getString("biosDir", null)
        com.armsx2.bios.BuiltinBios.installIfPresent(applicationContext)
''',
)

# One remaining user-facing toast in MainActivityRuntime was still hard-coded in English.
# Translate it directly so the Russian-default build does not leak English here.
runtime_text = runtime.read_text(encoding="utf-8")
runtime_text = runtime_text.replace(
    '"Turn on Emulate USB Keyboard (Network settings) first"',
    '"Сначала включите «Эмуляция USB-клавиатуры» в настройках сети"',
)
runtime.write_text(runtime_text, encoding="utf-8")

# ---------------------------------------------------------------------------
# Russian-first UI
# ---------------------------------------------------------------------------
# ARMSX2 already ships a full live i18n system and assets/i18n/ru.json. Make
# Russian the first-run/default language for PS2 AutoTune while preserving the
# language picker, so a user can still choose another language later.
i18n = KOTLIN / "i18n/I18n.kt"
replace_exact(i18n, '    var current by mutableStateOf("en")\n', '    var current by mutableStateOf("ru")\n')
replace_exact(i18n, '    var selected by mutableStateOf(SYSTEM_CODE)\n', '    var selected by mutableStateOf("ru")\n')
replace_exact(
    i18n,
    '        val selection = if (saved != null && languages.any { it.code == saved }) saved else SYSTEM_CODE\n',
    '        val selection = if (saved != null && languages.any { it.code == saved }) saved else "ru"\n',
)

# Polish the most visible machine-translated Russian strings. Keep upstream's
# complete key set and only replace values, so future/new UI keys still fall back
# through the normal I18n mechanism instead of disappearing.
ru_path = ANDROID / "assets/i18n/ru.json"
if not ru_path.exists():
    fail(f"Russian translation missing: {ru_path}")
ru = json.loads(ru_path.read_text(encoding="utf-8"))
ru.update({
    "about.tagline": "Быстрый современный эмулятор PlayStation 2 для Android с автоматической настройкой.",
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
})
ru_path.write_text(json.dumps(ru, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

# ---------------------------------------------------------------------------
# Source-only GPL build fixes / branding
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

print("AutoTune RU stability overlay applied (Russian default, optional validated built-in BIOS, ADPF OFF)")
