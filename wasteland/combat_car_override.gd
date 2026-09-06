extends CharacterBody3D

signal car_destroyed(car, killer)
signal lap_completed(car, lap)
signal race_won(car)
signal pickup_collected(car, kind)
signal damage_taken(car, amount)
signal special_used(car, name)
signal drift_boosted(car, power)

const RocketScript = preload("res://scripts/rocket.gd")
const MineScript = preload("res://scripts/mine.gd")

@export var is_player: bool = false
@export var car_type: int = 0
@export var car_color: Color = Color("ff4d24")
@export var max_health: float = 110.0
@export var acceleration: float = 34.0
@export var reverse_acceleration: float = 17.0
@export var max_speed: float = 54.0
@export var max_reverse_speed: float = 14.0
@export var turn_rate: float = 2.35
@export var total_checkpoints: int = 12
@export var total_laps: int = 3
@export var starting_rockets: int = 1
@export var starting_mines: int = 1
@export var nitro_capacity: float = 100.0
@export var nitro_regen: float = 3.2
@export var gun_damage: float = 2.8
@export var rocket_damage: float = 31.0
@export var mine_damage: float = 38.0
@export var ai_aggression: float = 0.65
@export var ai_skill: float = 1.0
@export var special_cooldown_base: float = 20.0

var health: float = 110.0
var rockets: int = 1
var mines: int = 1
var nitro: float = 100.0
var shield_time: float = 0.0
var special_cooldown: float = 0.0
var boost_time: float = 0.0
var stun_time: float = 0.0
var dead: bool = false
var controls_enabled: bool = false
var ai_target: Node3D
var current_lap: int = 0
var next_checkpoint: int = 1
var last_attacker
var race_path: Array[Vector3] = []

var _speed: float = 0.0
var _vertical_velocity: float = 0.0
var _gun_cooldown: float = 0.0
var _rocket_cooldown: float = 0.0
var _mine_cooldown: float = 0.0
var _camera_shake: float = 0.0
var _last_impact_time: float = 0.0
var _dust_timer: float = 0.0
var _smoke_timer: float = 0.0
var _drift_charge: float = 0.0
var _was_drifting: bool = false
var _ai_path_index: int = 0
var _breakable_parts: Array = []
var _detached_parts: int = 0

var _mobile: Dictionary = {
    "left": false, "right": false, "go": false, "back": false,
    "fire": false, "rocket": false, "mine": false, "nitro": false,
    "special": false, "drift": false
}

var _turret: Node3D
var _muzzle: Marker3D
var _body_mesh: MeshInstance3D
var _wheel_nodes: Array[MeshInstance3D] = []
var _nitro_light: OmniLight3D
var _nitro_flames: Array[MeshInstance3D] = []
var _camera_arm: SpringArm3D
var _camera: Camera3D
var _dust_mat: StandardMaterial3D
var _smoke_mat: StandardMaterial3D
var _spark_mat: StandardMaterial3D
var _dark_mat: StandardMaterial3D
var _armor_mat: StandardMaterial3D
var _main_mat: StandardMaterial3D

func _ready() -> void:
    collision_layer = 1
    collision_mask = 1 | 2 | 4
    add_to_group("damageable")
    add_to_group("cars")
    health = max_health
    rockets = starting_rockets
    mines = starting_mines
    nitro = nitro_capacity
    _build_car()

func set_race_path(points: Array[Vector3]) -> void:
    race_path = points.duplicate()
    if not race_path.is_empty():
        _ai_path_index = _nearest_path_index(global_position)

func _physics_process(delta: float) -> void:
    if dead:
        return
    _gun_cooldown = maxf(0.0, _gun_cooldown - delta)
    _rocket_cooldown = maxf(0.0, _rocket_cooldown - delta)
    _mine_cooldown = maxf(0.0, _mine_cooldown - delta)
    special_cooldown = maxf(0.0, special_cooldown - delta)
    shield_time = maxf(0.0, shield_time - delta)
    boost_time = maxf(0.0, boost_time - delta)
    stun_time = maxf(0.0, stun_time - delta)
    _camera_shake = maxf(0.0, _camera_shake - delta * 2.8)

    var throttle: float = 0.0
    var steer: float = 0.0
    var want_fire: bool = false
    var want_rocket: bool = false
    var want_mine: bool = false
    var want_nitro: bool = false
    var want_special: bool = false
    var handbrake: bool = false

    if controls_enabled and stun_time <= 0.0:
        if is_player:
            throttle = Input.get_axis("brake", "accelerate")
            steer = Input.get_axis("steer_left", "steer_right")
            if bool(_mobile["go"]): throttle = 1.0
            if bool(_mobile["back"]): throttle = -1.0
            if bool(_mobile["left"]): steer = -1.0
            if bool(_mobile["right"]): steer = 1.0
            want_fire = Input.is_action_pressed("fire") or bool(_mobile["fire"])
            want_rocket = Input.is_action_just_pressed("rocket") or bool(_mobile["rocket"])
            want_mine = Input.is_action_just_pressed("mine") or bool(_mobile["mine"])
            want_nitro = Input.is_action_pressed("nitro") or bool(_mobile["nitro"])
            want_special = Input.is_action_just_pressed("special") or bool(_mobile["special"])
            handbrake = Input.is_action_pressed("handbrake") or bool(_mobile["drift"])
        else:
            var ai: Vector4 = _ai_controls()
            throttle = ai.x
            steer = ai.y
            want_fire = ai.z > 0.5
            want_rocket = ai.w > 0.5
            handbrake = absf(steer) > 0.70 and absf(_speed) > max_speed * 0.52
            want_nitro = absf(steer) < 0.25 and _speed > max_speed * 0.70 and nitro > 22.0 and randf() < 0.045
            want_mine = _mine_cooldown <= 0.0 and mines > 0 and randf() < 0.0007 * ai_aggression
            want_special = special_cooldown <= 0.0 and randf() < 0.0012 * ai_aggression

    var drifting: bool = handbrake and absf(_speed) > 18.0 and absf(steer) > 0.18 and is_on_floor()
    if drifting:
        _drift_charge = minf(2.8, _drift_charge + delta * (0.75 + absf(_speed) / maxf(1.0, max_speed)))
    elif _was_drifting:
        _release_drift_boost()
    else:
        _drift_charge = move_toward(_drift_charge, 0.0, delta * 1.5)
    _was_drifting = drifting

    var nitro_active: bool = want_nitro and throttle > 0.25 and nitro > 0.5
    var speed_multiplier: float = 1.0
    if nitro_active:
        speed_multiplier = 1.28
        nitro = maxf(0.0, nitro - 32.0 * delta)
    else:
        nitro = minf(nitro_capacity, nitro + nitro_regen * delta)
    if boost_time > 0.0:
        speed_multiplier *= 1.18
    _set_nitro_fx(nitro_active or boost_time > 0.0)

    var target_speed: float = max_speed * throttle * speed_multiplier if throttle >= 0.0 else max_reverse_speed * throttle
    var accel_mult: float = 1.0
    if nitro_active: accel_mult *= 1.35
    if boost_time > 0.0: accel_mult *= 1.25
    var accel_value: float = acceleration * accel_mult if throttle >= 0.0 else reverse_acceleration
    _speed = move_toward(_speed, target_speed, accel_value * delta)
    if absf(throttle) < 0.05:
        _speed = move_toward(_speed, 0.0, (6.0 if drifting else 3.0) * delta)

    var steer_power: float = clampf(absf(_speed) / 12.0, 0.15, 1.0)
    var reverse_sign: float = 1.0 if _speed >= 0.0 else -1.0
    var steering_mult: float = 1.30 if drifting else 1.0
    rotation.y -= steer * turn_rate * steer_power * reverse_sign * steering_mult * delta
    if drifting:
        _speed = move_toward(_speed, _speed * 0.94, 4.5 * delta)

    if not is_on_floor():
        _vertical_velocity -= 25.0 * delta
    else:
        _vertical_velocity = -0.9

    var forward: Vector3 = -global_transform.basis.z
    var right: Vector3 = global_transform.basis.x
    var lateral_factor: float = 0.18 if drifting else 0.018
    velocity = forward * _speed + right * steer * absf(_speed) * lateral_factor
    velocity.y = _vertical_velocity
    move_and_slide()
    _handle_impacts()
    _update_turret(delta)
    _update_camera_feedback(nitro_active)
    _update_trails(delta, drifting)

    for wheel in _wheel_nodes:
        if is_instance_valid(wheel):
            wheel.rotate_x(_speed * delta * 0.52)

    if want_fire: _fire_gun()
    if want_rocket:
        _fire_rocket()
        if is_player: _mobile["rocket"] = false
    if want_mine:
        _drop_mine()
        if is_player: _mobile["mine"] = false
    if want_special:
        _use_special()
        if is_player: _mobile["special"] = false

