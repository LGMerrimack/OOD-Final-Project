import java.util.ArrayList;
import java.util.List;

/**
 * This class represents one square on the board.
 * It also remembers the squares directly next to it.
 */
public class Cell {
    public final int row;
    public final int col;
    public boolean hasShip;
    public boolean isHit;
    public Ship ship; // The ship on this square, or null if it is empty.
    public List<Cell> neighbors; // The squares directly next to this one.

    public Cell(int row, int col) {
        this.row = row;
        this.col = col;
        this.hasShip = false;
        this.isHit = false;
        this.ship = null;
        this.neighbors = new ArrayList<>();
    }

    @Override
    public String toString() {
        return "(" + (row + 1) + "," + (char)('A' + col) + ")";
    }
}
