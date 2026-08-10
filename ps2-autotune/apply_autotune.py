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

# Add our two source files without forking/copying the huge upstream tree into the
# user's repository. The workflow records the exact upstream tag + our patch commit.
config_dir = KOTLIN / "config"
config_dir.mkdir(parents=True, exist_ok=True)
for name in ("AutoTune.kt", "AdaptiveAutoTune.kt"):
    src = HERE / "src" / name
    if not src.exists():
        fail(f"missing overlay source {src}")
    shutil.copy2(src, config_dir / name)

# Insert AutoTune between global defaults and sparse user per-game overrides.
# Explicit per-game values therefore remain the final/highest-priority layer.
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

# PCSX2 PerformanceMetrics::GetFPS() is rendered game FPS. Many perfectly full-speed
# PS2 games intentionally render at 30 FPS on a ~60 Hz virtual console, so FPS/refresh
# is NOT a valid performance signal. Expose GetSpeed() (100 = native emulation speed)
# through one tiny JNI call and make the adaptive loop use that instead.
native_java = ANDROID / "java/kr/co/iefriends/pcsx2/NativeApp.java"
replace_exact(
    native_java,
    "\tpublic static native float getFPS();\n",
    "\tpublic static native float getFPS();\n\tpublic static native float getEmulationSpeed();\n",
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
'''
replace_exact(native_cpp, old_speed_jni, new_speed_jni)

# Start the adaptive learner on a different coroutine pool before the blocking VM
# loop; always cancel it when the VM exits/crashes back to the library.
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

# The sideload flavour normally contains ARMSX2's own GitHub updater. Our APK has a
# different applicationId, so an official ARMSX2 APK cannot update it. Disable that
# updater rather than offering an update which Android would reject as another app.
build_gradle = ROOT / "platforms/android/app/build.gradle.kts"
replace_exact(
    build_gradle,
    '            buildConfigField("boolean", "IN_APP_UPDATER", "true")\n',
    '            buildConfigField("boolean", "IN_APP_UPDATER", "false")\n',
)

# Visible fork branding only. Java/Kotlin package + JNI class identifiers intentionally
# stay unchanged, minimizing compatibility risk with the large native core. The Gradle
# workflow gives the APK a separate applicationId so it installs beside official ARMSX2.
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

print("AutoTune overlay applied successfully")