func _release_drift_boost() -> void:
    if _drift_charge < 0.35:
        _drift_charge = 0.0
        return
    var power: float = clampf(_drift_charge / 2.4, 0.18, 1.0)
    boost_time = maxf(boost_time, 0.35 + power * 0.85)
    _speed = maxf(_speed, max_speed * (0.82 + power * 0.18))
    nitro = minf(nitro_capacity, nitro + 6.0 + power * 10.0)
    drift_boosted.emit(self, power)
    _spawn_special_ring(Color("43d7ff"), 2.5 + power * 2.0)
    _drift_charge = 0.0

func apply_track_boost(seconds: float = 1.0) -> void:
    boost_time = maxf(boost_time, seconds)
    _speed = maxf(_speed, max_speed * 0.92)
    nitro = minf(nitro_capacity, nitro + 10.0)

func drift_charge_percent() -> float:
    return clampf(_drift_charge / 2.4, 0.0, 1.0)

func _ai_controls() -> Vector4:
    if not is_instance_valid(ai_target) or ai_target.dead:
        ai_target = _find_nearest_enemy()
    var desired: Vector3
    if race_path.size() >= 8:
        _ai_path_index = _nearest_path_index_local(global_position, _ai_path_index)
        var ahead: int = 5 + int(clampf(absf(_speed) / 15.0, 0.0, 4.0))
        var target_idx: int = (_ai_path_index + ahead) % race_path.size()
        desired = race_path[target_idx]
        var lateral_wave: float = sin(Time.get_ticks_msec() * 0.0007 + float(get_instance_id() % 17)) * 1.8
        var d: Vector3 = (race_path[(target_idx + 1) % race_path.size()] - race_path[target_idx]).normalized()
        var normal: Vector3 = Vector3(-d.z, 0.0, d.x)
        desired += normal * lateral_wave
    else:
        var angle: float = atan2(global_position.z, global_position.x) + 0.5
        desired = Vector3(cos(angle) * 50.0, global_position.y, sin(angle) * 50.0)
    var to_point: Vector3 = (desired - global_position).normalized()
    var local_dir: Vector3 = global_transform.basis.inverse() * to_point
    var steer: float = clampf(-local_dir.x * (2.25 + ai_skill * 0.18), -1.0, 1.0)
    var throttle: float = 1.0
    if absf(steer) > 0.70 and absf(_speed) > max_speed * 0.68:
        throttle = clampf(0.64 + ai_skill * 0.06, 0.62, 0.90)

    var fire: float = 0.0
    var rocket: float = 0.0
    if is_instance_valid(ai_target) and not ai_target.dead:
        var to_enemy: Vector3 = ai_target.global_position - global_position
        var dist: float = to_enemy.length()
        if dist < 28.0:
            var enemy_local: Vector3 = global_transform.basis.inverse() * to_enemy.normalized()
            if enemy_local.z < -0.45 and absf(enemy_local.x) < 0.42:
                fire = 1.0 if randf() < 0.55 * ai_aggression else 0.0
                if dist > 10.0 and rockets > 0 and _rocket_cooldown <= 0.0 and randf() < 0.010 * ai_aggression:
                    rocket = 1.0
    return Vector4(throttle, steer, fire, rocket)

func _nearest_path_index(pos: Vector3) -> int:
    var best_i: int = 0
    var best_d: float = INF
    for i in range(race_path.size()):
        var d: float = pos.distance_squared_to(race_path[i])
        if d < best_d:
            best_d = d
            best_i = i
    return best_i

