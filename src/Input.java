import java.awt.AWTException;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
//

public class Input implements KeyListener, MouseMotionListener{
	
	private final Set<Integer> keyDown = new HashSet<>();
	
	private final Set<Integer> keysPressed = new HashSet<>();
	
	
	private final Component canvas;
	private Robot robot;
	private int screenX;
	private int screenY;
	private float mouseX = 0f;
	private float mouseY = 0f;
	
	private boolean mouseCapture = true;
	
	public Input(Component canvas) {
		this.canvas = canvas;
		try {
			robot = new Robot();
		} catch (AWTException e) {
			System.out.println("Robot couldn't be created: " + e);
		}
	}
	
	public boolean isKeyDown(int k) {
		return keyDown.contains(k);
	}
	
	public boolean keysPressed(int k) {
		return keysPressed.contains(k);
	}
	
	public float getMouseX() {
		return mouseX;
	}
	
	public float getMouseY() {
		return mouseY;
	}
	
	public void frame() {
		keysPressed.clear();
		mouseX = 0f;
		mouseY = 0f;
		if(mouseCapture) {
			recenterMouse();
		}
	}
	
	private void recenterMouse() {
		if(robot == null) return;
		screenX = canvas.getLocationOnScreen().x + canvas.getWidth() / 2;
		screenY = canvas.getLocationOnScreen().y + canvas.getHeight() / 2;
		robot.mouseMove(screenX, screenY);
	}
	
	public void setMouseCaptured(boolean captured) {
		this.mouseCapture = captured;
		canvas.setCursor(captured ? 
				java.awt.Toolkit.getDefaultToolkit().createCustomCursor(
						new java.awt.image.BufferedImage(1,1, java.awt.image.BufferedImage.TYPE_INT_ARGB),
						new java.awt.Point(0,0), "blank")
				: java.awt.Cursor.getDefaultCursor());
	}
	
	public boolean isMouseCaptured() {
		return mouseCapture;
	}
	
	@Override
	public void keyPressed(KeyEvent e) {
		int c = e.getKeyCode();
		if(!keyDown.contains(c)) {
			keysPressed.add(c);
		}
		keyDown.add(c);
	}
	
	@Override
	public void keyReleased(KeyEvent e) {
		keyDown.remove(e.getKeyCode());
	}
	
	@Override
	public void keyTyped(KeyEvent e) {
	}
	
	
	public void mouseMove(MouseEvent e) {
		if(!mouseCapture) return;
		
		int deltaX = e.getXOnScreen() - screenX;
		int deltaY = e.getYOnScreen() - screenY;
		mouseX = deltaX * 0.1f;
		mouseY = deltaY * 0.1f;
	}
	
	@Override 
	public void mouseDragged(MouseEvent e) {
		mouseMove(e);
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		mouseMove(e);
		
	}

}
