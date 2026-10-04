package es.redactado.menu.api;

/** Raised when a component id names a menu that is not registered. */
public class MenuNotFoundException extends MenuException {

    private final String menuId;

    /**
     * Creates the exception for a missing menu.
     *
     * @param menuId the id that could not be resolved
     */
    public MenuNotFoundException(String menuId) {
        super("Menu not found: " + menuId);
        this.menuId = menuId;
    }

    /**
     * The menu id that could not be resolved.
     *
     * @return the menu id
     */
    public String menuId() {
        return menuId;
    }
}
