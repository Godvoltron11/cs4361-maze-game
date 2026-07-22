
import com.jogamp.opengl.*;
import com.jogamp.opengl.glu.GLU;

public class Renderer implements GLEventListener {
    private Maze maze;
    private Camera camera;
    private static final float CELL_SIZE = 2.0f;
    private GLU glu = new GLU();

    public Renderer(Maze maze, Camera camera) {
        this.maze = maze;
        this.camera = camera;
    }

    @Override
    public void init(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glClearColor(0.1f, 0.1f, 0.15f, 1f);
        gl.glEnable(GL2.GL_DEPTH_TEST);
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();

        // Apply camera transform
        glu.gluLookAt(
            camera.x, camera.y, camera.z,
            camera.x + Math.sin(Math.toRadians(camera.yaw)),
            camera.y - Math.sin(Math.toRadians(camera.pitch)),
            camera.z - Math.cos(Math.toRadians(camera.yaw)),
            0, 1, 0
        );

        drawMaze(gl);
    }

    private void drawMaze(GL2 gl) {
        for (int row = 0; row < maze.getHeight(); row++) {
            for (int col = 0; col < maze.getWidth(); col++) {
                float x = col * CELL_SIZE;
                float z = row * CELL_SIZE;
                if (maze.get(row, col) == 1) {
                    drawWallCube(gl, x, z);
                } else {
                    drawFloorQuad(gl, x, z);
                }
            }
        }
    }

    private void drawWallCube(GL2 gl, float x, float z) {
        gl.glColor3f(0.6f, 0.3f, 0.2f);
        float s = CELL_SIZE / 2f;
        gl.glBegin(GL2.GL_QUADS);
        // Front face
        gl.glVertex3f(x - s, 0, z + s);
        gl.glVertex3f(x + s, 0, z + s);
        gl.glVertex3f(x + s, CELL_SIZE, z + s);
        gl.glVertex3f(x - s, CELL_SIZE, z + s);
        // Back face
        gl.glVertex3f(x - s, 0, z - s);
        gl.glVertex3f(x + s, 0, z - s);
        gl.glVertex3f(x + s, CELL_SIZE, z - s);
        gl.glVertex3f(x - s, CELL_SIZE, z - s);
        // Left face
        gl.glVertex3f(x - s, 0, z - s);
        gl.glVertex3f(x - s, 0, z + s);
        gl.glVertex3f(x - s, CELL_SIZE, z + s);
        gl.glVertex3f(x - s, CELL_SIZE, z - s);
        // Right face
        gl.glVertex3f(x + s, 0, z - s);
        gl.glVertex3f(x + s, 0, z + s);
        gl.glVertex3f(x + s, CELL_SIZE, z + s);
        gl.glVertex3f(x + s, CELL_SIZE, z - s);
        gl.glEnd();
    }

    private void drawFloorQuad(GL2 gl, float x, float z) {
        gl.glColor3f(0.3f, 0.3f, 0.3f);
        float s = CELL_SIZE / 2f;
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex3f(x - s, 0, z - s);
        gl.glVertex3f(x + s, 0, z - s);
        gl.glVertex3f(x + s, 0, z + s);
        gl.glVertex3f(x - s, 0, z + s);
        gl.glEnd();
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int w, int h) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        glu.gluPerspective(60.0, (double) w / h, 0.5, 200.0);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {}
}