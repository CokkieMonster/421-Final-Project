import java.util.Random;

/**
 * Stage 2: rotary encoder ("safe lock").
 *
 * A sequence of 4 random numbers from -10 to 10 is shown. For each number in
 * order, the player turns the dial to it and presses the dial in.
 *   - All numbers right: the game is solved.
 *   - A wrong press on the first attempt: the player gets ONE more chance and
 *     must redo all the numbers from the start.
 *   - A wrong press on the second attempt: the game is failed.
 *
 * The bomb resets the dial to 0 when this game starts, then sends "ENC,n" each
 * time the dial moves and "PRESS" when the dial is pressed.
 */
public class EncoderStrategy implements DefusalStrategy {

    private static final int LENGTH = 4;

    private int[] sequence = new int[LENGTH];
    private int index = 0;          // which number of the sequence the player is on
    private int attempt = 1;        // 1 or 2
    private int dial = 0;
    private String note = "";

    public EncoderStrategy(Random random) {
        for (int i = 0; i < LENGTH; i++) {
            sequence[i] = random.nextInt(21) - 10;     // -10 to 10, 0 included
        }
    }

    public String getName() {
        return "ROTARY ENCODER";
    }

    public int getStage() {
        return 2;
    }

    public int handle(String type, String value) {
        if (type.equals("ENC")) {
            dial = Integer.parseInt(value.trim());
            return CONTINUE;
        }
        if (!type.equals("PRESS")) {
            return CONTINUE;
        }

        if (dial == sequence[index]) {
            index++;
            note = "Correct!";
            if (index == LENGTH) {
                return DONE;
            }
        } else if (attempt == 1) {
            attempt = 2;
            index = 0;
            note = "Wrong number! Last chance: start again from the first number.";
        } else {
            return STRIKE;
        }
        return CONTINUE;
    }

    public String getScreen() {
        String numbers = "";
        for (int i = 0; i < LENGTH; i++) {
            if (i < index) {
                numbers = numbers + "  (" + sequence[i] + " OK)";
            } else if (i == index) {
                numbers = numbers + "  >> " + sequence[i] + " <<";
            } else {
                numbers = numbers + "  " + sequence[i];
            }
        }
        return "Turn the dial to each number, then PRESS the dial:\n\n"
             + numbers + "\n\n"
             + "Dial now reads: " + dial + "\n"
             + "Attempt " + attempt + " of 2     " + note;
    }
}
