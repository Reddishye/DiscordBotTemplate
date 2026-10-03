package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Ticker;
import es.redactado.menu.api.Session;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionStoreTest {

    /** A clock the test moves by hand, so expiry needs no sleeping. */
    private static final class FakeTicker implements Ticker {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        void advance(Duration amount) {
            nanos.addAndGet(amount.toNanos());
        }
    }

    @Test
    @DisplayName("getOrCreate returns one instance per message id")
    void oneInstancePerId() {
        try (SessionStore store = new SessionStore(SessionConfig.defaults())) {
            Session first = store.getOrCreate(1L);
            Session again = store.getOrCreate(1L);

            assertThat(again).isSameAs(first);
            assertThat(store.getOrCreate(2L)).isNotSameAs(first);
        }
    }

    @Test
    @DisplayName("find is empty before creation and present after")
    void findOnlyAfterCreation() {
        try (SessionStore store = new SessionStore(SessionConfig.defaults())) {
            assertThat(store.find(1L)).isEmpty();

            store.getOrCreate(1L);

            assertThat(store.find(1L)).isPresent();
        }
    }

    @Test
    @DisplayName("remove drops a session")
    void removeDrops() {
        try (SessionStore store = new SessionStore(SessionConfig.defaults())) {
            store.getOrCreate(1L);

            store.remove(1L);

            assertThat(store.find(1L)).isEmpty();
        }
    }

    @Test
    @DisplayName("a session expires once the idle ttl passes")
    void expiresAfterIdleTtl() {
        FakeTicker ticker = new FakeTicker();
        SessionConfig config = new SessionConfig(100, Duration.ofMinutes(30));
        try (SessionStore store = new SessionStore(config, ticker, Runnable::run)) {
            store.getOrCreate(1L);

            ticker.advance(Duration.ofMinutes(31));

            assertThat(store.find(1L)).isEmpty();
        }
    }

    @Test
    @DisplayName("reading a session keeps it alive")
    void accessRenews() {
        FakeTicker ticker = new FakeTicker();
        SessionConfig config = new SessionConfig(100, Duration.ofMinutes(30));
        try (SessionStore store = new SessionStore(config, ticker, Runnable::run)) {
            store.getOrCreate(1L);

            for (int i = 0; i < 5; i++) {
                ticker.advance(Duration.ofMinutes(20));
                assertThat(store.find(1L)).isPresent();
            }

            // Never read for longer than the ttl, so it finally expires.
            ticker.advance(Duration.ofMinutes(31));
            assertThat(store.find(1L)).isEmpty();
        }
    }

    @Test
    @DisplayName("size stays within the configured maximum")
    void respectsMaximumSize() {
        FakeTicker ticker = new FakeTicker();
        SessionConfig config = new SessionConfig(100, Duration.ofHours(1));
        try (SessionStore store = new SessionStore(config, ticker, Runnable::run)) {
            for (long id = 0; id < 1_000; id++) {
                store.getOrCreate(id);
            }

            assertThat(store.size()).isLessThanOrEqualTo(100L);
        }
    }

    @Test
    @DisplayName("concurrent first clicks create exactly one session")
    void concurrentCreateIsSingle() throws InterruptedException {
        try (SessionStore store = new SessionStore(SessionConfig.defaults())) {
            int threads = 64;
            Set<Session> observed = ConcurrentHashMap.newKeySet();
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);

            for (int i = 0; i < threads; i++) {
                pool.execute(
                        () -> {
                            try {
                                start.await();
                                observed.add(store.getOrCreate(1L));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                done.countDown();
                            }
                        });
            }
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();

            assertThat(observed).hasSize(1);
        }
    }

    @Test
    @DisplayName("close empties the store")
    void closeEmpties() {
        SessionStore store = new SessionStore(SessionConfig.defaults());
        store.getOrCreate(1L);

        store.close();

        assertThat(store.size()).isZero();
        assertThat(store.find(1L)).isEmpty();
    }

    @Test
    @DisplayName("defaults are 50,000 sessions and 30 minutes")
    void defaults() {
        SessionConfig config = SessionConfig.defaults();

        assertThat(config.maxSize()).isEqualTo(50_000L);
        assertThat(config.idleTtl()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("a non-positive bound is rejected")
    void rejectsBadConfig() {
        assertThatThrownBy(() -> new SessionConfig(0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SessionConfig(10, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SessionConfig(10, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
