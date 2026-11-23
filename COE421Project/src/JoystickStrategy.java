import java.util.Random;

/**
 * Stage 3: joystick maze.
 *
 * There are 15 mazes. One is picked at random:
 *   Normal mode: one of the first 10 (4-8 moves)
 *   Hard mode  : one of the last 5, the biggest (10-14 moves)
 *
 * One push of the stick = one move. The stick must go back to the middle
 * before the next move counts. There is no time limit between moves.
 *   - All the moves in the right order: the game is solved.
 *   - A wrong move: the game is failed.
 *
 * The maze is drawn as all #s (dark) unless the light sensor reads above 700.
 * Moves still count in the dark.
 *
 * The bomb sends "JOY,x,y" (0-1023, about 512 in the middle) and "LIGHT,n".
 */
public class JoystickStrategy implements DefusalStrategy {

    // Readings measured on our joystick:
    //   UP = y below 200, DOWN = y above 800, LEFT = x below 200, RIGHT = x above 800
    private static final int LOW = 200;
    private static final int HIGH = 800;
    // Both readings between these = the stick is back in the middle
    private static final int MIDDLE_LOW = 350;
    private static final int MIDDLE_HIGH = 670;

    // The light reading must be above this to see the maze.
    private static final int LIGHT_NEEDED = 700;

    // S = start, E = exit, . = path, # = wall
    // Mazes 0-9 are for Normal mode, 10-14 for Hard mode.
    private static final String[][] MAZES = {
        // ----- Normal: small (4 moves) -----
        {"#####",
         "#..E#",
         "#.###",
         "#S###",
         "#####"},
        {"#####",
         "#S..#",
         "###.#",
         "###E#",
         "#####"},
        {"#####",
         "###E#",
         "##..#",
         "#S.##",
         "#####"},
        {"#####",
         "#E###",
         "#.###",
         "#..S#",
         "#####"},
        {"#####",
         "#S###",
         "#...#",
         "###E#",
         "#####"},
        // ----- Normal: bigger (7-8 moves) -----
        {"#######",
         "#S..#E#",
         "###.#.#",
         "###...#",
         "#######"},
        {"#######",
         "#S#####",
         "#.#####",
         "#.#####",
         "#....E#",
         "#######"},
        {"######",
         "#..S##",
         "#.####",
         "#.####",
         "#..E##",
         "######"},
        {"#######",
         "#S.####",
         "##..###",
         "###..##",
         "####.E#",
         "#######"},
        {"######",
         "#E##S#",
         "#.##.#",
         "#....#",
         "######"},
        // ----- Hard (10-14 moves) -----
        {"#######",
         "#E###S#",
         "#.###.#",
         "#.###.#",
         "#.....#",
         "#######"},
        {"#####",
         "#S..#",
         "###.#",
         "#...#",
         "#.###",
         "#..E#",
         "#####"},
        {"#######",
         "#S....#",
         "#####.#",
         "#E#...#",
         "#.#.###",
         "#...###",
         "#######"},
        {"#########",
         "#S.######",
         "##..#####",
         "###..####",
         "####..###",
         "#####..##",
         "######.E#",
         "#########"},
        {"########",
         "#...#E.#",
         "#.#.##.#",
         "#S#.##.#",
         "###....#",
         "########"}
    };

    // The right moves for each maze above, in the same order.
    // U = up, D = down, L = left, R = right
    private static final String[] ANSWERS = {
        // Normal: small
        "UURR", "RRDD", "RURU", "LLUU", "DRRD",
        // Normal: bigger
        "RRDDRRUU", "DDDRRRR", "LLDDDRR", "RDRDRDR", "DDLLLUU",
        // Hard
        "DDDLLLLUUU", "RRDDLLDDRR", "RRRRDDLLDDLLUU", "RDRDRDRDRDR", "UURRDDDRRRUUUL"
    };

    private String[] maze;                     // the drawing of the chosen maze
    private String answer;                     // its right moves
    private int done = 0;                      // how many moves are done
    private int row;                           // where the player is in the maze
    private int col;
    private boolean waitForMiddle = true;      // the stick must start in the middle
    private String lastMove = "-";
    private int light = -1;                    // -1 = no light reading yet (dark)

