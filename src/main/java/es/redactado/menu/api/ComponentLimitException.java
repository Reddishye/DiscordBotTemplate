package es.redactado.menu.api;

/** Raised when a rendered container exceeds a documented Discord limit. */
public class ComponentLimitException extends MenuException {

    private final int actual;
    private final int limit;

    /**
     * Creates the exception for an exceeded limit.
     *
     * @param actual the observed count
     * @param limit the allowed count
     * @param detail human-readable description of what was counted
     */
    public ComponentLimitException(int actual, int limit, String detail) {
        super("Component limit exceeded: %d/%d: %s".formatted(actual, limit, detail));
        this.actual = actual;
        this.limit = limit;
    }

    /**
     * The observed count.
     *
     * @return the actual count
     */
    public int actual() {
        return actual;
    }

    /**
     * The allowed count.
     *
     * @return the limit
     */
    public int limit() {
        return limit;
    }
}
