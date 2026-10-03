package es.redactado.menu.core;

import es.redactado.menu.api.Context;

/**
 * Describes a navigation action triggered by a button click.
 * Encoded as custom ID: {@code menu:<currentMenu>:nav:<mode>:<targetMenu>}
 */
public record NavigationAction(NavigationMode mode, String targetMenuId) {

    /** Parse from a Context whose action is "nav". */
    public static NavigationAction fromContext(Context ctx) {
        var mode = NavigationMode.valueOf(ctx.param(0).orElse("PUSH").toUpperCase());
        var target =
                ctx.param(1).orElseThrow(() -> new IllegalArgumentException("Missing target menu"));
        return new NavigationAction(mode, target);
    }

    /** Generate a custom ID for a navigation button. */
    public static String buttonId(String currentMenuId, NavigationMode mode, String targetMenuId) {
        return ComponentId.encode(currentMenuId, "nav", mode.name().toLowerCase(), targetMenuId);
    }
}
