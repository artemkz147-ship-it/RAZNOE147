extends Node3D

const DinosaurControllerScript = preload("res://scripts/dinosaur_controller.gd")
const QualityManagerScript = preload("res://scripts/quality_manager.gd")

@onready var dinosaur_slot: Node3D = $DinosaurSlot
@onready var environment_slot: Node3D = $EnvironmentSlot
@onready var camera_rig: Node3D = $CameraRig
@onready var ui_layer: CanvasLayer = $UI

var catalog: Array = []
var current_index := 0
var current_data: Dictionary = {}
var dinosaur_controller: DinosaurController
var quality_manager: QualityManager
var current_actor: Node3D
var current_environment: Node3D
var ambience_player: AudioStreamPlayer
var narration_player: AudioStreamPlayer
var roar_player: AudioStreamPlayer3D
var name_label: Label
var latin_label: Label
var era_label: Label
var status_label: Label
var info_panel: PanelContainer
var info_text: RichTextLabel
var quality_button: Button

func _ready() -> void:
    dinosaur_controller = DinosaurControllerScript.new()
    add_child(dinosaur_controller)
    dinosaur_controller.asset_error.connect(_set_status)
    quality_manager = QualityManagerScript.new()
    add_child(quality_manager)
    quality_manager.apply(quality_manager.choose_initial())
    _build_runtime_audio()
    _build_ui()
    _load_catalog()
    if not catalog.is_empty():
        _show_dinosaur(0)

func _load_catalog() -> void:
    var path := "res://data/dinosaurs.json"
    if not FileAccess.file_exists(path):
        _set_status("Каталог динозавров не найден")
        return
    var file := FileAccess.open(path, FileAccess.READ)
    var parsed = JSON.parse_string(file.get_as_text())
    if parsed is Dictionary and parsed.has("dinosaurs"):
        catalog = parsed.dinosaurs
    else:
        _set_status("Ошибка формата каталога")

func _show_dinosaur(index: int) -> void:
    if catalog.is_empty():
        return
    current_index = wrapi(index, 0, catalog.size())
    current_data = catalog[current_index]
    _clear_loaded_scene()
    _update_labels()
    _load_environment(str(current_data.get("environment_path", "")))
    _load_actor(str(current_data.get("model_path", "")))
    _load_audio()

func _load_actor(path: String) -> void:
    if path.is_empty() or not ResourceLoader.exists(path):
        _set_status("Финальный 3D-ассет T. rex ещё не подключён: ожидается %s" % path)
        return
    var packed := load(path) as PackedScene
    if packed == null:
        _set_status("Не удалось импортировать GLB-модель")
        return
    current_actor = packed.instantiate() as Node3D
    dinosaur_slot.add_child(current_actor)
    dinosaur_controller.attach(current_actor, current_data.get("animations", {}))
    _fit_camera_to_actor(current_actor)
    _set_status("Проведи пальцем, чтобы осмотреть динозавра")

func _load_environment(path: String) -> void:
    if path.is_empty() or not ResourceLoader.exists(path):
        _set_status("Среда обитания ожидает финальный GLB-ассет")
        return
    var packed := load(path) as PackedScene
    if packed != null:
        current_environment = packed.instantiate() as Node3D
        environment_slot.add_child(current_environment)

func _fit_camera_to_actor(actor: Node3D) -> void:
    var bounds := _combined_aabb(actor)
    if bounds.size.length() <= 0.01:
        camera_rig.call("reset_view")
        return
    var center := actor.to_global(bounds.get_center())
    var radius: float = maxf(bounds.size.x, maxf(bounds.size.y, bounds.size.z)) * 0.5
    camera_rig.call("focus_target", center, maxf(radius, 1.0))

