package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.ComponentLimitException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ViewEditorTest {

    private static Container container(int children) {
        List<ContainerChildComponent> parts = new ArrayList<>(children);
        for (int i = 0; i < children; i++) {
            parts.add(TextDisplay.of("line " + i));
        }
        return Container.of(parts);
    }

    @Test
    @DisplayName("a valid container is sent exactly once")
    void sendsValidContainer() {
        InteractionHook hook = JdaMocks.hook();
        Container container = container(3);

        CompletableFuture<Void> result = ViewEditor.edit(hook, container);

        assertThat(result.join()).isNull();
        verify(hook).editOriginalComponents(any(MessageTopLevelComponent[].class));
    }

    @Test
    @DisplayName("a container over the child limit fails and sends nothing")
    void refusesOversizedContainer() {
        InteractionHook hook = JdaMocks.hook();
        Container container = container(26);

        CompletableFuture<Void> result = ViewEditor.edit(hook, container);

        assertThat(result).isCompletedExceptionally();
        assertThatThrownBy(result::join).hasCauseInstanceOf(ComponentLimitException.class);
        verify(hook, never()).editOriginalComponents(any(MessageTopLevelComponent[].class));
        verify(hook, never()).editOriginalComponents(any(java.util.Collection.class));
    }

    @Test
    @DisplayName("validation warnings do not stop the send")
    void warningsStillSend() {
        InteractionHook hook = JdaMocks.hook();
        Container container = container(21);

        assertThat(ViewEditor.edit(hook, container).join()).isNull();

        verify(hook).editOriginalComponents(any(MessageTopLevelComponent[].class));
    }

    @Test
    @DisplayName("the exact limit is accepted")
    void exactLimitIsAccepted() {
        InteractionHook hook = JdaMocks.hook();

        assertThat(ViewEditor.edit(hook, container(25)).join()).isNull();

        verify(hook).editOriginalComponents(any(MessageTopLevelComponent[].class));
    }

    @Test
    @DisplayName("a failed REST call surfaces as a failed future")
    void restFailurePropagates() {
        InteractionHook hook = mock(InteractionHook.class);
        net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction<
                        net.dv8tion.jda.api.entities.Message>
                action =
                        mock(
                                net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
                                        .class);
        when(hook.editOriginalComponents(any(MessageTopLevelComponent[].class))).thenReturn(action);
        when(action.useComponentsV2()).thenReturn(action);
        when(action.submit())
                .thenReturn(
                        CompletableFuture.failedFuture(
                                new IllegalStateException("429 rate limited")));

        CompletableFuture<Void> result = ViewEditor.edit(hook, container(1));

        assertThatThrownBy(result::join).hasRootCauseMessage("429 rate limited");
    }

    @Test
    @DisplayName("the container sent is the one it was given")
    void sendsTheSameContainer() {
        InteractionHook hook = JdaMocks.hook();
        Container container = container(1);

        ViewEditor.edit(hook, container);

        ArgumentCaptor<MessageTopLevelComponent[]> captor =
                ArgumentCaptor.forClass(MessageTopLevelComponent[].class);
        verify(hook).editOriginalComponents(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }
}
