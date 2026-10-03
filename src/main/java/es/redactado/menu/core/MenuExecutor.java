package es.redactado.menu.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs menu handlers off the JDA event thread.
 *
 * <p>Handlers do I/O and may block, so they must never run on a JDA thread. The
 * interaction is acknowledged on the JDA thread first, which is the only part that
 * has to respect Discord's three-second deadline, and the handler body runs here
 * afterwards.
 */
public final class MenuExecutor implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MenuExecutor.class);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final ExecutorService delegate;

    private MenuExecutor(ExecutorService delegate) {
        this.delegate = delegate;
    }

    /**
     * Creates an executor backed by one virtual thread per task.
     *
     * <p>Virtual threads are used because a menu handler is dominated by waiting
     * on a network or database call, so the useful concurrency is far higher than
     * the number of cores. Threads are named {@code menu-N} so they are easy to
     * spot in a thread dump.
     *
     * @return a new executor, which the caller must close
     */
    public static MenuExecutor virtual() {
        return new MenuExecutor(
                Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("menu-", 0).factory()));
    }

    /**
     * Wraps an existing executor. Visible for tests that need a deterministic one.
     *
     * @param delegate the executor to run handlers on
     * @return a new executor
     */
    static MenuExecutor of(ExecutorService delegate) {
        return new MenuExecutor(delegate);
    }

    /**
     * Submits a handler body.
     *
     * @param task the work to run off the JDA thread
     * @throws RejectedExecutionException if the executor is closed or saturated
     */
    public void execute(Runnable task) {
        delegate.execute(task);
    }

    /**
     * Stops accepting work, waits briefly for running tasks, then interrupts
     * whatever is left.
     *
     * <p>The wait is bounded so shutdown cannot hang on a handler that is stuck on
     * a slow dependency.
     */
    @Override
    public void close() {
        delegate.shutdown();
        try {
            if (!delegate.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOG.warn(
                        "Menu handlers still running after {}s, interrupting",
                        SHUTDOWN_TIMEOUT_SECONDS);
                delegate.shutdownNow();
            }
        } catch (InterruptedException e) {
            delegate.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
