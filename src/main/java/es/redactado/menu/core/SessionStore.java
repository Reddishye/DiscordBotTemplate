package es.redactado.menu.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import es.redactado.menu.api.Session;
import java.util.Optional;

/**
 * Keeps one {@link Session} per menu message.
 *
 * <p>Backed by Caffeine so entries expire on their own; nothing has to sweep them.
 * Reads are lock-free and a session is created at most once per message even under
 * concurrent first clicks, because the cache's mapping function runs once.
 *
 * <p><strong>There is no maintenance executor here, on purpose.</strong> Caffeine only
 * delegates to one for removal notifications, {@code AsyncCache} computations,
 * {@code refresh} and periodic maintenance, and a session store configures none of them.
 * A parameter that can never be used is worse than no parameter: it implies the work is
 * happening somewhere else.
 *
 * <p>What a store does need is an occasional {@code cleanUp}, and that is scheduled
 * explicitly by the host, on its own timer. Draining buffers in the background on every
 * write is not a thing this store wants.
 */
public final class SessionStore implements AutoCloseable {

    private final Cache<Long, Session> sessions;

    /**
     * Creates a store with the given bounds and real time.
     *
     * @param config the size and lifetime bounds
     */
    public SessionStore(SessionConfig config) {
        this(config, null);
    }

    /**
     * Creates a store on an explicit clock, so a test can drive expiry without sleeping.
     *
     * @param config the size and lifetime bounds
     * @param ticker the time source, or null for the system clock
     */
    SessionStore(SessionConfig config, com.github.benmanes.caffeine.cache.Ticker ticker) {
        Caffeine<Object, Object> builder =
                Caffeine.newBuilder()
                        .maximumSize(config.maxSize())
                        .expireAfterAccess(config.idleTtl());
        if (ticker != null) {
            builder.ticker(ticker);
        }
        this.sessions = builder.build();
    }

    /**
     * Runs pending eviction and expiry work, so {@link #size()} converges on the bound.
     *
     * <p>Called on a schedule by the host, which is where a pool belongs: this class owns
     * no thread and no timer. Eviction is lazy, so a store that is written heavily and
     * never drained can hold more than its maximum for a while.
     */
    public void cleanUp() {
        sessions.cleanUp();
    }

    /**
     * Looks up an existing session without creating one.
     *
     * @param messageId the menu message id
     * @return the session, or empty when none exists or it expired
     */
    public Optional<Session> find(long messageId) {
        return Optional.ofNullable(sessions.getIfPresent(messageId));
    }

    /**
     * Returns the session for a message, creating it on first use.
     *
     * @param messageId the menu message id
     * @return the session, never null
     */
    public Session getOrCreate(long messageId) {
        return sessions.get(messageId, key -> new Session());
    }

    /**
     * Discards a session, for example when the menu it belonged to is deleted.
     *
     * @param messageId the menu message id
     */
    public void remove(long messageId) {
        sessions.invalidate(messageId);
    }

    /**
     * Approximate number of retained sessions.
     *
     * <p>Approximate because Caffeine evicts lazily; call after enough activity
     * and the figure converges on the configured maximum.
     *
     * @return the estimated size
     */
    public long size() {
        return sessions.estimatedSize();
    }

    @Override
    public void close() {
        sessions.invalidateAll();
        sessions.cleanUp();
    }
}
