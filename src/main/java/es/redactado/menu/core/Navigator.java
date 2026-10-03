package es.redactado.menu.core;

import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.NavEntry;
import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.api.Session;
import es.redactado.menu.api.UserFacingException;
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

    Navigator(Function<String, Menu> lookup, SessionStore sessions, Messages messages) {
        this.lookup = lookup;
        this.sessions = sessions;
        this.messages = messages;
    }

    /**
     * Performs a navigation and shows the resulting view.
     *
     * @param ctx the context of the interaction that triggered the navigation
     * @param mode how to move
     * @param targetMenuId the menu to show, ignored by {@link NavigationMode#BACK}
     * @return a completed future; navigation is in-memory and never blocks
     * @throws UserFacingException if the target menu is not registered
     */
    CompletableFuture<Void> go(MenuContext ctx, NavigationMode mode, String targetMenuId) {
        return switch (mode) {
            case PUSH -> {
                ctx.session().push(currentEntry(ctx));
                yield show(ctx, homeOf(ctx, targetMenuId));
            }
            case REPLACE -> show(ctx, homeOf(ctx, targetMenuId));
            case ROOT -> {
                findSession(ctx).ifPresent(Session::clearStack);
                yield show(ctx, homeOf(ctx, targetMenuId));
            }
            case BACK -> back(ctx);
        };
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

    private NavEntry currentEntry(MenuContext ctx) {
        return new NavEntry(ctx.menuId(), ctx.action(), ctx.params());
    }

    private NavEntry currentHome(MenuContext ctx) {
        return new NavEntry(ctx.menuId(), "home", List.of());
    }

    private NavEntry homeOf(MenuContext ctx, String targetMenuId) {
        Menu target = lookupMenu(targetMenuId);
        return target.home(ctx);
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
     * <p>The edit happens only once the render future completes, so a view that
     * loads data asynchronously never leaves the message showing stale content. A
     * failed render propagates to whoever is handling the interaction rather than
     * being swallowed here.
     *
     * @return a future completing when the edit is sent
     */
    private CompletableFuture<Void> show(MenuContext ctx, NavEntry entry) {
        Menu menu = lookupMenu(entry.menuId());
        return menu.render(ctx.at(entry))
                .thenCompose(container -> ViewEditor.edit(ctx.event().getHook(), container));
    }
}
