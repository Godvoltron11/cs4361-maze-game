import com.jogamp.opengl.GL2;

/**
 * Turns the Maze grid into actual 3D geometry: wall cubes, a floor, and a
 * ceiling, all with surface normals and texture coordinates.
 *
 * Author: Youssef Higazy (basic 3D rendering pipeline, wall/floor models)
 *
 * Two things this class does that a naive "draw a cube per wall cell" loop
 * does not:
 *
 *   1. Hidden-face removal. A wall face is only emitted if the neighbouring
 *      cell is walkable. Two wall cells sitting next to each other never draw
 *      the faces they share, and the outside of the maze border is skipped
 *      entirely because the player can never get out there to see it. On the
 *      8x8 test maze this cuts the triangle count by more than half.
 *
 *   2. Display lists. The maze never changes shape once the game starts, so
 *      all of that geometry is compiled once in build() and replayed by the
 *      GPU every frame with a single glCallList. Re-sending every vertex 60
 *      times a second in immediate mode is what would otherwise cap our frame
 *      rate on the lab machines.
 *
 * World layout convention (kept identical to the original renderer so the
 * collision code and the camera start position still line up):
 *   cell (row, col) is centred at world x = col * CELL_SIZE, z = row * CELL_SIZE
 *   the floor is y = 0 and walls run up to y = WALL_HEIGHT.
 */
public class MazeGeometry {

    /** Width and depth of one maze cell in world units. */
    public static final float CELL_SIZE = 2.0f;

    /** How tall the walls are. Kept equal to CELL_SIZE so cells are cubes. */
    public static final float WALL_HEIGHT = 2.0f;

    private final Maze maze;

    // Display list handles. 0 means "not built yet".
    private int wallList = 0;
    private int floorList = 0;
    private int ceilingList = 0;

    public MazeGeometry(Maze maze) {
        this.maze = maze;
    }

    /**
     * Compiles the whole maze into three display lists. Call once from
     * GLEventListener.init(), after the GL context exists.
     */
    public void build(GL2 gl) {
        dispose(gl);   // safe to call again if the context is recreated

        wallList = gl.glGenLists(1);
        gl.glNewList(wallList, GL2.GL_COMPILE);
        emitWalls(gl);
        gl.glEndList();

        floorList = gl.glGenLists(1);
        gl.glNewList(floorList, GL2.GL_COMPILE);
        emitFloor(gl);
        gl.glEndList();

        ceilingList = gl.glGenLists(1);
        gl.glNewList(ceilingList, GL2.GL_COMPILE);
        emitCeiling(gl);
        gl.glEndList();
    }

    public void drawWalls(GL2 gl) {
        if (wallList != 0) gl.glCallList(wallList);
    }

    public void drawFloor(GL2 gl) {
        if (floorList != 0) gl.glCallList(floorList);
    }

    public void drawCeiling(GL2 gl) {
        if (ceilingList != 0) gl.glCallList(ceilingList);
    }

    /** Releases the display lists. Called from GLEventListener.dispose(). */
    public void dispose(GL2 gl) {
        if (wallList != 0) {
            gl.glDeleteLists(wallList, 1);
            wallList = 0;
        }
        if (floorList != 0) {
            gl.glDeleteLists(floorList, 1);
            floorList = 0;
        }
        if (ceilingList != 0) {
            gl.glDeleteLists(ceilingList, 1);
            ceilingList = 0;
        }
    }

    // ------------------------------------------------------------------
    // Geometry emission
    // ------------------------------------------------------------------

