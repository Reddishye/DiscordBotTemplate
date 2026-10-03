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
     * @throws IllegalArgumentException if no items are given, or more than
     *     {@link Limits#MAX_ACTION_ROW_CHILDREN} are
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
        return new Row(List.of(items));
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
