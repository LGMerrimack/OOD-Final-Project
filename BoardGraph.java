import java.util.*;

/**
 * This is the 10x10 game board.
 *
 * Each square knows which squares are right next to it. That makes it easier
 * to place ships, check hits, and let the bot search around a ship it found.
 */
public class BoardGraph {

    public static final int SIZE = 10;

    public final Cell[][] cells;
    private final List<Ship> ships;

    public BoardGraph() {
        cells = new Cell[SIZE][SIZE];
        ships = new ArrayList<>();

        // Make every square on the board.
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                cells[r][c] = new Cell(r, c);
            }
        }

        // Connect each square to the ones above, below, left, and right of it.
        int[] dr = {-1, 1, 0, 0};
        int[] dc = {0,  0, -1, 1};
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                for (int d = 0; d < 4; d++) {
                    int nr = r + dr[d];
                    int nc = c + dc[d];
                    if (nr >= 0 && nr < SIZE && nc >= 0 && nc < SIZE) {
                        cells[r][c].neighbors.add(cells[nr][nc]);
                    }
                }
            }
        }
    }

    // Ship placement

    /**
     * Tries to place a ship starting at the given row and column.
     * If horizontal is true, the ship goes across. Otherwise it goes down.
     *
     * Returns true if the ship fits and does not overlap another one.
     */
    public boolean placeShip(Ship ship, int startRow, int startCol, boolean horizontal) {
        List<Cell> toPlace = new ArrayList<>();

        for (int i = 0; i < ship.size; i++) {
            int r = startRow + (horizontal ? 0 : i);
            int c = startCol + (horizontal ? i : 0);
            if (r < 0 || r >= SIZE || c < 0 || c >= SIZE) return false;
            Cell cell = cells[r][c];
            if (cell.hasShip) return false;
            toPlace.add(cell);
        }

        for (Cell cell : toPlace) {
            cell.hasShip = true;
            cell.ship = ship;
            ship.cells.add(cell);
        }
        ships.add(ship);
        return true;
    }

    // Firing shots

    public enum FireResult { MISS, HIT, SUNK, ALREADY_FIRED }

    /** Fires at one square and reports what happened. */
    public FireResult fire(int row, int col) {
        Cell cell = cells[row][col];
        if (cell.isHit) return FireResult.ALREADY_FIRED;
        cell.isHit = true;
        if (!cell.hasShip) return FireResult.MISS;
        return cell.ship.isSunk() ? FireResult.SUNK : FireResult.HIT;
    }

    // Checking for a win

    public boolean allShipsSunk() {
        return ships.stream().allMatch(Ship::isSunk);
    }

    public List<Ship> getShips() {
        return Collections.unmodifiableList(ships);
    }

    // Find all the hit squares that belong to the same ship.
    public List<Cell> bfsConnectedShipHits(Cell start) {
        List<Cell> result = new ArrayList<>();
        if (!start.isHit || !start.hasShip) return result;

        Queue<Cell> queue = new LinkedList<>();
        Set<Cell> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Cell cur = queue.poll();
            result.add(cur);
            for (Cell nb : cur.neighbors) {
                if (!visited.contains(nb) && nb.isHit && nb.hasShip && nb.ship == start.ship) {
                    visited.add(nb);
                    queue.add(nb);
                }
            }
        }
        return result;
    }

    // Showing the board

    /**
     * Builds the board as text.
     * If showShips is true, your ships are shown.
     * If it is false, hidden ships stay hidden.
     */
    public String display(boolean showShips) {
        StringBuilder sb = new StringBuilder();
        sb.append("   A B C D E F G H I J\n");
        for (int r = 0; r < SIZE; r++) {
            sb.append(String.format("%2d ", r + 1));
            for (int c = 0; c < SIZE; c++) {
                Cell cell = cells[r][c];
                if (cell.isHit) {
                    sb.append(cell.hasShip ? "X " : "O ");
                } else if (showShips && cell.hasShip) {
                    sb.append("S ");
                } else {
                    sb.append(". ");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
