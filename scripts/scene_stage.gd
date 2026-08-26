extends Node3D

# Curated asset-based Hell Creek scene: scanned Poly Haven assets, no generated vegetation primitives.
const FERN_PATH := "res://assets/environments/hell_creek/models/fern_02.glb"
const LOG_PATH := "res://assets/environments/hell_creek/models/dead_tree_trunk.glb"
const STUMP_PATH := "res://assets/environments/hell_creek/models/tree_stump_01.glb"
const ROCKS_PATH := "res://assets/environments/hell_creek/models/rock_moss_set_01.glb"
const SHRUB_PATH := "res://assets/environments/hell_creek/models/shrub_03.glb"
const HDRI_PATH := "res://assets/environments/hell_creek/xanderklinge_2k.hdr"
const MUD_DIFF := "res://assets/environments/hell_creek/textures/mud_forest_diff_1k.jpg"
const MUD_NORMAL := "res://assets/environments/hell_creek/textures/mud_forest_nor_gl_1k.jpg"
const MUD_ROUGH := "res://assets/environments/hell_creek/textures/mud_forest_rough_1k.jpg"

var _assets: Dictionary = {}

func _ready() -> void:
    _load_assets()
    _create_environment()
    _create_lighting()
    _create_ground()
    _create_water()
    _dress_deadwood()
    _dress_rocks()
    _dress_shrubs()
    _dress_ferns()

func _load_assets() -> void:
    for entry in [["fern",FERN_PATH],["log",LOG_PATH],["stump",STUMP_PATH],["rocks",ROCKS_PATH],["shrub",SHRUB_PATH]]:
        var key: String = str(entry[0])
        var path: String = str(entry[1])
        if ResourceLoader.exists(path):
            var packed: PackedScene = load(path) as PackedScene
            if packed != null:
                _assets[key] = packed

func _create_environment() -> void:
    var world := WorldEnvironment.new()
    world.name = "WorldEnvironment"
    var env := Environment.new()
    env.background_mode = Environment.BG_SKY
    env.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
    env.reflected_light_source = Environment.REFLECTION_SOURCE_SKY
    env.ambient_light_energy = 0.82
    env.tonemap_mode = Environment.TONE_MAPPER_ACES
    env.tonemap_exposure = 1.0
    env.fog_enabled = true
    env.fog_light_color = Color(0.49,0.55,0.50)
    env.fog_light_energy = 0.72
    env.fog_density = 0.0065
    env.fog_height = 0.5
    env.fog_height_density = 0.12
    env.fog_aerial_perspective = 0.38
    env.fog_sky_affect = 0.36
    var sky := Sky.new()
    sky.radiance_size = Sky.RADIANCE_SIZE_256
    if ResourceLoader.exists(HDRI_PATH):
        var hdri: Texture2D = load(HDRI_PATH) as Texture2D
        if hdri != null:
            var panorama := PanoramaSkyMaterial.new()
            panorama.panorama = hdri
            panorama.energy_multiplier = 0.68
            sky.sky_material = panorama
    if sky.sky_material == null:
        var fallback := ProceduralSkyMaterial.new()
        fallback.sky_top_color = Color(0.16,0.23,0.27)
        fallback.sky_horizon_color = Color(0.55,0.61,0.54)
        fallback.ground_bottom_color = Color(0.04,0.045,0.035)
        fallback.ground_horizon_color = Color(0.14,0.17,0.13)
        sky.sky_material = fallback
    env.sky = sky
    world.environment = env
    add_child(world)

func _create_lighting() -> void:
    var sun := DirectionalLight3D.new()
    sun.name = "Sun"
    sun.rotation_degrees = Vector3(-43,-28,-3)
    sun.light_color = Color(1.0,0.91,0.77)
    sun.light_energy = 2.0
    sun.shadow_enabled = true
    sun.directional_shadow_max_distance = 95.0
    add_child(sun)
    var fill := DirectionalLight3D.new()
    fill.name = "SkyFill"
    fill.rotation_degrees = Vector3(-25,145,0)
    fill.light_color = Color(0.70,0.79,0.84)
    fill.light_energy = 0.23
    fill.shadow_enabled = false
    add_child(fill)

func _create_ground() -> void:
    var ground := MeshInstance3D.new()
    ground.name = "ScannedMudGround"
    var plane := PlaneMesh.new()
    plane.size = Vector2(105,105)
    var mat := StandardMaterial3D.new()
    mat.roughness = 0.88
    mat.uv1_scale = Vector3(14,14,14)
    if ResourceLoader.exists(MUD_DIFF):
        mat.albedo_texture = load(MUD_DIFF) as Texture2D
    if ResourceLoader.exists(MUD_NORMAL):
        mat.normal_enabled = true
        mat.normal_scale = 1.25
        mat.normal_texture = load(MUD_NORMAL) as Texture2D
    if ResourceLoader.exists(MUD_ROUGH):
        mat.roughness_texture = load(MUD_ROUGH) as Texture2D
        mat.roughness_texture_channel = BaseMaterial3D.TEXTURE_CHANNEL_RED
    plane.material = mat
    ground.mesh = plane
    ground.position.y = -0.08
    add_child(ground)

