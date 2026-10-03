package es.redactado.menu.view;

import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.MenuContext;
import java.util.List;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.separator.Separator;

/**
 * A break between two sections of a menu.
 *
 * <p>Two things rather than one, because they are two decisions. {@link #line()} draws
 * a rule or does not, depending on the preset, while both forms reserve the same space.
 * {@link #space()} always reserves space and never draws, which is how a caller asks for
 * breathing room in a preset that has no rules at all.
 *
 * <p>Renamed from {@code JdaSeparator}, which named the JDA type rather than the thing
 * it is for.
 */
public final class Divider implements MenuComponent {

    private final boolean visible;

    private Divider(boolean visible) {
        this.visible = visible;
    }

    /**
     * A break that draws a rule when the preset wants one.
     *
     * @return the component
     */
    public static Divider line() {
        return new Divider(true);
    }

    /**
     * A break that only reserves space, whatever the preset says.
     *
     * @return the component
     */
    public static Divider space() {
        return new Divider(false);
    }

    @Override
    public List<ContainerChildComponent> render(MenuContext ctx) {
        Separator.Spacing spacing = Looks.spacing(ctx.preset());
        boolean drawRule = visible && ctx.preset().divider().visible();
        return List.of(Separator.create(drawRule, spacing));
    }
}
