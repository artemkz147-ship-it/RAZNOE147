from pathlib import Path
import re

rocket = Path('wasteland/scripts/rocket.gd')
r = rocket.read_text()
old = 'var desired := (target.global_position + Vector3.UP * 0.65 - global_position).normalized()'
new = 'var desired: Vector3 = (target.global_position + Vector3.UP * 0.65 - global_position).normalized()'
if old in r:
    r = r.replace(old, new)
rocket.write_text(r)

car_path = Path('wasteland/scripts/combat_car.gd')
car = car_path.read_text()
if 'var _external_model: Node3D' not in car:
    car = car.replace('var _camera: Camera3D\n', 'var _camera: Camera3D\nvar _external_model: Node3D\n', 1)

start = car.index('func _build_car() -> void:')
end = car.index('\nfunc _build_interceptor()', start)
new_build = r'''func _build_car() -> void:
    _make_materials()
    var collision_size: Vector3 = Vector3(2.15, 1.15, 4.35)
    if car_type == 1: collision_size = Vector3(2.20, 1.20, 4.10)
    elif car_type == 2: collision_size = Vector3(2.45, 1.45, 4.95)
    elif car_type == 3: collision_size = Vector3(2.18, 1.00, 4.55)
    elif car_type == 4: collision_size = Vector3(2.65, 1.85, 5.45)
    elif car_type == 5: collision_size = Vector3(2.42, 1.65, 4.90)
    var cs := CollisionShape3D.new()
    var shape := BoxShape3D.new()
    shape.size = collision_size
    cs.shape = shape
    cs.position.y = collision_size.y * 0.52
    add_child(cs)

    _build_external_vehicle()
    _build_damage_armor()
    _build_turret()
    _build_lights()
    _build_nitro()
    if is_player: _build_camera()

func _build_external_vehicle() -> void:
    var paths: Array[String] = [
        "res://assets/kenney/vehicles/race.glb",
        "res://assets/kenney/vehicles/hatchback-sports.glb",
        "res://assets/kenney/vehicles/suv.glb",
        "res://assets/kenney/vehicles/race-future.glb",
        "res://assets/kenney/vehicles/firetruck.glb",
        "res://assets/kenney/vehicles/delivery.glb"
    ]
    var idx: int = clampi(car_type, 0, paths.size() - 1)
    var packed: PackedScene = load(paths[idx]) as PackedScene
    if packed == null:
        _body_mesh = _add_box(Vector3(2.0, 0.65, 4.2), Vector3(0, 0.72, 0), _main_mat)
        _build_wheels()
        return
    _external_model = packed.instantiate() as Node3D
    if _external_model == null:
        _body_mesh = _add_box(Vector3(2.0, 0.65, 4.2), Vector3(0, 0.72, 0), _main_mat)
        _build_wheels()
        return
    _external_model.name = "VehicleModel"
    add_child(_external_model)
    var scales: Array[float] = [1.10, 1.10, 1.08, 1.14, 0.94, 1.02]
    _external_model.scale = Vector3.ONE * scales[idx]
    _external_model.position = Vector3(0.0, 0.04, 0.0)
    _body_mesh = _find_first_mesh(_external_model)

func _find_first_mesh(root: Node) -> MeshInstance3D:
    if root is MeshInstance3D:
        return root as MeshInstance3D
    for child in root.get_children():
        var found: MeshInstance3D = _find_first_mesh(child)
        if found != null:
            return found
    return null

func _build_damage_armor() -> void:
    var idx: int = clampi(car_type, 0, 5)
    var half_x: Array[float] = [1.08, 1.06, 1.22, 1.08, 1.32, 1.20]
    var half_z: Array[float] = [2.20, 2.10, 2.50, 2.28, 2.72, 2.45]
    var hx: float = half_x[idx]
    var hz: float = half_z[idx]
    _add_breakable_box("front_bumper", Vector3(hx * 1.95, 0.20, 0.18), Vector3(0, 0.52, -hz), _armor_mat, 0.82)
    _add_breakable_box("rear_bumper", Vector3(hx * 1.88, 0.18, 0.16), Vector3(0, 0.54, hz), _armor_mat, 0.38)
    _add_breakable_box("door_l", Vector3(0.12, 0.56, 1.35), Vector3(-hx - 0.04, 0.93, 0.18), _main_mat, 0.62)
    _add_breakable_box("door_r", Vector3(0.12, 0.56, 1.35), Vector3(hx + 0.04, 0.93, 0.18), _main_mat, 0.54)
    if car_type == 0 or car_type == 3:
        _add_breakable_box("spoiler", Vector3(hx * 1.78, 0.11, 0.28), Vector3(0, 1.25, hz - 0.25), _armor_mat, 0.30)
    elif car_type == 2 or car_type == 4:
        _add_breakable_box("ram", Vector3(hx * 2.05, 0.38, 0.34), Vector3(0, 0.62, -hz - 0.12), _armor_mat, 0.72)
    else:
        _add_breakable_box("roof_plate", Vector3(hx * 1.55, 0.10, 1.45), Vector3(0, 1.55, 0.22), _armor_mat, 0.33)

func _update_external_visual(delta: float, steer: float, drifting: bool) -> void:
    if not is_instance_valid(_external_model):
        return
    var speed_ratio: float = clampf(absf(_speed) / maxf(1.0, max_speed), 0.0, 1.4)
    var target_roll: float = -steer * speed_ratio * (0.16 if drifting else 0.09)
    var target_pitch: float = -clampf(_speed / maxf(1.0, max_speed), -1.0, 1.0) * 0.018
    _external_model.rotation.z = lerpf(_external_model.rotation.z, target_roll, clampf(delta * 7.0, 0.0, 1.0))
    _external_model.rotation.x = lerpf(_external_model.rotation.x, target_pitch, clampf(delta * 5.0, 0.0, 1.0))
    var road_bob: float = sin(Time.get_ticks_msec() * 0.018) * 0.015 * speed_ratio
    _external_model.position.y = lerpf(_external_model.position.y, 0.04 + road_bob, clampf(delta * 8.0, 0.0, 1.0))
'''
car = car[:start] + new_build + car[end:]

