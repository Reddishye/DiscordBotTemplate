package es.redactado.menu.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import es.redactado.menu.api.Session;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Keeps one {@link Session} per menu message.
 *
 * <p>Backed by Caffeine so entries expire on their own; nothing has to sweep them.
 * Reads are lock-free and a session is created at most once per message even under
 * concurrent first clicks, because the cache's mapping function runs once.
 *
 * <p>A host application with its own pool can hand it over for cache maintenance, so eviction
 * and expiry bookkeeping happens there rather than on whichever thread happened to touch the
 * store. That is a scheduling choice only: sessions are small, the work is bookkeeping, and
 * nothing waits for it.
 */
public final class SessionStore implements AutoCloseable {

    private final Cache<Long, Session> sessions;

    /**
     * Creates a store with the given bounds and real time.
     *
     * @param config the size and lifetime bounds
     */
    public SessionStore(SessionConfig config) {
        this(config, null, null);
    }

    /**
     * Creates a store that runs cache maintenance on the given executor.
     *
     * @param config the size and lifetime bounds
     * @param maintenance where eviction and expiry bookkeeping runs, or null for the
     *     library default
     */
    public SessionStore(SessionConfig config, Executor maintenance) {
        this(config, null, maintenance);
    }

    /**
     * Creates a store on an explicit clock and eviction executor, so a test can
     * drive expiry without sleeping.
     *
     * @param config the size and lifetime bounds
     * @param ticker the time source, or null for the system clock
     * @param executor the executor eviction runs on, or null for Caffeine's default
     */
    SessionStore(
            SessionConfig config,
            com.github.benmanes.caffeine.cache.Ticker ticker,
            Executor executor) {
        Caffeine<Object, Object> builder =
                Caffeine.newBuilder()
                        .maximumSize(config.maxSize())
                        .expireAfterAccess(config.idleTtl());
        if (ticker != null) {
            builder.ticker(ticker);
        }
        if (executor != null) {
            builder.executor(executor);
        }
        this.sessions = builder.build();
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
