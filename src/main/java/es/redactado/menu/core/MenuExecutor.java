package es.redactado.menu.core;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
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
     * Runs a task that produces a value, on a virtual thread.
     *
     * <p>This is the sanctioned bridge to a blocking service such as JDBC or a
     * synchronous HTTP client. Menu code must never call one directly from a
     * handler, because a handler may run on a JDA event thread in the same task
     * that has to answer the interaction.
     *
     * <p>Virtual threads remove the platform-thread bottleneck but not the resource
     * one: a HikariCP pool of ten connections still admits ten concurrent
     * queries, and the rest queue. That pool, not this executor, is what actually
     * limits concurrency, so sizing the pool is the meaningful decision.
     *
     * @param task the work to run; may block
     * @param <T> the produced type
     * @return a future completing with the value, or completing exceptionally if
     *     the task threw
     */
    public <T> CompletableFuture<T> supply(Supplier<T> task) {
        Objects.requireNonNull(task, "task");
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            execute(
                    () -> {
                        try {
                            result.complete(task.get());
                        } catch (Throwable error) {
                            result.completeExceptionally(error);
                        }
                    });
        } catch (RejectedExecutionException e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    /**
     * Runs a task that produces nothing, on a virtual thread.
     *
     * @param task the work to run; may block
     * @return a future completing when the task does, or completing exceptionally
     *     if it threw
     */
    public CompletableFuture<Void> run(Runnable task) {
        Objects.requireNonNull(task, "task");
        return supply(
                () -> {
                    task.run();
                    return null;
                });
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