func _nearest_path_index_local(pos: Vector3, around: int) -> int:
    if race_path.is_empty(): return 0
    var best_i: int = around % race_path.size()
    var best_d: float = INF
    for off in range(-4, 13):
        var idx: int = posmodi(around + off, race_path.size())
        var d: float = pos.distance_squared_to(race_path[idx])
        if d < best_d:
            best_d = d
            best_i = idx
    return best_i

func _find_nearest_enemy():
    var best
    var best_dist: float = INF
    for node in get_tree().get_nodes_in_group("cars"):
        if node == self or not is_instance_valid(node) or node.dead:
            continue
        var d: float = global_position.distance_squared_to(node.global_position)
        if d < best_dist:
            best_dist = d
            best = node
    return best

func _find_aim_target(max_dist: float, max_side: float):
    var best
    var best_score: float = INF
    for node in get_tree().get_nodes_in_group("cars"):
        if node == self or not is_instance_valid(node) or node.dead:
            continue
        var to_enemy: Vector3 = node.global_position - global_position
        var dist: float = to_enemy.length()
        if dist > max_dist or dist < 0.1: continue
        var local_dir: Vector3 = global_transform.basis.inverse() * to_enemy.normalized()
        if local_dir.z >= -0.20 or absf(local_dir.x) > max_side: continue
        var score: float = dist + absf(local_dir.x) * 35.0
        if score < best_score:
            best_score = score
            best = node
    return best

func _handle_impacts() -> void:
    var now: float = Time.get_ticks_msec() / 1000.0
    if now - _last_impact_time < 0.16: return
    for i in range(get_slide_collision_count()):
        var col: KinematicCollision3D = get_slide_collision(i)
        var collider = col.get_collider()
        var impact: float = absf(_speed)
        if impact > 14.0:
            if collider and collider.has_method("take_damage"):
                var ram_mult: float = 1.30 if car_type == 2 or car_type == 4 else 0.72
                collider.take_damage(maxf(0.0, impact - 16.0) * ram_mult, col.get_position(), self)
            take_damage(maxf(0.0, impact - 22.0) * (0.10 if car_type == 4 else 0.15), col.get_position(), collider)
            _speed *= 0.78
            _camera_shake = maxf(_camera_shake, 0.55)
            _spawn_hit_sparks(col.get_position(), 7)
            if impact > 34.0:
                _detach_one_random(col.get_position(), impact * 0.12)
            _last_impact_time = now
            break

func _make_materials() -> void:
    _main_mat = StandardMaterial3D.new()
    _main_mat.albedo_color = car_color
    _main_mat.metallic = 0.72
    _main_mat.roughness = 0.23
    _dark_mat = StandardMaterial3D.new()
    _dark_mat.albedo_color = Color("101319")
    _dark_mat.metallic = 0.84
    _dark_mat.roughness = 0.26
    _armor_mat = StandardMaterial3D.new()
    _armor_mat.albedo_color = Color("3e4650")
    _armor_mat.metallic = 0.90
    _armor_mat.roughness = 0.33
    _dust_mat = StandardMaterial3D.new()
    _dust_mat.albedo_color = Color(0.48, 0.39, 0.29, 0.38)
    _dust_mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    _dust_mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
    _smoke_mat = StandardMaterial3D.new()
    _smoke_mat.albedo_color = Color(0.08, 0.09, 0.11, 0.52)
    _smoke_mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    _smoke_mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
    _spark_mat = StandardMaterial3D.new()
    _spark_mat.albedo_color = Color("ffd27a")
    _spark_mat.emission_enabled = true
    _spark_mat.emission = Color("ff8b26")
    _spark_mat.emission_energy_multiplier = 7.0

func _build_car() -> void:
    _make_materials()
    var collision_size: Vector3 = Vector3(2.15, 1.15, 4.35)
    if car_type == 1: collision_size = Vector3(2.35, 1.35, 4.15)
    elif car_type == 2: collision_size = Vector3(2.45, 1.45, 5.0)
    elif car_type == 3: collision_size = Vector3(2.20, 0.95, 4.55)
    elif car_type == 4: collision_size = Vector3(2.65, 1.85, 5.35)
    elif car_type == 5: collision_size = Vector3(2.15, 1.25, 4.85)
    elif car_type == 6: collision_size = Vector3(2.55, 1.85, 4.85)
    var cs := CollisionShape3D.new()
    var shape := BoxShape3D.new()
    shape.size = collision_size
    cs.shape = shape
    cs.position.y = collision_size.y * 0.52
    add_child(cs)

    match car_type:
        1: _build_buggy()
        2: _build_pickup()
        3: _build_wedge()
        4: _build_heavy_truck()
        5: _build_hotrod()
        6: _build_armored_van()
        _: _build_interceptor()
    _build_wheels()
    _build_turret()
    _build_lights()
    _build_nitro()
    if is_player: _build_camera()

func _build_interceptor() -> void:
    _body_mesh = _add_box(Vector3(2.05, 0.55, 4.45), Vector3(0, 0.63, 0), _main_mat)
    _add_breakable_box("hood", Vector3(1.82, 0.16, 1.48), Vector3(0, 0.97, -1.32), _main_mat, 0.74)
    _add_box(Vector3(1.62, 0.64, 1.55), Vector3(0, 1.20, 0.36), _dark_mat)
    _add_breakable_box("door_l", Vector3(0.12, 0.58, 1.35), Vector3(-1.05, 0.95, 0.32), _main_mat, 0.54)
    _add_breakable_box("door_r", Vector3(0.12, 0.58, 1.35), Vector3(1.05, 0.95, 0.32), _main_mat, 0.46)
    _add_breakable_box("front_bumper", Vector3(2.28, 0.25, 0.24), Vector3(0, 0.52, -2.32), _armor_mat, 0.82)
    _add_breakable_box("rear_spoiler", Vector3(2.25, 0.12, 0.34), Vector3(0, 1.32, 1.88), _armor_mat, 0.34)

