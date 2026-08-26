extends Node3D

# One shared 50x50 m valley for every dinosaur. The visible mountains are actual
# 3D rock geometry; the sky is procedural rather than a panorama/background image.
const FERN_PATH := "res://assets/environments/hell_creek/models/fern_02.glb"
const LOG_PATH := "res://assets/environments/hell_creek/models/dead_tree_trunk.glb"
const STUMP_PATH := "res://assets/environments/hell_creek/models/tree_stump_01.glb"
const ROCKS_PATH := "res://assets/environments/hell_creek/models/rock_moss_set_01.glb"
const SHRUB_PATH := "res://assets/environments/hell_creek/models/shrub_03.glb"
const MUD_DIFF := "res://assets/environments/hell_creek/textures/mud_forest_diff_1k.jpg"
const MUD_NORMAL := "res://assets/environments/hell_creek/textures/mud_forest_nor_gl_1k.jpg"
const MUD_ROUGH := "res://assets/environments/hell_creek/textures/mud_forest_rough_1k.jpg"

var _assets: Dictionary = {}

func _ready() -> void:
    _load_assets()
    _create_environment()
    _create_lighting()
    _create_meadow()
    _dress_clearing()
    _create_tree_ring()
    _create_mountain_ring()

func _load_assets() -> void:
    for entry in [["fern", FERN_PATH], ["log", LOG_PATH], ["stump", STUMP_PATH], ["rocks", ROCKS_PATH], ["shrub", SHRUB_PATH]]:
        var key := str(entry[0])
        var path := str(entry[1])
        if ResourceLoader.exists(path):
            var packed := load(path) as PackedScene
            if packed != null:
                _assets[key] = packed

func _create_environment() -> void:
    var world := WorldEnvironment.new()
    world.name = "WorldEnvironment"
    var env := Environment.new()
    env.background_mode = Environment.BG_SKY
    env.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
    env.reflected_light_source = Environment.REFLECTION_SOURCE_SKY
    env.ambient_light_energy = 0.78
    env.tonemap_mode = Environment.TONE_MAPPER_ACES
    env.tonemap_exposure = 1.04
    env.fog_enabled = true
    env.fog_light_color = Color(0.72, 0.80, 0.83)
    env.fog_light_energy = 0.58
    env.fog_density = 0.0038
    env.fog_height = 2.0
    env.fog_height_density = 0.055
    env.fog_aerial_perspective = 0.34
    env.fog_sky_affect = 0.24

    var sky := Sky.new()
    sky.radiance_size = Sky.RADIANCE_SIZE_256
    var material := ProceduralSkyMaterial.new()
    material.sky_top_color = Color(0.18, 0.39, 0.68)
    material.sky_horizon_color = Color(0.72, 0.83, 0.90)
    material.ground_bottom_color = Color(0.08, 0.12, 0.07)
    material.ground_horizon_color = Color(0.28, 0.37, 0.24)
    material.sun_angle_max = 14.0
    material.sun_curve = 0.07
    sky.sky_material = material
    env.sky = sky
    world.environment = env
    add_child(world)

func _create_lighting() -> void:
    var sun := DirectionalLight3D.new()
    sun.name = "Sun"
    sun.rotation_degrees = Vector3(-48, -35, -2)
    sun.light_color = Color(1.0, 0.93, 0.80)
    sun.light_energy = 1.9
    sun.shadow_enabled = true
    sun.directional_shadow_max_distance = 130.0
    add_child(sun)

    var fill := DirectionalLight3D.new()
    fill.name = "SkyFill"
    fill.rotation_degrees = Vector3(-26, 145, 0)
    fill.light_color = Color(0.70, 0.82, 0.92)
    fill.light_energy = 0.28
    fill.shadow_enabled = false
    add_child(fill)

