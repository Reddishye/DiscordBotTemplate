package es.redactado.menu.view;

import es.redactado.menu.api.MenuContext;
import es.redactado.menu.core.ComponentId;
import es.redactado.menu.preset.ButtonRole;
import es.redactado.menu.preset.IconKey;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;

/**
 * A button that runs one of the menu's actions.
 *
 * <p>The factory names what the button <em>means</em>, not how it looks: {@code danger}
 * asks for the danger role and the preset decides that is a red button in one theme and a
 * quiet grey one in another. Nothing here names a colour, which is what lets a monochrome
 * theme render everything the same without any component knowing.
 *
 * <p>A button with no icon renders with no icon. There is no default glyph, because a
 * default is a hardcoded emoji that every bot would ship and no preset could remove.
 */
public final class ActionButton implements RowItem {

    private final ButtonRole role;
    private final String action;
    private final String label;
    private final IconKey icon;
    private final EmojiUnion literalEmoji;
    private final boolean disabled;
    private final String[] extraParams;

    private ActionButton(
            ButtonRole role,
            String action,
            String label,
            IconKey icon,
            EmojiUnion literalEmoji,
            boolean disabled,
            String... extraParams) {
        this.role = role;
        this.action = action;
        this.label = label;
        this.icon = icon;
        this.literalEmoji = literalEmoji;
        this.disabled = disabled;
        this.extraParams = extraParams;
    }

    /**
     * The menu's main action.
     *
     * @param action the action id
     * @param label the button's text
     * @return the component
     */
    public static ActionButton primary(String action, String label) {
        return new ActionButton(ButtonRole.PRIMARY, action, label, null, null, false);
    }

    /**
     * An action of secondary importance.
     *
     * @param action the action id
     * @param label the button's text
     * @return the component
     */
    public static ActionButton secondary(String action, String label) {
        return new ActionButton(ButtonRole.SECONDARY, action, label, null, null, false);
    }

    /**
     * An action that confirms something good.
     *
     * @param action the action id
     * @param label the button's text
     * @return the component
     */
    public static ActionButton success(String action, String label) {
        return new ActionButton(ButtonRole.SUCCESS, action, label, null, null, false);
    }

    /**
     * An action that destroys or is otherwise hard to undo.
     *
     * @param action the action id
     * @param label the button's text
     * @return the component
     */
    public static ActionButton danger(String action, String label) {
        return new ActionButton(ButtonRole.DANGER, action, label, null, null, false);
    }

    /**
     * Shows an icon taken from the preset.
     *
     * <p>Asks for a meaning, not a glyph. A preset with no icon for it renders no icon.
     *
     * @param icon the meaning
     * @return a copy of this button
     */
    public ActionButton icon(IconKey icon) {
        return new ActionButton(role, action, label, icon, literalEmoji, disabled, extraParams);
    }

    /**
     * Shows a specific emoji regardless of the preset.
     *
     * <p>For an emoji that is part of the content rather than the decoration, such as a
     * user's own status. Prefer {@link #icon(IconKey)} for anything decorative.
     *
     * <p>Typed as {@link EmojiUnion} because that is what {@code Button.of} accepts; a
     * narrower type here would not compile at the call site.
     *
     * @param emoji the emoji to show
     * @return a copy of this button
     */
    public ActionButton emoji(EmojiUnion emoji) {
        return new ActionButton(role, action, label, icon, emoji, disabled, extraParams);
    }

    /**
     * Greys the button out and stops it being pressed.
     *
     * @param disabled whether the button is disabled
     * @return a copy of this button
     */
    public ActionButton disabled(boolean disabled) {
        return new ActionButton(role, action, label, icon, literalEmoji, disabled, extraParams);
    }

    /**
     * Appends parameters to the encoded action id.
     *
     * @param params the parameters
     * @return a copy of this button
     */
    public ActionButton params(String... params) {
        return new ActionButton(role, action, label, icon, literalEmoji, disabled, params);
    }

    @Override
    public ActionRowChildComponent render(MenuContext ctx) {
        String id = ComponentId.encode(ctx.menuId(), action, extraParams);
        Button button = Button.of(Looks.style(ctx.preset(), role), id, label, resolveEmoji(ctx));
        return disabled ? button.asDisabled() : button;
    }

    /** A literal emoji wins, because it was chosen on purpose for this button. */
    private EmojiUnion resolveEmoji(MenuContext ctx) {
        if (literalEmoji != null) {
            return literalEmoji;
        }
        return icon == null ? null : Looks.icon(ctx.preset(), icon).orElse(null);
    }
}
