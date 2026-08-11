#!/usr/bin/env python3
from __future__ import annotations

import pathlib
import re
import shutil
import sys

ROOT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "upstream").resolve()
PROJECT = ROOT / "ARMSX2"
APP = PROJECT / "app"
MAIN = APP / "src/main/java/com/armsx2/Main.kt"
JAVA_ROOT = APP / "src/main/java"
RES = APP / "src/main/res"

if not APP.exists() or not MAIN.exists():
    raise SystemExit(f"ARMSX2 2.4.8 Android tree not found under {ROOT}")

# -----------------------------------------------------------------------------
# Built-in BIOS compatibility helper. Firmware bytes are never committed; a
# private post-build can place user-supplied files into assets/builtin_bios/.
# -----------------------------------------------------------------------------
bios_helper = JAVA_ROOT / "com/armsx2/BuiltinBiosCompat.kt"
bios_helper.write_text(r'''package com.armsx2

import android.content.Context
import java.io.File

object BuiltinBiosCompat {
    private const val BIOS_SIZE = 4L * 1024L * 1024L
    private const val ASSET_DIR = "builtin_bios"

    fun install(context: Context): String? {
        val existing = Main.bios.value?.let(::File)
        if (existing?.isFile == true && existing.length() == BIOS_SIZE) return existing.absolutePath

        val names = runCatching { context.assets.list(ASSET_DIR)?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.endsWith(".bin", true) || it.endsWith(".rom", true) }
        if (names.isEmpty()) return null

        val ordered = names.sortedBy {
            when {
                it.contains("Europe", true) || it.contains("SCPH50003", true) -> 0
                it.contains("USA", true) -> 1
                it.contains("Japan", true) || it.contains("SCPH10000", true) -> 2
                else -> 3
            }
        }
        val dir = Main.internalBiosDir(context).apply { mkdirs() }
        for (name in ordered) {
            val dst = File(dir, "builtin_${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
            val ok = runCatching {
                if (!dst.isFile || dst.length() != BIOS_SIZE) {
                    context.assets.open("$ASSET_DIR/$name").use { input ->
                        val tmp = File(dir, ".${dst.name}.tmp")
                        tmp.outputStream().buffered().use { output -> input.copyTo(output) }
                        if (tmp.length() != BIOS_SIZE) { tmp.delete(); return@runCatching false }
                        if (dst.exists()) dst.delete()
                        if (!tmp.renameTo(dst)) {
                            tmp.copyTo(dst, overwrite = true)
                            tmp.delete()
                        }
                    }
                }
                dst.isFile && dst.length() == BIOS_SIZE
            }.getOrDefault(false)
            if (!ok) continue

            // Conservative pure-Kotlin sanity check. PCSX2 still does its own full
            // BIOS validation when the VM starts.
            val sane = runCatching {
                val probe = ByteArray(256 * 1024)
                val n = dst.inputStream().buffered().use { it.read(probe) }
                n > 0 && probe.copyOf(n).toString(Charsets.ISO_8859_1).contains("ROMVER")
            }.getOrDefault(false)
            if (!sane) { runCatching { dst.delete() }; continue }

            Main.bios.value = dst.absolutePath
            Main.prefs.edit().putString("bios", dst.absolutePath).apply()
            return dst.absolutePath
        }
        return null
    }
}
''', encoding="utf-8")

# -----------------------------------------------------------------------------
# Native crash diagnostics. A native SIGSEGV kills the process, so we persist a
# marker before runVMThread and inspect ApplicationExitInfo on the next launch.
# The report is copied to Downloads/PS2AutoTune on Android 10+.
# -----------------------------------------------------------------------------
diag = JAVA_ROOT / "com/armsx2/CrashDiagnostics.kt"
diag.write_text(r'''package com.armsx2

import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import java.io.File

object CrashDiagnostics {
    private const val KEY_RUNNING = "diag.vm.running"
    private const val KEY_GAME = "diag.vm.game"
    private const val KEY_TIME = "diag.vm.time"

    fun onAppStart(context: Context) {
        if (!Main.prefs.getBoolean(KEY_RUNNING, false)) return
        val report = buildReport(context)
        Main.prefs.edit().putBoolean(KEY_RUNNING, false).apply()
        val local = File(context.getExternalFilesDir(null) ?: context.filesDir, "PS2AutoTune-crash-report.txt")
        runCatching { local.writeText(report) }
        var exported = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exported = runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "PS2AutoTune-crash-report.txt")
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/PS2AutoTune")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching false
                context.contentResolver.openOutputStream(uri, "w")!!.bufferedWriter().use { it.write(report) }
                true
            }.getOrDefault(false)
        }
        Toast.makeText(
            context,
            if (exported) "Сохранён отчёт о сбое: Загрузки/PS2AutoTune" else "Обнаружен сбой эмулятора; отчёт сохранён приложением",
            Toast.LENGTH_LONG
        ).show()
    }

    fun beforeVmStart(game: String) {
        Main.prefs.edit()
            .putBoolean(KEY_RUNNING, true)
            .putString(KEY_GAME, game)
            .putLong(KEY_TIME, System.currentTimeMillis())
            .apply()
    }

    fun afterVmReturn() {
        Main.prefs.edit().putBoolean(KEY_RUNNING, false).apply()
    }

    private fun buildReport(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("PS2 AutoTune Exynos-safe crash report")
        sb.appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
        sb.appendLine("abi=${Build.SUPPORTED_ABIS.joinToString()}")
        sb.appendLine("game=${Main.prefs.getString(KEY_GAME, "")}")
        sb.appendLine("vm_start=${Main.prefs.getLong(KEY_TIME, 0L)}")
        sb.appendLine("bios=${Main.bios.value}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val exits = am.getHistoricalProcessExitReasons(context.packageName, 0, 5)
                exits.forEachIndexed { i, e ->
                    sb.appendLine("exit[$i].reason=${e.reason} status=${e.status} importance=${e.importance} time=${e.timestamp}")
                    sb.appendLine("exit[$i].description=${e.description}")
                    runCatching {
                        e.traceInputStream?.bufferedReader()?.useLines { lines ->
                            lines.take(200).forEach { sb.appendLine("trace: $it") }
                        }
                    }
                }
            }
        }
        return sb.toString()
    }
}
''', encoding="utf-8")