func _create_meadow() -> void:
    var meadow := MeshInstance3D.new()
    meadow.name = "Meadow50x50"
    var plane := PlaneMesh.new()
    plane.size = Vector2(52, 52)
    plane.subdivide_width = 48
    plane.subdivide_depth = 48
    var mat := StandardMaterial3D.new()
    mat.albedo_color = Color(0.50, 0.69, 0.43)
    mat.roughness = 0.96
    mat.uv1_scale = Vector3(11, 11, 11)
    if ResourceLoader.exists(MUD_DIFF):
        mat.albedo_texture = load(MUD_DIFF) as Texture2D
    if ResourceLoader.exists(MUD_NORMAL):
        mat.normal_enabled = true
        mat.normal_scale = 0.82
        mat.normal_texture = load(MUD_NORMAL) as Texture2D
    if ResourceLoader.exists(MUD_ROUGH):
        mat.roughness_texture = load(MUD_ROUGH) as Texture2D
        mat.roughness_texture_channel = BaseMaterial3D.TEXTURE_CHANNEL_RED
    plane.material = mat
    meadow.mesh = plane
    meadow.position.y = -0.06
    add_child(meadow)

    # A huge lower field continues beyond the mountain ring so no map edge can be seen.
    var outer := MeshInstance3D.new()
    outer.name = "EndlessField"
    var outer_plane := PlaneMesh.new()
    outer_plane.size = Vector2(260, 260)
    var outer_mat := StandardMaterial3D.new()
    outer_mat.albedo_color = Color(0.24, 0.35, 0.18)
    outer_mat.roughness = 1.0
    outer_plane.material = outer_mat
    outer.mesh = outer_plane
    outer.position.y = -0.16
    add_child(outer)

func _dress_clearing() -> void:
    var ferns: Array[Vector3] = [
        Vector3(-20,0,-18), Vector3(-17,0,-12), Vector3(-21,0,-4), Vector3(-19,0,7),
        Vector3(-16,0,17), Vector3(-8,0,21), Vector3(3,0,22), Vector3(14,0,19),
        Vector3(20,0,12), Vector3(21,0,2), Vector3(19,0,-9), Vector3(14,0,-18),
        Vector3(5,0,-21), Vector3(-7,0,-22), Vector3(-13,0,12), Vector3(12,0,11),
        Vector3(-11,0,-9), Vector3(9,0,-12), Vector3(-4,0,17), Vector3(17,0,5)
    ]
    for i in ferns.size():
        _spawn("fern", _tr(ferns[i], Vector3(0, float((i * 137) % 360), 0), 0.72 + float((i * 17) % 38) / 100.0), "Fern")

    var shrubs: Array[Vector3] = [
        Vector3(-22,0,-14), Vector3(-22,0,14), Vector3(22,0,-15), Vector3(22,0,15),
        Vector3(-15,0,22), Vector3(15,0,22), Vector3(-15,0,-22), Vector3(15,0,-22),
        Vector3(-18,0,1), Vector3(18,0,-1), Vector3(-6,0,23), Vector3(7,0,-23)
    ]
    for i in shrubs.size():
        _spawn("shrub", _tr(shrubs[i], Vector3(0, float((i * 71) % 360), 0), 0.86 + float((i * 19) % 35) / 100.0), "Shrub")

    var rocks: Array[Vector3] = [
        Vector3(-18,0,-17), Vector3(-13,0,18), Vector3(18,0,-18), Vector3(17,0,18),
        Vector3(-23,0,1), Vector3(23,0,4), Vector3(-5,0,-23), Vector3(6,0,23),
        Vector3(-11,0,8), Vector3(12,0,-7), Vector3(4,0,15), Vector3(-3,0,-15)
    ]
    for i in rocks.size():
        _spawn("rocks", _tr(rocks[i], Vector3(0, float((i * 47) % 360), 0), 0.25 + float((i * 13) % 22) / 100.0), "MeadowRock")

    var logs: Array[Transform3D] = [
        _tr(Vector3(-19,0,-8), Vector3(2,18,82), 0.72),
        _tr(Vector3(18,0,10), Vector3(-3,58,86), 0.68),
        _tr(Vector3(-10,0,20), Vector3(3,121,84), 0.62),
        _tr(Vector3(12,0,-20), Vector3(-2,151,83), 0.66)
    ]
    for t in logs:
        _spawn("log", t, "FallenTrunk")

