from pathlib import Path
import re

# Apply the full v1.6 feature patch first.
exec(Path('wasteland/ci_patch_v16.py').read_text(), {})

game_path = Path('wasteland/scripts/game.gd')
game = game_path.read_text()

# Replace only the startup/menu portion with viewport-sized controls.
start = game.index('func _ready() -> void:')
end = game.index('\nfunc _refresh_menu()', start)

safe_menu = r'''func _ready() -> void:
    randomize()
    _setup_input()
    _make_materials()
    _build_main_menu()
    if OS.get_cmdline_user_args().has("--ci-race"):
        call_deferred("_start_selected_race")

func _menu_button(text: String, min_width: float = 120.0) -> Button:
    var b := Button.new()
    b.text = text
    b.custom_minimum_size = Vector2(min_width, 56)
    b.add_theme_font_size_override("font_size", 18)
    return b

func _build_main_menu() -> void:
    if is_instance_valid(menu_layer):
        menu_layer.queue_free()
    menu_layer = CanvasLayer.new()
    menu_layer.layer = 20
    add_child(menu_layer)

    var viewport_size: Vector2 = get_viewport().get_visible_rect().size
    if viewport_size.x < 640.0 or viewport_size.y < 360.0:
        viewport_size = Vector2(1280.0, 720.0)

    var root := Control.new()
    root.name = "MainMenuRoot"
    root.position = Vector2.ZERO
    root.size = viewport_size
    root.mouse_filter = Control.MOUSE_FILTER_PASS
    menu_layer.add_child(root)

    var bg := ColorRect.new()
    bg.position = Vector2.ZERO
    bg.size = viewport_size
    bg.color = Color("070b12")
    bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
    root.add_child(bg)

    var stripe := ColorRect.new()
    stripe.position = Vector2(0.0, 0.0)
    stripe.size = Vector2(viewport_size.x, 8.0)
    stripe.color = Color("ff6e2f")
    stripe.mouse_filter = Control.MOUSE_FILTER_IGNORE
    root.add_child(stripe)

    var title := Label.new()
    title.text = "WASTELAND CIRCUIT"
    title.position = Vector2(0.0, 38.0)
    title.size = Vector2(viewport_size.x, 68.0)
    title.add_theme_font_size_override("font_size", 48)
    title.add_theme_color_override("font_color", Color("ff8246"))
    title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    title.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    title.mouse_filter = Control.MOUSE_FILTER_IGNORE
    root.add_child(title)

    var sub := Label.new()
    sub.text = "АРКАДНАЯ БОЕВАЯ ГОНКА • СКОРОСТЬ ВАЖНЕЕ ОРУЖИЯ"
    sub.position = Vector2(0.0, 103.0)
    sub.size = Vector2(viewport_size.x, 34.0)
    sub.add_theme_font_size_override("font_size", 16)
    sub.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    sub.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    sub.mouse_filter = Control.MOUSE_FILTER_IGNORE
    root.add_child(sub)

    var panel := VBoxContainer.new()
    panel.name = "MenuPanel"
    panel.position = Vector2((viewport_size.x - 780.0) * 0.5, 146.0)
    panel.size = Vector2(780.0, minf(500.0, viewport_size.y - 164.0))
    panel.add_theme_constant_override("separation", 13)
    root.add_child(panel)

    var car_title := Label.new()
    car_title.text = "МАШИНА"
    car_title.add_theme_font_size_override("font_size", 18)
    car_title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    panel.add_child(car_title)

    var car_row := HBoxContainer.new()
    car_row.alignment = BoxContainer.ALIGNMENT_CENTER
    car_row.add_theme_constant_override("separation", 14)
    panel.add_child(car_row)
    var car_prev := _menu_button("◀", 80)
    car_row.add_child(car_prev)
    menu_car_label = Label.new()
    menu_car_label.custom_minimum_size = Vector2(420, 58)
    menu_car_label.add_theme_font_size_override("font_size", 29)
    menu_car_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    menu_car_label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    car_row.add_child(menu_car_label)
    var car_next := _menu_button("▶", 80)
    car_row.add_child(car_next)
    car_prev.pressed.connect(func(): selected_car = posmodi(selected_car - 1, 8); _refresh_menu())
    car_next.pressed.connect(func(): selected_car = posmodi(selected_car + 1, 8); _refresh_menu())

    menu_stats_label = Label.new()
    menu_stats_label.add_theme_font_size_override("font_size", 16)
    menu_stats_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    menu_stats_label.custom_minimum_size = Vector2(720, 44)
    panel.add_child(menu_stats_label)

    var track_title := Label.new()
    track_title.text = "ТРАССА"
    track_title.add_theme_font_size_override("font_size", 18)
    track_title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    panel.add_child(track_title)

    var track_row := HBoxContainer.new()
    track_row.alignment = BoxContainer.ALIGNMENT_CENTER
    track_row.add_theme_constant_override("separation", 14)
    panel.add_child(track_row)
    var track_prev := _menu_button("◀", 80)
    track_row.add_child(track_prev)
    menu_track_label = Label.new()
    menu_track_label.custom_minimum_size = Vector2(420, 54)
    menu_track_label.add_theme_font_size_override("font_size", 25)
    menu_track_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    menu_track_label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
    track_row.add_child(menu_track_label)
    var track_next := _menu_button("▶", 80)
    track_row.add_child(track_next)
    track_prev.pressed.connect(func(): selected_track = posmodi(selected_track - 1, 3); _refresh_menu())
    track_next.pressed.connect(func(): selected_track = posmodi(selected_track + 1, 3); _refresh_menu())

    var center := HBoxContainer.new()
    center.alignment = BoxContainer.ALIGNMENT_CENTER
    panel.add_child(center)
    var start_btn := _menu_button("СТАРТ ГОНКИ", 420)
    start_btn.add_theme_font_size_override("font_size", 24)
    start_btn.pressed.connect(_start_selected_race)
    center.add_child(start_btn)

    var note := Label.new()
    note.text = "Оружие только из ящиков • Дрифт I/II/III • Потеря колёс влияет на управление"
    note.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    note.add_theme_font_size_override("font_size", 14)
    panel.add_child(note)

    _refresh_menu()
'''

game = game[:start] + safe_menu + game[end:]

game_path.write_text(game)

# Patch version/package so this can be installed alongside the broken v1.6.0.
preset = Path('wasteland/export_presets.cfg')
p = preset.read_text()
p = re.sub(r'package/unique_name="[^"]+"', 'package/unique_name="ru.openai148.wastelandcircuit.race161"', p)
p = re.sub(r'version/code=\d+', 'version/code=161', p)
p = re.sub(r'version/name="[^"]+"', 'version/name="1.6.1"', p)
p = re.sub(r'export_path="[^"]+"', 'export_path="builds/Wasteland-Circuit-Race-v1.6.1-fixed.apk"', p)
preset.write_text(p)
