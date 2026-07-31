# Rendering, Models, Textures and Lighting

**Owner: Youssef Higazy**
Covers the proposal rows *Basic 3D rendering pipeline*, *Wall/floor models & textures*,
and *Lighting & visual polish*. This is also the raw material for the
design/implementation section of the final report.

## Files

| File | What it does |
|---|---|
| `src/Renderer.java` | The pipeline. GL state setup, per-frame draw order, camera view matrix, exit marker. |
| `src/MazeGeometry.java` | Turns the `Maze` grid into wall/floor/ceiling geometry with normals and UVs. |
| `src/TextureFactory.java` | Paints the brick, stone, ceiling and exit textures at runtime. |
| `src/Lighting.java` | Lights, materials, fog. |

`Maze.java`, `Camera.java` and `MazeGame.java` were **not modified** — the
`Renderer(Maze, Camera)` constructor is unchanged, so everything below is picked
up with no edits on anyone else's side.

## World convention

Unchanged from the original renderer, so collision and win-condition code still
lines up:

- cell `(row, col)` is centred at `x = col * CELL_SIZE`, `z = row * CELL_SIZE`
- `CELL_SIZE = 2.0`, floor at `y = 0`, walls and ceiling at `y = 2.0`
- both constants are exposed as `Renderer.CELL_SIZE` / `Renderer.WALL_HEIGHT`

## Per-frame order

1. `camera.update()` — apply this frame's input
2. clear colour + depth
3. `gluLookAt` — load the view matrix
4. `lighting.update()` — **must** come after step 3; a positional light's
   coordinates are baked by whatever modelview matrix is current when
   `glLightfv(..., GL_POSITION, ...)` is called. Call it before `gluLookAt` and
   the torch stays pinned to one spot in the maze instead of following the player.
5. floor → ceiling → walls, one texture bind per pass
6. exit marker, if one has been set

## Models

Walls are cubes, but a face is only emitted when the neighbouring cell is
walkable. Shared faces between adjacent wall cells and the entire outer shell of
the maze are never sent. On the 8×8 test maze that removes more than half the
triangles.

The whole maze is compiled into three display lists in `init()` and replayed with
`glCallList` each frame. Geometry never changes after startup, so there is no
reason to re-send vertices 60 times a second.

Every face is wound counter-clockwise as seen from outside, which lets
`GL_CULL_FACE` drop the back halves for free.

## Textures

Generated procedurally into a `BufferedImage` and uploaded with `AWTTextureIO` —
no image files in the repo, so nothing to lose or path-break between our four
machines.

- **Walls** — 256×256 running-bond brick, per-brick colour jitter, mortar lines
- **Floor** — 4×4 stone slabs with dark grout
- **Ceiling** — dark two-frequency noise, deliberately plain so the torch pooling
  on the floor reads more strongly
- **Exit** — green rings

All four tile seamlessly and are mipmapped with trilinear filtering. Mipmaps are
not optional here: a maze is mostly walls seen at a sharp angle down a corridor,
and without them the brick pattern shimmers badly while walking.

UVs are world-aligned (world position ÷ cell size) rather than 0..1 per face, so
brick courses and floor grout continue across cell boundaries instead of
restarting at every seam.

Texture env is `GL_MODULATE`, not `GL_REPLACE` — that is what lets the lighting
actually darken and brighten the brickwork.

## Lighting

- **`GL_LIGHT0`** — a torch carried by the player: positional, parked slightly
  above and ahead of the eye each frame, with distance attenuation tuned against
  `CELL_SIZE = 2` (bright in the current cell, usable one or two cells out, gone
  by about five). Brightness flickers between roughly 0.88× and 1.0× using two
  out-of-phase sine waves so the loop is not obvious.
- **`GL_LIGHT1`** — a dim directional fill from above. Purely so that surfaces
  facing away from the torch do not go pure black and become unreadable.
- **Fog** — `GL_EXP2`, density 0.085, same colour as the background. Stops far
  walls popping in as flat shapes at the clip plane and roughly doubles the sense
  of depth for almost nothing.

The torch is offset from the eye rather than sitting exactly on it: a light at
the eye hits every surface head-on and the shading goes flat.

## Integration hooks for the rest of the team

```java
renderer.setExit(row, col);   // draws a glowing green pillar on that cell
renderer.texturesEnabled = false;  // debug: flat colours
renderer.ceilingEnabled = false;   // debug: top-down view of the maze
renderer.getLighting().enabled = false;     // debug: unlit textures
renderer.getLighting().fogEnabled = false;  // debug: no fog
```

`setExit` is optional — nothing is drawn until it is called, so the win-logic
side can wire it up whenever it is ready.

## Fallback behaviour

If texture creation throws (old driver, headless run), `Renderer` logs it and
falls back to flat per-surface colours. The game still runs, it just looks
plainer. Geometry, lighting and fog are unaffected.

## Build note

`.classpath` expects `lib/jogamp-fat.jar`, which is gitignored (`*.jar`). Drop
the JOGL fat jar in `lib/` after cloning or Eclipse will not resolve
`com.jogamp.*`. Everything in these four files uses only core JOGL 2 plus
`com.jogamp.opengl.util.texture`, both of which are in the fat jar.
