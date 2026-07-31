import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.fixedfunc.GLLightingFunc;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.texture.Texture;

/**
 * The 3D rendering pipeline: sets up the GL state, builds the maze geometry
 * and textures once, and draws a frame from the camera's point of view.
 *
 * Author: Youssef Higazy (3D rendering pipeline, wall/floor models and
 * textures, lighting and visual polish)
 *
 * Per-frame order of operations, which matters more than it looks:
 *
 *   1. camera.update()      - apply this frame's input
 *   2. clear colour + depth
 *   3. gluLookAt            - load the view matrix
 *   4. lighting.update()    - light positions are baked by the CURRENT
 *                             modelview matrix, so this has to come after (3)
 *   5. draw floor / ceiling / walls, each with its own texture bound
 *   6. draw the exit marker, if one has been set
 *
 * The constructor signature is unchanged from the original version, so
 * MazeGame, Camera and Maze did not need any edits to pick all of this up.
 */
public class Renderer implements GLEventListener {

    /** Kept public for the collision and win-condition code. */
    public static final float CELL_SIZE = MazeGeometry.CELL_SIZE;
    public static final float WALL_HEIGHT = MazeGeometry.WALL_HEIGHT;

    private final Maze maze;
    private final Camera camera;

    private final GLU glu = new GLU();
    private final MazeGeometry geometry;
    private final Lighting lighting = new Lighting();

    private Texture wallTexture;
    private Texture floorTexture;
    private Texture ceilingTexture;
    private Texture exitTexture;

    /**
     * Falls back to flat colours if texture creation fails for any reason
     * (old driver, headless run). The game still renders, just plainer.
     */
    private boolean texturesReady = false;

    /** Public toggles - flip these from input handling if we want debug views. */
    public boolean texturesEnabled = true;
    public boolean ceilingEnabled = true;

    // Exit marker. -1 means "no exit set", and nothing extra is drawn.
    private int exitRow = -1;
    private int exitCol = -1;

    private long startNanos;

    public Renderer(Maze maze, Camera camera) {
        this.maze = maze;
        this.camera = camera;
        this.geometry = new MazeGeometry(maze);
    }

    /**
     * Optional hook for the maze/win-logic side: marks a cell with a glowing
     * green pillar so the goal is visible from down a corridor. Safe to never
     * call - the renderer just skips it.
     */
    public void setExit(int row, int col) {
        this.exitRow = row;
        this.exitCol = col;
    }

    /** Exposed so lighting and fog can be toggled from outside (debug views). */
    public Lighting getLighting() {
        return lighting;
    }

    // ------------------------------------------------------------------
    // GLEventListener
    // ------------------------------------------------------------------

    @Override
    public void init(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        startNanos = System.nanoTime();

        gl.glClearColor(Lighting.FOG_COLOR[0], Lighting.FOG_COLOR[1],
                        Lighting.FOG_COLOR[2], 1f);

        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LEQUAL);

        // Every face is wound counter-clockwise when seen from outside, so
        // back-face culling throws away roughly half the fragments for free.
        gl.glEnable(GL.GL_CULL_FACE);
        gl.glCullFace(GL.GL_BACK);
        gl.glFrontFace(GL.GL_CCW);

