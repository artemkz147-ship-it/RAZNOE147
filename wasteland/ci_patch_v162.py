from pathlib import Path
import re

exec(Path('wasteland/ci_patch_v161.py').read_text(), {})

for rel in ['wasteland/scripts/game.gd', 'wasteland/scripts/combat_car.gd']:
    path = Path(rel)
    text = path.read_text()
    text = text.replace('posmodi(', 'posmod(')
    path.write_text(text)

preset = Path('wasteland/export_presets.cfg')
p = preset.read_text()
p = re.sub(r'package/unique_name="[^"]+"', 'package/unique_name="ru.openai148.wastelandcircuit.race162"', p)
p = re.sub(r'version/code=\d+', 'version/code=162', p)
p = re.sub(r'version/name="[^"]+"', 'version/name="1.6.2"', p)
p = re.sub(r'export_path="[^"]+"', 'export_path="builds/Wasteland-Circuit-Race-v1.6.2-fixed.apk"', p)
preset.write_text(p)
