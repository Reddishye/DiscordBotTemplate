package es.redactado.menu.view;

import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.MenuContext;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

/**
 * Wraps children into a JDA ActionRow. Children MUST be button/select components
 * (ActionRowChildComponent), not display components (TextDisplay, Section, etc).
 */
public class Row implements MenuComponent {
    private final List<MenuComponent> children;

    private Row(List<MenuComponent> children) {
        this.children = children;
    }

    public static Row of(MenuComponent... components) {
        if (components.length > Limits.MAX_ACTION_ROW_CHILDREN) {
            throw new IllegalArgumentException(
                    "ActionRow max %d children, got %d"
                            .formatted(Limits.MAX_ACTION_ROW_CHILDREN, components.length));
        }
        return new Row(List.of(components));
    }

    @Override
    public List<ContainerChildComponent> render(MenuContext ctx) {
        var jdaComponents =
                children.stream()
                        .flatMap(c -> c.render(ctx).stream())
                        .map(cc -> (ActionRowChildComponent) cc)
                        .toList();
        return List.of(
                (ContainerChildComponent)
                        net.dv8tion.jda.api.components.actionrow.ActionRow.of(jdaComponents));
    }
}
