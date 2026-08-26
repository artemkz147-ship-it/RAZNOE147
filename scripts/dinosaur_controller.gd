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
var _using_procedural_walk := false
var _walk_phase := 0.0

var _skeleton: Skeleton3D
var _bone_base_rotations: Dictionary = {}
var _leg_bones: Array[int] = []
var _tail_bones: Array[int] = []
var _head_bones: Array[int] = []
var _jaw_bones: Array[int] = []
var _procedural_action_time := 0.0
var _procedural_action_duration := 0.0

func _ready() -> void:
    _auto_timer = Timer.new()
    _auto_timer.one_shot = true
    _auto_timer.timeout.connect(_on_auto_timer_timeout)
    add_child(_auto_timer)
    set_process(true)

func _process(delta: float) -> void:
    if actor == null or not is_instance_valid(actor):
        return

    if _current_action == "walk" and _walk_time_left > 0.0:
        _process_walk(delta)
        return

    if _procedural_action_time > 0.0:
        _procedural_action_time = maxf(_procedural_action_time - delta, 0.0)
        var progress := 1.0 - (_procedural_action_time / maxf(_procedural_action_duration, 0.001))
        _apply_procedural_action(_current_action, progress)
        if _procedural_action_time <= 0.0:
            play_idle()
            _queue_next_auto_action()

func attach(new_actor: Node3D, actions: Dictionary, data: Dictionary = {}) -> void:
    stop_all()
    actor = new_actor
    action_map = actions
    species_data = data
    animation_player = _find_animation_player(actor)
    _skeleton = _find_skeleton(actor)
    _prepare_procedural_rig()

    _home_position = actor.position
    _wander_radius = clampf(float(species_data.get("wander_radius", 7.0)), 2.0, 11.0)
    var length_m := float(species_data.get("length_m", 8.0))
    _walk_speed = clampf(0.55 + length_m * 0.045, 0.62, 1.35)
    _idle_candidates = _to_string_array(action_map.get("idle", ["idle", "Idle", "IDLE", "Idle_01", "Idle_02"]))

    if animation_player == null:
        asset_error.emit("В модели нет AnimationPlayer")
        return

    _auto_cycle_actions.clear()
    for candidate in ["walk", "look", "roar", "bite", "threat"]:
        if has_action(candidate):
            _auto_cycle_actions.append(candidate)

    play_idle()
    _queue_next_auto_action()

func play_idle() -> void:
    _is_busy = false
    _current_action = "idle"
    _walk_time_left = 0.0
    _using_procedural_walk = false
    _procedural_action_time = 0.0
    _restore_procedural_pose()

    if animation_player == null:
        return
    var clip := _resolve_clip(_idle_candidates)
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
    if action_name == "walk":
        return _start_walk()

    var candidates := _to_string_array(action_map.get(action_name, [action_name]))
    var clip := _resolve_clip(candidates)
    if clip != StringName():
        _is_busy = true
        _current_action = action_name
        _procedural_action_time = 0.0
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

    if _can_procedural_action(action_name):
        return _start_procedural_action(action_name)
    return false

func _start_walk() -> bool:
    if actor == null or animation_player == null:
        return false

    var walk_clip := _resolve_clip(_to_string_array(action_map.get("walk", ["walk", "Walk", "run", "Run", "Jog", "Sprint"])))
    var can_bone_walk := _can_procedural_walk()
    if walk_clip == StringName() and not can_bone_walk:
        return false

    _is_busy = true
    _current_action = "walk"
    _walk_phase = 0.0
    _walk_time_left = randf_range(3.2, 6.0)
    _procedural_action_time = 0.0

    var angle := randf_range(0.0, TAU)
    var distance := randf_range(_wander_radius * 0.45, _wander_radius)
    _walk_target = _home_position + Vector3(cos(angle) * distance, 0.0, sin(angle) * distance)

    if walk_clip != StringName():
        _using_procedural_walk = false
        animation_player.speed_scale = _speed_for_action("walk")
        var anim := animation_player.get_animation(walk_clip)
        if anim != null:
            anim.loop_mode = Animation.LOOP_LINEAR
        animation_player.play(walk_clip, 0.16)
    else:
        _using_procedural_walk = true
        animation_player.stop()
        _restore_procedural_pose()

    action_started.emit("walk")
    return true

func _process_walk(delta: float) -> void:
    _walk_time_left -= delta
    var flat := _walk_target - actor.position
    flat.y = 0.0
    if flat.length() < 0.32 or _walk_time_left <= 0.0:
        _finish_walk()
        return

    var direction := flat.normalized()
    var desired_yaw := atan2(direction.x, direction.z)
    actor.rotation.y = lerp_angle(actor.rotation.y, desired_yaw, clampf(delta * 2.8, 0.0, 1.0))
    actor.position += direction * _walk_speed * delta

    if _using_procedural_walk:
        _walk_phase += delta * 4.2
        _apply_procedural_walk(_walk_phase)

func _finish_walk() -> void:
    if animation_player != null:
        animation_player.speed_scale = 1.0
    play_idle()
    _queue_next_auto_action()

func has_action(action_name: String) -> bool:
    if animation_player == null:
        return false
    var candidates := _to_string_array(action_map.get(action_name, [action_name]))
    if _resolve_clip(candidates) != StringName():
        return true
    if action_name == "walk":
        return _can_procedural_walk()
    return _can_procedural_action(action_name)

func stop_all() -> void:
    if _auto_timer != null:
        _auto_timer.stop()
    if animation_player != null:
        animation_player.stop()
    _is_busy = false
    _current_action = "idle"
    _walk_time_left = 0.0
    _procedural_action_time = 0.0
    _using_procedural_walk = false
    _restore_procedural_pose()

