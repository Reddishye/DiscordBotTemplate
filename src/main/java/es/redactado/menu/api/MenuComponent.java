package es.redactado.menu.api;

import java.util.List;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

/**
 * A single UI piece within a menu. Each component knows how to render itself
 * into one or more JDA container child components.
 *
 * <p>Types in this package describe the protocol between a menu and the renderer,
 * so they never depend on the presentation layer.
 */
public interface MenuComponent {

    /**
     * Produces the JDA child components this element contributes.
     *
     * <p>Most components return a list of one. Compound components such as
     * {@code es.redactado.menu.view.Row} and
     * {@code es.redactado.menu.view.SectionList} return several.
     *
     * @param ctx the context of the current interaction
     * @return the JDA child components, never empty
     */
    List<ContainerChildComponent> render(MenuContext ctx);
}
