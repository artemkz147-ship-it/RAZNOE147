from pathlib import Path
import re

# Build on the already battle-tested v1.5 transformation first.
exec(Path('wasteland/ci_patch_v15.py').read_text(), {})

car_path = Path('wasteland/scripts/combat_car.gd')
car = car_path.read_text()

# --- 8 vehicle roster ------------------------------------------------------
car = car.replace(
    'elif car_type == 5: collision_size = Vector3(2.42, 1.65, 4.90)\n',
    'elif car_type == 5: collision_size = Vector3(2.42, 1.65, 4.90)\n'
    '    elif car_type == 6: collision_size = Vector3(2.20, 1.12, 4.62)\n'
    '    elif car_type == 7: collision_size = Vector3(2.72, 1.92, 5.55)\n',
    1,
)
car = car.replace(
    '        "res://assets/kenney/vehicles/delivery.glb"\n    ]',
    '        "res://assets/kenney/vehicles/delivery.glb",\n'
    '        "res://assets/kenney/vehicles/sedan-sports.glb",\n'
    '        "res://assets/kenney/vehicles/garbage-truck.glb"\n'
    '    ]',
    1,
)
car = car.replace(
    'var scales: Array[float] = [1.10, 1.10, 1.08, 1.14, 0.94, 1.02]',
    'var scales: Array[float] = [1.10, 1.10, 1.08, 1.14, 0.94, 1.02, 1.08, 0.92]',
    1,
)
car = car.replace('var idx: int = clampi(car_type, 0, 5)\n    var half_x:', 'var idx: int = clampi(car_type, 0, 7)\n    var half_x:', 1)
car = car.replace(
    'var half_x: Array[float] = [1.08, 1.06, 1.22, 1.08, 1.32, 1.20]',
    'var half_x: Array[float] = [1.08, 1.06, 1.22, 1.08, 1.32, 1.20, 1.10, 1.36]',
    1,
)
car = car.replace(
    'var half_z: Array[float] = [2.20, 2.10, 2.50, 2.28, 2.72, 2.45]',
    'var half_z: Array[float] = [2.20, 2.10, 2.50, 2.28, 2.72, 2.45, 2.34, 2.82]',
    1,
)

# --- Weapon crates / finite gun ammo --------------------------------------
if '@export var starting_gun_ammo: int = 0' not in car:
    car = car.replace('@export var starting_mines: int = 1\n', '@export var starting_mines: int = 1\n@export var starting_gun_ammo: int = 0\n', 1)
if 'var gun_ammo: int = 0' not in car:
    car = car.replace('var mines: int = 1\n', 'var mines: int = 1\nvar gun_ammo: int = 0\n', 1)
if 'gun_ammo = starting_gun_ammo' not in car:
    car = car.replace('    mines = starting_mines\n', '    mines = starting_mines\n    gun_ammo = starting_gun_ammo\n', 1)
car = car.replace(
    'if _gun_cooldown > 0.0 or not is_instance_valid(_muzzle): return\n    _gun_cooldown = 0.15',
    'if _gun_cooldown > 0.0 or gun_ammo <= 0 or not is_instance_valid(_muzzle): return\n    _gun_cooldown = 0.15\n    gun_ammo -= 1',
    1,
)
car = car.replace(
    'elif kind == 1:\n        rockets += int(amount if amount > 0.0 else 2.0); mines += 1',
    'elif kind == 1:\n        gun_ammo += int(70.0 if amount <= 0.0 else maxf(45.0, amount * 28.0)); rockets += 1; mines += 1',
    1,
)

# --- Three-stage arcade drift boost ---------------------------------------
start = car.index('func _release_drift_boost() -> void:')
end = car.index('\nfunc apply_track_boost', start)
drift_block = r'''func drift_stage() -> int:
    if _drift_charge >= 2.05:
        return 3
    if _drift_charge >= 1.20:
        return 2
    if _drift_charge >= 0.52:
        return 1
    return 0

func _release_drift_boost() -> void:
    var stage: int = drift_stage()
    if stage <= 0:
        _drift_charge = 0.0
        return
    var durations: Array[float] = [0.0, 0.38, 0.72, 1.16]
    var speed_floors: Array[float] = [0.0, 0.88, 0.98, 1.10]
    var nitro_gain: Array[float] = [0.0, 7.0, 13.0, 22.0]
    boost_time = maxf(boost_time, durations[stage])
    _speed = maxf(_speed, max_speed * speed_floors[stage])
    nitro = minf(nitro_capacity, nitro + nitro_gain[stage])
    var ring_color: Color = Color("43d7ff") if stage == 1 else (Color("b86cff") if stage == 2 else Color("ffd34d"))
    _spawn_special_ring(ring_color, 2.4 + float(stage) * 1.25)
    drift_boosted.emit(self, float(stage) / 3.0)
    _drift_charge = 0.0
'''
car = car[:start] + drift_block + car[end:]

