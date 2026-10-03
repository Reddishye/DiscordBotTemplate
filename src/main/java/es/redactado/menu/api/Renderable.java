package es.redactado.menu.api;

import net.dv8tion.jda.api.components.container.ContainerChildComponent;

/** Something that can produce a single JDA container child component. */
@FunctionalInterface
public interface Renderable {

    /**
     * Produces the JDA child component.
     *
     * @param ctx the context of the current interaction
     * @return the rendered component
     */
    ContainerChildComponent render(Context ctx);
}
