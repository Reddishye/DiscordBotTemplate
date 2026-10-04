package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
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
 * Proves the optional maintenance executor is really the one that runs the work.
 *
 * <p>Asserting that the constructor stored it would prove nothing, and running the supplied
 * executor inline would hide the difference entirely: an inline executor and no executor at all
 * both appear to run on the calling thread. So the recorder below runs its work on a thread with
 * a name of its own, and the assertion is on the <em>name</em> of the thread that ran the task.
 * That is the only way to tell "used the pool" from "used the library default".
 */
class MaintenanceExecutorTest {

    private final List<ExecutorService> pools = new ArrayList<>();

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
    @DisplayName("a data cache with no executor still loads, on the library default")
    void dataCacheWithoutExecutorStillLoads() {
        DataCache<String, String> cache =
                new DataCache<>(
                        DataCacheConfig.of(8, Duration.ofMinutes(1)),
                        key -> CompletableFuture.completedFuture("v"));

        assertThat(cache.get("k").join()).isEqualTo("v");
    }

    @Test
    @DisplayName("a session store behaves identically with and without a maintenance pool")
    void sessionStoreBehavesTheSameWithAPool() {
        Recorder recorder = recorder();
        SessionStore withPool = new SessionStore(SessionConfig.defaults(), recorder);
        SessionStore withoutPool = new SessionStore(SessionConfig.defaults());

        for (long message = 1; message <= 3; message++) {
            withPool.getOrCreate(message);
            withoutPool.getOrCreate(message);
        }
        assertThat(withPool.find(1L)).as("a session survives either way").isPresent();
        assertThat(withoutPool.find(1L)).isPresent();
        assertThat(withPool.size()).isEqualTo(withoutPool.size());

        withPool.close();
        withoutPool.close();
        assertThat(withPool.find(1L)).as("close discards everything").isEmpty();
        assertThat(withoutPool.find(1L)).isEmpty();
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
         * <p>No sleeping: a load is only handed back to the caller once it has run, so by the
         * time a join returns the task has already recorded its name.
         */
        List<String> threadNames() {
            return List.copyOf(names);
        }
    }
}
