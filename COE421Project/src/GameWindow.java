import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * The game window, drawn as a flat bomb:
 *
 *   [ TIMER ]   [ SOLVED ]   [ FAILED ]      <- 3 small slots
 *   [       current mini-game         ]      <- 1 big slot
 *   message, options and buttons             <- below the bomb
 *
 * The window has 3 modes: "start", "game" and "result". The mode decides which
 * buttons can be seen.
 *
 * Why run()? BombGame calls showGame() and showResult() from its own threads,
 * but a Swing window should only be changed by Swing's thread. So those methods
 * only save the new text, and invokeLater(this) asks Swing to call run(), which
 * puts the text on the screen.
 */
public class GameWindow implements ActionListener, Runnable {

    private static final Color BACKGROUND = new Color(24, 26, 30);
    private static final Color METAL = new Color(70, 75, 82);
    private static final Color AMBER = new Color(255, 196, 50);
    private static final Color GREEN = new Color(80, 235, 120);
    private static final Color RED = new Color(255, 70, 60);
    private static final Color BLUE = new Color(90, 160, 255);

    private BombGame game;
    private JFrame frame = new JFrame("Bomb Squad");

    // On the bomb
    private JLabel timeLabel = new JLabel("-:--", JLabel.CENTER);
    private JLabel solvedLabel = new JLabel("-", JLabel.CENTER);
    private JLabel failedLabel = new JLabel("-", JLabel.CENTER);
    private JLabel gameName = new JLabel("BOMB SQUAD", JLabel.CENTER);
    private JTextArea screen = new JTextArea();

    // Below the bomb
    private JLabel messageLabel = new JLabel(" ", JLabel.CENTER);
    private JPanel options = new JPanel();
    private JCheckBox lightBox = new JCheckBox("Light sensor", true);
    private JCheckBox encoderBox = new JCheckBox("Rotary encoder", true);
    private JCheckBox joystickBox = new JCheckBox("Joystick maze", true);
    private JButton startNormal = new JButton("Start NORMAL  (1:30)");
    private JButton startHard = new JButton("Start HARD  (1:30)");
    private JButton retry = new JButton("Retry this mini-game");
    private JButton menu = new JButton("Return to menu");
    private JButton playAgain = new JButton("Play again");

    // What run() should show
    private String mode = "start";
    private String time = "-:--";
    private String solved = "-";
    private String failed = "-";
    private String title = "";
    private String text = "";
    private String message = " ";
    private Color titleColor = AMBER;

    public GameWindow(BombGame game) {
        this.game = game;

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(BACKGROUND);
        bottom.add(messageLabel, BorderLayout.NORTH);
        bottom.add(options, BorderLayout.CENTER);
        bottom.add(makeButtons(), BorderLayout.SOUTH);
        fillOptions();

        messageLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        messageLabel.setForeground(Color.WHITE);

        frame.setLayout(new BorderLayout());
        frame.add(makeBomb(), BorderLayout.CENTER);
        frame.add(bottom, BorderLayout.SOUTH);
        frame.setSize(1100, 820);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        showStart();
    }

    // ---------- building the window ----------

    // The bomb: wires and 3 small slots on top, the big game slot below.
    private JPanel makeBomb() {
        JPanel smallSlots = new JPanel(new GridLayout(1, 3, 18, 0));
        smallSlots.setBackground(METAL);
        smallSlots.add(makeSlot("TIMER", timeLabel, RED));
        smallSlots.add(makeSlot("SOLVED", solvedLabel, GREEN));
        smallSlots.add(makeSlot("FAILED", failedLabel, RED));

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setBackground(METAL);
        top.add(makeWires(), BorderLayout.NORTH);
        top.add(smallSlots, BorderLayout.CENTER);

        screen.setEditable(false);
        screen.setFont(new Font(Font.MONOSPACED, Font.BOLD, 19));
        screen.setBackground(Color.BLACK);
        screen.setForeground(GREEN);
        screen.setBorder(BorderFactory.createEmptyBorder(10, 24, 10, 24));

        styleName(gameName);
        JPanel gameSlot = new JPanel(new BorderLayout());
        gameSlot.add(gameName, BorderLayout.NORTH);
        gameSlot.add(screen, BorderLayout.CENTER);

        JPanel bomb = new JPanel(new BorderLayout(0, 18));
        bomb.setBackground(METAL);
        bomb.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        bomb.add(top, BorderLayout.NORTH);
        bomb.add(gameSlot, BorderLayout.CENTER);
        return bomb;
    }

