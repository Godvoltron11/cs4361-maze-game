
public class Maze {
    // 1 = wall, 0 = open path
    private int[][] grid;
    private int width, height;

    public Maze(int[][] layout) {
        this.height = layout.length;
        this.width = layout[0].length;
        this.grid = layout;
    }

    public int get(int row, int col) {
        return grid[row][col];
    }

    public void set(int row, int col, int value) {
        grid[row][col] = value;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
