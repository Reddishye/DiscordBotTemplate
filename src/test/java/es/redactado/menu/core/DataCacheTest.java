package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DataCacheTest {

    private static final Duration HOUR = Duration.ofHours(1);

    /** A clock the test moves by hand, so expiry needs no sleeping. */
    private static final class FakeTicker implements Ticker {
        private final java.util.concurrent.atomic.AtomicLong nanos =
                new java.util.concurrent.atomic.AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        void advance(Duration amount) {
            nanos.addAndGet(amount.toNanos());
        }
    }

    @Test
    @DisplayName("concurrent readers of one key trigger exactly one load")
    void singleFlight() throws InterruptedException {
        AtomicInteger loads = new AtomicInteger();
        CompletableFuture<String> pending = new CompletableFuture<>();
        DataCache<String, String> cache =
                new DataCache<>(
                        DataCacheConfig.of(100, HOUR),
                        key -> {
                            loads.incrementAndGet();
                            return pending;
                        },
                        Runnable::run);

        int threads = 100;
        Set<String> results = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.execute(
                    () -> {
                        try {
                            start.await();
                            results.add(cache.get("k").join());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
        }
        start.countDown();
        // The single load is still in flight, so every reader is waiting on it.
        assertThat(done.await(200, TimeUnit.MILLISECONDS)).isFalse();
        pending.complete("value");

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(loads.get()).isEqualTo(1);
        assertThat(results).containsExactly("value");
    }

    @Test
    @DisplayName("two keys load independently")
    void distinctKeysLoadIndependently() {
        AtomicInteger loads = new AtomicInteger();
        DataCache<String, String> cache =
                new DataCache<>(
                        DataCacheConfig.of(100, HOUR),
                        key -> {
                            loads.incrementAndGet();
                            return CompletableFuture.completedFuture(
                                    key.toUpperCase(java.util.Locale.ROOT));
                        },
                        Runnable::run);

        assertThat(cache.get("a").join()).isEqualTo("A");
        assertThat(cache.get("b").join()).isEqualTo("B");
        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("a second read is served from the cache")
    void secondReadIsCached() {
        AtomicInteger loads = new AtomicInteger();
        DataCache<String, String> cache =
                new DataCache<>(
                        DataCacheConfig.of(100, HOUR),
                        key -> {
                            loads.incrementAndGet();
                            return CompletableFuture.completedFuture("v");
                        },
                        Runnable::run);

        cache.get("k").join();
        cache.get("k").join();

        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a failed load is not cached")
    void failedLoadIsNotCached() {
        AtomicInteger loads = new AtomicInteger();
        DataCache<String, String> cache =
                new DataCache<>(
                        DataCacheConfig.of(100, HOUR),
                        key -> {
                            if (loads.incrementAndGet() == 1) {
                                return CompletableFuture.failedFuture(
                                        new IllegalStateException("first fails"));
                            }
                            return CompletableFuture.completedFuture("recovered");
                        },
                        Runnable::run);

        assertThatThrownBy(() -> cache.get("k").join()).hasRootCauseMessage("first fails");
        assertThat(cache.get("k").join()).isEqualTo("recovered");
        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("a loader that throws synchronously yields a failed future")
    void syncThrowBecomesFailedFuture() {
        DataCache<String, String> cache =
                new DataCache<>(
                        DataCacheConfig.of(100, HOUR),
                        key -> {
                            throw new IllegalStateException("boom");
                        },
                        Runnable::run);

        CompletableFuture<String> future = cache.get("k");

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::join).hasRootCauseMessage("boom");
    }

    @Test
    @DisplayName("invalidate forces a reload")
    void invalidateForcesReload() {
        AtomicInteger loads = new AtomicInteger();
        DataCache<String, String> cache =
                new DataCache<>(
                        DataCacheConfig.of(100, HOUR),
                        key -> {
                            loads.incrementAndGet();
                            return CompletableFuture.completedFuture("v" + loads.get());
                        },
                        Runnable::run);

        assertThat(cache.get("k").join()).isEqualTo("v1");

        cache.invalidate("k");

        assertThat(cache.get("k").join()).isEqualTo("v2");
    }

    @Test
    @DisplayName("invalidateAll drops everything")
    void invalidateAllDropsEverything() {
        AtomicInteger loads = new AtomicInteger();
        DataCache<String, String> cache =
                new DataCache<>(
                        DataCacheConfig.of(100, HOUR),
                        key -> {
                            loads.incrementAndGet();
                            return CompletableFuture.completedFuture("v");
                        },
                        Runnable::run);
        cache.get("a").join();
        cache.get("b").join();

        cache.invalidateAll();

        cache.get("a").join();
        assertThat(loads.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("entries expire once the ttl passes")
    void expiresAfterTtl() {
        FakeTicker ticker = new FakeTicker();
        AtomicInteger loads = new AtomicInteger();
        DataCache<String, String> cache =
                new DataCache<>(
                        DataCacheConfig.of(100, Duration.ofMinutes(30)),
                        key -> {
                            loads.incrementAndGet();
                            return CompletableFuture.completedFuture("v");
                        },
                        Runnable::run,
                        ticker);

        cache.get("k").join();
        ticker.advance(Duration.ofMinutes(31));
        cache.get("k").join();

        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("size stays within the configured maximum")
    void respectsMaximumSize() {
        DataCache<Integer, String> cache =
                new DataCache<>(
                        DataCacheConfig.of(100, HOUR),
                        key -> CompletableFuture.completedFuture("v" + key),
                        Runnable::run);
        for (int i = 0; i < 1_000; i++) {
            cache.get(i).join();
        }

        cache.cleanUp();

        assertThat(cache.size()).isLessThanOrEqualTo(100L);
    }

    @Test
    @DisplayName("invalidateAfter invalidates on success and keeps the result")
    void invalidateAfterSuccess() {
        AtomicInteger loads = new AtomicInteger();
        DataCache<String, String> cache =
                new DataCache<>(
                        DataCacheConfig.of(100, HOUR),
                        key -> {
                            loads.incrementAndGet();
                            return CompletableFuture.completedFuture("v" + loads.get());
                        },
                        Runnable::run);
        cache.get("k").join();

        CompletableFuture<String> write = CompletableFuture.completedFuture("written");

        assertThat(cache.invalidateAfter(write, "k").join()).isEqualTo("written");
        assertThat(cache.get("k").join()).isEqualTo("v2");
    }

    @Test
    @DisplayName("invalidateAfter invalidates on failure and keeps the failure")
    void invalidateAfterFailure() {
        AtomicInteger loads = new AtomicInteger();
        DataCache<String, String> cache =
                new DataCache<>(
                        DataCacheConfig.of(100, HOUR),
                        key -> {
                            loads.incrementAndGet();
                            return CompletableFuture.completedFuture("v" + loads.get());
                        },
                        Runnable::run);
        cache.get("k").join();

        CompletableFuture<String> write =
                CompletableFuture.failedFuture(new IllegalStateException("write failed"));

        assertThatThrownBy(() -> cache.invalidateAfter(write, "k").join())
                .hasRootCauseMessage("write failed");
        assertThat(cache.get("k").join()).isEqualTo("v2");
    }

    @Test
    @DisplayName("a non-positive bound is rejected")
    void rejectsBadConfig() {
        assertThatThrownBy(() -> DataCacheConfig.of(0, HOUR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DataCacheConfig.of(10, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DataCacheConfig.of(10, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
