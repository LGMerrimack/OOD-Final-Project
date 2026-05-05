import javax.swing.*;
import java.awt.*;
import java.util.Random;

/**
 * A simple windowed version of Battleship for one player.
 *
 * The left board shows your side, and the right board is where you click to attack.
 */
public class BattleshipGUI {

    private static final String[][] FLEET = {
        {"Carrier", "5"},
        {"Battleship", "4"},
        {"Cruiser", "3"},
        {"Submarine", "3"},
        {"Destroyer", "2"}
    };

    private final String playerName;
    private final JFrame frame;
    private final JLabel statusLabel;
    private final JButton rotateButton;

    private final JButton[][] playerButtons;
    private final JButton[][] cpuButtons;

    private BoardGraph playerBoard;
    private BoardGraph cpuBoard;
    private BattleshipBot cpuBot;
    private boolean gameOver;
    private boolean placementMode;
    private boolean placementHorizontal;
    private int placementShipIndex;

    public static void launch() {
        launch("Player");
    }

    public static void launch(String playerName) {
        SwingUtilities.invokeLater(() -> new BattleshipGUI(playerName).show());
    }

    public BattleshipGUI() {
        this("Player");
    }

    public BattleshipGUI(String playerName) {
        this.playerName = (playerName != null && !playerName.isBlank()) ? playerName : "Player";
        frame = new JFrame("Graph Battleship — " + this.playerName);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout(10, 10));

        statusLabel = new JLabel("Click a cell on ENEMY WATERS to fire.", SwingConstants.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        frame.add(statusLabel, BorderLayout.NORTH);

        playerButtons = new JButton[BoardGraph.SIZE][BoardGraph.SIZE];
        cpuButtons = new JButton[BoardGraph.SIZE][BoardGraph.SIZE];

        JPanel boards = new JPanel(new GridLayout(1, 2, 12, 12));
        boards.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        boards.add(buildBoardPanel("YOUR WATERS", playerButtons, false));
        boards.add(buildBoardPanel("ENEMY WATERS", cpuButtons, true));
        frame.add(boards, BorderLayout.CENTER);

        JButton newGameButton = new JButton("New Game");
        newGameButton.addActionListener(e -> startNewGame());

        rotateButton = new JButton("Orientation: Horizontal");
        rotateButton.addActionListener(e -> {
            placementHorizontal = !placementHorizontal;
            updateRotateButtonText();
            if (placementMode) {
                statusLabel.setText("Place " + currentShipName() + " (size " + currentShipSize() + ")");
            }
        });

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottom.add(rotateButton);
        bottom.add(newGameButton);
        frame.add(bottom, BorderLayout.SOUTH);

        startNewGame();
    }

    private JPanel buildBoardPanel(String title, JButton[][] buttons, boolean enemyBoard) {
        JPanel wrapper = new JPanel(new BorderLayout(4, 4));
        JLabel label = new JLabel(title, SwingConstants.CENTER);
        wrapper.add(label, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(BoardGraph.SIZE, BoardGraph.SIZE, 1, 1));
        grid.setBackground(Color.DARK_GRAY);

        for (int r = 0; r < BoardGraph.SIZE; r++) {
            for (int c = 0; c < BoardGraph.SIZE; c++) {
                JButton cellButton = new JButton();
                cellButton.setPreferredSize(new Dimension(34, 34));
                cellButton.setMargin(new Insets(0, 0, 0, 0));
                cellButton.setFocusPainted(false);

                final int row = r;
                final int col = c;
                if (enemyBoard) {
                    cellButton.addActionListener(e -> handlePlayerShot(row, col));
                } else {
                    cellButton.addActionListener(e -> handleShipPlacement(row, col));
                }

                buttons[r][c] = cellButton;
                grid.add(cellButton);
            }
        }

        wrapper.add(grid, BorderLayout.CENTER);
        return wrapper;
    }