# Install bundled BIOS and inspect any previous native crash after prefs are ready.
main = MAIN.read_text(encoding="utf-8")
needle = '''        bios.value = prefs.getString("bios", null)\n        biosDir.value = prefs.getString("biosDir", null)\n'''
if needle not in main:
    raise SystemExit("Main.kt BIOS preference block not found")
main = main.replace(needle, needle + '''        BuiltinBiosCompat.install(applicationContext)\n        CrashDiagnostics.onAppStart(applicationContext)\n''', 1)

# Wrap both game and BIOS VM starts. If native kills the process, afterVmReturn
# never executes and the marker is recovered on next launch.
main = main.replace(
    '                    NativeApp.runVMThread(m_szGamefile)\n',
    '''                    CrashDiagnostics.beforeVmStart(m_szGamefile)\n                    try {\n                        NativeApp.runVMThread(m_szGamefile)\n                    } finally {\n                        CrashDiagnostics.afterVmReturn()\n                    }\n'''
)
MAIN.write_text(main, encoding="utf-8")

# Branding.
strings = RES / "values/strings.xml"
if strings.exists():
    s = strings.read_text(encoding="utf-8")
    s = re.sub(r'(<string\\s+name="app_name"[^>]*>).*?(</string>)', r'\\1PS2 AutoTune Exynos Safe\\2', s, count=1, flags=re.S)
    strings.write_text(s, encoding="utf-8")

# Main Russian pass for V29, which predates the later JSON i18n layer. We keep
# technical API names (Vulkan/OpenGL/CRC/etc.) intact and translate user-facing
# setup/library/common labels. This is deliberately literal so it cannot alter
# identifiers or emulator logic.
translations = {
    'Select ROMs folder': 'Выберите папку с играми',
    'Select ROMs Folder': 'Выберите папку с играми',
    'Pick ROMs Folder': 'Выбрать папку с играми',
    'ROM Location': 'Папки с играми',
    'ROMs folder': 'Папка с играми',
    'ROMs Folder': 'Папка с играми',
    'Re-scan ROMs folder': 'Пересканировать папку с играми',
    'No ROMs folders configured': 'Папки с играми не выбраны',
    'Select your BIOS': 'BIOS PS2',
    'BIOS Location': 'BIOS PS2',
    'Pick BIOS Folder': 'Выбрать папку BIOS',
    'Welcome': 'Добро пожаловать',
    'Next': 'Далее',
    'Back': 'Назад',
    'Cancel': 'Отмена',
    'Confirm': 'Подтвердить',
    'Save': 'Сохранить',
    'Delete': 'Удалить',
    'Reset': 'Сбросить',
    'Apply': 'Применить',
    'Settings': 'Настройки',
    'All Settings': 'Все настройки',
    'Play': 'Запустить',
    'Resume': 'Продолжить',
    'Performance': 'Производительность',
    'Renderer': 'Рендерер',
    'Controls': 'Управление',
    'On-Screen Controls': 'Экранное управление',
    'Audio': 'Звук',
    'Network': 'Сеть',
    'Patches': 'Патчи',
    'Advanced': 'Дополнительно',
    'Language': 'Язык',
    'Internal storage': 'Внутренняя память',
    'Internal Storage': 'Внутренняя память',
    'Custom Folder': 'Своя папка',
    'Select Folder': 'Выбрать папку',
    'Scanning...': 'Сканирование...',
    'Not selected': 'Не выбрано',
    'BIOS selected': 'BIOS выбран',
    'Choose renderer': 'Выберите рендерер',
    'System data folder': 'Папка данных эмулятора',
    'App Data Folder': 'Папка данных эмулятора',
    'Exit': 'Выход',
    'Search': 'Поиск',
    'Refresh': 'Обновить',
    'Import': 'Импортировать',
    'Export': 'Экспортировать',
    'Close': 'Закрыть',
    'Yes': 'Да',
    'No': 'Нет',
    'On': 'Вкл.',
    'Off': 'Выкл.',
}
for path in JAVA_ROOT.rglob('*.kt'):
    text = path.read_text(encoding='utf-8')
    old = text
    for en, ru in translations.items():
        text = text.replace(f'"{en}"', f'"{ru}"')
    if text != old:
        path.write_text(text, encoding='utf-8')

print('Applied PS2 AutoTune Exynos-safe V29 overlay')
