extends SceneTree

const TREX_REQUIRED_ANIMS: Array[String] = ["idle", "run", "roar", "bite", "tail"]
const SHARED_ASSETS: Array[String] = [
    "res://assets/environments/hell_creek/models/fern_02.glb",
    "res://assets/environments/hell_creek/models/dead_tree_trunk.glb",
    "res://assets/environments/hell_creek/models/tree_stump_01.glb",
    "res://assets/environments/hell_creek/models/rock_moss_set_01.glb",
    "res://assets/environments/hell_creek/models/shrub_03.glb",
    "res://assets/environments/hell_creek/xanderklinge_2k.hdr",
    "res://assets/environments/hell_creek/textures/mud_forest_diff_1k.jpg",
    "res://assets/audio/tyrannosaurus_rex/roar_realistic.wav",
    "res://assets/audio/tyrannosaurus_rex/hell_creek_ambience.ogg",
    "res://assets/audio/shared/morrison_plain.ogg",
    "res://assets/audio/shared/dry_wind.ogg",
]

func _init() -> void:
    var failures: Array[String] = []
    for path in SHARED_ASSETS:
        if not ResourceLoader.exists(path):
            failures.append("Missing shared production asset: %s" % path)

    var catalog_path := "res://data/dinosaurs.json"
    if not FileAccess.file_exists(catalog_path):
        failures.append("Missing dinosaur catalog")
        _finish(failures)
        return
    var file := FileAccess.open(catalog_path, FileAccess.READ)
    var parsed: Variant = JSON.parse_string(file.get_as_text())
    if not (parsed is Dictionary) or not parsed.has("dinosaurs"):
        failures.append("Invalid dinosaur catalog JSON")
        _finish(failures)
        return
    var dinosaurs: Array = parsed["dinosaurs"]
    if dinosaurs.size() != 6:
        failures.append("Expected six dinosaurs, got %d" % dinosaurs.size())

    var seen_ids: Dictionary = {}
    for raw_entry in dinosaurs:
        if not (raw_entry is Dictionary):
            failures.append("Catalog entry is not a Dictionary")
            continue
        var entry: Dictionary = raw_entry
        var species_id := str(entry.get("id", ""))
        if species_id.is_empty():
            failures.append("Catalog entry has no id")
            continue
        if seen_ids.has(species_id):
            failures.append("Duplicate species id: %s" % species_id)
        seen_ids[species_id] = true
        _validate_species(entry, failures)

    _finish(failures)

func _validate_species(entry: Dictionary, failures: Array[String]) -> void:
    var species_id := str(entry.get("id", ""))
    var model_path := str(entry.get("model_path", ""))
    var narration_path := str(entry.get("narration_path", ""))
    var ambience_path := str(entry.get("ambience_path", ""))
    if model_path.is_empty() or not ResourceLoader.exists(model_path):
        failures.append("%s: model is missing: %s" % [species_id, model_path])
        return
    if narration_path.is_empty() or not ResourceLoader.exists(narration_path):
        failures.append("%s: packaged Russian narration missing: %s" % [species_id, narration_path])
    if ambience_path.is_empty() or not ResourceLoader.exists(ambience_path):
        failures.append("%s: ambience missing: %s" % [species_id, ambience_path])

    var packed := load(model_path) as PackedScene
    if packed == null:
        failures.append("%s: GLB did not import as PackedScene" % species_id)
        return
    var actor := packed.instantiate()
    var meshes := actor.find_children("*", "MeshInstance3D", true, false)
    var animation_players := actor.find_children("*", "AnimationPlayer", true, false)
    print(species_id, " imported meshes: ", meshes.size(), " animation_players: ", animation_players.size())
    if meshes.is_empty():
        failures.append("%s: scene contains no MeshInstance3D" % species_id)
    if animation_players.is_empty():
        failures.append("%s: scene contains no AnimationPlayer" % species_id)
    else:
        var names: Array[String] = []
        for player_node in animation_players:
            var player := player_node as AnimationPlayer
            for clip in player.get_animation_list():
                var lower := String(clip).to_lower()
                names.append(lower)
                print(species_id, " animation: ", clip)
        if names.is_empty():
            failures.append("%s: AnimationPlayer contains no animations" % species_id)
        if species_id == "tyrannosaurus_rex":
            for required in TREX_REQUIRED_ANIMS:
                var found := false
                for name in names:
                    if required in name:
                        found = true
                        break
                if not found:
                    failures.append("T. rex missing animation family: %s" % required)
    actor.free()

func _finish(failures: Array[String]) -> void:
    if failures.is_empty():
        print("ASSET_VALIDATION_OK species=6")
        quit(0)
        return
    for failure in failures:
        push_error(failure)
    quit(1)
