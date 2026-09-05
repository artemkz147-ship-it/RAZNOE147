extends Node3D

const CarScript = preload("res://scripts/combat_car.gd")
const DestructibleScript = preload("res://scripts/destructible.gd")
const PickupScript = preload("res://scripts/pickup.gd")
const HudScript = preload("res://scripts/hud.gd")

const TRACK_RADIUS := 48.0
const TRACK_WIDTH := 18.0
const SEGMENTS := 56
const CHECKPOINTS := 8
const LAPS := 3

var player
var hud
var cars: Array = []
var finished := false
var started_at := 0.0
var road_mat: StandardMaterial3D
var ground_mat: StandardMaterial3D
var wall_mat: StandardMaterial3D
var glow_mat: StandardMaterial3D

func _ready() -> void:
    randomize()
    _setup_input()
    _make_materials()
    _build_world()
    _build_track()
    _build_checkpoints()
    _spawn_destructibles()
    _spawn_pickups()
    _spawn_cars()
    _build_hud()
    _enable_cars()
    started_at = Time.get_ticks_msec() / 1000.0
    if hud:
        hud.flash_message("БОЕВОЙ ЗАЕЗД — 3 КРУГА", 1.4)

func _process(_delta: float) -> void:
    if Input.is_action_just_pressed("restart"):
        get_tree().reload_current_scene()
    if finished or not is_instance_valid(player):
        return
    var alive := 0
    for car in cars:
        if is_instance_valid(car) and not car.dead:
            alive += 1
    if hud:
        hud.update_status(player, alive, LAPS, "WASTELAND CIRCUIT", "3 круга • уничтожай соперников", _elapsed(), 0.0)

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
    road_mat = StandardMaterial3D.new()
    road_mat.albedo_color = Color("151a20")
    ground_mat = StandardMaterial3D.new()
    ground_mat.albedo_color = Color("302820")
    wall_mat = StandardMaterial3D.new()
    wall_mat.albedo_color = Color("4b535d")
    glow_mat = StandardMaterial3D.new()
    glow_mat.albedo_color = Color("ff6b24")
    glow_mat.emission_enabled = true
    glow_mat.emission = Color("ff4a12")
    glow_mat.emission_energy_multiplier = 3.0

func _build_world() -> void:
    var env_node := WorldEnvironment.new()
    var env := Environment.new()
    env.background_mode = Environment.BG_COLOR
    env.background_color = Color("091019")
    env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    env.ambient_light_color = Color("8799ad")
    env.ambient_light_energy = 0.75
    env_node.environment = env
    add_child(env_node)
    var sun := DirectionalLight3D.new()
    sun.rotation_degrees = Vector3(-52.0, -32.0, 0.0)
    sun.light_color = Color("ffd0a2")
    sun.light_energy = 1.35
    sun.shadow_enabled = true
    add_child(sun)
    _static_box("Ground", Vector3(0, -0.6, 0), Vector3(190, 1.0, 190), Vector3.ZERO, ground_mat)

func _build_track() -> void:
    var seg_len := 5.7
    for i in range(SEGMENTS):
        var a := TAU * float(i) / float(SEGMENTS)
        var p := Vector3(cos(a) * TRACK_RADIUS, 0.03, sin(a) * TRACK_RADIUS)
        _static_box("Road_%02d" % i, p, Vector3(TRACK_WIDTH, 0.14, seg_len), Vector3(0, -a, 0), road_mat)
        var inner_r := TRACK_RADIUS - TRACK_WIDTH * 0.58
        var outer_r := TRACK_RADIUS + TRACK_WIDTH * 0.58
        _static_box("Inner_%02d" % i, Vector3(cos(a) * inner_r, 0.72, sin(a) * inner_r), Vector3(0.72, 1.45, seg_len), Vector3(0, -a, 0), wall_mat)
        _static_box("Outer_%02d" % i, Vector3(cos(a) * outer_r, 0.72, sin(a) * outer_r), Vector3(0.72, 1.45, seg_len), Vector3(0, -a, 0), wall_mat)
        if i % 4 == 0:
            _deco_box(Vector3(cos(a) * (outer_r - 0.7), 1.75, sin(a) * (outer_r - 0.7)), Vector3(0.20, 3.4, 0.20), glow_mat)

