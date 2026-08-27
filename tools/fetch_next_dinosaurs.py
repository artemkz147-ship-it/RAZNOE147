#!/usr/bin/env python3
"""Fetch five additional dinosaurs with authored skeletal locomotion clips."""
from __future__ import annotations

import json
import shutil
import struct
import sys
import urllib.request
from pathlib import Path

PACK_COMMIT = "1ba35d999996f8be7936a015441a78141b4fef6e"
PACK_BASE = f"https://raw.githubusercontent.com/mur1ll0/chomp-3d-web/{PACK_COMMIT}/public/models/dinos"

MODELS = {
    "triceratops": "Triceratops.glb",
    "velociraptor": "Velociraptor.glb",
    "stegosaurus": "Stegossaurus.glb",
    "apatosaurus": "Apatossaurus.glb",
    "parasaurolophus": "Parasaurolophus.glb",
}


def download(url: str, dst: Path) -> None:
    dst.parent.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(url, headers={"User-Agent": "DinoEncyclopedia-CI/1.0"})
    with urllib.request.urlopen(req, timeout=180) as response, dst.open("wb") as out:
        shutil.copyfileobj(response, out)
    if dst.stat().st_size < 100_000:
        raise RuntimeError(f"Downloaded model is unexpectedly small: {dst}")
    print(f"downloaded {dst} ({dst.stat().st_size} bytes)")


def read_doc(path: Path) -> tuple[dict, int]:
    with path.open("rb") as f:
        magic, version, total = struct.unpack("<4sII", f.read(12))
        if magic != b"glTF" or version != 2:
            raise RuntimeError(f"invalid GLB: {path}")
        clen, ctype = struct.unpack("<II", f.read(8))
        if ctype != 0x4E4F534A:
            raise RuntimeError(f"GLB JSON chunk missing: {path}")
        return json.loads(f.read(clen).decode("utf-8")), total


def inspect_and_require(species_id: str, path: Path) -> None:
    doc, total = read_doc(path)
    if not doc.get("meshes"):
        raise RuntimeError(f"{species_id}: no meshes")
    names = [(a.get("name") or "").strip() for a in doc.get("animations", [])]
    lower = [n.lower() for n in names]
    required = ("idle", "walk", "run", "attack")
    for family in required:
        if not any(family in n for n in lower):
            raise RuntimeError(f"{species_id}: missing real {family} clip: {names}")
    joints = 0
    for skin in doc.get("skins", []):
        joints += len(skin.get("joints", []))
    if joints < 4:
        raise RuntimeError(f"{species_id}: not a usable skinned model")
    print(f"SPECIES_MODEL_OK id={species_id} bytes={total} animations={names} joints={joints}")


def update_catalog() -> None:
    path = Path("data/dinosaurs.json")
    data = json.loads(path.read_text(encoding="utf-8"))
    dinos = data["dinosaurs"]

    # Replace the old sixth species with a fully animated Parasaurolophus.
    for i, d in enumerate(dinos):
        if d.get("id") == "dilophosaurus":
            dinos[i] = {
                "id": "parasaurolophus",
                "name_ru": "Паразауролоф",
                "scientific_name": "Parasaurolophus walkeri",
                "period_ru": "Поздний мел, около 76–73 млн лет назад",
                "region_ru": "Запад Северной Америки",
                "diet_ru": "Растительноядный",
                "length_m": 10.0,
                "mass_kg": 2500,
                "description_ru": "Паразауролоф был крупным утконосым динозавром с длинным полым костным гребнем. Он мог ходить на двух и четырёх конечностях и питался растительностью.",
                "narration_text_ru": "Паразауролоф жил в позднем меловом периоде на западе Северной Америки. Он достигал примерно десяти метров в длину. Самая заметная особенность животного — длинный костный гребень на голове. Паразауролоф был растительноядным и мог передвигаться как на двух, так и на четырёх конечностях.",
                "evidence_notes": [
                    "Длина крупных особей составляла около десяти метров.",
                    "Имел длинный полый костный гребень.",
                    "Был растительноядным."
                ],
                "model_path": "res://assets/dinosaurs/parasaurolophus/model.glb",
                "environment_path": "",
                "ambience_path": "res://assets/audio/tyrannosaurus_rex/hell_creek_ambience.ogg",
                "narration_path": "res://assets/audio/parasaurolophus/narration_ru.wav",
                "roar_path": "res://assets/audio/parasaurolophus/roar_voice.wav",
                "animations": {},
                "interactive_actions": [],
                "wander_radius": 7.0,
                "sources": []
            }
            break

    for d in dinos:
        species_id = d.get("id")
        if species_id not in MODELS:
            continue
        d["model_path"] = f"res://assets/dinosaurs/{species_id}/model.glb"
        d["animations"] = {
            "idle": ["Idle"],
            "walk": ["Walk"],
            "run": ["Run"],
            "roar": ["Attack"],
            "threat": ["Attack"],
            "bite": ["Attack"],
            "look": ["Idle"],
            "jump": ["Jump"],
        }
        d["interactive_actions"] = ["walk", "run", "threat", "roar", "jump"]
        d["wander_radius"] = 5.5 if species_id == "velociraptor" else 7.0

    data["schema_version"] = max(int(data.get("schema_version", 1)), 6)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print("CATALOG_ANIMATION_MAP_OK")


def main() -> None:
    for species_id, filename in MODELS.items():
        dst = Path("assets/dinosaurs") / species_id / "model.glb"
        download(f"{PACK_BASE}/{filename}", dst)
        inspect_and_require(species_id, dst)
    update_catalog()
    print("NEXT_DINOSAUR_MODELS_OK")


if __name__ == "__main__":
    main()
