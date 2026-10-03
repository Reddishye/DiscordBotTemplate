package es.redactado.menu.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

/**
 * Builds stubbed JDA interactions, messages, and hooks for the router tests.
 *
 * <p>Nothing here touches a gateway. Every object is a Mockito mock, and only the
 * handful of methods the router actually calls are stubbed.
 *
 * <p>Stubs are always created before the {@code when(...)} that returns them.
 * Mockito cannot record a stub while another stubbing is in progress, so calling a
 * factory from inside a {@code when} argument fails.
 */
final class JdaMocks {

    private JdaMocks() {}

    static ButtonInteractionEvent button(String componentId, boolean acknowledged) {
        return button(componentId, acknowledged, NO_MESSAGE, Long.MAX_VALUE);
    }

    /**
     * Builds a button event on a message owned by {@code ownerId}.
     *
     * @param ownerId the interacting user, or {@link Long#MAX_VALUE} for a message
     *     with no interaction metadata, meaning it was sent directly to a channel
     */
    static ButtonInteractionEvent button(
            String componentId, boolean acknowledged, long messageId, long ownerId) {
        InteractionHook hook = hook();
        MessageEditCallbackAction deferEdit = mock(MessageEditCallbackAction.class);
        ReplyCallbackAction reply = replyAction();
        Message message = message(messageId, ownerId);
        User clicker = clicker();
        Guild guild = guild();

        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn(componentId);
        when(event.isAcknowledged()).thenReturn(acknowledged);
        when(event.getHook()).thenReturn(hook);
        when(event.getMessage()).thenReturn(message);
        when(event.getMessageIdLong()).thenReturn(message == null ? 0 : messageId);
        when(event.getUser()).thenReturn(clicker);
        when(event.getGuild()).thenReturn(guild);
        when(event.deferEdit()).thenReturn(deferEdit);
        when(event.deferReply(true)).thenReturn(reply);
        when(event.reply(anyString())).thenReturn(reply);
        when(event.replyModal(any(Modal.class))).thenReturn(modalAction());
        return event;
    }

    static ModalInteractionEvent modal(String modalId, boolean acknowledged) {
        return modal(modalId, acknowledged, NO_MESSAGE, Long.MAX_VALUE);
    }

    static ModalInteractionEvent modal(
            String modalId, boolean acknowledged, long messageId, long ownerId) {
        InteractionHook hook = hook();
        MessageEditCallbackAction deferEdit = mock(MessageEditCallbackAction.class);
        ReplyCallbackAction reply = replyAction();
        Message message = message(messageId, ownerId);
        User clicker = clicker();
        Guild guild = guild();

        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getModalId()).thenReturn(modalId);
        when(event.isAcknowledged()).thenReturn(acknowledged);
        when(event.getHook()).thenReturn(hook);
        when(event.getMessage()).thenReturn(message);
        when(event.getUser()).thenReturn(clicker);
        when(event.getGuild()).thenReturn(guild);
        when(event.deferEdit()).thenReturn(deferEdit);
        when(event.deferReply(true)).thenReturn(reply);
        when(event.reply(anyString())).thenReturn(reply);
        return event;
    }

    /**
     * Builds a string select submission on a message owned by {@code ownerId}.
     *
     * <p>Same shape as {@link #button} so the select tests read like the button ones and
     * a difference in behaviour shows up as a difference in the test rather than in the
     * mock.
     */
    static StringSelectInteractionEvent select(
            String componentId, boolean acknowledged, String... values) {
        return select(componentId, acknowledged, NO_MESSAGE, NO_OWNER, values);
    }

    static StringSelectInteractionEvent select(
            String componentId,
            boolean acknowledged,
            long messageId,
            long ownerId,
            String... values) {
        InteractionHook hook = hook();
        MessageEditCallbackAction deferEdit = mock(MessageEditCallbackAction.class);
        ReplyCallbackAction reply = replyAction();
        Message message = message(messageId, ownerId);
        User clicker = clicker();
        Guild guild = guild();

        StringSelectInteractionEvent event = mock(StringSelectInteractionEvent.class);
        when(event.getComponentId()).thenReturn(componentId);
        when(event.getValues()).thenReturn(List.of(values));
        when(event.isAcknowledged()).thenReturn(acknowledged);
        when(event.getHook()).thenReturn(hook);
        when(event.getMessage()).thenReturn(message);
        when(event.getUser()).thenReturn(clicker);
        when(event.getGuild()).thenReturn(guild);
        when(event.deferEdit()).thenReturn(deferEdit);
        when(event.deferReply(true)).thenReturn(reply);
        when(event.reply(anyString())).thenReturn(reply);
        return event;
    }

    /** Message id meaning "this interaction has no menu message". */
    static final long NO_MESSAGE = -1L;

    /** User id meaning "the message carries no interaction metadata". */
    static final long NO_OWNER = Long.MAX_VALUE;

    static Message message(long messageId, long ownerId) {
        if (messageId == NO_MESSAGE) {
            return null;
        }
        Message.InteractionMetadata metadata = null;
        if (ownerId != NO_OWNER) {
            User ownerUser = user(ownerId);
            metadata = mock(Message.InteractionMetadata.class);
            when(metadata.getUser()).thenReturn(ownerUser);
        }
        Message message = mock(Message.class);
        when(message.getIdLong()).thenReturn(messageId);
        when(message.getInteractionMetadata()).thenReturn(metadata);
        return message;
    }

    private static User clicker() {
        return user(42L);
    }

    private static Guild guild() {
        return mock(Guild.class);
    }

    static User user(long id) {
        User user = mock(User.class);
        when(user.getIdLong()).thenReturn(id);
        when(user.getId()).thenReturn(Long.toString(id));
        return user;
    }

    static InteractionHook hook() {
        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageCreateAction<Message> send = messageAction();
        WebhookMessageEditAction<Message> edit = editAction();
        when(hook.sendMessage(anyString())).thenReturn(send);
        when(hook.editOriginalComponents(anyCollection())).thenReturn(edit);
        when(hook.editOriginalComponents(any(MessageTopLevelComponent[].class))).thenReturn(edit);
        return hook;
    }

    @SuppressWarnings("unchecked")
    private static Collection<MessageTopLevelComponent> anyCollection() {
        return any();
    }

    private static WebhookMessageCreateAction<Message> messageAction() {
        WebhookMessageCreateAction<Message> action = mock(WebhookMessageCreateAction.class);
        when(action.setEphemeral(true)).thenReturn(action);
        return action;
    }

    private static WebhookMessageEditAction<Message> editAction() {
        WebhookMessageEditAction<Message> action = mock(WebhookMessageEditAction.class);
        when(action.useComponentsV2()).thenReturn(action);
        when(action.submit())
                .thenReturn(
                        CompletableFuture.completedFuture(
                                mock(net.dv8tion.jda.api.entities.Message.class)));
        return action;
    }

    private static ReplyCallbackAction replyAction() {
        ReplyCallbackAction action = mock(ReplyCallbackAction.class);
        when(action.setEphemeral(true)).thenReturn(action);
        return action;
    }

    /** Raw because JDA declares {@code replyModal} as returning the raw type. */
    @SuppressWarnings("rawtypes")
    private static ModalCallbackAction modalAction() {
        return mock(ModalCallbackAction.class);
    }
}
