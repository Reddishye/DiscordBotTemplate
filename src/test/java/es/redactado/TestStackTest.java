package es.redactado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves the test stack works on the pinned toolchain: JUnit 5 runs the test,
 * Mockito mocks an interface, and AssertJ asserts on the mock. Every later suite
 * depends on this one being green.
 */
class TestStackTest {

    /** A plain interface, so the mock exercises interface mocking only. */
    interface Greeter {
        String greet(String name);
    }

    @Test
    @DisplayName("mocks an interface and returns a stubbed value")
    void mocksInterface() {
        Greeter greeter = mock(Greeter.class);
        when(greeter.greet("world")).thenReturn("hello world");

        assertThat(greeter.greet("world")).isEqualTo("hello world");
    }

    @Test
    @DisplayName("verifies an interaction on a mock")
    void verifiesInteraction() {
        @SuppressWarnings("unchecked")
        Runnable task = mock(Runnable.class);

        task.run();

        verify(task).run();
    }

    @Test
    @DisplayName("runs work on a virtual thread")
    void runsOnVirtualThread() throws InterruptedException {
        AtomicReference<Throwable> thrown = new AtomicReference<>();

        Thread thread =
                Thread.ofVirtual()
                        .unstarted(
                                () -> {
                                    thrown.set(new IllegalStateException("expected"));
                                });
        thread.start();
        thread.join();

        assertThat(thrown.get()).isInstanceOf(IllegalStateException.class);
        assertThat(thread.isVirtual()).isTrue();
    }

    @Test
    @DisplayName("runs on the pinned toolchain version")
    void runsOnPinnedToolchain() {
        int major =
                Integer.parseInt(
                        System.getProperty("java.specification.version").replaceAll("[^0-9]", ""));

        assertThat(major).isGreaterThanOrEqualTo(21);
    }
}
