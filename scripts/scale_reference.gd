extends Node3D

var _reference: Node3D
var _button: Button
var _visible := false

func _ready() -> void:
    _build_reference()
    call_deferred("_install_button")

func _build_reference() -> void:
    _reference = Node3D.new()
    _reference.name = "HumanReference180cm"
    _reference.visible = false
    add_child(_reference)

    var material := StandardMaterial3D.new()
    material.albedo_color = Color(0.76, 0.78, 0.72)
    material.roughness = 0.86
    material.metallic = 0.0

    _add_capsule(Vector3(0.0, 1.18, 0.0), 0.24, 0.72, material)
    _add_sphere(Vector3(0.0, 1.67, 0.0), 0.14, material)
    _add_capsule(Vector3(-0.11, 0.48, 0.0), 0.085, 0.78, material)
    _add_capsule(Vector3(0.11, 0.48, 0.0), 0.085, 0.78, material)
    _add_capsule(Vector3(-0.30, 1.10, 0.0), 0.065, 0.66, material, deg_to_rad(-8.0))
    _add_capsule(Vector3(0.30, 1.10, 0.0), 0.065, 0.66, material, deg_to_rad(8.0))

    # Stand a few metres beside the animal, not inside the orbit focus.
    _reference.position = Vector3(3.2, 0.0, 0.3)

func _add_capsule(position_value: Vector3, radius: float, height: float, material: Material, z_rotation := 0.0) -> void:
    var mesh_instance := MeshInstance3D.new()
    var mesh := CapsuleMesh.new()
    mesh.radius = radius
    mesh.height = maxf(height, radius * 2.05)
    mesh.radial_segments = 12
    mesh.rings = 4
    mesh.material = material
    mesh_instance.mesh = mesh
    mesh_instance.position = position_value
    mesh_instance.rotation.z = z_rotation
    mesh_instance.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON
    _reference.add_child(mesh_instance)

func _add_sphere(position_value: Vector3, radius: float, material: Material) -> void:
    var mesh_instance := MeshInstance3D.new()
    var mesh := SphereMesh.new()
    mesh.radius = radius
    mesh.height = radius * 2.0
    mesh.radial_segments = 12
    mesh.rings = 6
    mesh.material = material
    mesh_instance.mesh = mesh
    mesh_instance.position = position_value
    mesh_instance.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON
    _reference.add_child(mesh_instance)

func _install_button() -> void:
    var ui := get_parent().get_node_or_null("UI")
    if ui == null:
        return
    var root_control: Control = null
    for child in ui.get_children():
        if child is Control and child.name != "CinematicVignette":
            root_control = child as Control
            break
    if root_control == null:
        return

    _button = Button.new()
    _button.text = "Масштаб 1,8 м"
    _button.tooltip_text = "Показать человека ростом 1,8 м для сравнения"
    _button.custom_minimum_size = Vector2(150, 44)
    _button.set_anchors_preset(Control.PRESET_TOP_RIGHT)
    _button.offset_left = -178
    _button.offset_top = 80
    _button.offset_right = -24
    _button.offset_bottom = 126
    _button.pressed.connect(_toggle_reference)
    root_control.add_child(_button)

func _toggle_reference() -> void:
    _visible = not _visible
    _reference.visible = _visible
    if _button != null:
        _button.text = "Убрать масштаб" if _visible else "Масштаб 1,8 м"
