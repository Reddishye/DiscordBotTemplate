package es.redactado.menu.view;

import es.redactado.menu.api.Limits;
import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.MenuContext;
import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

/**
 * Groups {@link RowItem}s into a JDA action row, which is the only way buttons
 * and selects reach a container.
 */
public final class Row implements MenuComponent {

    private final List<RowItem> items;

    private Row(List<RowItem> items) {
        this.items = items;
    }

    /**
     * Builds a row from one to {@link Limits#MAX_ACTION_ROW_CHILDREN} items.
     *
     * @param items the buttons or selects in this row
     * @return the row
     * @throws IllegalArgumentException if no items are given, if more than
     *     {@link Limits#MAX_ACTION_ROW_CHILDREN} are, or if a {@link SelectMenu} shares
     *     the row with anything else
     */
    public static Row of(RowItem... items) {
        if (items.length == 0) {
            throw new IllegalArgumentException("A row requires at least one item");
        }
        if (items.length > Limits.MAX_ACTION_ROW_CHILDREN) {
            throw new IllegalArgumentException(
                    "A row accepts at most %d items, got %d"
                            .formatted(Limits.MAX_ACTION_ROW_CHILDREN, items.length));
        }
        rejectSharedSelect(items);
        return new Row(List.of(items));
    }

    /**
     * Rejects a select menu sharing its row.
     *
     * <p>Discord gives a select the full width of the row and no more, so anything beside
     * it is discarded by the client without an error reaching the bot. Catching it here
     * turns a silently missing button into a failure in the menu author's own test.
     */
    private static void rejectSharedSelect(RowItem[] items) {
        if (items.length < 2) {
            return;
        }
        for (RowItem item : items) {
            if (item instanceof SelectMenu) {
                throw new IllegalArgumentException(
                        "A select menu must be the only item in its row, but the row has %d items"
                                .formatted(items.length));
            }
        }
    }

    @Override
    public List<ContainerChildComponent> render(MenuContext ctx) {
        List<ActionRowChildComponent> rendered = new ArrayList<>(items.size());
        for (RowItem item : items) {
            rendered.add(item.render(ctx));
        }
        return List.of(ActionRow.of(rendered));
    }
}
