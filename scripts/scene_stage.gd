extends Node3D

# Procedural Hell Creek-inspired floodplain stage.
# It deliberately avoids modern palms and generic tropical-jungle dressing.
# The scene uses low-cost procedural materials and MultiMesh vegetation so the
# Android build remains scalable while still reading as a living habitat.

const TREE_COUNT := 58
const CONIFER_COUNT := 22
const FERN_COUNT := 240
const REED_COUNT := 150
const DEADWOOD_COUNT := 18

var _rng := RandomNumberGenerator.new()

func _ready() -> void:
    _rng.seed = 14766026
    _create_environment()
    _create_sun()
    _create_ground()
    _create_river()
    _create_bank()
    _create_floodplain_trees()
    _create_conifers()
    _create_ferns()
    _create_reeds()
    _create_deadwood()

func _create_environment() -> void:
    var world := WorldEnvironment.new()
    world.name = "WorldEnvironment"

    var environment := Environment.new()
    environment.background_mode = Environment.BG_SKY
    environment.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
    environment.reflected_light_source = Environment.REFLECTION_SOURCE_SKY
    environment.ambient_light_energy = 0.72
    environment.tonemap_mode = Environment.TONE_MAPPER_ACES
    environment.tonemap_exposure = 1.05

    environment.fog_enabled = true
    environment.fog_light_color = Color(0.46, 0.53, 0.47)
    environment.fog_light_energy = 0.75
    environment.fog_density = 0.008
    environment.fog_height = 1.2
    environment.fog_height_density = 0.18
    environment.fog_aerial_perspective = 0.42
    environment.fog_sky_affect = 0.48

    var sky := Sky.new()
    sky.radiance_size = Sky.RADIANCE_SIZE_128
    var sky_material := ProceduralSkyMaterial.new()
    sky_material.sky_top_color = Color(0.09, 0.18, 0.24)
    sky_material.sky_horizon_color = Color(0.63, 0.69, 0.62)
    sky_material.ground_bottom_color = Color(0.035, 0.041, 0.031)
    sky_material.ground_horizon_color = Color(0.20, 0.25, 0.19)
    sky_material.sun_curve = 0.075
    sky_material.sun_angle_max = 18.0
    sky.sky_material = sky_material
    environment.sky = sky

    world.environment = environment
    add_child(world)

func _create_sun() -> void:
    var sun := DirectionalLight3D.new()
    sun.name = "LateAfternoonSun"
    sun.rotation_degrees = Vector3(-38.0, -31.0, -2.0)
    sun.light_color = Color(1.0, 0.91, 0.77)
    sun.light_energy = 2.15
    sun.shadow_enabled = true
    sun.directional_shadow_max_distance = 82.0
    sun.directional_shadow_split_1 = 0.12
    sun.directional_shadow_split_2 = 0.34
    sun.directional_shadow_split_3 = 0.66
    add_child(sun)

func _create_ground() -> void:
    var ground := MeshInstance3D.new()
    ground.name = "FloodplainGround"
    var plane := PlaneMesh.new()
    plane.size = Vector2(110.0, 110.0)
    plane.subdivide_width = 32
    plane.subdivide_depth = 32
    plane.material = _ground_material()
    ground.mesh = plane
    ground.position = Vector3(0.0, -0.08, 0.0)
    add_child(ground)

func _ground_material() -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;

float hash21(vec2 p){
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float noise2(vec2 p){
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f*f*(3.0-2.0*f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0,0.0));
    float c = hash21(i + vec2(0.0,1.0));
    float d = hash21(i + vec2(1.0,1.0));
    return mix(mix(a,b,f.x), mix(c,d,f.x), f.y);
}

float fbm(vec2 p){
    float v = 0.0;
    float a = 0.5;
    for(int i=0;i<5;i++){
        v += noise2(p) * a;
        p = p * 2.03 + vec2(17.1, 9.2);
        a *= 0.5;
    }
    return v;
}

