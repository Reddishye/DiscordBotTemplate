package es.redactado.menu.core;

import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.api.UserFacingException;

/**
 * A navigation button, encoded as {@code menu:<currentMenu>:nav:<mode>:<target>}.
 *
 * <p>{@code BACK} carries no target, so its id omits the last segment.
 */
public record NavigationAction(NavigationMode mode, String targetMenuId) {

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
            mode = NavigationMode.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UserFacingException(MessageKeys.ERROR_UNKNOWN_NAV_MODE);
        }
        String target = ctx.param(1).orElse("");
        return new NavigationAction(mode, target);
    }

    /**
     * Builds the custom id of a navigation button.
     *
     * @param currentMenuId the menu rendering the button
     * @param mode how the navigation should behave
     * @param targetMenuId the menu to show, ignored by {@link NavigationMode#BACK}
     * @return the component id
     */
    public static String buttonId(String currentMenuId, NavigationMode mode, String targetMenuId) {
        if (mode == NavigationMode.BACK) {
            return ComponentId.encode(
                    currentMenuId, "nav", mode.name().toLowerCase(java.util.Locale.ROOT));
        }
        return ComponentId.encode(
                currentMenuId, "nav", mode.name().toLowerCase(java.util.Locale.ROOT), targetMenuId);
    }
}
