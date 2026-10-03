package es.redactado.menu.core;

/**
 * Every user-facing message key, so no code has to spell one out.
 *
 * <p>A bare string literal in a call that shows something to a user is invisible in a
 * review and impossible to translate. Going through a constant makes the key
 * greppable, lets {@code MessageKeysTest} prove every key exists in the bundles and
 * is actually used, and means a typo is a compile error rather than a message that
 * quietly falls back to its own key.
 *
 * <p>Grouped by prefix. Keys are snake_case and namespaced by area, so
 * {@code menu.error.*} covers everything the router tells a user and
 * {@code menu.nav.*} covers navigation.
 *
 * <p>Messages meant for developers are not here and are not translated: the text of
 * an {@code IllegalArgumentException} or {@code IllegalStateException}, and every log
 * line, stay English literals.
 */
public final class MessageKeys {

    private MessageKeys() {}

    // menu.error: what the user is told when an interaction cannot proceed
    public static final String ERROR_UNKNOWN_ACTION = "menu.error.unknown_action";
    public static final String ERROR_NOT_OWNER = "menu.error.not_owner";
    public static final String ERROR_BUSY = "menu.error.busy";
    public static final String ERROR_GENERIC = "menu.error.generic";
    public static final String ERROR_LOAD_TIMEOUT = "menu.error.load_timeout";
    public static final String ERROR_BAD_PARAM = "menu.error.bad_param";
    public static final String ERROR_UNKNOWN_MENU = "menu.error.unknown_menu";
    public static final String ERROR_UNKNOWN_NAV_MODE = "menu.error.unknown_nav_mode";

    // menu.nav: navigation and session lifetime
    public static final String NAV_EXPIRED = "menu.nav.expired";

    // menu.field and menu.list: default view text
    public static final String FIELD_NOT_SET = "menu.field.not_set";
    public static final String LIST_EMPTY = "menu.list.empty";
}
