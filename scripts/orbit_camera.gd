extends Node3D

@export var min_distance := 2.0
@export var max_distance := 34.0
@export var rotation_speed := 0.005
@export var zoom_speed := 0.012
@export var min_pitch_degrees := -60.0
@export var max_pitch_degrees := 89.0
@export var smoothing_speed := 8.0

@onready var pitch_node: Node3D = $CameraPitch
@onready var camera: Camera3D = $CameraPitch/Camera3D

var _yaw := 0.0
var _pitch := deg_to_rad(12.0)
var _distance := 8.0
var _target_yaw := 0.0
var _target_pitch := deg_to_rad(12.0)
var _target_distance := 8.0
var _focus_center := Vector3.ZERO
var _focus_height := 1.65
var _target_focus_center := Vector3.ZERO
var _target_focus_height := 1.65
var _touches: Dictionary = {}
var _last_pinch_distance := -1.0
var _mouse_dragging := false
var _tap_time_msec := 0

func _ready() -> void:
    _apply_immediate()

func _process(delta: float) -> void:
    var t: float = clampf(delta * smoothing_speed, 0.0, 1.0)
    _yaw = lerp_angle(_yaw, _target_yaw, t)
    _pitch = lerpf(_pitch, _target_pitch, t)
    _distance = lerpf(_distance, _target_distance, t)
    _focus_center = _focus_center.lerp(_target_focus_center, t)
    _focus_height = lerpf(_focus_height, _target_focus_height, t)
    _apply_transform()

func focus_target(center: Vector3, radius: float) -> void:
    _target_focus_center = center
    _target_focus_height = clampf(radius * 0.35, 1.2, 5.5)
    _target_distance = clampf(radius * 2.4, min_distance, max_distance)

func reset_view() -> void:
    _target_yaw = 0.0
    _target_pitch = deg_to_rad(12.0)
    _target_distance = 8.0

func set_top_view() -> void:
    _target_pitch = deg_to_rad(86.0)

func set_side_view() -> void:
    _target_pitch = deg_to_rad(12.0)

func _unhandled_input(event: InputEvent) -> void:
    if event is InputEventScreenTouch:
        if event.pressed:
            _touches[event.index] = event.position
            var now: int = Time.get_ticks_msec()
            if _touches.size() == 1 and now - _tap_time_msec < 260:
                _toggle_top_view()
            _tap_time_msec = now
        else:
            _touches.erase(event.index)
            _last_pinch_distance = -1.0
        get_viewport().set_input_as_handled()
        return

    if event is InputEventScreenDrag:
        _touches[event.index] = event.position
        if _touches.size() == 1:
            _orbit(event.relative)
        elif _touches.size() >= 2:
            var points: Array = _touches.values()
            var point_a: Vector2 = points[0] as Vector2
            var point_b: Vector2 = points[1] as Vector2
            var pinch_distance: float = point_a.distance_to(point_b)
            if _last_pinch_distance > 0.0:
                _zoom((_last_pinch_distance - pinch_distance) * zoom_speed)
            _last_pinch_distance = pinch_distance
        get_viewport().set_input_as_handled()
        return

    if event is InputEventMouseButton:
        if event.button_index == MOUSE_BUTTON_LEFT:
            _mouse_dragging = event.pressed
            if event.double_click and event.pressed:
                _toggle_top_view()
        elif event.pressed and event.button_index == MOUSE_BUTTON_WHEEL_UP:
            _zoom(-0.75)
        elif event.pressed and event.button_index == MOUSE_BUTTON_WHEEL_DOWN:
            _zoom(0.75)

    if event is InputEventMouseMotion and _mouse_dragging:
        _orbit(event.relative)

func _toggle_top_view() -> void:
    if rad_to_deg(_target_pitch) > 60.0:
        set_side_view()
    else:
        set_top_view()

func _orbit(delta: Vector2) -> void:
    _target_yaw -= delta.x * rotation_speed
    _target_pitch = clampf(
        _target_pitch - delta.y * rotation_speed,
        deg_to_rad(min_pitch_degrees),
        deg_to_rad(max_pitch_degrees)
    )

func _zoom(delta: float) -> void:
    _target_distance = clampf(_target_distance + delta, min_distance, max_distance)

func _apply_immediate() -> void:
    _focus_center = _target_focus_center
    _focus_height = _target_focus_height
    _yaw = _target_yaw
    _pitch = _target_pitch
    _distance = _target_distance
    _apply_transform()

func _apply_transform() -> void:
    global_position = _focus_center
    rotation.y = _yaw
    pitch_node.rotation.x = _pitch
    camera.position = Vector3(0.0, _focus_height, _distance)
