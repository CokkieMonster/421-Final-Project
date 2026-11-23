import java.io.FileOutputStream;
import java.util.Random;

/**
 * BombGame runs one round of the bomb.
 *
 * The player has 1:30 (90 seconds) to solve 6 games. The bomb explodes after
 * 2 failed games.
 *
 * There are two modes:
 *   Normal: the small and middle mazes. The bomb's LED is green.
 *   Hard  : the biggest mazes, and a hand must be in front of the distance
 *           sensor (under 10 cm) at all times. Without the hand the mini-game is
 *           locked while the time keeps running. The bomb's LED is red.
 *
 * Each game is picked at random from 3 stages (light sensor, rotary encoder,
 * joystick). BombGame keeps the current game as a DefusalStrategy and passes
 * every message from the bomb to strategy.handle(...), without caring which
 * game it is. After each game, setStrategy(...) puts in a new random game.
 *
 * Solving all games in time saves the day. The bomb explodes if the time runs
 * out or if too many games are failed.
 *
 * For testing, the window lets you choose which mini-games can come up, and a
 * "Retry" button restarts the current mini-game without any penalty.
 *
 * BombGame also runs the countdown on its own thread (Runnable, as in Lab 9).
 * The main thread (reading the XBee), the countdown thread and the window's
 * buttons all use this object, so its methods are synchronized (as in Lab 9's Vault).
 */
public class BombGame implements Runnable {

    private static final int TOTAL_SECONDS = 90;  // 1:30
    private static final int GAMES_NEEDED = 6;    // games to solve
    private static final int MAX_FAILURES = 2;    // failed games before it explodes
    private static final int HAND_CM = 10;        // Hard: the hand must be closer than this
    private static final int BAD_LIMIT = 3;       // Hard: bad readings in a row = hand is gone

    private DefusalStrategy strategy;             // the current game
    private Random random = new Random();
    private boolean[] allowed = {true, true, true};   // which of stages 1-3 can come up
    private boolean hard = false;                 // true = Hard mode
    private boolean handOn = false;               // Hard: is the hand on the sensor?
    private int badReadings = 0;                  // Hard: bad distance readings in a row
    private int timeLeft = TOTAL_SECONDS;
    private int solved = 0;
    private int failures = 0;
    private boolean running = false;
    private String message = "";
    private SerialPortHandle sph;                 // null in mock mode
    private GameWindow window;
    private Thread t;

    public BombGame(SerialPortHandle sph) {
        this.sph = sph;
        t = new Thread(this);    // create our own thread and start it (Lab 9)
        t.start();
    }

    public void setWindow(GameWindow window) {
        this.window = window;
    }

    // Changes the current game.
    public void setStrategy(DefusalStrategy strategy) {
        this.strategy = strategy;
    }

    // Starts a round with the mini-games ticked in the window (allowed[0] = light ...
    // allowed[2] = joystick) and the mode (hard = true or false).
    public synchronized void start(boolean[] allowed, boolean hard) {
        this.allowed = allowed;
        this.hard = hard;
        handOn = false;
        badReadings = 0;
        timeLeft = TOTAL_SECONDS;
        solved = 0;
        failures = 0;
        running = true;

        send("RESET");
        if (hard) {
            send("LED,R");
            send("MODE,H");                       // the bomb also sends the distance
        } else {
            send("LED,G");
            send("MODE,N");
        }
        nextGame("Go! Solve " + GAMES_NEEDED + " games before the time runs out.");
    }

    // Picks a random game (stage 1-3, only from the allowed ones) and tells the
    // bomb which sensor to send.
    private void nextGame(String text) {
        int stage = random.nextInt(3) + 1;
        while (!allowed[stage - 1]) {
            stage = random.nextInt(3) + 1;     // not ticked in the window: pick again
        }
        setStrategy(makeGame(stage));
        send("STAGE," + strategy.getStage());
        message = text;
        refresh();
    }

    // Creates a new mini-game for the given stage.
    private DefusalStrategy makeGame(int stage) {
        if (stage == 1) {
            return new LightStrategy(random);
        } else if (stage == 2) {
            return new EncoderStrategy(random);
        } else {
            return new JoystickStrategy(random, hard);
        }
    }

    private String modeName() {
        if (hard) {
            return "Hard";
        }
        return "Normal";
    }

    // "Retry" button: restarts the current mini-game from the beginning.
    // No failure is counted and the counters do not change.
    public synchronized void retryGame() {
        if (!running) {
            return;
        }
        setStrategy(makeGame(strategy.getStage()));
        send("STAGE," + strategy.getStage());    // also resets the encoder dial to 0
        message = "Retrying " + strategy.getName() + "...";
        refresh();
    }

