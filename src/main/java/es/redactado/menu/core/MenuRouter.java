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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
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
 * map lookups and no string matching.
 *
 * <p>The interaction is acknowledged on the calling JDA thread, because Discord
 * only allows three seconds for that, and the handler body then runs on the
 * router's executor, because a handler may block. The owner check and the
 * per-message re-entrancy claim both happen before the acknowledgement, since
 * neither should cost the user their interaction.
 *
 * <p>Close the router to release the executor. The template does not wire this up
 * yet.
 */
public final class MenuRouter implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MenuRouter.class);

    /** Sentinel for an interaction with no menu message, such as a bare modal. */
    private static final long NO_MESSAGE = -1L;

    private static final String NOT_YOURS = "This menu is not yours.";
    private static final String BUSY = "The bot is busy. Try again.";

    private final Map<String, Registered> menus = new ConcurrentHashMap<>();
    private final MenuExecutor executor;
    private final SessionStore sessions;
    private final InteractionGuard guard = new InteractionGuard();
    private final Navigator navigator;

    /** A menu paired with the immutable action table built from it. */
    private record Registered(Menu menu, ActionTable table) {}

    /**
     * Creates a router that runs handlers on the given executor and keeps
     * navigation history in the given store.
     *
     * @param executor the executor that runs handler bodies
     * @param sessions the store holding one session per menu message
     */
    public MenuRouter(MenuExecutor executor, SessionStore sessions) {
        this.executor = executor;
        this.sessions = sessions;
        this.navigator = new Navigator(this::get, sessions);
    }

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

        if (menus.putIfAbsent(id, new Registered(menu, table)) != null) {
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
     * @return a future for the rendered container
     * @throws MenuNotFoundException if no menu is registered under that id
     */
    public CompletableFuture<Container> render(String menuId, MenuContext ctx) {
        return get(menuId).render(ctx);
    }

    /**
     * The executor that runs handler bodies.
     *
     * <p>Exposed so a menu can be constructed with it and use
     * {@link MenuExecutor#supply(java.util.function.Supplier)} to call a blocking
     * service without touching a JDA thread.
     *
     * @return this router's executor
     */
    public MenuExecutor executor() {
        return executor;
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

        long messageId = messageIdOf(event.getMessage());
        if (!admit(registered.menu(), event, messageId)) {
            return true;
        }

        Ack ack = action.get().ack();
        acknowledge(ack, event);
        submit(
                event,
                messageId,
                parsed.get(),
                () ->
                        action.get()
                                .handler()
                                .handle(
                                        BaseContext.fromButton(
                                                event, parsed.get(), sessions, navigator),
                                        event));
        return true;
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

        long messageId = messageIdOf(event.getMessage());
        if (!admit(registered.menu(), event, messageId)) {
            return true;
        }

        Ack ack = action.get().ack();
        acknowledge(ack, event);
        submit(
                event,
                messageId,
                parsed.get(),
                () ->
                        action.get()
                                .handler()
                                .handle(
                                        BaseContext.fromModal(
                                                event, parsed.get(), sessions, navigator),
                                        event));
        return true;
    }

    /**
     * Runs the owner check, then claims the message, in that order.
     *
     * <p>Both happen before the acknowledgement so that a rejection costs nothing.
     * A duplicate click is swallowed with a deferred edit, because the first click
     * already owns the message and will produce the visible result.
     *
     * @return {@code true} when the interaction may proceed
     */
    private boolean admit(Menu menu, IReplyCallback event, long messageId) {
        if (!owns(menu, messageId(event), event)) {
            Replies.ephemeral(event, NOT_YOURS);
            return false;
        }
        if (messageId != NO_MESSAGE && !guard.tryAcquire(messageId)) {
            LOG.debug("Dropping a duplicate interaction on message {}", messageId);
            if (event instanceof ButtonInteractionEvent button) {
                button.deferEdit().queue();
            } else if (event instanceof ModalInteractionEvent modal) {
                modal.deferEdit().queue();
            }
            return false;
        }
        return true;
    }

    /**
     * Decides whether the interacting user may press this menu's buttons.
     *
     * <p>A menu is personal unless it declares itself shared. A message that was
     * not produced by an interaction carries no interaction metadata, which means
     * it was sent directly to the channel and has no single owner, so the click is
     * allowed. Allocating nothing on the allowed path matters, because this runs
     * on the JDA thread for every click.
     */
    private static boolean owns(Menu menu, Message message, IReplyCallback event) {
        if (menu.shared() || message == null) {
            return true;
        }
        Message.InteractionMetadata metadata = message.getInteractionMetadata();
        if (metadata == null) {
            return true;
        }
        User owner = metadata.getUser();
        if (owner == null) {
            return true;
        }
        return owner.getIdLong() == event.getUser().getIdLong();
    }

    private static Message messageId(IReplyCallback event) {
        return event instanceof ButtonInteractionEvent button
                ? button.getMessage()
                : event instanceof ModalInteractionEvent modal ? modal.getMessage() : null;
    }

    private static long messageIdOf(Message message) {
        return message == null ? NO_MESSAGE : message.getIdLong();
    }

    /**
     * Hands the handler body to the executor and arranges for the message claim to
     * be released exactly once, whether the handler succeeds, throws, or never
     * completes its future.
     */
    private void submit(
            IReplyCallback event,
            long messageId,
            ComponentId id,
            Supplier<CompletableFuture<Void>> work) {
        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> releaseOnce(messageId, released);
        try {
            executor.execute(
                    () -> {
                        try {
                            work.get()
                                    .whenComplete(
                                            (ignored, error) -> {
                                                if (error != null) {
                                                    ErrorReply.send(
                                                            event, error, id.menuId(), id.action());
                                                }
                                                release.run();
                                            });
                        } catch (Exception error) {
                            // The only broad catch in the menu package, permitted
                            // solely in the dispatcher. A handler that throws before
                            // returning a future never produces one to observe.
                            ErrorReply.send(event, error, id.menuId(), id.action());
                            release.run();
                        }
                    });
        } catch (RejectedExecutionException error) {
            LOG.warn("Executor rejected action '{}' of menu '{}'", id.action(), id.menuId(), error);
            release.run();
            Replies.ephemeral(event, BUSY);
        }
    }

    private void releaseOnce(long messageId, AtomicBoolean released) {
        if (messageId == NO_MESSAGE) {
            return;
        }
        if (released.compareAndSet(false, true)) {
            guard.release(messageId);
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

    /**
     * Number of menu messages currently claimed by a running handler. Visible for
     * tests.
     *
     * @return the size of the in-flight set
     */
    int inFlight() {
        return guard.size();
    }

    @Override
    public void close() {
        executor.close();
        sessions.close();
    }
}
