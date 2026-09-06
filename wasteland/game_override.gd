extends Node3D

const CarScript = preload("res://scripts/combat_car.gd")
const DestructibleScript = preload("res://scripts/destructible.gd")
const PickupScript = preload("res://scripts/pickup.gd")
const HudScript = preload("res://scripts/hud.gd")

const PATH_SEGMENTS: int = 84
const CHECKPOINTS: int = 12
const LAPS: int = 3
const RACERS: int = 6
const TRACK_WIDTH: float = 18.5

var player
var hud
var cars: Array = []
var path_points: Array[Vector3] = []
var checkpoint_points: Array[Vector3] = []
var finished: bool = false
var started_at: float = 0.0
var race_started: bool = false

var road_mat: StandardMaterial3D
var shoulder_mat: StandardMaterial3D
var barrier_mat: StandardMaterial3D
var glow_mat: StandardMaterial3D
var stripe_mat: StandardMaterial3D
var dirt_mat: StandardMaterial3D
var metal_mat: StandardMaterial3D
var cyan_mat: StandardMaterial3D

func _ready() -> void:
    randomize()
    _setup_input()
    _make_materials()
    _make_path()
    _build_world()
    _build_track()
    _build_checkpoints()
    _build_boost_pads()
    _build_ramps()
    _build_trackside_world()
    _spawn_destructibles()
    _spawn_pickups()
    _spawn_cars()
    _build_hud()
    call_deferred("_start_countdown")

func _process(_delta: float) -> void:
    if Input.is_action_just_pressed("restart"):
        get_tree().reload_current_scene()
    if finished or not is_instance_valid(player):
        return
    var alive: int = 0
    for car in cars:
        if is_instance_valid(car) and not car.dead:
            alive += 1
    if hud:
        hud.update_status(player, alive, LAPS, "RUST CIRCUIT", "ГОНКА • таран и оружие помогают обгонять", _elapsed(), 0.0)
        hud.set_race_position(_race_position(player), RACERS)
        hud.set_drift_charge(player.drift_charge_percent())

func _elapsed() -> float:
    if started_at <= 0.0:
        return 0.0
    return Time.get_ticks_msec() / 1000.0 - started_at

func _setup_input() -> void:
    _add_keys("accelerate", [KEY_W, KEY_UP])
    _add_keys("brake", [KEY_S, KEY_DOWN])
    _add_keys("steer_left", [KEY_A, KEY_LEFT])
    _add_keys("steer_right", [KEY_D, KEY_RIGHT])
    _add_keys("handbrake", [KEY_SHIFT])
    _add_keys("nitro", [KEY_CTRL, KEY_F])
    _add_keys("fire", [KEY_SPACE])
    _add_keys("rocket", [KEY_E])
    _add_keys("mine", [KEY_Q])
    _add_keys("special", [KEY_X])
    _add_keys("restart", [KEY_R])

func _add_keys(action: StringName, keys: Array) -> void:
    if not InputMap.has_action(action):
        InputMap.add_action(action)
    for code in keys:
        var ev := InputEventKey.new()
        ev.physical_keycode = code
        InputMap.action_add_event(action, ev)

func _make_materials() -> void:
    road_mat = _mat(Color("181b20"), 0.08, 0.66)
    shoulder_mat = _mat(Color("30343a"), 0.20, 0.72)
    barrier_mat = _mat(Color("515965"), 0.74, 0.34)
    dirt_mat = _mat(Color("2b241e"), 0.0, 0.90)
    metal_mat = _mat(Color("292f36"), 0.82, 0.34)
    stripe_mat = _mat(Color("f5d55b"), 0.18, 0.56)
    glow_mat = _emissive(Color("ff6a2a"), 3.6)
    cyan_mat = _emissive(Color("33cfff"), 5.0)

func _mat(color: Color, metallic: float, roughness: float) -> StandardMaterial3D:
    var m := StandardMaterial3D.new()
    m.albedo_color = color
    m.metallic = metallic
    m.roughness = roughness
    return m

func _emissive(color: Color, energy: float) -> StandardMaterial3D:
    var m := StandardMaterial3D.new()
    m.albedo_color = color
    m.emission_enabled = true
    m.emission = color
    m.emission_energy_multiplier = energy
    m.metallic = 0.35
    m.roughness = 0.28
    return m

