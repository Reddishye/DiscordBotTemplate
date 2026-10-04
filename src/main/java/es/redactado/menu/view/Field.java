package es.redactado.menu.view;

import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.core.ComponentId;
import es.redactado.menu.preset.ButtonRole;
import es.redactado.menu.preset.IconKey;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;

/**
 * One labelled value, optionally editable.
 *
 * <p>A field with nothing to do renders as a line of text. A field with an action renders
 * as a section with a button beside it. That split is not cosmetic: Discord rejects an
 * action button that has neither a label nor an emoji, and a read-only field has no
 * action to press, so giving it a button would mean inventing an icon for it, which is
 * the hardcoded emoji this package no longer has.
 *
 * <p>The editable button takes its icon from the preset. A preset that defines none falls
 * back to the field's own label as the button's text, which is caller-supplied and
 * therefore already localized, rather than to a hardcoded glyph.
 */
public final class Field implements MenuComponent {

    private final String label;
    private final String value;
    private final String actionId;
    private final IconKey icon;

    private Field(String label, String value, String actionId, IconKey icon) {
        this.label = label;
        this.value = value;
        this.actionId = actionId;
        this.icon = icon;
    }

    /**
     * A read-only value.
     *
     * @param label what the value is
     * @param value the value
     * @return the component
     */
    public static Field of(String label, String value) {
        return new Field(label, value, null, null);
    }

    /**
     * A value the user can edit.
     *
     * @param label what the value is
     * @param value the value
     * @param actionId the action the button runs
     * @return the component
     */
    public static Field editable(String label, String value, String actionId) {
        return new Field(label, value, actionId, IconKey.EDIT);
    }

    /**
     * A value whose action is destructive.
     *
     * @param label what the value is
     * @param value the value
     * @param actionId the action the button runs
     * @return the component
     */
    public static Field danger(String label, String value, String actionId) {
        return new Field(label, value, actionId, IconKey.EDIT);
    }

    /**
     * Shows a different icon on the edit button, taken from the preset.
     *
     * @param icon the meaning
     * @return a copy of this field
     */
    public Field icon(IconKey icon) {
        return new Field(label, value, actionId, icon);
    }

    @Override
    public List<ContainerChildComponent> render(MenuContext ctx) {
        String content = "**%s:** %s".formatted(label, safeValue(ctx));
        if (actionId == null) {
            return List.of(TextDisplay.of(content));
        }

        String id = ComponentId.encode(ctx.menuId(), actionId);
        Accessory accessory =
                c ->
                        Button.of(
                                Looks.style(c.preset(), ButtonRole.SECONDARY),
                                id,
                                accessoryLabel(c),
                                accessoryEmoji(c));
        return List.of(Section.of(accessory.render(ctx), TextDisplay.of(content)));
    }

    /** The preset's icon for this field, or none when the preset defines none. */
    private EmojiUnion accessoryEmoji(MenuContext ctx) {
        IconKey key = icon == null ? IconKey.INFO : icon;
        return Looks.icon(ctx.preset(), key).orElse(null);
    }

    /**
     * The button's text when it has no icon.
     *
     * <p>Discord requires an action button to have a label or an emoji, and the
     * {@code minimal} preset defines no icons at all, so something caller-supplied has to
     * stand in. The field's own label is the honest choice: it names what the button
     * edits.
     */
    private String accessoryLabel(MenuContext ctx) {
        return accessoryEmoji(ctx) == null ? label : "";
    }

    /**
     * The value, or a localized stand-in when there is none.
     *
     * <p>Resolved from the context so a menu shown to a Spanish speaker says its own word
     * rather than the English default.
     */
    private String safeValue(MenuContext ctx) {
        return value != null && !value.isBlank()
                ? value
                : ctx.t(es.redactado.menu.core.MessageKeys.FIELD_NOT_SET);
    }
}
