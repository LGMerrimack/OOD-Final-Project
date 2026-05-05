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
    private JFrame frame;
    private final JLabel statusLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel enemyFleetLabel = new JLabel("Enemy Fleet: \u2014", SwingConstants.CENTER);
    private final JButton rotateButton = new JButton("Orientation: Horizontal");

    private final JButton[][] playerButtons = new JButton[BoardGraph.SIZE][BoardGraph.SIZE];
    private final JButton[][] cpuButtons = new JButton[BoardGraph.SIZE][BoardGraph.SIZE];

    private BoardGraph playerBoard;
    private BoardGraph cpuBoard;
    private BattleshipBot cpuBot;
    private boolean gameOver;
    private boolean placementMode;
    private boolean placementHorizontal;
    private int placementShipIndex;

    // Online multiplayer fields
    private final boolean onlineMode;
    private final boolean isHost;           // host fires first
    private final String opponentName;
    private final ChatClient chatClient;
    private boolean myTurn;
    private boolean opponentReady;
    private boolean localReady;
    private boolean waitingForResult;       // fired a shot, awaiting RESULT back
    private final java.util.Set<String> onlineSunkEnemyShips = new java.util.HashSet<>();

    // ──────────────────────────────────────────────────────────────────────────
    // Launch helpers (solo / choosing mode)
    // ──────────────────────────────────────────────────────────────────────────

    public static void launch() {
        launchWithStartChoice("Player", null);
    }

    public static void launch(String playerName) {
        launchWithStartChoice(playerName, null);
    }

    /**
     * Shows the start-mode dialog.
     * If the user picks "Invite Online Player" the inviteAction runs and the solo
     * game is NOT opened — the game will be opened later once the invite is accepted.
     */
    public static void launchWithStartChoice(String playerName, Runnable inviteAction) {
        SwingUtilities.invokeLater(() -> {
            Object[] options = {"Invite Online Player", "Play By Myself", "Cancel"};
            int choice = JOptionPane.showOptionDialog(
                    null,
                    "How would you like to start Battleship?",
                    "Start Battleship",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[1]);

            if (choice == JOptionPane.CLOSED_OPTION || choice == 2) {
                return;
            }

            if (choice == 0) {
                // Online invite — delegate entirely to the invite action; don't open game here.
                if (inviteAction != null) {
                    inviteAction.run();
                } else {
                    JOptionPane.showMessageDialog(
                            null,
                            "Online invites are available from the chat window.",
                            "Invite Online Player",
                            JOptionPane.INFORMATION_MESSAGE);
                }
                return; // game opens when opponent accepts
            }

            // "Play By Myself"
            new BattleshipGUI(playerName).show();
        });
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Constructors
    // ──────────────────────────────────────────────────────────────────────────

    public BattleshipGUI() {
        this("Player");
    }

    /** Solo (vs CPU) constructor. */
    public BattleshipGUI(String playerName) {
        this.playerName = (playerName != null && !playerName.isBlank()) ? playerName : "Player";
        this.onlineMode = false;
        this.isHost = false;
        this.opponentName = "CPU";
        this.chatClient = null;
        this.myTurn = true;
        this.opponentReady = true;
        this.localReady = false;
        this.waitingForResult = false;

        frame = buildFrame();
        startNewGame();
    }

    /**
     * Online multiplayer constructor.
     *
     * @param playerName   this player's username
     * @param opponentName the opponent's username
     * @param isHost       true = we fire first (inviter); false = opponent fires first (invitee)
     * @param chatClient   used to relay moves through the chat server
     */
    public BattleshipGUI(String playerName, String opponentName, boolean isHost, ChatClient chatClient) {
        this.playerName = (playerName != null && !playerName.isBlank()) ? playerName : "Player";
        this.opponentName = (opponentName != null && !opponentName.isBlank()) ? opponentName : "Opponent";
        this.onlineMode = true;
        this.isHost = isHost;
        this.chatClient = chatClient;
        this.myTurn = false;          // set properly once both players are ready
        this.opponentReady = false;
        this.localReady = false;
        this.waitingForResult = false;

        frame = buildFrame();
        startOnlineGame();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Frame / board construction
    // ──────────────────────────────────────────────────────────────────────────

    private JFrame buildFrame() {
        String title = onlineMode
                ? "Graph Battleship — " + playerName + " vs " + opponentName
                : "Graph Battleship — " + playerName;

        JFrame f = new JFrame(title);
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        f.setLayout(new BorderLayout(10, 10));

        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 8, 2, 8));
        enemyFleetLabel.setFont(enemyFleetLabel.getFont().deriveFont(12f));
        enemyFleetLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 6, 8));

        rotateButton.addActionListener(e -> {
            placementHorizontal = !placementHorizontal;
            updateRotateButtonText();
            if (placementMode) {
                statusLabel.setText("Place " + currentShipName() + " (size " + currentShipSize() + ")");
            }
        });

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(statusLabel, BorderLayout.NORTH);
        northPanel.add(enemyFleetLabel, BorderLayout.SOUTH);
        f.add(northPanel, BorderLayout.NORTH);

        JPanel boards = new JPanel(new GridLayout(1, 2, 12, 12));
        boards.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        boards.add(buildBoardPanel("YOUR WATERS", playerButtons, false));
        boards.add(buildBoardPanel(onlineMode ? opponentName.toUpperCase() + "'S WATERS" : "ENEMY WATERS",
                cpuButtons, true));
        f.add(boards, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottom.add(rotateButton);
        if (!onlineMode) {
            JButton newGameButton = new JButton("New Game");
            newGameButton.addActionListener(e -> startNewGame());
            bottom.add(newGameButton);
        }
        f.add(bottom, BorderLayout.SOUTH);

        return f;
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

    public void show() {
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
        updateEnemyFleetStatus();

        updateRotateButtonText();
        statusLabel.setText("Place " + currentShipName() + " (size " + currentShipSize() + ")");
        refreshBoards();
    }

    /** Initialises the online game state — both players place their own ships. */
    private void startOnlineGame() {
        playerBoard = new BoardGraph();
        cpuBoard = new BoardGraph();   // represents the opponent's board (display only)
        cpuBot = null;
        gameOver = false;
        placementMode = true;
        placementHorizontal = true;
        placementShipIndex = 0;
        localReady = false;
        opponentReady = false;
        myTurn = false;
        waitingForResult = false;

        updateRotateButtonText();
        statusLabel.setText("Place " + currentShipName() + " (size " + currentShipSize() + ")");
        refreshBoards();
    }

    /**
     * Called by MainChatWindow when a GAME_MOVE arrives from the opponent.
     * Protocol payloads:
     *   READY                           — opponent finished placing ships
     *   SHOT:row:col                    — opponent fires at our board
     *   RESULT:row:col:MISS             — result of our shot (miss)
     *   RESULT:row:col:HIT              — result of our shot (hit)
     *   RESULT:row:col:SUNK:shipName    — result of our shot (sunk)
     *   GAMEOVER                        — opponent declares we won (all their ships sunk)
     */
    public void receiveMove(String payload) {
        if (gameOver) return;

        if (payload.equals("READY")) {
            opponentReady = true;
            if (localReady) startOnlineFiring();
            return;
        }

        if (payload.startsWith("SHOT:")) {
            // opponent fired at our board
            String[] parts = payload.split(":");
            int row = Integer.parseInt(parts[1]);
            int col = Integer.parseInt(parts[2]);
            handleOpponentShot(row, col);
            return;
        }

        if (payload.startsWith("RESULT:")) {
            // result of our shot on opponent's board
            String[] parts = payload.split(":", 5);
            int row = Integer.parseInt(parts[1]);
            int col = Integer.parseInt(parts[2]);
            String type = parts[3];
            String shipName = parts.length > 4 ? parts[4] : "";
            handleShotResult(row, col, type, shipName);
            return;
        }

        if (payload.equals("GAMEOVER")) {
            gameOver = true;
            statusLabel.setText(playerName + " wins! All of " + opponentName + "'s ships are sunk.");
            refreshBoards();
            JOptionPane.showMessageDialog(frame,
                    "All of " + opponentName + "'s ships have been destroyed!\n" + playerName + " wins!",
                    "Victory!", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** Both players are ready — the host fires first. */
    private void startOnlineFiring() {
        myTurn = isHost;
        if (myTurn) {
            statusLabel.setText("Both ready! Your turn — fire on " + opponentName.toUpperCase() + "'S WATERS.");
        } else {
            statusLabel.setText("Both ready! Waiting for " + opponentName + " to fire...");
        }
        refreshBoards();
    }

    /**
     * Opponent fired at our board — process it and send the result back.
     * Also switches turn so we can fire next.
     */
    private void handleOpponentShot(int row, int col) {
        BoardGraph.FireResult result = playerBoard.fire(row, col);
        refreshBoards();

        String payload;
        if (result == BoardGraph.FireResult.SUNK) {
            Ship sunk = playerBoard.cells[row][col].ship;
            payload = "RESULT:" + row + ":" + col + ":SUNK:" + sunk.name;
            long remaining = playerBoard.getShips().stream().filter(s -> !s.isSunk()).count();
            statusLabel.setText(opponentName + " sunk your " + sunk.name + "! ("
                    + remaining + " of your ships remaining). Your turn.");
        } else if (result == BoardGraph.FireResult.HIT) {
            payload = "RESULT:" + row + ":" + col + ":HIT";
            statusLabel.setText(opponentName + " hit your ship at " + toCoord(row, col) + ". Your turn.");
        } else {
            payload = "RESULT:" + row + ":" + col + ":MISS";
            statusLabel.setText(opponentName + " missed at " + toCoord(row, col) + ". Your turn.");
        }

        chatClient.sendGameMove(opponentName, payload);

        if (playerBoard.allShipsSunk()) {
            gameOver = true;
            statusLabel.setText(opponentName + " wins! Your fleet was sunk.");
            refreshBoards();
            // Tell opponent they won
            chatClient.sendGameMove(opponentName, "GAMEOVER");
            JOptionPane.showMessageDialog(frame,
                    "All your ships have been destroyed!\n" + opponentName + " wins!",
                    "Defeat!", JOptionPane.ERROR_MESSAGE);
            return;
        }

        myTurn = true;
        waitingForResult = false;
        refreshBoards();
    }

    /**
     * We received the result of our shot from the opponent.
     * Update the display board and check for win.
     */
    private void handleShotResult(int row, int col, String type, String shipName) {
        waitingForResult = false;

        Cell cell = cpuBoard.cells[row][col];
        // cell.isHit was already optimistically set when we fired

        if (type.equals("MISS")) {
            cell.hasShip = false;
            statusLabel.setText("You missed at " + toCoord(row, col) + ". " + opponentName + "'s turn.");
            myTurn = false;
        } else if (type.equals("HIT")) {
            cell.hasShip = true;
            statusLabel.setText("Hit at " + toCoord(row, col) + "! " + opponentName + "'s turn.");
            myTurn = false;
        } else if (type.equals("SUNK")) {
            cell.hasShip = true;
            onlineSunkEnemyShips.add(shipName);
            updateEnemyFleetStatusOnline();
            if (onlineSunkEnemyShips.size() >= FLEET.length) {
                gameOver = true;
                statusLabel.setText(playerName + " wins! All of " + opponentName + "'s ships are sunk.");
                refreshBoards();
                JOptionPane.showMessageDialog(frame,
                        "All of " + opponentName + "'s ships have been destroyed!\n" + playerName + " wins!",
                        "Victory!", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            statusLabel.setText("You sunk " + opponentName + "'s " + shipName + "! "
                    + opponentName + "'s turn.");
            myTurn = false;
        }

        refreshBoards();
    }

    private void updateEnemyFleetStatusOnline() {
        StringBuilder sb = new StringBuilder("<html><center><b>" + opponentName + "'s Fleet:</b>&nbsp;&nbsp;");
        for (String[] cfg : FLEET) {
            String n = cfg[0];
            if (onlineSunkEnemyShips.contains(n)) {
                sb.append("<font color='#cc3333'><strike>").append(n).append("</strike></font>");
            } else {
                sb.append("<font color='#2a7a2a'>").append(n).append("</font>");
            }
            sb.append("&nbsp;&nbsp;");
        }
        sb.append("</center></html>");
        enemyFleetLabel.setText(sb.toString());
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
            if (onlineMode) {
                localReady = true;
                chatClient.sendGameMove(opponentName, "READY");
                if (opponentReady) {
                    startOnlineFiring();
                } else {
                    statusLabel.setText("Fleet ready! Waiting for " + opponentName + " to finish placing ships...");
                }
            } else {
                statusLabel.setText("Fleet ready. Fire on ENEMY WATERS.");
            }
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

        if (onlineMode) {
            if (!myTurn) {
                statusLabel.setText("Wait for " + opponentName + "'s turn first.");
                return;
            }
            if (waitingForResult) {
                statusLabel.setText("Waiting for result of previous shot...");
                return;
            }
            Cell cell = cpuBoard.cells[row][col];
            if (cell.isHit) {
                statusLabel.setText("Already fired at " + toCoord(row, col) + ". Pick another cell.");
                return;
            }
            // Mark cell pending and send shot to opponent
            cell.isHit = true;   // optimistic mark; opponent's RESULT will set hasShip
            waitingForResult = true;
            myTurn = false;
            statusLabel.setText("Fired at " + toCoord(row, col) + "! Waiting for result...");
            refreshBoards();
            chatClient.sendGameMove(opponentName, "SHOT:" + row + ":" + col);
            return;
        }

        BoardGraph.FireResult result = cpuBoard.fire(row, col);
        if (result == BoardGraph.FireResult.ALREADY_FIRED) {
            statusLabel.setText("You already fired at " + toCoord(row, col) + ". Pick another cell.");
            return;
        }

        Ship justSunk = null;
        if (result == BoardGraph.FireResult.MISS) {
            statusLabel.setText("You missed at " + toCoord(row, col) + ". CPU turn...");
        } else if (result == BoardGraph.FireResult.HIT) {
            statusLabel.setText("Hit at " + toCoord(row, col) + "!");
        } else {
            justSunk = cpuBoard.cells[row][col].ship;
            long remaining = cpuBoard.getShips().stream().filter(s -> !s.isSunk()).count();
            statusLabel.setText("You sunk CPU's " + justSunk.name + "! ("
                    + remaining + " enemy ship" + (remaining == 1 ? "" : "s") + " remaining)");
            updateEnemyFleetStatus();
        }

        refreshBoards();

        if (cpuBoard.allShipsSunk()) {
            gameOver = true;
            statusLabel.setText(playerName + " wins! All enemy ships are sunk.");
            refreshBoards();
            JOptionPane.showMessageDialog(frame,
                    "You sunk the enemy " + justSunk.name + "!\nAll enemy ships have been destroyed!\n"
                    + playerName + " wins!",
                    "Victory!", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (justSunk != null) {
            long remaining = cpuBoard.getShips().stream().filter(s -> !s.isSunk()).count();
            JOptionPane.showMessageDialog(frame,
                    "You sunk the enemy " + justSunk.name + "!\n"
                    + remaining + " enemy ship" + (remaining == 1 ? "" : "s") + " still remaining.",
                    "Enemy Ship Sunk!", JOptionPane.WARNING_MESSAGE);
        }

        runCpuTurn();
    }

    private void runCpuTurn() {
        int[] shot = cpuBot.getNextShot(playerBoard);
        BoardGraph.FireResult result = playerBoard.fire(shot[0], shot[1]);

        Ship justSunk = null;
        if (result == BoardGraph.FireResult.HIT) {
            cpuBot.registerHit(playerBoard, shot[0], shot[1]);
            statusLabel.setText("CPU hits your ship at " + toCoord(shot[0], shot[1]) + ". Your turn.");
        } else if (result == BoardGraph.FireResult.SUNK) {
            cpuBot.registerSunk();
            justSunk = playerBoard.cells[shot[0]][shot[1]].ship;
            long remaining = playerBoard.getShips().stream().filter(s -> !s.isSunk()).count();
            statusLabel.setText("CPU sunk your " + justSunk.name + " at " + toCoord(shot[0], shot[1])
                    + "! (" + remaining + " of your ships remaining)");
        } else {
            statusLabel.setText("CPU missed at " + toCoord(shot[0], shot[1]) + ". Your turn.");
        }

        refreshBoards();

        if (playerBoard.allShipsSunk()) {
            gameOver = true;
            statusLabel.setText("CPU wins! " + playerName + "'s fleet was sunk.");
            refreshBoards();
            JOptionPane.showMessageDialog(frame,
                    "CPU sunk your " + justSunk.name + "!\nAll your ships have been destroyed!\nCPU wins!",
                    "Defeat!", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (justSunk != null) {
            long remaining = playerBoard.getShips().stream().filter(s -> !s.isSunk()).count();
            JOptionPane.showMessageDialog(frame,
                    "CPU sunk your " + justSunk.name + "!\n"
                    + remaining + " of your ship" + (remaining == 1 ? "" : "s") + " remaining.",
                    "Your Ship Sunk!", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateEnemyFleetStatus() {
        if (cpuBoard == null) {
            enemyFleetLabel.setText("Enemy Fleet: —");
            return;
        }
        StringBuilder sb = new StringBuilder("<html><center><b>Enemy Fleet:</b>&nbsp;&nbsp;");
        for (Ship ship : cpuBoard.getShips()) {
            if (ship.isSunk()) {
                sb.append("<font color='#cc3333'><strike>").append(ship.name).append("</strike></font>");
            } else {
                sb.append("<font color='#2a7a2a'>").append(ship.name).append("</font>");
            }
            sb.append("&nbsp;&nbsp;");
        }
        sb.append("</center></html>");
        enemyFleetLabel.setText(sb.toString());
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

        boolean canFire = !gameOver && !placementMode && !cell.isHit;
        if (onlineMode) {
            canFire = canFire && myTurn && !waitingForResult;
        }
        button.setEnabled(canFire);
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
