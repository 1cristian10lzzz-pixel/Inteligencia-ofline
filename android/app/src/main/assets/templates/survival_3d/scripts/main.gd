extends Node3D

var sun: DirectionalLight3D
var day_clock := 0.0
var info_label: Label

func _ready() -> void:
    _configure_input()
    _create_environment()
    _create_world()
    _create_player()
    _create_hud()

func _process(delta: float) -> void:
    day_clock = fmod(day_clock + delta * 0.035, TAU)
    if is_instance_valid(sun):
        sun.rotation.x = deg_to_rad(-35.0) + sin(day_clock) * 0.55
        sun.light_energy = 0.55 + maxf(0.0, sin(day_clock)) * 0.7
    if is_instance_valid(info_label):
        info_label.text = "GAMEFORGE · SUPERVIVENCIA 3D\nDía %02d · Modo prototipo" % (1 + int(day_clock / TAU * 10.0))

func _configure_input() -> void:
    _add_key_action("move_forward", KEY_W)
    _add_key_action("move_forward", KEY_UP)
    _add_key_action("move_back", KEY_S)
    _add_key_action("move_back", KEY_DOWN)
    _add_key_action("move_left", KEY_A)
    _add_key_action("move_left", KEY_LEFT)
    _add_key_action("move_right", KEY_D)
    _add_key_action("move_right", KEY_RIGHT)
    _add_key_action("jump", KEY_SPACE)

func _add_key_action(action_name: String, key_code: Key) -> void:
    if not InputMap.has_action(action_name):
        InputMap.add_action(action_name)
    var event := InputEventKey.new()
    event.physical_keycode = key_code
    InputMap.action_add_event(action_name, event)

func _create_environment() -> void:
    var world_environment := WorldEnvironment.new()
    var environment := Environment.new()
    environment.background_mode = Environment.BG_SKY
    var sky := Sky.new()
    sky.sky_material = ProceduralSkyMaterial.new()
    environment.sky = sky
    environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
    environment.ambient_light_color = Color(0.55, 0.64, 0.78)
    environment.ambient_light_energy = 0.65
    world_environment.environment = environment
    add_child(world_environment)

    sun = DirectionalLight3D.new()
    sun.rotation_degrees = Vector3(-38, -25, 0)
    sun.light_energy = 1.0
    sun.shadow_enabled = false
    add_child(sun)

func _create_world() -> void:
    var floor_body := StaticBody3D.new()
    floor_body.name = "Ground"
    var floor_mesh := MeshInstance3D.new()
    var floor_shape_mesh := BoxMesh.new()
    floor_shape_mesh.size = Vector3(70, 1, 70)
    floor_mesh.mesh = floor_shape_mesh
    floor_mesh.position.y = -0.5
    var grass := StandardMaterial3D.new()
    grass.albedo_color = Color(0.22, 0.43, 0.25)
    floor_mesh.material_override = grass
    floor_body.add_child(floor_mesh)
    var floor_collision := CollisionShape3D.new()
    var floor_shape := BoxShape3D.new()
    floor_shape.size = Vector3(70, 1, 70)
    floor_collision.shape = floor_shape
    floor_collision.position.y = -0.5
    floor_body.add_child(floor_collision)
    add_child(floor_body)

    _make_tree(Vector3(-5, 0, -5), 1.0)
    _make_tree(Vector3(4, 0, -8), 0.85)
    _make_tree(Vector3(8, 0, 3), 1.15)
    _make_tree(Vector3(-9, 0, 5), 0.9)
    _make_rock(Vector3(2, 0, 5), Vector3(1.8, 1.0, 1.4))
    _make_rock(Vector3(-3, 0, -10), Vector3(2.2, 0.9, 1.5))
    _make_platform(Vector3(0, 0.7, -15), Vector3(5, 1.4, 4), Color(0.45, 0.38, 0.25))

func _make_tree(at: Vector3, scale_factor: float) -> void:
    var tree := StaticBody3D.new()
    tree.position = at
    var trunk := MeshInstance3D.new()
    var trunk_mesh := CylinderMesh.new()
    trunk_mesh.top_radius = 0.22 * scale_factor
    trunk_mesh.bottom_radius = 0.34 * scale_factor
    trunk_mesh.height = 1.8 * scale_factor
    trunk.mesh = trunk_mesh
    trunk.position.y = 0.9 * scale_factor
    var bark := StandardMaterial3D.new()
    bark.albedo_color = Color(0.36, 0.23, 0.13)
    trunk.material_override = bark
    tree.add_child(trunk)

    var leaves := MeshInstance3D.new()
    var leaves_mesh := SphereMesh.new()
    leaves_mesh.radius = 1.15 * scale_factor
    leaves_mesh.height = 2.0 * scale_factor
    leaves.mesh = leaves_mesh
    leaves.position.y = 2.25 * scale_factor
    var foliage := StandardMaterial3D.new()
    foliage.albedo_color = Color(0.12, 0.36, 0.19)
    leaves.material_override = foliage
    tree.add_child(leaves)

    var collision := CollisionShape3D.new()
    var shape := CylinderShape3D.new()
    shape.radius = 0.38 * scale_factor
    shape.height = 2.0 * scale_factor
    collision.shape = shape
    collision.position.y = scale_factor
    tree.add_child(collision)
    add_child(tree)

