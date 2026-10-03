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
import net.dv8tion.jda.api.components.container.Container;
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
    private static final String EXPIRED = "This menu expired.";

    private final Function<String, Menu> lookup;
    private final SessionStore sessions;

    Navigator(Function<String, Menu> lookup, SessionStore sessions) {
        this.lookup = lookup;
        this.sessions = sessions;
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
        switch (mode) {
            case PUSH -> {
                ctx.session().push(currentEntry(ctx));
                show(ctx, homeOf(ctx, targetMenuId));
            }
            case REPLACE -> show(ctx, homeOf(ctx, targetMenuId));
            case ROOT -> {
                findSession(ctx).ifPresent(Session::clearStack);
                show(ctx, homeOf(ctx, targetMenuId));
            }
            case BACK -> back(ctx);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void back(MenuContext ctx) {
        Optional<Session> existing = findSession(ctx);
        if (existing.isEmpty()) {
            // The session expired, so there is no history to honour. Say so rather
            // than pretending the user never navigated, then land them somewhere
            // usable.
            Replies.ephemeral(ctx.event(), EXPIRED);
            show(ctx, currentHome(ctx));
            return;
        }
        Optional<NavEntry> previous = existing.get().pop();
        if (previous.isEmpty()) {
            show(ctx, currentHome(ctx));
            return;
        }
        show(ctx, previous.get());
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
            throw new UserFacingException("Unknown menu.");
        }
    }

    private void show(MenuContext ctx, NavEntry entry) {
        Menu menu = lookupMenu(entry.menuId());
        Container container = menu.render(ctx.at(entry));
        ViewEditor.edit(ctx.event().getHook(), container);
    }
}
