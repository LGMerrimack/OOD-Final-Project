import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.*;

public class GameWWTBAM extends JFrame {

    private static final String QUESTIONS_FILE = "wwtbam_questions.txt";
    private static final int[] PRIZE_VALUES = {

            1_000, 5_000, 10_000, 25_000, 50_000,
            100_000, 250_000, 500_000, 750_000, 1_000_000

    };

    private static final char[] CHOICES = { 'A', 'B', 'C', 'D' };

    private static class Question {

        final String prompt;
        final String[] answers;
        final char correctChoice;

        Question(String prompt, String[] answers, char correctChoice) {

            this.prompt = prompt;
            this.answers = answers;
            this.correctChoice = correctChoice;

        }

    }

    private List<Question> gameQuestions;
    private int currentIndex = 0;
    private int winnings = 0;
    private boolean gameActive = false;

    private JPanel topBar, bodyPanel, centerPanel, questionPanel,
            answersPanel, bottomBar, ladderPanel;
    private JLabel titleLabel, prizeLabel, questionLabel, statusLabel;
    private JButton[] answerButtons = new JButton[4];
    private JButton walkAwayButton, themeButton, newGameButton;
    private JPanel[] ladderRows = new JPanel[PRIZE_VALUES.length];
    private JLabel[] ladderNumLabels = new JLabel[PRIZE_VALUES.length];
    private JLabel[] ladderPrizeLabels = new JLabel[PRIZE_VALUES.length];
    private JLabel ladderTitleLabel;

    public static void main(String[] args) {

        SwingUtilities.invokeLater(GameWWTBAM::new);

    }

    public GameWWTBAM() {

        super("Who Wants to Be a Millionaire?");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(920, 660);
        setMinimumSize(new Dimension(780, 520));
        getContentPane().setBackground(GUIStyles.current.BG_DARK);
        setLayout(new BorderLayout());

        topBar = buildTopBar();
        add(topBar, BorderLayout.NORTH);

        bodyPanel = buildBody();
        add(bodyPanel, BorderLayout.CENTER);

        setLocationRelativeTo(null);
        setVisible(true);
        startGame();

    }

