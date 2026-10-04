package es.redactado.menu.api;

import java.util.List;
import java.util.Objects;

/**
 * One position in a menu's navigation history: which menu, which action, and the
 * parameters that action was invoked with.
 *
 * <p>Enough information to rebuild the exact view that was on screen, so going
 * back restores it rather than approximating it from the menu's home view.
 *
 * @param menuId the menu the view belongs to
 * @param action the action that produced the view
 * @param params the parameters the action was invoked with, possibly empty
 */
public record NavEntry(String menuId, String action, List<String> params) {

    public NavEntry {
        Objects.requireNonNull(menuId, "menuId");
        if (menuId.isEmpty()) {
            throw new IllegalArgumentException("menuId must not be empty");
        }
        Objects.requireNonNull(action, "action");
        params = List.copyOf(Objects.requireNonNull(params, "params"));
    }
}
