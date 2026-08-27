class_name DinosaurController
extends Node

signal action_started(action_name: String)
signal asset_error(message: String)

var actor: Node3D
var animation_player: AnimationPlayer
var action_map: Dictionary = {}
var species_data: Dictionary = {}

var _auto_timer: Timer
var _is_busy := false
var _current_action := "idle"
var _home_position := Vector3.ZERO
var _move_target := Vector3.ZERO
var _move_time_left := 0.0
var _move_speed := 0.9
var _wander_radius := 7.0
var _move_clip := StringName()

func _ready() -> void:
    _auto_timer = Timer.new()
    _auto_timer.one_shot = true
    _auto_timer.timeout.connect(_on_auto_timer_timeout)
    add_child(_auto_timer)
    set_process(true)

func _process(delta: float) -> void:
    if actor == null or not is_instance_valid(actor):
        return
    if _current_action in ["walk", "run"] and _move_time_left > 0.0:
        _process_locomotion(delta)

func attach(new_actor: Node3D, actions: Dictionary, data: Dictionary = {}) -> void:
    stop_all()
    actor = new_actor
    action_map = actions
    species_data = data
    animation_player = _find_animation_player(actor)
    _home_position = actor.position
    _wander_radius = clampf(float(species_data.get("wander_radius", 7.0)), 2.0, 11.0)
    var length_m := float(species_data.get("length_m", 8.0))
    _move_speed = clampf(0.55 + length_m * 0.045, 0.65, 1.5)

    if animation_player == null:
        asset_error.emit("В модели нет AnimationPlayer")
        return
    if not has_action("idle"):
        asset_error.emit("В модели нет настоящей Idle-анимации")
        return
    if not has_action("walk"):
        asset_error.emit("В модели нет настоящей Walk-анимации")
        return

    play_idle()
    _queue_next_auto_action()

func play_idle() -> void:
    _is_busy = false
    _current_action = "idle"
    _move_time_left = 0.0
    _move_clip = StringName()
    if animation_player == null:
        return
    var clip := _resolve_clip(_candidates("idle", ["Idle", "idle", "Idle_01", "Idle_02"]))
    if clip == StringName():
        return
    animation_player.speed_scale = 1.0
    var anim := animation_player.get_animation(clip)
    if anim != null:
        anim.loop_mode = Animation.LOOP_LINEAR
    animation_player.play(clip, 0.18)

func play_action(action_name: String) -> bool:
    if animation_player == null:
        return false
    if action_name in ["walk", "run"]:
        return _start_locomotion(action_name)

    var clip := _resolve_clip(_candidates(action_name, [action_name]))
    if clip == StringName():
        return false

    _is_busy = true
    _current_action = action_name
    animation_player.speed_scale = _speed_for_action(action_name)
    var anim := animation_player.get_animation(clip)
    if anim != null:
        anim.loop_mode = Animation.LOOP_NONE
    animation_player.play(clip, 0.16)
    action_started.emit(action_name)
    if action_name == "roar":
        _play_host_roar_audio()
    if not animation_player.animation_finished.is_connected(_on_animation_finished):
        animation_player.animation_finished.connect(_on_animation_finished, CONNECT_ONE_SHOT)
    return true

func _start_locomotion(action_name: String) -> bool:
    var defaults: Array[String] = ["Walk", "walk"] if action_name == "walk" else ["Run", "run", "Jog", "Sprint"]
    var clip := _resolve_clip(_candidates(action_name, defaults))
    if clip == StringName():
        return false

    _is_busy = true
    _current_action = action_name
    _move_clip = clip
    _move_time_left = randf_range(3.0, 5.5) if action_name == "walk" else randf_range(1.8, 3.4)

    var angle := randf_range(0.0, TAU)
    var distance := randf_range(_wander_radius * 0.45, _wander_radius)
    _move_target = _home_position + Vector3(cos(angle) * distance, 0.0, sin(angle) * distance)

    animation_player.speed_scale = _speed_for_action(action_name)
    var anim := animation_player.get_animation(clip)
    if anim != null:
        anim.loop_mode = Animation.LOOP_LINEAR
    animation_player.play(clip, 0.15)
    action_started.emit(action_name)
    return true

