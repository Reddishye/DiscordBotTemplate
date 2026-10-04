package es.redactado.menu.simple;

import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.Msg;
import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.api.UserFacingException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.modals.Modal;

/**
 * What a handler can do to the menu it was declared in.
 *
 * <p>Everything a simple-menu handler needs to move around, redraw and answer, without
 * the handler knowing anything about the router, the navigator or the session.
 *
 * <p>Each concrete trigger adds the one thing that is specific to how it was reached:
 * {@link Click} has the button event and can open a modal, {@link Pick} has the chosen
 * values, {@link Submit} has the submitted fields.
 *
 * <p>Not thread-safe: one trigger belongs to one interaction, which is over long before the
 * handler's future completes.
 */
public abstract class Trigger {

    /**
     * What a trigger needs from the menu that created it.
     *
     * <p>Package-private because only {@link SimpleMenu} implements it, and it exists so
     * that {@link #refresh()} and {@link Click#modal(Modal)} can reach the protected members
     * of the menu's base class without this type knowing what that class is.
     */
    interface Support {
        MenuContext ctx();

        CompletableFuture<Void> refresh();

        CompletableFuture<Void> go(String menuId, String viewName);

        CompletableFuture<Void> done();

        void showModal(Modal modal);
    }

    private final Support support;

    /**
     * @param support the menu that owns this interaction
     */
    Trigger(Support support) {
        this.support = support;
    }

    /**
     * The context of the interaction being handled.
     *
     * @return the context, never null
     */
    public MenuContext ctx() {
        return support.ctx();
    }

    /**
     * Redraws the view this interaction belongs to.
     *
     * <p>Which view that is, is worked out from the action, so a handler written once
     * redraws the right thing whether the user is looking at the home view or five views
     * deep. The edit goes through the framework's single edit path.
     *
     * @return a future completing when the redraw has been sent
     */
    public CompletableFuture<Void> refresh() {
        return support.refresh();
    }

    /**
     * Opens a view of this menu, keeping this one in history so Back returns here.
     *
     * @param viewName a view declared on this menu
     * @return a future completing when the view has been shown
     * @throws UserFacingException if the menu does not know the view
     */
    public CompletableFuture<Void> go(String viewName) {
        return support.go(ctx().menuId(), viewName);
    }

    /**
     * Opens a view of another menu, keeping this one in history.
     *
     * @param menuId a registered menu
     * @param viewName a view that menu declares
     * @return a future completing when the view has been shown
     * @throws UserFacingException if the menu does not know the view
     */
    public CompletableFuture<Void> go(String menuId, String viewName) {
        return support.go(menuId, viewName);
    }

    /**
     * Returns to the previous view.
     *
     * @return a future completing when the view has been shown
     */
    public CompletableFuture<Void> back() {
        return ctx().navigate(NavigationMode.BACK, "");
    }

    /**
     * Reads a state value from this message's session, without creating one.
     *
     * @param key the state key
     * @param type the expected value type
     * @param <T> the expected value type
     * @return the value, or empty
     */
    public <T> Optional<T> sessionState(String key, Class<T> type) {
        return ctx().sessionState(key, type);
    }

    /**
     * Reads a state value from this message's session, falling back rather than creating one.
     *
     * @param key the state key
     * @param type the expected value type
     * @param fallback the value to answer with when there is none
     * @param <T> the value type
     * @return the value, or {@code fallback}
     */
    public <T> T sessionStateOr(String key, Class<T> type, T fallback) {
        return ctx().sessionStateOr(key, type, fallback);
    }

    /**
     * Stores a state value, creating the session on the first write.
     *
     * @param key the state key
     * @param value the value, which must not be null
     */
    public void putSessionState(String key, Object value) {
        ctx().putSessionState(key, value);
    }

    /**
     * Removes a state value from this message's session.
     *
     * @param key the state key
     */
    public void removeSessionState(String key) {
        ctx().removeSessionState(key);
    }

    /**
     * Sends an ephemeral follow-up through the hook, in the reader's language.
     *
     * <p>Fire and forget, like every JDA call here: there is nothing for a handler to await
     * and nothing useful it could do if the send failed.
     *
     * @param message the text to send, resolved for this interaction
     */
    public void reply(Msg message) {
        send(message.get(ctx()));
    }

    /**
     * Sends a localized ephemeral follow-up.
     *
     * @param key a key declared in the bundles
     * @param args values for the {@code {n}} placeholders in that message
     */
    public void reply(String key, Object... args) {
        send(ctx().t(key, args));
    }

    private void send(String text) {
        if (ctx().event().isAcknowledged()) {
            ctx().event().getHook().sendMessage(text).setEphemeral(true).queue();
        } else {
            ctx().event().reply(text).setEphemeral(true).queue();
        }
    }

    /**
     * A future that is already done, for a handler that changed nothing.
     *
     * @return a completed future
     */
    public CompletableFuture<Void> done() {
        return support.done();
    }

    /**
     * The menu behind this trigger, for the triggers that add something of their own.
     *
     * <p>Package-private because {@link Support} is: a subclass outside this package has
     * nothing to do with it.
     */
    final Support support() {
        return support;
    }
}
