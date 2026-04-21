
/* LoginWindow.java
 
 View layer — Login / Register screen
 
 Design Patterns:
    Singleton - only one instance at a time
    Observer - LoginListener called on success
 
  Opens MainChatWindow, then hands off to ChatClient on a background thread to connect to CServer
 */
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.HashMap;
import java.util.regex.*;

public class LoginWindow extends JFrame implements ActionListener {

    // singleton
    private static LoginWindow instance;

    public static LoginWindow getInstance() {
        if (instance == null)
            instance = new LoginWindow();
        return instance;
    }

    // observer
    public interface LoginListener {
        void onLoginSuccess(String username);
    }

    private LoginListener loginListener;

    public void setLoginListener(LoginListener l) {
        loginListener = l;
    }

    // validation
    private static final Pattern USER_RE = Pattern.compile("^\\w{3,20}$");
    private static final Pattern PASS_RE = Pattern.compile(
            "^(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,}$");

    // ui
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton submitButton;
    private JLabel statusLabel, usernameError, passwordError;
    private JButton loginTab, newTab;
    private boolean isNewUser = false;
    private Image logo;

    // constructor
    private LoginWindow() {
        logo = GUIStyles.loadImage("secureLock.png");
        if (logo != null)
            setIconImage(logo);

        setTitle("S.H.A.C. Waffle Ultra — Login");
        setSize(480, 640);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        setLayout(new BorderLayout());
        getContentPane().setBackground(GUIStyles.current.BG_DARK);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCard(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // header with watermark
    private JPanel buildHeader() {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                GUIStyles.paintWatermark(g, logo, getWidth(), getHeight(), 0.15f);
            }
        };
        p.setBackground(GUIStyles.current.BG_DARK);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(36, 0, 16, 0));
        p.setPreferredSize(new Dimension(480, 110));

        JLabel title = new JLabel("S.H.A.C. Waffle Ultra", SwingConstants.CENTER);
        title.setFont(GUIStyles.FONT_TITLE);
        title.setForeground(GUIStyles.current.ACCENT);
        title.setAlignmentX(CENTER_ALIGNMENT);
        title.setOpaque(false);

