package es.redactado.menu.core;

import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.NavEntry;
import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.api.Session;
import es.redactado.menu.api.UserFacingException;
import es.redactado.menu.preset.Preset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Moves between views, using the session's history to support going back.
 *
 * <p>Every edit goes through the interaction hook, because the router has already
 * acknowledged the interaction.
 */
final class Navigator {

    private static final Logger LOG = LoggerFactory.getLogger(Navigator.class);

    private final Function<String, Menu> lookup;
    private final SessionStore sessions;
    private final Messages messages;
    private final PresetResolver presets;

    Navigator(
            Function<String, Menu> lookup,
            SessionStore sessions,
            Messages messages,
            PresetResolver presets) {
        this.lookup = lookup;
        this.sessions = sessions;
        this.messages = messages;
        this.presets = presets;
    }

    /**
     * Performs a navigation to a menu's home view.
     *
     * <p>Asks the target for its home entry rather than assuming {@code home}, because a
     * menu is free to name its own starting view.
     *
     * @param ctx the context of the interaction that triggered the navigation
     * @param mode how to move
     * @param targetMenuId the menu to show, ignored by {@link NavigationMode#BACK}
     * @return a completed future; navigation is in-memory and never blocks
     * @throws UserFacingException if the target menu is not registered
     */
    CompletableFuture<Void> go(MenuContext ctx, NavigationMode mode, String targetMenuId) {
        if (mode == NavigationMode.BACK) {
            return back(ctx);
        }
        return apply(ctx, mode, lookupMenu(targetMenuId).home(ctx));
    }

    /**
     * Performs a navigation to a named view.
     *
     * @param ctx the context of the interaction that triggered the navigation
     * @param mode how to move
     * @param target the view to show, ignored by {@link NavigationMode#BACK}
     * @return a completed future; navigation is in-memory and never blocks
     * @throws UserFacingException if the target menu is not registered
     */
    CompletableFuture<Void> go(MenuContext ctx, NavigationMode mode, NavEntry target) {
        if (mode == NavigationMode.BACK) {
            return back(ctx);
        }
        return apply(ctx, mode, target);
    }

    /**
     * Shows the target and then records what happened to the history.
     *
     * <p><strong>The view being left is read before the new one is rendered, and pushed
     * after it has been shown.</strong> Both halves matter, and in that order.
     *
     * <p>Reading first is what a menu with several views needs: it remembers which view it
     * is showing, and rendering the target overwrites that memory before anyone asked.
     *
     * <p>Pushing afterwards is what keeps the history honest. A view that fails to render,
     * such as one the menu does not know, must not leave a stack entry for a screen the
     * user never saw, because the next Back would return to it. The cost is that a Back
     * pressed while a slow view is still on its way finds an empty stack and lands on home.
     */
    private CompletableFuture<Void> apply(MenuContext ctx, NavigationMode mode, NavEntry entry) {
        return switch (mode) {
            case PUSH -> {
                NavEntry from = whereWeAre(ctx);
                yield show(ctx, entry).thenRun(() -> ctx.session().push(from));
            }
            case REPLACE -> show(ctx, entry);
            case ROOT ->
                    show(ctx, entry).thenRun(() -> findSession(ctx).ifPresent(Session::clearStack));
            case BACK -> back(ctx);
        };
    }

    /**
     * The view to remember as the one being left behind.
     *
     * <p>Asked of the menu being left, not of the context: a click names the button that
     * was pressed, and that is {@code nav}, not the screen it was pressed on. A menu with
     * several views overrides {@link Menu#currentView(MenuContext)} to say what is on
     * screen; one with a single view is right by default.
     */
    private NavEntry whereWeAre(MenuContext ctx) {
        return lookupMenu(ctx.menuId()).currentView(ctx);
    }

    private CompletableFuture<Void> back(MenuContext ctx) {
        Optional<Session> existing = findSession(ctx);
        if (existing.isEmpty()) {
            // The session expired, so there is no history to honour. Say so rather
            // than pretending the user never navigated, then land them somewhere
            // usable.
            Replies.ephemeral(ctx.event(), messages, ctx.locale(), MessageKeys.NAV_EXPIRED);
            return show(ctx, currentHome(ctx));
        }
        Optional<NavEntry> previous = existing.get().pop();
        return show(ctx, previous.orElseGet(() -> currentHome(ctx)));
    }

    private Optional<Session> findSession(MenuContext ctx) {
        return ctx.messageId().isEmpty()
                ? Optional.empty()
                : sessions.find(ctx.messageId().getAsLong());
    }

    private NavEntry currentHome(MenuContext ctx) {
        return new NavEntry(ctx.menuId(), "home", List.of());
    }

    private Menu lookupMenu(String menuId) {
        try {
            return lookup.apply(menuId);
        } catch (RuntimeException e) {
            LOG.debug("Navigation target '{}' is not a registered menu", menuId);
            throw new UserFacingException(MessageKeys.ERROR_UNKNOWN_MENU);
        }
    }

    /**
     * Renders a remembered view and sends it.
     *
     * <p>The target menu's own preset is resolved first, because the target may declare
     * a {@code presetName()} and is otherwise a different menu with different rules.
     * Rendering the target with whatever look the current menu was using would show one
     * menu in another's colours, and going back would have to undo it.
     *
     * <p>The edit happens only once the render future completes, so a view that loads
     * data asynchronously never leaves the message showing stale content. A failed
     * render propagates to whoever is handling the interaction rather than being
     * swallowed here.
     *
     * @return a future completing when the edit is sent
     */
    private CompletableFuture<Void> show(MenuContext ctx, NavEntry entry) {
        Menu menu = lookupMenu(entry.menuId());
        return resolveFor(ctx, menu)
                .thenCompose(
                        preset ->
                                menu.render(ctx.at(entry).withPreset(preset))
                                        .thenCompose(
                                                container ->
                                                        ViewEditor.edit(
                                                                ctx.event().getHook(), container)));
    }

    /**
     * Resolves the preset for a menu within an interaction already in flight.
     *
     * <p>Uses the same resolver and the same identifiers the router used, so a menu's
     * own declaration wins and a guild or user preference is honoured exactly as it was
     * on the way in.
     */
    private CompletableFuture<Preset> resolveFor(MenuContext ctx, Menu menu) {
        long guildId = idOf(ctx.guildId());
        return presets.resolve(menu, guildId, idOf(ctx.userId()));
    }

    /** A snowflake as a number, with an absent or unparseable value meaning none. */
    private static long idOf(String id) {
        if (id == null || id.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
