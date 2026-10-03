package es.redactado.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fails the build if a blocking call appears anywhere in the menu package.
 *
 * <p>Menu code runs on a JDA event thread for the acknowledgement and on the menu
 * executor afterwards. A handler that blocks a JDA thread stalls every other event
 * that thread is responsible for, which in a sharded bot is a whole shard, so a
 * blocking call in this package is a defect rather than a style choice.
 *
 * <p>The scan covers main sources only. {@code .get()} is deliberately not
 * forbidden, because {@link java.util.Optional#get} and {@link java.util.Map#get}
 * are legitimate. {@code awaitTermination} is allowed for the same reason: the
 * bounded wait on shutdown is the point.
 */
class NoBlockingCallsTest {

    private static final Path MENU_ROOT = Path.of("src/main/java/es/redactado/menu");

    private static final List<String> FORBIDDEN =
            List.of(".complete()", ".join()", "Thread.sleep(");

    @Test
    @DisplayName("menu sources contain no blocking calls")
    void noBlockingCalls() throws IOException {
        assertThat(MENU_ROOT).isDirectory();

        try (Stream<Path> files = Files.walk(MENU_ROOT)) {
            List<String> offenders =
                    files.filter(path -> path.toString().endsWith(".java"))
                            .filter(path -> firstOffence(path).isPresent())
                            .map(path -> path + " -> " + firstOffence(path).orElseThrow())
                            .toList();

            assertThat(offenders)
                    .as("blocking calls under %s, none of %s is allowed", MENU_ROOT, FORBIDDEN)
                    .isEmpty();
        }
    }

    private static Optional<String> firstOffence(Path file) {
        String source;
        try {
            source = Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
        return FORBIDDEN.stream().filter(source::contains).findFirst();
    }
}
