import java.util.Scanner;

/**
 * Bomb Squad — the Java game controller (COE 421L, Fall 2026).
 *
 * Opens the game window. The player has 1:30 to solve 6 random
 * mini-games. The bomb explodes if the time runs out or 2 games are failed.
 *
 * Real game: set PORT to the XBee Explorer's port (as in Lab 8) and run.
 * Practice without hardware: set PORT to "MOCK"; the window still works, and
 * you type the bomb's messages in the console.
 */
public class Main {

    // e.g. "COM10" on Windows or "/dev/cu.usbserial-A601EREF" on Mac.
    public static final String PORT = "/dev/cu.usbserial-D30DNMOF";

    public static void main(String[] args) throws InterruptedException {
        if (PORT.equals("MOCK")) {
            playWithoutHardware();
            return;
        }

        // ---- Real game (same steps as the Lab 8 driver) ----
        SerialPortHandle sph = new SerialPortHandle(PORT);
        BombGame game = new BombGame(sph);
        GameWindow window = new GameWindow(game);
        game.setWindow(window);

        // Read every line the bomb sends and give it to the game
        while (true) {
            String line = sph.readLine();
            game.onMessage(line);
        }
    }

    // Mock mode: what you type in the console is treated as if the bomb had sent it.
    private static void playWithoutHardware() throws InterruptedException {
        Scanner input = new Scanner(System.in);
        BombGame game = new BombGame(null);
        GameWindow window = new GameWindow(game);
        game.setWindow(window);
        System.out.println("MOCK MODE. Press Start in the window, then type:");
        System.out.println("  light | dark | enc 5 | press | up | down | left | right | close | far");
        System.out.println("  or a raw message such as ENC,-3");

        while (true) {
            String line = input.nextLine().trim();
            String[] words = line.split(" ");

            if (line.equals("light")) {
                sendMany(game, "LIGHT,900", 5);          // a flashlight on the sensor
            } else if (line.equals("dark")) {
                sendMany(game, "LIGHT,20", 5);           // a hand over the sensor
            } else if (words[0].equals("enc") && words.length > 1) {
                game.onMessage("ENC," + words[1]);       // the dial now reads this number
            } else if (line.equals("press")) {
                game.onMessage("PRESS");                 // press the dial in
            } else if (line.equals("up")) {
                pushJoystick(game, "JOY,512,0");
            } else if (line.equals("down")) {
                pushJoystick(game, "JOY,512,1023");
            } else if (line.equals("left")) {
                pushJoystick(game, "JOY,0,512");
            } else if (line.equals("right")) {
                pushJoystick(game, "JOY,1023,512");
            } else if (line.equals("close")) {
                sendMany(game, "DIST,4", 3);             // Hard mode: hand 4 cm away
            } else if (line.equals("far")) {
                sendMany(game, "DIST,30", 3);            // Hard mode: hand taken away
            } else if (line.contains(",")) {
                game.onMessage(line);                    // a raw message
            } else {
                System.out.println("Unknown command.");
            }
        }
    }

    // Gives the game the same message several times (like a sensor that keeps sending).
    private static void sendMany(BombGame game, String message, int times) {
        for (int i = 0; i < times; i++) {
            game.onMessage(message);
        }
    }

    // One push of the joystick: middle, pushed, back to the middle.
    private static void pushJoystick(BombGame game, String push) {
        game.onMessage("JOY,512,512");
        game.onMessage(push);
        game.onMessage("JOY,512,512");
    }
}