# --- Wheel loss now affects handling --------------------------------------
if 'var _wheel_damage_stage: int = 0' not in car:
    car = car.replace('var _detached_parts: int = 0\n', 'var _detached_parts: int = 0\nvar _wheel_damage_stage: int = 0\n', 1)

wheel_block = r'''
func _hide_one_visual_wheel(root: Node) -> bool:
    for child in root.get_children():
        if child is MeshInstance3D:
            var mesh_node := child as MeshInstance3D
            var low: String = mesh_node.name.to_lower()
            if (low.contains("wheel") or low.contains("tire") or low.contains("tyre")) and not mesh_node.has_meta("wc_lost"):
                mesh_node.set_meta("wc_lost", true)
                mesh_node.visible = false
                return true
        if _hide_one_visual_wheel(child):
            return true
    return false

func _eject_damage_tire() -> void:
    var side: float = -1.0 if _wheel_damage_stage % 2 == 1 else 1.0
    var xform := Transform3D(global_transform.basis, global_position + global_transform.basis.x * side * 1.0 + Vector3.UP * 0.45)
    _spawn_debris_body("res://assets/kenney/debris/debris-tire.glb", xform, global_position - global_transform.basis.z, 5.0 + float(_wheel_damage_stage), 1.0)
    if is_instance_valid(_external_model):
        _hide_one_visual_wheel(_external_model)

func _update_wheel_damage() -> void:
    var ratio: float = health / maxf(1.0, max_health)
    if ratio <= 0.58 and _wheel_damage_stage < 1:
        _wheel_damage_stage = 1
        max_speed *= 0.93
        turn_rate *= 0.88
        acceleration *= 0.94
        _eject_damage_tire()
    if ratio <= 0.27 and _wheel_damage_stage < 2:
        _wheel_damage_stage = 2
        max_speed *= 0.86
        turn_rate *= 0.78
        acceleration *= 0.88
        _eject_damage_tire()

func weapon_ammo_text() -> String:
    return "%d • R%d • M%d" % [gun_ammo, rockets, mines]
'''
insert = car.index('\nfunc take_damage(')
if 'func _update_wheel_damage() -> void:' not in car:
    car = car[:insert] + wheel_block + car[insert:]
car = car.replace(
    'health -= applied; _camera_shake = maxf(_camera_shake, clampf(applied / 22.0, 0.12, 0.95)); _flash_damage(); _check_damage_parts(hit_position, applied); damage_taken.emit(self, applied)',
    'health -= applied; _camera_shake = maxf(_camera_shake, clampf(applied / 22.0, 0.12, 0.95)); _flash_damage(); _check_damage_parts(hit_position, applied); _update_wheel_damage(); damage_taken.emit(self, applied)',
    1,
)

vs = car.index('func vehicle_name() -> String:')
ve = car.index('\nfunc apply_stun', vs)
vehicle_block = r'''func vehicle_name() -> String:
    match car_type:
        1: return "RAZOR HATCH"
        2: return "WAR SUV"
        3: return "PHANTOM X"
        4: return "FIREBREAKER"
        5: return "DOOM DELIVERY"
        6: return "NIGHT SEDAN"
        7: return "GARBAGE KING"
        _: return "INTERCEPTOR"
'''
car = car[:vs] + vehicle_block + car[ve:]
car_path.write_text(car)

# --- Race menu, 8-car roster and elevated courses -------------------------
game_path = Path('wasteland/scripts/game.gd')
game = game_path.read_text()

if 'var selected_car: int = 0' not in game:
    game = game.replace(
        'var track_style: int = 0\n',
        'var track_style: int = 0\n'
        'var selected_car: int = 0\n'
        'var selected_track: int = 0\n'
        'var menu_layer: CanvasLayer\n'
        'var menu_car_label: Label\n'
        'var menu_track_label: Label\n'
        'var menu_stats_label: Label\n',
        1,
    )