        p.add(Box.createVerticalStrut(8));
        p.add(title);
        return p;
    }

    // form card
    private JPanel buildCard() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(GUIStyles.current.BG_DARK);

        JPanel card = new JPanel();
        card.setBackground(GUIStyles.current.BG_CARD);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GUIStyles.current.BORDER, 2),
                new EmptyBorder(24, 36, 24, 36)));
        card.setPreferredSize(new Dimension(380, 370));

        // login/new user toggle button
        JPanel tabRow = new JPanel(new GridLayout(1, 2));
        tabRow.setBackground(GUIStyles.current.BG_CARD);
        tabRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        tabRow.setBorder(BorderFactory.createLineBorder(GUIStyles.current.BORDER, 1));
        loginTab = GUIStyles.tabButton("Login", true);
        newTab = GUIStyles.tabButton("New User", false);
        loginTab.addActionListener(e -> setMode(false));
        newTab.addActionListener(e -> setMode(true));
        tabRow.add(loginTab);
        tabRow.add(newTab);
        card.add(tabRow);
        card.add(Box.createVerticalStrut(18));

        // username
        card.add(GUIStyles.fieldLabel("Username"));
        card.add(Box.createVerticalStrut(4));
        usernameField = new JTextField();
        GUIStyles.styleField(usernameField);
        card.add(usernameField);
        usernameError = GUIStyles.errorLabel();
        card.add(usernameError);
        card.add(Box.createVerticalStrut(14));

        // password
        card.add(GUIStyles.fieldLabel("Password"));
        card.add(Box.createVerticalStrut(4));
        passwordField = new JPasswordField();
        GUIStyles.styleField(passwordField);
        card.add(passwordField);
        passwordError = GUIStyles.errorLabel();
        card.add(passwordError);
        card.add(Box.createVerticalStrut(18));

        // status
        statusLabel = new JLabel(" ");
        statusLabel.setFont(GUIStyles.FONT_SMALL);
        statusLabel.setForeground(GUIStyles.current.TEXT_MUTED);
        statusLabel.setAlignmentX(CENTER_ALIGNMENT);
        card.add(statusLabel);
        card.add(Box.createVerticalStrut(10));

        // buttons
        submitButton = GUIStyles.button("Login", GUIStyles.current.BORDER, GUIStyles.current.BG_DARK);
        JButton exitBtn = GUIStyles.button("Exit", GUIStyles.current.ACCENT, GUIStyles.current.BG_DARK);
        submitButton.addActionListener(this);
        exitBtn.addActionListener(this);
        getRootPane().setDefaultButton(submitButton);

        JPanel btnRow = new JPanel(new GridLayout(1, 2, 12, 0));
        btnRow.setBackground(GUIStyles.current.BG_CARD);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnRow.add(submitButton);
        btnRow.add(exitBtn);
        card.add(btnRow);

        wrapper.add(card);
        return wrapper;
    }

    private JPanel buildFooter() {
        JPanel p = new JPanel();
        p.setBackground(GUIStyles.current.BG_DARK);
        p.setBorder(new EmptyBorder(0, 0, 18, 0));
        JLabel lbl = new JLabel("S.H.A.C. Waffle Ultra");
        lbl.setFont(GUIStyles.FONT_BODY);
        lbl.setForeground(GUIStyles.current.TEXT_MUTED);
        p.add(lbl);
        return p;
    }

    // toggle between register and login
    private void setMode(boolean newUser) {
        isNewUser = newUser;
        submitButton.setText(newUser ? "Register" : "Login");
        loginTab.setBackground(newUser ? GUIStyles.current.BG_CARD : GUIStyles.current.BORDER);
        loginTab.setForeground(newUser ? GUIStyles.current.TEXT_MUTED : GUIStyles.current.BG_DARK);
        newTab.setBackground(newUser ? GUIStyles.current.BORDER : GUIStyles.current.BG_CARD);
        newTab.setForeground(newUser ? GUIStyles.current.BG_DARK : GUIStyles.current.TEXT_MUTED);
        statusLabel.setText(" ");
        usernameError.setText(" ");
        passwordError.setText(" ");
    }

    // action handler
    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (cmd.equals("Exit")) {
            System.exit(0);
            return;
        }

        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (!validate(username, password))
            return;

        submitButton.setEnabled(false);
        statusLabel.setForeground(GUIStyles.current.ACCENT);
        statusLabel.setText(isNewUser ? "Registering..." : "Verifying...");

        // open chat window then connect on a background thread
        statusLabel.setText("Connecting...");

        MainChatWindow chat = new MainChatWindow(username);
        new Thread(() -> {
            ChatClient client = new ChatClient(username, password, isNewUser, chat);
            chat.setChatClient(client);
            client.connect();
        }, "ChatClient-Connect").start();

        dispose();
        instance = null;
    }

    // validation helpers
    private boolean validate(String user, String pass) {
        boolean ok = true;
        if (!USER_RE.matcher(user).matches()) {
            usernameError.setText("3–20 letters, digits, or underscores only");
            ok = false;
        } else {
            usernameError.setText(" ");
        }
        if (!PASS_RE.matcher(pass).matches()) {
            passwordError.setText("8+ chars, include a digit & special character");
            ok = false;
        } else {
            passwordError.setText(" ");
        }
        return ok;
    }

    // good to remove (?)
    private boolean checkUsersFile(String username, String password) {
        HashMap<String, String> users = new HashMap<>();
        File file = new File("users.txt");
        if (!file.exists()) {
            statusLabel.setForeground(GUIStyles.current.ERROR);
            statusLabel.setText("users.txt not found");
            return false;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;
                String[] p = line.split(":", 2);
                if (p.length == 2)
                    users.put(p[0].trim(), p[1].trim());
            }
        } catch (IOException ex) {
            statusLabel.setForeground(GUIStyles.current.ERROR);
            statusLabel.setText("Error reading users.txt");
            return false;
        }
        if (!users.containsKey(username)) {
            statusLabel.setForeground(GUIStyles.current.ERROR);
            statusLabel.setText("Username not found");
            usernameError.setText("No account with that username");
            return false;
        }
        if (!users.get(username).equals(password)) {
            statusLabel.setForeground(GUIStyles.current.ERROR);
            statusLabel.setText("Incorrect password");
            passwordError.setText("Password does not match");
            return false;
        }
        return true;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(LoginWindow::getInstance);
    }
}