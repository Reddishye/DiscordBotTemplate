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
 * <p><strong>Exactly one file is exempt:
 * {@code examples/FakeProfileService.java}.</strong> It blocks on purpose, because
 * the lesson it teaches is that the framework survives a blocking service: the
 * loader hands it to an executor rather than running it on a dispatch thread.
 * Demonstrating that needs a call that really blocks, and
 * {@link java.util.concurrent.locks.LockSupport#parkNanos(long)} would hide from
 * this scan while blocking just as hard, which would be worse than an exemption.
 *
 * <p>The exemption is one file rather than the examples package on purpose. A package
 * wide exemption would let any example block from then on without anyone deciding
 * it should, and the examples are the part of this tree most likely to grow.
 *
 * <p>{@link #theExemptionIsExactlyTheOneFileThatSimulatesLatency()} pins the list to
 * that file, and {@link #theExemptionIsNotANoOp()} asserts it really does block, so
 * the exemption cannot quietly stop meaning anything.
 */
class NoBlockingCallsTest {

    private static final Path MENU_ROOT = Path.of("src/main/java/es/redactado/menu");

    private static final List<String> FORBIDDEN =
            List.of(".complete()", ".join()", "Thread.sleep(");

    /**
     * The one file allowed to block.
     *
     * <p>Named in full because the exemption is a file and not a directory, so a
     * prefix match would quietly exempt everything beside it.
     */
    private static final List<Path> EXEMPT =
            List.of(MENU_ROOT.resolve("examples").resolve("FakeProfileService.java"));

    @Test
    @DisplayName("menu sources contain no blocking calls")
    void noBlockingCalls() throws IOException {
        assertThat(MENU_ROOT).isDirectory();

        try (Stream<Path> files = Files.walk(MENU_ROOT)) {
            List<String> offenders =
                    files.filter(path -> path.toString().endsWith(".java"))
                            .filter(path -> !EXEMPT.contains(path))
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

    @Test
    @DisplayName("the exemption is exactly the one file that simulates latency")
    void theExemptionIsExactlyTheOneFileThatSimulatesLatency() {
        assertThat(EXEMPT)
                .as("a second exemption needs a reason and a reviewer, not a quiet edit")
                .containsExactly(MENU_ROOT.resolve("examples").resolve("FakeProfileService.java"));
        assertThat(EXEMPT).allSatisfy(path -> assertThat(path).isRegularFile());
    }

    @Test
    @DisplayName("the exempt file really does block, so the exemption is not a no-op")
    void theExemptionIsNotANoOp() {
        assertThat(EXEMPT).allSatisfy(path -> assertThat(firstOffence(path)).isPresent());
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