func _make_path() -> void:
    path_points.clear()
    for i in range(PATH_SEGMENTS):
        var a: float = TAU * float(i) / float(PATH_SEGMENTS)
        var radial: float = 59.0 + sin(a * 2.0) * 10.0 + sin(a * 5.0 + 0.55) * 4.5
        var x: float = cos(a) * radial * 1.14
        var z: float = sin(a) * radial * 0.88
        path_points.append(Vector3(x, 0.04, z))

func _build_world() -> void:
    var env_node := WorldEnvironment.new()
    var env := Environment.new()
    env.background_mode = Environment.BG_COLOR
    env.background_color = Color("070d13")
    env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    env.ambient_light_color = Color("8293a8")
    env.ambient_light_energy = 0.66
    env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
    env.glow_enabled = true
    env_node.environment = env
    add_child(env_node)

    var sun := DirectionalLight3D.new()
    sun.rotation_degrees = Vector3(-48.0, -35.0, 0.0)
    sun.light_color = Color("ffd0a0")
    sun.light_energy = 1.25
    sun.shadow_enabled = true
    add_child(sun)

    var moon := OmniLight3D.new()
    moon.position = Vector3(-42, 24, 30)
    moon.light_color = Color("6a8cff")
    moon.light_energy = 5.0
    moon.omni_range = 90.0
    add_child(moon)
    _static_box("Ground", Vector3(0, -0.75, 0), Vector3(220, 1.2, 190), Vector3.ZERO, dirt_mat)

func _build_track() -> void:
    for i in range(PATH_SEGMENTS):
        var p0: Vector3 = path_points[i]
        var p1: Vector3 = path_points[(i + 1) % PATH_SEGMENTS]
        var mid: Vector3 = (p0 + p1) * 0.5
        var dir: Vector3 = (p1 - p0).normalized()
        var len: float = p0.distance_to(p1) + 0.35
        var normal: Vector3 = Vector3(-dir.z, 0.0, dir.x).normalized()
        _oriented_static_box("Road_%02d" % i, mid, Vector3(TRACK_WIDTH, 0.18, len), p1, road_mat)
        _oriented_static_box("ShoulderL_%02d" % i, mid - normal * (TRACK_WIDTH * 0.55), Vector3(1.6, 0.22, len), p1, shoulder_mat)
        _oriented_static_box("ShoulderR_%02d" % i, mid + normal * (TRACK_WIDTH * 0.55), Vector3(1.6, 0.22, len), p1, shoulder_mat)
        _oriented_static_box("BarrierL_%02d" % i, mid - normal * (TRACK_WIDTH * 0.62), Vector3(0.55, 1.20, len), p1, barrier_mat, 0.64)
        _oriented_static_box("BarrierR_%02d" % i, mid + normal * (TRACK_WIDTH * 0.62), Vector3(0.55, 1.20, len), p1, barrier_mat, 0.64)
        if i % 2 == 0:
            _oriented_mesh(mid + Vector3.UP * 0.12, Vector3(0.20, 0.04, minf(2.3, len * 0.45)), p1, stripe_mat)
        if i % 7 == 0:
            _track_light(mid - normal * (TRACK_WIDTH * 0.67), Color("ff6a2a"))
            _track_light(mid + normal * (TRACK_WIDTH * 0.67), Color("33cfff"))

func _build_checkpoints() -> void:
    checkpoint_points.clear()
    for c in range(CHECKPOINTS):
        var idx: int = int(round(float(c) * float(PATH_SEGMENTS) / float(CHECKPOINTS))) % PATH_SEGMENTS
        var p: Vector3 = path_points[idx]
        var next_p: Vector3 = path_points[(idx + 1) % PATH_SEGMENTS]
        checkpoint_points.append(p)
        var area := Area3D.new()
        area.position = p + Vector3.UP * 1.4
        area.collision_layer = 0
        area.collision_mask = 1
        add_child(area)
        area.look_at(next_p + Vector3.UP * 1.4, Vector3.UP)
        var cs := CollisionShape3D.new()
        var box := BoxShape3D.new()
        box.size = Vector3(TRACK_WIDTH - 1.0, 3.6, 4.0)
        cs.shape = box
        area.add_child(cs)
        area.body_entered.connect(_on_checkpoint.bind(c))
        if c == 0:
            _build_start_gate(p, next_p)

