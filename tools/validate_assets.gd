extends SceneTree

const TREX_PATH := "res://assets/dinosaurs/tyrannosaurus_rex/model.glb"
const REQUIRED_ANIMS: Array[String] = ["idle", "run", "roar", "bite", "tail"]
const PRODUCTION_ASSETS: Array[String] = [
    "res://assets/environments/hell_creek/models/fern_02.glb",
    "res://assets/environments/hell_creek/models/dead_tree_trunk.glb",
    "res://assets/environments/hell_creek/models/tree_stump_01.glb",
    "res://assets/environments/hell_creek/models/rock_moss_set_01.glb",
    "res://assets/environments/hell_creek/models/shrub_03.glb",
    "res://assets/environments/hell_creek/xanderklinge_2k.hdr",
    "res://assets/environments/hell_creek/textures/mud_forest_diff_1k.jpg",
    "res://assets/audio/tyrannosaurus_rex/roar_realistic.ogg",
    "res://assets/audio/tyrannosaurus_rex/hell_creek_ambience.ogg",
    "res://assets/audio/tyrannosaurus_rex/narration_ru.wav",
]

func _init() -> void:
    var failures: Array[String] = []
    for path in PRODUCTION_ASSETS:
        if not ResourceLoader.exists(path):
            failures.append("Missing production asset: %s" % path)
    if not ResourceLoader.exists(TREX_PATH):
        failures.append("ResourceLoader cannot resolve %s" % TREX_PATH)
    else:
        var packed := load(TREX_PATH) as PackedScene
        if packed == null:
            failures.append("T. rex GLB did not import as PackedScene")
        else:
            var actor := packed.instantiate()
            var meshes := actor.find_children("*", "MeshInstance3D", true, false)
            var animation_players := actor.find_children("*", "AnimationPlayer", true, false)
            print("T. rex imported meshes: ", meshes.size())
            print("T. rex AnimationPlayers: ", animation_players.size())
            if meshes.is_empty(): failures.append("T. rex scene contains no MeshInstance3D")
            if animation_players.is_empty():
                failures.append("T. rex scene contains no AnimationPlayer")
            else:
                var names: Array[String] = []
                for player_node in animation_players:
                    var player := player_node as AnimationPlayer
                    for clip in player.get_animation_list():
                        var clip_name := String(clip).to_lower()
                        names.append(clip_name)
                        print("animation: ", clip)
                if names.size() < 5:
                    failures.append("Expected at least five T. rex animations, got %d" % names.size())
                for required in REQUIRED_ANIMS:
                    var found := false
                    for name in names:
                        if required in name:
                            found = true
                            break
                    if not found: failures.append("Missing animation family: %s" % required)
            actor.free()
    if failures.is_empty():
        print("ASSET_VALIDATION_OK")
        quit(0)
        return
    for failure in failures: push_error(failure)
    quit(1)
