import java.awt.event.KeyEvent;

/*PlayerLogic: this class is for including player logic such as
movements (keyboards), start time, finish time, making sure the 
player is only playing when the game is starting */

public class PlayerLogic {
	public float x, y, z;
	public float yaw;
	
	private float moveSpeed = 0.15f;
	private static final float TIME_LIMIT_SECONDS = 120f; // minute timer for the game
	private long startTimeMillis;
	private long finishTimeMillis = -1;
	private boolean gameRunning = false;
	private boolean timeOver = false;
	private boolean win = false;
	
	public PlayerLogic(float startX, float startY, float startZ) {
		x = startX; y = startY; z = startZ;
		yaw = 0;
	}
	
	//start the timer when the game is running
	public void startTimer() {
		startTimeMillis = System.currentTimeMillis();
		gameRunning = true;
		finishTimeMillis = -1;
		timeOver = false;
	}
	
	public void finishTimer() {
		if(gameRunning) {
			finishTimeMillis = System.currentTimeMillis();
			gameRunning = false;
		}	
	}
	
	public float getElapsedSeconds() {
		long end = gameRunning ? System.currentTimeMillis() : finishTimeMillis;
		if(end < 0) return 0f;
		return (end - startTimeMillis) / 1000f;
	}
	
	//shows the player on how much time is left for the game to be over
	public float getRemainingTime() {
		float remainTime = TIME_LIMIT_SECONDS - getElapsedSeconds();
		return Math.max(0f, remainTime);
	}
	
	//Once time expired it game over
	public boolean isTimeExpired() {
		return timeOver;
	}
	
	public boolean hasWin() {
		return win;
	}
	
	public void checkWin(Maze maze, float cellSize) {
		if (win || timeOver) return;
		
		float exitX = maze.getExitCol() * cellSize;
		float exitZ = maze.getExitRow() * cellSize;
		float dx = x - exitX;
		float dz = z - exitZ;
		float distance = (float) Math.sqrt(dx * dx + dz * dz);
		
		if(distance < 1.0f) {
			win = true;
			finishTimer();
		}
	}
	
	//this includes updates for the timer, keyboard movements (WASD) and wall collision detection for the player
	public void update(Input input, MazeCollisionCheck collision) {
		if(!gameRunning) return;
		
		if(getRemainingTime() <= 0f) {
			timeOver = true;
			finishTimer();
			return;
		}
		
		if (win) return;
		
		float moveX = 0f, moveZ = 0f;
		
		if(input.isKeyDown(KeyEvent.VK_W)) {
			moveX += (float) Math.sin(Math.toRadians(yaw)) * moveSpeed;
			
			moveZ -= (float) Math.cos(Math.toRadians(yaw)) * moveSpeed;
			
		}
		
		if(input.isKeyDown(KeyEvent.VK_S)) {
			moveX -= (float) Math.sin(Math.toRadians(yaw)) * moveSpeed;
			
			moveZ += (float) Math.cos(Math.toRadians(yaw)) * moveSpeed;
			
		}
		
		if(input.isKeyDown(KeyEvent.VK_A)) {
			moveX -= (float) Math.sin(Math.toRadians(yaw + 90)) * moveSpeed;
			
			moveZ += (float) Math.cos(Math.toRadians(yaw + 90)) * moveSpeed;
			
		}
		
		if(input.isKeyDown(KeyEvent.VK_D)) {
			moveX += (float) Math.sin(Math.toRadians(yaw + 90)) * moveSpeed;
			
			moveZ -= (float) Math.cos(Math.toRadians(yaw + 90)) * moveSpeed;
			
		}
		
		float nextX = x + moveX;
		
		if(!collision.isWall(nextX, z)) {
			x = nextX;
		}
		
		float nextZ = z + moveZ;
		if(!collision.isWall(x, nextZ)) {
			z = nextZ;
		}
		
			
	}	
	
}
