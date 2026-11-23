/*
  Bomb Squad - the bomb (Arduino Mega 2560)
  COE 421L Final Project, Fall 2026

  The Arduino does not run the game. It only:
    - reads the sensors and sends what happened to Java through the XBee
    - turns the RGB LED and buzzer on/off when Java tells it to

  Messages sent to Java (one per line):
    LIGHT,350         light sensor value 0-1023, covered = low (games 1 and 3)
    ENC,12            the encoder dial value, when it changes (game 2)
    PRESS             the encoder dial was pressed in (game 2)
    JOY,512,300       joystick x and y (game 3)
    DIST,8            distance in cm, -1 = no echo (Hard mode, in every game)

  Commands received from Java:
    STAGE,n   which game is running (0 = no game)
    MODE,H    Hard mode (also send the distance); MODE,N = Normal mode
    LED,G     LED colour: G, Y, R, B or O (off)
    STRIKE    red flash + long beep
    PHASE     two short beeps (game solved)
    WIN       green + three beeps
    LOSE      red + 2 second beep
    RESET     everything off

  Wiring: docs/wiring.html
*/

#include <SoftwareSerial.h>
#include <string.h>

SoftwareSerial mySerial(10, 11); // RX, TX (connected to the XBee, like Lab 6 and Lab 8)

// LilyPad light sensor (game 1, and game 3 where it lights up the maze)
const int lightPin = A2;

// Rotary encoder (game 2)
const int clkPin = 8;
const int dtPin = 9;
const int swPin = 12;            // the encoder's push button

// Joystick (game 3)
const int xPin = A0;
const int yPin = A1;

// Ultrasonic sensor (Hard mode: a hand must stay in front of it)
const int trigPin = 6;
const int echoPin = 7;

// RGB LED (common anode: LOW = on)
// Our LED board has R and B swapped, so red and blue are switched here.
const int redPin = 4;
const int greenPin = 3;
const int bluePin = 2;

// Active buzzer (HIGH = beep)
const int buzzerPin = 5;

int stage = 0;             // which game Java says is running (0 = no game)
bool hardMode = false;     // true = also send the distance during every game
char ledColor = 'O';       // the colour Java asked for
char line[50];             // one command received from Java
long loopCount = 0;        // counts the loops (one loop is about 50 ms)
int encoderValue = 0;      // the encoder dial value
int lastClk = HIGH;        // last state of the encoder CLK pin
int lastButton = HIGH;     // last state of the encoder button
int buttonWait = 0;        // ignores button bounce for a short time after a press

void setup() {
  Serial.begin(9600);      // USB serial monitor, for testing
  mySerial.begin(9600);    // XBee

  pinMode(clkPin, INPUT_PULLUP);
  pinMode(dtPin, INPUT_PULLUP);
  pinMode(swPin, INPUT_PULLUP);

  pinMode(trigPin, OUTPUT);
  pinMode(echoPin, INPUT);
  pinMode(redPin, OUTPUT);
  pinMode(greenPin, OUTPUT);
  pinMode(bluePin, OUTPUT);
  pinMode(buzzerPin, OUTPUT);

  setColor('O');
  beep(100);
  Serial.println("Bomb ready. You can type commands here, e.g. STAGE,2");
}

void loop() {
  // 1. Commands from Java (through the XBee)
  if (mySerial.available() > 0) {
    int length = mySerial.readBytesUntil('\n', line, sizeof(line) - 1);
    line[length] = '\0';
    handleCommand(line);
  }

  // Commands typed in the Serial Monitor (for testing without Java)
  if (Serial.available() > 0) {
    int length = Serial.readBytesUntil('\n', line, sizeof(line) - 1);
    line[length] = '\0';
    handleCommand(line);
  }

  // 2. Light sensor (game 1, and game 3 for the maze), about 5 times a second (every 4 loops)
  if ((stage == 1 || stage == 3) && loopCount % 4 == 0) {
    int light = analogRead(lightPin);
    mySerial.print("LIGHT,");
    mySerial.println(light);
    Serial.print("LIGHT,");
    Serial.println(light);
  }

  // 3. Joystick (game 3)
  if (stage == 3) {
    int x = analogRead(xPin);
    int y = analogRead(yPin);
    mySerial.print("JOY,");
    mySerial.print(x);
    mySerial.print(",");
    mySerial.println(y);
    Serial.print("JOY,");
    Serial.print(x);
    Serial.print(",");
    Serial.println(y);
  }

  // 4. Ultrasonic sensor (Hard mode, in every game), about 5 times a second (every 4 loops)
  if (hardMode && stage > 0 && loopCount % 4 == 0) {
    int distance = readDistance();
    mySerial.print("DIST,");
    mySerial.println(distance);
    Serial.print("DIST,");
    Serial.println(distance);
  }

  loopCount++;

  // Wait about 50 ms. During game 2 we check the encoder every millisecond
  // while waiting, so no clicks of the dial are missed.
  for (int i = 0; i < 50; i++) {
    if (stage == 2) {
      readEncoder();
    }
    delay(1);
  }
}

