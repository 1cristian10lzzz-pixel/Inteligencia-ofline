extends Node2D

func _ready() -> void:
    _configure_input()
    _create_world()
    _create_player()
    _create_hud()

func _configure_input() -> void:
    _add_key_action("move_left", KEY_A)
    _add_key_action("move_left", KEY_LEFT)
    _add_key_action("move_right", KEY_D)
    _add_key_action("move_right", KEY_RIGHT)
    _add_key_action("jump", KEY_SPACE)
    _add_key_action("jump", KEY_W)

func _add_key_action(action_name: String, key_code: Key) -> void:
    if not InputMap.has_action(action_name):
        InputMap.add_action(action_name)
    var event := InputEventKey.new()
    event.physical_keycode = key_code
    InputMap.action_add_event(action_name, event)

func _create_world() -> void:
    var background := ColorRect.new()
    background.color = Color(0.15, 0.23, 0.37)
    background.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    add_child(background)
    move_child(background, 0)

    _add_platform(Vector2(640, 650), Vector2(1280, 100), Color(0.25, 0.48, 0.35))
    _add_platform(Vector2(360, 500), Vector2(230, 28), Color(0.55, 0.42, 0.28))
    _add_platform(Vector2(760, 410), Vector2(220, 28), Color(0.55, 0.42, 0.28))
    _add_platform(Vector2(1080, 315), Vector2(180, 28), Color(0.55, 0.42, 0.28))

func _add_platform(at: Vector2, dimensions: Vector2, tint: Color) -> void:
    var body := StaticBody2D.new()
    body.position = at
    var visual := ColorRect.new()
    visual.color = tint
    visual.size = dimensions
    visual.position = -dimensions * 0.5
    body.add_child(visual)
    var collision := CollisionShape2D.new()
    var shape := RectangleShape2D.new()
    shape.size = dimensions
    collision.shape = shape
    body.add_child(collision)
    add_child(body)

func _create_player() -> void:
    var player := CharacterBody2D.new()
    player.name = "Player"
    player.position = Vector2(160, 560)
    player.set_script(preload("res://scripts/player.gd"))

    var visual := Polygon2D.new()
    visual.polygon = PackedVector2Array([Vector2(-18, -34), Vector2(18, -34), Vector2(18, 30), Vector2(-18, 30)])
    visual.color = Color(0.35, 0.65, 1.0)
    player.add_child(visual)

    var collision := CollisionShape2D.new()
    var shape := RectangleShape2D.new()
    shape.size = Vector2(34, 62)
    collision.shape = shape
    player.add_child(collision)

    var camera := Camera2D.new()
    camera.position_smoothing_enabled = true
    camera.position_smoothing_speed = 5.0
    player.add_child(camera)
    add_child(player)

func _create_hud() -> void:
    var layer := CanvasLayer.new()
    add_child(layer)
    var hint := Label.new()
    hint.text = "GAMEFORGE · PLATAFORMAS 2D"
    hint.position = Vector2(20, 18)
    hint.add_theme_font_size_override("font_size", 22)
    hint.add_theme_color_override("font_color", Color.WHITE)
    layer.add_child(hint)

    var controls := PanelContainer.new()
    controls.set_anchors_preset(Control.PRESET_BOTTOM_LEFT)
    controls.position = Vector2(16, -94)
    controls.custom_minimum_size = Vector2(300, 76)
    layer.add_child(controls)
    var row := HBoxContainer.new()
    row.add_theme_constant_override("separation", 8)
    controls.add_child(row)
    _add_touch_button(row, "◀", "move_left")
    _add_touch_button(row, "▶", "move_right")
    _add_touch_button(row, "SALTAR", "jump")

func _add_touch_button(parent: Node, caption: String, action_name: String) -> void:
    var button := Button.new()
    button.text = caption
    button.custom_minimum_size = Vector2(84, 54)
    button.focus_mode = Control.FOCUS_NONE
    button.button_down.connect(func(): Input.action_press(action_name))
    button.button_up.connect(func(): Input.action_release(action_name))
    parent.add_child(button)