func _create_water() -> void:
    var river := MeshInstance3D.new()
    river.name = "River"
    var plane := PlaneMesh.new()
    plane.size = Vector2(22,112)
    plane.subdivide_width = 20
    plane.subdivide_depth = 50
    river.mesh = plane
    river.position = Vector3(-30,-0.12,-4)
    river.rotation.y = deg_to_rad(-8)
    var shader := Shader.new()
    shader.code = "shader_type spatial; render_mode blend_mix,depth_prepass_alpha,diffuse_burley,specular_schlick_ggx; uniform vec3 deep_color:source_color=vec3(0.025,0.065,0.060); uniform vec3 shallow_color:source_color=vec3(0.080,0.145,0.120); void vertex(){float a=sin(VERTEX.z*0.42+TIME*0.72);float b=sin(VERTEX.x*0.73-TIME*0.49);VERTEX.y+=(a+b)*0.018;} void fragment(){float r1=sin(UV.y*71.0+TIME*0.55)*0.5+0.5;ALBEDO=mix(deep_color,shallow_color,0.32+0.22*r1);ROUGHNESS=0.20;SPECULAR=0.78;ALPHA=0.93;}"
    var water_mat := ShaderMaterial.new()
    water_mat.shader = shader
    river.material_override = water_mat
    add_child(river)

func _dress_deadwood() -> void:
    var logs: Array[Transform3D] = [_tr(Vector3(-10.5,0,-7),Vector3(0,22,78),1.15),_tr(Vector3(12,0,-10.5),Vector3(4,-33,85),0.86),_tr(Vector3(18,0,10),Vector3(-2,51,88),0.72),_tr(Vector3(-15,0,16),Vector3(3,114,86),0.93),_tr(Vector3(28,0,-4),Vector3(1,76,84),0.78),_tr(Vector3(-6,0,28),Vector3(-3,146,84),0.82)]
    for t in logs: _spawn("log",t,"DeadTreeTrunk")
    var stumps: Array[Transform3D] = [_tr(Vector3(8,0,8),Vector3(0,18,0),0.82),_tr(Vector3(-18,0,-14),Vector3(0,103,0),0.95),_tr(Vector3(24,0,19),Vector3(0,61,0),0.74),_tr(Vector3(-8,0,-24),Vector3(0,151,0),0.72)]
    for t in stumps: _spawn("stump",t,"TreeStump")

func _dress_rocks() -> void:
    var points: Array[Vector3] = [Vector3(-4,0,-8),Vector3(15,0,-4),Vector3(-14,0,5),Vector3(26,0,12),Vector3(-22,0,18),Vector3(4,0,25),Vector3(32,0,-18),Vector3(-7,0,-32)]
    var i := 0
    for p in points:
        _spawn("rocks",_tr(p,Vector3(0,float((i*47)%360),0),0.30+float((i*13)%18)/100.0),"MossRockSet")
        i += 1

func _dress_shrubs() -> void:
    var points: Array[Vector3] = [Vector3(-12,0,-2),Vector3(-18,0,-7),Vector3(14,0,-15),Vector3(20,0,-8),Vector3(23,0,5),Vector3(15,0,17),Vector3(-8,0,17),Vector3(-19,0,12),Vector3(31,0,23),Vector3(-29,0,25),Vector3(35,0,-25),Vector3(-32,0,-21)]
    var i := 0
    for p in points:
        _spawn("shrub",_tr(p,Vector3(0,float((i*71)%360),0),0.78+float((i*17)%43)/100.0),"Shrub")
        i += 1

func _dress_ferns() -> void:
    var points: Array[Vector3] = [Vector3(-7,0,-4),Vector3(-10,0,-9),Vector3(-13,0,-12),Vector3(-15,0,-2),Vector3(-18,0,4),Vector3(-12,0,9),Vector3(-7,0,12),Vector3(-3,0,15),Vector3(5,0,14),Vector3(10,0,11),Vector3(13,0,6),Vector3(15,0,1),Vector3(17,0,-5),Vector3(20,0,-11),Vector3(10,0,-14),Vector3(5,0,-11),Vector3(2,0,-7),Vector3(-2,0,-13),Vector3(-20,0,-10),Vector3(-23,0,-3),Vector3(-21,0,7),Vector3(-18,0,14),Vector3(-12,0,20),Vector3(-4,0,22),Vector3(7,0,21),Vector3(14,0,20),Vector3(20,0,15),Vector3(24,0,9),Vector3(25,0,0),Vector3(27,0,-8),Vector3(28,0,-17),Vector3(19,0,-20),Vector3(11,0,-23),Vector3(2,0,-25),Vector3(-8,0,-24),Vector3(-16,0,-22),Vector3(-24,0,-18),Vector3(32,0,18),Vector3(35,0,8),Vector3(35,0,-4),Vector3(34,0,-14),Vector3(-31,0,17),Vector3(-34,0,8),Vector3(-35,0,-4),Vector3(-32,0,-15),Vector3(-26,0,27),Vector3(-15,0,29),Vector3(-3,0,31),Vector3(11,0,30),Vector3(24,0,27),Vector3(38,0,25),Vector3(39,0,13),Vector3(40,0,0),Vector3(39,0,-13),Vector3(36,0,-26),Vector3(-38,0,25),Vector3(-40,0,13),Vector3(-41,0,0),Vector3(-39,0,-14),Vector3(-36,0,-26)]
    var i := 0
    for p in points:
        _spawn("fern",_tr(p,Vector3(0,float((i*137)%360),0),0.58+float((i*31)%53)/100.0),"Fern")
        i += 1

func _tr(position: Vector3, rotation_deg: Vector3, scale_value: float) -> Transform3D:
    var basis := Basis.from_euler(Vector3(deg_to_rad(rotation_deg.x),deg_to_rad(rotation_deg.y),deg_to_rad(rotation_deg.z)))
    basis = basis.scaled(Vector3.ONE*scale_value)
    return Transform3D(basis,position)

func _spawn(key: String, transform_value: Transform3D, prefix: String) -> void:
    if not _assets.has(key): return
    var packed: PackedScene = _assets[key] as PackedScene
    var instance: Node3D = packed.instantiate() as Node3D
    if instance == null: return
    instance.name = "%s_%d" % [prefix,get_child_count()]
    instance.transform = transform_value
    add_child(instance)
