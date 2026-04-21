
/* GUIStyles.java
 
 Shared colors, fonts, and component factory methods for all GUI classes.

 */
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

public class GUIStyles {

    // themes broken up
    public static class Theme {
        public final Color BG_DARK, BG_CARD, BG_PANEL, BG_CHAT;
        public final Color BG_TAB_ACTIVE, BG_TAB_IDLE, BG_USERS;
        public final Color ACCENT, BORDER, BORDER_SOFT;
        public final Color TEXT_PRIMARY, TEXT_MUTED, TEXT_TIMESTAMP;
        public final Color ERROR, SUCCESS, FIELD_BG;
        public final Color STATUS_ON, STATUS_OFF;
        public final String name;

        public Theme(String name,
                Color bgDark, Color bgCard, Color bgPanel, Color bgChat,
                Color bgTabActive, Color bgTabIdle, Color bgUsers,
                Color accent, Color border, Color borderSoft,
                Color textPrimary, Color textMuted, Color textTimestamp,
                Color error, Color success, Color fieldBg,
                Color statusOn, Color statusOff) {
            this.name = name;
            this.BG_DARK = bgDark;
            this.BG_CARD = bgCard;
            this.BG_PANEL = bgPanel;
            this.BG_CHAT = bgChat;
            this.BG_TAB_ACTIVE = bgTabActive;
            this.BG_TAB_IDLE = bgTabIdle;
            this.BG_USERS = bgUsers;
            this.ACCENT = accent;
            this.BORDER = border;
            this.BORDER_SOFT = borderSoft;
            this.TEXT_PRIMARY = textPrimary;
            this.TEXT_MUTED = textMuted;
            this.TEXT_TIMESTAMP = textTimestamp;
            this.ERROR = error;
            this.SUCCESS = success;
            this.FIELD_BG = fieldBg;
            this.STATUS_ON = statusOn;
            this.STATUS_OFF = statusOff;
        }
    }

    // dark theme color pallete
    public static final Theme DARK = new Theme("Dark",
            new Color(18, 18, 18), // BG_DARK
            new Color(30, 30, 30), // BG_CARD
            new Color(30, 30, 30), // BG_PANEL
            new Color(22, 22, 22), // BG_CHAT
            new Color(50, 50, 50), // BG_TAB_ACTIVE
            new Color(35, 35, 35), // BG_TAB_IDLE
            new Color(26, 26, 26), // BG_USERS
            new Color(180, 180, 180), // ACCENT
            new Color(100, 100, 100), // BORDER
            new Color(60, 60, 60), // BORDER_SOFT
            new Color(220, 220, 220), // TEXT_PRIMARY
            new Color(120, 120, 120), // TEXT_MUTED
            new Color(90, 90, 90), // TEXT_TIMESTAMP
            new Color(220, 80, 80), // ERROR
            new Color(100, 200, 120), // SUCCESS
            new Color(40, 40, 40), // FIELD_BG
            new Color(100, 200, 120), // STATUS_ON
            new Color(220, 80, 80) // STATUS_OFF
    );

    // light theme color pallete
    public static final Theme LIGHT = new Theme("Light",
            new Color(240, 240, 240), // BG_DARK
            new Color(255, 255, 255), // BG_CARD
            new Color(250, 250, 250), // BG_PANEL
            new Color(255, 255, 255), // BG_CHAT
            new Color(210, 210, 210), // BG_TAB_ACTIVE
            new Color(230, 230, 230), // BG_TAB_IDLE
            new Color(245, 245, 245), // BG_USERS
            new Color(60, 60, 60), // ACCENT
            new Color(160, 160, 160), // BORDER
            new Color(200, 200, 200), // BORDER_SOFT
            new Color(30, 30, 30), // TEXT_PRIMARY
            new Color(110, 110, 110), // TEXT_MUTED
            new Color(150, 150, 150), // TEXT_TIMESTAMP
            new Color(200, 50, 50), // ERROR
            new Color(50, 160, 80), // SUCCESS
            new Color(235, 235, 235), // FIELD_BG
            new Color(50, 160, 80), // STATUS_ON
            new Color(200, 50, 50) // STATUS_OFF
    );

    // theme default is dark
    public static Theme current = DARK;

    public static void toggle() {
        current = (current == DARK) ? LIGHT : DARK;
    }

    // static color passthroughs so existing code compiles unchanged
    public static Color BG_DARK() {
        return current.BG_DARK;
    }

    public static Color BG_CARD() {
        return current.BG_CARD;
    }

