#!/usr/bin/env python3
"""Fetch pinned CC BY dinosaur assets and repack them for Godot Mobile."""
from __future__ import annotations

import json
import shutil
import struct
import subprocess
import sys
import urllib.request
from pathlib import Path

MUSEUM_COMMIT = "4d824cb1973861c1463b012cb0d6bc5976cf9c1f"
MUSEUM_BASE = f"https://raw.githubusercontent.com/s010s/prehistoric-animal-museum/{MUSEUM_COMMIT}/src/content/animals"
VELOCIRAPTOR_COMMIT = "d11faeee4fb0e3c24288a018c905f9bf4e4d256e"
VELOCIRAPTOR_BASE = f"https://raw.githubusercontent.com/CarlosHenriqueMkt/portfolio/{VELOCIRAPTOR_COMMIT}/public/velociraptor"
GLTFPACK_VERSION = sys.argv[1] if len(sys.argv) > 1 else "1.2.0"

MUSEUM_SPECIES = ("triceratops", "stegosaurus", "apatosaurus", "dilophosaurus")
VELO_FILES = (
    "scene.gltf",
    "scene.bin",
    "textures/Body_Mat_baseColor.png",
    "textures/Body_Mat_metallicRoughness.png",
    "textures/Body_Mat_normal.png",
    "textures/Body_Mat_specularf0.png",
    "textures/Other_Mat_baseColor.png",
    "textures/Other_Mat_metallicRoughness.png",
    "textures/Other_Mat_normal.png",
)


def download(url: str, dst: Path) -> None:
    dst.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": "DinoEncyclopedia-CI/1.0"})
    with urllib.request.urlopen(request, timeout=120) as response, dst.open("wb") as out:
        shutil.copyfileobj(response, out)
    if dst.stat().st_size < 100:
        raise RuntimeError(f"Downloaded file is unexpectedly small: {dst}")
    print(f"downloaded {dst} ({dst.stat().st_size} bytes)")


def gltfpack(src: Path, dst: Path) -> None:
    dst.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([
        "npx", "--yes", f"gltfpack@{GLTFPACK_VERSION}",
        "-i", str(src), "-o", str(dst), "-noq", "-kn", "-km", "-ke",
    ], check=True)


def inspect_glb(species_id: str, path: Path) -> None:
    with path.open("rb") as f:
        magic, version, total = struct.unpack("<4sII", f.read(12))
        if magic != b"glTF" or version != 2:
            raise RuntimeError(f"{species_id}: invalid GLB")
        chunk_length, chunk_type = struct.unpack("<II", f.read(8))
        if chunk_type != 0x4E4F534A:
            raise RuntimeError(f"{species_id}: GLB JSON chunk missing")
        doc = json.loads(f.read(chunk_length).decode("utf-8"))
    extensions = set(doc.get("extensionsUsed", [])) | set(doc.get("extensionsRequired", []))
    if "KHR_mesh_quantization" in extensions:
        raise RuntimeError(f"{species_id}: KHR_mesh_quantization must not be required")
    if not doc.get("meshes"):
        raise RuntimeError(f"{species_id}: no meshes")
    animations = [(item.get("name") or "").strip() for item in doc.get("animations", [])]
    print(f"SPECIES_MODEL_OK id={species_id} bytes={total} meshes={len(doc['meshes'])} animations={animations} extensions={sorted(extensions)}")


def main() -> None:
    work = Path("/tmp/dino-model-sources")
    shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True)

    for species_id in MUSEUM_SPECIES:
        src = work / f"{species_id}.glb"
        dst = Path("assets/dinosaurs") / species_id / "model.glb"
        download(f"{MUSEUM_BASE}/{species_id}/model/model.glb", src)
        gltfpack(src, dst)
        inspect_glb(species_id, dst)

    velo_dir = work / "velociraptor"
    for rel in VELO_FILES:
        download(f"{VELOCIRAPTOR_BASE}/{rel}", velo_dir / rel)
    velo_dst = Path("assets/dinosaurs/velociraptor/model.glb")
    gltfpack(velo_dir / "scene.gltf", velo_dst)
    inspect_glb("velociraptor", velo_dst)

    print("NEXT_DINOSAUR_MODELS_OK")


if __name__ == "__main__":
    main()
