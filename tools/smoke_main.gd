extends SceneTree

func _initialize() -> void:
    call_deferred("_run")

func _fail(code: int, message: String, main: Node = null) -> void:
    push_error(message)
    if main != null and is_instance_valid(main):
        main.queue_free()
        await process_frame
        await process_frame
    quit(code)

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
    await process_frame

    var actor: Node = main.get("current_actor")
    if actor == null or not is_instance_valid(actor):
        await _fail(2, "SMOKE: T. rex actor was not instantiated", main)
        return
    var stage := main.get_node_or_null("Stage")
    if stage == null or stage.get_child_count() < 70:
        await _fail(3, "SMOKE: scanned habitat assets did not populate", main)
        return
    var meshes := actor.find_children("*", "MeshInstance3D", true, false)
    var animation_players := actor.find_children("*", "AnimationPlayer", true, false)
    if meshes.is_empty() or animation_players.is_empty():
        await _fail(4, "SMOKE: actor lost geometry or animation", main)
        return
    var animation_count := 0
    for node in animation_players:
        animation_count += (node as AnimationPlayer).get_animation_list().size()
    if animation_count < 5:
        await _fail(5, "SMOKE: expected live multi-animation T. rex", main)
        return
    var ambience: AudioStreamPlayer = main.get("ambience_player") as AudioStreamPlayer
    var narration: AudioStreamPlayer = main.get("narration_player") as AudioStreamPlayer
    var roar: AudioStreamPlayer3D = main.get("roar_player") as AudioStreamPlayer3D
    if ambience == null or ambience.stream == null:
        await _fail(6, "SMOKE: real habitat ambience is not loaded", main)
        return
    if narration == null or narration.stream == null:
        await _fail(7, "SMOKE: packaged Russian narration is not loaded", main)
        return
    if roar == null or roar.stream == null:
        await _fail(8, "SMOKE: roar audio is not loaded", main)
        return
    print("SMOKE_MAIN_OK meshes=", meshes.size(), " animations=", animation_count, " stage_children=", stage.get_child_count(), " audio=production")
    ambience.stop()
    narration.stop()
    roar.stop()
    main.queue_free()
    await process_frame
    await process_frame
    await process_frame
    packed = null
    await process_frame
    quit(0)
