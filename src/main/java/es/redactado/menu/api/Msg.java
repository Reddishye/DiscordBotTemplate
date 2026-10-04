package es.redactado.menu.api;

/**
 * A piece of user-facing text, resolved per interaction.
 *
 * <p>Two things a menu has to put in front of someone: a literal the author chose, and a
 * key that has to be translated for whoever is reading. Both are the same interface so a
 * view component takes either without knowing which it got.
 *
 * <p>There is no behaviour beyond resolving. A key that no bundle defines falls back to the
 * key itself, which is loud on purpose: a missing translation shows as
 * {@code menu.nav.back} rather than as an empty gap.
 */
@FunctionalInterface
public interface Msg {

    /**
     * Resolves the text for one interaction.
     *
     * @param ctx the context of the current interaction
     * @return the text to show, never null
     */
    String get(MenuContext ctx);

    /**
     * Text that is the same for everyone.
     *
     * <p>For content rather than chrome: a description, a URL, a value. A label that a user
     * reads should be a {@link #key(String, Object...)} so it can be translated.
     *
     * @param text the text
     * @return a message that always resolves to {@code text}
     */
    static Msg literal(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Literal message text must not be null");
        }
        return ctx -> text;
    }

    /**
     * A key declared in the bundles, with optional values for its placeholders.
     *
     * @param key a key from {@code MessageKeys} or a bundle of the bot's own
     * @param args values for the {@code {n}} placeholders in that message
     * @return a message that resolves the key for the interaction's locale
     */
    static Msg key(String key, Object... args) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("A message key must not be empty");
        }
        return ctx -> ctx.t(key, args);
    }
}
