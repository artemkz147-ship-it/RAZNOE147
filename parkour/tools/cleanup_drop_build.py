import os
import shutil

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
legacy = os.path.join(ROOT, 'public', 'assets')

if os.path.isdir(legacy):
    shutil.rmtree(legacy)
    print('Removed legacy runner assets from Drop Flow release build')