trail_marker = '    _update_trails(delta, drifting)\n'
if '_update_external_visual(delta, steer, drifting)' not in car:
    car = car.replace(trail_marker, trail_marker + '    _update_external_visual(delta, steer, drifting)\n', 1)

dstart = car.index('func _detach_part(index: int, hit_pos: Vector3, force: float) -> void:')
dend = car.index('\nfunc _set_nitro_fx', dstart)
detach_block = r'''func _debris_path_for_id(id_text: String) -> String:
    if id_text.contains("door"):
        return "res://assets/kenney/debris/debris-door.glb"
    if id_text.contains("bumper") or id_text.contains("ram") or id_text.contains("grille"):
        return "res://assets/kenney/debris/debris-bumper.glb"
    if id_text.contains("spoiler") or id_text.contains("roof"):
        return "res://assets/kenney/debris/debris-spoiler-a.glb"
    return "res://assets/kenney/debris/debris-plate-a.glb"

func _spawn_debris_body(scene_path: String, xform: Transform3D, hit_pos: Vector3, force: float, scale_factor: float = 1.0) -> RigidBody3D:
    var rb := RigidBody3D.new()
    rb.mass = 0.8
    rb.collision_layer = 4
    rb.collision_mask = 1 | 2
    get_tree().current_scene.add_child(rb)
    rb.global_transform = xform
    var packed: PackedScene = load(scene_path) as PackedScene
    if packed != null:
        var visual: Node = packed.instantiate()
        rb.add_child(visual)
        if visual is Node3D:
            (visual as Node3D).scale = Vector3.ONE * scale_factor
    var cs := CollisionShape3D.new()
    var bs := BoxShape3D.new()
    bs.size = Vector3(0.9, 0.35, 1.1) * scale_factor
    cs.shape = bs
    rb.add_child(cs)
    var away: Vector3 = xform.origin - hit_pos
    if away.length_squared() < 0.05:
        away = Vector3(randf_range(-1.0, 1.0), 0.35, randf_range(-1.0, 1.0))
    away = away.normalized()
    rb.apply_central_impulse(away * force + Vector3.UP * (2.4 + force * 0.34))
    rb.angular_velocity = Vector3(randf_range(-7.0, 7.0), randf_range(-8.0, 8.0), randf_range(-7.0, 7.0))
    var t := rb.create_tween()
    t.tween_interval(5.0)
    t.tween_property(rb, "scale", Vector3.ZERO, 0.45)
    t.tween_callback(rb.queue_free)
    return rb

func _detach_part(index: int, hit_pos: Vector3, force: float) -> void:
    if index < 0 or index >= _breakable_parts.size():
        return
    var e: Dictionary = _breakable_parts[index]
    if bool(e["detached"]):
        return
    var node: MeshInstance3D = e["node"]
    if not is_instance_valid(node):
        e["detached"] = true
        _breakable_parts[index] = e
        return
    var world_xform: Transform3D = node.global_transform
    var scene_path: String = _debris_path_for_id(String(e["id"]))
    _spawn_debris_body(scene_path, world_xform, hit_pos, force, 1.08)
    node.queue_free()
    e["detached"] = true
    _breakable_parts[index] = e
    _detached_parts += 1

func _spawn_bonus_debris(origin: Vector3, force: float) -> void:
    var tire_scene: String = "res://assets/kenney/debris/debris-tire.glb"
    var drive_scene: String = "res://assets/kenney/debris/debris-drivetrain.glb"
    for side in [-1.0, 1.0]:
        var tx := Transform3D(global_transform.basis, origin + global_transform.basis.x * side * 0.85 + Vector3.UP * 0.45)
        _spawn_debris_body(tire_scene, tx, origin - global_transform.basis.z, force * 0.9, 0.95)
    var dx := Transform3D(global_transform.basis, origin + Vector3.UP * 0.55)
    _spawn_debris_body(drive_scene, dx, origin + global_transform.basis.z, force * 0.75, 0.82)
'''
car = car[:dstart] + detach_block + car[dend:]

