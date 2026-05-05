import java.util.*;

/**
 * This is the computer opponent for single-player games.
 *
 * It starts by guessing, then it tightens its aim once it finds part of a ship.
 */
public class BattleshipBot {

    private final Queue<int[]> targetQueue; // Spots the bot wants to try next.
    private final Set<String> fired;        // Spots the bot has already used.
    private final Random random;

    public BattleshipBot() {
        targetQueue = new LinkedList<>();
        fired = new HashSet<>();
        random = new Random();
    }

    /** Picks the bot's next shot. */
    public int[] getNextShot(BoardGraph opponentBoard) {
        // Skip over any saved targets that are no longer useful.
        while (!targetQueue.isEmpty()) {
            int[] peek = targetQueue.peek();
            if (opponentBoard.cells[peek[0]][peek[1]].isHit) {
                targetQueue.poll();
            } else {
                break;
            }
        }

        if (!targetQueue.isEmpty()) {
            int[] shot = targetQueue.poll();
            fired.add(key(shot[0], shot[1]));
            return shot;
        }

        // If nothing stands out, just guess a new square.
        int row, col;
        do {
            row = random.nextInt(BoardGraph.SIZE);
            col = random.nextInt(BoardGraph.SIZE);
        } while (opponentBoard.cells[row][col].isHit);

        fired.add(key(row, col));
        return new int[]{row, col};
    }

    /** After a hit, add nearby squares so the bot can keep checking around it. */
    public void registerHit(BoardGraph opponentBoard, int row, int col) {
        Cell hitCell = opponentBoard.cells[row][col];
        for (Cell nb : hitCell.neighbors) {
            String k = key(nb.row, nb.col);
            if (!nb.isHit && !fired.contains(k)) {
                targetQueue.add(new int[]{nb.row, nb.col});
                fired.add(k); // Mark it now so it does not get added twice.
            }
        }
    }

    /** After sinking a ship, clear old target ideas and start fresh. */
    public void registerSunk() {
        targetQueue.clear();
    }

    private static String key(int row, int col) {
        return row + "," + col;
    }
}