    // "Return to menu" button: stops the round without a result and turns the bomb off.
    // Nothing is written to the log.
    public synchronized void quitGame() {
        if (!running) {
            return;
        }
        running = false;
        send("STAGE,0");                          // stop sending sensor values
        send("RESET");                            // LED and buzzer off
    }

    // Called for every line received from the bomb, e.g. "LIGHT,9" or "JOY,512,300".
    public synchronized void onMessage(String line) {
        if (!running) {
            return;                               // no round running: ignore the bomb
        }
        // Split at the first comma only: "JOY,512,300" -> "JOY" and "512,300"
        String[] parts = line.split(",", 2);
        String type = parts[0];
        String value = "";
        if (parts.length > 1) {
            value = parts[1];
        }

        int result;
        try {
            if (type.equals("DIST")) {
                checkHand(Integer.parseInt(value.trim()));
                refresh();
                return;
            }
            // Hard mode without the hand: the game is locked. Only the dial's
            // position is still followed, so it is right when the hand is back.
            if (hard && !handOn && !type.equals("ENC")) {
                return;
            }
            result = strategy.handle(type, value);   // the current game decides
        } catch (NumberFormatException e) {
            System.out.println("Ignored a bad message: " + line);
            return;
        }

        if (result == DefusalStrategy.DONE) {
            solved++;
            send("PHASE");                        // two short beeps
            if (solved >= GAMES_NEEDED) {
                endGame(true, "All " + GAMES_NEEDED + " games solved in time.");
            } else {
                nextGame("Correct! " + (GAMES_NEEDED - solved) + " to go.");
            }
        } else if (result == DefusalStrategy.STRIKE) {
            failures++;
            send("STRIKE");                       // red flash + long beep
            if (failures >= MAX_FAILURES) {
                endGame(false, MAX_FAILURES + " games failed.");
            } else {
                nextGame("Game failed! (" + failures + " of " + MAX_FAILURES + " failures)");
            }
        } else {
            refresh();                            // live values changed
        }
    }

    // Hard mode: checks one distance reading (in cm, -1 = no echo).
    // One or two bad readings are ignored, because the sensor sometimes glitches.
    private void checkHand(int distance) {
        if (distance >= 0 && distance < HAND_CM) {
            handOn = true;
            badReadings = 0;
        } else {
            badReadings++;
            if (badReadings >= BAD_LIMIT) {
                handOn = false;
            }
        }
    }

    // The countdown thread: one tick every second.
    public void run() {
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            tick();
        }
    }

    private synchronized void tick() {
        if (!running) {
            return;
        }
        timeLeft--;
        if (timeLeft <= 0) {
            endGame(false, "Time ran out.");
        } else {
            refresh();
        }
    }

    private void endGame(boolean won, String reason) {
        refresh();                                // show the final time and counters on the bomb
        running = false;
        send("STAGE,0");
        if (won) {
            send("WIN");
            System.out.println("DEFUSED: " + reason);
        } else {
            send("LOSE");
            System.out.println("BOOM: " + reason);
        }
        writeLog(won, reason);
        if (window != null) {
            window.showResult(won, reason + "\n\nMode: " + modeName()
                    + "\nGames solved: " + solved + " of " + GAMES_NEEDED
                    + "\nGames failed: " + failures + " of " + MAX_FAILURES
                    + "\nTime left: " + timeText());
        }
    }

    // Adds one line about the finished round to game_log.txt (FileOutputStream, Java I/O lecture).
    private void writeLog(boolean won, String reason) {
        try {
            FileOutputStream fout = new FileOutputStream("game_log.txt", true);
            String result = "EXPLODED";
            if (won) {
                result = "DEFUSED";
            }
            String s = result + " - "
                     + modeName() + " - " + reason
                     + " - solved " + solved + "/" + GAMES_NEEDED
                     + " - failed " + failures + "/" + MAX_FAILURES
                     + " - time left " + timeText() + "\n";
            fout.write(s.getBytes());
            fout.close();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    // Sends a command to the bomb through the XBee (or prints it in mock mode).
    private void send(String command) {
        if (sph != null) {
            sph.printLine(command);
        } else {
            System.out.println("   (to bomb) " + command);
        }
    }

    private String timeText() {
        int seconds = timeLeft % 60;
        if (seconds < 10) {
            return (timeLeft / 60) + ":0" + seconds;
        }
        return (timeLeft / 60) + ":" + seconds;
    }

    // Sends the current state to the window.
    private void refresh() {
        String text = strategy.getScreen();
        if (hard && !handOn) {
            text = "LOCKED!\n\n"
                 + "Keep a hand in front of the distance sensor\n"
                 + "(closer than " + HAND_CM + " cm) to unlock the game.";
        }
        if (window != null) {
            window.showGame(timeText(), solved, GAMES_NEEDED, failures, MAX_FAILURES,
                    strategy.getName() + "   (" + modeName() + ")",
                    text,
                    message);
        }
    }
}
