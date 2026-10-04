package es.redactado.menu.core;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.preset.Preset;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

/**
 * One interaction on its way through the router, whatever kind it is.
 *
 * <p>Buttons, modal submissions and select menus arrive through three different JDA
 * types that share nothing but a custom id, and the pipeline the router runs is the same
 * for all three: decode, find the menu, find the action, admit, acknowledge, resolve a
 * preset, then hand over. Writing that pipeline three times meant the ownership check,
 * the duplicate-click guard and the error path existed in three places, and a fix to one
 * would leave the other two wrong.
 *
 * <p>This is the seam that removes the duplication. Each adapter is a record holding one
 * event, so the per-interaction cost is one small object and a handful of field reads:
 * no stream, no reflection, no regex on a path that runs for every click.
 *
 * <p>The three kinds are the only ones that exist, which {@code sealed} says outright, so
 * adding a fourth interaction type is a compile error here rather than a silent gap in
 * the router.
 */
sealed interface Incoming permits Incoming.Button, Incoming.Modal, Incoming.Select {

    /** Message id meaning the interaction has no menu message, such as a bare modal. */
    long NO_MESSAGE = -1L;

    /**
     * An action that has been found in the table, reduced to what the pipeline needs.
     *
     * @param ack how to acknowledge the interaction
     * @param invoke runs the handler, given the context the pipeline built
     */
    record Resolved(
            Ack ack, Function<MenuContext, java.util.concurrent.CompletableFuture<Void>> invoke) {}

    /**
     * The custom id the interaction carries, which is where the menu and action live.
     *
     * @return the component or modal id
     */
    String sourceId();

    /**
     * A short name for this kind, used only in log messages.
     *
     * @return {@code button}, {@code modal} or {@code select}
     */
    String kind();

    /**
     * The message the interaction happened on.
     *
     * @return the message, or null when there is none
     */
    Message message();

    /**
     * The interaction, as the router needs it for replies and hooks.
     *
     * @return the event
     */
    IReplyCallback event();

    /**
     * Acknowledges the interaction, if the declared mode calls for it.
     *
     * @param ack the declared mode
     */
    void acknowledge(Ack ack);

    /**
     * Finds the declared action behind a name.
     *
     * @param table the immutable table built at registration
     * @param action the action name from the custom id
     * @return the action, or empty when the menu declares nothing under that name
     */
    Optional<Resolved> resolve(ActionTable table, String action);

    /**
     * Builds the context the handler receives.
     *
     * @param id the decoded custom id
     * @param sessions the store backing the session
     * @param navigator the navigator
     * @param messages where user-facing text is resolved
     * @param preset the preset resolved for this interaction
     * @return the context
     */
    MenuContext context(
            ComponentId id,
            SessionStore sessions,
            Navigator navigator,
            Messages messages,
            Preset preset);

    /**
     * The message this interaction happened on.
     *
     * @return the message id, or {@link #NO_MESSAGE}
     */
    default long messageId() {
        Message current = message();
        return current == null ? NO_MESSAGE : current.getIdLong();
    }

    /**
     * The user who produced the message, when it came from an interaction.
     *
     * <p>Null for a message sent straight to a channel, which has no single owner and so
     * is treated as shared.
     *
     * @return the owner, or null
     */
    default User owner() {
        Message current = message();
        if (current == null) {
            return null;
        }
        Message.InteractionMetadata metadata = current.getInteractionMetadata();
        return metadata == null ? null : metadata.getUser();
    }

    /**
     * The interacting user's id.
     *
     * @return the user id
     */
    default long userId() {
        return event().getUser().getIdLong();
    }

    /**
     * The guild, or {@code 0} in a direct message.
     *
     * <p>Zero rather than a nullable long because {@code PresetResolver} uses it to mean
     * "there is no guild, skip that level", and a direct message having no guild is
     * ordinary rather than exceptional.
     *
     * @return the guild id, or {@code 0}
     */
    default long guildId() {
        return event().getGuild() == null ? 0L : event().getGuild().getIdLong();
    }