func _build_checkpoints() -> void:
    for i in range(CHECKPOINTS):
        var a := -TAU * float(i) / float(CHECKPOINTS)
        var area := Area3D.new()
        area.position = Vector3(cos(a) * TRACK_RADIUS, 1.15, sin(a) * TRACK_RADIUS)
        area.rotation.y = -a
        area.collision_layer = 0
        area.collision_mask = 1
        var cs := CollisionShape3D.new()
        var box := BoxShape3D.new()
        box.size = Vector3(TRACK_WIDTH - 1.0, 3.0, 3.5)
        cs.shape = box
        area.add_child(cs)
        add_child(area)
        area.body_entered.connect(_on_checkpoint.bind(i))

func _on_checkpoint(body: Node, index: int) -> void:
    if body.has_method("register_checkpoint"):
        body.register_checkpoint(index)

func _spawn_destructibles() -> void:
    for i in range(24):
        var a := TAU * float(i) / 24.0 + 0.09
        var side := -1.0 if i % 2 == 0 else 1.0
        var obj = DestructibleScript.new()
        obj.style = 1 if i % 7 == 0 else (2 if i % 5 == 0 else 0)
        var r := TRACK_RADIUS + side * TRACK_WIDTH * 0.34
        obj.position = Vector3(cos(a) * r, 1.0, sin(a) * r)
        obj.rotation.y = -a
        add_child(obj)

func _spawn_pickups() -> void:
    for i in range(12):
        var a := TAU * float(i) / 12.0 + 0.16
        var p = PickupScript.new()
        p.kind = i % 5
        p.amount = 35.0 if p.kind == 0 else 2.0
        var r := TRACK_RADIUS + (-3.7 if i % 2 == 0 else 3.7)
        p.position = Vector3(cos(a) * r, 1.15, sin(a) * r)
        add_child(p)

func _spawn_cars() -> void:
    var colors := [Color("ff4d24"), Color("45b8ff"), Color("ffd34c"), Color("a970ff"), Color("64e39a")]
    for i in range(5):
        var car = CarScript.new()
        car.name = "PLAYER" if i == 0 else "RIVAL_%d" % i
        car.is_player = i == 0
        car.car_type = i
        car.car_color = colors[i]
        car.track_radius = TRACK_RADIUS
        car.total_checkpoints = CHECKPOINTS
        car.total_laps = LAPS
        car.max_health = 125.0 if i == 0 else 100.0 + i * 5.0
        car.max_speed = 33.0 if i == 0 else 29.0 + i * 0.65
        car.acceleration = 25.0 if i == 0 else 22.5 + i * 0.5
        car.starting_rockets = 8 if i == 0 else 5 + i
        car.starting_mines = 4 if i == 0 else 3
        car.ai_aggression = 0.9 + i * 0.12
        car.ai_skill = 0.85 + i * 0.08
        var a := 0.08 + i * 0.035
        var r := TRACK_RADIUS - 4.0 + float(i % 3) * 4.0
        car.position = Vector3(cos(a) * r, 1.1, sin(a) * r)
        car.rotation.y = -a - PI * 0.5
        add_child(car)
        cars.append(car)
        car.race_won.connect(_on_race_won)
        car.car_destroyed.connect(_on_car_destroyed)
        car.damage_taken.connect(_on_damage)
        car.special_used.connect(_on_special)
        if i == 0:
            player = car
    for car in cars:
        if not car.is_player:
            car.ai_target = player

func _enable_cars() -> void:
    for car in cars:
        car.controls_enabled = true

func _build_hud() -> void:
    var layer := CanvasLayer.new()
    add_child(layer)
    hud = HudScript.new()
    layer.add_child(hud)
    hud.set_player(player)

func _on_race_won(car) -> void:
    if finished:
        return
    finished = true
    _disable_cars()
    if hud:
        hud.show_end("ПОБЕДА!\nНажми R для рестарта" if car == player else "СОПЕРНИК ПОБЕДИЛ\nНажми R для рестарта")

func _on_car_destroyed(car, killer) -> void:
    if car == player and not finished:
        finished = true
        _disable_cars()
        if hud:
            hud.show_end("МАШИНА УНИЧТОЖЕНА\nНажми R для рестарта")
    elif killer == player and hud:
        hud.show_streak(1, 60)

func _on_damage(car, amount: float) -> void:
    if car == player and hud:
        hud.flash_damage(amount)

func _on_special(car, special_name: String) -> void:
    if car == player and hud:
        hud.flash_message(special_name, 0.7)

func _disable_cars() -> void:
    for car in cars:
        if is_instance_valid(car):
            car.controls_enabled = false

func _static_box(name_text: String, pos: Vector3, size: Vector3, rot: Vector3, mat: Material) -> void:
    var body := StaticBody3D.new()
    body.name = name_text
    body.position = pos
    body.rotation = rot
    body.collision_layer = 2
    body.collision_mask = 1
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
