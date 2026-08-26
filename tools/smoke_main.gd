extends SceneTree

func _initialize() -> void:
    call_deferred("_run")

func _shutdown_main(main: Node) -> void:
    if main == null or not is_instance_valid(main):
        return

    # Stop runtime systems before destroying the tree. This matters in headless CI:
    # AnimationPlayer, Timer and AudioStreamPlayer can otherwise retain imported
    # resources until SceneTree teardown and produce false-positive leak diagnostics.
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

    # Let queued scene removals and audio backend changes settle, then destroy the
    # complete node hierarchy synchronously instead of quitting on the same frame.
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
    await process_frame
    await process_frame
    await process_frame
    await process_frame

    var actor: Node = main.get("current_actor")
    if actor == null or not is_instance_valid(actor):
        await _fail(2, "SMOKE: T. rex actor was not instantiated", main)
        return

    var stage: Node = main.get_node_or_null("Stage")
    if stage == null or stage.get_child_count() < 70:
        await _fail(3, "SMOKE: scanned habitat assets did not populate", main)
        return

    var meshes: Array[Node] = actor.find_children("*", "MeshInstance3D", true, false)
    var animation_players: Array[Node] = actor.find_children("*", "AnimationPlayer", true, false)
    if meshes.is_empty() or animation_players.is_empty():
        await _fail(4, "SMOKE: actor lost geometry or animation", main)
        return

    var required := {
        "idle": false,
        "run": false,
        "roar": false,
        "bite": false,
        "attack_tail": false,
    }
    var animation_count := 0
    for node in animation_players:
        var player := node as AnimationPlayer
        for clip in player.get_animation_list():
            animation_count += 1
            var lower := String(clip).to_lower()
            if required.has(lower):
                required[lower] = true
    for clip_name in required:
        if not bool(required[clip_name]):
            await _fail(5, "SMOKE: missing required animation %s" % clip_name, main)
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

    # Drop local references before freeing the scene so the shutdown check measures
    # the application tree rather than temporary test variables.
    actor = null
    stage = null
    meshes.clear()
    animation_players.clear()
    ambience = null
    narration = null
    roar = null

    await _shutdown_main(main)
    main = null
    packed = null
    await process_frame
    await process_frame
    quit(0)