death_old = '    _spawn_explosion(global_position, 1.65); car_destroyed.emit(self, last_attacker); visible = false; collision_layer = 0; collision_mask = 0; set_physics_process(false)'
death_new = '    _spawn_bonus_debris(global_position, 5.6); _spawn_explosion(global_position, 1.65); car_destroyed.emit(self, last_attacker); visible = false; collision_layer = 0; collision_mask = 0; set_physics_process(false)'
if death_old in car:
    car = car.replace(death_old, death_new, 1)

car = car.replace('@export var gun_damage: float = 2.8', '@export var gun_damage: float = 2.1', 1)
car = car.replace('_gun_cooldown = 0.12', '_gun_cooldown = 0.15', 1)
car_path.write_text(car)

game_path = Path('wasteland/scripts/game.gd')
game = game_path.read_text()
if 'var track_style: int = 0' not in game:
    game = game.replace('var race_started: bool = false\n', 'var race_started: bool = false\nvar track_style: int = 0\n', 1)
if 'track_style = randi() % 3' not in game:
    game = game.replace('    randomize()\n', '    randomize()\n    track_style = randi() % 3\n', 1)
game = game.replace('hud.update_status(player, alive, LAPS, "RUST CIRCUIT", "ГОНКА • таран и оружие помогают обгонять", _elapsed(), 0.0)', 'hud.update_status(player, alive, LAPS, _track_title(), "ГОНКА • скорость, дрифт, таран и оружие", _elapsed(), 0.0)', 1)

pstart = game.index('func _make_path() -> void:')
pend = game.index('\nfunc _build_world()', pstart)
path_block = r'''func _track_title() -> String:
    match track_style:
        1: return "IRON QUARRY"
        2: return "NEON DOCKS"
        _: return "RUST CIRCUIT"

func _make_path() -> void:
    path_points.clear()
    for i in range(PATH_SEGMENTS):
        var a: float = TAU * float(i) / float(PATH_SEGMENTS)
        var radial: float
        var x_scale: float
        var z_scale: float
        match track_style:
            1:
                radial = 62.0 + sin(a * 3.0 + 0.35) * 11.0 + sin(a * 7.0) * 3.5
                x_scale = 0.98
                z_scale = 1.02
            2:
                radial = 57.0 + sin(a * 2.0 + 0.65) * 7.5 + sin(a * 6.0 - 0.30) * 5.0
                x_scale = 1.30
                z_scale = 0.74
            _:
                radial = 59.0 + sin(a * 2.0) * 10.0 + sin(a * 5.0 + 0.55) * 4.5
                x_scale = 1.14
                z_scale = 0.88
        var x: float = cos(a) * radial * x_scale
        var z: float = sin(a) * radial * z_scale
        path_points.append(Vector3(x, 0.04, z))
'''
game = game[:pstart] + path_block + game[pend:]

