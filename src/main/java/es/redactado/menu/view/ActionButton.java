package es.redactado.menu.view;

import es.redactado.menu.api.MenuContext;
import es.redactado.menu.core.ComponentId;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.emoji.Emoji;

/**
 * A button that triggers an action of the menu that rendered it. Place it in a
 * {@link Row}.
 */
public final class ActionButton implements RowItem {

    private final ButtonStyle style;
    private final String action;
    private final String label;
    private final Emoji emoji;
    private final boolean disabled;
    private final String[] extraParams;

    private ActionButton(
            ButtonStyle style,
            String action,
            String label,
            Emoji emoji,
            boolean disabled,
            String... extraParams) {
        this.style = style;
        this.action = action;
        this.label = label;
        this.emoji = emoji;
        this.disabled = disabled;
        this.extraParams = extraParams;
    }

    /**
     * Creates a primary-styled button.
     *
     * @param action the action name encoded into the component id
     * @param label the button text
     * @return the button
     */
    public static ActionButton primary(String action, String label) {
        return new ActionButton(ButtonStyle.PRIMARY, action, label, null, false);
    }

    /**
     * Creates a secondary-styled button.
     *
     * @param action the action name encoded into the component id
     * @param label the button text
     * @return the button
     */
    public static ActionButton secondary(String action, String label) {
        return new ActionButton(ButtonStyle.SECONDARY, action, label, null, false);
    }

    /**
     * Creates a success-styled button.
     *
     * @param action the action name encoded into the component id
     * @param label the button text
     * @return the button
     */
    public static ActionButton success(String action, String label) {
        return new ActionButton(ButtonStyle.SUCCESS, action, label, null, false);
    }

    /**
     * Creates a danger-styled button.
     *
     * @param action the action name encoded into the component id
     * @param label the button text
     * @return the button
     */
    public static ActionButton danger(String action, String label) {
        return new ActionButton(ButtonStyle.DANGER, action, label, null, false);
    }

    /**
     * Returns a copy carrying the given emoji.
     *
     * @param emoji the emoji to show on the button
     * @return a new button
     */
    public ActionButton emoji(Emoji emoji) {
        return new ActionButton(style, action, label, emoji, disabled, extraParams);
    }

    /**
     * Returns a copy in the given enabled state.
     *
     * @param disabled whether the button is disabled
     * @return a new button
     */
    public ActionButton disabled(boolean disabled) {
        return new ActionButton(style, action, label, emoji, disabled, extraParams);
    }

    /**
     * Returns a copy carrying extra component-id parameters.
     *
     * @param params the parameters appended after the action name
     * @return a new button
     */
    public ActionButton params(String... params) {
        return new ActionButton(style, action, label, emoji, disabled, params);
    }

    @Override
    public ActionRowChildComponent render(MenuContext ctx) {
        String id = ComponentId.encode(ctx.menuId(), action, extraParams);
        Button button = Button.of(style, id, label, emoji);
        return disabled ? button.asDisabled() : button;
    }
}
