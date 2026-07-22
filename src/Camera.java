
public class Camera {
    public float x, y, z;       // position
    public float yaw;           // left/right rotation (degrees)
    public float pitch;         // up/down rotation (degrees)

    public Camera(float startX, float startY, float startZ) {
        x = startX; y = startY; z = startZ;
        yaw = 0; pitch = 0;
    }

    // Move forward/backward relative to facing direction
    public void moveForward(float amount) {
        x += (float) Math.sin(Math.toRadians(yaw)) * amount;
        z -= (float) Math.cos(Math.toRadians(yaw)) * amount;
    }

    public void strafe(float amount) {
        x += (float) Math.sin(Math.toRadians(yaw + 90)) * amount;
        z -= (float) Math.cos(Math.toRadians(yaw + 90)) * amount;
    }

    public void rotate(float deltaYaw, float deltaPitch) {
        yaw += deltaYaw;
        pitch += deltaPitch;
        // clamp pitch so you can't flip upside down
        if (pitch > 89) pitch = 89;
        if (pitch < -89) pitch = -89;
    }
}