func _build_buggy() -> void:
    _body_mesh = _add_box(Vector3(1.95, 0.48, 3.75), Vector3(0, 0.62, 0.10), _main_mat)
    _add_breakable_box("nose", Vector3(1.72, 0.28, 1.15), Vector3(0, 0.88, -1.55), _main_mat, 0.75)
    _add_box(Vector3(1.45, 0.24, 1.35), Vector3(0, 1.20, 0.20), _dark_mat)
    for x in [-0.78, 0.78]:
        _add_cylinder(0.065, 1.45, Vector3(x, 1.55, 0.22), _armor_mat, Vector3(PI / 2.0, 0, 0))
        _add_cylinder(0.055, 1.30, Vector3(x, 1.55, 0.22), _armor_mat, Vector3(0, 0, 0.42 * (-1.0 if x < 0 else 1.0)))
    _add_cylinder(0.06, 1.65, Vector3(0, 1.92, 0.22), _armor_mat, Vector3(0, 0, PI / 2.0))
    _add_breakable_box("side_l", Vector3(0.16, 0.35, 2.2), Vector3(-1.06, 0.76, 0.15), _armor_mat, 0.56)
    _add_breakable_box("side_r", Vector3(0.16, 0.35, 2.2), Vector3(1.06, 0.76, 0.15), _armor_mat, 0.48)
    _add_breakable_box("rear_bar", Vector3(2.15, 0.18, 0.22), Vector3(0, 0.70, 2.0), _armor_mat, 0.32)

func _build_pickup() -> void:
    _body_mesh = _add_box(Vector3(2.30, 0.62, 4.80), Vector3(0, 0.68, 0.05), _main_mat)
    _add_box(Vector3(2.05, 0.84, 1.50), Vector3(0, 1.27, -0.55), _dark_mat)
    _add_box(Vector3(2.08, 0.18, 1.85), Vector3(0, 0.98, 1.40), _dark_mat)
    _add_breakable_box("hood", Vector3(2.05, 0.20, 1.10), Vector3(0, 1.03, -1.72), _main_mat, 0.74)
    _add_breakable_box("door_l", Vector3(0.13, 0.78, 1.40), Vector3(-1.16, 1.12, -0.48), _main_mat, 0.55)
    _add_breakable_box("door_r", Vector3(0.13, 0.78, 1.40), Vector3(1.16, 1.12, -0.48), _main_mat, 0.46)
    _add_breakable_box("ram", Vector3(2.65, 0.42, 0.38), Vector3(0, 0.60, -2.55), _armor_mat, 0.84)
    for x in [-0.82, 0.0, 0.82]:
        _add_box(Vector3(0.15, 0.15, 0.82), Vector3(x, 0.72, -2.85), _armor_mat, self, Vector3(-0.20, 0, 0))
    _add_breakable_box("tailgate", Vector3(2.12, 0.60, 0.14), Vector3(0, 0.91, 2.46), _main_mat, 0.34)

func _build_wedge() -> void:
    _body_mesh = _add_box(Vector3(2.08, 0.42, 4.55), Vector3(0, 0.55, 0), _main_mat)
    _add_box(Vector3(1.62, 0.46, 1.55), Vector3(0, 0.94, 0.30), _dark_mat, self, Vector3(-0.10, 0, 0))
    _add_breakable_box("nose", Vector3(1.92, 0.18, 1.60), Vector3(0, 0.77, -1.55), _main_mat, 0.70, self, Vector3(-0.08, 0, 0))
    _add_breakable_box("side_l", Vector3(0.13, 0.40, 2.35), Vector3(-1.08, 0.67, 0.10), _main_mat, 0.52)
    _add_breakable_box("side_r", Vector3(0.13, 0.40, 2.35), Vector3(1.08, 0.67, 0.10), _main_mat, 0.44)
    _add_breakable_box("wing", Vector3(2.48, 0.10, 0.36), Vector3(0, 1.15, 1.95), _armor_mat, 0.30)
    _add_box(Vector3(0.11, 0.52, 0.11), Vector3(-0.82, 0.94, 1.88), _armor_mat)
    _add_box(Vector3(0.11, 0.52, 0.11), Vector3(0.82, 0.94, 1.88), _armor_mat)

func _build_heavy_truck() -> void:
    _body_mesh = _add_box(Vector3(2.52, 0.76, 5.05), Vector3(0, 0.78, 0.05), _main_mat)
    _add_box(Vector3(2.25, 1.12, 1.70), Vector3(0, 1.55, -0.52), _dark_mat)
    _add_breakable_box("hood", Vector3(2.25, 0.22, 1.22), Vector3(0, 1.17, -1.85), _armor_mat, 0.78)
    _add_breakable_box("armor_l", Vector3(0.22, 0.78, 2.70), Vector3(-1.30, 1.06, 0.26), _armor_mat, 0.60)
    _add_breakable_box("armor_r", Vector3(0.22, 0.78, 2.70), Vector3(1.30, 1.06, 0.26), _armor_mat, 0.50)
    _add_breakable_box("ram", Vector3(2.95, 0.50, 0.42), Vector3(0, 0.68, -2.68), _armor_mat, 0.86)
    _add_breakable_box("rear_armor", Vector3(2.58, 0.62, 0.18), Vector3(0, 0.90, 2.62), _armor_mat, 0.36)
    for x in [-0.92, 0.92]:
        _add_cylinder(0.10, 1.55, Vector3(x, 1.62, 1.42), _dark_mat, Vector3(0, 0, 0))

func _build_hotrod() -> void:
    _body_mesh = _add_box(Vector3(1.78, 0.48, 4.35), Vector3(0, 0.61, 0.28), _main_mat)
    _add_box(Vector3(1.55, 0.58, 1.40), Vector3(0, 1.05, 0.65), _dark_mat)
    _add_breakable_box("grille", Vector3(1.50, 0.48, 0.18), Vector3(0, 0.75, -2.12), _armor_mat, 0.82)
    _add_breakable_box("door_l", Vector3(0.12, 0.50, 1.25), Vector3(-0.91, 0.91, 0.62), _main_mat, 0.52)
    _add_breakable_box("door_r", Vector3(0.12, 0.50, 1.25), Vector3(0.91, 0.91, 0.62), _main_mat, 0.44)
    for x in [-0.48, 0.0, 0.48]:
        _add_cylinder(0.18, 0.56, Vector3(x, 1.10, -1.15), _armor_mat, Vector3(PI / 2.0, 0, 0))
    for x in [-0.92, 0.92]:
        _add_cylinder(0.07, 1.45, Vector3(x, 1.06, -0.72), _dark_mat, Vector3(PI / 2.0, 0, 0))
    _add_breakable_box("rear_bumper", Vector3(2.12, 0.18, 0.22), Vector3(0, 0.55, 2.47), _armor_mat, 0.32)

