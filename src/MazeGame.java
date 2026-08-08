import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.util.FPSAnimator;
import javax.swing.JFrame;
import com.jogamp.opengl.util.awt.TextRenderer;
import java.awt.Color;
import java.awt.Font;
import javax.swing.JFrame;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;


public class MazeGame {
    public static void main(String[] args) {
        // Hardcoded test maze: 1 = wall, 0 = path
        int[][] layout = {
            {1,1,1,1,1,1,1,1},
            {1,0,0,0,1,0,0,1},
            {1,0,1,0,1,0,1,1},
            {1,0,1,0,0,0,0,1},
            {1,0,1,1,1,1,0,1},
            {1,0,0,0,0,1,0,1},
            {1,1,1,1,0,1,0,1},
            {1,1,1,1,1,1,1,1}
        };
        Maze maze = new Maze(layout, 6, 6);

        // Canvas has to exist BEFORE the Camera, since Camera needs
        // it (to find the screen center for mouse look)
        GLProfile profile = GLProfile.get(GLProfile.GL2);
        GLCapabilities caps = new GLCapabilities(profile);
        caps.setSampleBuffers(false);
        caps.setStencilBits(0);
        caps.setAlphaBits(0);
        caps.setDoubleBuffered(true);
        GLJPanel canvas = new GLJPanel(caps);

        Camera camera = new Camera(2f, 1f, 4f, canvas); // start inside the maze
        camera.yaw = 0f;
        camera.pitch = 0f;

        Renderer renderer = new Renderer(maze, camera);
        canvas.addGLEventListener(renderer);
        renderer.setExit(maze.getExitRow(), maze.getExitCol());

        // Let the camera actually receive key presses and mouse movement
        canvas.addKeyListener(camera);
        canvas.addMouseMotionListener(camera);
        
        Input input = new Input(canvas);
        canvas.addKeyListener(input);
        canvas.addMouseMotionListener(input);
        
        PlayerLogic player = new PlayerLogic(2f, 1f, 4f);
        player.yaw = camera.yaw;
        
        MazeCollisionCheck collision = new MazeCollisionCheck() {
        	@Override
        	public boolean isWall(float worldX, float worldZ) {
        		int col = Math.round(worldX / Renderer.CELL_SIZE);
        		int row = Math.round(worldZ / Renderer.CELL_SIZE);
        		boolean wall = (row < 0 || row >= maze.getHeight() ||
        				col < 0 || col >= maze.getWidth()) ||
        				maze.get(row, col) == 1;
        		return wall;
        	}
        };
        
        player.startTimer();
        
        final TextRenderer[] textRendererHolder = new TextRenderer[1];
        
        
        JFrame frame = new JFrame("Maze Game");
        frame.setSize(800, 600);
        frame.add(canvas);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        
        BufferedImage cursorImg = new BufferedImage(16,16, BufferedImage.TYPE_INT_ARGB);
        Cursor blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(cursorImg, new Point(0,0), "blank cursor");
        canvas.setCursor(blankCursor);

        // The canvas needs keyboard focus, or key presses won't
        // reach the Camera's keyPressed()/keyReleased() methods
        canvas.requestFocusInWindow();
        
        canvas.addGLEventListener(new GLEventListener() {
        	@Override 
        	public void init(GLAutoDrawable drawable) { 
        		textRendererHolder[0] = new TextRenderer(new Font("SanSerif", Font.BOLD, 24));
        	}

			@Override
			public void display(GLAutoDrawable drawable) {
				player.yaw = camera.yaw;
				player.update(input, collision);
				player.checkWin(maze, Renderer.CELL_SIZE);
				camera.x = player.x;
				camera.y = player.y;
				camera.z = player.z;
				input.frame();	
				
				TextRenderer textRenderer = textRendererHolder[0];
				
				float remainTime = player.getRemainingTime();
				int mins = (int) remainTime / 60;
				int seconds = (int) remainTime % 60;
				String timerText = String.format("Time: %02d:%02d", mins, seconds);
				int width = drawable.getSurfaceWidth();
				int height = drawable.getSurfaceHeight();
				textRenderer.beginRendering(width, height);
				textRenderer.setColor(remainTime <= 10f ? Color.RED : Color.WHITE);
				textRenderer.draw(timerText, 10, height - 30);
				
				
				if(player.hasWin()) {
					textRenderer.setColor(Color.BLUE);
					textRenderer.draw("YOU WIN!", width / 2 - 60, height / 2);
				}else if(player.isTimeExpired()) {
					textRenderer.setColor(Color.RED);
					textRenderer.draw("TIME'S UP! GAME OVER!", width / 2 - 60, height / 2);
					
				}
				textRenderer.endRendering();
			}

			public void reshape(GLAutoDrawable drawable, int x, int y, int z, int h) { }
			
			@Override
			public void dispose(GLAutoDrawable drawable) { }
        	
        }); 

        FPSAnimator animator = new FPSAnimator(canvas, 60);
        animator.start();
    }
}