    private void emitWalls(GL2 gl) {
        float s = CELL_SIZE / 2f;
        float h = WALL_HEIGHT;

        gl.glBegin(GL2.GL_QUADS);
        for (int row = 0; row < maze.getHeight(); row++) {
            for (int col = 0; col < maze.getWidth(); col++) {
                if (maze.get(row, col) != 1) continue;

                float x = col * CELL_SIZE;
                float z = row * CELL_SIZE;

                // Texture coordinates are world-aligned (world position
                // divided by cell size) rather than 0..1 per face, so the
                // brick courses line up across neighbouring wall cells
                // instead of restarting at every seam.
                float u0 = col;
                float u1 = col + 1;
                float w0 = row;
                float w1 = row + 1;

                // +Z face - visible only if the cell in front is walkable
                if (isOpen(row + 1, col)) {
                    gl.glNormal3f(0f, 0f, 1f);
                    gl.glTexCoord2f(u0, 1f); gl.glVertex3f(x - s, 0f, z + s);
                    gl.glTexCoord2f(u1, 1f); gl.glVertex3f(x + s, 0f, z + s);
                    gl.glTexCoord2f(u1, 0f); gl.glVertex3f(x + s, h,  z + s);
                    gl.glTexCoord2f(u0, 0f); gl.glVertex3f(x - s, h,  z + s);
                }

                // -Z face
                if (isOpen(row - 1, col)) {
                    gl.glNormal3f(0f, 0f, -1f);
                    gl.glTexCoord2f(u0, 1f); gl.glVertex3f(x + s, 0f, z - s);
                    gl.glTexCoord2f(u1, 1f); gl.glVertex3f(x - s, 0f, z - s);
                    gl.glTexCoord2f(u1, 0f); gl.glVertex3f(x - s, h,  z - s);
                    gl.glTexCoord2f(u0, 0f); gl.glVertex3f(x + s, h,  z - s);
                }

                // -X face
                if (isOpen(row, col - 1)) {
                    gl.glNormal3f(-1f, 0f, 0f);
                    gl.glTexCoord2f(w0, 1f); gl.glVertex3f(x - s, 0f, z - s);
                    gl.glTexCoord2f(w1, 1f); gl.glVertex3f(x - s, 0f, z + s);
                    gl.glTexCoord2f(w1, 0f); gl.glVertex3f(x - s, h,  z + s);
                    gl.glTexCoord2f(w0, 0f); gl.glVertex3f(x - s, h,  z - s);
                }

                // +X face
                if (isOpen(row, col + 1)) {
                    gl.glNormal3f(1f, 0f, 0f);
                    gl.glTexCoord2f(w0, 1f); gl.glVertex3f(x + s, 0f, z + s);
                    gl.glTexCoord2f(w1, 1f); gl.glVertex3f(x + s, 0f, z - s);
                    gl.glTexCoord2f(w1, 0f); gl.glVertex3f(x + s, h,  z - s);
                    gl.glTexCoord2f(w0, 0f); gl.glVertex3f(x + s, h,  z + s);
                }

                // Top cap. The player's eye is below WALL_HEIGHT so this is
                // normally hidden, but it stops the wall looking hollow if
                // anyone raises the camera or pitches up at a low wall.
                gl.glNormal3f(0f, 1f, 0f);
                gl.glTexCoord2f(u0, w1); gl.glVertex3f(x - s, h, z + s);
                gl.glTexCoord2f(u1, w1); gl.glVertex3f(x + s, h, z + s);
                gl.glTexCoord2f(u1, w0); gl.glVertex3f(x + s, h, z - s);
                gl.glTexCoord2f(u0, w0); gl.glVertex3f(x - s, h, z - s);
            }
        }
        gl.glEnd();
    }

    private void emitFloor(GL2 gl) {
        float s = CELL_SIZE / 2f;

        gl.glBegin(GL2.GL_QUADS);
        gl.glNormal3f(0f, 1f, 0f);
        for (int row = 0; row < maze.getHeight(); row++) {
            for (int col = 0; col < maze.getWidth(); col++) {
                if (maze.get(row, col) == 1) continue;   // no floor under a wall

                float x = col * CELL_SIZE;
                float z = row * CELL_SIZE;
                float u0 = col, u1 = col + 1;
                float w0 = row, w1 = row + 1;

                gl.glTexCoord2f(u0, w1); gl.glVertex3f(x - s, 0f, z + s);
                gl.glTexCoord2f(u1, w1); gl.glVertex3f(x + s, 0f, z + s);
                gl.glTexCoord2f(u1, w0); gl.glVertex3f(x + s, 0f, z - s);
                gl.glTexCoord2f(u0, w0); gl.glVertex3f(x - s, 0f, z - s);
            }
        }
        gl.glEnd();
    }

    private void emitCeiling(GL2 gl) {
        float s = CELL_SIZE / 2f;
        float h = WALL_HEIGHT;

        gl.glBegin(GL2.GL_QUADS);
        gl.glNormal3f(0f, -1f, 0f);   // faces down, toward the player
        for (int row = 0; row < maze.getHeight(); row++) {
            for (int col = 0; col < maze.getWidth(); col++) {
                if (maze.get(row, col) == 1) continue;

                float x = col * CELL_SIZE;
                float z = row * CELL_SIZE;
                float u0 = col, u1 = col + 1;
                float w0 = row, w1 = row + 1;

                gl.glTexCoord2f(u0, w0); gl.glVertex3f(x - s, h, z - s);
                gl.glTexCoord2f(u1, w0); gl.glVertex3f(x + s, h, z - s);
                gl.glTexCoord2f(u1, w1); gl.glVertex3f(x + s, h, z + s);
                gl.glTexCoord2f(u0, w1); gl.glVertex3f(x - s, h, z + s);
            }
        }
        gl.glEnd();
    }

    /**
     * A cell counts as open (and therefore as something that can see a wall
     * face) only if it is inside the grid and is not itself a wall. Treating
     * out-of-bounds as closed is what removes the outer shell of the maze.
     */
    private boolean isOpen(int row, int col) {
        if (row < 0 || row >= maze.getHeight()) return false;
        if (col < 0 || col >= maze.getWidth()) return false;
        return maze.get(row, col) != 1;
    }
}