ready_start = game.index('func _ready() -> void:')
ready_end = game.index('\nfunc _process', ready_start)
menu_block = r'''func _ready() -> void:
    randomize()
    _setup_input()
    _make_materials()
    _build_main_menu()

func _menu_button(text: String, min_width: float = 120.0) -> Button:
    var b := Button.new()
    b.text = text
    b.custom_minimum_size = Vector2(min_width, 56)
    b.add_theme_font_size_override("font_size", 18)
    return b

func _build_main_menu() -> void:
    menu_layer = CanvasLayer.new()
    add_child(menu_layer)
    var root := Control.new()
    root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    menu_layer.add_child(root)
    var bg := ColorRect.new()
    bg.color = Color("070b12")
    bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    root.add_child(bg)
    var title := Label.new()
    title.text = "WASTELAND CIRCUIT"
    title.add_theme_font_size_override("font_size", 48)
    title.add_theme_color_override("font_color", Color("ff8246"))
    title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    title.set_anchors_preset(Control.PRESET_TOP_WIDE)
    title.position = Vector2(0, 46)
    title.size = Vector2(0, 72)
    root.add_child(title)
    var sub := Label.new()
    sub.text = "АРКАДНАЯ БОЕВАЯ ГОНКА • СКОРОСТЬ ВАЖНЕЕ ОРУЖИЯ"
    sub.add_theme_font_size_override("font_size", 16)
    sub.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    sub.set_anchors_preset(Control.PRESET_TOP_WIDE)
    sub.position = Vector2(0, 112)
    sub.size = Vector2(0, 34)
    root.add_child(sub)

    var panel := VBoxContainer.new()
    panel.set_anchors_preset(Control.PRESET_CENTER)
    panel.position = Vector2(-390, -205)
    panel.size = Vector2(780, 440)
    panel.add_theme_constant_override("separation", 16)
    root.add_child(panel)

    var car_title := Label.new(); car_title.text = "МАШИНА"; car_title.add_theme_font_size_override("font_size", 18); car_title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER; panel.add_child(car_title)
    var car_row := HBoxContainer.new(); car_row.alignment = BoxContainer.ALIGNMENT_CENTER; car_row.add_theme_constant_override("separation", 14); panel.add_child(car_row)
    var car_prev := _menu_button("◀", 80); car_row.add_child(car_prev)
    menu_car_label = Label.new(); menu_car_label.custom_minimum_size = Vector2(420, 58); menu_car_label.add_theme_font_size_override("font_size", 29); menu_car_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER; menu_car_label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER; car_row.add_child(menu_car_label)
    var car_next := _menu_button("▶", 80); car_row.add_child(car_next)
    car_prev.pressed.connect(func(): selected_car = posmodi(selected_car - 1, 8); _refresh_menu())
    car_next.pressed.connect(func(): selected_car = posmodi(selected_car + 1, 8); _refresh_menu())

    menu_stats_label = Label.new(); menu_stats_label.add_theme_font_size_override("font_size", 16); menu_stats_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER; menu_stats_label.custom_minimum_size = Vector2(720, 48); panel.add_child(menu_stats_label)

    var track_title := Label.new(); track_title.text = "ТРАССА"; track_title.add_theme_font_size_override("font_size", 18); track_title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER; panel.add_child(track_title)
    var track_row := HBoxContainer.new(); track_row.alignment = BoxContainer.ALIGNMENT_CENTER; track_row.add_theme_constant_override("separation", 14); panel.add_child(track_row)
    var track_prev := _menu_button("◀", 80); track_row.add_child(track_prev)
    menu_track_label = Label.new(); menu_track_label.custom_minimum_size = Vector2(420, 54); menu_track_label.add_theme_font_size_override("font_size", 25); menu_track_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER; menu_track_label.vertical_alignment = VERTICAL_ALIGNMENT_CENTER; track_row.add_child(menu_track_label)
    var track_next := _menu_button("▶", 80); track_row.add_child(track_next)
    track_prev.pressed.connect(func(): selected_track = posmodi(selected_track - 1, 3); _refresh_menu())
    track_next.pressed.connect(func(): selected_track = posmodi(selected_track + 1, 3); _refresh_menu())

    var start_btn := _menu_button("СТАРТ ГОНКИ", 420)
    start_btn.add_theme_font_size_override("font_size", 24)
    start_btn.pressed.connect(_start_selected_race)
    var center := HBoxContainer.new(); center.alignment = BoxContainer.ALIGNMENT_CENTER; center.add_child(start_btn); panel.add_child(center)
    var note := Label.new(); note.text = "Оружие только из ящиков • Дрифт заряжается до III уровня • Потеря колёс ухудшает управление"; note.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER; note.add_theme_font_size_override("font_size", 14); panel.add_child(note)
    _refresh_menu()

func _refresh_menu() -> void:
    var names: Array[String] = ["INTERCEPTOR", "RAZOR HATCH", "WAR SUV", "PHANTOM X", "FIREBREAKER", "DOOM DELIVERY", "NIGHT SEDAN", "GARBAGE KING"]
    var tracks: Array[String] = ["RUST CIRCUIT", "IRON QUARRY", "NEON DOCKS"]
    var stats: Array[String] = [
        "Сбалансированный • 274 км/ч • хороший дрифт",
        "Лёгкий • 283 км/ч • резкие повороты",
        "Таран • 254 км/ч • высокая броня",
        "Скорость • 298 км/ч • слабая броня",
        "Тяжёлый • 240 км/ч • мощный контакт",
        "Универсал • 278 км/ч • стабильность",
        "Точный • 288 км/ч • быстрый выход из дрифта",
        "Монстр • 235 км/ч • максимальная живучесть"
    ]
    if is_instance_valid(menu_car_label): menu_car_label.text = names[selected_car]
    if is_instance_valid(menu_track_label): menu_track_label.text = tracks[selected_track]
    if is_instance_valid(menu_stats_label): menu_stats_label.text = stats[selected_car]

func _start_selected_race() -> void:
    if is_instance_valid(menu_layer):
        menu_layer.queue_free()
    track_style = selected_track
    _make_path()
    _build_world()
    _build_track()
    _build_checkpoints()
    _build_boost_pads()
    _build_ramps()
    _build_trackside_world()
    _build_v16_track_features()
    _spawn_destructibles()
    _spawn_pickups()
    _spawn_cars()
    _build_hud()
    call_deferred("_start_countdown")
'''
game = game[:ready_start] + menu_block + game[ready_end:]

