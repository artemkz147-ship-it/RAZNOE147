from pathlib import Path
import re
p=Path('skyfrontiers3d/game.js')
s=p.read_text()
s=s.replace('ridgesafe(ridge)','ridge')
s=re.sub(r'\n?function ridgesafe\(v\)\{return v;\}\n?','\n',s)
if 'ridgesafe' in s:
    raise SystemExit('unexpected ridgesafe remains')
p.write_text(s)
print('v4 terrain hotfix applied')
