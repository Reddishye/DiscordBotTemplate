package es.redactado.menu.core;

import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.NavEntry;
import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.api.UserFacingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A navigation button, encoded as
 * {@code menu:<currentMenu>:nav:<mode>[:<targetMenu>[:<viewAction>[:<param>...]]]}.
 *
 * <p>The target is a view, not only a menu. A menu with one screen needs nothing more
 * than a menu id, and omitting the rest keeps the id short; a menu with several screens
 * names the view it wants and its params, so going back can rebuild exactly what was
 * on screen.
 *
 * <p><strong>A missing view segment means the home view.</strong> That is what makes the
 * short form and the long form the same instruction, and it is why an id written before
 * views existed still resolves.
 *
 * <p>{@link NavigationMode#BACK} carries no target at all, so its id omits those
 * segments: there is nothing to go back to but the history.
 */
public record NavigationAction(NavigationMode mode, NavEntry target) {

    /** The view a target names when the id does not. */
    public static final String HOME_VIEW = "home";

    /** The action name every navigation id uses. */
    public static final String ACTION = "nav";

    public NavigationAction {
        if (mode == NavigationMode.BACK && target != null) {
            throw new IllegalArgumentException("A back navigation carries no target");
        }
        if (mode != NavigationMode.BACK && target == null) {
            throw new IllegalArgumentException("A " + mode + " navigation needs a target");
        }
    }

    /**
     * Parses a navigation action from a context whose action is {@code nav}.
     *
     * @param ctx the context of the current interaction
     * @return the parsed action
     * @throws UserFacingException if the mode is missing or not a known mode
     */
    public static NavigationAction fromContext(MenuContext ctx) {
        String raw = ctx.requireString(0);
        NavigationMode mode;
        try {
            mode = NavigationMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UserFacingException(MessageKeys.ERROR_UNKNOWN_NAV_MODE);
        }
        if (mode == NavigationMode.BACK) {
            return new NavigationAction(mode, null);
        }
        String menuId = ctx.param(1).orElse("");
        if (menuId.isEmpty()) {
            // Said as an unknown menu rather than a parse error, because that is what it is
            // from a user's point of view and it is the message the lookup would produce.
            throw new UserFacingException(MessageKeys.ERROR_UNKNOWN_MENU);
        }
        return new NavigationAction(
                mode, new NavEntry(menuId, ctx.param(2).orElse(HOME_VIEW), tail(ctx, 3)));
    }

    /**
     * Builds the id of a navigation that targets a menu's home view.
     *
     * <p>The short form, with no view segment, which is what a menu with one screen wants
     * and what every id written before views existed looks like.
     *
     * @param currentMenuId the menu rendering the button
     * @param mode how the navigation should behave
     * @param targetMenuId the menu to show, ignored by {@link NavigationMode#BACK}
     * @return the component id
     * @throws IllegalArgumentException if the result would exceed the custom-id limit
     */
    public static String buttonId(String currentMenuId, NavigationMode mode, String targetMenuId) {
        if (mode == NavigationMode.BACK) {
            return backId(currentMenuId);
        }
        return ComponentId.encode(currentMenuId, ACTION, segment(mode), targetMenuId);
    }

    /**
     * Builds the id of a navigation that targets a view.
     *
     * @param currentMenuId the menu rendering the button
     * @param mode how the navigation should behave
     * @param target the menu, view and params to show
     * @return the component id
     * @throws IllegalArgumentException if the result would exceed the custom-id limit
     */
    public static String buttonId(String currentMenuId, NavigationMode mode, NavEntry target) {
        if (mode == NavigationMode.BACK) {
            return backId(currentMenuId);
        }
        if (target == null) {
            throw new IllegalArgumentException("A " + mode + " navigation needs a target");
        }
        List<String> segments = new ArrayList<>(3 + target.params().size());
        segments.add(segment(mode));
        segments.add(target.menuId());
        segments.add(target.action());
        segments.addAll(target.params());
        return ComponentId.encode(currentMenuId, ACTION, segments.toArray(new String[0]));
    }

    private static String backId(String currentMenuId) {
        return ComponentId.encode(currentMenuId, ACTION, segment(NavigationMode.BACK));
    }

    private static String segment(NavigationMode mode) {
        return mode.name().toLowerCase(Locale.ROOT);
    }

    /**
     * The params from the given index onwards.
     *
     * <p>Guarded because an id may carry fewer segments than a full target needs: a
     * menu-only id stops after the menu, and asking for the view from it must not throw.
     */
    private static List<String> tail(MenuContext ctx, int from) {
        List<String> params = ctx.params();
        return from >= params.size() ? List.of() : List.copyOf(params.subList(from, params.size()));
    }
}