func _build_start_gate(p: Vector3, next_p: Vector3) -> void:
    var dir: Vector3 = (next_p - p).normalized()
    var normal: Vector3 = Vector3(-dir.z, 0, dir.x)
    for side in [-1.0, 1.0]:
        _oriented_static_box("StartPillar", p + normal * side * 7.6 + Vector3.UP * 2.6, Vector3(0.75, 5.2, 0.75), next_p + normal * side * 7.6, metal_mat)
    _oriented_static_box("StartTop", p + Vector3.UP * 5.0, Vector3(16.0, 0.65, 0.85), next_p + Vector3.UP * 5.0, metal_mat)
    for x in [-4.8, -1.6, 1.6, 4.8]:
        var local: Vector3 = p + normal * x + Vector3.UP * 4.85
        _oriented_mesh(local, Vector3(1.0, 0.18, 0.30), next_p + normal * x + Vector3.UP * 4.85, glow_mat)

func _build_boost_pads() -> void:
    for idx in [7, 19, 33, 48, 64, 77]:
        var p: Vector3 = path_points[idx]
        var next_p: Vector3 = path_points[(idx + 1) % PATH_SEGMENTS]
        var dir: Vector3 = (next_p - p).normalized()
        var normal: Vector3 = Vector3(-dir.z, 0.0, dir.x)
        var area := Area3D.new()
        area.position = p + Vector3.UP * 0.24
        area.collision_layer = 0
        area.collision_mask = 1
        add_child(area)
        area.look_at(next_p + Vector3.UP * 0.24, Vector3.UP)
        var cs := CollisionShape3D.new()
        var box := BoxShape3D.new()
        box.size = Vector3(8.0, 1.1, 4.5)
        cs.shape = box
        area.add_child(cs)
        for lane in [-2.4, 0.0, 2.4]:
            _oriented_mesh(p + normal * lane + Vector3.UP * 0.18, Vector3(1.25, 0.06, 3.2), next_p + normal * lane + Vector3.UP * 0.18, cyan_mat)
        area.body_entered.connect(_on_boost_pad)

func _build_ramps() -> void:
    for idx in [26, 55]:
        var p: Vector3 = path_points[idx]
        var next_p: Vector3 = path_points[(idx + 1) % PATH_SEGMENTS]
        var ramp := StaticBody3D.new()
        ramp.position = p + Vector3.UP * 0.48
        ramp.collision_layer = 2
        ramp.collision_mask = 1
        add_child(ramp)
        ramp.look_at(next_p + Vector3.UP * 0.48, Vector3.UP)
        ramp.rotate_object_local(Vector3.RIGHT, -0.10)
        var mesh := MeshInstance3D.new()
        var bm := BoxMesh.new()
        bm.size = Vector3(7.5, 0.45, 6.0)
        mesh.mesh = bm
        mesh.material_override = shoulder_mat
        ramp.add_child(mesh)
        var cs := CollisionShape3D.new()
        var bs := BoxShape3D.new()
        bs.size = Vector3(7.5, 0.45, 6.0)
        cs.shape = bs
        ramp.add_child(cs)

func _build_trackside_world() -> void:
    for i in range(18):
        var a: float = TAU * float(i) / 18.0 + 0.18
        var r: float = 88.0 + float((i * 17) % 23)
        var h: float = 7.0 + float((i * 11) % 15)
        var pos := Vector3(cos(a) * r, h * 0.5 - 0.1, sin(a) * r * 0.90)
        _static_box("Ruin_%02d" % i, pos, Vector3(7.0 + float(i % 4) * 2.0, h, 6.0 + float((i + 1) % 3) * 2.0), Vector3(0, -a * 0.4, 0), metal_mat)
        if i % 3 == 0:
            _deco_box(pos + Vector3(0, h * 0.55, 0), Vector3(4.5, 0.18, 0.22), glow_mat)
    for i in range(14):
        var a: float = TAU * float(i) / 14.0 + 0.42
        var pos := Vector3(cos(a) * 40.0, 1.0, sin(a) * 34.0)
        _static_box("Container_%02d" % i, pos, Vector3(5.5, 2.0, 2.3), Vector3(0, a, 0), _mat(Color.from_hsv(fmod(float(i) * 0.11, 1.0), 0.55, 0.48), 0.72, 0.38))

func _spawn_destructibles() -> void:
    for i in range(38):
        var idx: int = (i * 2 + 5) % PATH_SEGMENTS
        var p: Vector3 = path_points[idx]
        var next_p: Vector3 = path_points[(idx + 1) % PATH_SEGMENTS]
        var dir: Vector3 = (next_p - p).normalized()
        var normal: Vector3 = Vector3(-dir.z, 0, dir.x)
        var side: float = -1.0 if i % 2 == 0 else 1.0
        var obj = DestructibleScript.new()
        obj.style = 1 if i % 9 == 0 else (2 if i % 6 == 0 else 0)
        obj.position = p + normal * side * (TRACK_WIDTH * 0.46) + Vector3.UP * 1.0
        obj.rotation.y = atan2(dir.x, dir.z)
        add_child(obj)