    /**
     * The locale to answer in, from the user where there is one and the guild otherwise.
     *
     * @return the locale, never null
     */
    default Locale locale() {
        return Locales.resolve(
                event().getUserLocale(),
                event().getGuild() == null
                        ? net.dv8tion.jda.api.interactions.DiscordLocale.UNKNOWN
                        : event().getGuildLocale());
    }

    /** A click on a button. */
    record Button(ButtonInteractionEvent event) implements Incoming {

        @Override
        public String sourceId() {
            return event.getComponentId();
        }

        @Override
        public String kind() {
            return "button";
        }

        @Override
        public Message message() {
            return event.getMessage();
        }

        @Override
        public void acknowledge(Ack ack) {
            switch (ack) {
                case DEFER_EDIT -> event.deferEdit().queue();
                case DEFER_REPLY -> event.deferReply(true).queue();
                case MODAL, NONE -> {}
            }
        }

        @Override
        public Optional<Resolved> resolve(ActionTable table, String action) {
            return table.button(action)
                    .map(
                            declared ->
                                    new Resolved(
                                            declared.ack(),
                                            ctx -> declared.handler().handle(ctx, event)));
        }

        @Override
        public MenuContext context(
                ComponentId id,
                SessionStore sessions,
                Navigator navigator,
                Messages messages,
                Preset preset) {
            return BaseContext.fromButton(event, id, sessions, navigator, messages, preset);
        }
    }

    /** A submitted modal. */
    record Modal(ModalInteractionEvent event) implements Incoming {

        @Override
        public String sourceId() {
            return event.getModalId();
        }

        @Override
        public String kind() {
            return "modal";
        }

        @Override
        public Message message() {
            return event.getMessage();
        }

        @Override
        public void acknowledge(Ack ack) {
            switch (ack) {
                case DEFER_EDIT -> event.deferEdit().queue();
                case DEFER_REPLY -> event.deferReply(true).queue();
                case MODAL, NONE -> {}
            }
        }

        @Override
        public Optional<Resolved> resolve(ActionTable table, String action) {
            return table.modal(action)
                    .map(
                            declared ->
                                    new Resolved(
                                            declared.ack(),
                                            ctx -> declared.handler().handle(ctx, event)));
        }

        @Override
        public MenuContext context(
                ComponentId id,
                SessionStore sessions,
                Navigator navigator,
                Messages messages,
                Preset preset) {
            return BaseContext.fromModal(event, id, sessions, navigator, messages, preset);
        }
    }

    /** A submitted string select menu. */
    record Select(StringSelectInteractionEvent event) implements Incoming {

        @Override
        public String sourceId() {
            return event.getComponentId();
        }

        @Override
        public String kind() {
            return "select";
        }

        @Override
        public Message message() {
            return event.getMessage();
        }

        @Override
        public void acknowledge(Ack ack) {
            switch (ack) {
                case DEFER_EDIT -> event.deferEdit().queue();
                case DEFER_REPLY -> event.deferReply(true).queue();
                // A select cannot open a modal on submission, but the ack mode is
                // accepted at declaration so a select may open one from its handler,
                // before the interaction is acknowledged.
                case MODAL, NONE -> {}
            }
        }

        @Override
        public Optional<Resolved> resolve(ActionTable table, String action) {
            return table.select(action)
                    .map(
                            declared ->
                                    new Resolved(
                                            declared.ack(),
                                            ctx -> declared.handler().handle(ctx, event)));
        }

        @Override
        public MenuContext context(
                ComponentId id,
                SessionStore sessions,
                Navigator navigator,
                Messages messages,
                Preset preset) {
            return BaseContext.fromSelect(event, id, sessions, navigator, messages, preset);
        }
    }
}
