
/* MainChatWindow.java
 
 View layer — this is the main chat screen
 
 Design Patterns:
 Observer - appendMessage() / addUser() / removeUser() called by ChatClient
 MVC - View only; ChatClient is the Controller
 Singleton - one session at a time via LoginWindow
 
  Public API for ChatClient (networking thread -> GUI):
    appendMessage(sender, message)
    appendSystemMessage(channel, message)
    addUser(name) / removeUser(name)
    setConnected(boolean)
    setChatClient(ChatClient)
 */
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainChatWindow extends JFrame implements ActionListener {

    // state
    private final String username;
    private boolean connected = false;
    private ChatClient chatClient;
    private String currentRoomName = "General";
    private boolean currentRoomPrivate = false;
    private boolean ownsCurrentRoom = false;

    private StringBuilder chatLog = new StringBuilder();
    private StyledDocument chatDoc = new DefaultStyledDocument();
    private final Map<String, ImageIcon> emojiCache = new HashMap<>();
    private Image logo;

    // ui
    private JTextPane chatArea;
    private JTextField inputField;
    private JButton sendButton, clearButton, exportButton, logoutButton, emojiButton;
    private JButton availableRoomsButton, createRoomButton, deleteRoomButton, playBattleshipButton;
    private JButton themeButton, mediaButton;
    private JButton launchUnoButton, launchWwtbamButton;
    private JLabel statusDot, statusText, userLabel, roomLabel, roomTypeLabel;
    private DefaultListModel<String> userListModel = new DefaultListModel<>();

    // server dropdown button
    private JButton serverDropdownBtn;
    private final java.util.List<ChatClient.RoomInfo> knownRooms = new ArrayList<>();

    // text styles
    private final SimpleAttributeSet styleUser, styleTime, styleMsg, styleSys;

    // constructor
    public MainChatWindow(String username) {
        this.username = username;
        logo = GUIStyles.loadImage("secureLockPlus.png");
        loadEmojis();

        // initial text styles
        styleUser = style(GUIStyles.current.ACCENT, true, false, 14);
        styleTime = style(GUIStyles.current.TEXT_TIMESTAMP, false, false, 11);
        styleMsg = style(GUIStyles.current.TEXT_PRIMARY, false, false, 14);
        styleSys = style(new Color(80, 130, 80), false, true, 12);

        setTitle("S.H.A.C. Waffle Ultra — " + username);
        setSize(1000, 700);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(GUIStyles.current.BG_DARK);

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildInputBar(), BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmExit();
            }
        });
        setLocationRelativeTo(null);
        setVisible(true);

        appendSystemMessage("Welcome, " + username + "! Waiting for server...");
    }

    public void setChatClient(ChatClient c) {
        chatClient = c;
    }

    // public API so ChatClient can populate the room list after a login
    public void setKnownRooms(java.util.List<ChatClient.RoomInfo> rooms) {
        SwingUtilities.invokeLater(() -> {
            knownRooms.clear();
            if (rooms != null)
                knownRooms.addAll(rooms);
            updateRoomButtons();
        });
    }

    // top bar
    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(GUIStyles.current.BG_PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, GUIStyles.current.BORDER_SOFT),
                new EmptyBorder(10, 18, 10, 18)));

        JLabel title = new JLabel("S.H.A.C. Waffle Ultra");
        title.setFont(GUIStyles.FONT_HEADER);
        title.setForeground(GUIStyles.current.ACCENT);
        JPanel left = panel(GUIStyles.current.BG_PANEL);
        left.add(title);

        statusDot = label("●", GUIStyles.FONT_BODY, GUIStyles.current.STATUS_OFF);
        statusText = label("Disconnected", GUIStyles.FONT_SMALL, GUIStyles.current.TEXT_MUTED);

        serverDropdownBtn = new JButton(" General  ▾");
        serverDropdownBtn.setFont(GUIStyles.FONT_BODY);
        serverDropdownBtn.setBackground(GUIStyles.current.FIELD_BG);
        serverDropdownBtn.setForeground(GUIStyles.current.TEXT_PRIMARY);
        serverDropdownBtn.setFocusPainted(false);
        serverDropdownBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GUIStyles.current.BORDER_SOFT, 1),
                new EmptyBorder(4, 12, 4, 12)));
        serverDropdownBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        serverDropdownBtn.setOpaque(true);
        serverDropdownBtn.addActionListener(e -> showRoomPopup());

        JPanel center = panel(GUIStyles.current.BG_PANEL);
        center.add(statusDot);
        center.add(statusText);
        center.add(Box.createHorizontalStrut(10));
        center.add(serverDropdownBtn);

        logoutButton = GUIStyles.button("Logout", GUIStyles.current.ERROR, Color.WHITE);
        logoutButton.addActionListener(this);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        launchUnoButton = GUIStyles.button("UNO", GUIStyles.current.BG_TAB_ACTIVE, GUIStyles.current.TEXT_PRIMARY);
        launchUnoButton.setPreferredSize(new Dimension(60, 32));
        launchUnoButton.setToolTipText("Launch UNO game");
        launchUnoButton.addActionListener(this);

        launchWwtbamButton = GUIStyles.button("WWTBAM", GUIStyles.current.BG_TAB_ACTIVE, GUIStyles.current.TEXT_PRIMARY);
        launchWwtbamButton.setPreferredSize(new Dimension(90, 32));
        launchWwtbamButton.setToolTipText("Launch Who Wants to Be a Millionaire");
        launchWwtbamButton.addActionListener(this);

        right.setBackground(GUIStyles.current.BG_PANEL);
        right.add(userLabel = label(username, GUIStyles.FONT_SMALL, GUIStyles.current.TEXT_PRIMARY));
        right.add(launchUnoButton);
        right.add(launchWwtbamButton);
        right.add(logoutButton);

        bar.add(left, BorderLayout.WEST);
        bar.add(center, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // room pop-up
    private void showRoomPopup() {
        JPopupMenu popup = new JPopupMenu();
        popup.setBackground(GUIStyles.current.FIELD_BG);
        popup.setBorder(BorderFactory.createLineBorder(GUIStyles.current.BORDER_SOFT, 1));
        popup.setLayout(new BoxLayout(popup, BoxLayout.Y_AXIS));

        if (knownRooms.isEmpty()) {
            JMenuItem placeholder = new JMenuItem("No rooms loaded yet");
            placeholder.setFont(GUIStyles.FONT_SMALL);
            placeholder.setForeground(GUIStyles.current.TEXT_MUTED);
            placeholder.setBackground(GUIStyles.current.FIELD_BG);
            placeholder.setEnabled(false);
            popup.add(placeholder);
        } else {
            for (ChatClient.RoomInfo room : knownRooms) {
                String prefix = room.getName().equalsIgnoreCase("General") ? "Home "
                        : room.isPrivateRoom() ? "Private " : "Public ";
                String label = prefix + room.getName()
                        + "  (" + room.getClientCount() + " online)";
                JMenuItem item = new JMenuItem(label);
                item.setFont(GUIStyles.FONT_BODY);
                item.setBackground(GUIStyles.current.FIELD_BG);
                item.setForeground(room.getName().equals(currentRoomName)
                        ? GUIStyles.current.ACCENT
                        : GUIStyles.current.TEXT_PRIMARY);
                item.setBorder(new EmptyBorder(6, 14, 6, 14));
                item.setOpaque(true);
                item.addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) {
                        item.setBackground(GUIStyles.current.BG_TAB_ACTIVE);
                    }

                    public void mouseExited(MouseEvent e) {
                        item.setBackground(GUIStyles.current.FIELD_BG);
                    }
                });
                final ChatClient.RoomInfo target = room;
                item.addActionListener(e -> requestRoomSwitch(target));
                popup.add(item);
            }
        }

        JSeparator sep = new JSeparator();
        sep.setForeground(GUIStyles.current.BORDER_SOFT);
        sep.setBackground(GUIStyles.current.FIELD_BG);
        popup.add(sep);

        JMenuItem createItem = new JMenuItem("✚  Create Private Room");
        createItem.setFont(GUIStyles.FONT_SMALL);
        createItem.setForeground(GUIStyles.current.SUCCESS);
        createItem.setBackground(GUIStyles.current.FIELD_BG);
        createItem.setBorder(new EmptyBorder(6, 14, 3, 14));
        createItem.setOpaque(true);
        createItem.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                createItem.setBackground(GUIStyles.current.BG_TAB_ACTIVE);
            }

            public void mouseExited(MouseEvent e) {
                createItem.setBackground(GUIStyles.current.FIELD_BG);
            }
        });
        createItem.addActionListener(e -> promptCreatePrivateRoom());

        JMenuItem inviteItem = new JMenuItem("Invite User to Room");
        inviteItem.setFont(GUIStyles.FONT_SMALL);
        inviteItem.setForeground(GUIStyles.current.TEXT_MUTED);
        inviteItem.setBackground(GUIStyles.current.FIELD_BG);
        inviteItem.setBorder(new EmptyBorder(3, 14, 6, 14));
        inviteItem.setOpaque(true);
        inviteItem.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                inviteItem.setBackground(GUIStyles.current.BG_TAB_ACTIVE);
            }

            public void mouseExited(MouseEvent e) {
                inviteItem.setBackground(GUIStyles.current.FIELD_BG);
            }
        });
        inviteItem.addActionListener(e -> promptInviteUser());

        popup.add(createItem);
        popup.add(inviteItem);
        popup.show(serverDropdownBtn, 0, serverDropdownBtn.getHeight() + 2);
    }

    // body: tabs, chat, & users
    private JPanel buildBody() {
        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(GUIStyles.current.BG_DARK);
        body.add(buildRoomBar(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                buildChatPanel(), buildUsersPanel());
        split.setDividerLocation(720);
        split.setResizeWeight(1.0);
        split.setDividerSize(4);
        split.setBorder(null);
        body.add(split, BorderLayout.CENTER);
        return body;
    }

    private JPanel buildRoomBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(GUIStyles.current.BG_PANEL);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, GUIStyles.current.BORDER_SOFT));

        roomLabel = label("# " + currentRoomName, GUIStyles.FONT_BODY, GUIStyles.current.TEXT_PRIMARY);
        roomTypeLabel = label("Public Room", GUIStyles.FONT_SMALL, GUIStyles.current.TEXT_MUTED);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        left.setBackground(GUIStyles.current.BG_PANEL);
        left.add(roomLabel);
        left.add(roomTypeLabel);

        availableRoomsButton = GUIStyles.smallButton("Available Rooms");
        createRoomButton = GUIStyles.smallButton("Create Private Room");
        deleteRoomButton = GUIStyles.smallButton("Delete Private Room");
        playBattleshipButton = GUIStyles.smallButton("Play Battleship");
        availableRoomsButton.addActionListener(this);
        createRoomButton.addActionListener(this);
        deleteRoomButton.addActionListener(this);
        playBattleshipButton.addActionListener(this);
        availableRoomsButton.setEnabled(false);
        createRoomButton.setEnabled(false);
        deleteRoomButton.setEnabled(false);
        playBattleshipButton.setEnabled(true);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        right.setBackground(GUIStyles.current.BG_PANEL);
        right.add(playBattleshipButton);
        right.add(availableRoomsButton);
        right.add(createRoomButton);
        right.add(deleteRoomButton);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // caht panel (with watermark)
    private JPanel buildChatPanel() {
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                GUIStyles.paintWatermark(g, logo, getWidth(), getHeight(), 0.05f);
            }
        };
        panel.setBackground(GUIStyles.current.BG_DARK);
        panel.setBorder(new EmptyBorder(8, 12, 8, 6));

        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setBackground(GUIStyles.current.BG_CHAT);
        chatArea.setBorder(new EmptyBorder(10, 12, 10, 12));
        chatArea.setDocument(chatDoc);
        ((DefaultCaret) chatArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JScrollPane scroll = new JScrollPane(chatArea);
        scroll.setBorder(BorderFactory.createLineBorder(GUIStyles.current.BORDER_SOFT, 1));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        clearButton = GUIStyles.smallButton("Clear Chat");
        exportButton = GUIStyles.smallButton("Export Log");
        clearButton.addActionListener(this);
        exportButton.addActionListener(this);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        toolbar.setBackground(GUIStyles.current.BG_DARK);
        toolbar.setOpaque(false);
        toolbar.add(exportButton);
        toolbar.add(clearButton);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // user panel
    private JPanel buildUsersPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(GUIStyles.current.BG_USERS);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, GUIStyles.current.BORDER_SOFT),
                new EmptyBorder(8, 0, 8, 0)));
        panel.setPreferredSize(new Dimension(200, 0));

        JLabel header = label("  Online — 0", GUIStyles.FONT_TINY, GUIStyles.current.TEXT_MUTED);
        header.setBorder(new EmptyBorder(4, 10, 8, 10));

        JList<String> userList = new JList<>(userListModel);
        userList.setFont(GUIStyles.FONT_BODY);
        userList.setForeground(GUIStyles.current.TEXT_PRIMARY);
        userList.setBackground(GUIStyles.current.BG_USERS);
        userList.setFixedCellHeight(28);
        userList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> l, Object v, int i, boolean sel, boolean foc) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(l, v, i, sel, foc);
                lbl.setText("● " + v);
                lbl.setForeground(sel ? GUIStyles.current.ACCENT : GUIStyles.current.TEXT_PRIMARY);
                lbl.setBackground(sel ? GUIStyles.current.BG_TAB_ACTIVE : GUIStyles.current.BG_USERS);
                lbl.setFont(GUIStyles.FONT_BODY);
                lbl.setBorder(new EmptyBorder(4, 6, 4, 6));
                return lbl;
            }
        });
        userListModel.addListDataListener(new javax.swing.event.ListDataListener() {
            void update() {
                header.setText("  Online — " + userListModel.getSize());
            }

            public void intervalAdded(javax.swing.event.ListDataEvent e) {
                update();
            }

            public void intervalRemoved(javax.swing.event.ListDataEvent e) {
                update();
            }

            public void contentsChanged(javax.swing.event.ListDataEvent e) {
                update();
            }
        });

        JScrollPane scroll = new JScrollPane(userList);
        scroll.setBorder(null);
        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // input bar
    private JPanel buildInputBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBackground(GUIStyles.current.BG_PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, GUIStyles.current.BORDER_SOFT),
                new EmptyBorder(12, 16, 12, 16)));

        inputField = new JTextField();
        GUIStyles.styleField(inputField);
        inputField.setToolTipText("Type a message and press Enter or Send");
        inputField.addActionListener(e -> sendMessage());

        emojiButton = GUIStyles.button(":)", GUIStyles.current.FIELD_BG, GUIStyles.current.ACCENT);
        emojiButton.setPreferredSize(new Dimension(46, 42));
        emojiButton.setBorder(BorderFactory.createLineBorder(GUIStyles.current.BORDER_SOFT, 1));
        emojiButton.setBorderPainted(true);
        emojiButton.setToolTipText("Open emoji picker");
        emojiButton.addActionListener(e -> showEmojiPicker());

        mediaButton = GUIStyles.button("[+]", GUIStyles.current.FIELD_BG, GUIStyles.current.TEXT_MUTED);
        mediaButton.setPreferredSize(new Dimension(46, 42));
        mediaButton.setBorder(BorderFactory.createLineBorder(GUIStyles.current.BORDER_SOFT, 1));
        mediaButton.setBorderPainted(true);
        mediaButton.setToolTipText("Upload image (PNG/JPG)");
        mediaButton.addActionListener(e -> uploadMedia());

        themeButton = GUIStyles.button("[Light]", GUIStyles.current.FIELD_BG, GUIStyles.current.TEXT_MUTED);
        themeButton.setPreferredSize(new Dimension(70, 42));
        themeButton.setBorder(BorderFactory.createLineBorder(GUIStyles.current.BORDER_SOFT, 1));
        themeButton.setBorderPainted(true);
        themeButton.setToolTipText("Toggle theme");
        themeButton.addActionListener(e -> toggleTheme());

        sendButton = GUIStyles.button("Send", GUIStyles.current.ACCENT, GUIStyles.current.BG_DARK);
        sendButton.setPreferredSize(new Dimension(100, 42));
        sendButton.addActionListener(e -> sendMessage());

        JPanel right = panel(GUIStyles.current.BG_PANEL);
        ((FlowLayout) right.getLayout()).setHgap(6);
        right.add(themeButton);
        right.add(mediaButton);
        right.add(emojiButton);
        right.add(sendButton);

        bar.add(inputField, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // emoji picker
    private void showEmojiPicker() {
        JDialog picker = new JDialog(this, false);
        picker.setUndecorated(true);

        JPanel grid = new JPanel();
        grid.setBackground(GUIStyles.current.BG_PANEL);
        grid.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GUIStyles.current.BORDER_SOFT, 1),
                new EmptyBorder(8, 8, 8, 8)));

        if (!emojiCache.isEmpty()) {
            grid.setLayout(new GridLayout(0, (int) Math.ceil(Math.sqrt(emojiCache.size())), 6, 6));
            for (Map.Entry<String, ImageIcon> entry : emojiCache.entrySet()) {
                String name = entry.getKey();
                java.net.URL iconUrl = getClass().getClassLoader().getResource("emojis/" + name + ".png");
                Image baseImg = iconUrl != null
                        ? new ImageIcon(iconUrl).getImage()
                        : entry.getValue().getImage();
                ImageIcon large = new ImageIcon(baseImg.getScaledInstance(36, 36, Image.SCALE_SMOOTH));
                JButton btn = new JButton(large);
                btn.setPreferredSize(new Dimension(44, 44));
                btn.setBackground(GUIStyles.current.BG_PANEL);
                btn.setFocusPainted(false);
                btn.setBorderPainted(false);
                btn.setToolTipText(name);
                btn.addActionListener(e -> {
                    int pos = inputField.getCaretPosition();
                    String t = inputField.getText();
                    String tag = ":" + name + ":";
                    inputField.setText(t.substring(0, pos) + tag + t.substring(pos));
                    inputField.setCaretPosition(pos + tag.length());
                    inputField.requestFocus();
                    picker.dispose();
                });
                grid.add(btn);
            }
        } else {
            grid.setLayout(new BorderLayout());
            JLabel msg = new JLabel("<html><center>No emojis found.<br>Add .png files to emojis/</center></html>");
            msg.setFont(GUIStyles.FONT_SMALL);
            msg.setForeground(GUIStyles.current.TEXT_MUTED);
            msg.setHorizontalAlignment(SwingConstants.CENTER);
            msg.setBorder(new EmptyBorder(10, 16, 10, 16));
            grid.add(msg);
        }

        picker.add(grid);
        picker.pack();
        Point loc = emojiButton.getLocationOnScreen();
        picker.setLocation(loc.x, loc.y - picker.getHeight() - 4);
        picker.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                picker.dispose();
            }
        });
        picker.setVisible(true);
    }

    // handles uploading media from computer
    private void uploadMedia() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select an image");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Images (PNG, JPG)", "png", "jpg", "jpeg"));
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;

        File file = chooser.getSelectedFile();
        if (file == null || !file.exists())
            return;

        if (chatClient == null || !connected) {
            JOptionPane.showMessageDialog(this, "Connect to the server before sharing an image.");
            return;
        }

        if (chatClient.sendFile(file)) {
            appendImageMessage(username, file);
        }
    }

    // toggles between the two themes
    private void toggleTheme() {
        GUIStyles.toggle();
        themeButton.setText(GUIStyles.current == GUIStyles.DARK ? "[Light]" : "[Dark]");
        applyTheme();
    }

    // applies the toggled theme
    private void applyTheme() {
        GUIStyles.Theme t = GUIStyles.current;

        // root panels
        getContentPane().setBackground(t.BG_DARK);

        // top bar
        applyPanel((JPanel) ((JPanel) getContentPane()
                .getComponent(0)), t.BG_PANEL);

        // input bar
        JPanel inputBar = (JPanel) getContentPane().getComponent(2);
        inputBar.setBackground(t.BG_PANEL);
        inputBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, t.BORDER_SOFT),
                new javax.swing.border.EmptyBorder(12, 16, 12, 16)));

        // input field
        inputField.setBackground(t.FIELD_BG);
        inputField.setForeground(t.TEXT_PRIMARY);
        inputField.setCaretColor(t.ACCENT);
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(t.BORDER, 1),
                new javax.swing.border.EmptyBorder(8, 12, 8, 10)));

        // buttons in input bar
        styleIconButton(emojiButton, t);
        styleIconButton(mediaButton, t);
        styleIconButton(themeButton, t);
        sendButton.setBackground(t.ACCENT);
        sendButton.setForeground(t.BG_DARK);

        // status
        statusDot.setForeground(connected ? t.STATUS_ON : t.STATUS_OFF);
        statusText.setForeground(connected ? t.SUCCESS : t.ERROR);

        // server dropdown button
        serverDropdownBtn.setBackground(t.FIELD_BG);
        serverDropdownBtn.setForeground(t.TEXT_PRIMARY);
        serverDropdownBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(t.BORDER_SOFT, 1),
                new javax.swing.border.EmptyBorder(4, 12, 4, 12)));

        // chat area
        chatArea.setBackground(t.BG_CHAT);

        // room bar labels
        roomLabel.setForeground(t.TEXT_PRIMARY);
        roomTypeLabel.setForeground(t.TEXT_MUTED);

        // small buttons
        clearButton.setBackground(t.BG_PANEL);
        clearButton.setForeground(t.TEXT_MUTED);
        clearButton.setBorder(BorderFactory.createLineBorder(t.BORDER_SOFT, 1));
        exportButton.setBackground(t.BG_PANEL);
        exportButton.setForeground(t.TEXT_MUTED);
        exportButton.setBorder(BorderFactory.createLineBorder(t.BORDER_SOFT, 1));
        availableRoomsButton.setBackground(t.BG_PANEL);
        availableRoomsButton.setForeground(t.TEXT_MUTED);
        availableRoomsButton.setBorder(BorderFactory.createLineBorder(t.BORDER_SOFT, 1));
        createRoomButton.setBackground(t.BG_PANEL);
        createRoomButton.setForeground(t.TEXT_MUTED);
        createRoomButton.setBorder(BorderFactory.createLineBorder(t.BORDER_SOFT, 1));
        deleteRoomButton.setBackground(t.BG_PANEL);
        deleteRoomButton.setForeground(t.TEXT_MUTED);
        deleteRoomButton.setBorder(BorderFactory.createLineBorder(t.BORDER_SOFT, 1));
        playBattleshipButton.setBackground(t.BG_PANEL);
        playBattleshipButton.setForeground(t.ACCENT);
        playBattleshipButton.setBorder(BorderFactory.createLineBorder(t.BORDER_SOFT, 1));

        // logout button stays red regardless of theme
        logoutButton.setBackground(t.ERROR);

        SwingUtilities.updateComponentTreeUI(this);
        repaint();
    }

    private void styleIconButton(JButton btn, GUIStyles.Theme t) {
        btn.setBackground(t.FIELD_BG);
        btn.setForeground(t.TEXT_MUTED);
        btn.setBorder(BorderFactory.createLineBorder(t.BORDER_SOFT, 1));
    }

    private void applyPanel(JPanel panel, Color bg) {
        panel.setBackground(bg);
    }

    private void appendImageMessage(String sender, String imageName, ImageIcon scaledImage) {
        String ts = new SimpleDateFormat("h:mma").format(new Date()).toLowerCase();
        SwingUtilities.invokeLater(() -> {
            try {
                chatDoc.insertString(chatDoc.getLength(), sender + "  ", styleUser);
                chatDoc.insertString(chatDoc.getLength(), ts + "\n", styleTime);
                chatArea.setCaretPosition(chatDoc.getLength());
                chatArea.insertIcon(scaledImage);
                chatDoc.insertString(chatDoc.getLength(), "\n\n", styleMsg);
            } catch (BadLocationException ex) {
                ex.printStackTrace();
            }
            chatArea.setCaretPosition(chatDoc.getLength());
            chatLog.append(sender).append("  ").append(ts)
                    .append("\n[Image: ").append(imageName).append("]\n\n");
        });
    }

    private ImageIcon scaleImage(ImageIcon raw, int maxWidth) {
        int width = raw.getIconWidth();
        int height = raw.getIconHeight();
        if (width > maxWidth) {
            height = (int) ((double) height / width * maxWidth);
            width = maxWidth;
        }
        return new ImageIcon(raw.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
    }

    // actions
    @Override
    public void actionPerformed(ActionEvent e) {
        switch (e.getActionCommand()) {
            case "Clear Chat" -> clearChat();
            case "Export Log" -> exportLog();
            case "Logout" -> confirmLogout();
            case "Available Rooms" -> promptAvailableRooms();
            case "Create Private Room" -> promptCreatePrivateRoom();
            case "Delete Private Room" -> promptDeletePrivateRoom();
            case "Play Battleship" -> launchBattleship();
            case "UNO" -> launchUnoGame();
            case "WWTBAM" -> launchWwtbamGame();
        }
    }

    private void launchUnoGame() {
        SwingUtilities.invokeLater(() -> {
            try {
                new UnoGame();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Could not launch UnoGame:\n" + ex.getMessage(),
                        "Launch Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void launchWwtbamGame() {
        SwingUtilities.invokeLater(() -> {
            try {
                new GameWWTBAM();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Could not launch GameWWTBAM:\n" + ex.getMessage(),
                        "Launch Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    // send
    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty())
            return;
        String ts = new SimpleDateFormat("h:mma").format(new Date()).toLowerCase();
        appendFormatted(username, ts, text);
        chatLog.append(username).append("  ").append(ts)
                .append("\n").append(text).append("\n\n");
        inputField.setText("");
        if (chatClient != null)
            chatClient.sendMessage(text);
    }

    // public API for ChatClient
    public void appendMessage(String sender, String message) {
        String ts = new SimpleDateFormat("h:mma").format(new Date()).toLowerCase();
        SwingUtilities.invokeLater(() -> {
            appendFormatted(sender, ts, message);
            chatLog.append(sender).append("  ").append(ts)
                    .append("\n").append(message).append("\n\n");
        });
    }

    public void appendImageMessage(String sender, File imageFile) {
        if (imageFile == null || !imageFile.exists()) {
            appendSystemMessage("Could not load shared image.");
            return;
        }

        ImageIcon raw = new ImageIcon(imageFile.getAbsolutePath());
        if (raw.getIconWidth() <= 0) {
            appendSystemMessage("Could not load shared image: " + imageFile.getName());
            return;
        }

        appendImageMessage(sender, imageFile.getName(), scaleImage(raw, 300));
    }

    public void appendSystemMessage(String message) {
        String ts = new SimpleDateFormat("h:mma").format(new Date()).toLowerCase();
        SwingUtilities.invokeLater(() -> {
            try {
                String line = "— " + message + "  " + ts + " —\n\n";
                chatDoc.insertString(chatDoc.getLength(), line, styleSys);
            } catch (BadLocationException ex) {
                ex.printStackTrace();
            }
            chatArea.setCaretPosition(chatDoc.getLength());
            chatLog.append("— ").append(message).append(" —\n\n");
        });
    }

    public void setCurrentRoom(String roomName, boolean privateRoom, boolean owner) {
        SwingUtilities.invokeLater(() -> {
            currentRoomName = roomName;
            currentRoomPrivate = privateRoom;
            ownsCurrentRoom = owner;
            // refresh dropdown button label with emojis
            String prefix = roomName.equalsIgnoreCase("General") ? "🏠 "
                    : privateRoom ? "🔒 " : "🌐 ";
            serverDropdownBtn.setText(prefix + roomName + "  ▾");
            roomLabel.setText("# " + currentRoomName);
            roomTypeLabel.setText(privateRoom
                    ? (owner ? "Private Room — owner" : "Private Room")
                    : "Public Room");
            updateRoomButtons();
        });
    }

    public void resetUsers(String selfName) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            if (selfName != null && !selfName.isBlank()) {
                userListModel.addElement(selfName);
            }
        });
    }

    public ChatClient.RoomInfo chooseRoom(List<ChatClient.RoomInfo> rooms) {
        if (rooms == null || rooms.isEmpty())
            return null;

        final ChatClient.RoomInfo[] selected = new ChatClient.RoomInfo[1];
        Runnable chooser = () -> {
            DefaultListModel<ChatClient.RoomInfo> model = new DefaultListModel<>();
            for (ChatClient.RoomInfo room : rooms) {
                model.addElement(room);
            }

            JList<ChatClient.RoomInfo> roomList = new JList<>(model);
            roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            roomList.setSelectedIndex(0);
            roomList.setBackground(GUIStyles.current.BG_CHAT);
            roomList.setForeground(GUIStyles.current.TEXT_PRIMARY);
            roomList.setFont(GUIStyles.FONT_BODY);
            roomList.setFixedCellHeight(30);

            JScrollPane scrollPane = new JScrollPane(roomList);
            scrollPane.setPreferredSize(new Dimension(420, 180));

            int choice = JOptionPane.showConfirmDialog(this, scrollPane,
                    "Choose Chatroom", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            selected[0] = choice == JOptionPane.OK_OPTION && roomList.getSelectedValue() != null
                    ? roomList.getSelectedValue()
                    : rooms.get(0);
        };

        try {
            if (SwingUtilities.isEventDispatchThread()) {
                chooser.run();
            } else {
                SwingUtilities.invokeAndWait(chooser);
            }
        } catch (Exception e) {
            return rooms.get(0);
        }

        return selected[0];
    }

    public void addUser(String name) {
        SwingUtilities.invokeLater(() -> {
            if (!userListModel.contains(name))
                userListModel.addElement(name);
        });
    }

    public void removeUser(String name) {
        SwingUtilities.invokeLater(() -> userListModel.removeElement(name));
    }

    public void setConnected(boolean online) {
        SwingUtilities.invokeLater(() -> {
            boolean wasConnected = connected;
            connected = online;
            statusDot.setForeground(online ? GUIStyles.current.STATUS_ON : GUIStyles.current.STATUS_OFF);
            statusText.setForeground(online ? GUIStyles.current.SUCCESS : GUIStyles.current.ERROR);
            statusText.setText(online ? "Connected" : "Disconnected");
            sendButton.setEnabled(online);
            updateRoomButtons();
            if (wasConnected && !online)
                appendSystemMessage("Connection lost.");
        });
    }

    // append formatted message (parses the ":emoji:" tags)
    private void appendFormatted(String sender, String ts, String text) {
        StyledDocument doc = chatDoc;
        try {
            doc.insertString(doc.getLength(), sender + "  ", styleUser);
            doc.insertString(doc.getLength(), ts + "\n", styleTime);
            for (String part : text.split("(?=:\\w+:)|(?<=:\\w+:)")) {
                if (part.matches(":\\w+:")) {
                    String name = part.substring(1, part.length() - 1);
                    ImageIcon icon = emojiCache.get(name);
                    if (icon != null) {
                        chatArea.setDocument(doc);
                        chatArea.setCaretPosition(doc.getLength());
                        chatArea.insertIcon(icon);
                    } else {
                        doc.insertString(doc.getLength(), part, styleMsg);
                    }
                } else {
                    doc.insertString(doc.getLength(), part, styleMsg);
                }
            }
            doc.insertString(doc.getLength(), "\n\n", styleMsg);
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
        chatArea.setCaretPosition(doc.getLength());
    }

    // helpers
    private void clearChat() {
        int ok = JOptionPane.showConfirmDialog(this,
                "Clear the chat display? (Log kept for export.)",
                "Clear Chat", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            chatDoc = new DefaultStyledDocument();
            chatArea.setDocument(chatDoc);
        }
    }

    private void exportLog() {
        JFileChooser chooser = new JFileChooser();
        String safeRoomName = currentRoomName.replaceAll("[^A-Za-z0-9_-]+", "_");
        chooser.setSelectedFile(new File("chat_log_" + safeRoomName + "_"
                + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".txt"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (FileWriter fw = new FileWriter(chooser.getSelectedFile())) {
                fw.write("=== " + currentRoomName + " — " + username + " ===\n\n");
                fw.write(chatLog.toString());
                JOptionPane.showMessageDialog(this, "Exported successfully.");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage());
            }
        }
    }

    private void promptCreatePrivateRoom() {
        if (!connected || chatClient == null)
            return;

        String roomName = JOptionPane.showInputDialog(this,
                "Enter a private room name (3-24 chars):",
                "Create Private Room", JOptionPane.PLAIN_MESSAGE);
        if (roomName == null)
            return;

        roomName = roomName.trim();
        if (roomName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Room name cannot be empty.");
            return;
        }

        chatClient.createPrivateRoom(roomName);
    }

    private void promptInviteUser() {
        if (!connected || chatClient == null)
            return;

        if (!currentRoomPrivate) {
            JOptionPane.showMessageDialog(this,
                    "Invites are only available inside a private room.");
            return;
        }

        if (!ownsCurrentRoom) {
            JOptionPane.showMessageDialog(this,
                    "Only the private room owner can invite users.");
            return;
        }

        JTextField userField = new JTextField();
        GUIStyles.styleField(userField);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(GUIStyles.current.BG_PANEL);

        JLabel userHint = new JLabel("Username to invite:");
        userHint.setFont(GUIStyles.FONT_SMALL);
        userHint.setForeground(GUIStyles.current.TEXT_MUTED);
        JLabel infoHint = new JLabel("The user must already be online.");
        infoHint.setFont(GUIStyles.FONT_TINY);
        infoHint.setForeground(GUIStyles.current.TEXT_MUTED);

        form.add(userHint);
        form.add(Box.createVerticalStrut(4));
        form.add(userField);
        form.add(Box.createVerticalStrut(4));
        form.add(infoHint);

        int result = JOptionPane.showConfirmDialog(this, form,
                "Invite User", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION)
            return;

        String invitedUsername = userField.getText().trim();
        if (invitedUsername.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username is required.");
            return;
        }

        chatClient.inviteUserToCurrentRoom(invitedUsername);
    }

    private void promptBattleshipInvite() {
        JTextField userField = new JTextField();
        GUIStyles.styleField(userField);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(GUIStyles.current.BG_PANEL);

        JLabel userHint = new JLabel("Enter the username to challenge:");
        userHint.setFont(GUIStyles.FONT_SMALL);
        userHint.setForeground(GUIStyles.current.TEXT_MUTED);
        JLabel infoHint = new JLabel("They must be online. An invite will appear in the chat.");
        infoHint.setFont(GUIStyles.FONT_TINY);
        infoHint.setForeground(GUIStyles.current.TEXT_MUTED);

        form.add(userHint);
        form.add(Box.createVerticalStrut(4));
        form.add(userField);
        form.add(Box.createVerticalStrut(4));
        form.add(infoHint);

        int result = JOptionPane.showConfirmDialog(this, form,
                "Invite to Battleship", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION)
            return;

        String opponent = userField.getText().trim();
        if (opponent.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username is required.");
            return;
        }

        String invite = "@" + opponent + " — " + username
                + " is challenging you to a game of Battleship! Open the game from chat to play.";
        if (connected && chatClient != null) {
            chatClient.sendMessage(invite);
        }
        appendSystemMessage("Battleship invite sent to " + opponent + ".");
    }

    private void promptAvailableRooms() {
        if (!connected || chatClient == null)
            return;

        if (knownRooms.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No rooms are available right now.");
            return;
        }

        DefaultListModel<ChatClient.RoomInfo> model = new DefaultListModel<>();
        for (ChatClient.RoomInfo room : knownRooms) {
            model.addElement(room);
        }

        JList<ChatClient.RoomInfo> roomList = new JList<>(model);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomList.setBackground(GUIStyles.current.BG_CHAT);
        roomList.setForeground(GUIStyles.current.TEXT_PRIMARY);
        roomList.setFont(GUIStyles.FONT_BODY);
        roomList.setVisibleRowCount(Math.min(model.getSize(), 8));
        roomList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                ChatClient.RoomInfo room = (ChatClient.RoomInfo) value;
                String prefix = room.getName().equalsIgnoreCase("General") ? "Home"
                        : room.isPrivateRoom() ? "Private" : "Public";
                label.setText(prefix + "  " + room.getName() + "  (" + room.getClientCount() + " online)");
                return label;
            }
        });

        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).getName().equalsIgnoreCase(currentRoomName)) {
                roomList.setSelectedIndex(i);
                break;
            }
        }

        JScrollPane scrollPane = new JScrollPane(roomList);
        scrollPane.setPreferredSize(new Dimension(420, 220));

        int choice = JOptionPane.showConfirmDialog(this, scrollPane,
                "Available Rooms", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION)
            return;

        requestRoomSwitch(roomList.getSelectedValue());
    }

    private void launchBattleship() {
        BattleshipGUI.launchWithStartChoice(username, this::promptBattleshipInvite);
    }

    private void promptDeletePrivateRoom() {
        if (!connected || chatClient == null || !currentRoomPrivate || !ownsCurrentRoom)
            return;

        int choice = JOptionPane.showConfirmDialog(this,
                "Delete private room '" + currentRoomName + "' and return to General?",
                "Delete Private Room", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            chatClient.deleteCurrentPrivateRoom();
        }
    }

    private void updateRoomButtons() {
        if (availableRoomsButton != null) {
            availableRoomsButton.setEnabled(connected && !knownRooms.isEmpty());
        }
        createRoomButton.setEnabled(connected);
        deleteRoomButton.setEnabled(connected && currentRoomPrivate && ownsCurrentRoom);
    }

    private void requestRoomSwitch(ChatClient.RoomInfo targetRoom) {
        if (targetRoom == null || chatClient == null)
            return;

        if (targetRoom.getName().equalsIgnoreCase(currentRoomName)) {
            JOptionPane.showMessageDialog(this,
                    "You are already in '" + targetRoom.getName() + "'.");
            return;
        }

        chatClient.sendMessage("/join-room " + targetRoom.getNumber());
    }

    private void confirmLogout() {
        if (JOptionPane.showConfirmDialog(this, "Logout?", "Logout",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            if (chatClient != null)
                chatClient.disconnect();
            dispose();
            LoginWindow.getInstance();
        }
    }

    private void confirmExit() {
        if (JOptionPane.showConfirmDialog(this, "Exit S.H.A.C. Waffle Ultra?",
                "Exit", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
            System.exit(0);
    }

    private void loadEmojis() {
        // Resolve emojis/ via classpath so it works when src/ is the classpath root
        java.net.URL dirUrl = getClass().getClassLoader().getResource("emojis");
        File dir;
        if (dirUrl != null) {
            try {
                dir = new File(dirUrl.toURI());
            } catch (java.net.URISyntaxException e) {
                dir = new File(dirUrl.getPath());
            }
        } else {
            dir = new File("emojis");
        }
        if (!dir.exists())
            return;
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
        if (files == null)
            return;
        for (File f : files) {
            String name = f.getName().replaceFirst("[.][^.]+$", "");
            emojiCache.put(name, new ImageIcon(
                    new ImageIcon(f.getAbsolutePath()).getImage()
                            .getScaledInstance(22, 22, Image.SCALE_SMOOTH)));
        }
    }

    // style builder shorthand
    private SimpleAttributeSet style(Color color, boolean bold, boolean italic, int size) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, color);
        StyleConstants.setBold(s, bold);
        StyleConstants.setItalic(s, italic);
        StyleConstants.setFontFamily(s, "Consolas");
        StyleConstants.setFontSize(s, size);
        return s;
    }

    // panel / label shorthand
    private JPanel panel(Color bg) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setBackground(bg);
        return p;
    }

    private JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainChatWindow w = new MainChatWindow("TestUser");
            w.resetUsers("TestUser");
            w.appendSystemMessage("UI preview mode.");
        });
    }
}