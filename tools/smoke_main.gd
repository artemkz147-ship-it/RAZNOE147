extends SceneTree

func _initialize() -> void:
    call_deferred("_run")

func _shutdown_main(main: Node) -> void:
    if main == null or not is_instance_valid(main):
        return
    var controller: Variant = main.get("dinosaur_controller")
    if controller is Node and is_instance_valid(controller):
        if (controller as Node).has_method("stop_all"):
            (controller as Node).call("stop_all")

    var ambience: Variant = main.get("ambience_player")
    var narration: Variant = main.get("narration_player")
    var roar: Variant = main.get("roar_player")
    if ambience is AudioStreamPlayer:
        (ambience as AudioStreamPlayer).stop()
        (ambience as AudioStreamPlayer).stream = null
    if narration is AudioStreamPlayer:
        (narration as AudioStreamPlayer).stop()
        (narration as AudioStreamPlayer).stream = null
    if roar is AudioStreamPlayer3D:
        (roar as AudioStreamPlayer3D).stop()
        (roar as AudioStreamPlayer3D).stream = null

    DisplayServer.tts_stop()
    await process_frame
    await process_frame
    if main.get_parent() != null:
        main.get_parent().remove_child(main)
    main.free()
    await process_frame
    await process_frame
    await process_frame

func _fail(code: int, message: String, main: Node = null) -> void:
    push_error(message)
    await _shutdown_main(main)
    quit(code)

func _run() -> void:
    var packed: PackedScene = load("res://scenes/main.tscn") as PackedScene
    if packed == null:
        push_error("SMOKE: main.tscn failed to load")
        quit(1)
        return

    var main: Node = packed.instantiate()
    root.add_child(main)
    for _frame in 5:
        await process_frame

    var catalog: Array = main.get("catalog")
    if catalog.size() != 6:
        await _fail(2, "SMOKE: expected 6 catalog entries, got %d" % catalog.size(), main)
        return

    var stage: Node = main.get_node_or_null("Stage")
    if stage == null or stage.get_child_count() < 70:
        await _fail(3, "SMOKE: scanned habitat assets did not populate", main)
        return

    for index in catalog.size():
        main.call("_show_dinosaur", index)
        for _frame in 4:
            await process_frame
        var actor: Node = main.get("current_actor")
        if actor == null or not is_instance_valid(actor):
            await _fail(10 + index, "SMOKE: species %d actor was not instantiated" % index, main)
            return
        var meshes: Array[Node] = actor.find_children("*", "MeshInstance3D", true, false)
        if meshes.is_empty():
            await _fail(20 + index, "SMOKE: species %d actor has no meshes" % index, main)
            return

        var narration: AudioStreamPlayer = main.get("narration_player") as AudioStreamPlayer
        var ambience: AudioStreamPlayer = main.get("ambience_player") as AudioStreamPlayer
        if narration == null or narration.stream == null:
            await _fail(30 + index, "SMOKE: species %d narration is missing" % index, main)
            return
        if ambience == null or ambience.stream == null:
            await _fail(40 + index, "SMOKE: species %d ambience is missing" % index, main)
            return

        var species: Dictionary = catalog[index]
        var species_id := str(species.get("id", "unknown"))
        var animation_players: Array[Node] = actor.find_children("*", "AnimationPlayer", true, false)
        var animation_count := 0
        for node in animation_players:
            var player := node as AnimationPlayer
            animation_count += player.get_animation_list().size()
        if animation_count < 1:
            await _fail(50 + index, "SMOKE: %s has no runtime animation" % species_id, main)
            return
        print("SMOKE_SPECIES_OK id=", species_id, " meshes=", meshes.size(), " animations=", animation_count)

        meshes.clear()
        animation_players.clear()
        actor = null
        narration = null
        ambience = null

    var roar: AudioStreamPlayer3D = main.get("roar_player") as AudioStreamPlayer3D
    main.call("_show_dinosaur", 0)
    for _frame in 3:
        await process_frame
    roar = main.get("roar_player") as AudioStreamPlayer3D
    if roar == null or roar.stream == null:
        await _fail(60, "SMOKE: T. rex roar audio is missing", main)
        return
    if roar.volume_db < 16.0:
        await _fail(61, "SMOKE: T. rex roar gain was not increased", main)
        return

    print("SMOKE_MAIN_OK species=6 stage_children=", stage.get_child_count(), " roar_db=", roar.volume_db)
    stage = null
    roar = null
    catalog.clear()
    packed = null
    await _shutdown_main(main)
    main = null
    await process_frame
    await process_frame
    quit(0)