pstart = game.index('func _make_path() -> void:')
pend = game.index('\nfunc _build_world()', pstart)
path_block = r'''func _make_path() -> void:
    path_points.clear()
    for i in range(PATH_SEGMENTS):
        var a: float = TAU * float(i) / float(PATH_SEGMENTS)
        var radial: float
        var x_scale: float
        var z_scale: float
        var y: float = 0.04
        match track_style:
            1:
                radial = 62.0 + sin(a * 3.0 + 0.35) * 11.0 + sin(a * 7.0) * 3.5
                x_scale = 0.98
                z_scale = 1.02
                y += maxf(0.0, sin(a * 2.0 - 0.35)) * 5.4
            2:
                radial = 57.0 + sin(a * 2.0 + 0.65) * 7.5 + sin(a * 6.0 - 0.30) * 5.0
                x_scale = 1.30
                z_scale = 0.74
                y += maxf(0.0, sin(a * 3.0 + 0.70)) * 3.2
            _:
                radial = 59.0 + sin(a * 2.0) * 10.0 + sin(a * 5.0 + 0.55) * 4.5
                x_scale = 1.14
                z_scale = 0.88
                y += maxf(0.0, sin(a * 2.0 - 0.65)) * 2.4
        var x: float = cos(a) * radial * x_scale
        var z: float = sin(a) * radial * z_scale
        path_points.append(Vector3(x, y, z))
'''
game = game[:pstart] + path_block + game[pend:]