func _create_tree_ring() -> void:
    # Real scanned trunks + real scanned shrubs used as layered 3D canopies.
    for i in 18:
        var angle := TAU * float(i) / 18.0 + 0.09
        var radius := 27.0 + float((i * 7) % 5)
        var root := Node3D.new()
        root.name = "Tree_%02d" % i
        root.position = Vector3(cos(angle) * radius, 0, sin(angle) * radius)
        root.rotation.y = -angle + PI * 0.5
        add_child(root)
        _spawn_into(root, "log", Transform3D(Basis().scaled(Vector3(1.25, 2.5, 1.25)), Vector3(0, 0, 0)), "Trunk")
        _spawn_into(root, "shrub", Transform3D(Basis().scaled(Vector3(3.2, 2.5, 3.2)), Vector3(0, 5.1, 0)), "CanopyA")
        _spawn_into(root, "shrub", Transform3D(Basis().scaled(Vector3(2.5, 2.0, 2.5)), Vector3(1.0, 6.8, -0.4)), "CanopyB")
        _spawn_into(root, "shrub", Transform3D(Basis().scaled(Vector3(2.3, 1.8, 2.3)), Vector3(-1.1, 6.2, 0.7)), "CanopyC")

func _create_mountain_ring() -> void:
    # Two staggered rings of the scanned moss-rock set form actual 3D mountains.
    # Their lower bases overlap the outer field, so there is no visible world edge.
    for i in 30:
        var angle := TAU * float(i) / 30.0
        var radius := 38.0 + float((i * 11) % 5)
        var height_scale := 4.6 + float((i * 17) % 22) / 10.0
        var width_scale := 4.0 + float((i * 13) % 18) / 10.0
        _spawn("rocks", _tr_nonuniform(
            Vector3(cos(angle) * radius, -0.5, sin(angle) * radius),
            Vector3(0, rad_to_deg(-angle), 0),
            Vector3(width_scale, height_scale, width_scale * 1.25)
        ), "MountainNear")

    for i in 22:
        var angle := TAU * float(i) / 22.0 + 0.13
        var radius := 52.0 + float((i * 5) % 7)
        var height_scale := 5.4 + float((i * 19) % 26) / 10.0
        var width_scale := 5.0 + float((i * 7) % 20) / 10.0
        _spawn("rocks", _tr_nonuniform(
            Vector3(cos(angle) * radius, -1.0, sin(angle) * radius),
            Vector3(0, rad_to_deg(-angle), 0),
            Vector3(width_scale, height_scale, width_scale * 1.35)
        ), "MountainFar")

func _tr(position: Vector3, rotation_deg: Vector3, scale_value: float) -> Transform3D:
    var basis := Basis.from_euler(Vector3(deg_to_rad(rotation_deg.x), deg_to_rad(rotation_deg.y), deg_to_rad(rotation_deg.z)))
    basis = basis.scaled(Vector3.ONE * scale_value)
    return Transform3D(basis, position)

func _tr_nonuniform(position: Vector3, rotation_deg: Vector3, scale_value: Vector3) -> Transform3D:
    var basis := Basis.from_euler(Vector3(deg_to_rad(rotation_deg.x), deg_to_rad(rotation_deg.y), deg_to_rad(rotation_deg.z)))
    basis = basis.scaled(scale_value)
    return Transform3D(basis, position)

func _spawn(key: String, transform_value: Transform3D, prefix: String) -> void:
    if not _assets.has(key):
        return
    var packed := _assets[key] as PackedScene
    var instance := packed.instantiate() as Node3D
    if instance == null:
        return
    instance.name = "%s_%d" % [prefix, get_child_count()]
    instance.transform = transform_value
    add_child(instance)

func _spawn_into(parent: Node3D, key: String, transform_value: Transform3D, prefix: String) -> void:
    if not _assets.has(key):
        return
    var packed := _assets[key] as PackedScene
    var instance := packed.instantiate() as Node3D
    if instance == null:
        return
    instance.name = prefix
    instance.transform = transform_value
    parent.add_child(instance)
