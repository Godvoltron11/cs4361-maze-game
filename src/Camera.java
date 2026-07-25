import java.awt.AWTException;
import java.awt.Component;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

// This is the "eyes" of the player in the maze game.
// It remembers where the player is standing and which way they're
// looking, and it listens for the arrow keys and mouse so it can
// move and turn when the player presses/moves them.
public class Camera implements KeyListener, MouseMotionListener {

    // Where the camera is standing in the maze (like a location on a map)
    public float x, y, z;

    // Which way the camera is facing.
    // yaw = turning left/right (like spinning around)
    // pitch = tilting up/down (like nodding your head)
    public float yaw;
    public float pitch;

    // How far the camera moves each time it steps forward/sideways
    public float moveSpeed = 0.1f;

    // These next few things are just needed to make the mouse work.
    // canvas is the game screen. Robot lets our code move the mouse
    // cursor by itself (explained more below).
    private Component canvas;
    private Robot robot;
    private int screenCenterX;
    private int screenCenterY;

    // These just remember which arrow keys are being held down
    // right now. True = held down, false = not held down.
    private boolean up = false;
    private boolean down = false;
    private boolean left = false;
    private boolean right = false;

    public Camera(float startX, float startY, float startZ, Component canvas) {
        x = startX; y = startY; z = startZ;
        yaw = 0; pitch = 0;
        this.canvas = canvas;

        // Try to set up Robot (the thing that lets us move the mouse).
        // On some computers this might not be allowed, so if it fails
        // we just skip mouse-recentering instead of crashing the game.
        try {
            robot = new Robot();
        } catch (AWTException e) {
            System.out.println("Robot could not be created: " + e);
        }
    }

    // This runs once every single frame (many times per second).
    // It's like asking "is a key being held right now?" over and
    // over, and moving the camera a tiny bit each time the answer
    // is yes. That's what makes holding a key feel smooth instead
    // of jumpy.
    public void update() {
        if (up) moveForward(moveSpeed);
        if (down) moveForward(-moveSpeed);
        if (right) strafe(moveSpeed);
        if (left) strafe(-moveSpeed);

        recenterMouse();
    }

    // This puts the mouse cursor back in the middle of the screen,
    // every single frame. Why? So the player can keep moving the
    // mouse to turn the camera forever, without ever running out of
    // desk space or hitting the edge of the screen. It also
    // remembers where "the middle" is, so we can compare the mouse's
    // next position to it (see mouseMoved below).
    private void recenterMouse() {
        if (robot == null) return;

        screenCenterX = canvas.getLocationOnScreen().x + canvas.getWidth() / 2;
        screenCenterY = canvas.getLocationOnScreen().y + canvas.getHeight() / 2;
        robot.mouseMove(screenCenterX, screenCenterY);
    }

    // Moves the camera forward (or backward, if amount is negative),
    // in whatever direction it's currently facing. The math with
    // sin/cos just converts "which way am I facing" into "how much
    // should x and z change" - you don't need to know how that math
    // works, just that it points movement in the direction yaw is set to.
    public void moveForward(float amount) {
        x += (float) Math.sin(Math.toRadians(yaw)) * amount;
        z -= (float) Math.cos(Math.toRadians(yaw)) * amount;
    }

    // Moves the camera sideways (left/right) instead of forward.
    // Same idea as moveForward, just aimed 90 degrees off to the side.
    public void strafe(float amount) {
        x += (float) Math.sin(Math.toRadians(yaw + 90)) * amount;
        z -= (float) Math.cos(Math.toRadians(yaw + 90)) * amount;
    }

    // Turns the camera. Gets called by the mouse-movement code below,
    // using however far the mouse moved as the turn amount.
    public void rotate(float deltaYaw, float deltaPitch) {
        yaw += deltaYaw;
        pitch += deltaPitch;

        // Don't let the player tilt so far up/down that the camera
        // flips upside down - cap it at 89 degrees either way
        if (pitch > 89) pitch = 89;
        if (pitch < -89) pitch = -89;
    }

    // This runs automatically whenever the mouse moves.
    // We check how far the mouse is from the "middle" point we
    // remembered earlier, and turn the camera by that amount.
    // Example: if the mouse moved 5 pixels right, we turn the
    // camera a little bit to the right too.
    @Override
    public void mouseMoved(MouseEvent e) {
        int deltaX = e.getXOnScreen() - screenCenterX;
        int deltaY = e.getYOnScreen() - screenCenterY;

        // deltaY is flipped (negative) because on screen, "up" is a
        // smaller number - but we want moving the mouse up to look up
        rotate(deltaX * 0.1f, -deltaY * 0.1f);
    }

    // If the player clicks and drags the mouse, treat it exactly
    // the same as just moving the mouse
    @Override
    public void mouseDragged(MouseEvent e) {
        mouseMoved(e);
    }

    // Runs automatically the moment an arrow key is pressed down.
    // All it does is flip a switch (true) - the actual movement
    // happens up in update()
    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_UP) up = true;
        if (key == KeyEvent.VK_DOWN) down = true;
        if (key == KeyEvent.VK_LEFT) left = true;
        if (key == KeyEvent.VK_RIGHT) right = true;
    }

    // Runs automatically the moment an arrow key is let go.
    // Flips the switch back off (false)
    @Override
    public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_UP) up = false;
        if (key == KeyEvent.VK_DOWN) down = false;
        if (key == KeyEvent.VK_LEFT) left = false;
        if (key == KeyEvent.VK_RIGHT) right = false;
    }

    // Java requires this method to exist since we said this class
    // "implements KeyListener", but we don't actually need it for
    // this game, so it's left empty
    @Override
    public void keyTyped(KeyEvent e) {
    }
}