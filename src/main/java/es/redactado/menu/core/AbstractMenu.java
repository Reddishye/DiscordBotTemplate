package es.redactado.menu.core;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for menus that build a container and declare their actions.
 *
 * <p>Subclasses implement {@link #build(MenuContext)} and
 * {@link #declare(ActionTable.Builder)}. The built-in {@code nav} action is
 * registered for every subclass, so a subclass cannot redeclare it.
 *
 * <p>Every edit goes through the interaction hook, never through the interaction
 * itself, because the router has already acknowledged the interaction by the time
 * a handler runs.
 */
public abstract class AbstractMenu implements Menu {

    /** The action name of the built-in back navigation button. */
    protected static final String NAV_ACTION = "nav";

    private static final String UNACKNOWLEDGED =
            "Action must declare an ack mode that acknowledges the interaction";

    protected final Logger log = LoggerFactory.getLogger(getClass());
    private final String id;

    protected AbstractMenu(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Container render(MenuContext ctx) {
        return build(ctx);
    }

    @Override
    public final void actions(ActionTable.Builder table) {
        table.button(NAV_ACTION, Ack.DEFER_EDIT, AbstractMenu::navigate);
        declare(table);
    }

    /**
     * Handles the built-in {@code nav} action by delegating to the navigator, which
     * owns the session history.
     */
    private static CompletableFuture<Void> navigate(MenuContext ctx, ButtonInteractionEvent event) {
        NavigationAction navigation = NavigationAction.fromContext(ctx);
        return ctx.navigate(navigation.mode(), navigation.targetMenuId());
    }

    /**
     * Declares the subclass's own actions. The built-in {@code nav} action is
     * already registered and must not be declared again.
     *
     * @param table the builder to declare actions on
     */
    protected abstract void declare(ActionTable.Builder table);

    /**
     * Builds the container for the current context.
     *
     * @param ctx the context of the current interaction
     * @return the rendered container
     */
    protected abstract Container build(MenuContext ctx);

    /**
     * Re-renders this menu into the original message.
     *
     * @param ctx the context of the current interaction
     * @throws IllegalStateException if the interaction was not acknowledged
     */
    protected void refresh(MenuContext ctx) {
        ViewEditor.edit(acknowledgedHook(ctx.event()), render(ctx));
    }

    /**
     * Opens a modal.
     *
     * <p>Legal only on an interaction the router did not acknowledge, which in
     * practice means only on an action declaring {@link Ack#MODAL} or
     * {@link Ack#NONE}.
     *
     * @param ctx the context of the current interaction
     * @param modal the modal to open
     * @throws IllegalStateException if the interaction was already acknowledged
     */
    protected void showModal(MenuContext ctx, Modal modal) {
        if (ctx.event().isAcknowledged()) {
            throw new IllegalStateException(UNACKNOWLEDGED);
        }
        if (ctx.event() instanceof IModalCallback callback) {
            callback.replyModal(modal).queue();
            return;
        }
        throw new IllegalStateException("This interaction type cannot open a modal");
    }

    private static InteractionHook acknowledgedHook(IReplyCallback event) {
        if (!event.isAcknowledged()) {
            throw new IllegalStateException(UNACKNOWLEDGED);
        }
        return event.getHook();
    }
}