    private void show() {
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void startNewGame() {
        playerBoard = new BoardGraph();
        cpuBoard = new BoardGraph();
        cpuBot = new BattleshipBot();
        gameOver = false;
        placementMode = true;
        placementHorizontal = true;
        placementShipIndex = 0;

        placeFleetRandom(cpuBoard);

        updateRotateButtonText();
        statusLabel.setText("Place " + currentShipName() + " (size " + currentShipSize() + ")");
        refreshBoards();
    }

    private void handleShipPlacement(int row, int col) {
        if (!placementMode || gameOver) {
            return;
        }

        Ship ship = new Ship(currentShipName(), currentShipSize());
        boolean placed = playerBoard.placeShip(ship, row, col, placementHorizontal);

        if (!placed) {
            statusLabel.setText("Can't place there. Try another cell or rotate.");
            return;
        }

        placementShipIndex++;
        if (placementShipIndex >= FLEET.length) {
            placementMode = false;
            statusLabel.setText("Fleet ready. Fire on ENEMY WATERS.");
        } else {
            statusLabel.setText("Placed! Now place " + currentShipName() + " (size " + currentShipSize() + ")");
        }

        updateRotateButtonText();
        refreshBoards();
    }

    private void handlePlayerShot(int row, int col) {
        if (gameOver || placementMode) {
            return;
        }

        BoardGraph.FireResult result = cpuBoard.fire(row, col);
        if (result == BoardGraph.FireResult.ALREADY_FIRED) {
            statusLabel.setText("You already fired at " + toCoord(row, col) + ". Pick another cell.");
            return;
        }

        if (result == BoardGraph.FireResult.MISS) {
            statusLabel.setText("You missed at " + toCoord(row, col) + ". CPU turn...");
        } else if (result == BoardGraph.FireResult.HIT) {
            statusLabel.setText("Hit at " + toCoord(row, col) + "!");
        } else {
            Ship sunk = cpuBoard.cells[row][col].ship;
            statusLabel.setText("You sunk CPU's " + sunk.name + "!");
        }

        refreshBoards();

        if (cpuBoard.allShipsSunk()) {
            gameOver = true;
            statusLabel.setText(playerName + " wins! All enemy ships are sunk.");
            refreshBoards();
            return;
        }

        runCpuTurn();
    }

    private void runCpuTurn() {
        int[] shot = cpuBot.getNextShot(playerBoard);
        BoardGraph.FireResult result = playerBoard.fire(shot[0], shot[1]);

        if (result == BoardGraph.FireResult.HIT) {
            cpuBot.registerHit(playerBoard, shot[0], shot[1]);
            statusLabel.setText("CPU hits your ship at " + toCoord(shot[0], shot[1]) + ". Your turn.");
        } else if (result == BoardGraph.FireResult.SUNK) {
            cpuBot.registerSunk();
            Ship sunk = playerBoard.cells[shot[0]][shot[1]].ship;
            statusLabel.setText("CPU sunk your " + sunk.name + " at " + toCoord(shot[0], shot[1]) + ". Your turn.");
        } else {
            statusLabel.setText("CPU missed at " + toCoord(shot[0], shot[1]) + ". Your turn.");
        }

        refreshBoards();

        if (playerBoard.allShipsSunk()) {
            gameOver = true;
            statusLabel.setText("CPU wins! " + playerName + "'s fleet was sunk.");
            refreshBoards();
        }
    }

    private void refreshBoards() {
        for (int r = 0; r < BoardGraph.SIZE; r++) {
            for (int c = 0; c < BoardGraph.SIZE; c++) {
                updatePlayerCellButton(r, c);
                updateCpuCellButton(r, c);
            }
        }
    }

    private void updatePlayerCellButton(int row, int col) {
        Cell cell = playerBoard.cells[row][col];
        JButton button = playerButtons[row][col];

        if (cell.isHit && cell.hasShip) {
            styleCell(button, "X", new Color(188, 44, 44), Color.WHITE);
        } else if (cell.isHit) {
            styleCell(button, "o", new Color(179, 205, 224), Color.BLACK);
        } else if (cell.hasShip) {
            styleCell(button, "S", new Color(108, 139, 160), Color.WHITE);
        } else {
            styleCell(button, "~", new Color(210, 226, 239), Color.BLACK);
        }

        button.setEnabled(placementMode && !cell.hasShip);
    }

    private void updateCpuCellButton(int row, int col) {
        Cell cell = cpuBoard.cells[row][col];
        JButton button = cpuButtons[row][col];

        if (cell.isHit && cell.hasShip) {
            styleCell(button, "X", new Color(188, 44, 44), Color.WHITE);
        } else if (cell.isHit) {
            styleCell(button, "o", new Color(179, 205, 224), Color.BLACK);
        } else {
            styleCell(button, "~", new Color(217, 235, 247), Color.BLACK);
        }

        button.setEnabled(!gameOver && !placementMode && !cell.isHit);
    }

    private String currentShipName() {
        return FLEET[placementShipIndex][0];
    }

    private int currentShipSize() {
        return Integer.parseInt(FLEET[placementShipIndex][1]);
    }

    private void updateRotateButtonText() {
        rotateButton.setText("Orientation: " + (placementHorizontal ? "Horizontal" : "Vertical"));
        rotateButton.setEnabled(placementMode);
    }

    private void styleCell(JButton button, String text, Color bg, Color fg) {
        button.setText(text);
        button.setBackground(bg);
        button.setForeground(fg);
        button.setOpaque(true);
        button.setBorderPainted(false);
    }

    private static void placeFleetRandom(BoardGraph board) {
        Random rand = new Random();
        for (String[] cfg : FLEET) {
            String name = cfg[0];
            int size = Integer.parseInt(cfg[1]);
            boolean placed = false;

            while (!placed) {
                int row = rand.nextInt(BoardGraph.SIZE);
                int col = rand.nextInt(BoardGraph.SIZE);
                boolean horizontal = rand.nextBoolean();
                placed = board.placeShip(new Ship(name, size), row, col, horizontal);
            }
        }
    }

    private static String toCoord(int row, int col) {
        return String.valueOf((char) ('A' + col)) + (row + 1);
    }
}
