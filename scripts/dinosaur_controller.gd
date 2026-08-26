class_name DinosaurController
extends Node

signal action_started(action_name: String)
signal asset_error(message: String)

var actor: Node3D
var animation_player: AnimationPlayer
var action_map: Dictionary = {}
var _idle_candidates: Array[String] = []
var _auto_cycle_actions: Array[String] = []
var _auto_timer: Timer
var _is_busy := false
var _current_action := "idle"

func _ready() -> void:
    _auto_timer = Timer.new()
    _auto_timer.one_shot = true
    _auto_timer.timeout.connect(_on_auto_timer_timeout)
    add_child(_auto_timer)

func attach(new_actor: Node3D, actions: Dictionary) -> void:
    actor = new_actor
    action_map = actions
    animation_player = _find_animation_player(actor)
    _idle_candidates = _to_string_array(action_map.get("idle", ["idle", "Idle"]))
    _auto_cycle_actions.clear()
    for candidate in ["roar", "bite", "threat", "walk"]:
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
    var clip: StringName = _resolve_clip(_idle_candidates)
    if clip == StringName():
        return
    _is_busy = false
    _current_action = "idle"
    animation_player.speed_scale = 1.0
    var anim: Animation = animation_player.get_animation(clip)
    if anim != null:
        anim.loop_mode = Animation.LOOP_LINEAR
    animation_player.play(clip, 0.22)

func play_action(action_name: String) -> bool:
    if animation_player == null:
        return false
    var candidates: Array[String] = _to_string_array(action_map.get(action_name, [action_name]))
    var clip: StringName = _resolve_clip(candidates)
    if clip == StringName():
        return false
    _is_busy = true
    _current_action = action_name
    animation_player.speed_scale = _speed_for_action(action_name)
    animation_player.play(clip, 0.20)
    action_started.emit(action_name)
    if not animation_player.animation_finished.is_connected(_on_animation_finished):
        animation_player.animation_finished.connect(_on_animation_finished, CONNECT_ONE_SHOT)
    return true

func has_action(action_name: String) -> bool:
    if animation_player == null:
        return false
    var candidates: Array[String] = _to_string_array(action_map.get(action_name, [action_name]))
    return _resolve_clip(candidates) != StringName()

func stop_all() -> void:
    if _auto_timer != null:
        _auto_timer.stop()
    if animation_player != null:
        animation_player.stop()
    _is_busy = false

func _speed_for_action(action_name: String) -> float:
    match action_name:
        "walk": return 0.48
        "threat": return 0.82
        "roar": return 0.92
        "bite": return 0.86
        _: return 1.0

func _queue_next_auto_action() -> void:
    if _auto_timer == null or _auto_cycle_actions.is_empty():
        return
    _auto_timer.start(randf_range(3.2, 6.4))

func _on_auto_timer_timeout() -> void:
    if _is_busy:
        _queue_next_auto_action()
        return
    if _auto_cycle_actions.is_empty():
        return
    var pool: Array[String] = _auto_cycle_actions.duplicate()
    if has_action("walk"):
        pool.append("walk")
        pool.append("walk")
    var next_action: String = str(pool.pick_random())
    if not play_action(next_action):
        play_idle()
        _queue_next_auto_action()

func _on_animation_finished(_clip: StringName) -> void:
    animation_player.speed_scale = 1.0
    play_idle()
    _queue_next_auto_action()

func _resolve_clip(candidates: Array[String]) -> StringName:
    if animation_player == null:
        return StringName()
    var clips: PackedStringArray = animation_player.get_animation_list()
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
        var found: AnimationPlayer = _find_animation_player(child)
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
