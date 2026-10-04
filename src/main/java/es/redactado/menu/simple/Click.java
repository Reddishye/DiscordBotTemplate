package es.redactado.menu.simple;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;

/**
 * A button press.
 *
 * <p>Adds the event itself, for the rare handler that needs something the context does not
 * carry, and {@link #modal(Modal)} for the flow where pressing a button asks the user a
 * question.
 */
public final class Click extends Trigger {

    private final ButtonInteractionEvent event;
    private final boolean mayOpenModal;

    Click(Support support, ButtonInteractionEvent event, boolean mayOpenModal) {
        super(support);
        this.event = event;
        this.mayOpenModal = mayOpenModal;
    }

    /**
     * The JDA event behind this click.
     *
     * @return the button interaction
     */
    public ButtonInteractionEvent event() {
        return event;
    }

    /**
     * Answers the interaction with a modal instead of a view.
     *
     * <p>Legal only on a button declared with {@code opensModal()}, because opening a modal
     * acknowledges the interaction and a modal has to be the first and only answer. The
     * framework refuses here rather than letting Discord refuse later, where the message
     * would be about a modal rather than about the button.
     *
     * @param modal the modal to open
     * @throws IllegalStateException if this button was not declared as opening a modal
     */
    public void modal(Modal modal) {
        if (!mayOpenModal) {
            throw new IllegalStateException(
                    "This button does not open a modal; declare it with opensModal()");
        }
        support().showModal(modal);
    }
}
