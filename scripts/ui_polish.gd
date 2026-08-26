extends CanvasLayer

var _glass := Color(0.035, 0.050, 0.045, 0.78)
var _glass_hover := Color(0.075, 0.105, 0.085, 0.92)
var _glass_pressed := Color(0.030, 0.085, 0.060, 0.96)
var _accent := Color(0.77, 0.88, 0.68, 1.0)

func _ready() -> void:
    call_deferred("_polish")

func _polish() -> void:
    _add_cinematic_vignette()
    for child in get_children():
        _style_recursive(child)

func _style_recursive(node: Node) -> void:
    if node is Button:
        _style_button(node as Button)
    elif node is PanelContainer:
        _style_panel(node as PanelContainer)
    elif node is RichTextLabel:
        var rich := node as RichTextLabel
        rich.add_theme_color_override("default_color", Color(0.93, 0.95, 0.91))
        rich.add_theme_color_override("font_outline_color", Color(0, 0, 0, 0.35))
        rich.add_theme_constant_override("outline_size", 1)
    elif node is Label:
        var label := node as Label
        label.add_theme_color_override("font_outline_color", Color(0, 0, 0, 0.62))
        label.add_theme_constant_override("outline_size", 4 if label.get_theme_font_size("font_size") >= 24 else 2)

    for child in node.get_children():
        _style_recursive(child)

func _style_button(button: Button) -> void:
    button.add_theme_font_size_override("font_size", 16)
    button.add_theme_color_override("font_color", Color(0.93, 0.96, 0.91))
    button.add_theme_color_override("font_hover_color", Color.WHITE)
    button.add_theme_color_override("font_pressed_color", _accent)
    button.add_theme_color_override("font_disabled_color", Color(0.58, 0.62, 0.57, 0.55))
    button.add_theme_stylebox_override("normal", _button_box(_glass, Color(1, 1, 1, 0.12), 1))
    button.add_theme_stylebox_override("hover", _button_box(_glass_hover, Color(_accent.r, _accent.g, _accent.b, 0.42), 1))
    button.add_theme_stylebox_override("pressed", _button_box(_glass_pressed, Color(_accent.r, _accent.g, _accent.b, 0.68), 1))
    button.add_theme_stylebox_override("disabled", _button_box(Color(0.025, 0.032, 0.030, 0.48), Color(1, 1, 1, 0.05), 1))
    button.add_theme_stylebox_override("focus", StyleBoxEmpty.new())

func _style_panel(panel: PanelContainer) -> void:
    var box := StyleBoxFlat.new()
    box.bg_color = Color(0.025, 0.035, 0.032, 0.91)
    box.border_color = Color(0.75, 0.86, 0.67, 0.16)
    box.set_border_width_all(1)
    box.corner_radius_top_left = 18
    box.corner_radius_top_right = 18
    box.corner_radius_bottom_left = 18
    box.corner_radius_bottom_right = 18
    box.shadow_color = Color(0, 0, 0, 0.42)
    box.shadow_size = 16
    box.shadow_offset = Vector2(0, 7)
    panel.add_theme_stylebox_override("panel", box)

func _button_box(background: Color, border: Color, border_width: int) -> StyleBoxFlat:
    var box := StyleBoxFlat.new()
    box.bg_color = background
    box.border_color = border
    box.set_border_width_all(border_width)
    box.corner_radius_top_left = 14
    box.corner_radius_top_right = 14
    box.corner_radius_bottom_left = 14
    box.corner_radius_bottom_right = 14
    box.content_margin_left = 16
    box.content_margin_right = 16
    box.content_margin_top = 9
    box.content_margin_bottom = 9
    return box

func _add_cinematic_vignette() -> void:
    var overlay := ColorRect.new()
    overlay.name = "CinematicVignette"
    overlay.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    overlay.mouse_filter = Control.MOUSE_FILTER_IGNORE
    overlay.z_index = -50

    var shader := Shader.new()
    shader.code = """
shader_type canvas_item;
render_mode unshaded;
void fragment(){
    vec2 p = UV * 2.0 - 1.0;
    float edge = smoothstep(0.45, 1.25, length(p * vec2(0.82, 1.0)));
    float top = smoothstep(0.56, 0.0, UV.y);
    float bottom = smoothstep(0.68, 1.0, UV.y);
    float a = clamp(edge * 0.40 + top * 0.13 + bottom * 0.20, 0.0, 0.56);
    COLOR = vec4(0.005, 0.012, 0.010, a);
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    overlay.material = material
    add_child(overlay)
    move_child(overlay, 0)
