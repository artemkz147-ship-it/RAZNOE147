from pathlib import Path
import re

exec(Path('wasteland/ci_patch_v162.py').read_text(), {})

game_path = Path('wasteland/scripts/game.gd')
game = game_path.read_text()
game = game.replace(
    'var tunnel_indices: Array[int] = [15, 16, 17] if track_style != 1 else [46, 47, 48]',
    'var tunnel_indices: Array = [15, 16, 17] if track_style != 1 else [46, 47, 48]',
)
game_path.write_text(game)

# Avoid Control warning caused by PRESET_BOTTOM_WIDE + explicit size on the hint.
hud_path = Path('wasteland/scripts/hud.gd')
hud = hud_path.read_text()
old = 'hint.set_anchors_preset(Control.PRESET_BOTTOM_WIDE); hint.position = Vector2(0, -24); hint.size = Vector2(0, 20); hint.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER; hint.modulate.a = 0.45; hint.mouse_filter = Control.MOUSE_FILTER_IGNORE; add_child(hint)'
new = 'hint.set_anchors_preset(Control.PRESET_BOTTOM_LEFT); hint.position = Vector2(0, -24); hint.size = Vector2(1280, 20); hint.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER; hint.modulate.a = 0.45; hint.mouse_filter = Control.MOUSE_FILTER_IGNORE; add_child(hint)'
if old in hud:
    hud = hud.replace(old, new, 1)
hud_path.write_text(hud)

preset = Path('wasteland/export_presets.cfg')
p = preset.read_text()
p = re.sub(r'package/unique_name="[^"]+"', 'package/unique_name="ru.openai148.wastelandcircuit.race163"', p)
p = re.sub(r'version/code=\d+', 'version/code=163', p)
p = re.sub(r'version/name="[^"]+"', 'version/name="1.6.3"', p)
p = re.sub(r'export_path="[^"]+"', 'export_path="builds/Wasteland-Circuit-Race-v1.6.3-fixed.apk"', p)
preset.write_text(p)