old_trackside_tail = '''    for i in range(14):
        var a: float = TAU * float(i) / 14.0 + 0.42
        var pos := Vector3(cos(a) * 40.0, 1.0, sin(a) * 34.0)
        _static_box("Container_%02d" % i, pos, Vector3(5.5, 2.0, 2.3), Vector3(0, a, 0), _mat(Color.from_hsv(fmod(float(i) * 0.11, 1.0), 0.55, 0.48), 0.72, 0.38))
'''
if old_trackside_tail in game and '_spawn_imported_city()' not in game:
    game = game.replace(old_trackside_tail, old_trackside_tail + '    _spawn_imported_city()\n', 1)

insert_at = game.index('\nfunc _spawn_destructibles()')
city_block = r'''
func _spawn_imported_city() -> void:
    var buildings: Array[String] = [
        "res://assets/kenney/city/building-type-a.glb",
        "res://assets/kenney/city/building-type-b.glb",
        "res://assets/kenney/city/building-type-c.glb",
        "res://assets/kenney/city/building-type-d.glb",
        "res://assets/kenney/city/building-type-e.glb",
        "res://assets/kenney/city/building-type-f.glb",
        "res://assets/kenney/city/building-type-g.glb",
        "res://assets/kenney/city/building-type-h.glb"
    ]
    for i in range(20):
        var packed: PackedScene = load(buildings[i % buildings.size()]) as PackedScene
        if packed == null:
            continue
        var inst: Node = packed.instantiate()
        if not (inst is Node3D):
            inst.queue_free()
            continue
        var n := inst as Node3D
        add_child(n)
        var a: float = TAU * float(i) / 20.0 + 0.12 * float(track_style)
        var r: float = 102.0 + float((i * 13) % 28)
        n.position = Vector3(cos(a) * r, -0.15, sin(a) * r * (0.86 if track_style != 1 else 1.0))
        n.rotation.y = -a + PI * 0.5
        var s: float = 1.25 + float(i % 4) * 0.12
        n.scale = Vector3.ONE * s

    var fence_packed: PackedScene = load("res://assets/kenney/city/fence.glb") as PackedScene
    if fence_packed != null:
        for idx in range(0, PATH_SEGMENTS, 7):
            var p: Vector3 = path_points[idx]
            var np: Vector3 = path_points[(idx + 1) % PATH_SEGMENTS]
            var dir: Vector3 = (np - p).normalized()
            var normal: Vector3 = Vector3(-dir.z, 0.0, dir.x)
            var inst: Node = fence_packed.instantiate()
            if inst is Node3D:
                var f := inst as Node3D
                add_child(f)
                f.position = p + normal * (TRACK_WIDTH * 0.72) + Vector3.UP * 0.02
                f.look_at(np + normal * (TRACK_WIDTH * 0.72), Vector3.UP)
                f.scale = Vector3.ONE * 1.35
            else:
                inst.queue_free()
'''
if 'func _spawn_imported_city() -> void:' not in game:
    game = game[:insert_at] + city_block + game[insert_at:]
game_path.write_text(game)

project = Path('wasteland/project.godot')
t = project.read_text()
wrong = 'rendering/textures/vram_compression/import_etc2_astc=true'
correct = 'textures/vram_compression/import_etc2_astc=true'
t = t.replace(wrong + '\n', '')
if correct not in t:
    if '[rendering]\n' not in t:
        raise SystemExit('Missing [rendering] section')
    t = t.replace('[rendering]\n', '[rendering]\n' + correct + '\n', 1)
project.write_text(t)

preset = Path('wasteland/export_presets.cfg')
p = preset.read_text()
p = re.sub(r'package/unique_name="[^"]+"', 'package/unique_name="ru.openai148.wastelandcircuit.race15"', p)
p = re.sub(r'package/name="[^"]+"', 'package/name="Wasteland Circuit Race"', p)
p = re.sub(r'version/code=\d+', 'version/code=15', p)
p = re.sub(r'version/name="[^"]+"', 'version/name="1.5.0"', p)
p = re.sub(r'export_path="[^"]+"', 'export_path="builds/Wasteland-Circuit-Race-v1.5.0.apk"', p)
preset.write_text(p)