func _combined_aabb(root: Node3D) -> AABB:
    var has_bounds := false
    var result := AABB()
    var meshes := root.find_children("*", "MeshInstance3D", true, false)
    for node in meshes:
        var mesh_node := node as MeshInstance3D
        if mesh_node.mesh == null:
            continue
        var local_aabb := mesh_node.get_aabb()
        var to_root := root.global_transform.affine_inverse() * mesh_node.global_transform
        var transformed := to_root * local_aabb
        if not has_bounds:
            result = transformed
            has_bounds = true
        else:
            result = result.merge(transformed)
    return result

func _clear_loaded_scene() -> void:
    if is_instance_valid(current_actor): current_actor.queue_free()
    if is_instance_valid(current_environment): current_environment.queue_free()
    current_actor = null
    current_environment = null
    ambience_player.stop()
    narration_player.stop()
    roar_player.stop()

func _load_audio() -> void:
    _assign_stream(ambience_player, str(current_data.get("ambience_path", "")))
    _assign_stream(narration_player, str(current_data.get("narration_path", "")))
    _assign_stream(roar_player, str(current_data.get("roar_path", "")))
    if ambience_player.stream != null: ambience_player.play()

func _assign_stream(player: Node, path: String) -> void:
    if path.is_empty() or not ResourceLoader.exists(path):
        player.set("stream", null)
        return
    player.set("stream", load(path))

func _build_runtime_audio() -> void:
    ambience_player = AudioStreamPlayer.new(); ambience_player.volume_db = -12.0; add_child(ambience_player)
    narration_player = AudioStreamPlayer.new(); narration_player.volume_db = -2.0; add_child(narration_player)
    roar_player = AudioStreamPlayer3D.new(); roar_player.volume_db = 0.0; roar_player.max_distance = 45.0; dinosaur_slot.add_child(roar_player)

func _build_ui() -> void:
    var root := Control.new(); root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT); root.mouse_filter = Control.MOUSE_FILTER_PASS; ui_layer.add_child(root)
    var top := MarginContainer.new(); top.set_anchors_preset(Control.PRESET_TOP_WIDE); top.offset_left = 24; top.offset_top = 18; top.offset_right = -24; top.offset_bottom = 110; root.add_child(top)
    var header := HBoxContainer.new(); header.add_theme_constant_override("separation", 12); top.add_child(header)
    var titles := VBoxContainer.new(); titles.size_flags_horizontal = Control.SIZE_EXPAND_FILL; header.add_child(titles)
    name_label = Label.new(); name_label.add_theme_font_size_override("font_size", 30); titles.add_child(name_label)
    latin_label = Label.new(); latin_label.modulate = Color(1,1,1,0.72); latin_label.add_theme_font_size_override("font_size", 17); titles.add_child(latin_label)
    era_label = Label.new(); era_label.modulate = Color(1,1,1,0.62); titles.add_child(era_label)
    quality_button = Button.new(); quality_button.text = "Качество: %s" % _quality_label(quality_manager.current_preset); quality_button.custom_minimum_size = Vector2(150, 48); quality_button.pressed.connect(_cycle_quality); header.add_child(quality_button)
    var controls := HBoxContainer.new(); controls.set_anchors_preset(Control.PRESET_BOTTOM_WIDE); controls.offset_left = 20; controls.offset_right = -20; controls.offset_top = -78; controls.offset_bottom = -18; controls.alignment = BoxContainer.ALIGNMENT_CENTER; controls.add_theme_constant_override("separation", 10); root.add_child(controls)
    _add_button(controls, "‹", func(): _show_dinosaur(current_index - 1), 54)
    _add_button(controls, "Справка", _toggle_info, 126)
    _add_button(controls, "Рык", _roar, 96)
    _add_button(controls, "Действие", _action, 126)
    _add_button(controls, "Слушать", _narrate, 126)
    _add_button(controls, "›", func(): _show_dinosaur(current_index + 1), 54)
    status_label = Label.new(); status_label.set_anchors_preset(Control.PRESET_BOTTOM_LEFT); status_label.offset_left = 22; status_label.offset_top = -116; status_label.offset_right = 760; status_label.offset_bottom = -84; status_label.modulate = Color(1,1,1,0.72); status_label.add_theme_font_size_override("font_size", 14); root.add_child(status_label)
    info_panel = PanelContainer.new(); info_panel.set_anchors_preset(Control.PRESET_CENTER_RIGHT); info_panel.offset_left = -430; info_panel.offset_top = -250; info_panel.offset_right = -22; info_panel.offset_bottom = 250; info_panel.visible = false; root.add_child(info_panel)
    var margin := MarginContainer.new(); margin.add_theme_constant_override("margin_left", 22); margin.add_theme_constant_override("margin_top", 20); margin.add_theme_constant_override("margin_right", 22); margin.add_theme_constant_override("margin_bottom", 20); info_panel.add_child(margin)
    info_text = RichTextLabel.new(); info_text.bbcode_enabled = true; info_text.scroll_active = true; margin.add_child(info_text)