    public static Color BG_PANEL() {
        return current.BG_PANEL;
    }

    public static Color BG_CHAT() {
        return current.BG_CHAT;
    }

    public static Color BG_TAB_ACTIVE() {
        return current.BG_TAB_ACTIVE;
    }

    public static Color BG_TAB_IDLE() {
        return current.BG_TAB_IDLE;
    }

    public static Color BG_USERS() {
        return current.BG_USERS;
    }

    public static Color ACCENT() {
        return current.ACCENT;
    }

    public static Color BORDER() {
        return current.BORDER;
    }

    public static Color BORDER_SOFT() {
        return current.BORDER_SOFT;
    }

    public static Color TEXT_PRIMARY() {
        return current.TEXT_PRIMARY;
    }

    public static Color TEXT_MUTED() {
        return current.TEXT_MUTED;
    }

    public static Color TEXT_TIMESTAMP() {
        return current.TEXT_TIMESTAMP;
    }

    public static Color ERROR() {
        return current.ERROR;
    }

    public static Color SUCCESS() {
        return current.SUCCESS;
    }

    public static Color FIELD_BG() {
        return current.FIELD_BG;
    }

    public static Color STATUS_ON() {
        return current.STATUS_ON;
    }

    public static Color STATUS_OFF() {
        return current.STATUS_OFF;
    }

    // fonts
    public static final Font FONT_TITLE = new Font("Consolas", Font.BOLD, 40);
    public static final Font FONT_HEADER = new Font("Consolas", Font.BOLD, 20);
    public static final Font FONT_LABEL = new Font("Consolas", Font.BOLD, 22);
    public static final Font FONT_BODY = new Font("Consolas", Font.PLAIN, 14);
    public static final Font FONT_SMALL = new Font("Consolas", Font.PLAIN, 12);
    public static final Font FONT_TINY = new Font("Consolas", Font.PLAIN, 11);
    public static final Font FONT_BTN = new Font("Consolas", Font.BOLD, 14);
    public static final Font FONT_TAB = new Font("Consolas", Font.BOLD, 13);

    private GUIStyles() {
    }

    // normal buttons
    public static JButton button(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BTN);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        return btn;
    }

    // small buttons
    public static JButton smallButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_SMALL);
        btn.setBackground(current.BG_PANEL);
        btn.setForeground(current.TEXT_MUTED);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createLineBorder(current.BORDER_SOFT, 1));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // buttons for tabs
    public static JButton tabButton(String text, boolean active) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_TAB);
        btn.setBackground(active ? current.BORDER : current.BG_CARD);
        btn.setForeground(active ? current.BG_DARK : current.TEXT_MUTED);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        return btn;
    }

    public static void styleField(JTextField tf) {
        tf.setFont(FONT_BODY);
        tf.setForeground(current.TEXT_PRIMARY);
        tf.setBackground(current.FIELD_BG);
        tf.setCaretColor(current.ACCENT);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(current.BORDER, 1),
                new EmptyBorder(8, 12, 8, 10)));
        tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        tf.setAlignmentX(Component.CENTER_ALIGNMENT);
    }

    public static JLabel fieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(FONT_LABEL);
        lbl.setForeground(current.TEXT_PRIMARY);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        return lbl;
    }

    public static JLabel errorLabel() {
        JLabel lbl = new JLabel(" ");
        lbl.setFont(FONT_SMALL);
        lbl.setForeground(current.ERROR);
        lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        return lbl;
    }

    public static Image loadImage(String filename) {
        java.net.URL url = GUIStyles.class.getClassLoader().getResource(filename);
        if (url != null)
            return new ImageIcon(url).getImage();
        java.io.File f = new java.io.File(filename);
        if (!f.exists()) {
            System.err.println("[GUIStyles] Image not found: " + f.getAbsolutePath());
            return null;
        }
        return new ImageIcon(f.getAbsolutePath()).getImage();
    }

    public static void paintWatermark(Graphics g, Image logo,
            int panelW, int panelH, float opacity) {
        if (logo == null)
            return;
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        int iw = logo.getWidth(null), ih = logo.getHeight(null);
        if (iw > 0 && ih > 0) {
            double scale = Math.min((double) panelW / iw, (double) panelH / ih);
            int dw = (int) (iw * scale), dh = (int) (ih * scale);
            g2d.drawImage(logo, (panelW - dw) / 2, (panelH - dh) / 2, dw, dh, null);
        }
        g2d.dispose();
    }
}