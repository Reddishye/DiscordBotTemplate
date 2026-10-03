package es.redactado.menu.view;

import es.redactado.menu.api.Component;
import es.redactado.menu.api.Context;
import es.redactado.menu.core.ComponentId;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.emoji.Emoji;

/** A labelled field with optional edit button. Renders Section(Button + TextDisplay). */
public class Field implements Component {
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
    public List<ContainerChildComponent> render(Context ctx) {
        Emoji e =
                emoji != null
                        ? emoji
                        : Emoji.fromCustom("lucide_check", 1521846140775960626L, false);
        String id = actionId != null ? ComponentId.encode(ctx.menuId(), actionId) : "noop";
        var btn = Button.of(ButtonStyle.SECONDARY, id, e);
        return List.of(
                (ContainerChildComponent)
                        Section.of(
                                btn, TextDisplay.of("**%s:** %s".formatted(label, safeValue()))));
    }

    private String safeValue() {
        return value != null && !value.isBlank() ? value : "*Not set*";
    }
}
