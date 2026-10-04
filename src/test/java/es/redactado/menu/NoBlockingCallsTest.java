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
 *
 * <p><strong>Exactly one package is exempt: {@code menu/examples}.</strong> The profile
 * example's fake service blocks on purpose, because the lesson it teaches is that the
 * framework survives a blocking service: the loader hands it to an executor rather than
 * running it on a dispatch thread. Demonstrating that needs a call that really blocks,
 * and {@link LockSupport#parkNanos(long)} would hide from this scan while blocking just
 * as hard, which would be worse than an exemption.
 *
 * <p>{@link #theExemptionIsExactlyTheExamplesPackage()} pins the list to that one package,
 * and {@link #theExemptionIsNotANoOp()} asserts the exempt sources really do contain a
 * blocking call, so the exemption cannot quietly stop meaning anything.
 */
class NoBlockingCallsTest {

    private static final Path MENU_ROOT = Path.of("src/main/java/es/redactado/menu");

    private static final List<String> FORBIDDEN =
            List.of(".complete()", ".join()", "Thread.sleep(");

    /** The one package allowed to block, because blocking is what it is demonstrating. */
    private static final List<Path> EXEMPT = List.of(MENU_ROOT.resolve("examples"));

    @Test
    @DisplayName("menu sources contain no blocking calls")
    void noBlockingCalls() throws IOException {
        assertThat(MENU_ROOT).isDirectory();

        try (Stream<Path> files = Files.walk(MENU_ROOT)) {
            List<String> offenders =
                    files.filter(path -> path.toString().endsWith(".java"))
                            .filter(path -> !exempt(path))
                            .filter(path -> firstOffence(path).isPresent())
                            .map(path -> path + " -> " + firstOffence(path).orElseThrow())
                            .toList();

            assertThat(offenders)
                    .as(
                            "blocking calls under %s outside %s, none of %s is allowed",
                            MENU_ROOT, EXEMPT, FORBIDDEN)
                    .isEmpty();
        }
    }

    private static boolean exempt(Path file) {
        return EXEMPT.stream().anyMatch(file::startsWith);
    }

    @Test
    @DisplayName("the exemption is exactly the examples package, no more")
    void theExemptionIsExactlyTheExamplesPackage() {
        assertThat(EXEMPT)
                .as("a second exemption needs a reason and a reviewer, not a quiet edit")
                .containsExactly(MENU_ROOT.resolve("examples"));
        assertThat(EXEMPT).allSatisfy(path -> assertThat(path).isDirectory());
    }

    @Test
    @DisplayName("an exempt file really does block, so the exemption is not a no-op")
    void theExemptionIsNotANoOp() throws IOException {
        List<Path> blocking =
                EXEMPT.stream()
                        .flatMap(
                                root -> {
                                    try (Stream<Path> files = Files.walk(root)) {
                                        return files
                                                .filter(path -> path.toString().endsWith(".java"))
                                                .filter(path -> firstOffence(path).isPresent())
                                                .toList()
                                                .stream();
                                    } catch (IOException e) {
                                        throw new IllegalStateException(root.toString(), e);
                                    }
                                })
                        .toList();

        assertThat(blocking)
                .as(
                        "the examples are exempt precisely because they block; if none of them"
                                + " did, the exemption should be deleted instead of kept")
                .isNotEmpty();
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