func _build_armored_van() -> void:
    _body_mesh = _add_box(Vector3(2.42, 1.18, 4.72), Vector3(0, 1.02, 0.18), _main_mat)
    _add_box(Vector3(2.12, 0.58, 1.25), Vector3(0, 1.70, -1.18), _dark_mat, self, Vector3(-0.08, 0, 0))
    _add_breakable_box("front_plate", Vector3(2.48, 0.66, 0.20), Vector3(0, 0.95, -2.44), _armor_mat, 0.82)
    _add_breakable_box("side_l", Vector3(0.20, 0.95, 2.50), Vector3(-1.25, 1.16, 0.28), _armor_mat, 0.58)
    _add_breakable_box("side_r", Vector3(0.20, 0.95, 2.50), Vector3(1.25, 1.16, 0.28), _armor_mat, 0.48)
    _add_breakable_box("rear_doors", Vector3(2.16, 0.94, 0.18), Vector3(0, 1.18, 2.52), _main_mat, 0.34)
    _add_breakable_box("roof_rack", Vector3(2.20, 0.12, 2.60), Vector3(0, 1.76, 0.38), _armor_mat, 0.28)

func _build_wheels() -> void:
    var wheel_radius: float = 0.56
    var wheel_width: float = 0.42
    var z_front: float = -1.45
    var z_rear: float = 1.45
    var x_off: float = 1.16
    if car_type == 1:
        wheel_radius = 0.67; x_off = 1.28; z_front = -1.35; z_rear = 1.38
    elif car_type == 2:
        wheel_radius = 0.65; x_off = 1.29; z_front = -1.62; z_rear = 1.68
    elif car_type == 3:
        wheel_radius = 0.52; z_front = -1.48; z_rear = 1.52
    elif car_type == 4:
        wheel_radius = 0.72; wheel_width = 0.50; x_off = 1.38; z_front = -1.70; z_rear = 1.72
    elif car_type == 5:
        x_off = 1.10; z_front = -1.55; z_rear = 1.62
    elif car_type == 6:
        wheel_radius = 0.66; x_off = 1.32; z_front = -1.55; z_rear = 1.55
    for z in [z_front, z_rear]:
        for x in [-x_off, x_off]:
            var r: float = wheel_radius
            if car_type == 5 and z > 0.0: r = 0.74
            var wheel := MeshInstance3D.new()
            var cyl := CylinderMesh.new()
            cyl.top_radius = r; cyl.bottom_radius = r; cyl.height = wheel_width; cyl.radial_segments = 16
            wheel.mesh = cyl; wheel.material_override = _dark_mat
            wheel.position = Vector3(x, r, z); wheel.rotation.z = PI / 2.0
            add_child(wheel); _wheel_nodes.append(wheel)

func _build_turret() -> void:
    _turret = Node3D.new()
    var turret_y: float = 1.72
    if car_type == 4 or car_type == 6: turret_y = 2.22
    elif car_type == 3: turret_y = 1.35
    _turret.position = Vector3(0, turret_y, 0.0)
    add_child(_turret)
    var base := MeshInstance3D.new()
    var cb := CylinderMesh.new(); cb.top_radius = 0.36; cb.bottom_radius = 0.48; cb.height = 0.22; cb.radial_segments = 12
    base.mesh = cb; base.material_override = _dark_mat; _turret.add_child(base)
    var gun := MeshInstance3D.new()
    var gb := BoxMesh.new(); gb.size = Vector3(0.18, 0.18, 1.45)
    gun.mesh = gb; gun.material_override = _armor_mat; gun.position = Vector3(0, 0.10, -0.76); _turret.add_child(gun)
    _muzzle = Marker3D.new(); _muzzle.position = Vector3(0, 0.10, -1.56); _turret.add_child(_muzzle)

func _build_lights() -> void:
    var front := StandardMaterial3D.new(); front.albedo_color = Color("fff0c0"); front.emission_enabled = true; front.emission = Color("ffe099"); front.emission_energy_multiplier = 4.2
    var rear := StandardMaterial3D.new(); rear.albedo_color = Color("ff362b"); rear.emission_enabled = true; rear.emission = Color("ff1e15"); rear.emission_energy_multiplier = 4.0
    for x in [-0.62, 0.62]:
        _add_box(Vector3(0.34, 0.18, 0.08), Vector3(x, 0.78, -2.18), front)
        _add_box(Vector3(0.30, 0.16, 0.08), Vector3(x, 0.72, 2.18), rear)

func _build_nitro() -> void:
    _nitro_light = OmniLight3D.new(); _nitro_light.position = Vector3(0, 0.72, 2.0); _nitro_light.light_color = Color("38bfff"); _nitro_light.light_energy = 0.0; _nitro_light.omni_range = 5.0; add_child(_nitro_light)
    var flame_mat := StandardMaterial3D.new(); flame_mat.albedo_color = Color("69ddff"); flame_mat.emission_enabled = true; flame_mat.emission = Color("1698ff"); flame_mat.emission_energy_multiplier = 8.0
    for x in [-0.48, 0.48]:
        var flame := MeshInstance3D.new(); var fm := CylinderMesh.new(); fm.top_radius = 0.08; fm.bottom_radius = 0.24; fm.height = 1.25; fm.radial_segments = 10
        flame.mesh = fm; flame.rotation.x = PI / 2.0; flame.position = Vector3(x, 0.62, 2.58); flame.material_override = flame_mat; flame.visible = false; add_child(flame); _nitro_flames.append(flame)

func _build_camera() -> void:
    _camera_arm = SpringArm3D.new(); _camera_arm.spring_length = 10.5; _camera_arm.position = Vector3(0, 3.15, 1.10); _camera_arm.rotation_degrees.x = -10.0; _camera_arm.collision_mask = 2; add_child(_camera_arm)
    _camera = Camera3D.new(); _camera.fov = 76.0; _camera.current = true; _camera_arm.add_child(_camera)

