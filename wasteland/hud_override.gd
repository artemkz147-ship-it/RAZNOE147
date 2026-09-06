extends Control

var player
var hp_label: Label
var hp_bar: ProgressBar
var lap_label: Label
var position_label: Label
var rockets_label: Label
var mines_label: Label
var speed_label: Label
var nitro_label: Label
var nitro_bar: ProgressBar
var drift_label: Label
var drift_bar: ProgressBar
var special_label: Label
var event_label: Label
var objective_label: Label
var timer_label: Label
var message_label: Label
var streak_label: Label
var end_panel: PanelContainer
var end_label: Label
var damage_overlay: ColorRect

func _ready() -> void:
    set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    mouse_filter = Control.MOUSE_FILTER_PASS
    _build_hud()

func set_player(p) -> void:
    player = p

func _build_hud() -> void:
    var pos_panel := PanelContainer.new()
    pos_panel.position = Vector2(14, 14)
    pos_panel.size = Vector2(182, 120)
    pos_panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
    pos_panel.add_theme_stylebox_override("panel", _panel_style(Color(0.015, 0.020, 0.030, 0.90), Color("ff7a35"), 15, 2))
    add_child(pos_panel)
    var pv := VBoxContainer.new(); pv.alignment = BoxContainer.ALIGNMENT_CENTER; pos_panel.add_child(pv)
    position_label = _label("1/6", 52); position_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER; position_label.add_theme_color_override("font_color", Color("ff9a52")); pv.add_child(position_label)
    lap_label = _label("КРУГ 0/3", 18); lap_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER; pv.add_child(lap_label)

    var left_panel := PanelContainer.new()
    left_panel.position = Vector2(14, 142)
    left_panel.size = Vector2(350, 116)
    left_panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
    left_panel.add_theme_stylebox_override("panel", _panel_style(Color(0.015, 0.020, 0.030, 0.86), Color("5e6875"), 12, 1))
    add_child(left_panel)
    var lv := VBoxContainer.new(); lv.add_theme_constant_override("separation", 5); left_panel.add_child(lv)
    hp_label = _label("БРОНЯ 100/100", 17); lv.add_child(hp_label)
    hp_bar = _bar(Color("ff573d"), 320, 12); lv.add_child(hp_bar)
    var ammo := HBoxContainer.new(); ammo.add_theme_constant_override("separation", 16); lv.add_child(ammo)
    rockets_label = _label("РАКЕТЫ 1", 15); mines_label = _label("МИНЫ 1", 15); ammo.add_child(rockets_label); ammo.add_child(mines_label)

    var right_panel := PanelContainer.new()
    right_panel.set_anchors_preset(Control.PRESET_TOP_RIGHT)
    right_panel.position = Vector2(-414, 14)
    right_panel.size = Vector2(400, 172)
    right_panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
    right_panel.add_theme_stylebox_override("panel", _panel_style(Color(0.015, 0.020, 0.030, 0.88), Color("5e6875"), 12, 1))
    add_child(right_panel)
    var rv := VBoxContainer.new(); rv.add_theme_constant_override("separation", 3); right_panel.add_child(rv)
    event_label = _label("RUST CIRCUIT", 21); event_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT; event_label.add_theme_color_override("font_color", Color("ff9458")); rv.add_child(event_label)
    objective_label = _label("ГОНКА", 14); objective_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT; rv.add_child(objective_label)
    var sr := HBoxContainer.new(); sr.alignment = BoxContainer.ALIGNMENT_END; rv.add_child(sr)
    timer_label = _label("0.0 С", 16); timer_label.add_theme_color_override("font_color", Color("a9cfff")); sr.add_child(timer_label)
    speed_label = _label("0 КМ/Ч", 34); speed_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT; sr.add_child(speed_label)
    nitro_label = _label("НИТРО 100%", 14); nitro_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT; rv.add_child(nitro_label)
    nitro_bar = _bar(Color("39c9ff"), 360, 10); rv.add_child(nitro_bar)
    special_label = _label("СПЕЦ: ГОТОВА", 14); special_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT; special_label.add_theme_color_override("font_color", Color("ffd760")); rv.add_child(special_label)

    var drift_panel := PanelContainer.new()
    drift_panel.set_anchors_preset(Control.PRESET_CENTER_BOTTOM)
    drift_panel.position = Vector2(-190, -104)
    drift_panel.size = Vector2(380, 72)
    drift_panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
    drift_panel.add_theme_stylebox_override("panel", _panel_style(Color(0.015, 0.020, 0.030, 0.72), Color("3fcaff"), 12, 1))
    add_child(drift_panel)
    var dv := VBoxContainer.new(); drift_panel.add_child(dv)
    drift_label = _label("ДРИФТ-БУСТ", 14); drift_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER; dv.add_child(drift_label)
    drift_bar = _bar(Color("48d9ff"), 350, 10); drift_bar.value = 0.0; dv.add_child(drift_bar)

    message_label = _label("", 40)
    message_label.set_anchors_preset(Control.PRESET_CENTER_TOP)
    message_label.position = Vector2(-340, 112)
    message_label.size = Vector2(680, 72)
    message_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    add_child(message_label)

    streak_label = _label("", 22)
    streak_label.set_anchors_preset(Control.PRESET_CENTER_TOP)
    streak_label.position = Vector2(-260, 175)
    streak_label.size = Vector2(520, 38)
    streak_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    streak_label.add_theme_color_override("font_color", Color("ffd15a"))
    add_child(streak_label)

    _hold("◀", Vector2(16, -112), Control.PRESET_BOTTOM_LEFT, "left", 92, Color("4abfff"))
    _hold("▶", Vector2(114, -112), Control.PRESET_BOTTOM_LEFT, "right", 92, Color("4abfff"))
    _hold("ДРИФТ", Vector2(66, -207), Control.PRESET_BOTTOM_LEFT, "drift", 112, Color("44d9ff"))

    _hold("ГАЗ", Vector2(-108, -112), Control.PRESET_BOTTOM_RIGHT, "go", 88, Color("ff8244"))
    _hold("ТОРМ", Vector2(-202, -112), Control.PRESET_BOTTOM_RIGHT, "back", 88, Color("ff8244"))
    _hold("НИТРО", Vector2(-296, -112), Control.PRESET_BOTTOM_RIGHT, "nitro", 88, Color("41cfff"))
    _hold("ОГОНЬ", Vector2(-108, -207), Control.PRESET_BOTTOM_RIGHT, "fire", 88, Color("ff6840"))
    _tap("РАКЕТА", Vector2(-202, -207), Control.PRESET_BOTTOM_RIGHT, "rocket", 88, Color("ffb03d"))
    _tap("МИНА", Vector2(-296, -207), Control.PRESET_BOTTOM_RIGHT, "mine", 88, Color("d98cff"))
    _tap("СПЕЦ", Vector2(-390, -207), Control.PRESET_BOTTOM_RIGHT, "special", 88, Color("ffe15a"))

    var hint := _label("Shift — дрифт • Ctrl — нитро • Space — пулемёт • E — ракета • Q — мина", 12)
    hint.set_anchors_preset(Control.PRESET_BOTTOM_WIDE); hint.position = Vector2(0, -24); hint.size = Vector2(0, 20); hint.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER; hint.modulate.a = 0.45; hint.mouse_filter = Control.MOUSE_FILTER_IGNORE; add_child(hint)

    damage_overlay = ColorRect.new(); damage_overlay.color = Color(0.72, 0.02, 0.01, 0.0); damage_overlay.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT); damage_overlay.mouse_filter = Control.MOUSE_FILTER_IGNORE; add_child(damage_overlay)

    end_panel = PanelContainer.new(); end_panel.set_anchors_preset(Control.PRESET_CENTER); end_panel.position = Vector2(-300, -155); end_panel.size = Vector2(600, 310); end_panel.visible = false; end_panel.add_theme_stylebox_override("panel", _panel_style(Color(0.018, 0.025, 0.038, 0.98), Color("ff6b2c"), 16, 2)); add_child(end_panel)
    var ev := VBoxContainer.new(); ev.alignment = BoxContainer.ALIGNMENT_CENTER; ev.add_theme_constant_override("separation", 14); end_panel.add_child(ev)
    end_label = _label("", 27); end_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER; end_label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER; end_label.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART; end_label.custom_minimum_size = Vector2(520, 205); ev.add_child(end_label)
    var restart := Button.new(); restart.text = "НОВЫЙ ЗАЕЗД"; restart.custom_minimum_size = Vector2(390, 58); restart.add_theme_font_size_override("font_size", 19); _style_button(restart, Color("ff7132")); restart.pressed.connect(_restart); ev.add_child(restart)