    private JPanel buildTopBar() {

        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(GUIStyles.current.BG_PANEL);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, GUIStyles.current.BORDER_SOFT),
                new EmptyBorder(10, 18, 10, 18)));

        titleLabel = new JLabel("Who Wants to Be a Millionaire?");
        titleLabel.setFont(GUIStyles.FONT_HEADER);
        titleLabel.setForeground(GUIStyles.current.ACCENT);

        themeButton = GUIStyles.button(
                GUIStyles.current == GUIStyles.DARK ? "[Light]" : "[Dark]",
                GUIStyles.current.FIELD_BG, GUIStyles.current.TEXT_MUTED);
        themeButton.setPreferredSize(new Dimension(80, 38));
        themeButton.setBorder(BorderFactory.createLineBorder(GUIStyles.current.BORDER_SOFT, 1));
        themeButton.setBorderPainted(true);
        themeButton.setToolTipText("Toggle theme");
        themeButton.addActionListener(e -> toggleTheme());

        newGameButton = GUIStyles.button("New Game",
                GUIStyles.current.BG_TAB_ACTIVE, GUIStyles.current.TEXT_PRIMARY);
        newGameButton.setPreferredSize(new Dimension(110, 38));
        newGameButton.addActionListener(e -> startGame());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setBackground(GUIStyles.current.BG_PANEL);
        right.add(themeButton);
        right.add(newGameButton);

        bar.add(titleLabel, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;

    }

    private JPanel buildBody() {

        JPanel body = new JPanel(new BorderLayout(0, 0));
        body.setBackground(GUIStyles.current.BG_DARK);
        body.add(buildCenterPanel(), BorderLayout.CENTER);
        body.add(buildLadderPanel(), BorderLayout.EAST);
        return body;

    }

    private JPanel buildCenterPanel() {

        centerPanel = new JPanel(new BorderLayout(0, 14));
        centerPanel.setBackground(GUIStyles.current.BG_DARK);
        centerPanel.setBorder(new EmptyBorder(20, 20, 16, 14));

        questionPanel = new JPanel(new BorderLayout(0, 10));
        questionPanel.setBackground(GUIStyles.current.BG_CARD);
        questionPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GUIStyles.current.BORDER_SOFT, 1),
                new EmptyBorder(18, 22, 18, 22)));

        prizeLabel = new JLabel("Question 1 — for $1,000");
        prizeLabel.setFont(GUIStyles.FONT_SMALL);
        prizeLabel.setForeground(GUIStyles.current.ACCENT);
        prizeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        questionLabel = new JLabel();
        questionLabel.setFont(GUIStyles.FONT_BODY);
        questionLabel.setForeground(GUIStyles.current.TEXT_PRIMARY);
        questionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        questionLabel.setVerticalAlignment(SwingConstants.CENTER);

        questionPanel.add(prizeLabel, BorderLayout.NORTH);
        questionPanel.add(questionLabel, BorderLayout.CENTER);

        answersPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        answersPanel.setBackground(GUIStyles.current.BG_DARK);

        String[] prefixes = { "A)  ", "B)  ", "C)  ", "D)  " };

        for (int i = 0; i < 4; i++) {

            final char choice = CHOICES[i];
            answerButtons[i] = GUIStyles.button(prefixes[i],
                    GUIStyles.current.BG_CARD, GUIStyles.current.TEXT_PRIMARY);
            answerButtons[i].setFont(GUIStyles.FONT_BODY);
            answerButtons[i].setHorizontalAlignment(SwingConstants.LEFT);
            answerButtons[i].setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(GUIStyles.current.BORDER_SOFT, 1),
                    new EmptyBorder(12, 16, 12, 16)));
            answerButtons[i].setBorderPainted(true);
            answerButtons[i].addActionListener(e -> handleAnswer(choice));
            answersPanel.add(answerButtons[i]);

        }

        bottomBar = new JPanel(new BorderLayout(10, 0));
        bottomBar.setBackground(GUIStyles.current.BG_DARK);
        bottomBar.setBorder(new EmptyBorder(4, 0, 0, 0));

        statusLabel = new JLabel(" ");
        statusLabel.setFont(GUIStyles.FONT_SMALL);
        statusLabel.setForeground(GUIStyles.current.TEXT_MUTED);

        walkAwayButton = GUIStyles.button("Walk Away",
                GUIStyles.current.BG_TAB_IDLE, GUIStyles.current.TEXT_MUTED);
        walkAwayButton.setEnabled(false);
        walkAwayButton.addActionListener(e -> walkAway());

        bottomBar.add(statusLabel, BorderLayout.CENTER);
        bottomBar.add(walkAwayButton, BorderLayout.EAST);

        centerPanel.add(questionPanel, BorderLayout.NORTH);
        centerPanel.add(answersPanel, BorderLayout.CENTER);
        centerPanel.add(bottomBar, BorderLayout.SOUTH);
        return centerPanel;

    }

    private JPanel buildLadderPanel() {

        ladderPanel = new JPanel();
        ladderPanel.setLayout(new BoxLayout(ladderPanel, BoxLayout.Y_AXIS));
        ladderPanel.setBackground(GUIStyles.current.BG_PANEL);
        ladderPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, GUIStyles.current.BORDER_SOFT),
                new EmptyBorder(16, 10, 16, 10)));
        ladderPanel.setPreferredSize(new Dimension(190, 0));

        ladderTitleLabel = new JLabel("Prize Ladder");
        ladderTitleLabel.setFont(GUIStyles.FONT_SMALL);
        ladderTitleLabel.setForeground(GUIStyles.current.TEXT_MUTED);
        ladderTitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        ladderPanel.add(ladderTitleLabel);
        ladderPanel.add(Box.createVerticalStrut(10));

        for (int i = PRIZE_VALUES.length - 1; i >= 0; i--) {
            JPanel row = new JPanel(new BorderLayout());
            row.setBackground(GUIStyles.current.BG_PANEL);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, GUIStyles.current.BORDER_SOFT),
                    new EmptyBorder(5, 8, 5, 8)));

            JLabel num = new JLabel((i + 1) + ".");
            num.setFont(GUIStyles.FONT_TINY);
            num.setForeground(GUIStyles.current.TEXT_MUTED);
            num.setPreferredSize(new Dimension(26, 20));

            JLabel prize = new JLabel("$" + formatMoney(PRIZE_VALUES[i]));
            prize.setFont(GUIStyles.FONT_SMALL);
            prize.setForeground(GUIStyles.current.TEXT_PRIMARY);

            row.add(num, BorderLayout.WEST);
            row.add(prize, BorderLayout.CENTER);

            ladderRows[i] = row;
            ladderNumLabels[i] = num;
            ladderPrizeLabels[i] = prize;
            ladderPanel.add(row);

        }

        return ladderPanel;

    }

    private void startGame() {

        currentIndex = 0;
        winnings = 0;
        gameActive = false;
        newGameButton.setText("New Game");

        List<Question> all;

        try {

            all = loadQuestions(Paths.get(QUESTIONS_FILE));

        } catch (IOException ex) {

            showError("Could not read \"" + QUESTIONS_FILE + "\".\n" + ex.getMessage(), "File Not Found");
            setQuestionHtml("Could not load questions.<br>Place <b>wwtbam_questions.txt</b> "
            + "next to GameWWTBAM.java and recompile.");

            prizeLabel.setText("Error");
            setAnswersEnabled(false);
            walkAwayButton.setEnabled(false);
            return;

        } catch (IllegalArgumentException ex) {

            showError("Questions file format error:\n" + ex.getMessage(), "Format Error");
            setAnswersEnabled(false);
            walkAwayButton.setEnabled(false);
            return;

        }

        if (all.size() < PRIZE_VALUES.length) {

            showError("Need at least " + PRIZE_VALUES.length
            + " questions in " + QUESTIONS_FILE + ".", "Not Enough Questions");
            setAnswersEnabled(false);
            return;

        }

        Collections.shuffle(all);
        gameQuestions = new ArrayList<>(all.subList(0, PRIZE_VALUES.length));
        gameActive = true;

        statusLabel.setText(" ");
        statusLabel.setForeground(GUIStyles.current.TEXT_MUTED);
        updateLadder();
        showQuestion(0);

    }

    private void showQuestion(int idx) {

        currentIndex = idx;
        Question q = gameQuestions.get(idx);
        int prize = PRIZE_VALUES[idx];

        prizeLabel.setText("Question " + (idx + 1) + "  —  for $" + formatMoney(prize));
        prizeLabel.setForeground(GUIStyles.current.ACCENT);
        setQuestionHtml(escapeHtml(q.prompt));

        String[] prefixes = { "A)  ", "B)  ", "C)  ", "D)  " };

        for (int i = 0; i < 4; i++) {

            answerButtons[i].setText(prefixes[i] + q.answers[i]);
            answerButtons[i].setBackground(GUIStyles.current.BG_CARD);
            answerButtons[i].setForeground(GUIStyles.current.TEXT_PRIMARY);
            answerButtons[i].setEnabled(true);

        }

        walkAwayButton.setEnabled(idx > 0);
        updateLadder();

    }

    private void handleAnswer(char choice) {

        if (!gameActive) return;
        Question q = gameQuestions.get(currentIndex);
        setAnswersEnabled(false);
        walkAwayButton.setEnabled(false);

        int choiceIdx = choice - 'A';
        int correctIdx = q.correctChoice - 'A';

        if (choice == q.correctChoice) {

            answerButtons[choiceIdx].setBackground(GUIStyles.current.SUCCESS);
            answerButtons[choiceIdx].setForeground(GUIStyles.current.BG_DARK);
            winnings = PRIZE_VALUES[currentIndex];

            statusLabel.setText("Correct!  You now have $" + formatMoney(winnings) + ".");
            statusLabel.setForeground(GUIStyles.current.SUCCESS);

            if (currentIndex == PRIZE_VALUES.length - 1) {

                gameActive = false;
                updateLadder();
                timer(1400, e -> showWinScreen());

            } else {

                int next = currentIndex + 1;
                timer(1200, e -> showQuestion(next));

            }

        } else {

            answerButtons[choiceIdx].setBackground(GUIStyles.current.ERROR);
            answerButtons[choiceIdx].setForeground(Color.WHITE);
            answerButtons[correctIdx].setBackground(GUIStyles.current.SUCCESS);
            answerButtons[correctIdx].setForeground(GUIStyles.current.BG_DARK);

            gameActive = false;
            statusLabel.setText("Wrong. The answer was " + q.correctChoice
                    + ". You leave with $0.");
            statusLabel.setForeground(GUIStyles.current.ERROR);
            updateLadder();
            timer(2200, e -> showGameOverScreen(false));

        }

    }

    private void walkAway() {

        if (!gameActive || currentIndex == 0) return;
        gameActive = false;
        setAnswersEnabled(false);
        walkAwayButton.setEnabled(false);
        statusLabel.setText("You walked away with $" + formatMoney(winnings) + ".");
        statusLabel.setForeground(GUIStyles.current.TEXT_MUTED);
        updateLadder();
        timer(1600, e -> showGameOverScreen(true));

    }

    private void showWinScreen() {

        prizeLabel.setText("Congratulations!");
        prizeLabel.setForeground(GUIStyles.current.SUCCESS);
        setQuestionHtml("<b>YOU ARE A MILLIONAIRE!</b><br><br>"
                + "You answered all 10 questions correctly.<br>"
                + "You won <b>$1,000,000</b>!");
        clearAnswerButtons();
        statusLabel.setText("Click New Game to play again.");
        statusLabel.setForeground(GUIStyles.current.TEXT_MUTED);
        newGameButton.setText("Play Again");

    }

    private void showGameOverScreen(boolean walked) {

        prizeLabel.setText(walked ? "You Walked Away!" : "Game Over");
        prizeLabel.setForeground(walked ? GUIStyles.current.TEXT_MUTED : GUIStyles.current.ERROR);
        String msg = walked
                ? "You walked away with <b>$" + formatMoney(winnings) + "</b>."
                : "Game over. You leave with <b>$0</b>.";
        setQuestionHtml(msg + "<br><br>Click <i>New Game</i> to play again.");
        clearAnswerButtons();
        statusLabel.setText(" ");
        newGameButton.setText("New Game");

    }

    private void updateLadder() {

        for (int i = 0; i < PRIZE_VALUES.length; i++) {

            Color rowBg, labelFg;

            if (!gameActive) {

                rowBg = GUIStyles.current.BG_PANEL;
                labelFg = GUIStyles.current.TEXT_MUTED;

            } else if (i == currentIndex) {

                rowBg = GUIStyles.current.ACCENT;
                labelFg = GUIStyles.current.BG_DARK;

            } else if (i < currentIndex) {

                rowBg = GUIStyles.current.BG_PANEL;
                labelFg = GUIStyles.current.SUCCESS;

            } else {

                rowBg = GUIStyles.current.BG_PANEL;
                labelFg = GUIStyles.current.TEXT_PRIMARY;

            }

            ladderRows[i].setBackground(rowBg);
            ladderNumLabels[i].setForeground(GUIStyles.current.TEXT_MUTED);
            ladderPrizeLabels[i].setForeground(labelFg);

        }

        ladderPanel.revalidate();
        ladderPanel.repaint();

    }

    private void toggleTheme() {

        GUIStyles.toggle();
        themeButton.setText(GUIStyles.current == GUIStyles.DARK ? "[Light]" : "[Dark]");
        applyTheme();

    }

    private void applyTheme() {

        GUIStyles.Theme t = GUIStyles.current;

        getContentPane().setBackground(t.BG_DARK);

        topBar.setBackground(t.BG_PANEL);
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, t.BORDER_SOFT),
                new EmptyBorder(10, 18, 10, 18)));
        titleLabel.setForeground(t.ACCENT);
        themeButton.setBackground(t.FIELD_BG);
        themeButton.setForeground(t.TEXT_MUTED);
        themeButton.setBorder(BorderFactory.createLineBorder(t.BORDER_SOFT, 1));
        newGameButton.setBackground(t.BG_TAB_ACTIVE);
        newGameButton.setForeground(t.TEXT_PRIMARY);

        centerPanel.setBackground(t.BG_DARK);
        questionPanel.setBackground(t.BG_CARD);
        questionPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(t.BORDER_SOFT, 1),
                new EmptyBorder(18, 22, 18, 22)));
        prizeLabel.setForeground(t.ACCENT);
        questionLabel.setForeground(t.TEXT_PRIMARY);
        answersPanel.setBackground(t.BG_DARK);
        for (JButton btn : answerButtons) {
            btn.setBackground(t.BG_CARD);
            btn.setForeground(t.TEXT_PRIMARY);
            btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(t.BORDER_SOFT, 1),
                    new EmptyBorder(12, 16, 12, 16)));

        }

        bottomBar.setBackground(t.BG_DARK);
        statusLabel.setForeground(t.TEXT_MUTED);
        walkAwayButton.setBackground(t.BG_TAB_IDLE);
        walkAwayButton.setForeground(t.TEXT_MUTED);

        ladderPanel.setBackground(t.BG_PANEL);
        ladderPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, t.BORDER_SOFT),
                new EmptyBorder(16, 10, 16, 10)));
        ladderTitleLabel.setForeground(t.TEXT_MUTED);

        updateLadder();
        SwingUtilities.updateComponentTreeUI(this);
        repaint();

    }

    private void setQuestionHtml(String inner) {

        questionLabel.setText("<html><body style='width:360px; text-align:center; padding:4px'>"
        + inner + "</body></html>");

    }

    private void setAnswersEnabled(boolean on) {

        for (JButton b : answerButtons) b.setEnabled(on);

    }

    private void clearAnswerButtons() {

        for (JButton b : answerButtons) {

            b.setText("");
            b.setBackground(GUIStyles.current.BG_CARD);
            b.setForeground(GUIStyles.current.TEXT_PRIMARY);
            b.setEnabled(false);

        }

    }

    private void showError(String msg, String title) {

        JOptionPane.showMessageDialog(this, msg, title, JOptionPane.ERROR_MESSAGE);
    
	}

    private static void timer(int ms, ActionListener task) {

        Timer t = new Timer(ms, task);
        t.setRepeats(false);
        t.start();
    
	}

    private static String escapeHtml(String s) {

        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    
	}

    private List<Question> loadQuestions(Path path) throws IOException {

        List<String> lines = Files.readAllLines(path);
        List<Question> questions = new ArrayList<>();
        int index = 0;

        while (index < lines.size()) {

            while (index < lines.size() && lines.get(index).trim().isEmpty()) {

                index++;

            }

            if (index >= lines.size()) break;
            if (index + 5 >= lines.size())
                throw new IllegalArgumentException("Incomplete question block at line " + (index + 1));

            String questionLine = lines.get(index++).trim();
            String aLine = lines.get(index++).trim();
            String bLine = lines.get(index++).trim();
            String cLine = lines.get(index++).trim();
            String dLine = lines.get(index++).trim();
            String answerLine = lines.get(index++).trim();

            if (!questionLine.startsWith("Q:"))
                throw new IllegalArgumentException("Expected Q: at line " + index);
            if (!aLine.startsWith("A:"))
                throw new IllegalArgumentException("Expected A: at line " + (index - 4));
            if (!bLine.startsWith("B:"))
                throw new IllegalArgumentException("Expected B: at line " + (index - 3));
            if (!cLine.startsWith("C:"))
                throw new IllegalArgumentException("Expected C: at line " + (index - 2));
            if (!dLine.startsWith("D:"))
                throw new IllegalArgumentException("Expected D: at line " + (index - 1));
            if (!answerLine.startsWith("ANSWER:"))
                throw new IllegalArgumentException("Expected ANSWER: at line " + index);

            String prompt = questionLine.substring(2).trim();
            String[] answers = {

                    aLine.substring(2).trim(),
                    bLine.substring(2).trim(),
                    cLine.substring(2).trim(),
                    dLine.substring(2).trim()

            };

            String answerValue = answerLine.substring("ANSWER:".length()).trim().toUpperCase(Locale.ROOT);
            if (answerValue.length() != 1)
                throw new IllegalArgumentException("ANSWER must be one letter at line " + index);

            char correctChoice = answerValue.charAt(0);
            if (correctChoice != 'A' && correctChoice != 'B' && correctChoice != 'C' && correctChoice != 'D')
                throw new IllegalArgumentException("ANSWER must be A, B, C, or D at line " + index);

            questions.add(new Question(prompt, answers, correctChoice));

        }

        return questions;

    }

    private String formatMoney(int value) {

        return String.format(Locale.US, "%,d", value);

    }
	
}
