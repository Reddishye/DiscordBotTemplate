package es.redactado.menu.core;

import java.time.Duration;

/**
 * Bounds on how many menu sessions are retained and how long an idle one lives.
 *
 * <p>Sessions are keyed by message id and hold only a short navigation stack plus
 * a small state map, so the cache is cheap. The size bound exists to stop a long
 * running bot from accumulating one entry per message it has ever rendered.
 *
 * @param maxSize the largest number of sessions to keep
 * @param idleTtl how long a session survives without being read or written
 */
public record SessionConfig(long maxSize, Duration idleTtl) {

    private static final long DEFAULT_MAX_SIZE = 50_000;
    private static final Duration DEFAULT_IDLE_TTL = Duration.ofMinutes(30);

    public SessionConfig {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive, got " + maxSize);
        }
        if (idleTtl == null || idleTtl.isZero() || idleTtl.isNegative()) {
            throw new IllegalArgumentException("idleTtl must be positive, got " + idleTtl);
        }
    }

    /**
     * The bounds used unless a caller supplies its own.
     *
     * @return 50,000 sessions retained for 30 minutes of inactivity
     */
    public static SessionConfig defaults() {
        return new SessionConfig(DEFAULT_MAX_SIZE, DEFAULT_IDLE_TTL);
    }
}