func _process_locomotion(delta: float) -> void:
    _move_time_left -= delta
    var flat := _move_target - actor.position
    flat.y = 0.0
    if flat.length() < 0.35 or _move_time_left <= 0.0:
        _finish_locomotion()
        return

    var direction := flat.normalized()
    var desired_yaw := atan2(direction.x, direction.z)
    actor.rotation.y = lerp_angle(actor.rotation.y, desired_yaw, clampf(delta * 3.0, 0.0, 1.0))
    var speed_multiplier := 1.7 if _current_action == "run" else 1.0
    actor.position += direction * _move_speed * speed_multiplier * delta
    actor.position.y = _home_position.y

func _finish_locomotion() -> void:
    if animation_player != null:
        animation_player.speed_scale = 1.0
    play_idle()
    _queue_next_auto_action()

func has_action(action_name: String) -> bool:
    if animation_player == null:
        return false
    var defaults: Array[String] = []
    match action_name:
        "idle": defaults = ["Idle", "idle", "Idle_01", "Idle_02"]
        "walk": defaults = ["Walk", "walk"]
        "run": defaults = ["Run", "run", "Jog", "Sprint"]
        "roar": defaults = ["Roar", "roar", "Roar_01", "Roar_02", "Roar_03", "Call_Alert", "Attack"]
        "bite": defaults = ["Bite", "bite", "Bite_01", "Bite_02", "Attack"]
        "threat": defaults = ["Attack", "attack", "Tackle", "Action_Pose", "Leap_01"]
        "look": defaults = ["Sniff", "Idle_02", "Idle"]
        "jump": defaults = ["Jump", "jump", "Leap_01", "Leap_02"]
        _: defaults = [action_name]
    return _resolve_clip(_candidates(action_name, defaults)) != StringName()

func stop_all() -> void:
    if _auto_timer != null:
        _auto_timer.stop()
    if animation_player != null:
        animation_player.stop()
    _is_busy = false
    _current_action = "idle"
    _move_time_left = 0.0
    _move_clip = StringName()

func _queue_next_auto_action() -> void:
    if _auto_timer == null or animation_player == null:
        return
    _auto_timer.start(randf_range(2.4, 4.8))

func _on_auto_timer_timeout() -> void:
    if _is_busy:
        _queue_next_auto_action()
        return

    var pool: Array[String] = []
    for candidate in ["walk", "walk", "look", "roar", "threat"]:
        if has_action(candidate):
            pool.append(candidate)
    if has_action("run") and randf() < 0.25:
        pool.append("run")
    if pool.is_empty():
        play_idle()
        _queue_next_auto_action()
        return

    var next_action := str(pool.pick_random())
    if not play_action(next_action):
        play_idle()
        _queue_next_auto_action()

func _on_animation_finished(_clip: StringName) -> void:
    if _current_action in ["walk", "run"]:
        return
    if animation_player != null:
        animation_player.speed_scale = 1.0
    play_idle()
    _queue_next_auto_action()

func _speed_for_action(action_name: String) -> float:
    match action_name:
        "walk": return 0.88
        "run": return 0.9
        "roar": return 0.95
        "threat": return 0.95
        _: return 1.0

func _play_host_roar_audio() -> void:
    var host := get_parent()
    if host == null:
        return
    var value: Variant = host.get("roar_player")
    if value is AudioStreamPlayer3D:
        var player := value as AudioStreamPlayer3D
        if player.stream != null and not player.playing:
            if actor != null and is_instance_valid(actor):
                player.global_position = actor.global_position
            player.play(0.0)

func _candidates(action_name: String, defaults: Array[String]) -> Array[String]:
    var result := _to_string_array(action_map.get(action_name, []))
    for item in defaults:
        if item not in result:
            result.append(item)
    return result

func _resolve_clip(candidates: Array[String]) -> StringName:
    if animation_player == null:
        return StringName()
    var clips := animation_player.get_animation_list()
    for candidate in candidates:
        for clip in clips:
            if String(clip).to_lower() == candidate.to_lower():
                return clip
    for candidate in candidates:
        for clip in clips:
            if candidate.to_lower() in String(clip).to_lower():
                return clip
    return StringName()

func _find_animation_player(root: Node) -> AnimationPlayer:
    if root is AnimationPlayer:
        return root as AnimationPlayer
    for child in root.get_children():
        var found := _find_animation_player(child)
        if found != null:
            return found
    return null

func _to_string_array(value: Variant) -> Array[String]:
    var result: Array[String] = []
    if value is Array:
        for item in value:
            result.append(str(item))
    elif value != null and not str(value).is_empty():
        result.append(str(value))
    return result
