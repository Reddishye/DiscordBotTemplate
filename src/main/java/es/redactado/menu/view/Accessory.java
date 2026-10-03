package es.redactado.menu.view;

import es.redactado.menu.api.MenuContext;
import net.dv8tion.jda.api.components.section.SectionAccessoryComponent;

/**
 * Something that renders into a JDA section accessory, which is a button or a
 * thumbnail.
 *
 * <p>Accessories are not container children, so they cannot be added to a
 * {@link MenuBuilder} directly. They are placed in a JDA {@code Section}.
 */
@FunctionalInterface
public interface Accessory {

    /**
     * Renders this accessory into a JDA section accessory component.
     *
     * @param ctx the context of the current interaction
     * @return the button or thumbnail to place beside the section text
     */
    SectionAccessoryComponent render(MenuContext ctx);
}
