package es.redactado.menu.core;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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
 *
 * <p><strong>The executor is reached through an interface, never through the template's
 * services.</strong> This package takes a {@link java.util.concurrent.Executor} and knows
 * nothing about what provides it, so it can be driven by a pool of the host application, by
 * {@code MenuExecutor.virtual()} in a test, or by a deterministic executor in a test that needs
 * one. Wiring a concrete pool to this class happens outside the package, in the bot's own
 * startup code.
 *
 * <p><strong>An executor is closed by whoever created it.</strong> {@link #virtual()} creates
 * one and closes it; {@link #shared(Executor)} borrows one and leaves it running. A borrowed pool
 * outlives every menu, and closing it here would break whatever else is using it.
 */
public final class MenuExecutor implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MenuExecutor.class);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final Executor delegate;

    /** What {@link #close()} may shut down: the delegate when created here, else nothing. */
    private final ExecutorService owned;

    private MenuExecutor(Executor delegate, ExecutorService owned) {
        this.delegate = delegate;
        this.owned = owned;
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
        ExecutorService created =
                Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("menu-", 0).factory());
        return new MenuExecutor(created, created);
    }

    /**
     * Runs handlers on an executor this class does not own.
     *
     * <p>For a host application that already has a pool: the menu package asks for an
     * {@link Executor} and never learns where it came from, so the pool can be the bot's own
     * without the menu package depending on the bot's services.
     *
     * <p>{@link #close()} leaves the executor alone. Rejection still reaches the caller:
     * {@link #execute} delegates rather than swallowing, so a saturated pool surfaces as the
     * localized "busy" answer rather than as a menu that silently stops responding.
     *
     * @param io the executor to run handlers on
     * @return an executor that borrows {@code io}
     * @throws NullPointerException if {@code io} is null
     */
    public static MenuExecutor shared(Executor io) {
        return new MenuExecutor(Objects.requireNonNull(io, "io"), null);
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
     *
     * <p>Does nothing when the executor is borrowed from
     * {@link #shared(Executor)}: the pool belongs to the caller and outlives every menu.
     */
    @Override
    public void close() {
        if (owned == null) {
            LOG.debug("Leaving the shared executor running; it belongs to the caller");
            return;
        }
        owned.shutdown();
        try {
            if (!owned.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOG.warn(
                        "Menu handlers still running after {}s, interrupting",
                        SHUTDOWN_TIMEOUT_SECONDS);
                owned.shutdownNow();
            }
        } catch (InterruptedException e) {
            owned.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
