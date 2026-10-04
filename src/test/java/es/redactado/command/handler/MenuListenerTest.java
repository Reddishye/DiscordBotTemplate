package es.redactado.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import es.redactado.config.Listeners;
import es.redactado.menu.core.MenuRouter;
import es.redactado.service.MenuService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The listener, which is three lines of delegation and nothing else.
 *
 * <p>What matters is that it forwards and that it stays out of the way: an interaction the
 * menu system does not own must come out untouched, because another listener is entitled to
 * answer it.
 */
class MenuListenerTest {

    @Test
    @DisplayName("a button click is dispatched and nothing else happens")
    void buttonIsDispatched() {
        MenuRouter router = mock(MenuRouter.class);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(router.dispatchButton(event)).thenReturn(true);

        listener(router).onButtonInteraction(event);

        verify(router).dispatchButton(event);
        verify(event, never()).reply(any(String.class));
    }

    @Test
    @DisplayName("a modal submission is dispatched")
    void modalIsDispatched() {
        MenuRouter router = mock(MenuRouter.class);
        ModalInteractionEvent event = mock(ModalInteractionEvent.class);

        listener(router).onModalInteraction(event);

        verify(router).dispatchModal(event);
    }

    @Test
    @DisplayName("a select submission is dispatched")
    void selectIsDispatched() {
        MenuRouter router = mock(MenuRouter.class);
        StringSelectInteractionEvent event = mock(StringSelectInteractionEvent.class);

        listener(router).onStringSelectInteraction(event);

        verify(router).dispatchSelect(event);
    }

    @Test
    @DisplayName("an interaction the menu system does not own is left for another listener")
    void foreignInteractionIsUntouched() {
        MenuRouter router = mock(MenuRouter.class);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        InteractionHook hook = mock(InteractionHook.class);
        when(event.getHook()).thenReturn(hook);
        when(router.dispatchButton(event)).thenReturn(false);

        listener(router).onButtonInteraction(event);

        verify(event, never()).reply(any(String.class));
        verify(hook, never()).sendMessage(any(String.class));
        verify(router).dispatchButton(event);
    }

    @Test
    @DisplayName("it is in the template's listener list")
    void itIsRegistered() {
        assertThat(Listeners.LISTENERS).contains(MenuListener.class);
        assertThat(MenuListener.class).isAssignableTo(ListenerAdapter.class);
    }

    @Test
    @DisplayName("it dispatches through the service it was given")
    void itUsesTheServiceRouter() {
        MenuService service = mock(MenuService.class);
        MenuRouter router = mock(MenuRouter.class);
        when(service.router()).thenReturn(router);
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);

        new MenuListener(service).onButtonInteraction(event);

        verify(service).router();
        verify(router).dispatchButton(event);
    }

    private static MenuListener listener(MenuRouter router) {
        MenuService service = mock(MenuService.class);
        when(service.router()).thenReturn(router);
        return new MenuListener(service);
    }
}