void fragment(){
    vec2 p = UV * 18.0;
    float n = fbm(p);
    float fine = noise2(p * 7.0);
    float wet = smoothstep(0.57, 0.82, n);
    vec3 soil = vec3(0.082,0.070,0.045);
    vec3 loam = vec3(0.135,0.120,0.074);
    vec3 moss = vec3(0.075,0.110,0.055);
    vec3 c = mix(soil, loam, n);
    c = mix(c, moss, smoothstep(0.50,0.76,fine) * (1.0-wet));
    ALBEDO = c;
    ROUGHNESS = mix(0.94, 0.46, wet);
    SPECULAR = mix(0.18, 0.42, wet);
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    return material

func _create_river() -> void:
    var river := MeshInstance3D.new()
    river.name = "MeanderingRiver"
    var plane := PlaneMesh.new()
    plane.size = Vector2(27.0, 96.0)
    plane.subdivide_width = 24
    plane.subdivide_depth = 48
    plane.material = _water_material()
    river.mesh = plane
    river.position = Vector3(-25.0, -0.14, -3.0)
    river.rotation.y = deg_to_rad(-7.0)
    add_child(river)

func _water_material() -> ShaderMaterial:
    var shader := Shader.new()
    shader.code = """
shader_type spatial;
render_mode blend_mix, depth_prepass_alpha, diffuse_burley, specular_schlick_ggx;

uniform vec3 shallow_color : source_color = vec3(0.075,0.155,0.145);
uniform vec3 deep_color : source_color = vec3(0.020,0.070,0.075);

float wave(vec2 p, float t){
    return sin(p.x*1.1+t*0.9)*0.42 + sin(p.y*1.7-t*0.65)*0.31 + sin((p.x+p.y)*2.5+t*0.42)*0.16;
}

void vertex(){
    float w = wave(VERTEX.xz, TIME);
    VERTEX.y += w * 0.035;
}

void fragment(){
    vec2 p = UV * 24.0;
    float w1 = wave(p*1.4, TIME);
    float w2 = wave(p*1.4+vec2(0.03,0.0), TIME);
    float w3 = wave(p*1.4+vec2(0.0,0.03), TIME);
    vec3 n = normalize(vec3(w1-w2, 0.12, w1-w3));
    NORMAL = mix(NORMAL, n, 0.36);
    float depth_hint = smoothstep(0.0, 1.0, UV.x);
    ALBEDO = mix(deep_color, shallow_color, depth_hint);
    ROUGHNESS = 0.12;
    METALLIC = 0.0;
    SPECULAR = 0.88;
    ALPHA = 0.86;
}
"""
    var material := ShaderMaterial.new()
    material.shader = shader
    return material

func _create_bank() -> void:
    var bank_mat := StandardMaterial3D.new()
    bank_mat.albedo_color = Color(0.11, 0.095, 0.055)
    bank_mat.roughness = 0.97
    for i in range(28):
        var clump := MeshInstance3D.new()
        var mesh := SphereMesh.new()
        mesh.radius = _rng.randf_range(1.8, 4.2)
        mesh.height = mesh.radius * 2.0
        mesh.radial_segments = 12
        mesh.rings = 5
        mesh.material = bank_mat
        clump.mesh = mesh
        var z := _rng.randf_range(-45.0, 44.0)
        var side: float = -1.0 if i % 2 == 0 else 1.0
        clump.position = Vector3(-25.0 + side * _rng.randf_range(10.5, 14.5), -0.20, z)
        clump.scale.y = _rng.randf_range(0.16, 0.28)
        add_child(clump)

func _create_floodplain_trees() -> void:
    var trunk_mesh := CylinderMesh.new()
    trunk_mesh.top_radius = 0.22
    trunk_mesh.bottom_radius = 0.48
    trunk_mesh.height = 5.4
    trunk_mesh.radial_segments = 7
    var bark := StandardMaterial3D.new()
    bark.albedo_color = Color(0.18, 0.135, 0.085)
    bark.roughness = 0.96
    trunk_mesh.material = bark

    var canopy_mesh := SphereMesh.new()
    canopy_mesh.radius = 2.35
    canopy_mesh.height = 4.7
    canopy_mesh.radial_segments = 8
    canopy_mesh.rings = 5
    var leaves := StandardMaterial3D.new()
    leaves.albedo_color = Color(0.105, 0.205, 0.090)
    leaves.roughness = 0.93
    canopy_mesh.material = leaves

    var trunks := _make_multimesh("AngiospermTrunks", trunk_mesh, TREE_COUNT)
    var canopies := _make_multimesh("AngiospermCanopies", canopy_mesh, TREE_COUNT)
    for i in range(TREE_COUNT):
        var p := _random_habitat_point(14.0, 52.0)
        var s := _rng.randf_range(0.72, 1.45)
        var yaw := _rng.randf_range(-PI, PI)
        var trunk_t := Transform3D(Basis(Vector3.UP, yaw).scaled(Vector3(s, s, s)), p + Vector3(0.0, 2.55*s, 0.0))
        trunks.multimesh.set_instance_transform(i, trunk_t)
        var canopy_t := Transform3D(Basis(Vector3.UP, yaw).scaled(Vector3(s*_rng.randf_range(0.85,1.18), s, s*_rng.randf_range(0.85,1.18))), p + Vector3(0.0, 5.3*s, 0.0))
        canopies.multimesh.set_instance_transform(i, canopy_t)

func _create_conifers() -> void:
    var trunk_mesh := CylinderMesh.new()
    trunk_mesh.top_radius = 0.12
    trunk_mesh.bottom_radius = 0.32
    trunk_mesh.height = 7.0
    trunk_mesh.radial_segments = 7
    var trunk_mat := StandardMaterial3D.new()
    trunk_mat.albedo_color = Color(0.135, 0.105, 0.070)
    trunk_mat.roughness = 0.96
    trunk_mesh.material = trunk_mat

    var crown_mesh := CylinderMesh.new()
    crown_mesh.top_radius = 0.08
    crown_mesh.bottom_radius = 2.1
    crown_mesh.height = 5.5
    crown_mesh.radial_segments = 9
    var needle_mat := StandardMaterial3D.new()
    needle_mat.albedo_color = Color(0.055, 0.145, 0.075)
    needle_mat.roughness = 0.95
    crown_mesh.material = needle_mat

    var trunks := _make_multimesh("ConiferTrunks", trunk_mesh, CONIFER_COUNT)
    var crowns := _make_multimesh("ConiferCrowns", crown_mesh, CONIFER_COUNT)
    for i in range(CONIFER_COUNT):
        var p := _random_habitat_point(20.0, 54.0)
        var s := _rng.randf_range(0.8, 1.6)
        var yaw := _rng.randf_range(-PI, PI)
        trunks.multimesh.set_instance_transform(i, Transform3D(Basis(Vector3.UP, yaw).scaled(Vector3(s,s,s)), p + Vector3(0,3.4*s,0)))
        crowns.multimesh.set_instance_transform(i, Transform3D(Basis(Vector3.UP, yaw).scaled(Vector3(s,s,s)), p + Vector3(0,6.4*s,0)))

func _create_ferns() -> void:
    var leaf := QuadMesh.new()
    leaf.size = Vector2(0.85, 1.8)
    leaf.orientation = PlaneMesh.FACE_Z
    var fern_mat := StandardMaterial3D.new()
    fern_mat.albedo_color = Color(0.085, 0.24, 0.075)
    fern_mat.roughness = 0.90
    fern_mat.cull_mode = BaseMaterial3D.CULL_DISABLED
    leaf.material = fern_mat
    var ferns := _make_multimesh("FernUnderstory", leaf, FERN_COUNT)
    for i in range(FERN_COUNT):
        var p := _random_habitat_point(5.5, 49.0)
        var s := _rng.randf_range(0.45, 1.2)
        var yaw := _rng.randf_range(-PI, PI)
        var pitch := deg_to_rad(_rng.randf_range(-12.0, 12.0))
        var basis := Basis(Vector3.UP, yaw) * Basis(Vector3.RIGHT, pitch)
        basis = basis.scaled(Vector3(s, s, s))
        ferns.multimesh.set_instance_transform(i, Transform3D(basis, p + Vector3(0.0, 0.68*s, 0.0)))

func _create_reeds() -> void:
    var reed_mesh := CylinderMesh.new()
    reed_mesh.top_radius = 0.018
    reed_mesh.bottom_radius = 0.026
    reed_mesh.height = 1.35
    reed_mesh.radial_segments = 5
    var reed_mat := StandardMaterial3D.new()
    reed_mat.albedo_color = Color(0.23, 0.31, 0.10)
    reed_mat.roughness = 0.9
    reed_mesh.material = reed_mat
    var reeds := _make_multimesh("RiverbankReeds", reed_mesh, REED_COUNT)
    for i in range(REED_COUNT):
        var z := _rng.randf_range(-46.0, 46.0)
        var side: float = -1.0 if _rng.randf() < 0.5 else 1.0
        var x := -25.0 + side * _rng.randf_range(12.4, 14.8)
        var s := _rng.randf_range(0.65, 1.45)
        reeds.multimesh.set_instance_transform(i, Transform3D(Basis().scaled(Vector3(s,s,s)), Vector3(x, 0.58*s, z)))

func _create_deadwood() -> void:
    var log_mesh := CylinderMesh.new()
    log_mesh.top_radius = 0.17
    log_mesh.bottom_radius = 0.24
    log_mesh.height = 3.8
    log_mesh.radial_segments = 7
    var mat := StandardMaterial3D.new()
    mat.albedo_color = Color(0.11, 0.082, 0.050)
    mat.roughness = 0.98
    log_mesh.material = mat
    var logs := _make_multimesh("Deadwood", log_mesh, DEADWOOD_COUNT)
    for i in range(DEADWOOD_COUNT):
        var p := _random_habitat_point(8.0, 42.0)
        var yaw := _rng.randf_range(-PI, PI)
        var basis := Basis(Vector3.UP, yaw) * Basis(Vector3.FORWARD, deg_to_rad(88.0))
        var s := _rng.randf_range(0.7, 1.3)
        basis = basis.scaled(Vector3(s,s,s))
        logs.multimesh.set_instance_transform(i, Transform3D(basis, p + Vector3(0.0, 0.22*s, 0.0)))

func _make_multimesh(name: String, mesh: Mesh, count: int) -> MultiMeshInstance3D:
    var instance := MultiMeshInstance3D.new()
    instance.name = name
    var mm := MultiMesh.new()
    mm.transform_format = MultiMesh.TRANSFORM_3D
    mm.instance_count = count
    mm.visible_instance_count = count
    mm.mesh = mesh
    instance.multimesh = mm
    instance.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_ON
    add_child(instance)
    return instance

func _random_habitat_point(min_radius: float, max_radius: float) -> Vector3:
    for _attempt in range(20):
        var angle: float = _rng.randf_range(-PI, PI)
        var radius: float = sqrt(_rng.randf_range(min_radius*min_radius, max_radius*max_radius))
        var p := Vector3(cos(angle)*radius, 0.0, sin(angle)*radius)
        # Keep the river corridor open and preserve a presentation clearing near the animal.
        var river_distance: float = absf(p.x + 25.0)
        if river_distance > 15.0 and (absf(p.x) > 5.0 or absf(p.z) > 7.5):
            return p
    return Vector3(max_radius, 0.0, 0.0)
