from pathlib import Path
p=Path('skyfrontiers3d/game.js')
s=p.read_text()
s=s.replace('ridgesafe(ridge)','ridge')
if 'ridgesafe(' in s:
    raise SystemExit('bad terrain helper still present')
p.write_text(s)
print('v4 terrain hotfix applied')
