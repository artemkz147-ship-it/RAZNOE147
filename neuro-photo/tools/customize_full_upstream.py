#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def replace_required(path: Path, old: str, new: str, count: int = -1) -> None:
    text = read(path)
    if old not in text:
        raise RuntimeError(f"Expected text not found in {path}: {old[:120]!r}")
    write(path, text.replace(old, new, count))


def add_import(path: Path) -> None:
    text = read(path)
    line = "import 'bundled_model.dart';\n"
    if line in text:
        return
    marker = "import 'ffi_bindings.dart';\n"
    if marker not in text:
        raise RuntimeError(f"FFI import anchor missing in {path}")
    write(path, text.replace(marker, marker + line, 1))


# ---------------------------------------------------------------------------
# Android: bundled 1.57 GB GGUF is copied into app-private files on first use.
# No rootBundle.load(): that would attempt to materialize the whole model in RAM.
# ---------------------------------------------------------------------------

manifest = ROOT / "android/app/src/main/AndroidManifest.xml"
manifest_text = read(manifest)
for permission in (
    '    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />\n',
    '    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />\n',
    '    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>\n',
):
    manifest_text = manifest_text.replace(permission, "")
write(manifest, manifest_text)

build_gradle = ROOT / "android/app/build.gradle"
gradle_text = read(build_gradle)
if "noCompress 'gguf'" not in gradle_text:
    gradle_text = gradle_text.replace(
        "android {\n",
        "android {\n    // Keep the bundled GGUF byte-for-byte and avoid expensive recompression.\n"
        "    aaptOptions {\n        noCompress 'gguf'\n    }\n\n",
        1,
    )
write(build_gradle, gradle_text)

main_activity = ROOT / "android/app/src/main/kotlin/com/example/local_diffusion/MainActivity.kt"
write(
    main_activity,
    r'''package com.artem147.neurophoto

import android.os.Bundle
import android.os.StatFs
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MainActivity : FlutterActivity() {
    private val modelChannel = "com.artem147.neurophoto/models"
    private val ioExecutor = Executors.newSingleThreadExecutor()

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, modelChannel)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "ensureBundledSd15" -> ioExecutor.execute {
                        try {
                            val path = ensureBundledSd15()
                            runOnUiThread { result.success(path) }
                        } catch (t: Throwable) {
                            runOnUiThread {
                                result.error(
                                    "MODEL_PREPARE_FAILED",
                                    t.message ?: "Не удалось подготовить встроенную нейросеть",
                                    null
                                )
                            }
                        }
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun ensureBundledSd15(): String {
        val modelDir = File(filesDir, "models")
        if (!modelDir.exists() && !modelDir.mkdirs()) {
            error("Не удалось создать папку для нейросети")
        }

        val model = File(modelDir, "neurophoto-sd15-q4.gguf")
        val expectedBytes = 1_566_768_416L
        if (model.exists() && model.length() == expectedBytes) {
            return model.absolutePath
        }

        if (model.exists()) model.delete()

        val available = StatFs(filesDir.absolutePath).availableBytes
        val reserve = 256L * 1024L * 1024L
        if (available < expectedBytes + reserve) {
            val needGb = (expectedBytes + reserve) / 1_073_741_824.0
            val freeGb = available / 1_073_741_824.0
            error(
                "Для подготовки нейросети нужно около %.1f ГБ свободного места, доступно %.1f ГБ"
                    .format(needGb, freeGb)
            )
        }

        val temp = File(modelDir, "neurophoto-sd15-q4.gguf.part")
        if (temp.exists()) temp.delete()

        assets.open("models/neurophoto-sd15-q4.gguf").use { input ->
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(8 * 1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }

        if (temp.length() != expectedBytes) {
            val actual = temp.length()
            temp.delete()
            error("Встроенная нейросеть повреждена: $actual байт вместо $expectedBytes")
        }

        if (!temp.renameTo(model)) {
            temp.copyTo(model, overwrite = true)
            temp.delete()
        }

        if (model.length() != expectedBytes) {
            model.delete()
            error("Не удалось сохранить встроенную нейросеть")
        }
        return model.absolutePath
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }
}
''',
)