func _panel_style(bg: Color, border: Color, radius: int, width: int) -> StyleBoxFlat:
    var s := StyleBoxFlat.new(); s.bg_color = bg; s.border_width_left = width; s.border_width_top = width; s.border_width_right = width; s.border_width_bottom = width; s.border_color = border; s.corner_radius_top_left = radius; s.corner_radius_top_right = radius; s.corner_radius_bottom_left = radius; s.corner_radius_bottom_right = radius; s.content_margin_left = 12; s.content_margin_right = 12; s.content_margin_top = 8; s.content_margin_bottom = 8; return s

func _bar(color: Color, width: float, height: float) -> ProgressBar:
    var b := ProgressBar.new(); b.min_value = 0.0; b.max_value = 100.0; b.value = 100.0; b.show_percentage = false; b.custom_minimum_size = Vector2(width, height)
    var bg := StyleBoxFlat.new(); bg.bg_color = Color(0.07, 0.08, 0.11, 0.95); bg.corner_radius_top_left = int(height * 0.5); bg.corner_radius_top_right = int(height * 0.5); bg.corner_radius_bottom_left = int(height * 0.5); bg.corner_radius_bottom_right = int(height * 0.5)
    var fill := StyleBoxFlat.new(); fill.bg_color = color; fill.corner_radius_top_left = int(height * 0.5); fill.corner_radius_top_right = int(height * 0.5); fill.corner_radius_bottom_left = int(height * 0.5); fill.corner_radius_bottom_right = int(height * 0.5)
    b.add_theme_stylebox_override("background", bg); b.add_theme_stylebox_override("fill", fill); return b

