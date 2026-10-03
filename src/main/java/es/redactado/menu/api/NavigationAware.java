package es.redactado.menu.api;

/**
 * Implemented by menus that need to observe navigation. The source router never
 * invokes these hooks, so nothing depends on them yet.
 */
public interface NavigationAware {

    /**
     * Called when navigating to this menu.
     *
     * @param ctx the context of the current interaction
     */
    default void onNavigate(Context ctx) {}

    /**
     * Called when navigating away from this menu.
     *
     * @param ctx the context of the current interaction
     * @return {@code false} to block the navigation
     */
    default boolean onNavigateAway(Context ctx) {
        return true;
    }
}
