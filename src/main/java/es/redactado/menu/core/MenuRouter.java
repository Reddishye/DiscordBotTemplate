package es.redactado.menu.core;

import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.MenuNotFoundException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central dispatcher for menu interactions.
 * Register menus by id, then wire into your existing listener.
 *
 * <pre>{@code
 * MenuRouter router = new MenuRouter();
 * router.register("profile", new ProfileMenu(applyService));
 * router.register("entry", new EntryMenu(applyService));
 *
 * // In your listener:
 * public void onButtonInteraction(ButtonInteractionEvent event) {
 *     if (router.dispatchButton(event)) return; // handled
 *     // ... legacy button handling
 * }
 * }</pre>
 */
public class MenuRouter {
    private static final Logger LOG = LoggerFactory.getLogger(MenuRouter.class);
    private final Map<String, Menu> menus = new ConcurrentHashMap<>();

    public void register(String id, Menu menu) {
        menus.put(id, menu);
        LOG.info("Menu registered: {}", id);
    }

    public Menu get(String id) {
        Menu menu = menus.get(id);
        if (menu == null) throw new MenuNotFoundException(id);
        return menu;
    }

    /** Try to handle a button event. Returns true if the event was consumed. */
    public boolean dispatchButton(ButtonInteractionEvent event) {
        var raw = event.getComponentId();
        if (raw == null || raw.isEmpty()) return false;

        var parsed = ComponentId.decode(raw);
        if (parsed.isEmpty()) return false;

        Menu menu;
        try {
            menu = get(parsed.get().menuId());
        } catch (MenuNotFoundException e) {
            LOG.warn("No menu registered for id: {}", parsed.get().menuId());
            return false;
        }

        try {
            var ctx = BaseContext.fromButton(event, parsed.get());
            menu.onButton(ctx, event);
        } catch (Exception e) {
            LOG.error("Error dispatching button for menu: {}", parsed.get().menuId(), e);
            event.reply("Error.").setEphemeral(true).queue();
        }
        return true;
    }

    /** Try to handle a modal event. Returns true if the event was consumed. */
    public boolean dispatchModal(ModalInteractionEvent event) {
        var mid = event.getModalId();
        if (mid == null || mid.isEmpty()) return false;

        // Modal IDs: "menu:menuId:action:params..."
        var parsed = ComponentId.decode(mid);
        if (parsed.isEmpty()) return false;

        Menu menu;
        try {
            menu = get(parsed.get().menuId());
        } catch (MenuNotFoundException e) {
            LOG.warn("No menu registered for modal: {}", parsed.get().menuId());
            return false;
        }

        try {
            var ctx = BaseContext.fromModal(event, parsed.get());
            menu.onModal(ctx, event);
        } catch (Exception e) {
            LOG.error("Error dispatching modal for menu: {}", parsed.get().menuId(), e);
            event.reply("Error.").setEphemeral(true).queue();
        }
        return true;
    }

    /** Render a Container for a menu. */
    public net.dv8tion.jda.api.components.container.Container render(
            String menuId, MenuContext ctx) {
        return get(menuId).render(ctx);
    }
}
