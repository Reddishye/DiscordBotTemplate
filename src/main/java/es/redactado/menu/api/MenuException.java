package es.redactado.menu.api;

/** Base type for every failure raised by the menu system. */
public class MenuException extends RuntimeException {

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message
     */
    public MenuException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message the detail message
     * @param cause the underlying failure
     */
    public MenuException(String message, Throwable cause) {
        super(message, cause);
    }
}