    // One small slot: a yellow name on top and a big value below it.
    private JPanel makeSlot(String name, JLabel value, Color color) {
        JLabel nameLabel = new JLabel(name, JLabel.CENTER);
        styleName(nameLabel);
        value.setFont(new Font(Font.MONOSPACED, Font.BOLD, 48));
        value.setForeground(color);

        JPanel slot = new JPanel(new BorderLayout());
        slot.setBackground(Color.BLACK);
        slot.add(nameLabel, BorderLayout.NORTH);
        slot.add(value, BorderLayout.CENTER);
        return slot;
    }

    // Dark text on a yellow bar.
    private void styleName(JLabel label) {
        label.setOpaque(true);          // needed for the background colour to show
        label.setBackground(AMBER);
        label.setForeground(Color.BLACK);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
    }

    // Coloured bars that look like wires (only for decoration).
    private JPanel makeWires() {
        JPanel wires = new JPanel(new GridLayout(1, 5, 14, 0));
        wires.setBackground(METAL);
        Color[] colors = {RED, BLUE, AMBER, GREEN, RED};
        for (int i = 0; i < colors.length; i++) {
            JPanel wire = new JPanel();
            wire.setBackground(colors[i]);
            wires.add(wire);
        }
        return wires;
    }

    // Start screen options: which mini-games can come up.
    private void fillOptions() {
        options.add(new JLabel("Include:"));
        options.add(lightBox);
        options.add(encoderBox);
        options.add(joystickBox);
    }

    private JPanel makeButtons() {
        JPanel buttons = new JPanel();
        buttons.setBackground(BACKGROUND);
        JButton[] all = {startNormal, startHard, retry, menu, playAgain};
        for (int i = 0; i < all.length; i++) {
            all[i].setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            all[i].addActionListener(this);      // a click calls actionPerformed()
            buttons.add(all[i]);
        }
        return buttons;
    }

    // ---------- called by BombGame: save the text, then ask Swing to show it ----------

    public synchronized void showStart() {
        mode = "start";
        time = "-:--";
        solved = "-";
        failed = "-";
        title = "BOMB SQUAD";
        titleColor = AMBER;
        text = "Solve 6 games in 1:30 to defuse the bomb.\n"
             + "It explodes if the time runs out or after 2 failed games.\n\n"
             + "NORMAL (green LED): mazes of 4 to 8 moves\n\n"
             + "HARD (red LED): mazes of 10 to 14 moves, and one hand must stay\n"
             + "closer than 10 cm to the distance sensor AT ALL TIMES.\n"
             + "Without the hand the game is locked, but the time keeps running.\n\n"
             + "Tick only one mini-game to test it alone.";
        message = " ";
        SwingUtilities.invokeLater(this);
    }

    public synchronized void showGame(String time, int solved, int needed, int failures,
                                      int maxFailures, String title, String text, String message) {
        mode = "game";
        this.time = time;
        this.solved = solved + "/" + needed;
        this.failed = failures + "/" + maxFailures;
        this.title = title;
        this.text = text;
        this.message = message;
        titleColor = AMBER;
        SwingUtilities.invokeLater(this);
    }

    public synchronized void showResult(boolean won, String text) {
        mode = "result";
        if (won) {
            title = "BOMB DEFUSED! YOU SAVED THE DAY!";
            titleColor = GREEN;
        } else {
            title = "BOOM! THE BOMB EXPLODED!";
            titleColor = RED;
        }
        this.text = text;
        message = " ";
        SwingUtilities.invokeLater(this);
    }

    // ---------- called by Swing: put the saved text on the screen ----------

    public synchronized void run() {
        timeLabel.setText(time);
        solvedLabel.setText(solved);
        failedLabel.setText(failed);
        gameName.setText(title);
        gameName.setBackground(titleColor);
        screen.setText(text);
        messageLabel.setText(message);

        // Show only the parts that belong to this mode
        boolean start = mode.equals("start");
        boolean playing = mode.equals("game");
        boolean result = mode.equals("result");
        options.setVisible(start);
        startNormal.setVisible(start);
        startHard.setVisible(start);
        retry.setVisible(playing);
        menu.setVisible(playing);
        playAgain.setVisible(result);
    }

    // ---------- buttons ----------

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == startNormal) {
            startRound(false);
        } else if (e.getSource() == startHard) {
            startRound(true);
        } else if (e.getSource() == retry) {
            game.retryGame();
        } else if (e.getSource() == menu) {
            game.quitGame();                      // stop the round, bomb off
            showStart();
        } else if (e.getSource() == playAgain) {
            showStart();
        }
    }

    // Starts a round with the ticked mini-games.
    private void startRound(boolean hard) {
        boolean[] allowed = new boolean[3];
        allowed[0] = lightBox.isSelected();
        allowed[1] = encoderBox.isSelected();
        allowed[2] = joystickBox.isSelected();
        if (!allowed[0] && !allowed[1] && !allowed[2]) {
            messageLabel.setText("Tick at least one mini-game first.");
            return;
        }
        game.start(allowed, hard);
    }
}
