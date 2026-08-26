extends SceneTree

func _initialize() -> void:
    call_deferred("_run")

func _run() -> void:
    var packed := load("res://scenes/main.tscn") as PackedScene
    if packed == null:
        push_error("SMOKE: main.tscn failed to load")
        quit(1)
        return

    var main := packed.instantiate()
    root.add_child(main)
    await process_frame
    await process_frame
    await process_frame

    var actor: Node = main.get("current_actor")
    if actor == null or not is_instance_valid(actor):
        push_error("SMOKE: T. rex actor was not instantiated")
        quit(2)
        return

    var stage := main.get_node_or_null("Stage")
    if stage == null or stage.get_child_count() < 8:
        push_error("SMOKE: habitat stage did not build correctly")
        quit(3)
        return

    var meshes := actor.find_children("*", "MeshInstance3D", true, false)
    var animation_players := actor.find_children("*", "AnimationPlayer", true, false)
    if meshes.is_empty() or animation_players.is_empty():
        push_error("SMOKE: actor lost geometry or animation")
        quit(4)
        return

    print("SMOKE_MAIN_OK meshes=", meshes.size(), " animation_players=", animation_players.size(), " stage_children=", stage.get_child_count())
    quit(0)
