import java.awt.image.BufferedImage;
import java.util.Random;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

/**
 * Builds every texture the maze uses at runtime.
 *
 * Author: Youssef Higazy (wall/floor models and textures)
 *
 * Nothing here loads a file from disk. The textures are painted into a
 * BufferedImage with plain Java2D and then handed to JOGL, which means the
 * repo stays asset-free and the game looks identical on all four of our
 * machines - no "image not found" surprises when somebody clones it.
 *
 * Every texture is 256x256 and tiles seamlessly left-to-right and
 * top-to-bottom, so the geometry can repeat it across a whole wall or floor
 * without a visible seam.
 */
public final class TextureFactory {

    private static final int SIZE = 256;

    private TextureFactory() {
        // utility class - never instantiated
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** Rough brown brick with mortar lines. Used on the maze walls. */
    public static Texture createWallTexture(GL2 gl) {
        return upload(gl, paintBricks());
    }

    /** Grey stone slabs with darker grout. Used on the floor. */
    public static Texture createFloorTexture(GL2 gl) {
        return upload(gl, paintStoneTiles());
    }

    /** Dark, mottled surface. Used on the ceiling so the maze feels enclosed. */
    public static Texture createCeilingTexture(GL2 gl) {
        return upload(gl, paintCeiling());
    }

    /** Bright green pulse pattern for the exit marker. */
    public static Texture createExitTexture(GL2 gl) {
        return upload(gl, paintExit());
    }

    // ------------------------------------------------------------------
    // Upload
    // ------------------------------------------------------------------

    /**
     * Hands a BufferedImage to OpenGL as a mipmapped, repeating texture.
     *
     * Mipmaps matter here: a maze is full of walls seen at a sharp angle far
     * down a corridor, and without them the brick pattern shimmers badly as
     * the player walks. GL_LINEAR_MIPMAP_LINEAR (trilinear) is what smooths
     * that out.
     */
    private static Texture upload(GL2 gl, BufferedImage image) {
        Texture texture = AWTTextureIO.newTexture(GLProfile.get(GLProfile.GL2), image, true);

        texture.setTexParameteri(gl, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
        texture.setTexParameteri(gl, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        texture.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        texture.setTexParameteri(gl, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);

        return texture;
    }

    // ------------------------------------------------------------------
    // Painting
    // ------------------------------------------------------------------

    /**
     * Running-bond brick: 8 courses tall, every other course offset by half a
     * brick. Drawn per-pixel rather than with Graphics2D rectangles so the
     * pattern wraps cleanly at the edges (a modulo on x is all it takes).
     */
    private static BufferedImage paintBricks() {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Random rng = new Random(4361L);

        final int courseHeight = SIZE / 8;   // 32px tall bricks
        final int brickWidth = SIZE / 4;     // 64px wide bricks
        final int mortar = 3;                // mortar line thickness in px

        // Per-brick colour jitter, so the wall does not look stamped out.
        // 8 courses x 4 bricks, plus slack for the offset rows.
        float[][] tint = new float[8][8];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                tint[r][c] = 0.82f + rng.nextFloat() * 0.30f;
            }
        }

        for (int y = 0; y < SIZE; y++) {
            int course = y / courseHeight;
            int yInCourse = y % courseHeight;
            // Offset every other course by half a brick (running bond)
            int xOffset = (course % 2 == 0) ? 0 : brickWidth / 2;

            for (int x = 0; x < SIZE; x++) {
                int xShifted = (x + xOffset) % SIZE;
                int brickIndex = xShifted / brickWidth;
                int xInBrick = xShifted % brickWidth;

                boolean isMortar = yInCourse < mortar || xInBrick < mortar;

                int r, g, b;
                if (isMortar) {
                    int grey = 138 + rng.nextInt(14);
                    r = grey;
                    g = grey;
                    b = (int) (grey * 0.96f);
                } else {
                    float t = tint[course % 8][brickIndex % 8];
                    // Fine grain plus a soft vertical shade inside each brick,
                    // which reads as a slightly rounded surface once lit.
                    float grain = 0.93f + rng.nextFloat() * 0.14f;
                    float shade = 1.0f - 0.14f * (yInCourse / (float) courseHeight);
                    float k = t * grain * shade;
                    r = (int) (150 * k);
                    g = (int) (77 * k);
                    b = (int) (52 * k);
                }
                img.setRGB(x, y, clampRGB(r, g, b));
            }
        }
        return img;
    }

    /**
     * Floor: 4x4 stone slabs separated by dark grout, each slab given its own
     * brightness and a light speckle so adjacent cells do not look cloned.
     */
    private static BufferedImage paintStoneTiles() {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Random rng = new Random(1121L);

        final int tile = SIZE / 4;   // 64px slabs
        final int grout = 4;

        float[][] tint = new float[4][4];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                tint[r][c] = 0.85f + rng.nextFloat() * 0.28f;
            }
        }

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int tx = x / tile;
                int ty = y / tile;
                boolean isGrout = (x % tile) < grout || (y % tile) < grout;

                int r, g, b;
                if (isGrout) {
                    int grey = 46 + rng.nextInt(10);
                    r = grey;
                    g = grey;
                    b = grey + 4;
                } else {
                    float k = tint[ty][tx] * (0.94f + rng.nextFloat() * 0.12f);
                    int grey = (int) (118 * k);
                    r = grey;
                    g = grey;
                    b = (int) (grey * 1.04f);   // faint cool cast, reads as stone
                }
                img.setRGB(x, y, clampRGB(r, g, b));
            }
        }
        return img;
    }

    /**
     * Ceiling: no pattern, just dark mottled noise. It is deliberately plain -
     * the player rarely looks up, and keeping it dark makes the torch light
     * pooling on the floor read much more strongly.
     */
    private static BufferedImage paintCeiling() {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Random rng = new Random(777L);

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                // Two frequencies of noise so it looks like rough rock rather
                // than TV static.
                float coarse = 0.5f + 0.5f * (float) Math.sin(x * 0.05f) * (float) Math.cos(y * 0.043f);
                float fine = rng.nextFloat();
                int grey = (int) (26 + coarse * 16 + fine * 10);
                img.setRGB(x, y, clampRGB(grey, grey, (int) (grey * 1.10f)));
            }
        }
        return img;
    }

    /**
     * Exit marker: concentric green rings. Drawn on the optional exit pillar so
     * the goal is visible from down a corridor even before the win logic fires.
     */
    private static BufferedImage paintExit() {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                // Rings run horizontally around the pillar
                float band = 0.5f + 0.5f * (float) Math.sin(y * 0.14f);
                int g = (int) (90 + band * 165);
                int r = (int) (20 + band * 60);
                int b = (int) (40 + band * 70);
                img.setRGB(x, y, clampRGB(r, g, b));
            }
        }
        return img;
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    /** Clamps each channel to 0..255 and packs them into one int. */
    private static int clampRGB(int r, int g, int b) {
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        return (r << 16) | (g << 8) | b;
    }
}
