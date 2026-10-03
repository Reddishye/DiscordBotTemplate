package es.redactado.menu.core;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.MenuNotFoundException;
import es.redactado.menu.preset.InMemoryPresetPreferences;
import es.redactado.menu.preset.Preset;
import es.redactado.menu.preset.PresetRegistry;
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
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
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

    private final Map<String, Registered> menus = new ConcurrentHashMap<>();
    private final MenuExecutor executor;
    private final SessionStore sessions;
    private final Messages messages;
    private final PresetResolver presets;
    private final boolean ownsExecutor;
    private final boolean ownsSessions;
    private final InteractionGuard guard = new InteractionGuard();
    private final Navigator navigator;

    /** A menu paired with the immutable action table built from it. */
    private record Registered(Menu menu, ActionTable table) {}

    private MenuRouter(
            MenuExecutor executor,
            SessionStore sessions,
            Messages messages,
            PresetResolver presets,
            boolean ownsExecutor,
            boolean ownsSessions) {
        this.executor = executor;
        this.sessions = sessions;
        this.messages = messages;
        this.presets = presets;
        this.ownsExecutor = ownsExecutor;
        this.ownsSessions = ownsSessions;
        this.navigator = new Navigator(this::get, sessions, messages, presets);
    }

    /**
     * Starts building a router.
     *
     * <p>Replaces the public constructors because the parameter list had reached four
     * and every new capability made it worse. A caller who supplies nothing gets a
     * working router over the template's own defaults: a virtual-thread executor, an
     * in-memory session store, the bundled messages, and a resolver over a fresh
     * registry with no persisted preferences.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Assembles a {@link MenuRouter}.
     *
     * <p>Every component is optional and has a working default, so the only required
     * call is {@link #build()}.
     */
    public static final class Builder {

        private MenuExecutor executor;
        private SessionStore sessions;
        private Messages messages;
        private PresetResolver presets;

        private Builder() {}

        /**
         * Sets the executor that runs handler bodies.
         *
         * <p>A supplied executor is owned by the caller: the router will not close it.
         *
         * @param executor the executor
         * @return this builder
         */
        public Builder executor(MenuExecutor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Sets the store holding one session per menu message.
         *
         * <p>A supplied store is owned by the caller: the router will not close it.
         *
         * @param sessions the store
         * @return this builder
         */
        public Builder sessions(SessionStore sessions) {
            this.sessions = sessions;
            return this;
        }

        /**
         * Sets where user-facing text is resolved from.
         *
         * @param messages the bundles
         * @return this builder
         */
        public Builder messages(Messages messages) {
            this.messages = messages;
            return this;
        }

        /**
         * Sets how each interaction finds its preset.
         *
         * <p>A supplied resolver is owned by the caller, including anything it owns such
         * as a registry.
         *
         * @param presets the resolver
         * @return this builder
         */
        public Builder presets(PresetResolver presets) {
            this.presets = presets;
            return this;
        }

        /**
         * Builds the router.
         *
         * @return a router that closes only the components it created
         */
        public MenuRouter build() {
            MenuExecutor chosenExecutor = executor != null ? executor : MenuExecutor.virtual();
            SessionStore chosenSessions =
                    sessions != null ? sessions : new SessionStore(SessionConfig.defaults());
            Messages chosenMessages = messages != null ? messages : Messages.standard();
            PresetResolver chosenPresets =
                    presets != null
                            ? presets
                            : new PresetResolver(
                                    new PresetRegistry(), new InMemoryPresetPreferences(), false);
            return new MenuRouter(
                    chosenExecutor,
                    chosenSessions,
                    chosenMessages,
                    chosenPresets,
                    executor == null,
                    sessions == null);
        }
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
        return run(new Incoming.Button(event));
    }

    /**
     * Handles a modal submission if it belongs to a registered menu.
     *
     * @param event the JDA modal event
     * @return {@code true} when the event was consumed
     */
    public boolean dispatchModal(ModalInteractionEvent event) {
        return run(new Incoming.Modal(event));
    }

    /**
     * Handles a string select submission if it belongs to a registered menu.
     *
     * @param event the JDA select event
     * @return {@code true} when the event was consumed
     */
    public boolean dispatchSelect(StringSelectInteractionEvent event) {
        return run(new Incoming.Select(event));
    }

    /**
     * The one pipeline every interaction takes.
     *
     * <p>Six steps, in this order and for every kind:
     *
     * <ol>
     *   <li>decode the custom id, so an id that is not ours is left alone;
     *   <li>find the menu, and leave the interaction alone if it is not registered;
     *   <li>find the action, answering the user if the menu declares nothing under that
     *       name, because at that point the menu has consumed the interaction;
     *   <li>admit: check ownership and claim the message, both before acknowledging so a
     *       rejection costs nothing;
     *   <li>acknowledge, on the JDA thread, inside Discord's three second budget;
     *   <li>resolve the preset and hand over on the executor, since a handler may block.
     * </ol>
     *
     * <p>Steps one and two return false, meaning the caller may keep listening; from step
     * three the menu owns the interaction and the answer is always true.
     */
    private boolean run(Incoming incoming) {
        Optional<ComponentId> parsed = ComponentId.decode(incoming.sourceId());
        if (parsed.isEmpty()) {
            return false;
        }

        Registered registered = menus.get(parsed.get().menuId());
        if (registered == null) {
            LOG.warn("No menu registered for {} id: {}", incoming.kind(), parsed.get().menuId());
            return false;
        }

        Optional<Incoming.Resolved> action =
                incoming.resolve(registered.table(), parsed.get().action());
        if (action.isEmpty()) {
            LOG.warn(
                    "Menu '{}' has no {} action '{}'",
                    parsed.get().menuId(),
                    incoming.kind(),
                    parsed.get().action());
            Replies.ephemeral(
                    incoming.event(),
                    messages,
                    incoming.locale(),
                    MessageKeys.ERROR_UNKNOWN_ACTION);
            return true;
        }

        long messageId = incoming.messageId();
        if (!admit(registered.menu(), incoming, messageId)) {
            return true;
        }

        incoming.acknowledge(action.get().ack());
        submit(
                incoming,
                messageId,
                parsed.get(),
                registered.menu(),
                action.get(),
                () ->
                        resolve(incoming, registered.menu())
                                .thenApply(
                                        preset ->
                                                action.get()
                                                        .invoke()
                                                        .apply(
                                                                incoming.context(
                                                                        parsed.get(),
                                                                        sessions,
                                                                        navigator,
                                                                        messages,
                                                                        preset))));
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
    private boolean admit(Menu menu, Incoming incoming, long messageId) {
        if (!owns(menu, incoming.message(), incoming.event())) {
            Replies.ephemeral(
                    incoming.event(), messages, incoming.locale(), MessageKeys.ERROR_NOT_OWNER);
            return false;
        }
        if (messageId != Incoming.NO_MESSAGE && !guard.tryAcquire(messageId)) {
            LOG.debug(
                    "Dropping a duplicate {} interaction on message {}",
                    incoming.kind(),
                    messageId);
            deferEdit(incoming.event());
            return false;
        }
        return true;
    }

    /**
     * Swallows a duplicate click by deferring the edit.
     *
     * <p>The first click already owns the message and will produce the visible result,
     * so the second one must not answer the user with an error; deferring simply closes
     * it quietly. A select cannot defer an edit, so it is acknowledged instead, which is
     * the closest legal no-op.
     */
    private static void deferEdit(IReplyCallback event) {
        if (event instanceof ButtonInteractionEvent button) {
            button.deferEdit().queue();
        } else if (event instanceof ModalInteractionEvent modal) {
            modal.deferEdit().queue();
        } else if (event instanceof StringSelectInteractionEvent select) {
            select.deferReply(true).queue();
        }
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

    private static long messageIdOf(Message message) {
        return message == null ? Incoming.NO_MESSAGE : message.getIdLong();
    }

    /**
     * Hands the handler body to the executor and arranges for the message claim to
     * be released exactly once, whether the handler succeeds, throws, or never
     * completes its future.
     */
    private void submit(
            Incoming incoming,
            long messageId,
            ComponentId id,
            Menu menu,
            Incoming.Resolved action,
            Supplier<CompletableFuture<CompletableFuture<Void>>> work) {
        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> releaseOnce(messageId, released);
        try {
            executor.execute(
                    () -> {
                        try {
                            work.get()
                                    .thenCompose(inner -> inner)
                                    .whenComplete(
                                            (ignored, error) -> {
                                                if (error != null) {
                                                    ErrorReply.send(
                                                            incoming.event(),
                                                            messages,
                                                            incoming.locale(),
                                                            error,
                                                            id.menuId(),
                                                            id.action());
                                                }
                                                release.run();
                                            });
                        } catch (Exception error) {
                            // The only broad catch in the menu package, permitted
                            // solely in the dispatcher. A handler that throws before
                            // returning a future never produces one to observe.
                            ErrorReply.send(
                                    incoming.event(),
                                    messages,
                                    incoming.locale(),
                                    error,
                                    id.menuId(),
                                    id.action());
                            release.run();
                        }
                    });
        } catch (RejectedExecutionException error) {
            LOG.warn("Executor rejected action '{}' of menu '{}'", id.action(), id.menuId(), error);
            release.run();
            Replies.ephemeral(
                    incoming.event(), messages, incoming.locale(), MessageKeys.ERROR_BUSY);
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

    /**
     * Finds the preset for this interaction.
     *
     * <p>A direct message reports guild id {@code 0}, which is how the resolver knows to
     * skip the guild level: a DM has no guild, and asking about one would be asking
     * about the absence of something.
     *
     * <p>The resolver never fails, so there is no error path here. If it somehow did,
     * the exception would reach the same error handling as a handler failure and the
     * user would get the generic message rather than silence.
     */
    private CompletableFuture<Preset> resolve(Incoming incoming, Menu menu) {
        return presets.resolve(menu, incoming.guildId(), incoming.userId());
    }

    /**
     * Releases what this router created.
     *
     * <p><strong>The rule is that a component is closed by whoever created it.</strong> A
     * router built with the builder and no arguments owns its executor and its session
     * store and closes them. Anything supplied to the builder belongs to the caller and
     * is left running, because a shared executor outlives one router and closing it
     * would silently break the next one. {@link Builder#build()} records which of the two
     * happened rather than guessing.
     *
     * <p>Idempotent in effect: closing the same underlying executor twice is harmless.
     */
    @Override
    public void close() {
        if (ownsExecutor) {
            executor.close();
        }
        if (ownsSessions) {
            sessions.close();
        }
    }
}