func _spawn_pickups() -> void:
    for i in range(18):
        var idx: int = (i * 4 + 10) % PATH_SEGMENTS
        var p: Vector3 = path_points[idx]
        var next_p: Vector3 = path_points[(idx + 1) % PATH_SEGMENTS]
        var dir: Vector3 = (next_p - p).normalized()
        var normal: Vector3 = Vector3(-dir.z, 0, dir.x)
        var pickup = PickupScript.new()
        if i % 6 == 0:
            pickup.kind = 1
            pickup.amount = 2.0
        elif i % 7 == 0:
            pickup.kind = 0
            pickup.amount = 25.0
        else:
            pickup.kind = 2
            pickup.amount = 45.0
        var lane: float = [-3.5, 0.0, 3.5][i % 3]
        pickup.position = p + normal * lane + Vector3.UP * 1.15
        add_child(pickup)

func _spawn_cars() -> void:
    var colors: Array[Color] = [Color("ff4a22"), Color("40b9ff"), Color("f7c941"), Color("ad6cff"), Color("65d887"), Color("e85d91"), Color("f08038")]
    var healths: Array[float] = [120.0, 100.0, 145.0, 92.0, 175.0, 108.0, 155.0]
    var speeds: Array[float] = [57.0, 59.0, 53.0, 62.0, 49.0, 58.0, 51.0]
    var accels: Array[float] = [37.0, 40.0, 34.0, 42.0, 31.0, 39.0, 32.0]
    var turns: Array[float] = [2.45, 2.72, 2.20, 2.65, 1.95, 2.52, 2.08]
    var start: Vector3 = path_points[0]
    var next_p: Vector3 = path_points[1]
    var dir: Vector3 = (next_p - start).normalized()
    var normal: Vector3 = Vector3(-dir.z, 0.0, dir.x)
    for i in range(RACERS):
        var car = CarScript.new()
        car.name = "PLAYER" if i == 0 else "RIVAL_%d" % i
        car.is_player = i == 0
        car.car_type = i if i < 5 else i + 1
        car.car_color = colors[car.car_type]
        car.total_checkpoints = CHECKPOINTS
        car.total_laps = LAPS
        car.max_health = healths[car.car_type]
        car.max_speed = speeds[car.car_type] + (0.0 if i == 0 else float(i) * 0.35)
        car.acceleration = accels[car.car_type]
        car.turn_rate = turns[car.car_type]
        car.starting_rockets = 1 if i == 0 else (1 if i % 2 == 0 else 0)
        car.starting_mines = 1 if i == 0 else 0
        car.gun_damage = 2.7 if i == 0 else 2.2
        car.ai_aggression = 0.55 + float(i) * 0.07
        car.ai_skill = 0.92 + float(i) * 0.06
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

func _start_countdown() -> void:
    if not hud: return
    _disable_cars()
    for n in [3, 2, 1]:
        hud.show_countdown(str(n))
        await get_tree().create_timer(0.72).timeout
    hud.show_countdown("ПОЕХАЛИ!")
    _enable_cars()
    race_started = true
    started_at = Time.get_ticks_msec() / 1000.0
    await get_tree().create_timer(0.65).timeout
    hud.flash_message("ДРИФТ + ТОЧНЫЙ ВЫХОД = БУСТ", 1.4)

func _race_position(target) -> int:
    var ranked: Array = []
    for car in cars:
        if not is_instance_valid(car): continue
        var score: float = _race_score(car)
        ranked.append({"car": car, "score": score})
    ranked.sort_custom(func(a, b): return float(a["score"]) > float(b["score"]))
    for i in range(ranked.size()):
        if ranked[i]["car"] == target:
            return i + 1
    return RACERS

func _race_score(car) -> float:
    if car.dead: return -100000.0
    var cp: int = car.next_checkpoint
    var base: float = float(car.current_lap * CHECKPOINTS + cp) * 10000.0
    var target_cp: Vector3 = checkpoint_points[cp] if cp >= 0 and cp < checkpoint_points.size() else checkpoint_points[0]
    return base - car.global_position.distance_to(target_cp)

