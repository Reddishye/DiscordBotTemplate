package es.redactado.menu.api;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/**
 * A named, renderable menu that handles button and modal interactions. Register
 * it with the router by id.
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
     * Handles a button click belonging to this menu.
     *
     * @param ctx the context of the current interaction
     * @param event the JDA button event
     */
    default void onButton(MenuContext ctx, ButtonInteractionEvent event) {}

    /**
     * Handles a modal submission belonging to this menu.
     *
     * @param ctx the context of the current interaction
     * @param event the JDA modal event
     */
    default void onModal(MenuContext ctx, ModalInteractionEvent event) {}
}
