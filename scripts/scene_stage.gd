extends Node3D

# Neutral high-quality fallback stage. It is intentionally not labelled as a
# Hell Creek reconstruction; a dedicated habitat GLB can replace/augment it.

func _ready() -> void:
    _create_environment()
    _create_sun()
    _create_ground()

func _create_environment() -> void:
    var world := WorldEnvironment.new()
    world.name = "WorldEnvironment"

    var environment := Environment.new()
    environment.background_mode = Environment.BG_SKY
    environment.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
    environment.reflected_light_source = Environment.REFLECTION_SOURCE_SKY
    environment.ambient_light_energy = 0.85
    environment.tonemap_mode = Environment.TONE_MAPPER_ACES

    var sky := Sky.new()
    sky.radiance_size = Sky.RADIANCE_SIZE_128

    var sky_material := ProceduralSkyMaterial.new()
    sky_material.sky_top_color = Color(0.12, 0.22, 0.30)
    sky_material.sky_horizon_color = Color(0.70, 0.72, 0.65)
    sky_material.ground_bottom_color = Color(0.055, 0.052, 0.045)
    sky_material.ground_horizon_color = Color(0.26, 0.27, 0.22)
    sky_material.sun_angle_max = 16.0
    sky_material.sun_curve = 0.08
    sky.sky_material = sky_material
    environment.sky = sky

    world.environment = environment
    add_child(world)

func _create_sun() -> void:
    var sun := DirectionalLight3D.new()
    sun.name = "Sun"
    sun.rotation_degrees = Vector3(-42.0, -32.0, 0.0)
    sun.light_color = Color(1.0, 0.93, 0.82)
    sun.light_energy = 2.0
    sun.shadow_enabled = true
    sun.directional_shadow_max_distance = 90.0
    add_child(sun)

func _create_ground() -> void:
    var ground := MeshInstance3D.new()
    ground.name = "FallbackGround"

    var plane := PlaneMesh.new()
    plane.size = Vector2(90.0, 90.0)
    plane.subdivide_width = 1
    plane.subdivide_depth = 1

    var material := StandardMaterial3D.new()
    material.albedo_color = Color(0.095, 0.105, 0.078)
    material.roughness = 0.92
    material.metallic = 0.0
    plane.material = material

    ground.mesh = plane
    ground.position.y = -0.015
    add_child(ground)
