class_name DinosaurController
extends Node

signal action_started(action_name: String)
signal asset_error(message: String)

var actor: Node3D
var animation_player: AnimationPlayer
var audio_player: AudioStreamPlayer3D
var action_map: Dictionary = {}
var _idle_candidates: Array[String] = []
var _auto_cycle_actions: Array[String] = []
var _auto_timer: Timer
var _is_busy := false

func _ready() -> void:
    _auto_timer = Timer.new()
    _auto_timer.one_shot = true
    _auto_timer.timeout.connect(_on_auto_timer_timeout)
    add_child(_auto_timer)

func attach(new_actor: Node3D, actions: Dictionary) -> void:
    actor = new_actor
    action_map = actions
    animation_player = _find_animation_player(actor)
    audio_player = _find_audio_player(actor)
    _idle_candidates = _to_string_array(action_map.get("idle", ["idle", "Idle", "breathing", "Idle_Breath", "Breathing"]))
    _auto_cycle_actions = []
    for candidate in ["look", "walk", "threat", "roar"]:
        if has_action(candidate):
            _auto_cycle_actions.append(candidate)
    play_idle()
    _queue_next_auto_action()

func play_idle() -> void:
    if animation_player == null:
        return
    var clip := _resolve_clip(_idle_candidates)
    if clip != StringName():
        _is_busy = false
        animation_player.play(clip, 0.25)

func play_action(action_name: String) -> bool:
    if animation_player == null:
        asset_error.emit("В модели отсутствует AnimationPlayer")
        return false
    var candidates := _to_string_array(action_map.get(action_name, [action_name]))
    var clip := _resolve_clip(candidates)
    if clip == StringName():
        return false
    _is_busy = true
    animation_player.play(clip, 0.2)
    action_started.emit(action_name)
    if not animation_player.animation_finished.is_connected(_on_animation_finished):
        animation_player.animation_finished.connect(_on_animation_finished, CONNECT_ONE_SHOT)
    return true

func has_action(action_name: String) -> bool:
    var candidates := _to_string_array(action_map.get(action_name, [action_name]))
    return _resolve_clip(candidates) != StringName()

func stop_all() -> void:
    if _auto_timer != null:
        _auto_timer.stop()
    _is_busy = false

func _queue_next_auto_action() -> void:
    if _auto_timer == null or _auto_cycle_actions.is_empty():
        return
    _auto_timer.start(randf_range(4.5, 9.0))

func _on_auto_timer_timeout() -> void:
    if _is_busy:
        _queue_next_auto_action()
        return
    if _auto_cycle_actions.is_empty():
        return
    var next_action := _auto_cycle_actions.pick_random()
    if not play_action(next_action):
        play_idle()
        _queue_next_auto_action()

func _on_animation_finished(_clip: StringName) -> void:
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

func _find_audio_player(root: Node) -> AudioStreamPlayer3D:
    if root is AudioStreamPlayer3D:
        return root
    for child in root.get_children():
        var found := _find_audio_player(child)
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
