package es.redactado.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fails the build if any Java source contains a non-ASCII character.
 *
 * <p>Two rules depend on this. Emoji must be written as code points so they read the
 * same everywhere, and identifiers, comments, and log messages must stay in English.
 * Both are easy to violate by accident and hard to review by eye, since a stray
 * emoji looks identical in a diff.
 */
class AsciiSourcesTest {

    private static final int ASCII_LIMIT = 0x7F;
    private static final List<Path> ROOTS =
            List.of(Path.of("src/main/java"), Path.of("src/test/java"));

    @Test
    @DisplayName("every java source is pure ASCII")
    void sourcesAreAscii() throws IOException {
        for (Path root : ROOTS) {
            assertThat(root).isDirectory();
        }

        List<String> offenders = new ArrayList<>();
        int scanned = 0;

        for (Path root : ROOTS) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    scanned++;
                    firstOffender(file).ifPresent(offenders::add);
                }
            }
        }

        // Guards against the rule passing because nothing was read.
        assertThat(scanned).as("java files scanned").isGreaterThan(0);
        assertThat(offenders).as("non-ASCII characters in sources").isEmpty();
    }

    @Test
    @DisplayName("the detector finds a non-ASCII character when one is present")
    void detectorIsNotVacuous() throws IOException {
        Path probe = Files.createTempFile("AsciiProbe", ".java");
        try {
            String dash = Character.toString(0x2014);
            Files.writeString(
                    probe,
                    "class Probe { String s = \"" + dash + "\"; }\n",
                    StandardCharsets.UTF_8);

            assertThat(firstOffender(probe)).isPresent();
        } finally {
            Files.deleteIfExists(probe);
        }
    }

    private static Optional<String> firstOffender(Path file) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (int c = 0; c < line.length(); c++) {
                    if (line.charAt(c) > ASCII_LIMIT) {
                        return Optional.of(
                                "%s:%d column %d is U+%04X"
                                        .formatted(file, i + 1, c + 1, (int) line.charAt(c)));
                    }
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }
}