# Dart bridge for the native asset-copy operation.
write(
    ROOT / "lib/bundled_model.dart",
    r'''import 'package:flutter/services.dart';

class BundledModel {
  static const MethodChannel _channel =
      MethodChannel('com.artem147.neurophoto/models');

  static Future<String> ensureSd15() async {
    final path = await _channel.invokeMethod<String>('ensureBundledSd15');
    if (path == null || path.isEmpty) {
      throw StateError('Android не вернул путь к встроенной нейросети');
    }
    return path;
  }
}
''',
)

# Full build opens directly on photo-to-photo editing; no all-files permission gate.
main_dart = ROOT / "lib/main.dart"
main_text = read(main_dart)
home_pattern = re.compile(
    r"\s+home: _isLoading.*?_requestManageStoragePermission\), // Pass the request function",
    re.DOTALL,
)
main_text, replaced = home_pattern.subn("\n      home: const Img2ImgPage(),", main_text, count=1)
if replaced != 1:
    raise RuntimeError("Could not replace MyApp permission-gated home screen")
write(main_dart, main_text)

# Pages that can work with the bundled SD1.5 checkpoint without extra AI weights.
core_pages = [
    ROOT / "lib/main.dart",
    ROOT / "lib/img2img_page.dart",
    ROOT / "lib/inpainting_page.dart",
    ROOT / "lib/outpainting_page.dart",
]

loader_method = r'''
  Future<void> _loadBundledModel() async {
    if (isModelLoading || isGenerating) return;
    setState(() {
      isModelLoading = true;
      loadingText = 'Подготовка встроенной нейросети';
      _loadingError = '';
      useVAETiling = true;
    });
    try {
      final modelPath = await BundledModel.ensureSd15();
      if (!mounted) return;
      _initializeProcessor(
        modelPath,
        true,
        SDType.SD_TYPE_Q4_0,
        Schedule.DEFAULT,
      );
    } catch (e) {
      if (!mounted) return;
      setState(() {
        isModelLoading = false;
        loadingText = '';
        _loadingError = 'Не удалось подготовить встроенную нейросеть: $e';
      });
    }
  }

'''

for page in core_pages:
    add_import(page)
    text = read(page)
    anchor = "  void showModelLoadDialog() {\n"
    if anchor not in text:
        raise RuntimeError(f"Model dialog anchor missing in {page}")
    if "Future<void> _loadBundledModel()" not in text:
        text = text.replace(anchor, loader_method + anchor, 1)

    # Put the one-tap bundled model button before the manual model picker.
    manual = """                ShadButton(\n                  enabled: !(isModelLoading || isGenerating),\n                  onPressed: showModelLoadDialog,\n                  child: const Text('Загрузить модель'),\n                ),\n                const SizedBox(width: 8),\n"""
    bundled = """                ShadButton(\n                  enabled: !(isModelLoading || isGenerating),\n                  onPressed: _loadBundledModel,\n                  child: const Text('Встроенная нейросеть'),\n                ),\n                const SizedBox(width: 8),\n""" + manual
    if manual not in text:
        raise RuntimeError(f"Manual model button anchor missing in {page}")
    if "child: const Text('Встроенная нейросеть')" not in text:
        text = text.replace(manual, bundled, 1)
    write(page, text)

# On the primary editor, prepare the built-in model automatically after first frame.
img2img = ROOT / "lib/img2img_page.dart"
img2img_text = read(img2img)
init_anchor = """    _cannyProcessor!.imageStream.listen((image) async {\n      final bytes = await image.toByteData(format: ui.ImageByteFormat.png);\n\n      setState(() {\n        _cannyImage = Image.memory(bytes!.buffer.asUint8List());\n      });\n    });\n  }\n"""
init_replacement = """    _cannyProcessor!.imageStream.listen((image) async {\n      final bytes = await image.toByteData(format: ui.ImageByteFormat.png);\n\n      setState(() {\n        _cannyImage = Image.memory(bytes!.buffer.asUint8List());\n      });\n    });\n\n    WidgetsBinding.instance.addPostFrameCallback((_) {\n      if (mounted) _loadBundledModel();\n    });\n  }\n"""
if init_anchor not in img2img_text:
    raise RuntimeError("Img2Img initState anchor missing")
