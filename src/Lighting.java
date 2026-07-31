import com.jogamp.opengl.GL2;
import com.jogamp.opengl.fixedfunc.GLLightingFunc;

/**
 * All of the lighting, material and fog state for the maze.
 *
 * Author: Youssef Higazy (lighting and visual polish)
 *
 * The lighting model is deliberately simple but does the one thing a maze
 * really needs: it makes distance readable. Three pieces:
 *
 *   GL_LIGHT0 - a torch the player carries. It is a positional light parked at
 *               the camera every frame with distance attenuation, so corridors
 *               fall off into darkness ahead of you and walls brighten as you
 *               approach them. It also flickers slightly, which sells it as a
 *               flame rather than a flashlight bolted to your head.
 *
 *   GL_LIGHT1 - a very dim directional fill from above. Without it, surfaces
 *               facing away from the torch go completely black and the maze
 *               becomes unreadable; this just lifts them off zero.
 *
 *   Fog       - exponential-squared fog in the same colour as the background.
 *               This is what stops far-off walls popping in as flat shapes at
 *               the clip plane, and it doubles the sense of depth for almost
 *               no cost.
 *
 * IMPORTANT for whoever touches display(): a positional light's coordinates are
 * transformed by the modelview matrix at the moment glLightfv is called. So
 * updateTorch() must be called AFTER gluLookAt, otherwise the torch ends up
 * stuck at a fixed spot in the maze instead of following the player.
 */
public class Lighting {

    /** Background / fog colour. Renderer clears to this same colour. */
    public static final float[] FOG_COLOR = { 0.05f, 0.05f, 0.07f, 1f };

    // Torch colour - warm, slightly orange
    private static final float[] TORCH_DIFFUSE  = { 1.00f, 0.86f, 0.66f, 1f };
    private static final float[] TORCH_SPECULAR = { 0.45f, 0.40f, 0.32f, 1f };
    private static final float[] TORCH_AMBIENT  = { 0.06f, 0.05f, 0.05f, 1f };

    // Dim overhead fill so nothing is ever pure black
    private static final float[] FILL_DIFFUSE   = { 0.16f, 0.17f, 0.22f, 1f };
    private static final float[] FILL_POSITION  = { 0.3f, 1f, 0.2f, 0f };  // w=0 -> directional

    // Global ambient, kept low on purpose
    private static final float[] GLOBAL_AMBIENT = { 0.10f, 0.10f, 0.13f, 1f };

    /** Flip to false to see the raw unlit textures - handy when debugging UVs. */
    public boolean enabled = true;

    /** Flip to false to disable fog. */
    public boolean fogEnabled = true;

    /**
     * One-time setup. Call from GLEventListener.init().
     */
    public void setup(GL2 gl) {
        gl.glShadeModel(GLLightingFunc.GL_SMOOTH);
        gl.glEnable(GLLightingFunc.GL_LIGHTING);
        gl.glEnable(GLLightingFunc.GL_LIGHT0);
        gl.glEnable(GLLightingFunc.GL_LIGHT1);

        // Normals stay unit length even if anyone adds a glScale later
        gl.glEnable(GL2.GL_NORMALIZE);

        // Lets glColor3f keep driving the surface colour instead of forcing
        // every draw call to set a full material. The textures are combined on
        // top of it via GL_MODULATE (set in Renderer).
        gl.glEnable(GLLightingFunc.GL_COLOR_MATERIAL);
        gl.glColorMaterial(GL2.GL_FRONT, GLLightingFunc.GL_AMBIENT_AND_DIFFUSE);

        gl.glLightModelfv(GL2.GL_LIGHT_MODEL_AMBIENT, GLOBAL_AMBIENT, 0);
        gl.glLightModeli(GL2.GL_LIGHT_MODEL_LOCAL_VIEWER, 1);

        // --- torch (GL_LIGHT0) ---
        gl.glLightfv(GLLightingFunc.GL_LIGHT0, GLLightingFunc.GL_DIFFUSE, TORCH_DIFFUSE, 0);
        gl.glLightfv(GLLightingFunc.GL_LIGHT0, GLLightingFunc.GL_SPECULAR, TORCH_SPECULAR, 0);
        gl.glLightfv(GLLightingFunc.GL_LIGHT0, GLLightingFunc.GL_AMBIENT, TORCH_AMBIENT, 0);

        // Attenuation tuned against CELL_SIZE = 2: bright in the current cell,
        // usable one or two cells out, gone by about five.
        gl.glLightf(GLLightingFunc.GL_LIGHT0, GLLightingFunc.GL_CONSTANT_ATTENUATION, 0.45f);
        gl.glLightf(GLLightingFunc.GL_LIGHT0, GLLightingFunc.GL_LINEAR_ATTENUATION, 0.09f);
        gl.glLightf(GLLightingFunc.GL_LIGHT0, GLLightingFunc.GL_QUADRATIC_ATTENUATION, 0.020f);

        // --- overhead fill (GL_LIGHT1) ---
        gl.glLightfv(GLLightingFunc.GL_LIGHT1, GLLightingFunc.GL_DIFFUSE, FILL_DIFFUSE, 0);

        // Surfaces are matte stone and brick, so specular is almost off. A
        // little shininess keeps the torch highlight from looking like a blob.
        float[] matSpecular = { 0.10f, 0.10f, 0.10f, 1f };
        gl.glMaterialfv(GL2.GL_FRONT, GLLightingFunc.GL_SPECULAR, matSpecular, 0);
        gl.glMaterialf(GL2.GL_FRONT, GLLightingFunc.GL_SHININESS, 12f);

        setupFog(gl);
    }

