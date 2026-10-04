package es.redactado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the two executor accessors.
 *
 * <p>Deliberately narrow: it covers the accessors added here and nothing else about the service.
 * The point of interest is the one place they differ from the methods that submit work. Running a
 * task on a stopped manager fails at the moment of submission and comes back as a failed future;
 * an accessor has no future to fail, so it throws at the call, which is a different contract and a
 * different failure to handle.
 *
 * <p>No gateway, no container and no database are involved.
 */
class TaskManagerExecutorTest {

    private static final int AWAIT_SECONDS = 5;

    private final TaskManager manager = new TaskManager();

    @AfterEach
    void stop() {
        manager.shutdown();
    }

    @Test
    @DisplayName("both accessors hand back a usable executor while the manager runs")
    void accessorsWorkWhileRunning() {
        manager.init();

        assertThat(runsOn(manager.ioExecutor())).as("ioExecutor").isTrue();
        assertThat(runsOn(manager.cpuExecutor())).as("cpuExecutor").isTrue();
    }

    @Test
    @DisplayName("a task really lands on the pool that was handed out")
    void thePoolIsTheOneUsed() throws InterruptedException {
        manager.init();

        assertThat(threadOf(manager.ioExecutor())).startsWith("task-io-");
        assertThat(threadOf(manager.cpuExecutor())).startsWith("task-cpu-");
    }

    @Test
    @DisplayName("both accessors refuse before init")
    void accessorsRefuseWhenNotStarted() {
        assertThatThrownBy(manager::ioExecutor)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not running");
        assertThatThrownBy(manager::cpuExecutor)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not running");
    }

    @Test
    @DisplayName("both accessors refuse again after shutdown")
    void accessorsRefuseAfterShutdown() {
        manager.init();
        manager.shutdown();

        assertThatThrownBy(manager::ioExecutor).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(manager::cpuExecutor).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a manager can be restarted, and the accessors serve the new pools")
    void accessorsFollowARestart() {
        manager.init();
        Executor first = manager.ioExecutor();
        manager.shutdown();
        manager.init();

        Executor second = manager.ioExecutor();

        assertThat(second).as("a restart must not hand back the stopped pool").isNotSameAs(first);
        assertThat(runsOn(second)).isTrue();
    }

    /** Runs a task on the executor, so a usable pool is proven rather than assumed. */
    private static boolean runsOn(Executor executor) {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            executor.execute(latch::countDown);
        } catch (RejectedExecutionException refused) {
            return false;
        }
        try {
            return latch.await(AWAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** The name of the thread a task ran on, which is how the pool is identified. */
    private static String threadOf(Executor executor) throws InterruptedException {
        String[] captured = new String[1];
        CountDownLatch latch = new CountDownLatch(1);
        executor.execute(
                () -> {
                    captured[0] = Thread.currentThread().getName();
                    latch.countDown();
                });
        if (!latch.await(AWAIT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("the task never ran");
        }
        return captured[0];
    }
}
