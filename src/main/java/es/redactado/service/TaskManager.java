package es.redactado.service;

import com.google.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central entry point for asynchronous and scheduled work.
 *
 * <p>Three executors back this service, each with a distinct job:
 *
 * <ul>
 *   <li>a small timer pool that only fires triggers and never runs long tasks;
 *   <li>a bounded, fixed-size pool for CPU-bound work;
 *   <li>a virtual-thread-per-task executor for blocking I/O.
 * </ul>
 *
 * <p>Keeping timers apart from workers avoids contention on the delay queue's single lock and
 * stops a slow task from delaying unrelated triggers.
 *
 * <p>The service is thread-safe. {@link #init()} and {@link #shutdown()} are idempotent. Every
 * other method throws {@link IllegalStateException} if the service is not running.
 *
 * <p><strong>Scoped as a singleton</strong> because the service registry resolves a service
 * by class and inits that instance, while anything injecting this class would otherwise get
 * a second, never-started copy: its executors would not exist and {@link #ioExecutor()}
 * would throw. One instance, started once, shared by everyone who needs its pools.
 *
 * <p>Requires Java 21 or later.
 */
@Singleton
public class TaskManager implements IService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskManager.class);

    private static final int DEFAULT_TIMER_THREADS = 2;
    private static final int DEFAULT_QUEUE_CAPACITY = 4096;
    private static final long SHUTDOWN_TIMEOUT_MILLIS = 5_000L;

    private final int cpuThreads;
    private final int timerThreads;
    private final int queueCapacity;

    private volatile Pools pools;

    /** Creates a manager sized to the number of available processors. */
    public TaskManager() {
        this(
                Runtime.getRuntime().availableProcessors(),
                DEFAULT_TIMER_THREADS,
                DEFAULT_QUEUE_CAPACITY);
    }

    /**
     * Creates a manager with explicit sizing.
     *
     * @param cpuThreads number of threads in the CPU-bound pool, at least 1
     * @param timerThreads number of threads firing timers, at least 1
     * @param queueCapacity maximum number of queued CPU-bound tasks, at least 1; submissions beyond
     *     this limit are rejected instead of growing memory without bound
     * @throws IllegalArgumentException if any argument is lower than 1
     */
    public TaskManager(int cpuThreads, int timerThreads, int queueCapacity) {
        if (cpuThreads < 1 || timerThreads < 1 || queueCapacity < 1) {
            throw new IllegalArgumentException("All sizing arguments must be at least 1");
        }
        this.cpuThreads = cpuThreads;
        this.timerThreads = timerThreads;
        this.queueCapacity = queueCapacity;
    }

    /** Starts the executors. Calling this on a running manager has no effect. */
    @Override
    public synchronized void init() {
        if (pools != null) {
            return;
        }

        ScheduledThreadPoolExecutor timer =
                new ScheduledThreadPoolExecutor(timerThreads, new NamedThreadFactory("task-timer"));
        timer.setRemoveOnCancelPolicy(true);
        timer.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        timer.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);

        ThreadPoolExecutor cpu =
                new ThreadPoolExecutor(
                        cpuThreads,
                        cpuThreads,
                        0L,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>(queueCapacity),
                        new NamedThreadFactory("task-cpu"),
                        new ThreadPoolExecutor.AbortPolicy());
        cpu.prestartAllCoreThreads();

        ExecutorService io =
                Executors.newThreadPerTaskExecutor(
                        Thread.ofVirtual().name("task-io-", 0).factory());

        pools = new Pools(timer, cpu, io);
        LOGGER.info(
                "TaskManager initialized (cpuThreads={}, timerThreads={}, queueCapacity={})",
                cpuThreads,
                timerThreads,
                queueCapacity);
    }

    /**
     * Stops accepting work and waits up to five seconds for running tasks to finish. Tasks still
     * running after that are interrupted. Calling this on a stopped manager has no effect.
     */
    @Override
    public synchronized void shutdown() {
        Pools current = pools;
        if (current == null) {
            return;
        }
        pools = null;

        current.timer().shutdown();
        current.cpu().shutdown();
        current.io().shutdown();

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SHUTDOWN_TIMEOUT_MILLIS);
        boolean interrupted = false;
        try {
            for (ExecutorService executor :
                    new ExecutorService[] {current.timer(), current.cpu(), current.io()}) {
                long remaining = Math.max(0L, deadline - System.nanoTime());
                if (!executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                    int dropped = executor.shutdownNow().size();
                    LOGGER.warn(
                            "Executor did not terminate in time, {} queued task(s) dropped",
                            dropped);
                }
            }
        } catch (InterruptedException e) {
            interrupted = true;
            current.timer().shutdownNow();
            current.cpu().shutdownNow();
            current.io().shutdownNow();
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        LOGGER.info("TaskManager shut down");
    }

    /**
     * Runs a CPU-bound task on the worker pool.
     *
     * @param task the task to run
     * @return a future completed when the task finishes; it completes exceptionally if the task
     *     throws or if the worker queue is full
     */
    public CompletableFuture<Void> run(Runnable task) {
        Objects.requireNonNull(task, "task");
        return submit(() -> CompletableFuture.runAsync(task, pools().cpu()));
    }

    /**
     * Runs a CPU-bound task that returns a value on the worker pool.
     *
     * @param task the task to run
     * @param <T> the result type
     * @return a future completed with the task's result; checked exceptions are wrapped in a {@link
     *     CompletionException}
     */
    public <T> CompletableFuture<T> supply(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        return submit(() -> CompletableFuture.supplyAsync(callableToSupplier(task), pools().cpu()));
    }

    /**
     * Runs a blocking task on a virtual thread. Use this for network, disk and database calls, not
     * for heavy computation.
     *
     * @param task the task to run
     * @return a future completed when the task finishes
     */
    public CompletableFuture<Void> runIo(Runnable task) {
        Objects.requireNonNull(task, "task");
        return submit(() -> CompletableFuture.runAsync(task, pools().io()));
    }

    /**
     * Runs a blocking task that returns a value on a virtual thread.
     *
     * @param task the task to run
     * @param <T> the result type
     * @return a future completed with the task's result
     * @see #runIo(Runnable)
     */
    public <T> CompletableFuture<T> supplyIo(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        return submit(() -> CompletableFuture.supplyAsync(callableToSupplier(task), pools().io()));
    }

    /**
     * The executor that runs blocking I/O, one virtual thread per task.
     *
     * <p>Returned as a bare {@link Executor} rather than an {@link ExecutorService} so a caller
     * cannot shut down a pool it does not own. Whoever creates an executor closes it; a component
     * that borrows this one keeps running after the component is closed, and a component that
     * closed the pool would break every other borrower at once.
     *
     * <p>The pool is bounded by whatever the task waits on rather than by thread count, so this is
     * the executor to hand to work that blocks on a network or database call.
     *
     * @return the I/O executor, which stays owned by this manager
     * @throws IllegalStateException if the manager is not running
     */
    public Executor ioExecutor() {
        return pools().io();
    }

    /**
     * The executor that runs CPU-bound work, a fixed pool sized to the available processors.
     *
     * <p>Returned as a bare {@link Executor} for the same reason as {@link #ioExecutor()}: the
     * caller does not own it and must not be able to close it.
     *
     * @return the CPU-bound executor, which stays owned by this manager
     * @throws IllegalStateException if the manager is not running
     */
    public Executor cpuExecutor() {
        return pools().cpu();
    }

    /**
     * Runs a task once after a delay. The timer thread only triggers the task; the work itself
     * runs on the CPU pool.
     *
     * @param task the task to run
     * @param delay the time to wait before running
     * @param unit the unit of {@code delay}
     * @return a handle that can cancel the task before it fires
     */
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(unit, "unit");
        Pools p = pools();
        return p.timer().schedule(() -> dispatch(p.cpu(), guarded(task)), delay, unit);
    }

    /**
     * Runs a task periodically at a fixed rate. The task executes on the CPU pool.
     *
     * <p>If a run is still in progress when the next trigger fires, that trigger is skipped. Runs
     * of the same task therefore never overlap. An exception thrown by the task is logged and does
     * not cancel later runs.
     *
     * @param task the task to run
     * @param initialDelay the delay before the first trigger
     * @param period the time between triggers
     * @param unit the unit of both time arguments
     * @return a handle that can cancel the periodic task
     */
    public ScheduledFuture<?> scheduleAtFixedRate(
            Runnable task, long initialDelay, long period, TimeUnit unit) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(unit, "unit");
        Pools p = pools();
        AtomicBoolean running = new AtomicBoolean();
        Runnable trigger =
                () -> {
                    if (!running.compareAndSet(false, true)) {
                        LOGGER.debug("Skipping trigger, previous run is still active");
                        return;
                    }
                    Runnable work =
                            () -> {
                                try {
                                    guarded(task).run();
                                } finally {
                                    running.set(false);
                                }
                            };
                    try {
                        p.cpu().execute(work);
                    } catch (RejectedExecutionException e) {
                        running.set(false);
                        LOGGER.warn("Periodic task rejected by the worker pool", e);
                    }
                };
        return p.timer().scheduleAtFixedRate(trigger, initialDelay, period, unit);
    }

    /**
     * Runs a task periodically, waiting a fixed delay between the end of one run and the start of
     * the next.
     *
     * <p>Unlike the other scheduling methods, the task runs <em>on the timer thread</em>, because
     * the delay is measured from completion and only the scheduler can track that. Keep these
     * tasks short and non-blocking. Exceptions are logged and do not cancel later runs.
     *
     * @param task the task to run
     * @param initialDelay the delay before the first run
     * @param delay the time between the end of one run and the start of the next
     * @param unit the unit of both time arguments
     * @return a handle that can cancel the periodic task
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(
            Runnable task, long initialDelay, long delay, TimeUnit unit) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(unit, "unit");
        return pools().timer().scheduleWithFixedDelay(guarded(task), initialDelay, delay, unit);
    }

    private Pools pools() {
        Pools current = pools;
        if (current == null) {
            throw new IllegalStateException("TaskManager is not running");
        }
        return current;
    }

    private static <T> CompletableFuture<T> submit(
            java.util.function.Supplier<CompletableFuture<T>> action) {
        try {
            return action.get();
        } catch (RejectedExecutionException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static <T> java.util.function.Supplier<T> callableToSupplier(Callable<T> task) {
        return () -> {
            try {
                return task.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        };
    }

    private static void dispatch(Executor executor, Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            LOGGER.warn("Scheduled task rejected by the worker pool", e);
        }
    }

    private static Runnable guarded(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Exception e) {
                LOGGER.error("Scheduled task failed", e);
            }
        };
    }

    private record Pools(
            ScheduledThreadPoolExecutor timer, ThreadPoolExecutor cpu, ExecutorService io) {}

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger();

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            t.setUncaughtExceptionHandler(
                    (thread, e) -> LOGGER.error("Uncaught exception in {}", thread.getName(), e));
            return t;
        }
    }
}