func _prepare_procedural_rig() -> void:
    _bone_base_rotations.clear()
    _leg_bones.clear()
    _tail_bones.clear()
    _head_bones.clear()
    _jaw_bones.clear()
    if _skeleton == null:
        return

    _skeleton.reset_bone_poses()
    for i in _skeleton.get_bone_count():
        var name := String(_skeleton.get_bone_name(i)).to_lower()
        _bone_base_rotations[i] = _skeleton.get_bone_pose_rotation(i)
        if _is_primary_leg_bone(name):
            _leg_bones.append(i)
        if "tail" in name:
            _tail_bones.append(i)
        if "head" in name or "neck" in name:
            _head_bones.append(i)
        if "jaw" in name:
            _jaw_bones.append(i)

func _is_primary_leg_bone(name: String) -> bool:
    if "leg_front" in name or "leg_rear" in name:
        return true
    if "upperleg" in name or "upper_leg" in name:
        return true
    if "upperarm" in name or "upper_arm" in name:
        return true
    if "shoulder.l" in name or "shoulder.r" in name:
        return true
    return false

func _can_procedural_walk() -> bool:
    return _skeleton != null and _leg_bones.size() >= 4

func _can_procedural_action(action_name: String) -> bool:
    if _skeleton == null:
        return false
    match action_name:
        "look":
            return not _head_bones.is_empty()
        "roar":
            return not _head_bones.is_empty() or not _jaw_bones.is_empty()
        "threat":
            return not _head_bones.is_empty() or not _tail_bones.is_empty()
        "bite":
            return not _jaw_bones.is_empty()
        _:
            return false

func _start_procedural_action(action_name: String) -> bool:
    if not _can_procedural_action(action_name):
        return false
    _is_busy = true
    _current_action = action_name
    animation_player.stop()
    _restore_procedural_pose()
    _procedural_action_duration = 2.2 if action_name == "roar" else 1.7
    _procedural_action_time = _procedural_action_duration
    action_started.emit(action_name)
    if action_name == "roar":
        _play_host_roar_audio()
    return true

func _apply_procedural_walk(phase: float) -> void:
    if _skeleton == null:
        return
    var step := sin(phase)
    for bone_idx in _leg_bones:
        var name := String(_skeleton.get_bone_name(bone_idx)).to_lower()
        var side := -1.0 if (".l" in name or "_l" in name or "left" in name) else 1.0
        var front := 1.0 if ("front" in name or "arm" in name or "shoulder" in name) else -1.0
        var angle := step * side * front * 0.32
        _set_bone_offset(bone_idx, Vector3.RIGHT, angle)

    var tail_sway := sin(phase * 0.55) * 0.09
    for i in mini(_tail_bones.size(), 5):
        _set_bone_offset(_tail_bones[i], Vector3.UP, tail_sway * (1.0 + float(i) * 0.12))

    if not _head_bones.is_empty():
        _set_bone_offset(_head_bones[0], Vector3.RIGHT, sin(phase * 2.0) * 0.035)

func _apply_procedural_action(action_name: String, progress: float) -> void:
    if _skeleton == null:
        return
    _restore_procedural_pose()
    var pulse := sin(progress * PI)
    match action_name:
        "look":
            for i in mini(_head_bones.size(), 3):
                _set_bone_offset(_head_bones[i], Vector3.UP, sin(progress * TAU) * 0.16 * (1.0 - float(i) * 0.18))
        "roar":
            for i in mini(_head_bones.size(), 4):
                _set_bone_offset(_head_bones[i], Vector3.RIGHT, -pulse * 0.18 * (1.0 - float(i) * 0.12))
            for bone_idx in _jaw_bones:
                _set_bone_offset(bone_idx, Vector3.RIGHT, pulse * 0.38)
            for i in mini(_tail_bones.size(), 5):
                _set_bone_offset(_tail_bones[i], Vector3.UP, sin(progress * TAU) * 0.08)
        "threat":
            for i in mini(_head_bones.size(), 3):
                _set_bone_offset(_head_bones[i], Vector3.RIGHT, pulse * 0.11)
            for i in mini(_tail_bones.size(), 7):
                _set_bone_offset(_tail_bones[i], Vector3.UP, sin(progress * PI * 2.0) * 0.16)
        "bite":
            for bone_idx in _jaw_bones:
                _set_bone_offset(bone_idx, Vector3.RIGHT, pulse * 0.50)
            if not _head_bones.is_empty():
                _set_bone_offset(_head_bones[0], Vector3.RIGHT, pulse * 0.15)

func _set_bone_offset(bone_idx: int, axis: Vector3, angle: float) -> void:
    if _skeleton == null or not _bone_base_rotations.has(bone_idx):
        return
    var base := _bone_base_rotations[bone_idx] as Quaternion
    _skeleton.set_bone_pose_rotation(bone_idx, base * Quaternion(axis, angle))

func _restore_procedural_pose() -> void:
    if _skeleton == null:
        return
    for key in _bone_base_rotations.keys():
        var bone_idx := int(key)
        _skeleton.set_bone_pose_rotation(bone_idx, _bone_base_rotations[key] as Quaternion)

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
        "walk": return 0.78
        "threat": return 0.92
        "roar": return 0.98
        "bite": return 0.96
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
    if "walk" in _auto_cycle_actions:
        pool.append("walk")
    if pool.is_empty():
        return
    var next_action := str(pool.pick_random())
    if not play_action(next_action):
        play_idle()
        _queue_next_auto_action()

func _on_animation_finished(_clip: StringName) -> void:
    if _current_action == "walk":
        return
    if animation_player != null:
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

func _find_skeleton(root: Node) -> Skeleton3D:
    if root is Skeleton3D:
        return root
    for child in root.get_children():
        var found := _find_skeleton(child)
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
