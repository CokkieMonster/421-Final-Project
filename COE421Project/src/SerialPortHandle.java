import jssc.SerialPort;
import jssc.SerialPortException;

/**
 * SerialPortHandle — the class from Lab 8 (Java and Arduino communication).
 * It opens the XBee Explorer's serial port with the jssc library and lets us
 * read and send one line of text at a time.
 */
public class SerialPortHandle {

    // Serial port used for communication and its path.
    SerialPort sp;
    String path;

    // Initialize the serial port.
    public SerialPortHandle(String path) {
        this.sp = new SerialPort(path);
        this.path = path;
        try {
            sp.openPort();
            // Set baud rate to 9600, 8 data bits, 1 stop bit, no parity.
            sp.setParams(9600, 8, 1, 0);
            // Clear any data already in the buffer.
            while (sp.getInputBufferBytesCount() > 0) {
                sp.readBytes();
            }
        } catch (SerialPortException e) {
            // Print the error if the port cannot be opened.
            e.printStackTrace();
        }
    }

    // Read a line of data received from the serial port.
    public String readLine() {
        StringBuilder string = new StringBuilder();
        while (true) {
            try {
                // Read one byte and check if data was received.
                byte[] buffer = sp.readBytes(1);
                if (buffer == null || buffer.length == 0) {
                    continue;
                }
                char c = (char) buffer[0];
                // A newline or carriage return marks the end of a line.
                if (c == '\n' || c == '\r') {
                    if (string.length() == 0) {
                        continue; // Skip empty lines.
                    } else {
                        break;
                    }
                }
                // Add the character to the received message.
                string.append(c);
            } catch (SerialPortException e) {
                e.printStackTrace();
                break;
            }
        }
        return string.toString().trim();
    }

    // Send a string to the serial port.
    public void printLine(String s) {
        byte byteArray[] = s.getBytes();
        try {
            sp.writeBytes(byteArray);
            // Send a newline after the message.
            sp.writeByte((byte) '\n');
        } catch (SerialPortException e1) {
            e1.printStackTrace();
        }
    }
}
