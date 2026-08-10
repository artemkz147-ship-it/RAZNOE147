#!/usr/bin/env python3
from __future__ import annotations

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

config_dir = KOTLIN / "config"
config_dir.mkdir(parents=True, exist_ok=True)
for name in ("AutoTune.kt", "AdaptiveAutoTune.kt"):
    src = HERE / "src" / name
    if not src.exists():
        fail(f"missing overlay source {src}")
    shutil.copy2(src, config_dir / name)

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

print("AutoTune stability overlay applied successfully (ADPF default preserved OFF)")
