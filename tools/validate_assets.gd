extends SceneTree

const TREX_PATH := "res://assets/dinosaurs/tyrannosaurus_rex/model.glb"

func _init() -> void:
    var failures: Array[String] = []

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

            if meshes.is_empty():
                failures.append("T. rex scene contains no MeshInstance3D")
            if animation_players.is_empty():
                failures.append("T. rex scene contains no AnimationPlayer")
            else:
                var found_idle := false
                for player_node in animation_players:
                    var player := player_node as AnimationPlayer
                    for clip in player.get_animation_list():
                        print("animation: ", clip)
                        if String(clip).to_lower().contains("idle"):
                            found_idle = true
                if not found_idle:
                    failures.append("T. rex scene has no Idle animation")
            actor.free()

    if failures.is_empty():
        print("ASSET_VALIDATION_OK")
        quit(0)
        return

    for failure in failures:
        push_error(failure)
    quit(1)
