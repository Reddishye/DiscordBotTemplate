package es.redactado.menu.view;

import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.core.MessageKeys;
import es.redactado.menu.core.NavigationAction;
import es.redactado.menu.preset.ButtonRole;
import es.redactado.menu.preset.IconKey;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent;
import net.dv8tion.jda.api.components.buttons.Button;

/**
 * Buttons that move between views.
 *
 * <p>Every one uses the built-in {@code nav} action, which {@code AbstractMenu}
 * registers for every menu, so a subclass cannot forget to wire navigation and cannot
 * redeclare it either. The id carries the mode and the target, so no handler is needed:
 * the router and the navigator do the work.
 *
 * <p>All four render in the {@link ButtonRole#SECONDARY} style, because moving around is
 * navigation rather than an action with an outcome, and a row of navigation should not
 * compete with the one button that actually does something.
 *
 * <p>The id is built at render time rather than at construction, because it starts with
 * the id of the menu doing the rendering, which the constructor does not know.
 *
 * <p>Labels are caller-supplied and therefore already localized, except for
 * {@link #back()}: "back" is the one label a menu author should not have to write in
 * every language, so it comes from the bundles.
 */
public final class Nav implements RowItem {

    private final NavigationMode mode;
    private final String targetMenuId;
    private final String label;
    private final IconKey icon;

    private Nav(NavigationMode mode, String targetMenuId, String label, IconKey icon) {
        this.mode = mode;
        this.targetMenuId = targetMenuId;
        this.label = label;
        this.icon = icon;
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
        return new Nav(NavigationMode.BACK, null, null, IconKey.BACK);
    }

    /**
     * Opens a view and keeps the current one in history.
     *
     * @param targetMenuId the menu to show
     * @param label the button's text, already localized
     * @return the component
     */
    public static Nav push(String targetMenuId, String label) {
        return new Nav(NavigationMode.PUSH, targetMenuId, label, null);
    }

    /**
     * Opens a view, discarding the history.
     *
     * @param targetMenuId the menu to show
     * @param label the button's text, already localized
     * @return the component
     */
    public static Nav replace(String targetMenuId, String label) {
        return new Nav(NavigationMode.REPLACE, targetMenuId, label, null);
    }

    /**
     * Opens a view and clears the history entirely.
     *
     * @param targetMenuId the menu to show
     * @param label the button's text, already localized
     * @return the component
     */
    public static Nav root(String targetMenuId, String label) {
        return new Nav(NavigationMode.ROOT, targetMenuId, label, null);
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
        return new Nav(mode, targetMenuId, label, icon);
    }

    /**
     * Shows a semantic icon, resolved from the preset.
     *
     * @param icon the meaning
     * @return a copy of this button
     */
    public Nav icon(IconKey icon) {
        return new Nav(mode, targetMenuId, label, icon);
    }

    @Override
    public ActionRowChildComponent render(MenuContext ctx) {
        return Button.of(
                Looks.style(ctx.preset(), ButtonRole.SECONDARY),
                NavigationAction.buttonId(ctx.menuId(), mode, targetMenuId),
                resolvedLabel(ctx),
                icon == null ? null : Looks.icon(ctx.preset(), icon).orElse(null));
    }

    /** The caller's label, or the bundle's for {@link #back()}. */
    private String resolvedLabel(MenuContext ctx) {
        return label != null ? label : ctx.t(MessageKeys.NAV_BACK);
    }
}
