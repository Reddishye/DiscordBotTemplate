package es.redactado.menu.view;

import es.redactado.menu.api.MenuContext;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent;

/**
 * Something that renders into a JDA action-row child, which is a button or a
 * select menu.
 *
 * <p>Row items are not container children, so they cannot be added to a
 * {@link MenuBuilder} directly. {@link Row} is the only way to place them.
 */
@FunctionalInterface
public interface RowItem {

    /**
     * Renders this item into a JDA action-row child.
     *
     * @param ctx the context of the current interaction
     * @return the button or select to place in the row
     */
    ActionRowChildComponent render(MenuContext ctx);
}
