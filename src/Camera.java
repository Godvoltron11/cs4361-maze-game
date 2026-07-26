import java.awt.AWTException;
import java.awt.Component;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

public class Camera implements KeyListener, MouseMotionListener {

    public float x, y, z;
    public float yaw;
    public float pitch;
    public float turnSpeed = 1.5f;

    private Component canvas;
    private Robot robot;
    private int screenCenterX;
    private int screenCenterY;

    private boolean up = false;
    private boolean down = false;
    private boolean left = false;
    private boolean right = false;

    public Camera(float startX, float startY, float startZ, Component canvas) {
        x = startX; y = startY; z = startZ;
        yaw = 0; pitch = 0;
        this.canvas = canvas;

        try {
            robot = new Robot();
        } catch (AWTException e) {
            System.out.println("Robot could not be created: " + e);
        }
    }

    public void update() {
        if (up) rotate(0, turnSpeed);
        if (down) rotate(0, -turnSpeed);
        if (left) rotate(-turnSpeed, 0);
        if (right) rotate(turnSpeed, 0);

        recenterMouse();
    }

    private void recenterMouse() {
        if (robot == null) return;

        screenCenterX = canvas.getLocationOnScreen().x + canvas.getWidth() / 2;
        screenCenterY = canvas.getLocationOnScreen().y + canvas.getHeight() / 2;
        robot.mouseMove(screenCenterX, screenCenterY);
    }

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

        if (pitch > 89) pitch = 89;
        if (pitch < -89) pitch = -89;
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        int deltaX = e.getXOnScreen() - screenCenterX;
        int deltaY = e.getYOnScreen() - screenCenterY;

        rotate(deltaX * 0.1f, -deltaY * 0.1f);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        mouseMoved(e);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_UP) up = true;
        if (key == KeyEvent.VK_DOWN) down = true;
        if (key == KeyEvent.VK_LEFT) left = true;
        if (key == KeyEvent.VK_RIGHT) right = true;
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_UP) up = false;
        if (key == KeyEvent.VK_DOWN) down = false;
        if (key == KeyEvent.VK_LEFT) left = false;
        if (key == KeyEvent.VK_RIGHT) right = false;
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }
}
