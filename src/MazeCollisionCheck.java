//MazeCollisionCheck: this interface is used to check if the player is near the wall.

public interface MazeCollisionCheck {
	
	boolean isWall(float worldX, float worldZ);

}