func _make_rock(at: Vector3, dimensions: Vector3) -> void:
    _make_platform(at + Vector3(0, dimensions.y * 0.5, 0), dimensions, Color(0.34, 0.38, 0.41))

func _make_platform(at: Vector3, dimensions: Vector3, tint: Color) -> void:
    var body := StaticBody3D.new()
    body.position = at
    var mesh_instance := MeshInstance3D.new()
    var mesh := BoxMesh.new()
    mesh.size = dimensions
    mesh_instance.mesh = mesh
    var material := StandardMaterial3D.new()
    material.albedo_color = tint
    mesh_instance.material_override = material
    body.add_child(mesh_instance)
    var collision := CollisionShape3D.new()
    var shape := BoxShape3D.new()
    shape.size = dimensions
    collision.shape = shape
    body.add_child(collision)
    add_child(body)

func _create_player() -> void:
    var player := CharacterBody3D.new()
    player.name = "Player"
    player.position = Vector3(0, 0.15, 2)
    player.set_script(preload("res://scripts/player.gd"))

    var body_mesh := MeshInstance3D.new()
    var capsule_mesh := CapsuleMesh.new()
    capsule_mesh.radius = 0.34
    capsule_mesh.height = 1.65
    body_mesh.mesh = capsule_mesh
    body_mesh.position.y = 0.82
    var player_material := StandardMaterial3D.new()
    player_material.albedo_color = Color(0.25, 0.48, 0.9)
    body_mesh.material_override = player_material
    player.add_child(body_mesh)

    var collision := CollisionShape3D.new()
    var capsule_shape := CapsuleShape3D.new()
    capsule_shape.radius = 0.34
    capsule_shape.height = 1.65
    collision.shape = capsule_shape
    collision.position.y = 0.82
    player.add_child(collision)

    var camera := Camera3D.new()
    camera.position = Vector3(0, 2.1, 5.2)
    camera.rotation_degrees.x = -17
    camera.current = true
    camera.fov = 72
    player.add_child(camera)
    add_child(player)

func _create_hud() -> void:
    var layer := CanvasLayer.new()
    add_child(layer)
    var panel := PanelContainer.new()
    panel.position = Vector2(16, 16)
    panel.custom_minimum_size = Vector2(240, 60)
    layer.add_child(panel)
    info_label = Label.new()
    info_label.text = "GAMEFORGE · SUPERVIVENCIA 3D\nCargando mundo…"
    info_label.add_theme_color_override("font_color", Color(0.92, 0.96, 1.0))
    info_label.add_theme_font_size_override("font_size", 15)
    panel.add_child(info_label)

    var controls := PanelContainer.new()
    controls.set_anchors_preset(Control.PRESET_BOTTOM_LEFT)
    controls.position = Vector2(16, -176)
    controls.custom_minimum_size = Vector2(190, 156)
    layer.add_child(controls)
    var grid := GridContainer.new()
    grid.columns = 3
    grid.add_theme_constant_override("h_separation", 5)
    grid.add_theme_constant_override("v_separation", 5)
    controls.add_child(grid)
    _add_blank(grid)
    _add_touch_button(grid, "▲", "move_forward")
    _add_blank(grid)
    _add_touch_button(grid, "◀", "move_left")
    _add_touch_button(grid, "▼", "move_back")
    _add_touch_button(grid, "▶", "move_right")
    _add_blank(grid)
    _add_touch_button(grid, "SALTO", "jump")
    _add_blank(grid)

func _add_blank(parent: Node) -> void:
    var spacer := Control.new()
    spacer.custom_minimum_size = Vector2(52, 43)
    parent.add_child(spacer)

func _add_touch_button(parent: Node, caption: String, action_name: String) -> void:
    var button := Button.new()
    button.text = caption
    button.custom_minimum_size = Vector2(52, 43)
    button.focus_mode = Control.FOCUS_NONE
    button.button_down.connect(func(): Input.action_press(action_name))
    button.button_up.connect(func(): Input.action_release(action_name))
    parent.add_child(button)
