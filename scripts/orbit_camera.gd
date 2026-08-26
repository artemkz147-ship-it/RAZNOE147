extends Node3D

@export var min_distance := 2.4
@export var max_distance := 16.0
@export var rotation_speed := 0.005
@export var zoom_speed := 0.012
@export var min_pitch_degrees := -20.0
@export var max_pitch_degrees := 58.0

@onready var pitch_node: Node3D = $CameraPitch
@onready var camera: Camera3D = $CameraPitch/Camera3D

var _yaw := 0.0
var _pitch := deg_to_rad(8.0)
var _distance := 7.2
var _touches: Dictionary = {}
var _last_pinch_distance := -1.0
var _mouse_dragging := false

func _ready() -> void:
    _apply_transform()

func focus_target(center: Vector3, radius: float) -> void:
    global_position = center
    _distance = clamp(radius * 2.35, min_distance, max_distance)
    _apply_transform()

func reset_view() -> void:
    _yaw = 0.0
    _pitch = deg_to_rad(8.0)
    _distance = 7.2
    _apply_transform()

func _unhandled_input(event: InputEvent) -> void:
    if event is InputEventScreenTouch:
        if event.pressed:
            _touches[event.index] = event.position
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
            var points := _touches.values()
            var pinch_distance: float = points[0].distance_to(points[1])
            if _last_pinch_distance > 0.0:
                _zoom((_last_pinch_distance - pinch_distance) * zoom_speed)
            _last_pinch_distance = pinch_distance
        get_viewport().set_input_as_handled()
        return

    if event is InputEventMouseButton:
        if event.button_index == MOUSE_BUTTON_LEFT:
            _mouse_dragging = event.pressed
        elif event.pressed and event.button_index == MOUSE_BUTTON_WHEEL_UP:
            _zoom(-0.55)
        elif event.pressed and event.button_index == MOUSE_BUTTON_WHEEL_DOWN:
            _zoom(0.55)

    if event is InputEventMouseMotion and _mouse_dragging:
        _orbit(event.relative)

func _orbit(delta: Vector2) -> void:
    _yaw -= delta.x * rotation_speed
    _pitch = clamp(_pitch - delta.y * rotation_speed, deg_to_rad(min_pitch_degrees), deg_to_rad(max_pitch_degrees))
    _apply_transform()

func _zoom(delta: float) -> void:
    _distance = clamp(_distance + delta, min_distance, max_distance)
    _apply_transform()

func _apply_transform() -> void:
    rotation.y = _yaw
    pitch_node.rotation.x = _pitch
    camera.position = Vector3(0.0, 1.65, _distance)