sp = game.index('func _spawn_pickups() -> void:')
se = game.index('\nfunc _spawn_cars()', sp)
pickup_block = r'''func _spawn_pickups() -> void:
    for i in range(24):
        var idx: int = (i * 3 + 8) % PATH_SEGMENTS
        var p: Vector3 = path_points[idx]
        var next_p: Vector3 = path_points[(idx + 1) % PATH_SEGMENTS]
        var dir: Vector3 = (next_p - p).normalized()
        var normal: Vector3 = Vector3(-dir.z, 0, dir.x)
        var pickup = PickupScript.new()
        if i % 4 == 0:
            pickup.kind = 1
            pickup.amount = 2.0
        elif i % 7 == 0:
            pickup.kind = 0
            pickup.amount = 24.0
        else:
            pickup.kind = 2
            pickup.amount = 42.0
        var lane: float = [-3.8, 0.0, 3.8][i % 3]
        pickup.position = p + normal * lane + Vector3.UP * 1.15
        add_child(pickup)
'''
game = game[:sp] + pickup_block + game[se:]

ss = game.index('func _spawn_cars() -> void:')
send = game.index('\nfunc _start_countdown()', ss)
spawn_block = r'''func _spawn_cars() -> void:
    var colors: Array[Color] = [Color("ff4a22"), Color("40b9ff"), Color("f7c941"), Color("ad6cff"), Color("65d887"), Color("e85d91"), Color("6ad8ff"), Color("d0aa52")]
    var healths: Array[float] = [120.0, 100.0, 148.0, 92.0, 180.0, 112.0, 108.0, 195.0]
    var speeds: Array[float] = [57.0, 59.0, 53.0, 62.0, 50.0, 58.0, 60.0, 49.0]
    var accels: Array[float] = [37.0, 40.0, 34.0, 42.0, 31.0, 39.0, 41.0, 30.0]
    var turns: Array[float] = [2.45, 2.72, 2.20, 2.65, 1.95, 2.52, 2.62, 1.88]
    var start: Vector3 = path_points[0]
    var next_p: Vector3 = path_points[1]
    var dir: Vector3 = (next_p - start).normalized()
    var normal: Vector3 = Vector3(-dir.z, 0.0, dir.x)
    for i in range(RACERS):
        var car = CarScript.new()
        car.name = "PLAYER" if i == 0 else "RIVAL_%d" % i
        car.is_player = i == 0
        car.car_type = selected_car if i == 0 else posmodi(selected_car + i + 1, 8)
        car.car_color = colors[car.car_type]
        car.total_checkpoints = CHECKPOINTS
        car.total_laps = LAPS
        car.max_health = healths[car.car_type]
        car.max_speed = speeds[car.car_type] + (0.0 if i == 0 else float(i) * 0.22)
        car.acceleration = accels[car.car_type]
        car.turn_rate = turns[car.car_type]
        car.starting_gun_ammo = 0
        car.starting_rockets = 0
        car.starting_mines = 0
        car.gun_damage = 2.1 if i == 0 else 1.8
        car.ai_aggression = 0.48 + float(i) * 0.06
        car.ai_skill = 0.94 + float(i) * 0.055
        var row: int = i / 2
        var lane_side: float = -2.4 if i % 2 == 0 else 2.4
        car.position = start - dir * (float(row) * 4.8 + 2.0) + normal * lane_side + Vector3.UP * 1.1
        add_child(car)
        car.look_at(car.position + dir, Vector3.UP)
        car.set_race_path(path_points)
        cars.append(car)
        car.race_won.connect(_on_race_won)
        car.car_destroyed.connect(_on_car_destroyed)
        car.damage_taken.connect(_on_damage)
        car.special_used.connect(_on_special)
        car.drift_boosted.connect(_on_drift_boost)
        if i == 0:
            player = car
    for car in cars:
        if not car.is_player:
            car.ai_target = player
'''
game = game[:ss] + spawn_block + game[send:]

feature_insert = game.index('\nfunc _spawn_destructibles()')
feature_block = r'''
func _build_v16_track_features() -> void:
    var tunnel_indices: Array[int] = [15, 16, 17] if track_style != 1 else [46, 47, 48]
    for idx in tunnel_indices:
        var p: Vector3 = path_points[idx]
        var np: Vector3 = path_points[(idx + 1) % PATH_SEGMENTS]
        var mid: Vector3 = (p + np) * 0.5
        var dir: Vector3 = (np - p).normalized()
        var normal: Vector3 = Vector3(-dir.z, 0.0, dir.x).normalized()
        var length: float = p.distance_to(np) + 0.5
        _oriented_static_box("TunnelL", mid - normal * 7.1 + Vector3.UP * 2.1, Vector3(0.65, 4.2, length), np, metal_mat)
        _oriented_static_box("TunnelR", mid + normal * 7.1 + Vector3.UP * 2.1, Vector3(0.65, 4.2, length), np, metal_mat)
        _oriented_static_box("TunnelRoof", mid + Vector3.UP * 4.15, Vector3(14.6, 0.50, length), np, metal_mat)
    for idx in range(0, PATH_SEGMENTS, 5):
        var p: Vector3 = path_points[idx]
        if p.y > 1.6:
            _static_box("BridgeSupport", Vector3(p.x, p.y * 0.5 - 0.15, p.z), Vector3(1.15, maxf(1.0, p.y), 1.15), Vector3.ZERO, metal_mat)
'''
if 'func _build_v16_track_features() -> void:' not in game:
    game = game[:feature_insert] + feature_block + game[feature_insert:]

