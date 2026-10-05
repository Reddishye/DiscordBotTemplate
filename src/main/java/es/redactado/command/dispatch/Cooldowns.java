package es.redactado.command.dispatch;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user, per-command cooldowns.
 *
 * <p>The map is the whole store. A restart clears it, which is the right behaviour for a
 * cooldown: it is a rate limit, not a preference.
 */
public final class Cooldowns {

    private final ConcurrentHashMap<String, Long> until = new ConcurrentHashMap<>();

    /**
     * Reserves the next use, or reports how long is left.
     *
     * @param cooldown zero or negative means the command is not limited
     * @return empty when the command may run
     */
    public Optional<Duration> tryAcquire(
            String command, long userId, Duration cooldown, long nowMillis) {
        if (cooldown == null || cooldown.isZero() || cooldown.isNegative()) {
            return Optional.empty();
        }
        String key = command + ":" + userId;
        long next = nowMillis + cooldown.toMillis();
        while (true) {
            Long existing = until.putIfAbsent(key, next);
            if (existing == null) {
                return Optional.empty();
            }
            if (nowMillis >= existing) {
                if (until.replace(key, existing, next)) {
                    return Optional.empty();
                }
                continue;
            }
            return Optional.of(Duration.ofMillis(existing - nowMillis));
        }
    }
}