    public JoystickStrategy(Random random, boolean hard) {
        int number;
        if (hard) {
            number = 10 + random.nextInt(5);     // mazes 10-14
        } else {
            number = random.nextInt(10);         // mazes 0-9
        }
        maze = MAZES[number];
        answer = ANSWERS[number];
        findStart();
    }

    // Finds the S in the maze and puts the player there.
    private void findStart() {
        for (int r = 0; r < maze.length; r++) {
            for (int c = 0; c < maze[r].length(); c++) {
                if (maze[r].charAt(c) == 'S') {
                    row = r;
                    col = c;
                }
            }
        }
    }

    public String getName() {
        return "JOYSTICK MAZE";
    }

    public int getStage() {
        return 3;
    }

    public int handle(String type, String value) {
        if (type.equals("LIGHT")) {
            light = Integer.parseInt(value.trim());
            return CONTINUE;
        }
        if (!type.equals("JOY")) {
            return CONTINUE;
        }

        // value is "x,y"
        String[] parts = value.split(",");
        if (parts.length < 2) {
            return CONTINUE;
        }
        int x = Integer.parseInt(parts[0].trim());
        int y = Integer.parseInt(parts[1].trim());

        // After a move, wait until the stick is back in the middle.
        if (waitForMiddle) {
            if (x > MIDDLE_LOW && x < MIDDLE_HIGH && y > MIDDLE_LOW && y < MIDDLE_HIGH) {
                waitForMiddle = false;
            }
            return CONTINUE;
        }

        String move = readMove(x, y);
        if (move.equals("")) {
            return CONTINUE;                     // not pushed far enough
        }
        waitForMiddle = true;
        lastMove = move;

        // Compare the first letter of the move (U, D, L or R) with the answer.
        if (move.charAt(0) != answer.charAt(done)) {
            return STRIKE;                       // wrong move
        }
        movePlayer(move);
        done++;
        if (done == answer.length()) {
            return DONE;
        }
        return CONTINUE;
    }

    // Turns the joystick readings into "UP", "DOWN", "LEFT", "RIGHT" or "" (no move).
    private String readMove(int x, int y) {
        // On a diagonal push, use the direction pushed further from the middle (512).
        int xDistance = Math.abs(x - 512);
        int yDistance = Math.abs(y - 512);
        if (xDistance >= yDistance) {
            if (x < LOW) {
                return "LEFT";
            }
            if (x > HIGH) {
                return "RIGHT";
            }
        } else {
            if (y < LOW) {
                return "UP";
            }
            if (y > HIGH) {
                return "DOWN";
            }
        }
        return "";
    }

    // Moves the @ one square.
    private void movePlayer(String move) {
        if (move.equals("UP")) {
            row--;
        } else if (move.equals("DOWN")) {
            row++;
        } else if (move.equals("LEFT")) {
            col--;
        } else {
            col++;
        }
    }

    public String getScreen() {
        boolean lit = light > LIGHT_NEEDED;

        String s = "Get from S to E (@ = you). One push = one move;\n"
                 + "let the stick go back to the middle between moves.\n"
                 + "A wrong move fails the game.\n";
        if (lit) {
            s = s + "The maze is lit!\n\n";
        } else {
            s = s + "Too dark! Shine a light on the light sensor (above " + LIGHT_NEEDED + ").\n\n";
        }

        // Draw the maze square by square
        for (int r = 0; r < maze.length; r++) {
            s = s + "        ";
            for (int c = 0; c < maze[r].length(); c++) {
                if (!lit) {
                    s = s + "# ";                // dark: every square looks like a wall
                } else if (r == row && c == col) {
                    s = s + "@ ";                // the player
                } else {
                    s = s + maze[r].charAt(c) + " ";
                }
            }
            s = s + "\n";
        }

        String lightText = "-";
        if (light >= 0) {
            lightText = "" + light;
        }
        return s + "\nLight: " + lightText + "     Moves done: " + done + " of " + answer.length()
                 + "     Last move: " + lastMove;
    }
}
