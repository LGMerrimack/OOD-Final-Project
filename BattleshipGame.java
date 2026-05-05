import java.util.*;

/**
 * This class runs the game.
 *
 * It handles the menu, sets up the boards, and manages the turn-by-turn flow
 * for both single-player and same-computer multiplayer.
 */
public class BattleshipGame {

    private static final Scanner scanner = new Scanner(System.in);

    /** Standard Battleship fleet: each entry is a ship name and its length. */
    private static final String[][] FLEET = {
        {"Carrier",    "5"},
        {"Battleship", "4"},
        {"Cruiser",    "3"},
        {"Submarine",  "3"},
        {"Destroyer",  "2"}
    };

    // Main menu and game start

    public static void main(String[] args) {
        try {
            printBanner();

            System.out.println("Game Mode:");
            System.out.println("  1. Single Player (vs AI)");
            System.out.println("  2. Multiplayer   (2 players, same machine)");
            System.out.println("  3. Basic GUI     (single player)");
            System.out.print("Choose (1/2/3): ");

            int mode = readInt(1, 3);
            if (mode == 1) {
                playSinglePlayer();
            } else if (mode == 2) {
                playMultiplayer();
            } else {
                BattleshipGUI.launch();
            }
        } catch (NoSuchElementException e) {
            // If there is no console input available, just open the GUI instead.
            BattleshipGUI.launch();
        }
    }

    
    // Single-player mode
    private static void playSinglePlayer() {
        System.out.println("\n--- SINGLE PLAYER ---");
        System.out.print("Your name: ");
        String name = readName();

        BoardGraph playerBoard = new BoardGraph();
        BoardGraph cpuBoard = new BoardGraph();
        BattleshipBot cpu = new BattleshipBot();

        System.out.println("\n[" + name + "] Set up your fleet:");
        setupFleet(playerBoard, name);

        System.out.println("\nCPU is placing its fleet...");
        placeFleetRandom(cpuBoard);
        System.out.println("Done.\n");

        boolean playerTurn = true;

        while (true) {
            if (playerTurn) {
                System.out.println("\n========== YOUR TURN ==========");
                printSideBySide("YOUR BOARD", playerBoard, true,
                                "CPU BOARD",  cpuBoard,     false);

                int[] shot  = promptShot(cpuBoard, name);
                BoardGraph.FireResult result = cpuBoard.fire(shot[0], shot[1]);
                String coord = toCoord(shot[0], shot[1]);

                printFireResult(result, coord, cpuBoard, shot, "CPU's");

                if (cpuBoard.allShipsSunk()) {
                    System.out.println("\n*** YOU WIN! All opponent's ships have been sunk! ***");
                    break;
                }
            } else {
                // Now the CPU takes its turn.
                int[] shot  = cpu.getNextShot(playerBoard);
                BoardGraph.FireResult result = playerBoard.fire(shot[0], shot[1]);
                String coord = toCoord(shot[0], shot[1]);

                System.out.println("\n--- CPU fires at " + coord + " ---");

                if (result == BoardGraph.FireResult.HIT) {
                    System.out.println("CPU HIT your ship at " + coord + "!");
                    cpu.registerHit(playerBoard, shot[0], shot[1]);
                } else if (result == BoardGraph.FireResult.SUNK) {
                    Ship sunk = playerBoard.cells[shot[0]][shot[1]].ship;
                    System.out.println("CPU SUNK your " + sunk.name + "!");
                    cpu.registerSunk();
                } else {
                    System.out.println("CPU missed.");
                }

                if (playerBoard.allShipsSunk()) {
                    System.out.println("\n*** CPU WINS! All your ships have been sunk! ***");
                    break;
                }
            }

            playerTurn = !playerTurn;
        }

        System.out.println("\n--- FINAL BOARDS ---");
        printSideBySide("YOUR BOARD", playerBoard, true,
                        "CPU BOARD",  cpuBoard,     true);
    }

    // Two-player mode on the same computer

    private static void playMultiplayer() {
        System.out.println("\n--- MULTIPLAYER ---");
        System.out.print("Player 1 name: ");
        String p1 = readName();
        System.out.print("Player 2 name: ");
        String p2 = readName();

        BoardGraph b1 = new BoardGraph();
        BoardGraph b2 = new BoardGraph();

        System.out.println("\n[" + p1 + "] Set up your fleet:");
        setupFleet(b1, p1);
        waitAndClear(p2);

        System.out.println("\n[" + p2 + "] Set up your fleet:");
        setupFleet(b2, p2);
        waitAndClear(p1);

        boolean p1Turn = true;

        while (true) {
            String curName  = p1Turn ? p1 : p2;
            String oppName  = p1Turn ? p2 : p1;
            BoardGraph cur  = p1Turn ? b1 : b2;
            BoardGraph opp  = p1Turn ? b2 : b1;

            System.out.println("\n========== " + curName.toUpperCase() + "'S TURN ==========");
            printSideBySide("YOUR BOARD", cur, true,
                            oppName.toUpperCase() + "'S BOARD", opp, false);

            int[] shot = promptShot(opp, curName);
            BoardGraph.FireResult result = opp.fire(shot[0], shot[1]);
            String coord = toCoord(shot[0], shot[1]);

            printFireResult(result, coord, opp, shot, oppName + "'s");

            if (opp.allShipsSunk()) {
                System.out.println("\n*** " + curName.toUpperCase() + " WINS! All of "
                                   + oppName + "'s ships are sunk! ***");
                break;
            }

            waitAndClear(oppName);
            p1Turn = !p1Turn;
        }
    }

    // Ship setup

