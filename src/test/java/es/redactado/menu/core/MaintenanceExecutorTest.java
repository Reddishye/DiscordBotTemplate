package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves the optional executor on a data cache is really the one that runs the load.
 *
 * <p>Asserting that the constructor stored it would prove nothing, and running the supplied
 * executor inline would hide the difference entirely: an inline executor and no executor at
 * all both appear to run on the calling thread. So the recorder runs its work on a thread with
 * a name of its own, and the assertion is on the <em>name</em> of the thread that ran the
 * loader. That is the only way to tell "used the pool" from "used the library default".
 *
 * <p>A session store deliberately has no such parameter, because nothing in it would ever run
 * on one. Its own Javadoc says so; this test is the other half of that argument, showing the
 * difference is not theoretical for the cache that does use the executor.
 */
class MaintenanceExecutorTest {

    private final List<ExecutorService> pools = new java.util.ArrayList<>();

    @AfterEach
    void shutdownPools() {
        pools.forEach(pool -> pool.shutdownNow());
    }

    @Test
    @DisplayName("a data cache runs the loader on the pool it was given")
    void dataCacheLoadsOnTheGivenPool() {
        Recorder recorder = recorder();
        AtomicReference<String> loaderThread = new AtomicReference<>();
        DataCache<String, String> cache =
                new DataCache<>(
                        DataCacheConfig.of(8, Duration.ofMinutes(5)),
                        key -> {
                            loaderThread.set(Thread.currentThread().getName());
                            return CompletableFuture.completedFuture("value-" + key);
                        },
                        recorder);

        assertThat(cache.get("k").join()).as("the value still arrives").isEqualTo("value-k");
        assertThat(loaderThread.get())
                .as("the loader must start on the supplied pool, not on the reading thread")
                .startsWith("maintenance-");
        assertThat(recorder.threadNames())
                .as("and the pool is the one that was handed over")
                .isNotEmpty()
                .allSatisfy(name -> assertThat(name).startsWith("maintenance-"));
    }

    @Test
    @DisplayName("a data cache with no executor loads on the library default pool")
    void dataCacheWithoutExecutorStillLoads() {
        AtomicReference<String> loaderThread = new AtomicReference<>();
        DataCache<String, String> cache =
                new DataCache<String, String>(
                        DataCacheConfig.of(8, Duration.ofMinutes(1)),
                        key -> {
                            loaderThread.set(Thread.currentThread().getName());
                            return CompletableFuture.completedFuture("v");
                        });

        assertThat(cache.get("k").join()).isEqualTo("v");
        assertThat(loaderThread.get())
                .as("without a pool the load happens on the library default executor")
                .contains("ForkJoinPool");
    }

    @Test
    @DisplayName("an executor that rejects fails the read rather than losing it")
    void aRejectingExecutorFailsTheRead() {
        Executor rejecting =
                task -> {
                    throw new java.util.concurrent.RejectedExecutionException("full");
                };

        DataCache<String, String> cache =
                new DataCache<String, String>(
                        DataCacheConfig.of(8, Duration.ofMinutes(1)),
                        key -> CompletableFuture.completedFuture("v"),
                        rejecting);

        assertThatThrownBy(() -> cache.get("k").join())
                .hasRootCauseInstanceOf(java.util.concurrent.RejectedExecutionException.class);
    }

    private Recorder recorder() {
        ExecutorService pool =
                Executors.newSingleThreadExecutor(
                        Thread.ofPlatform().name("maintenance-", 0).factory());
        pools.add(pool);
        return new Recorder(pool, new CopyOnWriteArrayList<>());
    }

    /** An executor that runs tasks on its own thread and remembers where they ran. */
    private record Recorder(Executor pool, List<String> names) implements Executor {

        @Override
        public void execute(Runnable task) {
            pool.execute(
                    () -> {
                        names.add(Thread.currentThread().getName());
                        task.run();
                    });
        }

        /**
         * The names of the threads tasks ran on.
         *
         * <p>No sleeping: a load is only handed back to the caller once it has run, so by
         * the time a join returns the task has already recorded its name.
         */
        List<String> threadNames() {
            return List.copyOf(names);
        }
    }
}
