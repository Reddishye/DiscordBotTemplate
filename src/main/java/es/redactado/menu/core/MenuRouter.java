package es.redactado.menu.core;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.ButtonAction;
import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.MenuNotFoundException;
import es.redactado.menu.api.ModalAction;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central dispatcher for menu interactions. Register menus by id, then wire it
 * into a listener.
 *
 * <p>Each menu's action table is built once at registration, so dispatch is two
 * map lookups and no string matching. Handlers run inline for now; T4 moves them
 * onto a dedicated executor.
 */
public class MenuRouter {

    private static final Logger LOG = LoggerFactory.getLogger(MenuRouter.class);

    private final Map<String, Registered> menus = new ConcurrentHashMap<>();

    /** A menu paired with the immutable action table built from it. */
    private record Registered(Menu menu, ActionTable table) {}

    /**
     * Registers a menu and builds its action table.
     *
     * @param id the id to route on
     * @param menu the menu
     * @throws IllegalArgumentException if {@code id} does not equal
     *     {@link Menu#id()}
     * @throws IllegalStateException if the id is already registered
     */
    public void register(String id, Menu menu) {
        if (!id.equals(menu.id())) {
            throw new IllegalArgumentException(
                    "Menu id '%s' does not match its own id() '%s'".formatted(id, menu.id()));
        }

        ActionTable.Builder builder = ActionTable.builder();
        menu.actions(builder);
        ActionTable table = builder.build();

        Registered registered = new Registered(menu, table);
        if (menus.putIfAbsent(id, registered) != null) {
            throw new IllegalStateException("Menu already registered: " + id);
        }
        LOG.info(
                "Menu registered: {} with {} button and {} modal actions",
                id,
                table.buttonCount(),
                table.modalCount());
    }

    /**
     * Reports whether an id is registered.
     *
     * @param id the menu id
     * @return {@code true} when a menu is registered under that id
     */
    public boolean isRegistered(String id) {
        return menus.containsKey(id);
    }

    /**
     * Renders a menu for a context.
     *
     * @param menuId the menu id
     * @param ctx the context of the current interaction
     * @return the rendered container
     * @throws MenuNotFoundException if no menu is registered under that id
     */
    public Container render(String menuId, MenuContext ctx) {
        return get(menuId).render(ctx);
    }

    /**
     * Looks up a registered menu.
     *
     * @param id the menu id
     * @return the menu
     * @throws MenuNotFoundException if no menu is registered under that id
     */
    public Menu get(String id) {
        Registered registered = menus.get(id);
        if (registered == null) {
            throw new MenuNotFoundException(id);
        }
        return registered.menu();
    }

    /**
     * Handles a button event if it belongs to a registered menu.
     *
     * @param event the JDA button event
     * @return {@code true} when the event was consumed
     */
    public boolean dispatchButton(ButtonInteractionEvent event) {
        Optional<ComponentId> parsed = ComponentId.decode(event.getComponentId());
        if (parsed.isEmpty()) {
            return false;
        }

        Registered registered = menus.get(parsed.get().menuId());
        if (registered == null) {
            LOG.warn("No menu registered for id: {}", parsed.get().menuId());
            return false;
        }

        Optional<ButtonAction> action = registered.table().button(parsed.get().action());
        if (action.isEmpty()) {
            LOG.warn(
                    "Menu '{}' has no button action '{}'",
                    parsed.get().menuId(),
                    parsed.get().action());
            Replies.ephemeral(event, Replies.UNKNOWN_ACTION);
            return true;
        }

        MenuContext ctx = BaseContext.fromButton(event, parsed.get());
        acknowledge(action.get().ack(), event);
        return run(event, parsed.get(), () -> action.get().handler().handle(ctx, event));
    }

    /**
     * Handles a modal submission if it belongs to a registered menu.
     *
     * @param event the JDA modal event
     * @return {@code true} when the event was consumed
     */
    public boolean dispatchModal(ModalInteractionEvent event) {
        Optional<ComponentId> parsed = ComponentId.decode(event.getModalId());
        if (parsed.isEmpty()) {
            return false;
        }

        Registered registered = menus.get(parsed.get().menuId());
        if (registered == null) {
            LOG.warn("No menu registered for modal id: {}", parsed.get().menuId());
            return false;
        }

        Optional<ModalAction> action = registered.table().modal(parsed.get().action());
        if (action.isEmpty()) {
            LOG.warn(
                    "Menu '{}' has no modal action '{}'",
                    parsed.get().menuId(),
                    parsed.get().action());
            Replies.ephemeral(event, Replies.UNKNOWN_ACTION);
            return true;
        }

        MenuContext ctx = BaseContext.fromModal(event, parsed.get());
        acknowledge(action.get().ack(), event);
        return run(event, parsed.get(), () -> action.get().handler().handle(ctx, event));
    }

    /**
     * Invokes a handler and reports any failure, whether thrown before the future
     * is returned or completed exceptionally afterwards.
     *
     * <p>This is the only place in the menu package that catches a broad
     * exception, which the code style permits solely in the top-level dispatcher.
     * A handler that throws before returning a future never produces a future to
     * attach to, so the synchronous case needs its own guard.
     */
    private static boolean run(
            IReplyCallback event, ComponentId id, Supplier<CompletableFuture<Void>> work) {
        CompletableFuture<Void> result;
        try {
            result = work.get();
        } catch (Exception error) {
            report(event, id, error);
            return true;
        }
        result.whenComplete(
                (ignored, error) -> {
                    if (error != null) {
                        report(event, id, error);
                    }
                });
        return true;
    }

    private static void report(IReplyCallback event, ComponentId id, Throwable error) {
        LOG.error("Action '{}' of menu '{}' failed", id.action(), id.menuId(), error);
        try {
            Replies.ephemeral(event, Replies.ERROR);
        } catch (Exception replyError) {
            LOG.warn("Failed to send the error reply for action '{}'", id.action(), replyError);
        }
    }

    private static void acknowledge(Ack ack, ButtonInteractionEvent event) {
        switch (ack) {
            case DEFER_EDIT -> event.deferEdit().queue();
            case DEFER_REPLY -> event.deferReply(true).queue();
            case MODAL, NONE -> {}
        }
    }

    private static void acknowledge(Ack ack, ModalInteractionEvent event) {
        switch (ack) {
            case DEFER_EDIT -> event.deferEdit().queue();
            case DEFER_REPLY -> event.deferReply(true).queue();
            case MODAL, NONE -> {}
        }
    }
}
