/**
 * Strategy interface: each mini-game of the bomb is one strategy.
 * BombGame keeps the current strategy and swaps it for a new random one
 * after every game.
 */
public interface DefusalStrategy {

    // Possible results of handle()
    public static final int CONTINUE = 0;   // nothing decided yet
    public static final int STRIKE = 1;     // the players failed this game
    public static final int DONE = 2;       // the players solved this game

    // Name of the game, shown in the window.
    public String getName();

    // Stage number sent to the bomb ("STAGE,n") so it sends the right sensor:
    // 1 = light sensor, 2 = rotary encoder, 3 = joystick.
    public int getStage();

    // Reacts to one message from the bomb.
    // type = "LIGHT", "ENC", "PRESS" or "JOY";
    // value = the rest of the message, e.g. "12" or "512,300".
    // Returns CONTINUE, STRIKE or DONE.
    public int handle(String type, String value);

    // Instructions and live status for this game, shown in the window.
    public String getScreen();
}
