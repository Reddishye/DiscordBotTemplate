package es.redactado;

import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.Normalizer;

public class LogbackOutputStream extends ByteArrayOutputStream {
    private final String lineSeparator = System.lineSeparator();
    private final Logger logger;
    private final PrintStream originalOut;

    public LogbackOutputStream(Logger logger, PrintStream originalOut) {
        super();
        this.logger = logger;
        this.originalOut = originalOut;
    }

    @Override
    public void flush() throws IOException {
        String record = this.toString();
        super.reset();

        if (!record.isEmpty() && !record.equals(lineSeparator)) {
            String recordWithoutColor =
                    Normalizer.normalize(record, Normalizer.Form.NFD)
                            .replaceAll("\u001B\\[[;\\d]*m", "");
            if (recordWithoutColor.startsWith("[Hibernate]")) {
                String[] lines = record.split(lineSeparator);
                originalOut.print("--- [ Database Altered ] ---" + lineSeparator);
                for (int i = 1; i < lines.length; i++) {
                    originalOut.print("| " + lines[i] + lineSeparator);
                }
                originalOut.print("--- [ End of Database Alteration] ---" + lineSeparator);
                return;
            }

            // Detect if already parsed by logback (starts with XX:XX:XX.XXX, being X a digit)
            if (record.charAt(0) >= '0' && record.charAt(0) <= '9') {
                originalOut.print(record);
            } else {
                // Log the record using logback, removing the additional line separator
                // at the end of the record
                String logMessage =
                        record.endsWith(lineSeparator)
                                ? record.substring(0, record.length() - lineSeparator.length())
                                : record;
                logger.info(logMessage);
            }
        }
    }

    public static void redirectSystemOutToLogger() {
        org.slf4j.Logger logger = (org.slf4j.Logger) LoggerFactory.getLogger("System.out");
        PrintStream originalOut = System.out;

        PrintStream loggingPrintStream =
                new PrintStream(
                        new LogbackOutputStream(
                                (ch.qos.logback.classic.Logger) logger, originalOut),
                        true);
        System.setOut(loggingPrintStream);
    }
}
