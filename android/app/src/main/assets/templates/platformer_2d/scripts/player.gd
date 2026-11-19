extends CharacterBody2D

@export var speed := 310.0
@export var jump_velocity := -470.0

func _physics_process(delta: float) -> void:
    var axis := Input.get_axis("move_left", "move_right")
    if absf(axis) > 0.01:
        velocity.x = axis * speed
    else:
        velocity.x = move_toward(velocity.x, 0.0, speed * 6.0 * delta)

    if not is_on_floor():
        velocity += get_gravity() * delta
    elif Input.is_action_just_pressed("jump"):
        velocity.y = jump_velocity

    move_and_slide()
