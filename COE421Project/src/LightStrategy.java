import java.util.Random;

/**
 * Stage 1: light sensor.
 *
 * Each time this game comes up, it randomly asks for one of two things:
 *   LIGHT - shine a light on the sensor (reading 700 or more, e.g. a phone flashlight)
 *   DARK  - cover the sensor with a hand (reading below 80)
 * The reading must stay right for 5 readings in a row (about 1 s).
 * This game has no failure; it only costs time.
 *
 * The bomb sends "LIGHT,value" (0-1023) about 5 times per second.
 */
public class LightStrategy implements DefusalStrategy {

    private static final int LIGHT_LEVEL = 700;    // 700 or more = light
    private static final int DARK_LEVEL = 80;      // below 80 = covered
    private static final int READINGS_NEEDED = 5;

    private boolean wantLight;
    private int goodReadings = 0;
    private int reading = -1;

    public LightStrategy(Random random) {
        wantLight = random.nextInt(2) == 1;    // 1 = light, 0 = dark
    }

    public String getName() {
        return "LIGHT SENSOR";
    }

    public int getStage() {
        return 1;
    }

    public int handle(String type, String value) {
        if (!type.equals("LIGHT")) {
            return CONTINUE;
        }
        reading = Integer.parseInt(value.trim());

        boolean right;
        if (wantLight) {
            right = reading >= LIGHT_LEVEL;
        } else {
            right = reading < DARK_LEVEL;
        }

        if (right) {
            goodReadings++;
        } else {
            goodReadings = 0;
        }
        if (goodReadings >= READINGS_NEEDED) {
            return DONE;
        }
        return CONTINUE;
    }

    public String getScreen() {
        String task;
        if (wantLight) {
            task = "SHINE A LIGHT on the light sensor\n(reading must be 700 or more)";
        } else {
            task = "COVER the light sensor with your hand\n(reading must be below 80)";
        }
        String now = "-";
        if (reading >= 0) {
            now = "" + reading;
        }
        return task + "\n\nSensor reading: " + now
             + "\nHolding: " + goodReadings + " of " + READINGS_NEEDED;
    }
}
