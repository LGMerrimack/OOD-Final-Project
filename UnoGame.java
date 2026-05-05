import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.geom.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * UnoGame.java
 *
 * Usage:
 * Host: java UnoGame --host <port> <playerName>
 * Join: java UnoGame --join <ip> <port> <playerName>
 * Solo (debug): java UnoGame --solo
 */
public class UnoGame extends JFrame {
    // constants
    public enum CardColor {
        RED, GREEN, BLUE, YELLOW, WILD
    }

    public enum CardType {
        ZERO, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE,
        DRAW_TWO, REVERSE, WILD, WILD_DRAW_FOUR
    }

    // base tint colors for blend
    private static final float[][] TINT_HSB = {
            { 0.00f, 1.0f, 0.9f }, // RED
            { 0.38f, 1.0f, 0.8f }, // GREEN
            { 0.61f, 1.0f, 0.9f }, // BLUE
            { 0.14f, 1.0f, 1.0f }, // YELLOW
    };

    // rainbow animation state
    private float rainbowHue = 0f;
    private javax.swing.Timer rainbowTimer;

    private static final Color[] SOLID_COLORS = {
            new Color(200, 50, 50),
            new Color(50, 160, 60),
            new Color(50, 100, 210),
            new Color(200, 170, 30),
            new Color(40, 40, 40)
    };

    private static final int CARD_W = 130;
    private static final int CARD_H = 173;
    private static final int MAX_PLAYERS = 4;

    // card logic
    static class Card implements Serializable {
        CardColor color;
        CardType type;

        Card(CardColor color, CardType type) {
            this.color = color;
            this.type = type;
        }

        boolean isWild() {
            return type == CardType.WILD || type == CardType.WILD_DRAW_FOUR;
        }

        boolean isAction() {
            return type == CardType.DRAW_TWO || type == CardType.REVERSE || isWild();
        }

        int drawPenalty() {
            if (type == CardType.DRAW_TWO)
                return 2;
            if (type == CardType.WILD_DRAW_FOUR)
                return 4;
            return 0;
        }

        boolean canPlayOn(Card top, CardColor activeWildColor) {
            if (isWild())
                return true;
            CardColor effectiveTop = top.isWild() ? activeWildColor : top.color;
            if (effectiveTop == null)
                return true;
            return color == effectiveTop || type == top.type;
        }

        String imageName() {
            return switch (type) {
                case ZERO -> "Card_0";
                case ONE -> "Card_1";
                case TWO -> "Card_2";
                case THREE -> "Card_3";
                case FOUR -> "Card_4";
                case FIVE -> "Card_5";
                case SIX -> "Card_6";
                case SEVEN -> "Card_7";
                case EIGHT -> "Card_8";
                case NINE -> "Card_9";
                case DRAW_TWO -> "Card_Draw2";
                case REVERSE -> "Card_Reverse";
                case WILD -> "Card_Wild";
                case WILD_DRAW_FOUR -> "Card_Draw4";
            };
        }

        @Override
        public String toString() {
            return color + "_" + type;
        }

        @Override
        public boolean equals(Object o) {
            return this == o;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    // deck
    static class Deck {
        private final Deque<Card> draw = new ArrayDeque<>();
        private final Deque<Card> discard = new ArrayDeque<>();

        Deck() {
            buildAndShuffle();
        }

        private void buildAndShuffle() {
            List<Card> cards = new ArrayList<>();
            for (CardColor c : new CardColor[] { CardColor.RED, CardColor.GREEN, CardColor.BLUE, CardColor.YELLOW }) {
                cards.add(new Card(c, CardType.ZERO));
                for (CardType t : new CardType[] { CardType.ONE, CardType.TWO, CardType.THREE, CardType.FOUR,
                        CardType.FIVE, CardType.SIX, CardType.SEVEN, CardType.EIGHT, CardType.NINE,
                        CardType.DRAW_TWO, CardType.REVERSE }) {
                    cards.add(new Card(c, t));
                    cards.add(new Card(c, t));
                }
            }
            for (int i = 0; i < 4; i++) {
                cards.add(new Card(CardColor.WILD, CardType.WILD));
                cards.add(new Card(CardColor.WILD, CardType.WILD_DRAW_FOUR));
            }
            Collections.shuffle(cards);
            draw.addAll(cards);
        }

        // always returns a card
        Card draw() {
            ensureDrawable(1);
            return draw.isEmpty() ? generateFallback() : draw.pop();
        }

        List<Card> drawN(int n) {
            ensureDrawable(n);
            List<Card> hand = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                hand.add(draw.isEmpty() ? generateFallback() : draw.pop());
            }
            return hand;
        }

        /**
         * Silently recycles the discard pile into the draw pile when running low.
         * If the discard pile is also too small, generates a fresh shuffled deck.
         */

        // recycle draw pile when enough cards drawn. regen pile too
        private void ensureDrawable(int needed) {
            if (draw.size() >= needed)
                return;

            // recycle discard & keep top card
            if (discard.size() > 1) {
                Card top = discard.pop();
                List<Card> rest = new ArrayList<>(discard);
                discard.clear();
                Collections.shuffle(rest);
                draw.addAll(rest);
                discard.push(top);
            }

            // create fresh shuffled deck
            if (draw.size() < needed) {
                List<Card> extra = new ArrayList<>();
                for (CardColor c : new CardColor[] { CardColor.RED, CardColor.GREEN, CardColor.BLUE,
                        CardColor.YELLOW }) {
                    extra.add(new Card(c, CardType.ZERO));
                    for (CardType t : new CardType[] { CardType.ONE, CardType.TWO, CardType.THREE, CardType.FOUR,
                            CardType.FIVE, CardType.SIX, CardType.SEVEN, CardType.EIGHT, CardType.NINE,
                            CardType.DRAW_TWO, CardType.REVERSE }) {
                        extra.add(new Card(c, t));
                        extra.add(new Card(c, t));
                    }
                }
                for (int i = 0; i < 4; i++) {
                    extra.add(new Card(CardColor.WILD, CardType.WILD));
                    extra.add(new Card(CardColor.WILD, CardType.WILD_DRAW_FOUR));
                }
                Collections.shuffle(extra);
                draw.addAll(extra);
            }
        }

        // just in case generate single card (emergency fallback)
        private Card generateFallback() {
            CardColor[] colors = { CardColor.RED, CardColor.GREEN, CardColor.BLUE, CardColor.YELLOW };
            CardType[] types = { CardType.ONE, CardType.TWO, CardType.THREE, CardType.FIVE };
            return new Card(colors[new Random().nextInt(colors.length)], types[new Random().nextInt(types.length)]);
        }

        void discard(Card c) {
            discard.push(c);
        }

        Card peekDiscard() {
            return discard.isEmpty() ? null : discard.peek();
        }

        int discardSize() {
            return discard.size();
        }
    }