    private static void setupFleet(BoardGraph board, String playerName) {
        System.out.println("Place ships manually or randomly?");
        System.out.println("  1. Manual");
        System.out.println("  2. Random");
        System.out.print("Choose (1/2): ");

        if (readInt(1, 2) == 2) {
            placeFleetRandom(board);
            System.out.println("Fleet placed randomly:");
            System.out.print(board.display(true));
        } else {
            placeFleetManual(board);
        }
    }

    private static void placeFleetManual(BoardGraph board) {
        for (String[] cfg : FLEET) {
            String name = cfg[0];
            int    size = Integer.parseInt(cfg[1]);

            while (true) {
                System.out.println("\n" + board.display(true));
                System.out.println("Placing: " + name + " (length " + size + ")");
                System.out.print("  Start coordinate (e.g. A1): ");
                int[] coord = readCoord();
                if (coord == null) { System.out.println("  Invalid. Try again."); continue; }

                System.out.print("  Direction — H(orizontal) or V(ertical): ");
                String dir = scanner.nextLine().trim().toUpperCase();
                if (!dir.equals("H") && !dir.equals("V")) {
                    System.out.println("  Enter H or V.");
                    continue;
                }

                Ship ship = new Ship(name, size);
                if (board.placeShip(ship, coord[0], coord[1], dir.equals("H"))) {
                    System.out.println("  " + name + " placed!");
                    break;
                } else {
                    System.out.println("  Can't place there (out of bounds or overlapping). Try again.");
                }
            }
        }
        System.out.println("\nAll ships placed:");
        System.out.print(board.display(true));
    }

    private static void placeFleetRandom(BoardGraph board) {
        Random rand = new Random();
        for (String[] cfg : FLEET) {
            String name = cfg[0];
            int    size = Integer.parseInt(cfg[1]);
            boolean placed = false;
            while (!placed) {
                int  row  = rand.nextInt(BoardGraph.SIZE);
                int  col  = rand.nextInt(BoardGraph.SIZE);
                boolean h = rand.nextBoolean();
                placed = board.placeShip(new Ship(name, size), row, col, h);
            }
        }
    }

    // Asking for shots

    private static int[] promptShot(BoardGraph opponentBoard, String playerName) {
        while (true) {
            System.out.print(playerName + ", enter target (e.g. B3): ");
            int[] coord = readCoord();
            if (coord == null) {
                System.out.println("  Invalid format. Use column letter + row number (e.g. B3).");
                continue;
            }
            if (opponentBoard.cells[coord[0]][coord[1]].isHit) {
                System.out.println("  Already fired there! Choose another cell.");
                continue;
            }
            return coord;
        }
    }

    // Display helpers

    private static void printFireResult(BoardGraph.FireResult result, String coord,
                                        BoardGraph board, int[] shot, String ownerLabel) {
        switch (result) {
            case HIT:
                System.out.println(">>> HIT at " + coord + "!");
                break;
            case SUNK:
                Ship sunk = board.cells[shot[0]][shot[1]].ship;
                System.out.println(">>> HIT at " + coord + "! " + ownerLabel + " " + sunk.name + " is SUNK!");
                break;
            case MISS:
                System.out.println(">>> Miss at " + coord + ".");
                break;
            default:
                break;
        }
    }

    /** Prints two boards next to each other so they are easier to compare. */
    private static void printSideBySide(String label1, BoardGraph b1, boolean show1,
                                         String label2, BoardGraph b2, boolean show2) {
        String[] left  = b1.display(show1).split("\n");
        String[] right = b2.display(show2).split("\n");

        int width = 24; // Give each board enough room when printing side by side.
        System.out.printf("%-" + width + "s    %s%n", label1, label2);
        for (int i = 0; i < left.length; i++) {
            String l = (i < left.length)  ? left[i]  : "";
            String r = (i < right.length) ? right[i] : "";
            System.out.printf("%-" + width + "s    %s%n", l, r);
        }
    }

    private static void printBanner() {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║         GRAPH  BATTLESHIP            ║");
        System.out.println("║  Board = graph  |  CPU uses BFS      ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println();
        System.out.println("Legend:  S = your ship   X = hit   O = miss   . = unknown");
        System.out.println();
    }

    // Input helpers

    /**
     * Reads something like "B3" and turns it into a row and column.
     * The letter is the column and the number is the row.
     */
    private static int[] readCoord() {
        String input = scanner.nextLine().trim().toUpperCase();
        if (input.length() < 2) return null;
        char colChar = input.charAt(0);
        if (colChar < 'A' || colChar > 'J') return null;
        int col = colChar - 'A';
        try {
            int row = Integer.parseInt(input.substring(1)) - 1;
            if (row < 0 || row >= BoardGraph.SIZE) return null;
            return new int[]{row, col};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Keeps asking until the player enters a number in range. */
    private static int readInt(int min, int max) {
        while (true) {
            try {
                int v = Integer.parseInt(scanner.nextLine().trim());
                if (v >= min && v <= max) return v;
            } catch (NumberFormatException ignored) {}
            System.out.print("  Please enter a number between " + min + " and " + max + ": ");
        }
    }

    /** Reads a player name and uses "Player" if nothing was entered. */
    private static String readName() {
        String n = scanner.nextLine().trim();
        return n.isEmpty() ? "Player" : n;
    }

    /** Gives the next player a chance to take over before play continues. */
    private static void waitAndClear(String nextPlayer) {
        System.out.println("\nPass the device to " + nextPlayer + ".");
        System.out.print("Press Enter when ready...");
        scanner.nextLine();
        // Push the last board mostly off-screen so the next player does not see it.
        for (int i = 0; i < 40; i++) System.out.println();
    }

    private static String toCoord(int row, int col) {
        return String.valueOf((char)('A' + col)) + (row + 1);
    }
}