func _add_box(size: Vector3, pos: Vector3, mat: Material, parent: Node = null, rot: Vector3 = Vector3.ZERO) -> MeshInstance3D:
    var target: Node = parent if parent != null else self
    var mi := MeshInstance3D.new(); var bm := BoxMesh.new(); bm.size = size
    mi.mesh = bm; mi.material_override = mat; mi.position = pos; mi.rotation = rot; target.add_child(mi); return mi

func _add_cylinder(radius: float, height: float, pos: Vector3, mat: Material, rot: Vector3 = Vector3.ZERO, parent: Node = null) -> MeshInstance3D:
    var target: Node = parent if parent != null else self
    var mi := MeshInstance3D.new(); var cm := CylinderMesh.new(); cm.top_radius = radius; cm.bottom_radius = radius; cm.height = height; cm.radial_segments = 10
    mi.mesh = cm; mi.material_override = mat; mi.position = pos; mi.rotation = rot; target.add_child(mi); return mi

func _add_breakable_box(id_text: String, size: Vector3, pos: Vector3, mat: Material, threshold: float, parent: Node = null, rot: Vector3 = Vector3.ZERO) -> MeshInstance3D:
    var mi: MeshInstance3D = _add_box(size, pos, mat, parent, rot)
    _breakable_parts.append({"id": id_text, "node": mi, "size": size, "mat": mat, "threshold": threshold, "detached": false})
    return mi

func _detach_one_random(hit_pos: Vector3, force: float) -> void:
    var choices: Array[int] = []
    for i in range(_breakable_parts.size()):
        var e: Dictionary = _breakable_parts[i]
        if not bool(e["detached"]): choices.append(i)
    if choices.is_empty(): return
    _detach_part(choices[randi() % choices.size()], hit_pos, force)

func _check_damage_parts(hit_pos: Vector3, applied: float) -> void:
    var ratio: float = health / maxf(1.0, max_health)
    for i in range(_breakable_parts.size()):
        var e: Dictionary = _breakable_parts[i]
        if not bool(e["detached"]) and ratio <= float(e["threshold"]):
            _detach_part(i, hit_pos, 2.4 + applied * 0.06)
            break
    if applied > 16.0 and randf() < clampf(applied / 55.0, 0.10, 0.65):
        _detach_one_random(hit_pos, 2.0 + applied * 0.08)

func _detach_part(index: int, hit_pos: Vector3, force: float) -> void:
    if index < 0 or index >= _breakable_parts.size(): return
    var e: Dictionary = _breakable_parts[index]
    if bool(e["detached"]): return
    var node: MeshInstance3D = e["node"]
    if not is_instance_valid(node):
        e["detached"] = true; _breakable_parts[index] = e; return
    var world_xform: Transform3D = node.global_transform
    var rb := RigidBody3D.new(); rb.mass = 0.8; rb.collision_layer = 4; rb.collision_mask = 1 | 2
    get_tree().current_scene.add_child(rb); rb.global_transform = world_xform
    var mesh := MeshInstance3D.new(); var bm := BoxMesh.new(); bm.size = e["size"]; mesh.mesh = bm; mesh.material_override = e["mat"]; rb.add_child(mesh)
    var cs := CollisionShape3D.new(); var bs := BoxShape3D.new(); bs.size = e["size"]; cs.shape = bs; rb.add_child(cs)
    var away: Vector3 = world_xform.origin - hit_pos
    if away.length_squared() < 0.05: away = Vector3(randf_range(-1, 1), 0.3, randf_range(-1, 1))
    away = away.normalized()
    rb.apply_central_impulse((away * force + Vector3.UP * (2.2 + force * 0.35)))
    rb.angular_velocity = Vector3(randf_range(-7, 7), randf_range(-8, 8), randf_range(-7, 7))
    node.queue_free(); e["detached"] = true; _breakable_parts[index] = e; _detached_parts += 1
    var t := rb.create_tween(); t.tween_interval(4.0); t.tween_property(rb, "scale", Vector3.ZERO, 0.45); t.tween_callback(rb.queue_free)

func _set_nitro_fx(active: bool) -> void:
    if is_instance_valid(_nitro_light): _nitro_light.light_energy = 7.0 if active else 0.0
    for flame in _nitro_flames:
        if is_instance_valid(flame):
            flame.visible = active
            if active: flame.scale = Vector3(1.0, randf_range(0.85, 1.35), 1.0)
    if is_instance_valid(_camera_arm): _camera_arm.spring_length = lerpf(_camera_arm.spring_length, 12.3 if active else 10.5, 0.11)

func _update_turret(delta: float) -> void:
    if not is_instance_valid(_turret): return
    var target = ai_target if not is_player else _find_aim_target(38.0, 0.52)
    var desired_y: float = 0.0
    if is_instance_valid(target):
        var local_dir: Vector3 = global_transform.basis.inverse() * (target.global_position - global_position)
        desired_y = clampf(atan2(-local_dir.x, -local_dir.z), -0.50, 0.50)
    _turret.rotation.y = lerp_angle(_turret.rotation.y, desired_y, clampf(delta * 5.0, 0.0, 1.0))

func _update_camera_feedback(nitro_active: bool) -> void:
    if not is_instance_valid(_camera): return
    var speed_ratio: float = clampf(absf(_speed) / maxf(1.0, max_speed), 0.0, 1.5)
    var target_fov: float = 76.0 + speed_ratio * 11.0 + (5.0 if nitro_active or boost_time > 0.0 else 0.0)
    _camera.fov = lerpf(_camera.fov, target_fov, 0.10)
    if is_instance_valid(_camera_arm):
        var shake: float = _camera_shake
        _camera_arm.position.x = randf_range(-shake, shake) * 0.16
        _camera_arm.position.y = 3.15 + randf_range(-shake, shake) * 0.10
        _camera_arm.position.z = 1.10

func _update_trails(delta: float, drifting: bool) -> void:
    _dust_timer -= delta; _smoke_timer -= delta
    if _dust_timer <= 0.0 and is_on_floor() and absf(_speed) > 14.0:
        _dust_timer = 0.07 if is_player else 0.14
        _spawn_dust(1.45 if drifting else 0.72)
    if _smoke_timer <= 0.0 and health < max_health * 0.58:
        _smoke_timer = 0.16 if health < max_health * 0.30 else 0.30
        _spawn_damage_smoke()

