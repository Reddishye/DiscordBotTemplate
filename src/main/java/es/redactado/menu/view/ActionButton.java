package es.redactado.menu.view;

import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.core.ComponentId;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.entities.emoji.Emoji;

public class ActionButton implements MenuComponent {
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

    public static ActionButton primary(String action, String label) {
        return new ActionButton(ButtonStyle.PRIMARY, action, label, null, false);
    }

    public static ActionButton secondary(String action, String label) {
        return new ActionButton(ButtonStyle.SECONDARY, action, label, null, false);
    }

    public static ActionButton success(String action, String label) {
        return new ActionButton(ButtonStyle.SUCCESS, action, label, null, false);
    }

    public static ActionButton danger(String action, String label) {
        return new ActionButton(ButtonStyle.DANGER, action, label, null, false);
    }

    public ActionButton emoji(Emoji e) {
        return new ActionButton(style, action, label, e, disabled, extraParams);
    }

    public ActionButton disabled(boolean d) {
        return new ActionButton(style, action, label, emoji, d, extraParams);
    }

    public ActionButton params(String... params) {
        return new ActionButton(style, action, label, emoji, disabled, params);
    }

    @Override
    public List<ContainerChildComponent> render(MenuContext ctx) {
        String id = ComponentId.encode(ctx.menuId(), action, extraParams);
        Button btn =
                label != null && !label.isEmpty()
                        ? Button.of(style, id, label, emoji)
                        : Button.of(style, id, emoji);
        if (disabled) btn = btn.asDisabled();
        return List.of((ContainerChildComponent) btn);
    }
}
