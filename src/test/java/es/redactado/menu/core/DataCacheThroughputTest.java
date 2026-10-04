package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The load-sharing property under load: many readers, few keys, one load each.
 *
 * <p>A naive cache would run 1,000 loads here. The point of the cache is that it
 * runs at most 10, so the assertion is on the loader's call count, not just on
 * elapsed time.
 */
class DataCacheThroughputTest {

    private static final int CLICKS = 1_000;
    private static final int KEYS = 10;
    private static final long LOAD_DELAY_MILLIS = 50;
    private static final long BUDGET_MILLIS = 10_000;

    @Test
    @DisplayName("1,000 reads over 10 keys load each key at most once")
    void singleFlightPerKey() throws InterruptedException {
        AtomicInteger loads = new AtomicInteger();
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(KEYS);
        DataCache<Integer, String> cache =
                new DataCache<>(
                        DataCacheConfig.of(100, Duration.ofHours(1)),
                        key -> {
                            loads.incrementAndGet();
                            // Completes on another thread after a delay, so the
                            // window for a second reader to start a load is real.
                            CompletableFuture<String> future = new CompletableFuture<>();
                            scheduler.schedule(
                                    () -> future.complete("value-" + key),
                                    LOAD_DELAY_MILLIS,
                                    TimeUnit.MILLISECONDS);
                            return future;
                        },
                        scheduler);

        CountDownLatch done = new CountDownLatch(CLICKS);
        AtomicInteger completed = new AtomicInteger();
        ExecutorService readers = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);

        long before = System.nanoTime();
        for (int i = 0; i < CLICKS; i++) {
            int key = i % KEYS;
            readers.execute(
                    () -> {
                        try {
                            start.await();
                            cache.get(key).join();
                            completed.incrementAndGet();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
        }
        start.countDown();

        assertThat(done.await(BUDGET_MILLIS * 2, TimeUnit.MILLISECONDS)).isTrue();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);

        readers.shutdownNow();
        scheduler.shutdownNow();

        assertThat(completed.get()).isEqualTo(CLICKS);
        assertThat(loads.get()).isLessThanOrEqualTo(KEYS);
        assertThat(elapsedMillis).isLessThan(BUDGET_MILLIS);
    }

    @Test
    @DisplayName("invalidating after a write makes the next read see the new value")
    void writeThenRead() {
        AtomicInteger version = new AtomicInteger();
        DataCache<String, String> cache =
                new DataCache<>(
                        DataCacheConfig.of(100, Duration.ofHours(1)),
                        key -> CompletableFuture.completedFuture("v" + version.get()),
                        Runnable::run);

        assertThat(cache.get("k").join()).isEqualTo("v0");

        version.incrementAndGet();
        cache.invalidateAfter(CompletableFuture.completedFuture(null), "k").join();

        assertThat(cache.get("k").join()).isEqualTo("v1");
    }
}
