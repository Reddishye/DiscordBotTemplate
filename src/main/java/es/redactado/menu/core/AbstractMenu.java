package es.redactado.menu.core;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.Loader;
import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.Renderer;
import es.redactado.menu.api.UserFacingException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for menus that build a container and declare their actions.
 *
 * <p>Subclasses implement {@link #declare(ActionTable.Builder)} and
 * {@link #render(MenuContext)}. The built-in {@code nav} action is registered for
 * every subclass, so a subclass cannot redeclare it.
 *
 * <p>Every edit goes through {@link ViewEditor}, never through the interaction
 * itself, because the router has already acknowledged the interaction by the time
 * a handler runs.
 */
public abstract class AbstractMenu implements Menu {

    /** The action name of the built-in back navigation button. */
    protected static final String NAV_ACTION = "nav";

    private static final Logger LOG = LoggerFactory.getLogger(AbstractMenu.class);
    private static final Duration DEFAULT_LOAD_TIMEOUT = Duration.ofSeconds(10);

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
     * How long {@link #view(MenuContext, Loader, Renderer)} waits for its loader.
     *
     * <p>Exceeding it does not break Discord's three-second rule, because the
     * interaction was already acknowledged before the load started. It only means
     * the user would wait forever, so past the timeout they get a message.
     *
     * @return the loader timeout, 10 seconds by default
     */
    protected Duration loadTimeout() {
        return DEFAULT_LOAD_TIMEOUT;
    }

    /**
     * Loads a model and renders it, applying {@link #loadTimeout()}.
     *
     * <p>A loader that throws synchronously becomes a failed future rather than a
     * thrown exception, so writing the loader inline cannot break the dispatcher's
     * contract.
     *
     * @param ctx the context of the current interaction
     * @param loader fetches the data
     * @param renderer turns the data into a container
     * @param <M> the model type
     * @return a future for the rendered container
     */
    protected final <M> CompletableFuture<Container> view(
            MenuContext ctx, Loader<M> loader, Renderer<M> renderer) {
        return guard(loader, ctx)
                .orTimeout(loadTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .exceptionallyCompose(
                        error ->
                                error instanceof TimeoutException
                                        ? CompletableFuture.failedFuture(
                                                new UserFacingException(
                                                        MessageKeys.ERROR_LOAD_TIMEOUT))
                                        : CompletableFuture.failedFuture(error))
                .thenApply(model -> renderer.render(ctx, model));
    }

    private static <M> CompletableFuture<M> guard(Loader<M> loader, MenuContext ctx) {
        try {
            return loader.load(ctx);
        } catch (RuntimeException e) {
            LOG.debug("Loader threw before returning a future", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Re-renders this menu into the original message.
     *
     * @param ctx the context of the current interaction
     * @return a future completing when the edit is sent
     * @throws IllegalStateException if the interaction was not acknowledged
     */
    protected CompletableFuture<Void> refresh(MenuContext ctx) {
        IReplyCallback event = ctx.event();
        if (!event.isAcknowledged()) {
            throw new IllegalStateException(UNACKNOWLEDGED);
        }
        return render(ctx).thenCompose(container -> ViewEditor.edit(event.getHook(), container));
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
}
