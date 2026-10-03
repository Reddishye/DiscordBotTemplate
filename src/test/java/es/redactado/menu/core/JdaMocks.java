package es.redactado.menu.core;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

/**
 * Builds stubbed JDA interactions and hooks for the router tests.
 *
 * <p>Nothing here touches a gateway. Every interaction is a Mockito mock, and only
 * the handful of methods the router actually calls are stubbed.
 */
final class JdaMocks {

    private JdaMocks() {}

    static ButtonInteractionEvent button(String componentId, boolean acknowledged) {
        InteractionHook hook = hook();
        MessageEditCallbackAction deferEdit = mock(MessageEditCallbackAction.class);
        ReplyCallbackAction reply = replyAction();

        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn(componentId);
        when(event.isAcknowledged()).thenReturn(acknowledged);
        when(event.getHook()).thenReturn(hook);
        when(event.deferEdit()).thenReturn(deferEdit);
        when(event.deferReply(true)).thenReturn(reply);
        when(event.reply(anyString())).thenReturn(reply);
        return event;
    }

    static ModalInteractionEvent modal(String modalId, boolean acknowledged) {
        InteractionHook hook = hook();
        MessageEditCallbackAction deferEdit = mock(MessageEditCallbackAction.class);
        ReplyCallbackAction reply = replyAction();

        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getModalId()).thenReturn(modalId);
        when(event.isAcknowledged()).thenReturn(acknowledged);
        when(event.getHook()).thenReturn(hook);
        when(event.deferEdit()).thenReturn(deferEdit);
        when(event.deferReply(true)).thenReturn(reply);
        when(event.reply(anyString())).thenReturn(reply);
        return event;
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
        return action;
    }

    private static ReplyCallbackAction replyAction() {
        ReplyCallbackAction action = mock(ReplyCallbackAction.class);
        when(action.setEphemeral(true)).thenReturn(action);
        return action;
    }
}
