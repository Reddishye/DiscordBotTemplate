package es.redactado.menu.core;

import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.UserFacingException;
import java.util.List;
import java.util.Locale;

/**
 * The built-in {@code page} action, decoded from a page button's id.
 *
 * <p>Lives in {@code core} rather than next to the pager because the handler in
 * {@code AbstractMenu} needs to read and write the stored page, and {@code core} does not
 * depend on {@code view}. Putting the key convention here as well as the decoding keeps
 * the two halves of the feature in one file, which is what stops them disagreeing.
 *
 * <p>The view being paged travels in the id, so the handler can re-render exactly what the
 * user was looking at without the menu keeping a stack of its own.
 */
public record PageAction(
        Direction direction, String pagerId, String viewAction, List<String> viewParams) {

    /**
     * Which way the user is moving.
     *
     * <p>The delta is stored on the constant rather than computed from the name so the
     * two cannot fall out of step.
     */
    public enum Direction {
        PREV(-1),
        NEXT(1);

        private final int delta;

        Direction(int delta) {
            this.delta = delta;
        }

        /**
         * How far the page moves.
         *
         * @return {@code -1} or {@code 1}
         */
        public int delta() {
            return delta;
        }
    }

    /**
     * The key a pager's current page is stored under.
     *
     * @param pagerId the pager's id
     * @return the session state key
     */
    public static String stateKey(String pagerId) {
        return "pager:" + pagerId;
    }

    /**
     * Decodes a page action from a context whose action is {@code page}.
     *
     * @param ctx the context of the current interaction
     * @return the decoded action
     * @throws UserFacingException if the id does not have the shape a pager writes
     */
    public static PageAction fromContext(MenuContext ctx) {
        Direction direction = directionOf(ctx.requireString(0));
        String pagerId = ctx.requireString(1);
        String viewAction = ctx.requireString(2);

        List<String> viewParams = List.of();
        for (int i = 3; i < ctx.params().size(); i++) {
            String param = ctx.param(i).orElseThrow();
            List<String> grown = new java.util.ArrayList<>(viewParams);
            grown.add(param);
            viewParams = List.copyOf(grown);
        }
        return new PageAction(direction, pagerId, viewAction, viewParams);
    }

    /**
     * Reads a direction word.
     *
     * <p>An unknown word is a user-facing failure rather than an
     * {@code IllegalArgumentException}, because the value came from a custom id and a
     * hand-edited or truncated one must produce a message the user can act on, not a
     * generic error.
     *
     * @param word {@code prev} or {@code next}
     * @return the direction
     * @throws UserFacingException if the word is neither
     */
    private static Direction directionOf(String word) {
        try {
            return Direction.valueOf(word.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UserFacingException(MessageKeys.ERROR_BAD_PARAM);
        }
    }
}
