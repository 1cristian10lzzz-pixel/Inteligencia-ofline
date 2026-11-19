extends CharacterBody3D

@export var move_speed := 5.2
@export var jump_velocity := 4.8

func _physics_process(delta: float) -> void:
    var input_vector := Input.get_vector("move_left", "move_right", "move_forward", "move_back")
    var direction := (transform.basis * Vector3(input_vector.x, 0.0, input_vector.y)).normalized()

    if direction.length() > 0.01:
        velocity.x = direction.x * move_speed
        velocity.z = direction.z * move_speed
    else:
        velocity.x = move_toward(velocity.x, 0.0, move_speed * 6.0 * delta)
        velocity.z = move_toward(velocity.z, 0.0, move_speed * 6.0 * delta)

    if not is_on_floor():
        velocity.y -= 9.8 * delta
    elif Input.is_action_just_pressed("jump"):
        velocity.y = jump_velocity

    move_and_slide()