func _spawn_dust(scale_factor: float) -> void:
    for x in [-0.80, 0.80]:
        var puff := MeshInstance3D.new(); var sm := SphereMesh.new(); sm.radius = 0.15; sm.height = 0.30; puff.mesh = sm; puff.material_override = _dust_mat
        get_tree().current_scene.add_child(puff); puff.global_position = global_position + global_transform.basis.x * x + global_transform.basis.z * 1.55 + Vector3.UP * 0.22; puff.scale = Vector3.ONE * scale_factor
        var t := puff.create_tween().set_parallel(true); t.tween_property(puff, "scale", Vector3.ONE * scale_factor * 3.1, 0.42); t.tween_property(puff, "transparency", 1.0, 0.42); t.chain().tween_callback(puff.queue_free)

func _spawn_damage_smoke() -> void:
    var puff := MeshInstance3D.new(); var sm := SphereMesh.new(); sm.radius = 0.22; sm.height = 0.44; puff.mesh = sm; puff.material_override = _smoke_mat
    get_tree().current_scene.add_child(puff); puff.global_position = global_position + Vector3(0, 1.65, 0.25)
    var end_pos: Vector3 = puff.global_position + Vector3(randf_range(-0.4, 0.4), 1.8, randf_range(-0.4, 0.4))
    var t := puff.create_tween().set_parallel(true); t.tween_property(puff, "global_position", end_pos, 0.72); t.tween_property(puff, "scale", Vector3.ONE * 3.0, 0.72); t.tween_property(puff, "transparency", 1.0, 0.72); t.chain().tween_callback(puff.queue_free)

func _fire_gun() -> void:
    if _gun_cooldown > 0.0 or not is_instance_valid(_muzzle): return
    _gun_cooldown = 0.12
    var from: Vector3 = _muzzle.global_position
    var dir: Vector3 = -global_transform.basis.z
    var aim = _find_aim_target(38.0, 0.34 if is_player else 0.42)
    if is_instance_valid(aim): dir = (aim.global_position + Vector3.UP * 0.70 - from).normalized()
    var to: Vector3 = from + dir * 50.0
    var query := PhysicsRayQueryParameters3D.create(from, to); query.exclude = [get_rid()]; query.collision_mask = 1 | 2
    var hit: Dictionary = get_world_3d().direct_space_state.intersect_ray(query)
    var end: Vector3 = to
    if not hit.is_empty():
        end = hit.position
        var target = hit.collider
        if target and target.has_method("take_damage"): target.take_damage(gun_damage, hit.position, self)
        _spawn_hit_sparks(hit.position, 3)
    _spawn_muzzle_flash(from); _spawn_tracer(from, end)

func _fire_rocket() -> void:
    if _rocket_cooldown > 0.0 or rockets <= 0 or not is_instance_valid(_muzzle): return
    rockets -= 1; _rocket_cooldown = 1.15
    var rocket = RocketScript.new(); rocket.owner_car = self; rocket.damage = rocket_damage; rocket.target = ai_target if not is_player else _find_aim_target(52.0, 0.60)
    get_tree().current_scene.add_child(rocket); rocket.global_position = _muzzle.global_position + (-global_transform.basis.z) * 0.5; rocket.direction = -global_transform.basis.z

func _drop_mine() -> void:
    if _mine_cooldown > 0.0 or mines <= 0: return
    mines -= 1; _mine_cooldown = 1.2
    var mine = MineScript.new(); mine.owner_car = self; mine.damage = mine_damage; get_tree().current_scene.add_child(mine); mine.global_position = global_position + global_transform.basis.z * 2.5 + Vector3.UP * 0.18

func _use_special() -> void:
    if special_cooldown > 0.0 or dead: return
    special_cooldown = special_cooldown_base
    if car_type == 1 or car_type == 3:
        nitro = nitro_capacity; boost_time = 2.4; _speed = maxf(_speed, max_speed * 1.08); _spawn_special_ring(Color("4ecbff"), 5.0)
    elif car_type == 2:
        _shockwave(6.5, 28.0, Color("ff7a30"))
    elif car_type == 4 or car_type == 6:
        health = minf(max_health, health + max_health * 0.20); shield_time = maxf(shield_time, 5.0); _spawn_special_ring(Color("6cff9a"), 5.5)
    else:
        boost_time = 1.6; nitro = minf(nitro_capacity, nitro + 35.0); _spawn_special_ring(Color("ffd45b"), 4.8)
    special_used.emit(self, special_name())

func _shockwave(radius: float, damage: float, color: Color) -> void:
    for node in get_tree().get_nodes_in_group("cars"):
        if node == self or not is_instance_valid(node) or node.dead: continue
        var d: float = global_position.distance_to(node.global_position)
        if d <= radius and node.has_method("take_damage"): node.take_damage(damage * maxf(0.25, 1.0 - d / radius), global_position, self)
    _spawn_special_ring(color, radius); _camera_shake = 0.75

func _spawn_special_ring(color: Color, radius: float) -> void:
    var ring := MeshInstance3D.new(); var torus := TorusMesh.new(); torus.inner_radius = 0.52; torus.outer_radius = 0.72; torus.rings = 18; torus.ring_segments = 8; ring.mesh = torus; ring.rotation.x = PI / 2.0
    var mat := StandardMaterial3D.new(); mat.albedo_color = color; mat.emission_enabled = true; mat.emission = color; mat.emission_energy_multiplier = 6.0; mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    ring.material_override = mat; get_tree().current_scene.add_child(ring); ring.global_position = global_position + Vector3.UP * 0.35; ring.scale = Vector3.ONE * 0.3
    var t := ring.create_tween().set_parallel(true); t.tween_property(ring, "scale", Vector3.ONE * radius, 0.34); t.tween_property(ring, "transparency", 1.0, 0.36); t.chain().tween_callback(ring.queue_free)

func special_name() -> String:
    match car_type:
        1: return "БАГГИ-ФОРСАЖ"
        2: return "ТАРАННЫЙ ИМПУЛЬС"
        3: return "ПЕРЕХВАТ"
        4: return "ТЯЖЁЛАЯ БРОНЯ"
        5: return "ХОТ-РОД БУСТ"
        6: return "БРОНЕЩИТ"
        _: return "ШТУРМОВОЙ БУСТ"

