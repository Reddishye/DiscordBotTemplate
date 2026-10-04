package es.redactado.menu.api;

/**
 * Raised when a rendered container exceeds a documented Discord limit.
 *
 * <p>The counts are in the message rather than in accessors, because nothing catches this to
 * read them: the router turns it into one localized sentence with a reference code, and the
 * reference in the log is what a maintainer needs.
 */
public class ComponentLimitException extends MenuException {

    /**
     * Creates the exception for an exceeded limit.
     *
     * @param actual the observed count
     * @param limit the allowed count
     * @param detail human-readable description of what was counted
     */
    public ComponentLimitException(int actual, int limit, String detail) {
        super("Component limit exceeded: %d/%d: %s".formatted(actual, limit, detail));
    }
}
