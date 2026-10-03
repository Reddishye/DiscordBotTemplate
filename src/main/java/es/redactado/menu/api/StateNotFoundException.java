package es.redactado.menu.api;

/** Raised when a required state key or parameter is absent. */
public class StateNotFoundException extends MenuException {

    private final String key;

    /**
     * Creates the exception for a missing key.
     *
     * @param key the key that could not be resolved
     */
    public StateNotFoundException(String key) {
        super("Required state key not found: " + key);
        this.key = key;
    }

    /**
     * The key that could not be resolved.
     *
     * @return the key
     */
    public String key() {
        return key;
    }
}