    // player state
    static class PlayerInfo {
        String name;
        List<Card> hand = new ArrayList<>();
        boolean isLocal;

        PlayerInfo(String name, boolean isLocal) {
            this.name = name;
            this.isLocal = isLocal;
        }
    }

    // image loader and & tinter
    private File cardsDir = null;

    private File resolveCardsDir() {
        if (cardsDir != null)
            return cardsDir;

        File f = new File("cards");
        System.out.println("[SHACUNO] Looking for cards/ in: " + f.getAbsolutePath());
        if (f.isDirectory()) {
            cardsDir = f;
            System.out.println("[SHACUNO] Found cards/ at: " + cardsDir.getAbsolutePath());
            return cardsDir;
        }

        try {
            File classLoc = new File(UnoGame.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File dir = classLoc.isDirectory() ? classLoc : classLoc.getParentFile();
            f = new File(dir, "cards");
            System.out.println("[SHACUNO] Looking for cards/ in: " + f.getAbsolutePath());
            if (f.isDirectory()) {
                cardsDir = f;
                System.out.println("[SHACUNO] Found cards/ at: " + cardsDir.getAbsolutePath());
                return cardsDir;
            }
        } catch (Exception ignored) {
        }

        f = new File(System.getProperty("user.dir"), "cards");
        System.out.println("[SHACUNO] Looking for cards/ in: " + f.getAbsolutePath());
        if (f.isDirectory()) {
            cardsDir = f;
            System.out.println("[SHACUNO] Found cards/ at: " + cardsDir.getAbsolutePath());
            return cardsDir;
        }

        System.out.println("[SHACUNO] WARNING: cards/ folder not found! Using placeholder cards.");
        return null;
    }

    private BufferedImage loadImageFile(String filename) {
        File dir = resolveCardsDir();
        if (dir == null)
            return null;
        File imgFile = new File(dir, filename);
        if (!imgFile.exists()) {
            System.out.println("[SHACUNO] File not found: " + imgFile.getAbsolutePath());
            return null;
        }
        try {
            BufferedImage img = javax.imageio.ImageIO.read(imgFile);
            if (img == null)
                System.out.println("[SHACUNO] ImageIO returned null for: " + filename);
            else
                System.out
                        .println("[SHACUNO] Loaded: " + filename + " (" + img.getWidth() + "x" + img.getHeight() + ")");
            return img;
        } catch (Exception e) {
            System.out.println("[SHACUNO] Failed to load " + filename + ": " + e.getMessage());
            return null;
        }
    }

    private final Map<String, ImageIcon> iconCache = new HashMap<>();

    private ImageIcon loadCardIcon(Card card) {
        return loadCardIconWithHue(card, -1f);
    }

    private ImageIcon loadCardIconWithHue(Card card, float hueOverride) {
        String key = card.imageName() + "_" + card.color + (hueOverride >= 0 ? "_" + (int) (hueOverride * 360) : "");
        if (hueOverride < 0 && iconCache.containsKey(key))
            return iconCache.get(key);

        BufferedImage raw = loadImageFile(card.imageName() + ".png");

        BufferedImage base = new BufferedImage(CARD_W, CARD_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g0 = base.createGraphics();
        g0.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (raw != null)
            g0.drawImage(raw, 0, 0, CARD_W, CARD_H, null);
        else
            g0.drawImage(createPlaceholderImage(card), 0, 0, null);
        g0.dispose();

        if (card.color != CardColor.WILD || hueOverride >= 0) {
            float hue, sat, bri;
            if (hueOverride >= 0) {
                hue = hueOverride;
                sat = 1.0f;
                bri = 0.85f;
            } else {
                float[] hsb = TINT_HSB[card.color.ordinal()];
                hue = hsb[0];
                sat = hsb[1];
                bri = hsb[2];
            }
            Color tintColor = Color.getHSBColor(hue, sat, bri);
            int tr = tintColor.getRed(), tg = tintColor.getGreen(), tb = tintColor.getBlue();

            for (int y = 0; y < CARD_H; y++) {
                for (int x = 0; x < CARD_W; x++) {
                    int argb = base.getRGB(x, y);
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    r = (r * tr) / 255;
                    g = (g * tg) / 255;
                    b = (b * tb) / 255;
                    base.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
        }

        ImageIcon result = new ImageIcon(base);
        if (hueOverride < 0)
            iconCache.put(key, result);
        return result;
    }

    private Image createPlaceholderImage(Card card) {
        BufferedImage img = new BufferedImage(CARD_W, CARD_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color base = card.color == CardColor.WILD ? new Color(30, 30, 30) : SOLID_COLORS[card.color.ordinal()];
        g2.setColor(base);
        g2.fillRoundRect(0, 0, CARD_W, CARD_H, 12, 12);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 13));
        FontMetrics fm = g2.getFontMetrics();
        String label = card.type.toString().replace("_", "\n");
        String[] lines = label.split("\n");
        int y = CARD_H / 2 - (lines.length * 15) / 2 + 5;
        for (String line : lines) {
            g2.drawString(line, (CARD_W - fm.stringWidth(line)) / 2, y);
            y += 16;
        }
        g2.setColor(new Color(255, 255, 255, 60));
        g2.setStroke(new BasicStroke(3));
        g2.drawRoundRect(2, 2, CARD_W - 4, CARD_H - 4, 10, 10);
        g2.dispose();
        return img;
    }

    private ImageIcon loadCardBack() {
        String key = "BACK";
        if (iconCache.containsKey(key))
            return iconCache.get(key);
        BufferedImage raw = loadImageFile("Card_Back.png");

        BufferedImage img = new BufferedImage(CARD_W, CARD_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (raw != null) {
            g2.drawImage(raw, 0, 0, CARD_W, CARD_H, null);
        } else {
            g2.setColor(new Color(30, 30, 80));
            g2.fillRoundRect(0, 0, CARD_W, CARD_H, 12, 12);
            g2.setColor(new Color(200, 50, 50));
            g2.setFont(new Font("Arial", Font.BOLD, 22));
            g2.drawString("SHACUNO", 18, CARD_H / 2 + 8);
            g2.setColor(new Color(255, 255, 255, 60));
            g2.setStroke(new BasicStroke(3));
            g2.drawRoundRect(2, 2, CARD_W - 4, CARD_H - 4, 10, 10);
        }
        g2.dispose();
        ImageIcon result = new ImageIcon(img);
        iconCache.put(key, result);
        return result;
    }

    // game loop
    private final List<PlayerInfo> players = new ArrayList<>();
    private Deck deck;
    private int currentTurn = 0;
    private int direction = 1;
    private CardColor activeWildColor = null;
    private int stackedDraw = 0;
    private boolean gameStarted = false;
    private int localPlayerIndex = 0;

    // network
    private boolean isHost = false;
    private ServerSocket serverSocket;
    private final List<Socket> clientSockets = new ArrayList<>();
    private final List<ObjectOutputStream> clientOuts = new ArrayList<>();
    private ObjectOutputStream hostOut;
    private Socket hostSocket;

    // ui components
    private JTextArea infoBox;
    private JPanel handPanel;
    private JPanel otherPlayersPanel;
    private JLabel drawPileLabel;
    private JLabel discardPileLabel;
    private JPanel centerPanel;

    // chat components
    private JTextArea chatArea;
    private JTextField chatInput;
    private String localPlayerName = "Player";

    // constructor
    public UnoGame(String localName, boolean host, String joinIp, int port) {
        super("SHACUNO");
        this.isHost = host;
        this.localPlayerName = localName;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1300, 750);
        setMinimumSize(new Dimension(1050, 650));
        setLocationRelativeTo(null);

        buildUI();
        applyWindowIcon();
        setVisible(true);

        if (host) {
            startHost(localName, port);
        } else {
            joinServer(localName, joinIp, port);
        }
    }

    // solo/debug
    public UnoGame() {
        super("SHACUNO — Solo Debug");
        this.isHost = true;
        this.localPlayerName = "You (Local)";

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1300, 750);
        setMinimumSize(new Dimension(1050, 650));
        setLocationRelativeTo(null);

        buildUI();
        applyWindowIcon();
        setVisible(true);
        initSoloGame();
    }

    // window icon
    private void applyWindowIcon() {
        BufferedImage raw = loadImageFile("Card_Back.png");
        if (raw != null) {
            // provide multiple sizes so the OS picks the sharpest one
            List<Image> icons = new ArrayList<>();
            for (int size : new int[] { 16, 32, 48, 64 }) {
                BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(raw, 0, 0, size, size, null);
                g.dispose();
                icons.add(scaled);
            }
            setIconImages(icons);
        }
    }

    // ui construction
    private void buildUI() {
        getContentPane().setBackground(new Color(20, 80, 40));
        setLayout(new BorderLayout(8, 8));

        // top: info box
        infoBox = new JTextArea(2, 40);
        infoBox.setEditable(false);
        infoBox.setLineWrap(true);
        infoBox.setWrapStyleWord(true);
        infoBox.setFont(new Font("Monospaced", Font.BOLD, 14));
        infoBox.setBackground(new Color(10, 50, 20));
        infoBox.setForeground(new Color(220, 255, 200));
        infoBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 160, 80), 2),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        infoBox.setText("Waiting for players to connect...");
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(10, 50, 20));
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));
        topBar.add(infoBox, BorderLayout.CENTER);
        add(topBar, BorderLayout.NORTH);

        // left: other players panel
        otherPlayersPanel = new JPanel();
        otherPlayersPanel.setLayout(new BoxLayout(otherPlayersPanel, BoxLayout.Y_AXIS));
        otherPlayersPanel.setBackground(new Color(15, 60, 30));
        otherPlayersPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 2, new Color(60, 140, 70)),
                BorderFactory.createEmptyBorder(16, 10, 16, 10)));
        otherPlayersPanel.setPreferredSize(new Dimension(160, 0));
        add(otherPlayersPanel, BorderLayout.WEST);

        // center: draw & discard pile
        centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(new Color(20, 80, 40));

        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.setBackground(new Color(20, 80, 40));
        centerWrapper.add(centerPanel, BorderLayout.NORTH);
        centerWrapper.add(new JPanel() {
            {
                setBackground(new Color(20, 80, 40));
            }
        }, BorderLayout.CENTER);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(40, 30, 0, 30);

        // discard pile
        JPanel discardArea = new JPanel(new BorderLayout(0, 6));
        discardArea.setBackground(new Color(20, 80, 40));
        JLabel discardTitle = new JLabel("DISCARD", SwingConstants.CENTER);
        discardTitle.setForeground(new Color(180, 255, 180));
        discardTitle.setFont(new Font("Monospaced", Font.BOLD, 11));
        discardPileLabel = new JLabel(loadCardBack(), SwingConstants.CENTER);
        discardPileLabel.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200, 80), 2));
        discardArea.add(discardTitle, BorderLayout.NORTH);
        discardArea.add(discardPileLabel, BorderLayout.CENTER);
        gbc.gridx = 0;
        gbc.gridy = 0;
        centerPanel.add(discardArea, gbc);

        // draw pile
        JPanel drawArea = new JPanel(new BorderLayout(0, 6));
        drawArea.setBackground(new Color(20, 80, 40));
        JLabel drawTitle = new JLabel("DRAW PILE", SwingConstants.CENTER);
        drawTitle.setForeground(new Color(180, 255, 180));
        drawTitle.setFont(new Font("Monospaced", Font.BOLD, 11));
        JLabel backLabel = new JLabel(loadCardBack(), SwingConstants.CENTER);
        backLabel.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200, 80), 2));
        backLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onDrawCard();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                backLabel.setBorder(BorderFactory.createLineBorder(Color.YELLOW, 2));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                backLabel.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200, 80), 2));
            }
        });
        drawArea.add(drawTitle, BorderLayout.NORTH);
        drawArea.add(backLabel, BorderLayout.CENTER);
        gbc.gridx = 1;
        gbc.gridy = 0;
        centerPanel.add(drawArea, gbc);

        add(centerWrapper, BorderLayout.CENTER);

        // right: chat
        add(buildChatPanel(), BorderLayout.EAST);

        // bottom: player's hand
        JPanel bottomWrapper = new JPanel(new BorderLayout());
        bottomWrapper.setBackground(new Color(15, 60, 30));
        bottomWrapper.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, new Color(60, 140, 70)));

        JLabel handLabel = new JLabel("YOUR HAND", SwingConstants.CENTER);
        handLabel.setForeground(new Color(150, 255, 150));
        handLabel.setFont(new Font("Monospaced", Font.BOLD, 11));
        handLabel.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
        bottomWrapper.add(handLabel, BorderLayout.NORTH);

        handPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 8));
        handPanel.setBackground(new Color(15, 60, 30));
        JScrollPane handScroll = new JScrollPane(handPanel,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        handScroll.setBorder(null);
        handScroll.getViewport().setBackground(new Color(15, 60, 30));
        handScroll.setPreferredSize(new Dimension(0, CARD_H + 40));
        bottomWrapper.add(handScroll, BorderLayout.CENTER);
        add(bottomWrapper, BorderLayout.SOUTH);
    }

    // chat panel
    private JPanel buildChatPanel() {
        JPanel chatPanel = new JPanel(new BorderLayout(0, 6));
        chatPanel.setPreferredSize(new Dimension(220, 0));
        chatPanel.setBackground(new Color(12, 48, 24));
        chatPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 2, 0, 0, new Color(60, 140, 70)),
                BorderFactory.createEmptyBorder(10, 8, 10, 8)));

        // header
        JLabel header = new JLabel("CHAT", SwingConstants.CENTER);
        header.setFont(new Font("Monospaced", Font.BOLD, 13));
        header.setForeground(new Color(150, 255, 150));
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        chatPanel.add(header, BorderLayout.NORTH);

        // chat display area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        chatArea.setBackground(new Color(8, 36, 18));
        chatArea.setForeground(new Color(210, 255, 210));
        chatArea.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        JScrollPane chatScroll = new JScrollPane(chatArea,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        chatScroll.setBorder(BorderFactory.createLineBorder(new Color(50, 110, 60), 1));
        chatPanel.add(chatScroll, BorderLayout.CENTER);

        // bottom: input + buttons
        JPanel bottomChat = new JPanel(new BorderLayout(0, 5));
        bottomChat.setBackground(new Color(12, 48, 24));

        // input row
        JPanel inputRow = new JPanel(new BorderLayout(4, 0));
        inputRow.setBackground(new Color(12, 48, 24));

        chatInput = new JTextField();
        chatInput.setFont(new Font("Monospaced", Font.PLAIN, 12));
        chatInput.setBackground(new Color(8, 36, 18));
        chatInput.setForeground(new Color(210, 255, 210));
        chatInput.setCaretColor(new Color(150, 255, 150));
        chatInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(50, 110, 60), 1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));

        JButton sendBtn = new JButton("Send");
        sendBtn.setFont(new Font("Monospaced", Font.BOLD, 11));
        sendBtn.setBackground(new Color(40, 120, 55));
        sendBtn.setForeground(Color.BLACK);
        sendBtn.setFocusPainted(false);
        sendBtn.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        sendBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        ActionListener sendAction = e -> sendChatMessage();
        chatInput.addActionListener(sendAction); // Enter key sends
        sendBtn.addActionListener(sendAction);

        inputRow.add(chatInput, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);

        // button row: Clear & Export
        JPanel btnRow = new JPanel(new GridLayout(1, 2, 5, 0));
        btnRow.setBackground(new Color(12, 48, 24));

        JButton clearBtn = new JButton("Clear");
        clearBtn.setFont(new Font("Monospaced", Font.BOLD, 11));
        clearBtn.setBackground(new Color(100, 40, 40));
        clearBtn.setForeground(Color.BLACK);
        clearBtn.setFocusPainted(false);
        clearBtn.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
        clearBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearBtn.setToolTipText("Clear chat (local only)");
        clearBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Clear chat history on your screen?",
                    "Clear Chat", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                chatArea.setText("");
            }
        });

        JButton exportBtn = new JButton("Export");
        exportBtn.setFont(new Font("Monospaced", Font.BOLD, 11));
        exportBtn.setBackground(new Color(30, 80, 120));
        exportBtn.setForeground(Color.BLACK);
        exportBtn.setFocusPainted(false);
        exportBtn.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
        exportBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        exportBtn.setToolTipText("Export chat to .txt file");
        exportBtn.addActionListener(e -> exportChat());

        btnRow.add(clearBtn);
        btnRow.add(exportBtn);

        bottomChat.add(inputRow, BorderLayout.NORTH);
        bottomChat.add(btnRow, BorderLayout.SOUTH);

        chatPanel.add(bottomChat, BorderLayout.SOUTH);
        return chatPanel;
    }

    // caht logic
    // appends message
    private void appendChat(String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    private String decryptChatPayload(String encryptedPayload) {
        try {
            return EncryptionManager.decryptMessage(encryptedPayload);
        } catch (RuntimeException ex) {
            return "[Could not decrypt chat message]";
        }
    }

    // sends message
    private void sendChatMessage() {
        String text = chatInput.getText().trim();
        if (text.isEmpty())
            return;
        chatInput.setText("");

        String formatted = localPlayerName + ": " + text;
        String encryptedPayload;
        try {
            encryptedPayload = EncryptionManager.encryptMessage(formatted);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not encrypt chat message:\n" + ex.getMessage(),
                    "Encryption Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        appendChat(formatted);

        if (isHost) {
            // broadcast to all clients
            for (ObjectOutputStream out : clientOuts) {
                try {
                    out.writeObject(new NetMessage(NetMessage.Type.CHAT, encryptedPayload));
                    out.flush();
                    out.reset();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        } else {
            // send to host, who will relay it to everyone
            sendToHost(new NetMessage(NetMessage.Type.CHAT, encryptedPayload));
        }
    }

    // exports chat log
    private void exportChat() {
        String content = chatArea.getText();
        if (content.isBlank()) {
            JOptionPane.showMessageDialog(this, "Chat is empty — nothing to export.",
                    "Export Chat", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Chat Log");
        chooser.setSelectedFile(new File("shacuno_chat.txt"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Text files (*.txt)", "txt"));

        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION)
            return;

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".txt")) {
            file = new File(file.getAbsolutePath() + ".txt");
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("=== SHACUNO Chat Log ===");
            pw.println("Exported: " + new java.util.Date());
            pw.println("====================");
            pw.println(content);
            JOptionPane.showMessageDialog(this,
                    "Chat exported to:\n" + file.getAbsolutePath(),
                    "Export Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to export chat:\n" + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // SOLO DEBUG
    private void initSoloGame() {
        players.clear();
        players.add(new PlayerInfo("You (Local)", true));
        players.add(new PlayerInfo("Bot Bob", false));
        players.add(new PlayerInfo("Bot Bobby", false));
        localPlayerIndex = 0;
        deck = new Deck();
        for (PlayerInfo p : players)
            p.hand.addAll(deck.drawN(7));
        Card first;
        do {
            first = deck.draw();
        } while (first.isWild());
        deck.discard(first);
        gameStarted = true;
        startRainbowTimer();
        refreshUI();
        setInfo("Game started! It's YOUR turn. Play a card or draw.");
        appendChat("[System]: Solo game started. Chat is local-only in solo mode.");
    }

    private void startRainbowTimer() {
        if (rainbowTimer != null)
            rainbowTimer.stop();
        rainbowTimer = new javax.swing.Timer(50, e -> {
            rainbowHue = (rainbowHue + 0.008f) % 1.0f;
            refreshHandRainbow();
            refreshDiscardRainbow();
        });
        rainbowTimer.start();
    }

    private void refreshHandRainbow() {
        if (!gameStarted || players.isEmpty())
            return;
        PlayerInfo local = players.get(localPlayerIndex);
        Component[] comps = handPanel.getComponents();
        int idx = 0;
        for (Card card : local.hand) {
            if (idx >= comps.length)
                break;
            if (card.isWild() && comps[idx] instanceof JLabel lbl) {
                lbl.setIcon(loadCardIconWithHue(card, rainbowHue));
            }
            idx++;
        }
    }

    private void refreshDiscardRainbow() {
        if (deck == null)
            return;
        Card top = deck.peekDiscard();
        if (top != null && top.isWild()) {
            discardPileLabel.setIcon(loadCardIconWithHue(top, rainbowHue));
        }
    }

    // networking for host
    private void startHost(String name, int port) {
        localPlayerIndex = 0;
        players.add(new PlayerInfo(name, true));
        setInfo("Hosting on port " + port + ". Waiting for players... (1/" + MAX_PLAYERS + ")");
        appendChat("[System]: You are the host. Share your IP and port " + port + " with friends.");

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                while (players.size() < MAX_PLAYERS) {
                    Socket client = serverSocket.accept();
                    ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(client.getInputStream());
                    clientSockets.add(client);
                    clientOuts.add(out);

                    String joinName = (String) in.readObject();
                    players.add(new PlayerInfo(joinName, false));
                    SwingUtilities.invokeLater(() -> {
                        setInfo(joinName + " joined! (" + players.size() + "/" + MAX_PLAYERS
                                + ") — Host: press Start when ready.");
                        appendChat("[System]: " + joinName + " joined the game.");
                        refreshOtherPlayers();
                    });
                    new Thread(() -> listenToClient(in)).start();

                    if (players.size() == MAX_PLAYERS)
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        JButton startBtn = new JButton("Start Game");
        startBtn.setFont(new Font("Monospaced", Font.BOLD, 14));
        startBtn.setBackground(new Color(220, 60, 60));
        startBtn.setForeground(Color.WHITE);
        startBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        startBtn.addActionListener(e -> {
            if (players.size() < 2) {
                setInfo("Need at least 2 players!");
                return;
            }
            startBtn.setVisible(false);
            hostStartGame();
        });
        centerPanel.add(startBtn, new GridBagConstraints() {
            {
                gridx = 0;
                gridy = 1;
                gridwidth = 2;
                insets = new Insets(20, 0, 0, 0);
            }
        });
        centerPanel.revalidate();
    }

    private void hostStartGame() {
        deck = new Deck();
        for (PlayerInfo p : players)
            p.hand.addAll(deck.drawN(7));
        Card first;
        do {
            first = deck.draw();
        } while (first.isWild());
        deck.discard(first);
        gameStarted = true;
        startRainbowTimer();
        broadcastState();
        refreshUI();
        setInfo(players.get(currentTurn).name + "'s turn.");
        appendChat("[System]: Game has started! Good luck everyone.");
    }

    // networking for client
    private void joinServer(String name, String ip, int port) {
        setInfo("Connecting to " + ip + ":" + port + "...");
        appendChat("[System]: Connecting to " + ip + ":" + port + "...");
        new Thread(() -> {
            try {
                hostSocket = new Socket(ip, port);
                hostOut = new ObjectOutputStream(hostSocket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(hostSocket.getInputStream());
                hostOut.writeObject(name);
                hostOut.flush();
                SwingUtilities.invokeLater(() -> {
                    setInfo("Connected! Waiting for host to start...");
                    appendChat("[System]: Connected! Waiting for host to start the game.");
                });
                listenToHost(in);
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    setInfo("Connection failed: " + e.getMessage());
                    appendChat("[System]: Connection failed — " + e.getMessage());
                });
            }
        }).start();
    }

    // network messages
    static class NetMessage implements Serializable {
        enum Type {
            STATE, PLAY_CARD, DRAW_CARD, CHOOSE_COLOR, CHAT
        }

        Type type;
        Object payload;

        NetMessage(Type t, Object p) {
            type = t;
            payload = p;
        }
    }

    static class GameState implements Serializable {
        List<String> playerNames = new ArrayList<>();
        List<List<Card>> hands = new ArrayList<>();
        Card topDiscard;
        CardColor wildColor;
        int currentTurn;
        int direction;
        int stackedDraw;
        int drawPileSize; // kept for protocol compatibility but not displayed
    }

    private void broadcastState() {
        if (!isHost)
            return;
        GameState gs = buildState();
        for (ObjectOutputStream out : clientOuts) {
            try {
                out.writeObject(new NetMessage(NetMessage.Type.STATE, gs));
                out.flush();
                out.reset();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private GameState buildState() {
        GameState gs = new GameState();
        for (PlayerInfo p : players) {
            gs.playerNames.add(p.name);
            gs.hands.add(new ArrayList<>(p.hand));
        }
        gs.topDiscard = deck.peekDiscard();
        gs.wildColor = activeWildColor;
        gs.currentTurn = currentTurn;
        gs.direction = direction;
        gs.stackedDraw = stackedDraw;
        gs.drawPileSize = 999; // always "infinite" from client perspective
        return gs;
    }

    private void applyState(GameState gs) {
        players.clear();
        for (int i = 0; i < gs.playerNames.size(); i++) {
            PlayerInfo p = new PlayerInfo(gs.playerNames.get(i), i == localPlayerIndex);
            p.hand.addAll(gs.hands.get(i));
            players.add(p);
        }
        currentTurn = gs.currentTurn;
        direction = gs.direction;
        activeWildColor = gs.wildColor;
        stackedDraw = gs.stackedDraw;
        deck = new Deck() {
            {
                discard(gs.topDiscard);
            }
        };
        gameStarted = true;
    }

    private void listenToHost(ObjectInputStream in) {
        try {
            while (true) {
                NetMessage msg = (NetMessage) in.readObject();
                SwingUtilities.invokeLater(() -> handleHostMessage(msg));
            }
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> setInfo("Disconnected from host."));
        }
    }

    private void listenToClient(ObjectInputStream in) {
        try {
            while (true) {
                NetMessage msg = (NetMessage) in.readObject();
                SwingUtilities.invokeLater(() -> handleClientMessage(msg));
            }
        } catch (Exception e) {
            /* client disconnected */
        }
    }

    private void handleHostMessage(NetMessage msg) {
        switch (msg.type) {
            case STATE -> {
                applyState((GameState) msg.payload);
                refreshUI();
                updateTurnInfo();
            }
            case CHAT -> {
                String encryptedPayload = (String) msg.payload;
                appendChat(decryptChatPayload(encryptedPayload));
            }
            default -> {
            }
        }
    }

    private void handleClientMessage(NetMessage msg) {
        switch (msg.type) {
            case PLAY_CARD -> performPlay((Card) msg.payload, null);
            case DRAW_CARD -> performDraw();
            case CHOOSE_COLOR -> {
                activeWildColor = (CardColor) msg.payload;
                broadcastState();
                refreshUI();
            }
            case CHAT -> {
                // Host relays encrypted chat payload to all clients, then decrypts locally.
                String encryptedPayload = (String) msg.payload;
                appendChat(decryptChatPayload(encryptedPayload));
                for (ObjectOutputStream out : clientOuts) {
                    try {
                        out.writeObject(new NetMessage(NetMessage.Type.CHAT, encryptedPayload));
                        out.flush();
                        out.reset();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
            default -> {
            }
        }
    }

    private void sendToHost(NetMessage msg) {
        try {
            if (hostOut != null) {
                hostOut.writeObject(msg);
                hostOut.flush();
                hostOut.reset();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // game logic
    private boolean isMyTurn() {
        return gameStarted && currentTurn == localPlayerIndex;
    }

    private void onPlayCard(Card card) {
        if (!isMyTurn()) {
            setInfo("It's not your turn!");
            return;
        }
        PlayerInfo local = players.get(localPlayerIndex);
        Card top = deck.peekDiscard();

        if (stackedDraw > 0) {
            if (card.drawPenalty() == 0) {
                setInfo("You must play a Draw card to stack, or draw " + stackedDraw + " cards.");
                return;
            }
        }

        if (top != null && !card.canPlayOn(top, activeWildColor)) {
            setInfo("That card can't be played on " + describeTop() + ".");
            return;
        }

        if (!local.hand.contains(card))
            return;

        if (isHost) {
            performPlay(card, null);
        } else {
            sendToHost(new NetMessage(NetMessage.Type.PLAY_CARD, card));
            local.hand.remove(card);
            refreshHand();
        }
    }

    private void performPlay(Card card, PlayerInfo sender) {
        PlayerInfo player = (sender != null) ? sender : players.get(currentTurn);
        player.hand.remove(card);
        deck.discard(card);
        activeWildColor = null;

        if (player.hand.isEmpty()) {
            gameStarted = false;
            broadcastState();
            refreshUI();
            appendChat("[System]: 🎉 " + player.name + " wins the game!");
            JOptionPane.showMessageDialog(this, player.name + " wins! 🎉", "SHACUNO - Game Over",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (card.type == CardType.REVERSE) {
            direction *= -1;
        }
        if (card.drawPenalty() > 0) {
            stackedDraw += card.drawPenalty();
        }

        if (card.isWild()) {
            if (player.isLocal) {
                promptColorChoice();
                return;
            } else {
                CardColor[] colors = { CardColor.RED, CardColor.GREEN, CardColor.BLUE, CardColor.YELLOW };
                activeWildColor = colors[new Random().nextInt(4)];
            }
        }

        advanceTurn();
    }

    private void advanceTurn() {
        currentTurn = (currentTurn + direction + players.size()) % players.size();
        broadcastState();
        refreshUI();
        updateTurnInfo();

        PlayerInfo current = players.get(currentTurn);
        if (!current.isLocal && isHost) {
            javax.swing.Timer t = new javax.swing.Timer(1200, e -> botTakeTurn(current));
            t.setRepeats(false);
            t.start();
        }
    }

    private void botTakeTurn(PlayerInfo bot) {
        Card top = deck.peekDiscard();
        if (stackedDraw > 0) {
            Card stack = bot.hand.stream()
                    .filter(c -> c.drawPenalty() > 0 && c.canPlayOn(top, activeWildColor))
                    .findFirst().orElse(null);
            if (stack != null) {
                performPlay(stack, bot);
                return;
            }
            List<Card> drawn = deck.drawN(stackedDraw);
            stackedDraw = 0;
            bot.hand.addAll(drawn);
            advanceTurn();
            return;
        }
        Card play = bot.hand.stream()
                .filter(c -> c.canPlayOn(top, activeWildColor))
                .findFirst().orElse(null);
        if (play != null) {
            performPlay(play, bot);
        } else {
            Card drawn = deck.draw();
            if (drawn != null)
                bot.hand.add(drawn);
            advanceTurn();
        }
    }

    private void onDrawCard() {
        if (!isMyTurn()) {
            setInfo("It's not your turn!");
            return;
        }
        if (isHost) {
            performDraw();
        } else {
            sendToHost(new NetMessage(NetMessage.Type.DRAW_CARD, null));
        }
    }

    private void performDraw() {
        PlayerInfo player = players.get(currentTurn);
        if (stackedDraw > 0) {
            List<Card> drawn = deck.drawN(stackedDraw);
            player.hand.addAll(drawn);
            stackedDraw = 0;
        } else {
            Card c = deck.draw();
            if (c != null)
                player.hand.add(c);
        }
        advanceTurn();
    }

    private void promptColorChoice() {
        String[] options = { "Red", "Green", "Blue", "Yellow" };
        int choice = JOptionPane.showOptionDialog(this,
                "Choose a color for your Wild card:",
                "Wild Card — Choose Color",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (choice < 0)
            choice = 0;
        CardColor[] colorMap = { CardColor.RED, CardColor.GREEN, CardColor.BLUE, CardColor.YELLOW };
        activeWildColor = colorMap[choice];
        if (!isHost) {
            sendToHost(new NetMessage(NetMessage.Type.CHOOSE_COLOR, activeWildColor));
        }
        advanceTurn();
    }

    // ui refresh
    private void refreshUI() {
        refreshDiscard();
        refreshHand();
        refreshOtherPlayers();
    }

    private void refreshDiscard() {
        Card top = (deck != null) ? deck.peekDiscard() : null;
        if (top != null) {
            discardPileLabel.setIcon(loadCardIcon(top));
        } else {
            discardPileLabel.setIcon(loadCardBack());
        }
        discardPileLabel.repaint();
    }

    private void refreshHand() {
        handPanel.removeAll();
        if (players.isEmpty() || !gameStarted) {
            handPanel.revalidate();
            handPanel.repaint();
            return;
        }
        PlayerInfo local = players.get(localPlayerIndex);
        Card top = deck != null ? deck.peekDiscard() : null;
        for (Card card : local.hand) {
            boolean canPlay = isMyTurn() && (stackedDraw == 0 || card.drawPenalty() > 0)
                    && (top == null || card.canPlayOn(top, activeWildColor));
            JLabel lbl = new JLabel(loadCardIcon(card));
            lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            lbl.setToolTipText(card.toString());
            if (canPlay) {
                lbl.setBorder(BorderFactory.createLineBorder(Color.YELLOW, 3));
            } else {
                lbl.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60, 100), 1));
            }
            final Card c = card;
            lbl.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onPlayCard(c);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    if (canPlay)
                        lbl.setBorder(BorderFactory.createLineBorder(Color.WHITE, 3));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (canPlay)
                        lbl.setBorder(BorderFactory.createLineBorder(Color.YELLOW, 3));
                    else
                        lbl.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60, 100), 1));
                }
            });
            handPanel.add(lbl);
        }
        handPanel.revalidate();
        handPanel.repaint();
    }

    private void refreshOtherPlayers() {
        otherPlayersPanel.removeAll();
        JLabel header = new JLabel("PLAYERS");
        header.setFont(new Font("Monospaced", Font.BOLD, 12));
        header.setForeground(new Color(150, 255, 150));
        header.setAlignmentX(Component.CENTER_ALIGNMENT);
        otherPlayersPanel.add(header);
        otherPlayersPanel.add(Box.createVerticalStrut(10));

        for (int i = 0; i < players.size(); i++) {
            if (i == localPlayerIndex)
                continue;
            PlayerInfo p = players.get(i);
            boolean isTurn = (i == currentTurn);
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBackground(isTurn ? new Color(60, 120, 60) : new Color(20, 70, 35));
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(isTurn ? Color.YELLOW : new Color(50, 100, 55), 2),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            card.setMaximumSize(new Dimension(140, 80));
            card.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel name = new JLabel(p.name);
            name.setFont(new Font("Monospaced", Font.BOLD, 12));
            name.setForeground(isTurn ? Color.YELLOW : new Color(200, 255, 200));
            name.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel count = new JLabel("🃏 " + p.hand.size() + " cards");
            count.setFont(new Font("Monospaced", Font.PLAIN, 11));
            count.setForeground(new Color(160, 230, 160));
            count.setAlignmentX(Component.CENTER_ALIGNMENT);

            if (p.hand.size() == 1) {
                JLabel uno = new JLabel("SHACUNO!");
                uno.setFont(new Font("Monospaced", Font.BOLD, 11));
                uno.setForeground(Color.RED);
                uno.setAlignmentX(Component.CENTER_ALIGNMENT);
                card.add(uno);
            }

            card.add(name);
            card.add(Box.createVerticalStrut(4));
            card.add(count);
            otherPlayersPanel.add(card);
            otherPlayersPanel.add(Box.createVerticalStrut(10));
        }

        otherPlayersPanel.revalidate();
        otherPlayersPanel.repaint();
    }

    private void updateTurnInfo() {
        if (!gameStarted)
            return;
        PlayerInfo current = players.get(currentTurn);
        String dir = direction == 1 ? "→" : "←";
        StringBuilder sb = new StringBuilder();
        sb.append(dir).append(" ").append(current.name).append("'s turn.  |  Top: ").append(describeTop());
        if (stackedDraw > 0)
            sb.append("  |  Pending draw: +").append(stackedDraw);
        if (activeWildColor != null)
            sb.append("  |  Wild color: ").append(activeWildColor);
        setInfo(sb.toString());
    }

    private String describeTop() {
        Card top = deck != null ? deck.peekDiscard() : null;
        if (top == null)
            return "none";
        CardColor c = top.isWild() ? activeWildColor : top.color;
        return (c != null ? c : "WILD") + " " + top.type;
    }

    private void setInfo(String msg) {
        SwingUtilities.invokeLater(() -> infoBox.setText(msg));
    }

    // main

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }

            if (args.length == 0 || args[0].equals("--solo")) {
                new UnoGame();
                return;
            }

            if (args[0].equals("--host") && args.length >= 3) {
                int port = Integer.parseInt(args[1]);
                String name = args[2];
                new UnoGame(name, true, null, port);
            } else if (args[0].equals("--join") && args.length >= 4) {
                String ip = args[1];
                int port = Integer.parseInt(args[2]);
                String name = args[3];
                new UnoGame(name, false, ip, port);
            } else {
                System.out.println("Usage:");
                System.out.println("  Host: java ShacunoGame --host <port> <playerName>");
                System.out.println("  Join: java ShacunoGame --join <ip> <port> <playerName>");
                System.out.println("  Solo: java ShacunoGame --solo");
            }
        });
    }
}