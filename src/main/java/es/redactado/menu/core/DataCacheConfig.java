package es.redactado.menu.core;

import java.time.Duration;

/**
 * Bounds on a {@link DataCache}.
 *
 * @param maxSize the largest number of entries to keep
 * @param ttl how long an entry survives after it was loaded
 */
public record DataCacheConfig(long maxSize, Duration ttl) {

    public DataCacheConfig {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive, got " + maxSize);
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive, got " + ttl);
        }
    }

    /**
     * Builds a config.
     *
     * @param maxSize the largest number of entries to keep
     * @param ttl how long an entry survives after it was loaded
     * @return the config
     */
    public static DataCacheConfig of(long maxSize, Duration ttl) {
        return new DataCacheConfig(maxSize, ttl);
    }
}