img2img_text = img2img_text.replace(init_anchor, init_replacement, 1)

# Presets are intentionally English prompts: SD1.5 understands them much better,
# while the user-facing labels remain Russian.
style_method = r'''
  void _applyStylePreset(String stylePrompt) {
    final current = _promptController.text.trim();
    final value = current.isEmpty ? stylePrompt : '$current, $stylePrompt';
    setState(() {
      prompt = value;
      _promptController.text = value;
      _promptController.selection = TextSelection.collapsed(offset: value.length);
    });
  }

'''
model_dialog_anchor = "  void showModelLoadDialog() {\n"
if "void _applyStylePreset" not in img2img_text:
    img2img_text = img2img_text.replace(
        model_dialog_anchor, style_method + model_dialog_anchor, 1
    )

prompt_anchor = """            ShadInput(\n              key: _promptFieldKey,\n              placeholder: const Text('Что изменить'),\n              controller: _promptController,\n"""
style_ui = r'''            Text(
              'Быстрые стили',
              style: theme.textTheme.h4.copyWith(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                ActionChip(
                  label: const Text('Кино'),
                  onPressed: () => _applyStylePreset(
                    'cinematic photography, dramatic lighting, film still, high detail, natural skin texture',
                  ),
                ),
                ActionChip(
                  label: const Text('Фотореализм'),
                  onPressed: () => _applyStylePreset(
                    'photorealistic, professional photography, realistic skin, natural light, highly detailed',
                  ),
                ),
                ActionChip(
                  label: const Text('Аниме'),
                  onPressed: () => _applyStylePreset(
                    'high quality anime illustration, clean line art, detailed eyes, vibrant shading',
                  ),
                ),
                ActionChip(
                  label: const Text('Киберпанк'),
                  onPressed: () => _applyStylePreset(
                    'cyberpunk, neon city lights, futuristic atmosphere, cinematic contrast, intricate details',
                  ),
                ),
                ActionChip(
                  label: const Text('Фэнтези'),
                  onPressed: () => _applyStylePreset(
                    'epic fantasy art, magical atmosphere, ornate details, cinematic lighting, concept art',
                  ),
                ),
                ActionChip(
                  label: const Text('Комикс'),
                  onPressed: () => _applyStylePreset(
                    'graphic novel comic art, bold ink lines, dynamic shading, detailed illustration',
                  ),
                ),
                ActionChip(
                  label: const Text('Акварель'),
                  onPressed: () => _applyStylePreset(
                    'watercolor painting, delicate pigments, textured paper, artistic brushwork',
                  ),
                ),
                ActionChip(
                  label: const Text('Масло'),
                  onPressed: () => _applyStylePreset(
                    'classical oil painting, rich brush strokes, museum quality, dramatic light',
                  ),
                ),
                ActionChip(
                  label: const Text('3D'),
                  onPressed: () => _applyStylePreset(
                    'high-end 3d render, soft global illumination, detailed materials, cinematic composition',
                  ),
                ),
                ActionChip(
                  label: const Text('Нуар'),
                  onPressed: () => _applyStylePreset(
                    'film noir, black and white, hard shadows, moody cinematic photography',
                  ),
                ),
                ActionChip(
                  label: const Text('Винтаж'),
                  onPressed: () => _applyStylePreset(
                    'vintage analog photography, 35mm film grain, warm faded colors, authentic retro look',
                  ),
                ),
                ActionChip(
                  label: const Text('Пластилин'),
                  onPressed: () => _applyStylePreset(
                    'claymation style, handcrafted plasticine, miniature set, soft studio lighting',
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
'''
if prompt_anchor not in img2img_text:
    raise RuntimeError("Translated Img2Img prompt anchor missing")
if "'Быстрые стили'" not in img2img_text:
    img2img_text = img2img_text.replace(prompt_anchor, style_ui + prompt_anchor, 1)
write(img2img, img2img_text)

write(ROOT / ".neurophoto-full-customized", "NeuroPhoto Full customization applied\n")
print("NeuroPhoto Full customization applied successfully")
