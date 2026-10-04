package es.redactado.menu.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ensures only one interaction per menu message is handled at a time.
 *
 * <p>Discord delivers button clicks independently and a user can double-click. Two
 * concurrent handlers on the same message would race on the session and the
 * rendered output, so the second one is dropped.
 */
final class InteractionGuard {

    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * Claims a message for processing.
     *
     * @param messageId the id of the menu message
     * @return {@code true} when the claim succeeded, {@code false} when another
     *     interaction on the same message is already running
     */
    boolean tryAcquire(long messageId) {
        return inFlight.add(messageId);
    }

    /**
     * Releases a message claim.
     *
     * <p>Idempotent, so it is safe to call more than once for the same
     * interaction. Callers guard with their own flag as well, because releasing
     * the wrong message would unblock an unrelated interaction.
     *
     * @param messageId the id of the menu message
     */
    void release(long messageId) {
        inFlight.remove(messageId);
    }

    /**
     * Number of messages currently claimed. Visible for tests.
     *
     * @return the size of the in-flight set
     */
    int size() {
        return inFlight.size();
    }
}
