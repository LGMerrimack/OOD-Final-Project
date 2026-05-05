import java.util.ArrayList;
import java.util.List;

/**
 * This class keeps track of one ship on the board.
 */
public class Ship {
    public final String name;
    public final int size;
    public List<Cell> cells;

    public Ship(String name, int size) {
        this.name = name;
        this.size = size;
        this.cells = new ArrayList<>();
    }

    /** Returns true when the whole ship has been hit. */
    public boolean isSunk() {
        return cells.stream().allMatch(c -> c.isHit);
    }

    /** Returns how many parts of the ship have been hit so far. */
    public int hitCount() {
        return (int) cells.stream().filter(c -> c.isHit).count();
    }
}
