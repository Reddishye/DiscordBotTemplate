package es.redactado.menu.view;

import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.NavEntry;
import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.core.MessageKeys;
import es.redactado.menu.core.NavigationAction;
import es.redactado.menu.preset.ButtonRole;
import es.redactado.menu.preset.IconKey;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent;
import net.dv8tion.jda.api.components.buttons.Button;

/**
 * Buttons that move between views.
 *
 * <p>Two things can be navigated to: another menu, and another view inside the menu you
 * are already in. Both are here.
 *
 * <pre>{@code
 * Row.of(Nav.back(), Nav.view("details", "Details", id))
 * Row.of(Nav.swap("filters", "Filters"))          // replaces, so Back leaves the view
 * Row.of(Nav.to("settings", "appearance", "Appearance"))
 * }</pre>
 *
 * <p>Every one uses the built-in {@code nav} action, which {@code AbstractMenu} registers
 * for every menu, so a subclass cannot forget to wire navigation and cannot redeclare it
 * either. The id carries the mode, the target menu and the target view, so no handler is
 * needed: the router and the navigator do the work.
 *
 * <p><strong>The menu id is read at render time</strong>, not at construction, because a
 * view of the current menu does not know which menu that is until the menu is rendering.
 * That is why {@link #view} and {@link #swap} take no menu id and {@link #to} does.
 *
 * <p>All of them render in the {@link ButtonRole#SECONDARY} style, because moving around is
 * navigation rather than an action with an outcome, and a row of navigation should not
 * compete with the one button that actually does something.
 *
 * <p>Labels are caller-supplied and therefore already localized, except for
 * {@link #back()}: "back" is the one label a menu author should not have to write in every
 * language, so it comes from the bundles.
 */
public final class Nav implements RowItem {

    /** The target view of a navigation within the current menu, or null for a menu. */
    private final String viewAction;

    /** The menu to show, or null for the one doing the rendering. */
    private final String targetMenuId;

    private final NavigationMode mode;
    private final String label;
    private final IconKey icon;
    private final String[] params;

    private Nav(
            NavigationMode mode,
            String targetMenuId,
            String viewAction,
            String label,
            IconKey icon,
            String... params) {
        this.mode = mode;
        this.targetMenuId = targetMenuId;
        this.viewAction = viewAction;
        this.label = label;
        this.icon = icon;
        this.params = params.clone();
    }

    /**
     * Returns to the previous view.
     *
     * <p>The only navigation button with a label the caller does not supply, because the
     * word is the same in every language a menu goes and a bot author should not have to
     * translate it once per menu.
     *
     * @return the component
     */
    public static Nav back() {
        return new Nav(NavigationMode.BACK, null, null, null, IconKey.BACK);
    }

    /**
     * Opens a view of another menu and keeps the current one in history.
     *
     * @param targetMenuId the menu to show
     * @param label the button's text, already localized
     * @return the component
     */
    public static Nav push(String targetMenuId, String label) {
        return new Nav(NavigationMode.PUSH, targetMenuId, null, label, null);
    }

    /**
     * Opens a view of another menu, discarding the history.
     *
     * @param targetMenuId the menu to show
     * @param label the button's text, already localized
     * @return the component
     */
    public static Nav replace(String targetMenuId, String label) {
        return new Nav(NavigationMode.REPLACE, targetMenuId, null, label, null);
    }

    /**
     * Opens a view of another menu and clears the history entirely.
     *
     * @param targetMenuId the menu to show
     * @param label the button's text, already localized
     * @return the component
     */
    public static Nav root(String targetMenuId, String label) {
        return new Nav(NavigationMode.ROOT, targetMenuId, null, label, null);
    }

    /**
     * Opens a view of the menu being rendered and keeps this one in history.
     *
     * <p>For a menu with more than one screen, where a menu id alone does not say what to
     * show. The view is named by an action the menu handles, so {@code render} must switch
     * on it and answer {@code unknownView} for anything else.
     *
     * @param action the action that produces the view
     * @param label the button's text, already localized
     * @param params parameters the view was invoked with
     * @return the component
     */
    public static Nav view(String action, String label, String... params) {
        return new Nav(NavigationMode.PUSH, null, action, label, null, params);
    }

    /**
     * Opens a view of this menu, replacing what is on screen.
     *
     * <p>For a drill-down inside one screen, such as swapping a list for its filters and
     * back. The history is untouched, so Back leaves the view entirely rather than
     * undoing the swap.
     *
     * @param action the action that produces the view
     * @param label the button's text, already localized
     * @param params parameters the view was invoked with
     * @return the component
     */
    public static Nav swap(String action, String label, String... params) {
        return new Nav(NavigationMode.REPLACE, null, action, label, null, params);
    }

    /**
     * Opens a view of another menu and keeps this one in history.
     *
     * @param menuId the menu that owns the view
     * @param action the action that produces the view
     * @param label the button's text, already localized
     * @param params parameters the view was invoked with
     * @return the component
     */
    public static Nav to(String menuId, String action, String label, String... params) {
        return new Nav(NavigationMode.PUSH, menuId, action, label, null, params);
    }

    /**
     * Replaces the button's text.
     *
     * <p>For the navigation buttons that are not going back, the label is already the
     * caller's. This is for {@link #back()} when the caller needs a different word, such
     * as "cancel" on a confirmation.
     *
     * @param label the text, already localized
     * @return a copy of this button
     */
    public Nav label(String label) {
        return new Nav(mode, targetMenuId, viewAction, label, icon, params);
    }

    /**
     * Shows a semantic icon, resolved from the preset.
     *
     * @param icon the meaning
     * @return a copy of this button
     */
    public Nav icon(IconKey icon) {
        return new Nav(mode, targetMenuId, viewAction, label, icon, params);
    }

    @Override
    public ActionRowChildComponent render(MenuContext ctx) {
        return Button.of(
                Looks.style(ctx.preset(), ButtonRole.SECONDARY),
                id(ctx),
                resolvedLabel(ctx),
                icon == null ? null : Looks.icon(ctx.preset(), icon).orElse(null));
    }

    /**
     * The id, in whichever form this button needs.
     *
     * <p>A button naming only a menu keeps the short id it has always had, with no view
     * segment, so a message sent before views existed still resolves. A button naming a
     * view spells the view out, and carries its params after it.
     */
    private String id(MenuContext ctx) {
        String menuId = targetMenuId == null ? ctx.menuId() : targetMenuId;
        if (viewAction == null) {
            return NavigationAction.buttonId(ctx.menuId(), mode, menuId);
        }
        return NavigationAction.buttonId(
                ctx.menuId(), mode, new NavEntry(menuId, viewAction, List.of(params.clone())));
    }

    /** The caller's label, or the bundle's for {@link #back()}. */
    private String resolvedLabel(MenuContext ctx) {
        return label != null ? label : ctx.t(MessageKeys.NAV_BACK);
    }
}
