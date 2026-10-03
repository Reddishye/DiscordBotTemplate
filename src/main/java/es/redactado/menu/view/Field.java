package es.redactado.menu.view;

import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.core.ComponentId;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.emoji.Emoji;

/** A labelled field with optional edit button. Renders Section(Button + TextDisplay). */
public class Field implements MenuComponent {

    private static final String NO_ACTION = "noop";

    private final String label;
    private final String value;
    private final String actionId;
    private final Emoji emoji;

    private Field(String label, String value, String actionId, Emoji emoji) {
        this.label = label;
        this.value = value;
        this.actionId = actionId;
        this.emoji = emoji;
    }

    public static Field of(String label, String value) {
        return new Field(label, value, null, null);
    }

    public static Field editable(String label, String value, String actionId) {
        return new Field(label, value, actionId, null);
    }

    public static Field danger(String label, String value, String actionId) {
        return new Field(
                label,
                value,
                actionId,
                Emoji.fromCustom("lucide_eraser", 1521822427246759936L, false));
    }

    public Field emoji(Emoji e) {
        return new Field(label, value, actionId, e);
    }

    @Override
    public List<ContainerChildComponent> render(MenuContext ctx) {
        Emoji accessoryEmoji =
                emoji != null
                        ? emoji
                        : Emoji.fromCustom("lucide_check", 1521846140775960626L, false);
        Accessory accessory =
                c ->
                        Button.of(
                                ButtonStyle.SECONDARY,
                                actionId != null
                                        ? ComponentId.encode(c.menuId(), actionId)
                                        : NO_ACTION,
                                accessoryEmoji);
        return List.of(
                Section.of(
                        accessory.render(ctx),
                        TextDisplay.of("**%s:** %s".formatted(label, safeValue(ctx)))));
    }

    /**
     * The value, or a localized stand-in when there is none.
     *
     * <p>Resolved from the context so a menu shown to a Spanish speaker says
     * "Sin definir" rather than the English default.
     */
    private String safeValue(MenuContext ctx) {
        return value != null && !value.isBlank()
                ? value
                : ctx.t(es.redactado.menu.core.MessageKeys.FIELD_NOT_SET);
    }
}
