package es.redactado.menu.api;

/**
 * Signals a failure whose text is safe and useful to show to the end user.
 *
 * <p>Everything else that goes wrong is reported as a generic message plus a reference
 * code, with the real detail only in the log. A handler should throw this for anything
 * the user can act on, such as a malformed parameter or a missing permission.
 *
 * <p>The text lives in a resource bundle, so this carries a key and its arguments
 * rather than a sentence. It cannot be translated when it is thrown, because the
 * exception does not know who will read it, nor in what language; the reply path
 * resolves the key with the locale of the interaction that failed. Declaring the text
 * here would bake in English.
 *
 * <p>Use a constant from {@code MessageKeys} for the key, never a literal.
 */
public class UserFacingException extends MenuException {

    private final String key;
    private final Object[] args;

    /**
     * Creates the exception for a message key.
     *
     * @param key a key declared in {@code MessageKeys}
     * @param args values for the {@code {n}} placeholders in that message
     */
    public UserFacingException(String key, Object... args) {
        super(key);
        this.key = key;
        this.args = args == null ? new Object[0] : args.clone();
    }

    /**
     * The message key, resolved later against the reader's locale.
     *
     * @return the key from {@code MessageKeys}
     */
    public String key() {
        return key;
    }

    /**
     * The placeholder values.
     *
     * @return a copy of the arguments, empty when there are none
     */
    public Object[] args() {
        return args.clone();
    }
}
