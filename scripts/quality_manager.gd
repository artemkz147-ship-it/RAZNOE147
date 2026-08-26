class_name QualityManager
extends Node

const PRESETS := {
    "performance": {"scale": 0.72, "shadow": 1024, "msaa": 0},
    "high": {"scale": 0.88, "shadow": 2048, "msaa": 2},
    "cinema": {"scale": 1.0, "shadow": 4096, "msaa": 2}
}

var current_preset := "high"

func apply(preset_name: String) -> void:
    if not PRESETS.has(preset_name):
        preset_name = "high"
    current_preset = preset_name
    var cfg: Dictionary = PRESETS[preset_name]
    var viewport := get_viewport()
    viewport.scaling_3d_scale = float(cfg.scale)
    viewport.positional_shadow_atlas_size = int(cfg.shadow)
    viewport.msaa_3d = int(cfg.msaa)

func choose_initial() -> String:
    if OS.has_feature("mobile"):
        return "high"
    return "cinema"