if 'building-type-p.glb' not in game:
    game = game.replace(
        '        "res://assets/kenney/city/building-type-h.glb"\n    ]',
        '        "res://assets/kenney/city/building-type-h.glb",\n'
        '        "res://assets/kenney/city/building-type-i.glb",\n'
        '        "res://assets/kenney/city/building-type-j.glb",\n'
        '        "res://assets/kenney/city/building-type-k.glb",\n'
        '        "res://assets/kenney/city/building-type-l.glb",\n'
        '        "res://assets/kenney/city/building-type-m.glb",\n'
        '        "res://assets/kenney/city/building-type-n.glb",\n'
        '        "res://assets/kenney/city/building-type-o.glb",\n'
        '        "res://assets/kenney/city/building-type-p.glb"\n'
        '    ]',
        1,
    )
    game = game.replace('for i in range(20):\n        var packed:', 'for i in range(30):\n        var packed:', 1)
    game = game.replace('TAU * float(i) / 20.0', 'TAU * float(i) / 30.0', 1)

game_path.write_text(game)

hud_path = Path('wasteland/scripts/hud.gd')
hud = hud_path.read_text()
hud = hud.replace(
    'rockets_label.text = "РАКЕТЫ %d" % p.rockets\n    mines_label.text = "МИНЫ %d" % p.mines',
    'rockets_label.text = "ПУЛЕМЁТ %d • РАКЕТЫ %d" % [p.gun_ammo, p.rockets]\n    mines_label.text = "МИНЫ %d" % p.mines',
    1,
)
if 'func set_drift_stage(stage: int) -> void:' not in hud:
    marker = '\nfunc set_race_position(position: int, total: int) -> void:'
    stage_func = r'''
func set_drift_stage(stage: int) -> void:
    if not is_instance_valid(drift_label):
        return
    var roman: Array[String] = ["", "I", "II", "III"]
    drift_label.text = "ДРИФТ-БУСТ %s" % roman[clampi(stage, 0, 3)] if stage > 0 else "ДРИФТ-БУСТ"
    if stage == 1:
        drift_label.add_theme_color_override("font_color", Color("48d9ff"))
    elif stage == 2:
        drift_label.add_theme_color_override("font_color", Color("bd78ff"))
    elif stage >= 3:
        drift_label.add_theme_color_override("font_color", Color("ffd34d"))
    else:
        drift_label.add_theme_color_override("font_color", Color("fff3e7"))
'''
    hud = hud.replace(marker, stage_func + marker, 1)
hud_path.write_text(hud)

game = game_path.read_text()
if 'hud.set_drift_stage(player.drift_stage())' not in game:
    game = game.replace('        hud.set_drift_charge(player.drift_charge_percent())\n', '        hud.set_drift_charge(player.drift_charge_percent())\n        hud.set_drift_stage(player.drift_stage())\n', 1)
game_path.write_text(game)

preset = Path('wasteland/export_presets.cfg')
p = preset.read_text()
p = re.sub(r'package/unique_name="[^"]+"', 'package/unique_name="ru.openai148.wastelandcircuit.race16"', p)
p = re.sub(r'package/name="[^"]+"', 'package/name="Wasteland Circuit Race"', p)
p = re.sub(r'version/code=\d+', 'version/code=16', p)
p = re.sub(r'version/name="[^"]+"', 'version/name="1.6.0"', p)
p = re.sub(r'export_path="[^"]+"', 'export_path="builds/Wasteland-Circuit-Race-v1.6.0.apk"', p)
preset.write_text(p)