func _label(text: String, size_px: int) -> Label:
    var l := Label.new(); l.text = text; l.add_theme_font_size_override("font_size", size_px); l.add_theme_color_override("font_color", Color("fff3e7")); l.add_theme_color_override("font_shadow_color", Color(0,0,0,0.92)); l.add_theme_constant_override("shadow_offset_x", 2); l.add_theme_constant_override("shadow_offset_y", 2); return l

func _style_button(b: Button, accent: Color) -> void:
    var n := StyleBoxFlat.new(); n.bg_color = Color(0.03, 0.04, 0.06, 0.82); n.border_width_left = 2; n.border_width_top = 2; n.border_width_right = 2; n.border_width_bottom = 2; n.border_color = _alpha(accent, 0.65); n.corner_radius_top_left = 11; n.corner_radius_top_right = 11; n.corner_radius_bottom_left = 11; n.corner_radius_bottom_right = 11
    var p := n.duplicate(); p.bg_color = _alpha(accent, 0.34); p.border_color = accent
    var h := n.duplicate(); h.bg_color = _alpha(accent, 0.18)
    b.add_theme_stylebox_override("normal", n); b.add_theme_stylebox_override("pressed", p); b.add_theme_stylebox_override("hover", h); b.add_theme_stylebox_override("focus", h)

func _hold(text: String, pos: Vector2, preset, action: String, side: float, accent: Color) -> void:
    var b := Button.new(); b.text = text; b.custom_minimum_size = Vector2(side, 82); b.set_anchors_preset(preset); b.position = pos; b.add_theme_font_size_override("font_size", 15); b.modulate.a = 0.86; _style_button(b, accent); add_child(b); b.button_down.connect(_mobile.bind(action, true)); b.button_up.connect(_mobile.bind(action, false))

