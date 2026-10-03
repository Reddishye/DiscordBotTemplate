package es.redactado.menu.api;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Navigation history and menu state for one menu message.
 *
 * <p>This replaces the state and back stack that used to hang off the
 * per-interaction context, where both were discarded the moment the click that
 * created them returned.
 *
 * <p><b>Threading.</b> Every method is synchronized on the session. The router's
 * re-entrancy guard already ensures one interaction per message at a time, but a
 * handler runs on its own virtual thread and can finish on another, so the
 * session still needs its own happens-before guarantee. Critical sections must
 * stay short and must never perform I/O or call out to other objects: everything
 * here is pure in-memory bookkeeping, and holding the monitor while waiting on a
 * database or the Discord API would serialise the whole message behind it.
 */
public final class Session {

    /**
     * Maximum navigation depth. Deeper history is truncated from the bottom rather
     * than rejected, so a user cannot break navigation by clicking around.
     */
    public static final int MAX_DEPTH = 20;

    private final Deque<NavEntry> stack = new ArrayDeque<>();
    private final Map<String, Object> state = new HashMap<>();

    /**
     * Records a view to return to later, discarding the oldest if the stack is
     * already at {@link #MAX_DEPTH}.
     *
     * @param entry the view to remember
     */
    public synchronized void push(NavEntry entry) {
        if (stack.size() >= MAX_DEPTH) {
            stack.removeLast();
        }
        stack.addFirst(entry);
    }

    /**
     * Removes and returns the most recently pushed view.
     *
     * @return the view, or empty when the stack is empty
     */
    public synchronized Optional<NavEntry> pop() {
        return Optional.ofNullable(stack.pollFirst());
    }

    /**
     * Returns the most recently pushed view without removing it.
     *
     * @return the view, or empty when the stack is empty
     */
    public synchronized Optional<NavEntry> peek() {
        return Optional.ofNullable(stack.peekFirst());
    }

    /** Forgets all navigation history. State values are left alone. */
    public synchronized void clearStack() {
        stack.clear();
    }

    /**
     * Current navigation depth.
     *
     * @return the number of remembered views, at most {@link #MAX_DEPTH}
     */
    public synchronized int depth() {
        return stack.size();
    }

    /**
     * Reads a state value.
     *
     * <p>Typed so a caller cannot read a value of the wrong shape as its own and
     * fail later with a cast somewhere unrelated.
     *
     * @param key the state key
     * @param type the expected value type
     * @param <T> the expected value type
     * @return the value, or empty when the key is absent or holds another type
     */
    public synchronized <T> Optional<T> state(String key, Class<T> type) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        Object value = state.get(key);
        // Class.cast throws on a mismatch; an instance check first turns that into
        // the empty Optional the contract promises.
        return value != null && type.isInstance(value)
                ? Optional.of(type.cast(value))
                : Optional.empty();
    }

    /**
     * Stores a state value, replacing any previous one.
     *
     * @param key the state key
     * @param value the value, which must not be null
     */
    public synchronized void putState(String key, Object value) {
        state.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
    }

    /**
     * Removes a state value.
     *
     * @param key the state key
     */
    public synchronized void removeState(String key) {
        state.remove(Objects.requireNonNull(key, "key"));
    }
}
