#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
jni_dir = root / "android/app/src/main/jniLibs/arm64-v8a"

removed: list[str] = []
for name in ("libc.so", "libm.so", "libdl.so"):
    path = jni_dir / name
    if path.exists():
        path.unlink()
        removed.append(name)

print("Removed bundled Android system libraries:", ", ".join(removed) if removed else "none")