func _tap(text: String, pos: Vector2, preset, action: String, side: float, accent: Color) -> void:
    var b := Button.new(); b.text = text; b.custom_minimum_size = Vector2(side, 82); b.set_anchors_preset(preset); b.position = pos; b.add_theme_font_size_override("font_size", 14); b.modulate.a = 0.86; _style_button(b, accent); add_child(b); b.pressed.connect(_mobile_tap.bind(action))

func _mobile(action: String, pressed: bool) -> void:
    if is_instance_valid(player): player.set_mobile_action(action, pressed)

func _mobile_tap(action: String) -> void:
    if is_instance_valid(player): player.set_mobile_action(action, true)

func update_status(p, _alive: int, total_laps: int, event_name: String, objective: String, elapsed: float = 0.0, time_limit: float = 0.0) -> void:
    if not is_instance_valid(p): return
    hp_label.text = "БРОНЯ %d/%d" % [maxi(0, int(p.health)), int(p.max_health)]
    hp_bar.value = clampf(100.0 * p.health / maxf(1.0, p.max_health), 0.0, 100.0)
    lap_label.text = "КРУГ %d/%d" % [mini(p.current_lap, total_laps), total_laps]
    rockets_label.text = "РАКЕТЫ %d" % p.rockets
    mines_label.text = "МИНЫ %d" % p.mines
    event_label.text = event_name
    objective_label.text = objective
    speed_label.text = "%d КМ/Ч" % int(absf(p._speed) * 4.8)
    nitro_label.text = "НИТРО %d%%" % int(100.0 * p.nitro / maxf(1.0, p.nitro_capacity))
    nitro_bar.value = clampf(100.0 * p.nitro / maxf(1.0, p.nitro_capacity), 0.0, 100.0)
    special_label.text = "СПЕЦ: %s" % ("ГОТОВА" if p.special_cooldown <= 0.0 else "%.1f С" % p.special_cooldown)
    timer_label.text = "ОСТАЛОСЬ %.1f" % maxf(0.0, time_limit - elapsed) if time_limit > 0.0 else "%.1f С" % elapsed

func set_race_position(position: int, total: int) -> void:
    position_label.text = "%d/%d" % [position, total]
    if position == 1: position_label.add_theme_color_override("font_color", Color("ffd657"))
    elif position <= 3: position_label.add_theme_color_override("font_color", Color("ff9854"))
    else: position_label.add_theme_color_override("font_color", Color("f5f1eb"))

func set_drift_charge(value: float) -> void:
    drift_bar.value = clampf(value * 100.0, 0.0, 100.0)
    if value > 0.78: drift_label.text = "ОТПУСТИ ДРИФТ — МАКС БУСТ"
    elif value > 0.34: drift_label.text = "ДРИФТ ЗАРЯЖЕН"
    else: drift_label.text = "ДРИФТ-БУСТ"

func flash_damage(amount: float = 10.0) -> void:
    if not is_instance_valid(damage_overlay): return
    damage_overlay.color = Color(0.75, 0.02, 0.01, clampf(0.09 + amount / 110.0, 0.11, 0.32))
    var t := damage_overlay.create_tween(); t.tween_property(damage_overlay, "color:a", 0.0, 0.30)

func show_streak(streak: int, reward: int) -> void:
    streak_label.text = "+%d БОЛТОВ" % reward if streak <= 1 else "СЕРИЯ x%d • +%d" % [streak, reward]
    streak_label.modulate.a = 1.0; var t := streak_label.create_tween(); t.tween_interval(0.9); t.tween_property(streak_label, "modulate:a", 0.0, 0.4)

func flash_message(text: String, hold: float = 1.0) -> void:
    message_label.text = text; message_label.modulate.a = 1.0; var t := message_label.create_tween(); t.tween_interval(hold); t.tween_property(message_label, "modulate:a", 0.0, 0.38)

func show_countdown(text: String) -> void:
    message_label.text = text; message_label.modulate.a = 1.0

func show_end(text: String) -> void:
    end_label.text = text; end_panel.visible = true

func _restart() -> void:
    get_tree().reload_current_scene()

func _alpha(color: Color, alpha: float) -> Color:
    var result := color; result.a = alpha; return result
