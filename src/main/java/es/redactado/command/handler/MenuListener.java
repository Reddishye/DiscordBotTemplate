package es.redactado.command.handler;

import com.google.inject.Inject;
import es.redactado.menu.core.MenuRouter;
import es.redactado.service.MenuService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Hands component interactions to the menu router.
 *
 * <p>Three overrides and nothing else. Each one asks the router whether it owns the
 * interaction and stops there: an interaction this system does not own is left completely
 * untouched, so another listener can answer it. A listener that replied on the way past
 * would steal it.
 *
 * <p><strong>Nothing here blocks and nothing here catches.</strong> The router acknowledges
 * on this thread and hands the work to its executor, and a handler that fails is reported
 * through the framework's own error reply. Wrapping the call in a try would only hide a
 * failure that the router already reports, with a reference code in the log.
 *
 * <p>Where a menu is opened from is not this class's business: a slash command, a modal of
 * another system, anything with a menu id. See {@link MenuService#open}.
 */
public class MenuListener extends ListenerAdapter {

    private final MenuService menuService;

    @Inject
    public MenuListener(MenuService menuService) {
        this.menuService = menuService;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        menuService.router().dispatchButton(event);
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        menuService.router().dispatchModal(event);
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        menuService.router().dispatchSelect(event);
    }

    /** The router this listener dispatches to, for a test that builds one by hand. */
    MenuRouter router() {
        return menuService.router();
    }
}
