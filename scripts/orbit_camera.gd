extends Node3D

@export var min_distance := 0.85
@export var max_distance := 36.0
@export var rotation_speed := 0.005
@export var zoom_speed := 0.012
@export var pan_speed := 0.018
@export var min_pitch_degrees := -78.0
@export var max_pitch_degrees := 0.0
@export var smoothing_speed := 9.0
@export var meadow_half_extent := 24.0

@onready var pitch_node: Node3D = $CameraPitch
@onready var camera: Camera3D = $CameraPitch/Camera3D

var _yaw := deg_to_rad(-24.0)
var _pitch := deg_to_rad(-14.0)
var _distance := 8.0
var _target_yaw := deg_to_rad(-24.0)
var _target_pitch := deg_to_rad(-14.0)
var _target_distance := 8.0
var _default_distance := 8.0
var _default_focus := Vector3.ZERO
var _default_focus_height := 1.2
var _focus_center := Vector3.ZERO
var _focus_height := 1.2
var _target_focus_center := Vector3.ZERO
var _target_focus_height := 1.2
var _touches: Dictionary = {}
var _last_pinch_distance := -1.0
var _last_pan_center := Vector2.ZERO
var _mouse_dragging := false
var _mouse_panning := false
var _tap_time_msec := 0

func _ready() -> void:
    _apply_immediate()

func _process(delta: float) -> void:
    var t := clampf(delta * smoothing_speed, 0.0, 1.0)
    _yaw = lerp_angle(_yaw, _target_yaw, t)
    _pitch = lerpf(_pitch, _target_pitch, t)
    _distance = lerpf(_distance, _target_distance, t)
    _focus_center = _focus_center.lerp(_target_focus_center, t)
    _focus_height = lerpf(_focus_height, _target_focus_height, t)
    _apply_transform()

func focus_target(center: Vector3, radius: float) -> void:
    var safe_radius := maxf(radius, 0.22)
    _default_focus = _clamp_focus(center)
    _default_focus_height = clampf(safe_radius * 0.42, 0.24, 4.8)
    # Small species need a deliberately closer camera. Large species still use the
    # same formula so switching never inherits the previous dinosaur's distance.
    _default_distance = clampf(safe_radius * 1.85 + 0.45, 1.10, max_distance)
    _target_focus_center = _default_focus
    _target_focus_height = _default_focus_height
    _target_distance = _default_distance
    _target_yaw = deg_to_rad(-28.0)
    _target_pitch = deg_to_rad(-12.0 if safe_radius < 1.4 else -16.0)
    camera.fov = 37.0 if safe_radius < 1.2 else (42.0 if safe_radius < 3.0 else 47.0)
    _apply_immediate()

func reset_view() -> void:
    _target_focus_center = _default_focus
    _target_focus_height = _default_focus_height
    _target_distance = _default_distance
    _target_yaw = deg_to_rad(-28.0)
    _target_pitch = deg_to_rad(-14.0)
    _apply_immediate()

func set_top_view() -> void:
    _target_pitch = deg_to_rad(-72.0)
    _target_distance = clampf(maxf(_default_distance * 1.35, 4.0), min_distance, max_distance)

func set_side_view() -> void:
    _target_pitch = deg_to_rad(-14.0)
    _target_distance = _default_distance

func _unhandled_input(event: InputEvent) -> void:
    if event is InputEventScreenTouch:
        if event.pressed:
            _touches[event.index] = event.position
            var now := Time.get_ticks_msec()
            if _touches.size() == 1 and now - _tap_time_msec < 260:
                _toggle_top_view()
            _tap_time_msec = now
            if _touches.size() >= 2:
                var pts: Array = _touches.values()
                var a := pts[0] as Vector2
                var b := pts[1] as Vector2
                _last_pinch_distance = a.distance_to(b)
                _last_pan_center = (a + b) * 0.5
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
            var pts: Array = _touches.values()
            var a := pts[0] as Vector2
            var b := pts[1] as Vector2
            var pinch_distance := a.distance_to(b)
            var pan_center := (a + b) * 0.5
            if _last_pinch_distance > 0.0:
                _zoom((_last_pinch_distance - pinch_distance) * zoom_speed)
                _pan(pan_center - _last_pan_center)
            _last_pinch_distance = pinch_distance
            _last_pan_center = pan_center
        get_viewport().set_input_as_handled()
        return

    if event is InputEventMouseButton:
        if event.button_index == MOUSE_BUTTON_LEFT:
            _mouse_dragging = event.pressed
            if event.double_click and event.pressed:
                _toggle_top_view()
        elif event.button_index == MOUSE_BUTTON_RIGHT or event.button_index == MOUSE_BUTTON_MIDDLE:
            _mouse_panning = event.pressed
        elif event.pressed and event.button_index == MOUSE_BUTTON_WHEEL_UP:
            _zoom(-0.75)
        elif event.pressed and event.button_index == MOUSE_BUTTON_WHEEL_DOWN:
            _zoom(0.75)

    if event is InputEventMouseMotion:
        if _mouse_dragging:
            _orbit(event.relative)
        elif _mouse_panning:
            _pan(event.relative)

func _toggle_top_view() -> void:
    if rad_to_deg(_target_pitch) < -55.0:
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

func _pan(delta: Vector2) -> void:
    var yaw_basis := Basis(Vector3.UP, _target_yaw)
    var right := yaw_basis.x.normalized()
    var forward := (-yaw_basis.z).normalized()
    var scale := pan_speed * clampf(_target_distance / 8.0, 0.55, 2.0)
    _target_focus_center += (-right * delta.x + forward * delta.y) * scale
    _target_focus_center = _clamp_focus(_target_focus_center)

func _zoom(delta: float) -> void:
    _target_distance = clampf(_target_distance + delta, min_distance, max_distance)

func _clamp_focus(value: Vector3) -> Vector3:
    return Vector3(
        clampf(value.x, -meadow_half_extent, meadow_half_extent),
        maxf(value.y, 0.0),
        clampf(value.z, -meadow_half_extent, meadow_half_extent)
    )

func _apply_immediate() -> void:
    _focus_center = _target_focus_center
    _focus_height = _target_focus_height
    _yaw = _target_yaw
    _pitch = _target_pitch
    _distance = _target_distance
    _apply_transform()

func _apply_transform() -> void:
    global_position = _clamp_focus(_focus_center)
    rotation.y = _yaw
    pitch_node.rotation.x = _pitch
    camera.position = Vector3(0.0, _focus_height, _distance)
