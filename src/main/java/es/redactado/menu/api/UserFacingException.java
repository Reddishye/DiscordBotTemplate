package es.redactado.menu.api;

/**
 * Signals a failure whose message is safe and useful to show to the end user.
 *
 * <p>Everything else that goes wrong is reported as a generic message plus a
 * reference code, with the real detail only in the log. A handler should throw
 * this for anything the user can act on, such as a malformed parameter or a
 * missing permission.
 *
 * <p>The message is currently plain English. The internationalization task
 * replaces it with a message key looked up in the resource bundles.
 */
public class UserFacingException extends MenuException {

    /**
     * Creates the exception with a message meant for the end user.
     *
     * @param message the text to show the user, never taken from a nested
     *     exception
     */
    public UserFacingException(String message) {
        super(message);
    }
}