// Splits a command like "STAGE,2" with strtok (as in Lab 4) and carries it out.
void handleCommand(char *text) {
  // remove a '\r' at the end if there is one
  int length = strlen(text);
  if (length > 0 && text[length - 1] == '\r') {
    text[length - 1] = '\0';
  }

  Serial.print("Got command: ");
  Serial.println(text);

  char *command = strtok(text, ",");
  char *value = strtok(NULL, ",");
  if (command == NULL) {
    return;
  }

  if (strcmp(command, "STAGE") == 0 && value != NULL) {
    stage = atoi(value);
    if (stage == 2) {
      encoderValue = 0;                          // the dial starts at 0 in game 2
      mySerial.println("ENC,0");
    }
  } else if (strcmp(command, "MODE") == 0 && value != NULL) {
    hardMode = (value[0] == 'H');
  } else if (strcmp(command, "LED") == 0 && value != NULL) {
    ledColor = value[0];
    setColor(ledColor);
  } else if (strcmp(command, "STRIKE") == 0) {
    setColor('R');
    beep(500);
    setColor(ledColor);                          // back to the normal colour
  } else if (strcmp(command, "PHASE") == 0) {
    beep(80);
    delay(80);
    beep(80);
  } else if (strcmp(command, "WIN") == 0) {
    ledColor = 'G';
    setColor('G');
    beep(100);
    delay(100);
    beep(100);
    delay(100);
    beep(400);
  } else if (strcmp(command, "LOSE") == 0) {
    ledColor = 'R';
    setColor('R');
    beep(2000);
  } else if (strcmp(command, "RESET") == 0) {
    stage = 0;
    hardMode = false;
    ledColor = 'O';
    setColor('O');
  }
}

// Reads the rotary encoder. When CLK goes from HIGH to LOW the dial moved one click;
// DT tells us the direction. Also checks the encoder's push button.
void readEncoder() {
  int clk = digitalRead(clkPin);
  if (clk != lastClk && clk == LOW) {
    if (digitalRead(dtPin) != clk) {
      encoderValue++;               // turned clockwise
    } else {
      encoderValue--;               // turned counterclockwise
    }
    mySerial.print("ENC,");
    mySerial.println(encoderValue);
    Serial.print("ENC,");
    Serial.println(encoderValue);
  }
  lastClk = clk;

  if (buttonWait > 0) {
    buttonWait--;                   // still ignoring bounce from the last press
  }
  int button = digitalRead(swPin);
  if (button == LOW && lastButton == HIGH && buttonWait == 0) {
    mySerial.println("PRESS");
    Serial.println("PRESS");
    buttonWait = 300;               // ignore the button for about 300 ms
  }
  lastButton = button;
}

// Measures the distance with the ultrasonic sensor, in cm (-1 if there is no echo).
int readDistance() {
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);      // 10 microsecond pulse starts a measurement
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);

  // Wait at most 3 ms for the echo (about 45 cm). A short wait, so the encoder
  // and joystick are not held up.
  long duration = pulseIn(echoPin, HIGH, 3000);
  if (duration == 0) {
    return -1;
  }
  return duration * 0.034 / 2;      // distance = echo time x 0.034 / 2 (cm)
}

// Sets the RGB LED colour: 'G' green, 'Y' yellow, 'R' red, 'B' blue, 'O' off.
void setColor(char color) {
  digitalWrite(redPin, HIGH);       // HIGH = off (common anode)
  digitalWrite(greenPin, HIGH);
  digitalWrite(bluePin, HIGH);
  if (color == 'R' || color == 'Y') {
    digitalWrite(redPin, LOW);
  }
  if (color == 'G' || color == 'Y') {
    digitalWrite(greenPin, LOW);
  }
  if (color == 'B') {
    digitalWrite(bluePin, LOW);
  }
}

// Beeps the buzzer for the given number of milliseconds.
void beep(int ms) {
  digitalWrite(buzzerPin, HIGH);
  delay(ms);
  digitalWrite(buzzerPin, LOW);
}