func _add_button(parent: Container, text: String, callback: Callable, width: float) -> void:
    var button := Button.new(); button.text = text; button.custom_minimum_size = Vector2(width, 54); button.pressed.connect(callback); parent.add_child(button)

func _update_labels() -> void:
    name_label.text = str(current_data.get("name_ru", "")); latin_label.text = str(current_data.get("scientific_name", "")); era_label.text = "%s · %s" % [current_data.get("period_ru", ""), current_data.get("region_ru", "")]; info_text.text = _build_info_bbcode()

func _build_info_bbcode() -> String:
    var evidence: Array = current_data.get("evidence_notes", []); var evidence_text := ""
    for note in evidence: evidence_text += "\n• %s" % str(note)
    return "[font_size=26][b]%s[/b][/font_size]\n[i]%s[/i]\n\n[b]Период:[/b] %s\n[b]Регион:[/b] %s\n[b]Питание:[/b] %s\n[b]Длина:[/b] ~%s м\n[b]Масса:[/b] ~%s кг\n\n%s\n\n[b]Что известно наверняка / где реконструкция[/b]%s" % [current_data.get("name_ru", ""), current_data.get("scientific_name", ""), current_data.get("period_ru", ""), current_data.get("region_ru", ""), current_data.get("diet_ru", ""), current_data.get("length_m", "—"), current_data.get("mass_kg", "—"), current_data.get("description_ru", ""), evidence_text]

func _toggle_info() -> void: info_panel.visible = not info_panel.visible
func _roar() -> void:
    dinosaur_controller.play_action("roar")
    if roar_player.stream != null: roar_player.play()
    else: _set_status("Финальная реконструкция рыка ещё не подключена")
func _action() -> void:
    var actions: Array = current_data.get("interactive_actions", ["look", "walk", "threat"])
    if actions.is_empty(): return
    dinosaur_controller.play_action(str(actions.pick_random()))
func _narrate() -> void:
    if narration_player.stream != null:
        if narration_player.playing: narration_player.stop()
        else: narration_player.play()
        return
    if DisplayServer.has_feature(DisplayServer.FEATURE_TEXT_TO_SPEECH):
        var voices := DisplayServer.tts_get_voices_for_language("ru")
        if not voices.is_empty():
            DisplayServer.tts_stop(); DisplayServer.tts_speak(str(current_data.get("narration_text_ru", "")), voices[0], 70, 1.0, 1.0); return
    _set_status("Озвучка ещё не подключена")
func _cycle_quality() -> void:
    var order := ["performance", "high", "cinema"]; var pos := order.find(quality_manager.current_preset); var next_name: String = order[(pos + 1) % order.size()]; quality_manager.apply(next_name); quality_button.text = "Качество: %s" % _quality_label(next_name)
func _quality_label(name: String) -> String:
    match name:
        "performance": return "Производительность"
        "cinema": return "Кино"
        _: return "Высокое"
func _set_status(message: String) -> void:
    if status_label != null: status_label.text = message
