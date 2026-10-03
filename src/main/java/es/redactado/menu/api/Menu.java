package es.redactado.menu.api;

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
