#!/usr/bin/env python3
"""Fetch dinosaur assets and prefer candidates with real skeletal animation."""
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
AR_WORLD_COMMIT = "ee6b4553b38f2d0780a1e89d940b46869f215720"
AR_WORLD_BASE = f"https://raw.githubusercontent.com/srirammv04/AR_World/{AR_WORLD_COMMIT}/3Dmodels/Dinosaurs"
VELOCIRAPTOR_COMMIT = "d11faeee4fb0e3c24288a018c905f9bf4e4d256e"
VELOCIRAPTOR_BASE = f"https://raw.githubusercontent.com/CarlosHenriqueMkt/portfolio/{VELOCIRAPTOR_COMMIT}/public/velociraptor"
GLTFPACK_VERSION = sys.argv[1] if len(sys.argv) > 1 else "1.2.0"

SPECIES_FILES = {
    "triceratops": "Triceratops.glb",
    "stegosaurus": "Stegosaurus.glb",
    "apatosaurus": "Apatosaurus.glb",
    "dilophosaurus": "Dilophosaurus.glb",
}
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
    with urllib.request.urlopen(request, timeout=180) as response, dst.open("wb") as out:
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


def _read_doc(path: Path) -> tuple[dict, int]:
    with path.open("rb") as f:
        magic, version, total = struct.unpack("<4sII", f.read(12))
        if magic != b"glTF" or version != 2:
            raise RuntimeError(f"invalid GLB: {path}")
        chunk_length, chunk_type = struct.unpack("<II", f.read(8))
        if chunk_type != 0x4E4F534A:
            raise RuntimeError(f"GLB JSON chunk missing: {path}")
        doc = json.loads(f.read(chunk_length).decode("utf-8"))
    return doc, total


def _accessor_bounds(doc: dict) -> tuple[list[float] | None, list[float] | None]:
    mins: list[float] | None = None
    maxs: list[float] | None = None
    accessors = doc.get("accessors", [])
    for mesh in doc.get("meshes", []):
        for primitive in mesh.get("primitives", []):
            pos_index = primitive.get("attributes", {}).get("POSITION")
            if pos_index is None or pos_index >= len(accessors):
                continue
            accessor = accessors[pos_index]
            amin, amax = accessor.get("min"), accessor.get("max")
            if not (isinstance(amin, list) and isinstance(amax, list) and len(amin) >= 3 and len(amax) >= 3):
                continue
            if mins is None:
                mins = [float(amin[i]) for i in range(3)]
                maxs = [float(amax[i]) for i in range(3)]
            else:
                assert maxs is not None
                for i in range(3):
                    mins[i] = min(mins[i], float(amin[i]))
                    maxs[i] = max(maxs[i], float(amax[i]))
    return mins, maxs


def inspect_glb(species_id: str, path: Path, label: str = "selected") -> dict:
    doc, total = _read_doc(path)
    extensions = set(doc.get("extensionsUsed", [])) | set(doc.get("extensionsRequired", []))
    if "KHR_mesh_quantization" in extensions:
        raise RuntimeError(f"{species_id}: KHR_mesh_quantization must not be required")
    if not doc.get("meshes"):
        raise RuntimeError(f"{species_id}: no meshes")
    animations = [(item.get("name") or "").strip() for item in doc.get("animations", [])]
    node_names = [(node.get("name") or f"node_{i}") for i, node in enumerate(doc.get("nodes", []))]
    skin_joints: list[str] = []
    for skin in doc.get("skins", []):
        for joint_index in skin.get("joints", []):
            if isinstance(joint_index, int) and 0 <= joint_index < len(node_names):
                name = node_names[joint_index]
                if name not in skin_joints:
                    skin_joints.append(name)
    mins, maxs = _accessor_bounds(doc)
    size = None
    if mins is not None and maxs is not None:
        size = [round(maxs[i] - mins[i], 5) for i in range(3)]
    animated_targets: list[str] = []
    for anim in doc.get("animations", []):
        for channel in anim.get("channels", []):
            target = channel.get("target", {}).get("node")
            if isinstance(target, int) and 0 <= target < len(node_names):
                name = node_names[target]
                if name not in animated_targets:
                    animated_targets.append(name)
    print(f"SPECIES_MODEL_OK id={species_id} label={label} bytes={total} meshes={len(doc['meshes'])} animations={animations} extensions={sorted(extensions)}")
    print(f"SPECIES_BOUNDS id={species_id} label={label} min={mins} max={maxs} size={size}")
    print(f"SPECIES_SKIN_JOINTS id={species_id} label={label} count={len(skin_joints)} names={skin_joints}")
    print(f"SPECIES_ANIM_TARGETS id={species_id} label={label} names={animated_targets}")
    return {
        "animations": animations,
        "joint_count": len(skin_joints),
        "size": size,
        "bytes": total,
    }


def _animation_score(meta: dict) -> int:
    names = [str(n).lower() for n in meta.get("animations", [])]
    score = len(names) * 3 + min(int(meta.get("joint_count", 0)), 60)
    for keyword in ("walk", "run", "attack", "roar", "bite", "idle"):
        if any(keyword in n for n in names):
            score += 25
    return score


def choose_model(species_id: str, museum_src: Path, candidate_src: Path, dst: Path) -> None:
    museum_packed = museum_src.with_name(f"{species_id}-museum-packed.glb")
    candidate_packed = candidate_src.with_name(f"{species_id}-candidate-packed.glb")
    gltfpack(museum_src, museum_packed)
    museum_meta = inspect_glb(species_id, museum_packed, "museum")
    gltfpack(candidate_src, candidate_packed)
    candidate_meta = inspect_glb(species_id, candidate_packed, "ar_world")
    museum_score = _animation_score(museum_meta)
    candidate_score = _animation_score(candidate_meta)
    # Prefer an actually rigged multi-animation model. Otherwise retain the higher-detail museum model.
    use_candidate = candidate_meta["joint_count"] > 0 and len(candidate_meta["animations"]) >= 2 and candidate_score > museum_score
    selected = candidate_packed if use_candidate else museum_packed
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(selected, dst)
    selected_meta = inspect_glb(species_id, dst, "FINAL")
    print(f"SPECIES_SELECTION id={species_id} source={'ar_world' if use_candidate else 'museum'} score={candidate_score if use_candidate else museum_score} animations={selected_meta['animations']}")


def main() -> None:
    work = Path("/tmp/dino-model-sources")
    shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True)

    for species_id, filename in SPECIES_FILES.items():
        museum_src = work / f"{species_id}-museum.glb"
        candidate_src = work / f"{species_id}-arworld.glb"
        dst = Path("assets/dinosaurs") / species_id / "model.glb"
        download(f"{MUSEUM_BASE}/{species_id}/model/model.glb", museum_src)
        download(f"{AR_WORLD_BASE}/{filename}", candidate_src)
        choose_model(species_id, museum_src, candidate_src, dst)

    velo_dir = work / "velociraptor"
    for rel in VELO_FILES:
        download(f"{VELOCIRAPTOR_BASE}/{rel}", velo_dir / rel)
    velo_dst = Path("assets/dinosaurs/velociraptor/model.glb")
    gltfpack(velo_dir / "scene.gltf", velo_dst)
    inspect_glb("velociraptor", velo_dst, "FINAL")

    print("NEXT_DINOSAUR_MODELS_OK")


if __name__ == "__main__":
    main()
