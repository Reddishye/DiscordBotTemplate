package es.redactado.menu.core;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single-flight cache for read-heavy data a view needs.
 *
 * <p>The point is that concurrent readers of one key share a single load. Ten users
 * opening the same panel at once produce one database query, not ten. A load that
 * fails is not remembered, so a transient outage does not poison the cache until
 * the TTL expires.
 *
 * <p>Writes should go through {@link #invalidateAfter(CompletableFuture, Object)}
 * so the cached copy is dropped as soon as the write lands, whether it succeeded or
 * failed. Failing to invalidate after a write is the usual way a cache serves
 * stale data that looks fresh.
 *
 * @param <K> the cache key type
 * @param <V> the cached value type
 */
public final class DataCache<K, V> {

    private static final Logger LOG = LoggerFactory.getLogger(DataCache.class);

    private final AsyncLoadingCache<K, V> cache;
    private final Function<K, CompletableFuture<V>> loader;

    /**
     * Creates a cache that loads on the given executor.
     *
     * @param config the size and lifetime bounds
     * @param loader produces the value for a key, asynchronously
     * @param executor the executor loads and maintenance run on
     */
    public DataCache(
            DataCacheConfig config, Function<K, CompletableFuture<V>> loader, Executor executor) {
        this(config, loader, executor, null);
    }

    /**
     * Creates a cache on an explicit clock, so a test can drive expiry.
     *
     * @param config the size and lifetime bounds
     * @param loader produces the value for a key, asynchronously
     * @param executor the executor loads and maintenance run on
     * @param ticker the time source, or null for the system clock
     */
    DataCache(
            DataCacheConfig config,
            Function<K, CompletableFuture<V>> loader,
            Executor executor,
            com.github.benmanes.caffeine.cache.Ticker ticker) {
        this.loader = Objects.requireNonNull(loader, "loader");
        Caffeine<Object, Object> builder =
                Caffeine.newBuilder()
                        .maximumSize(config.maxSize())
                        .expireAfterWrite(config.ttl())
                        .executor(executor);
        if (ticker != null) {
            builder.ticker(ticker);
        }
        this.cache = builder.buildAsync((AsyncCacheLoader<K, V>) this::loadAsync);
    }

    private CompletableFuture<V> loadAsync(K key, Executor executor) {
        return load(key);
    }

    private CompletableFuture<V> load(K key) {
        try {
            return loader.apply(key);
        } catch (RuntimeException e) {
            LOG.debug("Loader threw for key {}", key, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Reads a key, loading it on first use.
     *
     * <p>Concurrent calls for one key share a single load.
     *
     * @param key the cache key
     * @return a future for the value; never null
     */
    public CompletableFuture<V> get(K key) {
        return cache.get(key);
    }

    /**
     * Drops a key so the next read reloads it.
     *
     * @param key the cache key
     */
    public void invalidate(K key) {
        cache.synchronous().invalidate(key);
    }

    /** Drops every key. */
    public void invalidateAll() {
        cache.synchronous().invalidateAll();
    }

    /**
     * Invalidates a key once a write completes, and passes the write's outcome
     * through unchanged.
     *
     * <p>Invalidating on failure as well as success is deliberate: a failed write
     * may still have left the stored value different from what is cached.
     *
     * @param write the write to wait for
     * @param key the cache key the write affects
     * @param <T> the write's result type
     * @return a future carrying the write's result or the write's failure
     */
    public <T> CompletableFuture<T> invalidateAfter(CompletableFuture<T> write, K key) {
        AtomicReference<CompletableFuture<T>> result = new AtomicReference<>(write);
        return write.whenComplete(
                (value, error) -> {
                    invalidate(key);
                    if (error != null) {
                        result.set(CompletableFuture.failedFuture(error));
                    }
                });
    }

    /**
     * Approximate number of retained entries.
     *
     * @return the estimated size
     */
    public long size() {
        return cache.synchronous().estimatedSize();
    }

    /**
     * Runs pending maintenance, so {@link #size()} converges on the bound.
     *
     * <p>Caffeine evicts lazily during normal activity, so a test that fills a
     * cache past its maximum needs this before the figure settles.
     */
    public void cleanUp() {
        cache.synchronous().cleanUp();
    }
}