func _on_checkpoint(body: Node, index: int) -> void:
    if body.has_method("register_checkpoint"):
        body.register_checkpoint(index)

func _on_boost_pad(body: Node) -> void:
    if body.has_method("apply_track_boost"):
        body.apply_track_boost(1.05)
        if body == player and hud:
            hud.flash_message("ТУРБО-ПОЛОСА", 0.45)

func _enable_cars() -> void:
    for car in cars:
        if is_instance_valid(car): car.controls_enabled = true

func _disable_cars() -> void:
    for car in cars:
        if is_instance_valid(car): car.controls_enabled = false

func _build_hud() -> void:
    var layer := CanvasLayer.new()
    add_child(layer)
    hud = HudScript.new()
    layer.add_child(hud)
    hud.set_player(player)

func _on_race_won(car) -> void:
    if finished: return
    finished = true
    _disable_cars()
    if hud:
        if car == player:
            hud.show_end("ПОБЕДА!\nТы выиграл гонку — оружие было лишь инструментом.")
        else:
            hud.show_end("ФИНИШИРОВАЛ СОПЕРНИК\nНажми рестарт и отыграйся.")

func _on_car_destroyed(car, killer) -> void:
    if car == player and not finished:
        finished = true
        _disable_cars()
        if hud: hud.show_end("МАШИНА УНИЧТОЖЕНА\nR — новый заезд")
    elif killer == player and hud:
        hud.show_streak(1, 60)

func _on_damage(car, amount: float) -> void:
    if car == player and hud: hud.flash_damage(amount)

func _on_special(car, special_name: String) -> void:
    if car == player and hud: hud.flash_message(special_name, 0.65)

func _on_drift_boost(car, power: float) -> void:
    if car == player and hud:
        var label: String = "ДРИФТ-БУСТ!" if power < 0.72 else "ИДЕАЛЬНЫЙ ДРИФТ!"
        hud.flash_message(label, 0.55)

func _oriented_static_box(name_text: String, pos: Vector3, size: Vector3, look_target: Vector3, mat: Material, y_offset: float = 0.0) -> StaticBody3D:
    var body := StaticBody3D.new()
    body.name = name_text
    body.position = pos + Vector3.UP * y_offset
    body.collision_layer = 2
    body.collision_mask = 1 | 4
    add_child(body)
    body.look_at(look_target + Vector3.UP * y_offset, Vector3.UP)
    var mesh := MeshInstance3D.new()
    var box := BoxMesh.new()
    box.size = size
    mesh.mesh = box
    mesh.material_override = mat
    body.add_child(mesh)
    var collision := CollisionShape3D.new()
    var shape := BoxShape3D.new()
    shape.size = size
    collision.shape = shape
    body.add_child(collision)
    return body

func _oriented_mesh(pos: Vector3, size: Vector3, look_target: Vector3, mat: Material) -> MeshInstance3D:
    var mesh := MeshInstance3D.new()
    var box := BoxMesh.new()
    box.size = size
    mesh.mesh = box
    mesh.material_override = mat
    mesh.position = pos
    add_child(mesh)
    mesh.look_at(look_target, Vector3.UP)
    return mesh

func _static_box(name_text: String, pos: Vector3, size: Vector3, rot: Vector3, mat: Material) -> void:
    var body := StaticBody3D.new()
    body.name = name_text
    body.position = pos
    body.rotation = rot
    body.collision_layer = 2
    body.collision_mask = 1 | 4
    var mesh := MeshInstance3D.new()
    var box := BoxMesh.new()
    box.size = size
    mesh.mesh = box
    mesh.material_override = mat
    body.add_child(mesh)
    var collision := CollisionShape3D.new()
    var shape := BoxShape3D.new()
    shape.size = size
    collision.shape = shape
    body.add_child(collision)
    add_child(body)

func _deco_box(pos: Vector3, size: Vector3, mat: Material) -> void:
    var mesh := MeshInstance3D.new()
    var box := BoxMesh.new()
    box.size = size
    mesh.mesh = box
    mesh.position = pos
    mesh.material_override = mat
    add_child(mesh)

func _track_light(pos: Vector3, color: Color) -> void:
    _deco_box(pos + Vector3.UP * 1.8, Vector3(0.16, 3.5, 0.16), metal_mat)
    var lamp := OmniLight3D.new()
    lamp.position = pos + Vector3.UP * 3.5
    lamp.light_color = color
    lamp.light_energy = 2.8
    lamp.omni_range = 8.0
    add_child(lamp)