        gl.glEnable(GL2.GL_TEXTURE_2D);
        // MODULATE multiplies the texture by the lit surface colour, which is
        // what lets the torch actually darken and brighten the brickwork.
        // Under GL_REPLACE the textures would look flat and unlit.
        gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_MODULATE);
        gl.glHint(GL2.GL_PERSPECTIVE_CORRECTION_HINT, GL.GL_NICEST);

        lighting.setup(gl);
        geometry.build(gl);

        // GLJPanel can lose and rebuild its context, which calls init() again.
        // Drop anything from a previous context first so it does not leak.
        releaseTextures(gl);

        try {
            wallTexture = TextureFactory.createWallTexture(gl);
            floorTexture = TextureFactory.createFloorTexture(gl);
            ceilingTexture = TextureFactory.createCeilingTexture(gl);
            exitTexture = TextureFactory.createExitTexture(gl);
            texturesReady = true;
        } catch (RuntimeException e) {
            System.err.println("Texture setup failed, falling back to flat colours: " + e);
            texturesReady = false;
        }
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        // Applies any held-down keys and mouse movement from this frame -
        // without this call the camera's position/yaw/pitch never change.
        camera.update();

        GL2 gl = drawable.getGL().getGL2();
        double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;

        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();

        applyCamera();

        // After gluLookAt - see the note in Lighting.
        lighting.update(gl, camera, seconds);

        drawFloor(gl);
        if (ceilingEnabled) drawCeiling(gl);
        drawWalls(gl);
        drawExitMarker(gl, seconds);
    }

    /**
     * Builds the view matrix from the camera. Uses the same yaw/pitch maths as
     * Camera.moveForward(), so the direction you move and the direction you
     * are facing always agree.
     */
    private void applyCamera() {
        double yawRad = Math.toRadians(camera.yaw);
        double pitchRad = Math.toRadians(camera.pitch);

        double lookX = camera.x + Math.sin(yawRad) * Math.cos(pitchRad);
        double lookY = camera.y + Math.sin(pitchRad);
        double lookZ = camera.z - Math.cos(yawRad) * Math.cos(pitchRad);

        glu.gluLookAt(
            camera.x, camera.y, camera.z,
            lookX, lookY, lookZ,
            0, 1, 0
        );
    }

    // ------------------------------------------------------------------
    // Draw passes - one texture bind each, then replay a display list
    // ------------------------------------------------------------------

    private void drawWalls(GL2 gl) {
        gl.glColor3f(1f, 1f, 1f);
        bind(gl, wallTexture, 0.62f, 0.34f, 0.24f);
        geometry.drawWalls(gl);
        unbind(gl, wallTexture);
    }

    private void drawFloor(GL2 gl) {
        gl.glColor3f(1f, 1f, 1f);
        bind(gl, floorTexture, 0.34f, 0.34f, 0.36f);
        geometry.drawFloor(gl);
        unbind(gl, floorTexture);
    }

    private void drawCeiling(GL2 gl) {
        gl.glColor3f(1f, 1f, 1f);
        bind(gl, ceilingTexture, 0.16f, 0.16f, 0.19f);
        geometry.drawCeiling(gl);
        unbind(gl, ceilingTexture);
    }

    /**
     * A slowly pulsing green pillar on the exit cell. Uses GL_EMISSION so it
     * glows on its own instead of waiting for the torch to reach it - the whole
     * point is to be visible from far away in the dark.
     */
    private void drawExitMarker(GL2 gl, double seconds) {
        if (exitRow < 0 || exitCol < 0) return;
        if (exitRow >= maze.getHeight() || exitCol >= maze.getWidth()) return;

        float x = exitCol * CELL_SIZE;
        float z = exitRow * CELL_SIZE;
        float s = CELL_SIZE * 0.22f;      // slim pillar, easy to walk past
        float h = WALL_HEIGHT * 0.8f;

        float pulse = 0.55f + 0.45f * (float) Math.sin(seconds * 2.4);
        float[] emission = { 0.05f * pulse, 0.75f * pulse, 0.22f * pulse, 1f };
        float[] noEmission = { 0f, 0f, 0f, 1f };

        gl.glMaterialfv(GL2.GL_FRONT, GLLightingFunc.GL_EMISSION, emission, 0);
        gl.glColor3f(0.35f, 1f, 0.45f);
        bind(gl, exitTexture, 0.35f, 1f, 0.45f);

        gl.glBegin(GL2.GL_QUADS);
        // +Z
        gl.glNormal3f(0f, 0f, 1f);
        gl.glTexCoord2f(0f, 1f); gl.glVertex3f(x - s, 0f, z + s);
        gl.glTexCoord2f(1f, 1f); gl.glVertex3f(x + s, 0f, z + s);
        gl.glTexCoord2f(1f, 0f); gl.glVertex3f(x + s, h,  z + s);
        gl.glTexCoord2f(0f, 0f); gl.glVertex3f(x - s, h,  z + s);
        // -Z
        gl.glNormal3f(0f, 0f, -1f);
        gl.glTexCoord2f(0f, 1f); gl.glVertex3f(x + s, 0f, z - s);
        gl.glTexCoord2f(1f, 1f); gl.glVertex3f(x - s, 0f, z - s);
        gl.glTexCoord2f(1f, 0f); gl.glVertex3f(x - s, h,  z - s);
        gl.glTexCoord2f(0f, 0f); gl.glVertex3f(x + s, h,  z - s);
        // -X
        gl.glNormal3f(-1f, 0f, 0f);
        gl.glTexCoord2f(0f, 1f); gl.glVertex3f(x - s, 0f, z - s);
        gl.glTexCoord2f(1f, 1f); gl.glVertex3f(x - s, 0f, z + s);
        gl.glTexCoord2f(1f, 0f); gl.glVertex3f(x - s, h,  z + s);
        gl.glTexCoord2f(0f, 0f); gl.glVertex3f(x - s, h,  z - s);
        // +X
        gl.glNormal3f(1f, 0f, 0f);
        gl.glTexCoord2f(0f, 1f); gl.glVertex3f(x + s, 0f, z + s);
        gl.glTexCoord2f(1f, 1f); gl.glVertex3f(x + s, 0f, z - s);
        gl.glTexCoord2f(1f, 0f); gl.glVertex3f(x + s, h,  z - s);
        gl.glTexCoord2f(0f, 0f); gl.glVertex3f(x + s, h,  z + s);
        // top cap
        gl.glNormal3f(0f, 1f, 0f);
        gl.glTexCoord2f(0f, 1f); gl.glVertex3f(x - s, h, z + s);
        gl.glTexCoord2f(1f, 1f); gl.glVertex3f(x + s, h, z + s);
        gl.glTexCoord2f(1f, 0f); gl.glVertex3f(x + s, h, z - s);
        gl.glTexCoord2f(0f, 0f); gl.glVertex3f(x - s, h, z - s);
        gl.glEnd();

        unbind(gl, exitTexture);
        // Reset emission, or every surface drawn after this would glow too.
        gl.glMaterialfv(GL2.GL_FRONT, GLLightingFunc.GL_EMISSION, noEmission, 0);
    }

    // ------------------------------------------------------------------
    // Texture bind helpers, with a flat-colour fallback
    // ------------------------------------------------------------------

    private void bind(GL2 gl, Texture texture, float r, float g, float b) {
        if (texturesReady && texturesEnabled && texture != null) {
            texture.enable(gl);
            texture.bind(gl);
        } else {
            gl.glDisable(GL2.GL_TEXTURE_2D);
            gl.glColor3f(r, g, b);
        }
    }

    private void unbind(GL2 gl, Texture texture) {
        if (texturesReady && texturesEnabled && texture != null) {
            texture.disable(gl);
        }
    }

    // ------------------------------------------------------------------

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int w, int h) {
        if (h <= 0) h = 1;   // guard against a divide-by-zero on a collapsed window
        GL2 gl = drawable.getGL().getGL2();

        gl.glViewport(0, 0, w, h);
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        // Near plane is small because the player can stand right against a
        // wall; far plane only needs to outrun the fog.
        glu.gluPerspective(65.0, (double) w / h, 0.05, 100.0);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        geometry.dispose(gl);
        releaseTextures(gl);
    }

    private void releaseTextures(GL2 gl) {
        if (wallTexture != null) { wallTexture.destroy(gl); wallTexture = null; }
        if (floorTexture != null) { floorTexture.destroy(gl); floorTexture = null; }
        if (ceilingTexture != null) { ceilingTexture.destroy(gl); ceilingTexture = null; }
        if (exitTexture != null) { exitTexture.destroy(gl); exitTexture = null; }
        texturesReady = false;
    }
}
