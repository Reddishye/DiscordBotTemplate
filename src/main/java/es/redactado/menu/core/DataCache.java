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
 * <p>The executor a cache is built with decides where Caffeine completes reads and runs
 * eviction bookkeeping. It is optional: left out, the library default applies. It does not decide
 * where the loader's own work runs, because the loader returns a future it has already arranged.
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
     * Creates a cache that leaves maintenance to the library default.
     *
     * @param config the size and lifetime bounds
     * @param loader produces the value for a key, asynchronously
     */
    public DataCache(DataCacheConfig config, Function<K, CompletableFuture<V>> loader) {
        this(config, loader, null, null);
    }

    /**
     * Creates a cache that completes reads and evicts on the given executor.
     *
     * @param config the size and lifetime bounds
     * @param loader produces the value for a key, asynchronously
     * @param executor where reads complete and eviction runs, or null for the library
     *     default
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
                Caffeine.newBuilder().maximumSize(config.maxSize()).expireAfterWrite(config.ttl());
        if (executor != null) {
            // Caffeine rejects a null executor outright, so an absent one has to stay
            // unconfigured rather than be passed through. Unconfigured means the library
            // default, which is the behaviour this cache had before the parameter existed.
            builder.executor(executor);
        }
        if (ticker != null) {
            builder.ticker(ticker);
        }
        this.cache = builder.buildAsync((AsyncCacheLoader<K, V>) this::loadAsync);
    }

    /**
     * Loads one key on the executor Caffeine was configured with.
     *
     * <p>Caffeine hands the loader the executor back, and that is the documented place to
     * start the work. Using it is what makes the parameter worth passing: a loader that
     * returns an already-complete future leaves Caffeine nothing to schedule, so without this
     * the configured executor would never run a task at all.
     *
     * <p>It matters most for a loader that blocks, such as a synchronous repository call:
     * that call then happens on the pool the host application chose rather than on whichever
     * thread happened to read the cache.
     */
    private CompletableFuture<V> loadAsync(K key, Executor executor) {
        CompletableFuture<V> loaded = new CompletableFuture<>();
        try {
            executor.execute(() -> startLoad(key, loaded));
        } catch (RuntimeException rejected) {
            loaded.completeExceptionally(rejected);
        }
        return loaded;
    }

    /** Runs the loader and settles the future, so one rejection path serves both stages. */
    private void startLoad(K key, CompletableFuture<V> loaded) {
        try {
            loader.apply(key)
                    .whenComplete(
                            (value, error) -> {
                                if (error != null) {
                                    loaded.completeExceptionally(error);
                                } else {
                                    loaded.complete(value);
                                }
                            });
        } catch (RuntimeException thrown) {
            LOG.debug("Loader threw for key {}", key, thrown);
            loaded.completeExceptionally(thrown);
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