    private void setupFog(GL2 gl) {
        gl.glFogi(GL2.GL_FOG_MODE, GL2.GL_EXP2);
        gl.glFogfv(GL2.GL_FOG_COLOR, FOG_COLOR, 0);
        gl.glFogf(GL2.GL_FOG_DENSITY, 0.085f);
        gl.glHint(GL2.GL_FOG_HINT, GL2.GL_NICEST);
    }

    /**
     * Per-frame update. Must be called AFTER gluLookAt (see class comment).
     *
     * @param seconds time since the game started, used for the flame flicker
     */
    public void update(GL2 gl, Camera camera, double seconds) {
        if (!enabled) {
            gl.glDisable(GLLightingFunc.GL_LIGHTING);
        } else {
            gl.glEnable(GLLightingFunc.GL_LIGHTING);
            updateTorch(gl, camera, seconds);
            // Directional light, but still multiplied by the current modelview,
            // so it has to be re-sent after gluLookAt as well.
            gl.glLightfv(GLLightingFunc.GL_LIGHT1, GLLightingFunc.GL_POSITION, FILL_POSITION, 0);
        }

        if (fogEnabled) {
            gl.glEnable(GL2.GL_FOG);
        } else {
            gl.glDisable(GL2.GL_FOG);
        }
    }

    private void updateTorch(GL2 gl, Camera camera, double seconds) {
        // Sit the torch a little above and in front of the eye. Putting it
        // exactly at the eye makes every surface face the light head-on and
        // the shading goes flat; offsetting it gives the walls some gradient.
        float yawRad = (float) Math.toRadians(camera.yaw);
        float offX = (float) Math.sin(yawRad) * 0.35f;
        float offZ = (float) -Math.cos(yawRad) * 0.35f;

        float[] position = {
            camera.x + offX,
            camera.y + 0.25f,
            camera.z + offZ,
            1f   // w = 1 -> positional, so attenuation applies
        };
        gl.glLightfv(GLLightingFunc.GL_LIGHT0, GLLightingFunc.GL_POSITION, position, 0);

        // Two out-of-phase sine waves so the flicker never settles into an
        // obvious loop. Stays within roughly 0.88x - 1.0x brightness; anything
        // stronger reads as a broken monitor rather than a flame.
        float flicker = 0.94f
                + 0.045f * (float) Math.sin(seconds * 11.0)
                + 0.020f * (float) Math.sin(seconds * 23.7);

        float[] diffuse = {
            TORCH_DIFFUSE[0] * flicker,
            TORCH_DIFFUSE[1] * flicker,
            TORCH_DIFFUSE[2] * flicker,
            1f
        };
        gl.glLightfv(GLLightingFunc.GL_LIGHT0, GLLightingFunc.GL_DIFFUSE, diffuse, 0);
    }
}