func vehicle_name() -> String:
    match car_type:
        1: return "RAT BUGGY"
        2: return "RAM PICKUP"
        3: return "NEEDLE GT"
        4: return "JUGGERNAUT"
        5: return "HELLROD"
        6: return "WAR VAN"
        _: return "INTERCEPTOR"

func apply_stun(seconds: float) -> void:
    stun_time = maxf(stun_time, seconds); _speed *= 0.78

func apply_burn(_seconds: float, source = null) -> void:
    if source != null: last_attacker = source

func collect_pickup(kind: int, amount: float) -> void:
    if dead: return
    if kind == 0: health = minf(max_health, health + (amount if amount > 0.0 else 28.0))
    elif kind == 1:
        rockets += int(amount if amount > 0.0 else 2.0); mines += 1
    elif kind == 2: nitro = minf(nitro_capacity, nitro + (amount if amount > 0.0 else 50.0))
    elif kind == 3: shield_time = maxf(shield_time, amount if amount > 0.0 else 6.0)
    elif kind == 4: special_cooldown = maxf(0.0, special_cooldown - (amount if amount > 0.0 else 8.0))
    pickup_collected.emit(self, kind)

func take_damage(amount: float, hit_position: Vector3 = Vector3.ZERO, source = null) -> void:
    if dead: return
    if source != null and source != self: last_attacker = source
    var applied: float = amount * (0.52 if shield_time > 0.0 else 1.0)
    health -= applied; _camera_shake = maxf(_camera_shake, clampf(applied / 22.0, 0.12, 0.95)); _flash_damage(); _check_damage_parts(hit_position, applied); damage_taken.emit(self, applied)
    if health <= 0.0: _die(hit_position, source)

func _flash_damage() -> void:
    if not is_instance_valid(_body_mesh): return
    var old_scale: Vector3 = _body_mesh.scale
    var t := create_tween(); t.tween_property(_body_mesh, "scale", old_scale * 1.045, 0.04); t.tween_property(_body_mesh, "scale", old_scale, 0.07)

func _die(hit_position: Vector3, source) -> void:
    dead = true
    if source != null and source != self: last_attacker = source
    for i in range(_breakable_parts.size()):
        var e: Dictionary = _breakable_parts[i]
        if not bool(e["detached"]): _detach_part(i, hit_position, randf_range(3.5, 6.5))
    _spawn_explosion(global_position, 1.65); car_destroyed.emit(self, last_attacker); visible = false; collision_layer = 0; collision_mask = 0; set_physics_process(false)

func _spawn_muzzle_flash(pos: Vector3) -> void:
    var flash := MeshInstance3D.new(); var sm := SphereMesh.new(); sm.radius = 0.10; sm.height = 0.20; flash.mesh = sm; flash.material_override = _spark_mat; get_tree().current_scene.add_child(flash); flash.global_position = pos
    var t := flash.create_tween(); t.tween_interval(0.04); t.tween_callback(flash.queue_free)

func _spawn_tracer(from: Vector3, to: Vector3) -> void:
    var tracer := MeshInstance3D.new(); var mesh := BoxMesh.new(); var length: float = from.distance_to(to); mesh.size = Vector3(0.045, 0.045, length); tracer.mesh = mesh; tracer.material_override = _spark_mat; get_tree().current_scene.add_child(tracer); tracer.global_position = (from + to) * 0.5; tracer.look_at(to, Vector3.UP)
    var t := tracer.create_tween(); t.tween_interval(0.04); t.tween_callback(tracer.queue_free)

func _spawn_hit_sparks(pos: Vector3, count: int) -> void:
    for i in range(count):
        var spark := MeshInstance3D.new(); var bm := BoxMesh.new(); bm.size = Vector3(0.035, 0.035, randf_range(0.18, 0.42)); spark.mesh = bm; spark.material_override = _spark_mat; get_tree().current_scene.add_child(spark); spark.global_position = pos; spark.rotation = Vector3(randf() * PI, randf() * PI, randf() * PI)
        var target: Vector3 = pos + Vector3(randf_range(-1.1, 1.1), randf_range(0.4, 1.6), randf_range(-1.1, 1.1)); var t := spark.create_tween().set_parallel(true); t.tween_property(spark, "global_position", target, 0.26); t.tween_property(spark, "transparency", 1.0, 0.28); t.chain().tween_callback(spark.queue_free)

func _spawn_explosion(pos: Vector3, scale_factor: float) -> void:
    var root := get_tree().current_scene
    var flash := OmniLight3D.new(); flash.light_color = Color("ff6b20"); flash.light_energy = 12.0 * scale_factor; flash.omni_range = 10.0 * scale_factor; flash.position = pos; root.add_child(flash)
    var sphere := MeshInstance3D.new(); var sm := SphereMesh.new(); sm.radius = 0.55; sm.height = 1.1; sphere.mesh = sm
    var mat := StandardMaterial3D.new(); mat.albedo_color = Color("ff6a19"); mat.emission_enabled = true; mat.emission = Color("ff3100"); mat.emission_energy_multiplier = 7.0; mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA; sphere.material_override = mat; sphere.position = pos; sphere.scale = Vector3.ONE * 0.4; root.add_child(sphere)
    var t := sphere.create_tween().set_parallel(true); t.tween_property(sphere, "scale", Vector3.ONE * scale_factor * 3.7, 0.34); t.tween_property(sphere, "transparency", 1.0, 0.36); t.chain().tween_callback(sphere.queue_free)
    var tf := flash.create_tween(); tf.tween_property(flash, "light_energy", 0.0, 0.31); tf.tween_callback(flash.queue_free)

func register_checkpoint(index: int) -> void:
    if dead or not controls_enabled or index != next_checkpoint: return
    if index == 0:
        current_lap += 1; next_checkpoint = 1; lap_completed.emit(self, current_lap)
        if current_lap >= total_laps: race_won.emit(self)
    else:
        next_checkpoint += 1
        if next_checkpoint >= total_checkpoints: next_checkpoint = 0

func set_mobile_action(action: String, pressed: bool) -> void:
    if _mobile.has(action): _mobile[action] = pressed
