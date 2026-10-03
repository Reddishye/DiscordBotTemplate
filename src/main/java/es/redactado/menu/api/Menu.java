package es.redactado.menu.api;

import java.util.List;
import net.dv8tion.jda.api.components.container.Container;

/**
 * A named, renderable menu that declares the actions it handles. Register it
 * with the router by id.
 */
public interface Menu {

    /**
     * Identifier used for routing.
     *
     * @return the menu id, for example {@code profile}
     */
    String id();

    /**
     * Builds the JDA container for the current context.
     *
     * @param ctx the context of the current interaction
     * @return the rendered container
     */
    Container render(MenuContext ctx);

    /**
     * The view this menu shows when it is opened or navigated to without history.
     *
     * <p>A menu's {@link #render(MenuContext)} must therefore handle the action
     * {@code home} and treat it as its initial view, since navigation renders
     * through this entry rather than through a click.
     *
     * @param ctx the context of the current interaction
     * @return the entry describing this menu's initial view
     */
    default NavEntry home(MenuContext ctx) {
        return new NavEntry(id(), "home", List.of());
    }

    /**
     * Whether this menu is visible to and usable by everyone.
     *
     * <p>A personal menu is bound to the user whose interaction produced its
     * message, and only that user may press its buttons. A shared menu skips that
     * check.
     *
     * @return {@code true} when the menu is not personal
     */
    default boolean shared() {
        return false;
    }

    /**
     * Declares every action this menu handles, together with how each one is
     * acknowledged.
     *
     * <p>Called exactly once by the router at registration, never from a
     * constructor, so the table can be immutable for the lifetime of the menu.
     *
     * @param table the builder to declare actions on
     */
    void actions(ActionTable.Builder table);
}
