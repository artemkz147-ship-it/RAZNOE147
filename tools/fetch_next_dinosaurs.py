#!/usr/bin/env python3
"""Fetch dinosaur assets; every roaming species must have real skeletal locomotion."""
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
VELO_COMMIT = "d11faeee4fb0e3c24288a018c905f9bf4e4d256e"
VELO_BASE = f"https://raw.githubusercontent.com/CarlosHenriqueMkt/portfolio/{VELO_COMMIT}/public/velociraptor"
QUAT_COMMIT = "1ba35d999996f8be7936a015441a78141b4fef6e"
QUAT_BASE = f"https://raw.githubusercontent.com/mur1ll0/chomp-3d-web/{QUAT_COMMIT}/public/models/dinos"
GLTFPACK_VERSION = sys.argv[1] if len(sys.argv) > 1 else "1.2.0"

VELO_FILES = (
    "scene.gltf", "scene.bin",
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
    req = urllib.request.Request(url, headers={"User-Agent": "DinoEncyclopedia-CI/1.0"})
    with urllib.request.urlopen(req, timeout=180) as response, dst.open("wb") as out:
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


def read_doc(path: Path) -> tuple[dict, int]:
    with path.open("rb") as f:
        magic, version, total = struct.unpack("<4sII", f.read(12))
        if magic != b"glTF" or version != 2:
            raise RuntimeError(f"invalid GLB: {path}")
        clen, ctype = struct.unpack("<II", f.read(8))
        if ctype != 0x4E4F534A:
            raise RuntimeError(f"GLB JSON chunk missing: {path}")
        return json.loads(f.read(clen).decode("utf-8")), total


def inspect_glb(species_id: str, path: Path) -> dict:
    doc, total = read_doc(path)
    extensions = set(doc.get("extensionsUsed", [])) | set(doc.get("extensionsRequired", []))
    if "KHR_mesh_quantization" in extensions:
        raise RuntimeError(f"{species_id}: KHR_mesh_quantization unsupported")
    if not doc.get("meshes"):
        raise RuntimeError(f"{species_id}: no meshes")
    names = [(a.get("name") or "").strip() for a in doc.get("animations", [])]
    node_names = [(n.get("name") or f"node_{i}") for i, n in enumerate(doc.get("nodes", []))]
    joints: list[str] = []
    for skin in doc.get("skins", []):
        for idx in skin.get("joints", []):
            if isinstance(idx, int) and 0 <= idx < len(node_names):
                joints.append(node_names[idx])
    accessors = doc.get("accessors", [])
    mins = None
    maxs = None
    for mesh in doc.get("meshes", []):
        for primitive in mesh.get("primitives", []):
            idx = primitive.get("attributes", {}).get("POSITION")
            if idx is None or idx >= len(accessors):
                continue
            a = accessors[idx]
            amin, amax = a.get("min"), a.get("max")
            if not (isinstance(amin, list) and isinstance(amax, list) and len(amin) >= 3 and len(amax) >= 3):
                continue
            if mins is None:
                mins = [float(amin[i]) for i in range(3)]
                maxs = [float(amax[i]) for i in range(3)]
            else:
                for i in range(3):
                    mins[i] = min(mins[i], float(amin[i]))
                    maxs[i] = max(maxs[i], float(amax[i]))
    size = [round(maxs[i] - mins[i], 4) for i in range(3)] if mins is not None and maxs is not None else None
    print(f"SPECIES_MODEL_OK id={species_id} bytes={total} animations={names} joints={len(joints)} size={size}")
    return {"animations": names, "joints": len(joints), "size": size}


def require_locomotion(species_id: str, meta: dict) -> None:
    names = [n.lower() for n in meta["animations"]]
    if not any("idle" in n for n in names):
        raise RuntimeError(f"{species_id}: no idle animation: {names}")
    if not any(("walk" in n or "run" in n or "jog" in n or "sprint" in n) for n in names):
        raise RuntimeError(f"{species_id}: no locomotion animation: {names}")
    if meta["joints"] < 4:
        raise RuntimeError(f"{species_id}: locomotion model is not skinned")


def pack_museum(species_id: str, work: Path) -> None:
    src = work / f"{species_id}-museum.glb"
    dst = Path("assets/dinosaurs") / species_id / "model.glb"
    download(f"{MUSEUM_BASE}/{species_id}/model/model.glb", src)
    gltfpack(src, dst)
    inspect_glb(species_id, dst)


def pack_quaternius(species_id: str, filename: str, work: Path) -> None:
    src = work / filename
    dst = Path("assets/dinosaurs") / species_id / "model.glb"
    download(f"{QUAT_BASE}/{filename}", src)
    gltfpack(src, dst)
    meta = inspect_glb(species_id, dst)
    require_locomotion(species_id, meta)


def replace_sixth_catalog_species() -> None:
    path = Path("data/dinosaurs.json")
    data = json.loads(path.read_text(encoding="utf-8"))
    dinos = data["dinosaurs"]
    old_index = next(i for i, d in enumerate(dinos) if d.get("id") == "dilophosaurus")
    dinos[old_index] = {
        "id": "parasaurolophus",
        "name_ru": "Паразауролоф",
        "scientific_name": "Parasaurolophus walkeri",
        "period_ru": "Поздний мел, около 76–73 млн лет назад",
        "region_ru": "Запад Северной Америки",
        "diet_ru": "Растительноядный",
        "length_m": 10.0,
        "mass_kg": 2500,
        "description_ru": "Паразауролоф был крупным утконосым динозавром с длинным полым костным гребнем, уходившим назад от черепа. Он передвигался на двух и четырёх конечностях и питался растительностью. Гребень был связан с носовыми ходами и, вероятно, участвовал в звуковой коммуникации и распознавании сородичей.",
        "narration_text_ru": "Паразауролоф жил в позднем меловом периоде на западе Северной Америки. Он достигал примерно десяти метров в длину. Самая заметная особенность животного — длинный костный гребень на голове. Внутри него проходили вытянутые носовые каналы. Исследования формы этих каналов показывают, что гребень мог усиливать низкие звуки и помогать животным общаться. Паразауролоф был растительноядным и имел сложную зубную батарею, которая постоянно обновлялась. Он мог передвигаться как на двух, так и на четырёх конечностях.",
        "evidence_notes": [
            "Длина крупных особей составляла около десяти метров.",
            "Полый гребень соединён с дыхательными путями.",
            "Зубные батареи были приспособлены к переработке растительной пищи."
        ],
        "model_path": "res://assets/dinosaurs/parasaurolophus/model.glb",
        "environment_path": "res://assets/environments/hell_creek/environment.glb",
        "ambience_path": "res://assets/audio/tyrannosaurus_rex/hell_creek_ambience.ogg",
        "narration_path": "res://assets/audio/parasaurolophus/narration_ru.wav",
        "roar_path": "res://assets/audio/parasaurolophus/roar_voice.wav",
        "animations": {
            "idle": ["Idle", "idle"],
            "walk": ["Walk", "walk"],
            "threat": ["Attack", "attack"],
            "look": ["Eat", "eat"],
            "roar": ["Attack", "attack"]
        },
        "interactive_actions": ["walk", "look", "threat", "roar"],
        "wander_radius": 6.5,
        "sources": []
    }
    data["schema_version"] = max(int(data.get("schema_version", 1)), 5)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print("CATALOG_SIXTH_SPECIES_OK id=parasaurolophus")


def main() -> None:
    work = Path("/tmp/dino-model-sources")
    shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True)

    # Keep the higher-detail museum bodies where a real skeleton exists; the runtime
    # controller drives actual leg/head/tail bones for locomotion and behaviour.
    pack_museum("triceratops", work)
    pack_museum("stegosaurus", work)

    # These two use authored multi-clip skeletal animation: no whole-body sliding.
    pack_quaternius("apatosaurus", "Apatossaurus.glb", work)
    pack_quaternius("parasaurolophus", "Parasaurolophus.glb", work)

    velo_dir = work / "velociraptor"
    for rel in VELO_FILES:
        download(f"{VELO_BASE}/{rel}", velo_dir / rel)
    velo_dst = Path("assets/dinosaurs/velociraptor/model.glb")
    gltfpack(velo_dir / "scene.gltf", velo_dst)
    velo_meta = inspect_glb("velociraptor", velo_dst)
    require_locomotion("velociraptor", velo_meta)

    replace_sixth_catalog_species()
    print("NEXT_DINOSAUR_MODELS_OK")


if __name__ == "__main__":
    main()
