class_name DinosaurController
extends Node

signal action_started(action_name: String)
signal asset_error(message: String)

var actor: Node3D
var animation_player: AnimationPlayer
var action_map: Dictionary = {}
var species_data: Dictionary = {}
var _idle_candidates: Array[String] = []
var _auto_cycle_actions: Array[String] = []
var _auto_timer: Timer
var _is_busy := false
var _current_action := "idle"
var _home_position := Vector3.ZERO
var _walk_target := Vector3.ZERO
var _walk_time_left := 0.0
var _walk_speed := 0.9
var _wander_radius := 7.0
var _base_y := 0.0
var _walk_phase := 0.0
var _using_fallback_walk := false

func _ready() -> void:
    _auto_timer = Timer.new()
    _auto_timer.one_shot = true
    _auto_timer.timeout.connect(_on_auto_timer_timeout)
    add_child(_auto_timer)
    set_process(true)

func _process(delta: float) -> void:
    if actor == null or not is_instance_valid(actor):
        return
    if _current_action != "walk" or _walk_time_left <= 0.0:
        return

    _walk_time_left -= delta
    var flat := _walk_target - actor.position
    flat.y = 0.0
    if flat.length() < 0.35 or _walk_time_left <= 0.0:
        _finish_walk()
        return

    var direction := flat.normalized()
    var desired_yaw := atan2(direction.x, direction.z)
    actor.rotation.y = lerp_angle(actor.rotation.y, desired_yaw, clampf(delta * 2.8, 0.0, 1.0))
    actor.position += direction * _walk_speed * delta

    if _using_fallback_walk:
        _walk_phase += delta * 5.2
        actor.position.y = _base_y + sin(_walk_phase) * 0.025

func attach(new_actor: Node3D, actions: Dictionary, data: Dictionary = {}) -> void:
    actor = new_actor
    action_map = actions
    species_data = data
    animation_player = _find_animation_player(actor)
    _home_position = actor.position
    _base_y = actor.position.y
    _wander_radius = clampf(float(species_data.get("wander_radius", 7.0)), 3.0, 11.0)
    var length_m := float(species_data.get("length_m", 8.0))
    _walk_speed = clampf(0.55 + length_m * 0.045, 0.62, 1.35)
    _idle_candidates = _to_string_array(action_map.get("idle", ["idle", "Idle", "IDLE", "Idle_01", "Idle_02"]))

    _auto_cycle_actions.clear()
    _auto_cycle_actions.append("walk")
    for candidate in ["look", "roar", "bite", "threat"]:
        if has_action(candidate):
            _auto_cycle_actions.append(candidate)

    if animation_player == null:
        asset_error.emit("В модели нет анимационного контроллера")
        return
    play_idle()
    _queue_next_auto_action()

func play_idle() -> void:
    if animation_player == null:
        return
    _is_busy = false
    _current_action = "idle"
    _using_fallback_walk = false
    _walk_time_left = 0.0
    if actor != null and is_instance_valid(actor):
        actor.position.y = _base_y
    var clip := _resolve_clip(_idle_candidates)
    if clip == StringName():
        return
    animation_player.speed_scale = 1.0
    var anim := animation_player.get_animation(clip)
    if anim != null:
        anim.loop_mode = Animation.LOOP_LINEAR
    animation_player.play(clip, 0.22)

func play_action(action_name: String) -> bool:
    if animation_player == null:
        return false
    if action_name == "walk":
        return _start_walk()

    var candidates := _to_string_array(action_map.get(action_name, [action_name]))
    var clip := _resolve_clip(candidates)
    if clip == StringName():
        return false
    _is_busy = true
    _current_action = action_name
    animation_player.speed_scale = _speed_for_action(action_name)
    var anim := animation_player.get_animation(clip)
    if anim != null:
        anim.loop_mode = Animation.LOOP_NONE
    animation_player.play(clip, 0.20)
    action_started.emit(action_name)
    if action_name == "roar":
        _play_host_roar_audio()
    if not animation_player.animation_finished.is_connected(_on_animation_finished):
        animation_player.animation_finished.connect(_on_animation_finished, CONNECT_ONE_SHOT)
    return true

func _start_walk() -> bool:
    if actor == null or animation_player == null:
        return false
    _is_busy = true
    _current_action = "walk"
    _walk_phase = 0.0
    _base_y = actor.position.y
    _walk_time_left = randf_range(3.2, 6.0)

    var angle := randf_range(0.0, TAU)
    var distance := randf_range(_wander_radius * 0.45, _wander_radius)
    _walk_target = _home_position + Vector3(cos(angle) * distance, 0.0, sin(angle) * distance)

    var walk_clip := _resolve_clip(_to_string_array(action_map.get("walk", ["walk", "Walk", "run", "Run", "Jog"])))
    if walk_clip != StringName():
        _using_fallback_walk = false
        animation_player.speed_scale = _speed_for_action("walk")
        var anim := animation_player.get_animation(walk_clip)
        if anim != null:
            anim.loop_mode = Animation.LOOP_LINEAR
        animation_player.play(walk_clip, 0.22)
    else:
        _using_fallback_walk = true
        var idle_clip := _resolve_clip(_idle_candidates)
        if idle_clip != StringName():
            animation_player.speed_scale = 1.05
            var idle_anim := animation_player.get_animation(idle_clip)
            if idle_anim != null:
                idle_anim.loop_mode = Animation.LOOP_LINEAR
            animation_player.play(idle_clip, 0.20)
    action_started.emit("walk")
    return true

func _finish_walk() -> void:
    if actor != null and is_instance_valid(actor):
        actor.position.y = _base_y
    animation_player.speed_scale = 1.0
    play_idle()
    _queue_next_auto_action()

func has_action(action_name: String) -> bool:
    if animation_player == null:
        return false
    if action_name == "walk":
        return true
    var candidates := _to_string_array(action_map.get(action_name, [action_name]))
    return _resolve_clip(candidates) != StringName()

func stop_all() -> void:
    if _auto_timer != null:
        _auto_timer.stop()
    if animation_player != null:
        animation_player.stop()
    _is_busy = false
    _current_action = "idle"
    _walk_time_left = 0.0
    _using_fallback_walk = false

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

func _speed_for_action(action_name: String) -> float:
    match action_name:
        "walk": return 0.72
        "threat": return 0.88
        "roar": return 0.96
        "bite": return 0.92
        _: return 1.0

func _queue_next_auto_action() -> void:
    if _auto_timer == null or _auto_cycle_actions.is_empty():
        return
    _auto_timer.start(randf_range(2.8, 5.6))

func _on_auto_timer_timeout() -> void:
    if _is_busy:
        _queue_next_auto_action()
        return
    var pool: Array[String] = _auto_cycle_actions.duplicate()
    pool.append("walk")
    pool.append("walk")
    var next_action := str(pool.pick_random())
    if not play_action(next_action):
        play_idle()
        _queue_next_auto_action()

func _on_animation_finished(_clip: StringName) -> void:
    if _current_action == "walk":
        return
    animation_player.speed_scale = 1.0
    play_idle()
    _queue_next_auto_action()

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
        return root
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
    else:
        result.append(str(value))
    return result
