import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.util.FPSAnimator;
import javax.swing.JFrame;

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
        Maze maze = new Maze(layout);

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

        // Let the camera actually receive key presses and mouse movement
        canvas.addKeyListener(camera);
        canvas.addMouseMotionListener(camera);

        JFrame frame = new JFrame("Maze Game - Sameer's Build");
        frame.setSize(800, 600);
        frame.add(canvas);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        // The canvas needs keyboard focus, or key presses won't
        // reach the Camera's keyPressed()/keyReleased() methods
        canvas.requestFocusInWindow();

        FPSAnimator animator = new FPSAnimator(canvas, 60);
        animator.start();
    }
}