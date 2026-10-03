package es.redactado.menu.view;

import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.separator.Separator.Spacing;

/** Utility for creating JDA Separator components that can be added directly to Container child lists. */
public final class JdaSeparator {
    private JdaSeparator() {}

    public static ContainerChildComponent large() {
        return Separator.createDivider(Spacing.LARGE);
    }

    public static ContainerChildComponent small() {
        return Separator.createDivider(Spacing.SMALL);
    }

    public static ContainerChildComponent invisible() {
        return Separator.createInvisible(Spacing.SMALL);
    }

    public static ContainerChildComponent invisibleLarge() {
        return Separator.createInvisible(Spacing.LARGE);
    }
}
